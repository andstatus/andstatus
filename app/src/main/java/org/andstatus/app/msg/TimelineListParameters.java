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
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.andstatus.app.IntentExtra;
import org.andstatus.app.R;
import org.andstatus.app.WhichPage;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.database.DatabaseHolder;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.database.DatabaseHolder.User;
import org.andstatus.app.data.ParsedUri;
import org.andstatus.app.data.ProjectionMap;
import org.andstatus.app.data.SelectedUserIds;
import org.andstatus.app.data.TimelineSql;
import org.andstatus.app.data.TimelineType;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SelectionAndArgs;
import org.andstatus.app.util.UriUtils;

import java.util.Date;

public class TimelineListParameters {
    final Context mContext;
    
    LoaderManager.LoaderCallbacks<Cursor> mLoaderCallbacks = null;

    /**
     * Msg are being loaded into the list starting from one page. More Msg
     * are being loaded in a case User scrolls down to the end of list.
     */
    static final int PAGE_SIZE = 200;

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

    long maxSentDate = 0;

    // These params are updated just before page loading
    volatile long minSentDate = 0;
    volatile SelectionAndArgs selectionAndArgs = new SelectionAndArgs();
    volatile String sortOrderAndLimit = "";

    // Execution state / loaded data:
    volatile long startTime = 0;
    volatile long endTime = 0;
    volatile boolean cancelled = false;
    volatile TimelineType timelineToSync = TimelineType.UNKNOWN;
    volatile int rowsLoaded = 0;
    volatile long minSentDateLoaded = 0;
    volatile long maxSentDateLoaded = 0;
    volatile String selectedUserWebFingerId = "";

    public static TimelineListParameters clone(TimelineListParameters prev, WhichPage whichPage) {
        TimelineListParameters params = new TimelineListParameters(prev.mContext);
        params.whichPage = whichPage == WhichPage.ANY ? prev.whichPage : whichPage;
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

        String msgLog = "Constructing " + params.toSummary();
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
            default:
                break;
        }
        MyLog.v(TimelineListParameters.class, msgLog);

