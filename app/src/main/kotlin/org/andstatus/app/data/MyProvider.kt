/* 
 * Copyright (C) 2012-2015 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteQueryBuilder
import android.net.Uri
import android.provider.BaseColumns
import org.andstatus.app.actor.ActorsScreenType
import org.andstatus.app.actor.GroupType
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyContextHolder.Companion.myContextHolder
import org.andstatus.app.database.table.ActivityTable
import org.andstatus.app.database.table.ActorEndpointTable
import org.andstatus.app.database.table.ActorTable
import org.andstatus.app.database.table.AudienceTable
import org.andstatus.app.database.table.GroupMembersTable
import org.andstatus.app.database.table.NoteTable
import org.andstatus.app.database.table.OriginTable
import org.andstatus.app.database.table.UserTable
import org.andstatus.app.note.KeywordsFilter
import org.andstatus.app.notification.NotificationEventType
import org.andstatus.app.origin.Origin
import org.andstatus.app.timeline.meta.Timeline
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.StringUtil
import org.andstatus.app.util.TriState
import java.util.*
import java.util.function.Consumer
import java.util.function.Function
import java.util.stream.Collectors

/**
 * Database provider for the MyDatabase database.
 *
 * The code of this application accesses this class through [android.content.ContentResolver].
 * ContentResolver in it's turn accesses this class.
 *
 */
class MyProvider : ContentProvider() {
    /**
     * @see android.content.ContentProvider.onCreate
     */
    override fun onCreate(): Boolean {
        myContextHolder.initialize(context, this)
        return true
    }

    /**
     * Get MIME type of the content, used for the supplied Uri
     * For discussion how this may be used see:
     * [Why use ContentProvider.getType() to get MIME type](http://stackoverflow.com/questions/5351669/why-use-contentprovider-gettype-to-get-mime-type)
     */
    override fun getType(uri: Uri): String? {
        return MatchedUri.fromUri(uri).getMimeType()
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String?>?): Int {
        throw IllegalArgumentException("Delete method is not implemented $uri")
    }

    /**
     * Insert a new record into the database.
     *
     * @see android.content.ContentProvider.insert
     */
    override fun insert(uri: Uri, initialValues: ContentValues?): Uri? {
        val values: ContentValues
        var accountActorId: Long = 0
        val rowId: Long
        var newUri: Uri? = null
        try {
            val db: SQLiteDatabase? = myContextHolder.getNow().database
            if (db == null) {
                MyLog.databaseIsNull { "insert" }
                return null
            }
            val table: String
            values = initialValues?.let { ContentValues(it) } ?: ContentValues()
            val uriParser: ParsedUri = ParsedUri.fromUri(uri)
            when (uriParser.matched()) {
                MatchedUri.NOTE_ITEM -> {
                    accountActorId = uriParser.getAccountActorId()
                    table = NoteTable.TABLE_NAME
                    if (!values.containsKey(NoteTable.CONTENT)) {
                        values.put(NoteTable.CONTENT, "")
                    }
                    if (!values.containsKey(NoteTable.VIA)) {
                        values.put(NoteTable.VIA, "")
                    }
                }
                MatchedUri.ORIGIN_ITEM -> table = OriginTable.TABLE_NAME
                MatchedUri.ACTOR_ITEM -> {
                    table = ActorTable.TABLE_NAME
                    values.put(ActorTable.INS_DATE, MyLog.uniqueCurrentTimeMS)
                    accountActorId = uriParser.getAccountActorId()
                }
                else -> throw IllegalArgumentException(uriParser.toString())
            }
            rowId = db.insert(table, null, values)
            if (rowId == -1L) {
                throw SQLException("Failed to insert row into $uri")
            }
            when (uriParser.matched()) {
                MatchedUri.NOTE_ITEM -> newUri = MatchedUri.getMsgUri(accountActorId, rowId)
                MatchedUri.ORIGIN_ITEM -> newUri = MatchedUri.getOriginUri(rowId)
                MatchedUri.ACTOR_ITEM -> newUri = MatchedUri.getActorUri(accountActorId, rowId)
                else -> {
                }
            }
        } catch (e: Exception) {
            MyLog.e(this, "Insert $uri", e)
        }
        return newUri
    }

