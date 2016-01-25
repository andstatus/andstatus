/**
 * Copyright (C) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.msg;

import android.app.LoaderManager;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;

import org.andstatus.app.IntentExtra;
import org.andstatus.app.R;
import org.andstatus.app.WhichPage;
import org.andstatus.app.data.ProjectionMap;
import org.andstatus.app.data.SelectedUserIds;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.ParsedUri;
import org.andstatus.app.data.TimelineSql;
import org.andstatus.app.data.TimelineType;
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SelectionAndArgs;

import java.util.Date;

public class TimelineListParameters {
    final Context mContext;
    
    LoaderManager.LoaderCallbacks<Cursor> mLoaderCallbacks = null;

    /**
     * Msg are being loaded into the list starting from one page. More Msg
     * are being loaded in a case User scrolls down to the end of list.
     */
    static final int PAGE_SIZE = 100;

    TimelineType mTimelineType = TimelineType.UNKNOWN;
    /** Combined Timeline shows messages from all accounts */
    boolean mTimelineCombined = false;
    long myAccountUserId = 0;
    /**
     * Selected User for the {@link TimelineType#USER} timeline.
     * This is either User Id of current account OR user id of any other selected user.
     * So it's never == 0 for the {@link TimelineType#USER} timeline
     */
    long mSelectedUserId = 0;
    /**
     * The string is not empty if this timeline is filtered using query string
     * ("Mentions" are not counted here because they have separate TimelineType)
     */
    String mSearchQuery = "";

    WhichPage whichPage = WhichPage.EMPTY;
    String[] mProjection;

    Uri mContentUri = null;
    long minSentDate = 0;
    long maxSentDate = 0;
    final SelectionAndArgs mSa = new SelectionAndArgs();

    // Execution state / loaded data:
    volatile long startTime = 0;
    volatile long endTime = 0;
    volatile boolean cancelled = false;
    volatile TimelineType timelineToSync = TimelineType.UNKNOWN;
    volatile int rowsLoaded = 0;
    volatile long minSentDateLoaded = 0;
    volatile long maxSentDateLoaded = 0;

    public static TimelineListParameters clone(TimelineListParameters prev, WhichPage whichPage) {
        TimelineListParameters params = new TimelineListParameters(prev.mContext);
        params.whichPage = whichPage;
        if (whichPage != WhichPage.EMPTY) {
            enrichNonEmptyParameters(params, prev);
        }
        return params;
    }

    private static void enrichNonEmptyParameters(TimelineListParameters params, TimelineListParameters prev) {
        params.mLoaderCallbacks = prev.mLoaderCallbacks;
        params.mTimelineType = prev.getTimelineType();
        params.mTimelineCombined = prev.isTimelineCombined();
        params.myAccountUserId = prev.getMyAccountUserId();
        params.mSelectedUserId = prev.getSelectedUserId();
        params.mSearchQuery = prev.mSearchQuery;

        String msgLog = "Constructing " + params.getSummary();
        switch (params.whichPage) {
            case OLDER:
                if (prev.mayHaveOlderPage()) {
                    params.maxSentDate = prev.minSentDateLoaded;
                } else {
                    params.maxSentDate = prev.maxSentDate;
                }
                break;
            case YOUNGER:
                if (prev.mayHaveYoungerPage()) {
                    params.minSentDate = prev.maxSentDateLoaded;
                } else {
                    params.minSentDate = prev.minSentDate;
                }
                break;
            case SAME:
                params.minSentDate = prev.minSentDate;
                params.maxSentDate = prev.maxSentDate;
                break;
            case NEW:
            default:
                params.minSentDate = new TimelineListPositionStorage(null, null, params).getMinSentDate();
            case TOP:
            case YOUNGEST:
                break;
        }
        MyLog.v(TimelineListParameters.class, msgLog);

        params.mProjection = TimelineSql.getTimelineProjection();

        params.prepareQueryForeground();
    }

    public boolean mayHaveYoungerPage() {
        return maxSentDate > 0 || minSentDate > 0 && rowsLoaded == PAGE_SIZE;
    }

    public boolean mayHaveOlderPage() {
        return minSentDate > 0 || maxSentDate == 0 || rowsLoaded == PAGE_SIZE;
    }

    private void prepareQueryForeground() {
        mContentUri = MatchedUri.getTimelineSearchUri(myAccountUserId, mTimelineType,
                mTimelineCombined, mSelectedUserId, mSearchQuery);

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
                    SelectedUserIds userIds = new SelectedUserIds(isTimelineCombined(), getSelectedUserId());
                    // Reblogs are included also
                    mSa.addSelection(MyDatabase.Msg.AUTHOR_ID + " " + userIds.getSql()
                            + " OR "
                            + MyDatabase.Msg.SENDER_ID + " " + userIds.getSql()
                            + " OR " 
                            + "("
                            + User.LINKED_USER_ID + " " + userIds.getSql()
                            + " AND "
                            + MyDatabase.MsgOfUser.REBLOGGED + " = 1"
                            + ")",
                            null);
                    break;
                default:
                    break;
            }
        }

        if (minSentDate > 0) {
            mSa.addSelection(ProjectionMap.MSG_TABLE_ALIAS + "." + MyDatabase.Msg.SENT_DATE
                            + " >= ?",
                    new String[]{
                            String.valueOf(minSentDate)
                    });
        }
        if (maxSentDate > 0) {
            mSa.addSelection(ProjectionMap.MSG_TABLE_ALIAS + "." + MyDatabase.Msg.SENT_DATE
                            + " <= ?",
                    new String[]{
                            String.valueOf(maxSentDate)
                    });
        }
    }

    public String getSortOrderAndLimit() {
        return (isSortOrderAscending() ? MyDatabase.Msg.ASC_SORT_ORDER : MyDatabase.Msg.DESC_SORT_ORDER)
                + (minSentDate > 0 && maxSentDate > 0 ? "" : " LIMIT " + PAGE_SIZE);
    }

    public boolean isSortOrderAscending() {
        return maxSentDate == 0 && minSentDate > 0;
    }

    public TimelineListParameters(Context context) {
        this.mContext = context;
    }

    public boolean isEmpty() {
        return mTimelineType == TimelineType.UNKNOWN || whichPage == WhichPage.EMPTY;
    }
    
    @Override
    public String toString() {
        return MyLog.formatKeyValue(this,
                getSummary()
                + ", myAccountUserId=" + myAccountUserId
                + (mSelectedUserId == 0 ? "" : ", selectedUserId=" + mSelectedUserId)
            //    + ", projection=" + Arrays.toString(mProjection)
                + (TextUtils.isEmpty(mSearchQuery) ? "" : ", searchQuery=" + mSearchQuery)
                + ", contentUri=" + mContentUri
                + (minSentDate > 0 ? ", minSentDate=" + new Date(minSentDate).toString() : "")
                + (maxSentDate > 0 ? ", maxSentDate=" + new Date(maxSentDate).toString() : "")
                + (mSa.isEmpty() ? "" : ", sa=" + mSa)
                + ", sortOrder=" + getSortOrderAndLimit()
                + ", startTime=" + startTime
                + (cancelled ? ", cancelled" : "")
                + (timelineToSync == TimelineType.UNKNOWN ? "" : ", timelineToSync=" + timelineToSync)
                + (mLoaderCallbacks == null ? "" : ", loaderCallbacks=" + mLoaderCallbacks)
        );
    }

    public TimelineType getTimelineType() {
        return mTimelineType;
    }

    public void setTimelineType(TimelineType timelineType) {
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
        outState.putString(IntentExtra.TIMELINE_URI.key, getTimelineUri(false).toString());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TimelineListParameters that = (TimelineListParameters) o;

        if (mTimelineCombined != that.mTimelineCombined) return false;
        if (myAccountUserId != that.myAccountUserId) return false;
        if (mSelectedUserId != that.mSelectedUserId) return false;
        if (minSentDate != that.minSentDate) return false;
        if (maxSentDate != that.maxSentDate) return false;
        if (mTimelineType != that.mTimelineType) return false;
        return !(mSearchQuery != null ? !mSearchQuery.equals(that.mSearchQuery) : that.mSearchQuery != null);

    }

    @Override
    public int hashCode() {
        int result = mTimelineType.hashCode();
        result = 31 * result + (mTimelineCombined ? 1 : 0);
        result = 31 * result + (int) (myAccountUserId ^ (myAccountUserId >>> 32));
        result = 31 * result + (int) (mSelectedUserId ^ (mSelectedUserId >>> 32));
        result = 31 * result + (mSearchQuery != null ? mSearchQuery.hashCode() : 0);
        result = 31 * result + (int) (minSentDate ^ (minSentDate >>> 32));
        result = 31 * result + (int) (maxSentDate ^ (maxSentDate >>> 32));
        return result;
    }

    boolean restoreState(SharedPreferences savedInstanceState) {
        return parseUri(Uri.parse(savedInstanceState.getString(IntentExtra.TIMELINE_URI.key,"")));
    }
    
    void parseIntentData(Intent intentNew) {
        if (!parseUri(intentNew.getData())) {
            return;
        }
        if (TextUtils.isEmpty(mSearchQuery)) {
            mSearchQuery = notNullString(intentNew.getStringExtra(SearchManager.QUERY));
        }
    }

    /** @return true if parsed successfully */
    boolean parseUri(Uri uri) {
        ParsedUri parsedUri = ParsedUri.fromUri(uri);
        setTimelineType(parsedUri.getTimelineType());
        if (getTimelineType() == TimelineType.UNKNOWN) {
            return false;
        }
        setTimelineCombined(parsedUri.isCombined());
        mSelectedUserId = parsedUri.getUserId();
        mSearchQuery = parsedUri.getSearchQuery();
        return true;
    }
    
    Uri getTimelineUri(boolean globalSearch) {
        return MatchedUri.getTimelineSearchUri(myAccountUserId, globalSearch ? TimelineType.EVERYTHING
                : getTimelineType(), isTimelineCombined(), getSelectedUserId(), mSearchQuery);
    }
    
    public static String notNullString(String string) {
        return string == null ? "" : string;
    }

    public void rememberSentDateLoaded(long sentDate) {
        if (minSentDateLoaded == 0 || minSentDateLoaded > sentDate) {
            minSentDateLoaded = sentDate;
        }
        if (maxSentDateLoaded == 0 || maxSentDateLoaded < sentDate) {
            maxSentDateLoaded = sentDate;
        }
    }

    public String getSummary() {
        return whichPage.getTitle(mContext) + " " + getTimelineType().getTitle(mContext)
                + (mTimelineCombined ? ", "
                + (mContext == null ? "combined" : mContext.getText(R.string.combined_timeline_on))
                : "");
    }
}
