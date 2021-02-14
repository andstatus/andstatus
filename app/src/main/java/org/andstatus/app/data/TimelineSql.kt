/*
 * Copyright (C) 2014-2018 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.data;

import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.BaseColumns;

import org.andstatus.app.actor.GroupType;
import org.andstatus.app.context.ActorInTimeline;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.database.table.ActorTable;
import org.andstatus.app.database.table.AudienceTable;
import org.andstatus.app.database.table.GroupMembersTable;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.net.social.Visibility;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.TriState;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.andstatus.app.context.MyContextHolder.myContextHolder;
import static org.andstatus.app.data.ProjectionMap.NOTE_TABLE_ALIAS;

public class TimelineSql {

    private TimelineSql() {
        // Empty
    }

    private static String tablesForTimeline(Uri uri, String[] projection, int subQueryIndex) {
        Timeline timeline = Timeline.fromParsedUri(myContextHolder.getNow(), ParsedUri.fromUri(uri), "");
        SqlWhere actWhere = new SqlWhere().append(ActivityTable.UPDATED_DATE, ">0");
        SqlWhere noteWhere = new SqlWhere();
        SqlWhere audienceWhere = new SqlWhere();

        switch (timeline.getTimelineType()) {
            case FOLLOWERS:
            case FRIENDS:
                // Select only the latest note from each Friend's timeline
                String activityIds = "SELECT " + ActorTable.ACTOR_ACTIVITY_ID
                        + " FROM " + ActorTable.TABLE_NAME + " AS u1"
                        + " INNER JOIN (" + GroupMembership.selectMemberIds(SqlIds.actorIdsOfTimelineActor(timeline),
                        timeline.getTimelineType() == TimelineType.FOLLOWERS ? GroupType.FOLLOWERS : GroupType.FRIENDS,
                        false) + ") AS activity_ids" +
                        " ON activity_ids." + GroupMembersTable.MEMBER_ID + "=u1." + BaseColumns._ID;
                actWhere.append(BaseColumns._ID + " IN (" + activityIds + ")");
                break;
            case HOME:
                actWhere.append(ActivityTable.SUBSCRIBED + "=" + TriState.TRUE.id)
                        .append(ActivityTable.ACCOUNT_ID, SqlIds.actorIdsOfTimelineAccount(timeline));
                noteWhere.append(NOTE_TABLE_ALIAS + "." + NoteTable.VISIBILITY, "!=" + Visibility.PRIVATE.id);
                break;
            case PRIVATE:
                actWhere.append(ActivityTable.ACCOUNT_ID, SqlIds.actorIdsOfTimelineAccount(timeline));
                noteWhere.append(NOTE_TABLE_ALIAS + "." + NoteTable.VISIBILITY, "=" + Visibility.PRIVATE.id);
                break;
            case FAVORITES:
                actWhere.append(ActivityTable.ACTOR_ID, SqlIds.actorIdsOfTimelineActor(timeline));
                noteWhere.append(NOTE_TABLE_ALIAS + "." + NoteTable.FAVORITED, "=" + TriState.TRUE.id);
                break;
            case INTERACTIONS:
                actWhere.append(ActivityTable.INTERACTED, "=" + TriState.TRUE.id)
                        .append(ActivityTable.NOTIFIED_ACTOR_ID, SqlIds.notifiedActorIdsOfTimeline(timeline));
                break;
            case PUBLIC:
                noteWhere.append(NOTE_TABLE_ALIAS + "." + NoteTable.VISIBILITY, "<" + Visibility.PRIVATE.id);
                break;
            case DRAFTS:
                actWhere.append(ActivityTable.ACTOR_ID, SqlIds.actorIdsOfTimelineActor(timeline));
                noteWhere.append(NOTE_TABLE_ALIAS + "." + NoteTable.NOTE_STATUS, "=" + DownloadStatus.DRAFT.save());
                break;
            case OUTBOX:
                actWhere.append(ActivityTable.ACTOR_ID, SqlIds.actorIdsOfTimelineActor(timeline));
                noteWhere.append(NOTE_TABLE_ALIAS + "." + NoteTable.NOTE_STATUS, "=" + DownloadStatus.SENDING.save());
                break;
            case SENT:
                if (subQueryIndex == 0) {
                    actWhere.append(ActivityTable.ACTOR_ID, SqlIds.actorIdsOfTimelineActor(timeline));
                } else {
                    noteWhere.append(NOTE_TABLE_ALIAS + "." + NoteTable.AUTHOR_ID, SqlIds.actorIdsOfTimelineActor(timeline));
                }
                break;
            case GROUP:
                audienceWhere.append(AudienceTable.TABLE_NAME + "." + AudienceTable.ACTOR_ID,
                        SqlIds.actorIdsOfTimelineActor(timeline));
                break;
            case UNREAD_NOTIFICATIONS:
                actWhere.append(ActivityTable.NOTIFIED, "=" + TriState.TRUE.id)
                        .append(ActivityTable.NEW_NOTIFICATION_EVENT, "!=0")
                        .append(ActivityTable.NOTIFIED_ACTOR_ID, SqlIds.notifiedActorIdsOfTimeline(timeline));
                break;
            case NOTIFICATIONS:
                actWhere.append(ActivityTable.NOTIFIED, "=" + TriState.TRUE.id)
                        .append(ActivityTable.NOTIFIED_ACTOR_ID, SqlIds.notifiedActorIdsOfTimeline(timeline));
                break;
            default:
                break;
        }

        if (timeline.getTimelineType().isAtOrigin() && !timeline.isCombined()) {
            actWhere.append(ActivityTable.ORIGIN_ID, "=" + timeline.getOrigin().getId());
        }
        String  tables = "(SELECT * FROM " + ActivityTable.TABLE_NAME + actWhere.getWhere()
                + ") AS " + ProjectionMap.ACTIVITY_TABLE_ALIAS
                + (noteWhere.isEmpty() ? " LEFT" : " INNER") + " JOIN "
                + NoteTable.TABLE_NAME + " AS " + NOTE_TABLE_ALIAS
                + " ON (" + NOTE_TABLE_ALIAS + "." + BaseColumns._ID + "="
                    + ProjectionMap.ACTIVITY_TABLE_ALIAS + "." + ActivityTable.NOTE_ID
                    + noteWhere.getAndWhere() + ")";
        if (audienceWhere.nonEmpty()) {
            tables = tables + " INNER JOIN " + AudienceTable.TABLE_NAME + " ON (" +
                    NOTE_TABLE_ALIAS + "." + BaseColumns._ID + "=" +
                    AudienceTable.TABLE_NAME + "." + AudienceTable.NOTE_ID
                    + audienceWhere.getAndWhere() + ")";
        }
        return tables;
    }

    /**
     * @param uri the same as uri for
     *            {@link MyProvider#query(Uri, String[], String, String[], String)}
     * @param projection Projection
     * @return Strings for {@link SQLiteQueryBuilder#setTables(String)}, more than one for a union query
     */
    static List<String> tablesForTimeline(Uri uri, String[] projection) {
        Timeline timeline = Timeline.fromParsedUri(myContextHolder.getNow(), ParsedUri.fromUri(uri), "");
        switch (timeline.getTimelineType()) {
            case SENT:
                return Arrays.asList(
                        tablesForTimeline(uri, projection, 0),
                        tablesForTimeline(uri, projection, 1)
                );
            default:
                return Collections.singletonList(tablesForTimeline(uri, projection, 0));
        }
    }

    public static Set<String> getConversationProjection() {
        Set<String> columnNames = getActivityProjection();
        columnNames.add(NoteTable.CONVERSATION_ID);
        return columnNames;
    }

    /** Table columns to use for activities */
    public static Set<String> getActivityProjection() {
        Set<String> columnNames = getTimelineProjection();
        columnNames.add(ActivityTable.UPDATED_DATE);
        columnNames.add(ActivityTable.ACTIVITY_TYPE);
        columnNames.add(ActivityTable.OBJ_ACTOR_ID);
        return columnNames;
    }

    public static Set<String> getTimelineProjection() {
        Set<String> columnNames = new HashSet<>();
        columnNames.add(ActivityTable.ACTIVITY_ID);
        columnNames.add(ActivityTable.ACTOR_ID);
        columnNames.add(ActivityTable.NOTE_ID);
        columnNames.add(ActivityTable.ORIGIN_ID);
        columnNames.add(NoteTable.NAME);
        columnNames.add(NoteTable.SUMMARY);
        columnNames.add(NoteTable.SENSITIVE);
        columnNames.add(NoteTable.CONTENT);
        columnNames.add(NoteTable.CONTENT_TO_SEARCH);
        columnNames.add(NoteTable.IN_REPLY_TO_NOTE_ID);
        columnNames.add(NoteTable.VISIBILITY);
        columnNames.add(NoteTable.FAVORITED);
        columnNames.add(ActivityTable.INS_DATE);
        columnNames.add(NoteTable.UPDATED_DATE);
        columnNames.add(NoteTable.NOTE_STATUS);
        columnNames.add(ActivityTable.ACCOUNT_ID);
        columnNames.add(NoteTable.AUTHOR_ID);
        columnNames.add(NoteTable.VIA);
        columnNames.add(NoteTable.LIKES_COUNT);
        columnNames.add(NoteTable.REBLOGS_COUNT);
        columnNames.add(NoteTable.REPLIES_COUNT);
        columnNames.add(NoteTable.REBLOGGED);
        if (MyPreferences.getDownloadAndDisplayAttachedImages()) {
            columnNames.add(NoteTable.ATTACHMENTS_COUNT);
        }
        if (SharedPreferencesUtil.getBoolean(MyPreferences.KEY_MARK_REPLIES_TO_ME_IN_TIMELINE, true)
                || SharedPreferencesUtil.getBoolean(
                MyPreferences.KEY_FILTER_HIDE_REPLIES_NOT_TO_ME_OR_FRIENDS, false)) {
            columnNames.add(NoteTable.IN_REPLY_TO_ACTOR_ID);
        }
        return columnNames;
    }

    public static String usernameField() {
        ActorInTimeline actorInTimeline = MyPreferences.getActorInTimeline();
        return MyQuery.usernameField(actorInTimeline);
    }
    
}