    /**
     * Get a cursor to the database
     *
     * @see android.content.ContentProvider.query
     */
    override fun query(
        uri: Uri, projection: Array<String>?, selectionIn: String?, selectionArgsIn: Array<String>?,
        sortOrderIn: String?
    ): Cursor? {
        if (projection == null) return null

        val db: SQLiteDatabase = myContextHolder.getNow().database ?: kotlin.run {
            MyLog.databaseIsNull {}
            return null
        }

        val PAGE_SIZE = 400
        val qb = SQLiteQueryBuilder()
        var built = false
        val tables: MutableList<String>
        val where: String
        val selection: String?
        var selectionArgs: Array<String> = selectionArgsIn ?: arrayOf()
        var selectionArgs2: Array<String> = arrayOf()
        var limit: String? = null
        var sql = ""
        val uriParser: ParsedUri = ParsedUri.fromUri(uri)
        when (uriParser.matched()) {
            MatchedUri.TIMELINE -> {
                qb.isDistinct = true
                tables = TimelineSql.tablesForTimeline(uri, projection)
                qb.setProjectionMap(ProjectionMap.TIMELINE)
                selection = selectionIn
                where = ""
            }
            MatchedUri.TIMELINE_ITEM -> {
                tables = TimelineSql.tablesForTimeline(uri, projection)
                qb.setProjectionMap(ProjectionMap.TIMELINE)
                selection = selectionIn
                where = (ProjectionMap.ACTIVITY_TABLE_ALIAS + "."
                    + ActivityTable.NOTE_ID + "=" + uriParser.getNoteId())
            }
            MatchedUri.TIMELINE_SEARCH -> {
                tables = TimelineSql.tablesForTimeline(uri, projection)
                qb.setProjectionMap(ProjectionMap.TIMELINE)
                val rawQuery = uriParser.searchQuery
                if (rawQuery.isNotEmpty()) {
                    val searchQuery = KeywordsFilter(rawQuery)
                    selection = "(" + searchQuery.getSqlSelection(NoteTable.CONTENT_TO_SEARCH) + ")" +
                        if (!selectionIn.isNullOrEmpty()) " AND ($selectionIn)" else ""
                    selectionArgs = searchQuery.prependSqlSelectionArgs(selectionArgs)
                } else {
                    selection = selectionIn
                }
                where = ""
            }
            MatchedUri.ACTIVITY -> {
                tables = mutableListOf<String>(ActivityTable.TABLE_NAME + " AS " + ProjectionMap.ACTIVITY_TABLE_ALIAS)
                qb.setProjectionMap(ProjectionMap.TIMELINE)
                selection = selectionIn
                where = ""
            }
            MatchedUri.ACTOR, MatchedUri.ACTORS, MatchedUri.ACTORS_SEARCH -> {
                tables = mutableListOf<String>(ActorSql.allTables())
                qb.setProjectionMap(ActorSql.fullProjectionMap)
                val rawQuery = uriParser.searchQuery
                val actorWhere = SqlWhere().append(selectionIn ?: "")
                if (uriParser.getActorsScreenType() == ActorsScreenType.GROUPS_AT_ORIGIN) {
                    actorWhere.append(
                        ActorTable.GROUP_TYPE +
                            SqlIds.fromIds(GroupType.GENERIC.id, GroupType.ACTOR_OWNED.id).getSql()
                    )
                }
                if (rawQuery.isNotEmpty()) {
                    actorWhere.append(
                        ActorTable.WEBFINGER_ID + " LIKE ?" +
                            " OR " + ActorTable.REAL_NAME + " LIKE ?" +
                            " OR " + ActorTable.USERNAME + " LIKE ?"
                    )
                    selectionArgs = StringUtil.addBeforeArray(selectionArgs, "%$rawQuery%")
                    selectionArgs = StringUtil.addBeforeArray(selectionArgs, "%$rawQuery%")
                    selectionArgs = StringUtil.addBeforeArray(selectionArgs, "%$rawQuery%")
                }
                selection = actorWhere.getCondition()
                where = ""
                limit = PAGE_SIZE.toString()
            }
            MatchedUri.ACTORS_ITEM -> {
                tables = mutableListOf<String>(ActorSql.allTables())
                qb.setProjectionMap(ActorSql.fullProjectionMap)
                selection = selectionIn
                where = BaseColumns._ID + "=" + uriParser.getActorId()
            }
            MatchedUri.ACTOR_ITEM -> {
                tables = mutableListOf<String>(ActorTable.TABLE_NAME)
                qb.setProjectionMap(ActorSql.fullProjectionMap)
                selection = selectionIn
                where = BaseColumns._ID + "=" + uriParser.getActorId()
            }
            else -> throw IllegalArgumentException(uriParser.toString())
        }

        // If no sort order is specified use the default
        val sortOrder: String = if (sortOrderIn.isNullOrEmpty()) {
            when (uriParser.matched()) {
                MatchedUri.TIMELINE,
                MatchedUri.TIMELINE_ITEM,
                MatchedUri.TIMELINE_SEARCH ->
                    ActivityTable.getTimelineSortOrder(uriParser.getTimelineType(), false)
                MatchedUri.ACTOR,
                MatchedUri.ACTORS,
                MatchedUri.ACTORS_ITEM,
                MatchedUri.ACTORS_SEARCH,
                MatchedUri.ACTOR_ITEM -> ActorTable.DEFAULT_SORT_ORDER
                else -> throw IllegalArgumentException(uriParser.toString())
            }
        } else {
            sortOrderIn
        }
        var cursor: Cursor? = null
        if (myContextHolder.getNow().isReady) {
            try {
                if (where.isNotEmpty()) {
                    qb.appendWhere(where)
                }
                if (sql.isEmpty()) {
                    if (tables.size == 1) {
                        qb.tables = tables.get(0)
                        sql = qb.buildQuery(projection, selection, null, null, sortOrder, limit)
                        selectionArgs2 = selectionArgs
                    } else {
                        val subQueries: Array<String> = tables.stream().map { str: String? ->
                            qb.tables = str
                            qb.buildQuery(projection, selection, null, null, null, null)
                        }.collect(Collectors.toList()).toTypedArray()
                        selectionArgs2 += selectionArgs
                        qb.isDistinct = true
                        sql = qb.buildUnionQuery(subQueries, sortOrder, limit)
                    }
                    built = true
                }
                // Here we substitute ?-s in selection with values from selectionArgs
                cursor = db.rawQuery(sql, selectionArgs2)
                if (cursor == null) {
                    MyLog.e(this, "Null cursor returned " + formatSql(sql, selectionArgs2))
                }
            } catch (e: Exception) {
                MyLog.e(this, "Database query failed " + formatSql(sql, selectionArgs2), e)
            }
            if (MyLog.isDebugEnabled()) {
                MyLog.d(this, formatSql(sql, selectionArgs2))
                if (built && MyLog.isVerboseEnabled()) {
                    val msg2 = ("uri=" + uri + "; projection=" + Arrays.toString(projection)
                        + "; selection=" + selection + "; sortOrder=" + sortOrderIn
                        + "; qb.getTables=" + qb.tables + "; orderBy=" + sortOrder)
                    MyLog.v(this, msg2)
                }
            }
        }
        cursor?.setNotificationUri(context?.contentResolver, uri)
        return cursor
    }

