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
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.database.UserTable;
import org.andstatus.app.data.ParsedUri;
import org.andstatus.app.data.ProjectionMap;
import org.andstatus.app.data.SelectedUserIds;
import org.andstatus.app.data.TimelineSql;
import org.andstatus.app.data.TimelineType;
import org.andstatus.app.database.MsgOfUserTable;
import org.andstatus.app.database.MsgTable;
import org.andstatus.app.util.I18n;
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
    static final int PAGE_SIZE = 200;
    Timeline timeline = Timeline.getEmpty(MyAccount.getEmpty());
    /** Combined Timeline shows messages from all accounts/origins */
    boolean mTimelineCombined = false;

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
        params.timeline = prev.getTimeline();
        params.mTimelineCombined = prev.isTimelineCombined();

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
        return toAccountButtonText(timeline.getAccount());
    }

    public static String toAccountButtonText(MyAccount ma) {
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
        return timeline.isEmpty() || whichPage == WhichPage.EMPTY;
    }

    public boolean isAtHome() {
        return  timeline.equals(MyContextHolder.get().persistentTimelines().getDefaultForCurrentAccount())
                && isTimelineCombined() == MyPreferences.isTimelineCombinedByDefault();
    }

    public void setAtHome() {
        timeline = MyContextHolder.get().persistentTimelines().getDefaultForCurrentAccount();
        setTimelineCombined(MyPreferences.isTimelineCombinedByDefault());
    }

    public void switchTimeline(Timeline timeline) {
        this.timeline = timeline;
    }

    @Override
    public String toString() {
        return MyLog.formatKeyValue(this,
                toSummary()
                + ", account=" + timeline.getAccount().getAccountName()
                + (timeline.getUserId() == 0 ? "" : ", selectedUserId=" + timeline.getUserId())
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

    public Timeline getTimeline() {
        return timeline;
    }

    public void setTimeline(Timeline timeline) {
        this.timeline = timeline;
    }

    public TimelineType getTimelineType() {
        return timeline.getTimelineType();
    }

    public void setTimelineType(TimelineType timelineType) {

    }
    
    public long getSelectedUserId() {
        return timeline.getUserId();
    }

    public boolean isTimelineCombined() {
        return mTimelineCombined;
    }

    public void setTimelineCombined(boolean isTimelineCombined) {
        mTimelineCombined = isTimelineCombined;
    }

    public boolean hasSearchQuery() {
        return !TextUtils.isEmpty(timeline.getSearchQuery());
    }

    public void saveState(Bundle outState) {
        outState.putString(IntentExtra.TIMELINE_URI.key, toTimelineUri(false).toString());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TimelineListParameters that = (TimelineListParameters) o;

        if (!timeline.equals(that.timeline)) return false;
        if (mTimelineCombined != that.mTimelineCombined) return false;
        if (!whichPage.equals(WhichPage.CURRENT) && !that.whichPage.equals(WhichPage.CURRENT)) {
            if (minSentDate != that.minSentDate) return false;
        }
        return maxSentDate == that.maxSentDate;
    }

    @Override
    public int hashCode() {
        int result = timeline.hashCode();
        result = 31 * result + (mTimelineCombined ? 1 : 0);
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
        return parseUri(Uri.parse(savedInstanceState.getString(IntentExtra.TIMELINE_URI.key,"")), "");
    }
    
    void parseIntentData(Intent intentNew) {
        parseUri(intentNew.getData(), notNullString(intentNew.getStringExtra(SearchManager.QUERY)));
    }

    /** @return true if parsed successfully */
    boolean parseUri(Uri uri, String searchQuery) {
        ParsedUri parsedUri = ParsedUri.fromUri(uri);
        timeline = MyContextHolder.get().persistentTimelines().fromParsedUri(parsedUri, searchQuery);
        setTimelineCombined(parsedUri.isCombined());
        return !timeline.isEmpty();
    }
    
    Uri toTimelineUri(boolean globalSearch) {
        return MatchedUri.getTimelineSearchUri(timeline.getAccount().getUserId(),
                globalSearch ? TimelineType.EVERYTHING : getTimelineType(),
                isTimelineCombined(), timeline.getUserId(), timeline.getSearchQuery());
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
        if (hasSearchQuery()) {
            I18n.appendWithSpace(title, "'" + timeline.getSearchQuery() + "'");
        }
        if (getTimelineType() == TimelineType.USER
                && !(isTimelineCombined()
                && MyContextHolder.get().persistentAccounts()
                .fromUserId(timeline.getUserId()).isValid())) {
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
            if (getTimelineType().isAtOrigin()) {
                I18n.appendWithSpace(subTitle, getMyAccount().getOrigin().getName()
                        + ";");
            }
        }
        I18n.appendWithSpace(subTitle, toAccountButtonText());
        I18n.appendWithSpace(subTitle, additionalTitleText);
        return subTitle.toString();
    }

    @NonNull
    public MyAccount getMyAccount() {
        return timeline.getAccount();
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
        return (isSortOrderAscending() ? MsgTable.ASC_SORT_ORDER : MsgTable.DESC_SORT_ORDER)
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
                    sa.addSelection(MsgOfUserTable.SUBSCRIBED + " = ?", new String[] {
                            "1"
                    });
                }
                break;
            case MENTIONS:
                sa.addSelection(MsgOfUserTable.MENTIONED + " = ?", new String[] {
                        "1"
                });
                /*
                 * We already figured this out and set {@link MyDatabase.MsgOfUser.MENTIONED}:
                 * sa.addSelection(MyDatabase.Msg.BODY + " LIKE ?" ...
                 */
                break;
            case FAVORITES:
                sa.addSelection(MsgOfUserTable.FAVORITED + " = ?", new String[] {
                        "1"
                });
                break;
            case DIRECT:
                sa.addSelection(MsgOfUserTable.DIRECTED + " = ?", new String[] {
                        "1"
                });
                break;
            case USER:
                SelectedUserIds userIds = new SelectedUserIds(isTimelineCombined(), timeline.getUserId());
                // Reblogs are included also
                sa.addSelection(MsgTable.AUTHOR_ID + " " + userIds.getSql()
                                + " OR "
                                + MsgTable.SENDER_ID + " " + userIds.getSql()
                                + " OR "
                                + "("
                                + UserTable.LINKED_USER_ID + " " + userIds.getSql()
                                + " AND "
                                + MsgOfUserTable.REBLOGGED + " = 1"
                                + ")",
                        null);
                break;
            default:
                break;
        }

        if (minSentDate > 0) {
            sa.addSelection(ProjectionMap.MSG_TABLE_ALIAS + "." + MsgTable.SENT_DATE
                            + " >= ?",
                    new String[]{
                            String.valueOf(minSentDate)
                    });
        }
        if (maxSentDate > 0) {
            sa.addSelection(ProjectionMap.MSG_TABLE_ALIAS + "." + MsgTable.SENT_DATE
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
        return MatchedUri.getTimelineSearchUri(timeline.getAccount().getUserId(),
                timeline.getTimelineType(),
                mTimelineCombined, timeline.getUserId(), timeline.getSearchQuery());
    }
}
