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
package org.andstatus.app.data

import android.database.sqlite.SQLiteQueryBuilder
import android.net.Uri
import android.provider.BaseColumns
import org.andstatus.app.actor.GroupType
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.database.table.ActivityTable
import org.andstatus.app.database.table.ActorTable
import org.andstatus.app.database.table.AudienceTable
import org.andstatus.app.database.table.GroupMembersTable
import org.andstatus.app.database.table.NoteTable
import org.andstatus.app.net.social.Visibility
import org.andstatus.app.timeline.meta.Timeline
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.SharedPreferencesUtil
import org.andstatus.app.util.TriState
import java.util.*

object TimelineSql {
    private fun tablesForTimeline(uri: Uri?, projection: Array<String?>?, subQueryIndex: Int): String? {
        val timeline: Timeline = Timeline.Companion.fromParsedUri(MyContextHolder.Companion.myContextHolder.getNow(), ParsedUri.Companion.fromUri(uri), "")
        val actWhere = SqlWhere().append(ActivityTable.UPDATED_DATE, ">0")
        val noteWhere = SqlWhere()
        val audienceWhere = SqlWhere()
        when (timeline.timelineType) {
            TimelineType.FOLLOWERS, TimelineType.FRIENDS -> {
                // Select only the latest note from each Friend's timeline
                val activityIds = ("SELECT " + ActorTable.ACTOR_ACTIVITY_ID
                        + " FROM " + ActorTable.TABLE_NAME + " AS u1"
                        + " INNER JOIN (" + GroupMembership.Companion.selectMemberIds(SqlIds.Companion.actorIdsOfTimelineActor(timeline),
                        if (timeline.timelineType == TimelineType.FOLLOWERS) GroupType.FOLLOWERS else GroupType.FRIENDS,
                        false) + ") AS activity_ids" +
                        " ON activity_ids." + GroupMembersTable.MEMBER_ID + "=u1." + BaseColumns._ID)
                actWhere.append(BaseColumns._ID + " IN (" + activityIds + ")")
            }
            TimelineType.HOME -> {
                actWhere.append(ActivityTable.SUBSCRIBED + "=" + TriState.TRUE.id)
                        .append(ActivityTable.ACCOUNT_ID, SqlIds.Companion.actorIdsOfTimelineAccount(timeline))
                noteWhere.append(ProjectionMap.NOTE_TABLE_ALIAS + "." + NoteTable.VISIBILITY, "!=" + Visibility.PRIVATE.id)
            }
            TimelineType.PRIVATE -> {
                actWhere.append(ActivityTable.ACCOUNT_ID, SqlIds.Companion.actorIdsOfTimelineAccount(timeline))
                noteWhere.append(ProjectionMap.NOTE_TABLE_ALIAS + "." + NoteTable.VISIBILITY, "=" + Visibility.PRIVATE.id)
            }
            TimelineType.FAVORITES -> {
                actWhere.append(ActivityTable.ACTOR_ID, SqlIds.Companion.actorIdsOfTimelineActor(timeline))
                noteWhere.append(ProjectionMap.NOTE_TABLE_ALIAS + "." + NoteTable.FAVORITED, "=" + TriState.TRUE.id)
            }
            TimelineType.INTERACTIONS -> actWhere.append(ActivityTable.INTERACTED, "=" + TriState.TRUE.id)
                    .append(ActivityTable.NOTIFIED_ACTOR_ID, SqlIds.Companion.notifiedActorIdsOfTimeline(timeline))
            TimelineType.PUBLIC -> noteWhere.append(ProjectionMap.NOTE_TABLE_ALIAS + "." + NoteTable.VISIBILITY, "<" + Visibility.PRIVATE.id)
            TimelineType.DRAFTS -> {
                actWhere.append(ActivityTable.ACTOR_ID, SqlIds.Companion.actorIdsOfTimelineActor(timeline))
                noteWhere.append(ProjectionMap.NOTE_TABLE_ALIAS + "." + NoteTable.NOTE_STATUS, "=" + DownloadStatus.DRAFT.save())
            }
            TimelineType.OUTBOX -> {
                actWhere.append(ActivityTable.ACTOR_ID, SqlIds.Companion.actorIdsOfTimelineActor(timeline))
                noteWhere.append(ProjectionMap.NOTE_TABLE_ALIAS + "." + NoteTable.NOTE_STATUS, "=" + DownloadStatus.SENDING.save())
            }
            TimelineType.SENT -> if (subQueryIndex == 0) {
                actWhere.append(ActivityTable.ACTOR_ID, SqlIds.Companion.actorIdsOfTimelineActor(timeline))
            } else {
                noteWhere.append(ProjectionMap.NOTE_TABLE_ALIAS + "." + NoteTable.AUTHOR_ID, SqlIds.Companion.actorIdsOfTimelineActor(timeline))
            }
            TimelineType.GROUP -> audienceWhere.append(AudienceTable.TABLE_NAME + "." + AudienceTable.ACTOR_ID,
                    SqlIds.Companion.actorIdsOfTimelineActor(timeline))
            TimelineType.UNREAD_NOTIFICATIONS -> actWhere.append(ActivityTable.NOTIFIED, "=" + TriState.TRUE.id)
                    .append(ActivityTable.NEW_NOTIFICATION_EVENT, "!=0")
                    .append(ActivityTable.NOTIFIED_ACTOR_ID, SqlIds.Companion.notifiedActorIdsOfTimeline(timeline))
            TimelineType.NOTIFICATIONS -> actWhere.append(ActivityTable.NOTIFIED, "=" + TriState.TRUE.id)
                    .append(ActivityTable.NOTIFIED_ACTOR_ID, SqlIds.Companion.notifiedActorIdsOfTimeline(timeline))
            else -> {
            }
        }
        if (timeline.timelineType.isAtOrigin && !timeline.isCombined) {
            actWhere.append(ActivityTable.ORIGIN_ID, "=" + timeline.origin.id)
        }
        var tables = ("(SELECT * FROM " + ActivityTable.TABLE_NAME + actWhere.where
                + ") AS " + ProjectionMap.ACTIVITY_TABLE_ALIAS
                + (if (noteWhere.isEmpty) " LEFT" else " INNER") + " JOIN "
                + NoteTable.TABLE_NAME + " AS " + ProjectionMap.NOTE_TABLE_ALIAS
                + " ON (" + ProjectionMap.NOTE_TABLE_ALIAS + "." + BaseColumns._ID + "="
                + ProjectionMap.ACTIVITY_TABLE_ALIAS + "." + ActivityTable.NOTE_ID
                + noteWhere.andWhere + ")")
        if (audienceWhere.nonEmpty()) {
            tables = (tables + " INNER JOIN " + AudienceTable.TABLE_NAME + " ON (" +
                    ProjectionMap.NOTE_TABLE_ALIAS + "." + BaseColumns._ID + "=" +
                    AudienceTable.TABLE_NAME + "." + AudienceTable.NOTE_ID
                    + audienceWhere.andWhere + ")")
        }
        return tables
    }

