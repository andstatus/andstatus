/**
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.andstatus.app;

import android.app.LoaderManager;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import org.andstatus.app.data.AccountUserIds;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.TimelineSql;
import org.andstatus.app.data.TimelineTypeEnum;
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.util.SelectionAndArgs;

import java.util.Arrays;
import java.util.Date;

class TimelineListParameters {
    final Context mContext;
    
    LoaderManager.LoaderCallbacks<Cursor> mLoaderCallbacks = null;
    
    /**
     * Msg are being loaded into the list starting from one page. More Msg
     * are being loaded in a case User scrolls down to the end of list.
     */
    static final int PAGE_SIZE = 100;
    
    TimelineTypeEnum mTimelineType = TimelineTypeEnum.UNKNOWN;
    /** Combined Timeline shows messages from all accounts */
    boolean mTimelineCombined = false;
    long myAccountUserId = 0;
    /**
     * Selected User for the {@link TimelineTypeEnum#USER} timeline.
     * This is either User Id of current account OR user id of any other selected user.
     * So it's never == 0 for the {@link TimelineTypeEnum#USER} timeline
     */
    long mSelectedUserId = 0;
    /**
     * The string is not empty if this timeline is filtered using query string
     * ("Mentions" are not counted here because they have separate TimelineType)
     */
    String mSearchQuery = "";

    boolean mLoadOneMorePage = false;
    boolean mReQuery = false;
    String[] mProjection;

    Uri mContentUri = null;
    boolean mIncrementallyLoadingPages = false;
    int mRowsLimit = 0;
    long mLastItemSentDate = 0;
    volatile SelectionAndArgs mSa = new SelectionAndArgs();
    String mSortOrder = MyDatabase.Msg.DEFAULT_SORT_ORDER;
    
    // Execution state / data:
    volatile long startTime = 0;
    volatile boolean cancelled = false;
    volatile TimelineTypeEnum timelineToReload = TimelineTypeEnum.UNKNOWN;
    

    public static TimelineListParameters clone(TimelineListParameters prev, Bundle args) {
        TimelineListParameters params = new TimelineListParameters(prev.mContext);
        params.mLoaderCallbacks = prev.mLoaderCallbacks;
        params.mTimelineType = prev.getTimelineType();
        params.mTimelineCombined = prev.isTimelineCombined();
        params.myAccountUserId = prev.getMyAccountUserId();
        params.mSelectedUserId = prev.getSelectedUserId();
        params.mSearchQuery = prev.mSearchQuery;

        boolean positionRestored = false;
        boolean loadOneMorePage = false;
        boolean reQuery = false;
        if (args != null) {
            loadOneMorePage = args.getBoolean(IntentExtra.EXTRA_LOAD_ONE_MORE_PAGE.key);
            positionRestored = args.getBoolean(IntentExtra.EXTRA_POSITION_RESTORED.key);
            reQuery = args.getBoolean(IntentExtra.EXTRA_REQUERY.key);
            params.mRowsLimit = args.getInt(IntentExtra.EXTRA_ROWS_LIMIT.key);
        }
        params.mLoadOneMorePage = loadOneMorePage;
        params.mIncrementallyLoadingPages = positionRestored && loadOneMorePage;
        params.mReQuery = reQuery;
        params.mProjection = TimelineSql.getTimelineProjection();
        
        params.prepareQueryForeground(positionRestored);
        
        return params;
    }
    
    private void prepareQueryForeground(boolean positionRestored) {
        mContentUri = MyProvider.getTimelineSearchUri(myAccountUserId, mTimelineType,
                mTimelineCombined, mSearchQuery);

        if (mSa.nArgs == 0) {
            // In fact this is needed every time you want to load
            // next page of messages

            /* TODO: Other conditions... */
            mSa.clear();

            // TODO: Move these selections to the {@link MyProvider} ?!
            switch (getTimelineType()) {
                case HOME:
                    // In the Home of the combined timeline we see ALL loaded
                    // messages, even those that we downloaded
                    // not as Home timeline of any Account
                    if (!isTimelineCombined()) {
                        mSa.addSelection(MyDatabase.MsgOfUser.SUBSCRIBED + " = ?", new String[] {
                                "1"
                        });
                    }
                    break;
                case MENTIONS:
                    mSa.addSelection(MyDatabase.MsgOfUser.MENTIONED + " = ?", new String[] {
                            "1"
                    });
                    /*
                     * We already figured this out and set {@link MyDatabase.MsgOfUser.MENTIONED}:
                     * sa.addSelection(MyDatabase.Msg.BODY + " LIKE ?" ...
                     */
                    break;
                case FAVORITES:
                    mSa.addSelection(MyDatabase.MsgOfUser.FAVORITED + " = ?", new String[] {
                            "1"
                    });
                    break;
                case DIRECT:
                    mSa.addSelection(MyDatabase.MsgOfUser.DIRECTED + " = ?", new String[] {
                            "1"
                    });
                    break;
                case USER:
                    AccountUserIds userIds = new AccountUserIds(isTimelineCombined(), getSelectedUserId());
                    // Reblogs are included also
                    mSa.addSelection(MyDatabase.Msg.AUTHOR_ID + " " + userIds.getSqlUserIds() 
                            + " OR "
                            + MyDatabase.Msg.SENDER_ID + " " + userIds.getSqlUserIds() 
                            + " OR " 
                            + "("
                            + User.LINKED_USER_ID + " " + userIds.getSqlUserIds() 
                            + " AND "
                            + MyDatabase.MsgOfUser.REBLOGGED + " = 1"
                            + ")",
                            null);
                    break;
                default:
                    break;
            }
        }

        if (!positionRestored) {
            // We have to ensure that saved position will be
            // loaded from database into the list
            mLastItemSentDate = new TimelineListPositionStorage(null, null, this).getLastRetrievedSentDate();
        }

        if (mLastItemSentDate <= 0) {
            int rowsLimit2 = this.mRowsLimit;
            if (rowsLimit2 < TimelineListParameters.PAGE_SIZE) {
                rowsLimit2 = TimelineListParameters.PAGE_SIZE;
            }
            mSortOrder += " LIMIT 0," + rowsLimit2;
        }
    }
    
    public TimelineListParameters(Context context) {
        this.mContext = context;
    }

    public boolean isEmpty() {
        return mTimelineType == TimelineTypeEnum.UNKNOWN;
    }
    
    @Override
    public String toString() {
        return "TimelineListParameters [loaderCallbacks=" + mLoaderCallbacks + ", loadOneMorePage="
                + mLoadOneMorePage + ", reQuery=" + mReQuery + ", timelineType=" + mTimelineType
                + ", timelineCombined=" + mTimelineCombined + ", myAccountUserId=" + myAccountUserId
                + ", selectedUserId=" + mSelectedUserId + ", projection="
                + Arrays.toString(mProjection) + ", searchQuery=" + mSearchQuery + ", contentUri="
                + mContentUri + ", incrementallyLoadingPages=" + mIncrementallyLoadingPages
                + ", rowsLimit=" + mRowsLimit + ", lastSentDate=" + new Date(mLastItemSentDate).toString() + ", sa=" + mSa
                + ", sortOrder=" + mSortOrder + ", startTime=" + startTime + ", cancelled="
                + cancelled + ", timelineToReload=" + timelineToReload + "]";
    }

    public TimelineTypeEnum getTimelineType() {
        return mTimelineType;
    }

    public void setTimelineType(TimelineTypeEnum timelineType) {
        mTimelineType = timelineType;
    }
    
    public long getSelectedUserId() {
        return mSelectedUserId;
    }

    public boolean isTimelineCombined() {
        return mTimelineCombined;
    }

    public void setTimelineCombined(boolean isTimelineCombined) {
        mTimelineCombined = isTimelineCombined;
    }

    public long getMyAccountUserId() {
        return myAccountUserId;
    }

    public void saveState(Editor outState) {
        outState.putString(IntentExtra.EXTRA_TIMELINE_TYPE.key, getTimelineType().save());
        outState.putBoolean(IntentExtra.EXTRA_TIMELINE_IS_COMBINED.key, isTimelineCombined());
        outState.putString(IntentExtra.EXTRA_SEARCH_QUERY.key, mSearchQuery);
        outState.putLong(IntentExtra.EXTRA_SELECTEDUSERID.key, mSelectedUserId);
    }
    
    boolean restoreState(SharedPreferences savedInstanceState) {
        TimelineTypeEnum timelineTypeNew = TimelineTypeEnum.load(savedInstanceState
                .getString(IntentExtra.EXTRA_TIMELINE_TYPE.key,""));
        if (timelineTypeNew == TimelineTypeEnum.UNKNOWN) {
            return false;
        }
        setTimelineType(timelineTypeNew);
        if (savedInstanceState.contains(IntentExtra.EXTRA_TIMELINE_IS_COMBINED.key)) {
            setTimelineCombined(savedInstanceState.getBoolean(IntentExtra.EXTRA_TIMELINE_IS_COMBINED.key, false));
        }
        if (savedInstanceState.contains(IntentExtra.EXTRA_SEARCH_QUERY.key)) {
            mSearchQuery = notNullString(savedInstanceState.getString(IntentExtra.EXTRA_SEARCH_QUERY.key, ""));
        }
        if (savedInstanceState.contains(IntentExtra.EXTRA_SELECTEDUSERID.key)) {
            mSelectedUserId = savedInstanceState.getLong(IntentExtra.EXTRA_SELECTEDUSERID.key, 0);
        }
        return true;
    }
    
    void parseIntentData(Intent intentNew) {
        setTimelineType(TimelineTypeEnum.load(intentNew
                .getStringExtra(IntentExtra.EXTRA_TIMELINE_TYPE.key)));
        if (getTimelineType() != TimelineTypeEnum.UNKNOWN) {
            setTimelineCombined(intentNew.getBooleanExtra(IntentExtra.EXTRA_TIMELINE_IS_COMBINED.key, isTimelineCombined()));
            mSearchQuery = notNullString(intentNew.getStringExtra(SearchManager.QUERY));
            mSelectedUserId = intentNew.getLongExtra(IntentExtra.EXTRA_SELECTEDUSERID.key, mSelectedUserId);
        }
    }
    
    public static String notNullString(String string) {
        return string == null ? "" : string;
    }
    
}