    fun formatSql(sql: String, selectionArgs2: Array<String>): String {
        var msg1 = "query, SQL=\"$sql\""
        if (selectionArgs2.size > 0) {
            msg1 += "; selectionArgs=" + Arrays.toString(selectionArgs2)
        }
        return msg1
    }

    /**
     * Update objects (one or several records) in the database
     */
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int {
        if (values == null) return 0
        val db: SQLiteDatabase = myContextHolder.getNow().database ?: kotlin.run {
            MyLog.databaseIsNull { "update" }
            return 0
        }
        var count = 0
        val uriParser: ParsedUri = ParsedUri.fromUri(uri)
        when (uriParser.matched()) {
            MatchedUri.ACTIVITY -> count = db.update(NoteTable.TABLE_NAME, values, selection, selectionArgs)
            MatchedUri.NOTE_ITEM -> {
                val rowId = uriParser.getNoteId()
                if (values.size() > 0) {
                    count = db.update(
                        NoteTable.TABLE_NAME, values, BaseColumns._ID + "=" + rowId
                            + if (!selection.isNullOrEmpty()) " AND ($selection)" else "",
                        selectionArgs
                    )
                }
            }
            MatchedUri.ACTOR -> count = db.update(ActorTable.TABLE_NAME, values, selection, selectionArgs)
            MatchedUri.ACTOR_ITEM -> {
                val selectedActorId = uriParser.getActorId()
                if (values.size() > 0) {
                    count = db.update(
                        ActorTable.TABLE_NAME, values, BaseColumns._ID + "=" + selectedActorId
                            + if (!selection.isNullOrEmpty()) " AND ($selection)" else "",
                        selectionArgs
                    )
                }
            }
            else -> throw IllegalArgumentException(uriParser.toString())
        }
        return count
    }