    /**
     * @param uri the same as uri for
     * [MyProvider.query]
     * @param projection Projection
     * @return Strings for [SQLiteQueryBuilder.setTables], more than one for a union query
     */
    fun tablesForTimeline(uri: Uri?, projection: Array<String?>?): MutableList<String?>? {
        val timeline: Timeline = Timeline.Companion.fromParsedUri(MyContextHolder.Companion.myContextHolder.getNow(), ParsedUri.Companion.fromUri(uri), "")
        return when (timeline.timelineType) {
            TimelineType.SENT -> Arrays.asList(
                    tablesForTimeline(uri, projection, 0),
                    tablesForTimeline(uri, projection, 1)
            )
            else -> listOf(tablesForTimeline(uri, projection, 0))
        }
    }

    fun getConversationProjection(): MutableSet<String?>? {
        val columnNames = getActivityProjection()
        columnNames.add(NoteTable.CONVERSATION_ID)
        return columnNames
    }

    /** Table columns to use for activities  */
    fun getActivityProjection(): MutableSet<String?>? {
        val columnNames = getTimelineProjection()
        columnNames.add(ActivityTable.UPDATED_DATE)
        columnNames.add(ActivityTable.ACTIVITY_TYPE)
        columnNames.add(ActivityTable.OBJ_ACTOR_ID)
        return columnNames
    }

    fun getTimelineProjection(): MutableSet<String?>? {
        val columnNames: MutableSet<String?> = HashSet()
        columnNames.add(ActivityTable.ACTIVITY_ID)
        columnNames.add(ActivityTable.ACTOR_ID)
        columnNames.add(ActivityTable.NOTE_ID)
        columnNames.add(ActivityTable.ORIGIN_ID)
        columnNames.add(NoteTable.NAME)
        columnNames.add(NoteTable.SUMMARY)
        columnNames.add(NoteTable.SENSITIVE)
        columnNames.add(NoteTable.CONTENT)
        columnNames.add(NoteTable.CONTENT_TO_SEARCH)
        columnNames.add(NoteTable.IN_REPLY_TO_NOTE_ID)
        columnNames.add(NoteTable.VISIBILITY)
        columnNames.add(NoteTable.FAVORITED)
        columnNames.add(ActivityTable.INS_DATE)
        columnNames.add(NoteTable.UPDATED_DATE)
        columnNames.add(NoteTable.NOTE_STATUS)
        columnNames.add(ActivityTable.ACCOUNT_ID)
        columnNames.add(NoteTable.AUTHOR_ID)
        columnNames.add(NoteTable.VIA)
        columnNames.add(NoteTable.LIKES_COUNT)
        columnNames.add(NoteTable.REBLOGS_COUNT)
        columnNames.add(NoteTable.REPLIES_COUNT)
        columnNames.add(NoteTable.REBLOGGED)
        if (MyPreferences.getDownloadAndDisplayAttachedImages()) {
            columnNames.add(NoteTable.ATTACHMENTS_COUNT)
        }
        if (SharedPreferencesUtil.getBoolean(MyPreferences.KEY_MARK_REPLIES_TO_ME_IN_TIMELINE, true)
                || SharedPreferencesUtil.getBoolean(
                        MyPreferences.KEY_FILTER_HIDE_REPLIES_NOT_TO_ME_OR_FRIENDS, false)) {
            columnNames.add(NoteTable.IN_REPLY_TO_ACTOR_ID)
        }
        return columnNames
    }

    fun usernameField(): String? {
        val actorInTimeline = MyPreferences.getActorInTimeline()
        return MyQuery.usernameField(actorInTimeline)
    }
}