        params.mProjection = TimelineSql.getTimelineProjection();
    }

    public String toAccountButtonText() {
        return toAccountButtonText(myAccountUserId);
    }

    public static String toAccountButtonText(long myAccountUserId) {
        MyAccount ma = MyContextHolder.get().persistentAccounts().fromUserId(myAccountUserId);
        String accountButtonText = ma.shortestUniqueAccountName();
        if (!ma.isValidAndSucceeded()) {
            accountButtonText = "(" + accountButtonText + ")";
        }
        return accountButtonText;
    }

    public boolean isLoaded() {
        return endTime > 0;
    }

    public boolean mayHaveYoungerPage() {
        return maxSentDate > 0 ||
                (minSentDate > 0 && rowsLoaded > 0 && minSentDate < maxSentDateLoaded);
    }

    public boolean mayHaveOlderPage() {
        return whichPage.equals(WhichPage.CURRENT) ||
                minSentDate > 0 ||
                (maxSentDate > 0 && rowsLoaded > 0 && maxSentDate > minSentDateLoaded);
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

    public boolean isAtHome() {
        return getTimelineType().equals(MyPreferences.getDefaultTimeline())
                && isTimelineCombined() == MyPreferences.isTimelineCombinedByDefault()
                && getMyAccountUserId() ==
                MyContextHolder.get().persistentAccounts().getDefaultAccountUserId()
                && mSelectedUserId == 0
                && TextUtils.isEmpty(mSearchQuery);
    }

    public void setAtHome() {
        setTimelineType(MyPreferences.getDefaultTimeline());
        setTimelineCombined(MyPreferences.isTimelineCombinedByDefault());
        myAccountUserId = MyContextHolder.get().persistentAccounts().getDefaultAccountUserId();
        mSelectedUserId = 0;
        mSearchQuery = "";
    }

    public void switchTimelineType(TimelineType timelineType) {
        mTimelineType = timelineType;
        mSelectedUserId = 0;
        mSearchQuery = "";
    }

    @Override
    public String toString() {
        return MyLog.formatKeyValue(this,
                toSummary()
                + ", myAccountUserId=" + myAccountUserId
                + (mSelectedUserId == 0 ? "" : ", selectedUserId=" + mSelectedUserId)
            //    + ", projection=" + Arrays.toString(mProjection)
                + (minSentDate > 0 ? ", minSentDate=" + new Date(minSentDate).toString() : "")
                + (maxSentDate > 0 ? ", maxSentDate=" + new Date(maxSentDate).toString() : "")
                + (selectionAndArgs.isEmpty() ? "" : ", sa=" + selectionAndArgs)
                + (TextUtils.isEmpty(sortOrderAndLimit) ? "" : ", sortOrder=" + sortOrderAndLimit)
                + (startTime > 0 ? ", startTime=" + startTime : "")
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

    public void saveState(Bundle outState) {
        outState.putString(IntentExtra.TIMELINE_URI.key, toTimelineUri(false).toString());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TimelineListParameters that = (TimelineListParameters) o;

        if (mTimelineCombined != that.mTimelineCombined) return false;
        if (myAccountUserId != that.myAccountUserId) return false;
        if (mSelectedUserId != that.mSelectedUserId) return false;
        if (!whichPage.equals(WhichPage.CURRENT) && !that.whichPage.equals(WhichPage.CURRENT)) {
            if (minSentDate != that.minSentDate) return false;
        }
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
        if (whichPage.equals(WhichPage.CURRENT)) {
            result = 31 * result + (-1 ^ (-1 >>> 32));
        } else {
            result = 31 * result + (int) (minSentDate ^ (minSentDate >>> 32));
        }
        result = 31 * result + (int) (maxSentDate ^ (maxSentDate >>> 32));
        return result;
    }

    boolean restoreState(@NonNull Bundle savedInstanceState) {
        whichPage = WhichPage.CURRENT;
        minSentDate = 0;
        maxSentDate = 0;
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
        if (UriUtils.isEmpty(uri)) {
            return false;
        }
        ParsedUri parsedUri = ParsedUri.fromUri(uri);
        setTimelineType(parsedUri.getTimelineType());
        if (getTimelineType() == TimelineType.UNKNOWN ||
                parsedUri.getAccountUserId() == 0) {
            MyLog.e(this,"parseUri; uri:" + uri
                    + ", " + getTimelineType()
                    + ", accountId:" + parsedUri.getAccountUserId() );
            return false;
        }
        setTimelineCombined(parsedUri.isCombined());
        myAccountUserId = parsedUri.getAccountUserId();
        mSelectedUserId = parsedUri.getUserId();
        mSearchQuery = parsedUri.getSearchQuery();
        return true;
    }
    
    Uri toTimelineUri(boolean globalSearch) {
        return MatchedUri.getTimelineSearchUri(myAccountUserId, globalSearch ? TimelineType.EVERYTHING
                : getTimelineType(), isTimelineCombined(), getSelectedUserId(), mSearchQuery);
    }

    public String toSummary() {
        return whichPage.getTitle(mContext) + " " + toTimelineTitleAndSubtitle();
    }

    public String toTimelineTitleAndSubtitle() {
        return toTimelineTitleAndSubtitle("");
    }

    public String toTimelineTitleAndSubtitle(String additionalTitleText) {
        return toTimelineTitle() + "; " + toTimelineSubtitle(additionalTitleText);
    }

    public String toTimelineTitle() {
        StringBuilder title = new StringBuilder();
        I18n.appendWithSpace(title, getTimelineType().getTitle(mContext));
        if (!TextUtils.isEmpty(mSearchQuery)) {
            I18n.appendWithSpace(title, "'" + mSearchQuery + "'");
        }
        if (getTimelineType() == TimelineType.USER
                && !(isTimelineCombined()
                && MyContextHolder.get().persistentAccounts()
                .fromUserId(getSelectedUserId()).isValid())) {
            I18n.appendWithSpace(title, selectedUserWebFingerId);
        }
        if (isTimelineCombined()) {
            I18n.appendWithSpace(title,
                    mContext == null ? "combined" : mContext.getText(R.string.combined_timeline_on));
        }
        return title.toString();
    }

    public String toTimelineSubtitle(String additionalTitleText) {
        final StringBuilder subTitle = new StringBuilder();
        if (!isTimelineCombined()) {
            I18n.appendWithSpace(subTitle, getTimelineType()
                    .getPrepositionForNotCombinedTimeline(mContext));
            if (getTimelineType().atOrigin()) {
                I18n.appendWithSpace(subTitle, MyContextHolder.get().persistentAccounts()
                        .fromUserId(getMyAccountUserId()).getOrigin().getName()
                        + ";");
            }
        }
        I18n.appendWithSpace(subTitle, toAccountButtonText());
        I18n.appendWithSpace(subTitle, additionalTitleText);
        return subTitle.toString();
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

    private void prepareQueryParameters() {
        switch (whichPage) {
            case CURRENT:
                minSentDate = (new TimelineListPositionStorage(null, null, this)).getTLPosition().minSentDate;
                break;
            default:
                break;
        }
        sortOrderAndLimit = buildSortOrderAndLimit();
        selectionAndArgs = buildSelectionAndArgs();
    }

    private String buildSortOrderAndLimit() {
        return (isSortOrderAscending() ? DatabaseHolder.Msg.ASC_SORT_ORDER : DatabaseHolder.Msg.DESC_SORT_ORDER)
                + (minSentDate > 0 && maxSentDate > 0 ? "" : " LIMIT " + PAGE_SIZE);
    }

    private SelectionAndArgs buildSelectionAndArgs() {
        SelectionAndArgs sa = new SelectionAndArgs();

        // TODO: Move these selections to the {@link MyProvider} ?!
        switch (getTimelineType()) {
            case HOME:
                // In the Home of the combined timeline we see ALL loaded
                // messages, even those that we downloaded
                // not as Home timeline of any Account
                if (!isTimelineCombined()) {
                    sa.addSelection(DatabaseHolder.MsgOfUser.SUBSCRIBED + " = ?", new String[] {
                            "1"
                    });
                }
                break;
            case MENTIONS:
                sa.addSelection(DatabaseHolder.MsgOfUser.MENTIONED + " = ?", new String[] {
                        "1"
                });
                /*
                 * We already figured this out and set {@link MyDatabase.MsgOfUser.MENTIONED}:
                 * sa.addSelection(MyDatabase.Msg.BODY + " LIKE ?" ...
                 */
                break;
            case FAVORITES:
                sa.addSelection(DatabaseHolder.MsgOfUser.FAVORITED + " = ?", new String[] {
                        "1"
                });
                break;
            case DIRECT:
                sa.addSelection(DatabaseHolder.MsgOfUser.DIRECTED + " = ?", new String[] {
                        "1"
                });
                break;
            case USER:
                SelectedUserIds userIds = new SelectedUserIds(isTimelineCombined(), getSelectedUserId());
                // Reblogs are included also
                sa.addSelection(DatabaseHolder.Msg.AUTHOR_ID + " " + userIds.getSql()
                                + " OR "
                                + DatabaseHolder.Msg.SENDER_ID + " " + userIds.getSql()
                                + " OR "
                                + "("
                                + User.LINKED_USER_ID + " " + userIds.getSql()
                                + " AND "
                                + DatabaseHolder.MsgOfUser.REBLOGGED + " = 1"
                                + ")",
                        null);
                break;
            default:
                break;
        }

        if (minSentDate > 0) {
            sa.addSelection(ProjectionMap.MSG_TABLE_ALIAS + "." + DatabaseHolder.Msg.SENT_DATE
                            + " >= ?",
                    new String[]{
                            String.valueOf(minSentDate)
                    });
        }
        if (maxSentDate > 0) {
            sa.addSelection(ProjectionMap.MSG_TABLE_ALIAS + "." + DatabaseHolder.Msg.SENT_DATE
                            + " <= ?",
                    new String[]{
                            String.valueOf(maxSentDate)
                    });
        }
        return sa;
    }

    Cursor queryDatabase() {
        prepareQueryParameters();
        return mContext.getContentResolver().query(getContentUri(), mProjection,
                selectionAndArgs.selection, selectionAndArgs.selectionArgs, sortOrderAndLimit);
    }

    public Uri getContentUri() {
        return MatchedUri.getTimelineSearchUri(myAccountUserId, mTimelineType,
                mTimelineCombined, mSelectedUserId, mSearchQuery);
    }

}