    companion object {
        val TAG: String = MyProvider::class.simpleName!!

        /** @return Number of deleted activities of this note
         */
        fun deleteNoteAndItsActivities(context: MyContext?, noteId: Long): Int {
            return if (context == null || noteId == 0L) 0 else deleteActivities(
                context,
                ActivityTable.NOTE_ID + "=" + noteId,
                null,
                true
            )
        }

        fun deleteActivities(
            myContext: MyContext,
            selection: String?,
            selectionArgs: Array<String>?,
            inTransaction: Boolean
        ): Int {
            val db = myContext.database
            if (db == null) {
                MyLog.databaseIsNull { "deleteActivities" }
                return 0
            }
            var count = 0
            var sqlDesc = ""
            if (!inTransaction) {
                db.beginTransaction()
            }
            try {
                val descSuffix = "; args=" + Arrays.toString(selectionArgs)

                // Start from deletion of activities
                sqlDesc = selection + descSuffix
                count += db.delete(ActivityTable.TABLE_NAME, selection, selectionArgs)

                // Notes, which don't have any activities
                val sqlNoteIds = "SELECT msgA." + BaseColumns._ID +
                    " FROM " + NoteTable.TABLE_NAME + " AS msgA" +
                    " WHERE NOT EXISTS" +
                    " (SELECT " + ActivityTable.NOTE_ID + " FROM " + ActivityTable.TABLE_NAME +
                    " WHERE " + ActivityTable.NOTE_ID + "=msgA." + BaseColumns._ID + ")"
                val noteIds = MyQuery.getLongs(sqlNoteIds)

                // Audience
                var selectionG = " EXISTS (" + sqlNoteIds +
                    " AND (msgA." + BaseColumns._ID +
                    "=" + AudienceTable.TABLE_NAME + "." + AudienceTable.NOTE_ID + "))"
                sqlDesc = selectionG + descSuffix
                count += db.delete(AudienceTable.TABLE_NAME, selectionG, arrayOf())
                for (noteId in noteIds) {
                    DownloadData.deleteAllOfThisNote(db, noteId)
                }

                // Notes
                selectionG = " EXISTS (" + sqlNoteIds +
                    " AND (msgA." + BaseColumns._ID +
                    "=" + NoteTable.TABLE_NAME + "." + BaseColumns._ID + "))"
                sqlDesc = selectionG + descSuffix
                count += db.delete(NoteTable.TABLE_NAME, selectionG, arrayOf())
                if (!inTransaction) {
                    db.setTransactionSuccessful()
                }
            } catch (e: Exception) {
                MyLog.d(TAG, "; SQL='$sqlDesc'", e)
            } finally {
                if (!inTransaction) {
                    db.endTransaction()
                }
            }
            return count
        }

        fun deleteActor(myContext: MyContext, actorIdToDelete: Long): Long {
            return deleteActor(myContext, actorIdToDelete, 0)
        }

        private fun deleteActor(myContext: MyContext, actorId: Long, recursionLevel: Long): Long {
            if (recursionLevel < 3) {
                MyQuery.foldLeft(myContext, "SELECT " + BaseColumns._ID + " FROM " +
                    ActorTable.TABLE_NAME + " WHERE " + ActorTable.PARENT_ACTOR_ID + "=" + actorId,
                    ArrayList(),
                    { id: ArrayList<Long> ->
                        Function { cursor: Cursor ->
                            id.add(DbUtils.getLong(cursor, BaseColumns._ID))
                            id
                        }
                    }
                ).forEach(Consumer { childActorId: Long -> deleteActor(myContext, childActorId, recursionLevel + 1) })
            }
            val userId =
                MyQuery.idToLongColumnValue(myContext.database, ActorTable.TABLE_NAME, ActorTable.USER_ID, actorId)
            delete(myContext, AudienceTable.TABLE_NAME, AudienceTable.ACTOR_ID, actorId)
            delete(myContext, GroupMembersTable.TABLE_NAME, GroupMembersTable.GROUP_ID, actorId)
            delete(myContext, GroupMembersTable.TABLE_NAME, GroupMembersTable.MEMBER_ID, actorId)
            DownloadData.deleteAllOfThisActor(myContext, actorId)
            delete(myContext, ActorEndpointTable.TABLE_NAME, ActorEndpointTable.ACTOR_ID, actorId)
            delete(myContext, ActorTable.TABLE_NAME, BaseColumns._ID, actorId)
            if (!MyQuery.dExists(
                    myContext.database, "SELECT * FROM " + ActorTable.TABLE_NAME
                        + " WHERE " + ActorTable.USER_ID + "=" + userId
                )
            ) {
                delete(myContext, UserTable.TABLE_NAME, BaseColumns._ID, userId)
            }
            return 1
        }

        fun delete(myContext: MyContext, tableName: String, column: String, value: Any?): Int {
            return if (value == null) 0 else delete(myContext, tableName, "$column=$value")
        }

        fun delete(myContext: MyContext, tableName: String, where: String?): Int {
            val method = "delete"
            val db = myContext.database ?: kotlin.run {
                MyLog.databaseIsNull { method }
                return 0
            }
            try {
                return db.delete(tableName, where, null)
            } catch (e: Exception) {
                MyLog.w(TAG, "$method; table:'$tableName', where:'$where'", e)
            }
            return 0
        }

        // TODO: return Try<Long>
        fun deleteActivity(myContext: MyContext, activityId: Long, noteId: Long, inTransaction: Boolean): Long {
            val db = myContext.database ?: kotlin.run {
                MyLog.databaseIsNull { "deleteActivity" }
                return 0
            }
            val originId = MyQuery.activityIdToLongColumnValue(ActivityTable.ORIGIN_ID, activityId)
            if (originId == 0L) return 0
            val origin = myContext.origins.fromId(originId)
            // Was this the last activity for this note?
            val activityId2 = MyQuery.conditionToLongColumnValue(
                db, null, ActivityTable.TABLE_NAME,
                BaseColumns._ID, ActivityTable.NOTE_ID + "=" + noteId +
                    " AND " + ActivityTable.TABLE_NAME + "." + BaseColumns._ID + "!=" + activityId
            )
            val count: Long
            if (noteId != 0L && activityId2 == 0L) {
                // Delete related note if no more its activities left
                count = deleteActivities(
                    myContext, ActivityTable.TABLE_NAME + "." + BaseColumns._ID +
                        "=" + activityId, arrayOf(), inTransaction
                ).toLong()
            } else {
                // Delete this activity only
                count = db.delete(ActivityTable.TABLE_NAME, BaseColumns._ID + "=" + activityId, null).toLong()
                updateNoteFavorited(myContext, origin, noteId)
                updateNoteReblogged(myContext, origin, noteId)
            }
            return count
        }

        fun updateNoteReblogged(myContext: MyContext, origin: Origin, noteId: Long) {
            val reblogged: TriState = TriState.fromBoolean(
                myContext.users.containsMe(MyQuery.getRebloggers(myContext.database, origin, noteId))
            )
            update(
                myContext, NoteTable.TABLE_NAME,
                NoteTable.REBLOGGED + "=" + reblogged.id,
                BaseColumns._ID + "=" + noteId
            )
        }

        fun updateNoteDownloadStatus(myContext: MyContext, noteId: Long, downloadStatus: DownloadStatus) {
            update(
                myContext, NoteTable.TABLE_NAME,
                NoteTable.NOTE_STATUS + "=" + downloadStatus.save(),
                BaseColumns._ID + "=" + noteId
            )
        }

        fun updateNoteFavorited(myContext: MyContext, origin: Origin, noteId: Long) {
            val favorited: TriState = TriState.fromBoolean(
                myContext.users.containsMe(MyQuery.getStargazers(myContext.database, origin, noteId))
            )
            update(
                myContext, NoteTable.TABLE_NAME,
                NoteTable.FAVORITED + "=" + favorited.id,
                BaseColumns._ID + "=" + noteId
            )
        }

        fun updateActivityOid(myContext: MyContext, activityId: Long, oid: String) {
            update(
                myContext, ActivityTable.TABLE_NAME,
                ActivityTable.ACTIVITY_OID + "=" + MyQuery.quoteIfNotQuoted(oid),
                BaseColumns._ID + "=$activityId"
            )
        }

        fun clearAllNotifications(myContext: MyContext) {
            update(
                myContext, ActivityTable.TABLE_NAME,
                ActivityTable.NEW_NOTIFICATION_EVENT + "=0",
                ActivityTable.NEW_NOTIFICATION_EVENT + "!=0"
            )
        }

        fun clearNotification(myContext: MyContext, timeline: Timeline) {
            update(
                myContext, ActivityTable.TABLE_NAME,
                ActivityTable.NEW_NOTIFICATION_EVENT + "=0",
                if (timeline.actor.isEmpty) "" else ActivityTable.NOTIFIED_ACTOR_ID + "=" + timeline.actor.actorId
            )
        }

        fun setUnsentActivityNotification(myContext: MyContext, activityId: Long) {
            update(
                myContext, ActivityTable.TABLE_NAME,
                ActivityTable.NEW_NOTIFICATION_EVENT + "=" + NotificationEventType.OUTBOX.id
                    + ", " + ActivityTable.NOTIFIED + "=" + TriState.TRUE.id
                    + ", " + ActivityTable.NOTIFIED_ACTOR_ID + "=" + ActivityTable.ACTOR_ID,
                BaseColumns._ID + "=" + activityId
            )
        }

        fun update(myContext: MyContext, tableName: String, set: String, where: String?) {
            val method = "update"
            val db = myContext.database ?: kotlin.run {
                MyLog.databaseIsNull { method }
                return
            }
            val sql = "UPDATE " + tableName + " SET " + set + if (where.isNullOrEmpty()) "" else " WHERE $where"
            try {
                db.execSQL(sql)
            } catch (e: Exception) {
                MyLog.w(TAG, "$method; SQL:'$sql'", e)
            }
        }

        fun insert(myContext: MyContext, tableName: String, values: ContentValues): Long {
            val db = myContext.database
            if (db == null || values.size() == 0) return -1
            val rowId = db.insert(tableName, null, values)
            if (rowId == -1L) {
                throw SQLException("Failed to insert $values")
            }
            return rowId
        }
    }
}
