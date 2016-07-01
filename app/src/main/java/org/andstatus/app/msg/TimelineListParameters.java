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
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.andstatus.app.IntentExtra;
import org.andstatus.app.WhichPage;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.data.ParsedUri;
import org.andstatus.app.data.ProjectionMap;
import org.andstatus.app.data.SelectedUserIds;
import org.andstatus.app.data.TimelineSql;
import org.andstatus.app.database.MsgOfUserTable;
import org.andstatus.app.database.MsgTable;
import org.andstatus.app.database.UserTable;
import org.andstatus.app.timeline.Timeline;
import org.andstatus.app.timeline.TimelineTitle;
import org.andstatus.app.timeline.TimelineType;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SelectionAndArgs;

import java.util.Date;

public class TimelineListParameters {
    private final MyContext myContext;

    LoaderManager.LoaderCallbacks<Cursor> mLoaderCallbacks = null;

    /**
     * Msg are being loaded into the list starting from one page. More Msg
     * are being loaded in a case User scrolls down to the end of list.
     */
    static final int PAGE_SIZE = 200;
    Timeline timeline = Timeline.getEmpty(MyAccount.getEmpty());

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
    volatile Timeline timelineToSync = Timeline.getEmpty(MyAccount.getEmpty());
    volatile int rowsLoaded = 0;
    volatile long minSentDateLoaded = 0;
    volatile long maxSentDateLoaded = 0;

    public static TimelineListParameters clone(TimelineListParameters prev, WhichPage whichPage) {
        TimelineListParameters params = new TimelineListParameters(prev.myContext);
        params.whichPage = whichPage == WhichPage.ANY ? prev.whichPage : whichPage;
        if (whichPage != WhichPage.EMPTY) {
            enrichNonEmptyParameters(params, prev);
        }
        return params;
    }

    private static void enrichNonEmptyParameters(TimelineListParameters params, TimelineListParameters prev) {
        params.mLoaderCallbacks = prev.mLoaderCallbacks;
        params.timeline = prev.getTimeline();

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

    public TimelineListParameters(MyContext myContext) {
        this.myContext = myContext;
    }

    public boolean isEmpty() {
        return timeline.isEmpty() || whichPage == WhichPage.EMPTY;
    }

    public boolean isAtHome() {
        return  timeline.equals(myContext.persistentTimelines().getHome());
    }

    @Override
    public String toString() {
        return MyLog.formatKeyValue(this,
                toSummary()
                + ", account=" + timeline.getMyAccount().getAccountName()
                + (timeline.getUserId() == 0 ? "" : ", selectedUserId=" + timeline.getUserId())
            //    + ", projection=" + Arrays.toString(mProjection)
                + (minSentDate > 0 ? ", minSentDate=" + new Date(minSentDate).toString() : "")
                + (maxSentDate > 0 ? ", maxSentDate=" + new Date(maxSentDate).toString() : "")
                + (selectionAndArgs.isEmpty() ? "" : ", sa=" + selectionAndArgs)
                + (TextUtils.isEmpty(sortOrderAndLimit) ? "" : ", sortOrder=" + sortOrderAndLimit)
                + (startTime > 0 ? ", startTime=" + startTime : "")
                + (cancelled ? ", cancelled" : "")
                + (timelineToSync.isEmpty() ? "" : ", toSync=" + timelineToSync)
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

    public long getSelectedUserId() {
        return timeline.getUserId();
    }

    public boolean isTimelineCombined() {
        return timeline.isCombined();
    }

    public void saveState(Bundle outState) {
        outState.putString(IntentExtra.TIMELINE_URI.key, MatchedUri.getTimelineUri(timeline).toString());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TimelineListParameters that = (TimelineListParameters) o;

        if (!timeline.equals(that.timeline)) return false;
        if (!whichPage.equals(WhichPage.CURRENT) && !that.whichPage.equals(WhichPage.CURRENT)) {
            if (minSentDate != that.minSentDate) return false;
        }
        return maxSentDate == that.maxSentDate;
    }

    @Override
    public int hashCode() {
        int result = timeline.hashCode();
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
    
    /** @return true if parsed successfully */
    boolean parseUri(Uri uri, String searchQuery) {
        ParsedUri parsedUri = ParsedUri.fromUri(uri);
        timeline = Timeline.fromParsedUri(myContext, parsedUri, searchQuery);
        return !timeline.isEmpty();
    }

    public String toSummary() {
        return whichPage.getTitle(myContext.context()) + " " +
                TimelineTitle.load(myContext, timeline, MyAccount.getEmpty());
    }

    @NonNull
    public MyAccount getMyAccount() {
        return timeline.getMyAccount();
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
            case SENT:
                SelectedUserIds userIds = new SelectedUserIds(timeline);
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
        return myContext.context().getContentResolver().query(getContentUri(), mProjection,
                selectionAndArgs.selection, selectionAndArgs.selectionArgs, sortOrderAndLimit);
    }

    public Uri getContentUri() {
        return MatchedUri.getTimelineUri(timeline);
    }

    public MyContext getMyContext() {
        return myContext;
    }
}
