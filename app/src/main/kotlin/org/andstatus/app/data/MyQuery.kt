/* 
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
package org.andstatus.app.data

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDoneException
import android.database.sqlite.SQLiteStatement
import android.provider.BaseColumns
import androidx.core.util.Pair
import org.andstatus.app.context.ActorInTimeline
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.data.DbUtils.closeSilently
import org.andstatus.app.database.table.ActivityTable
import org.andstatus.app.database.table.ActorTable
import org.andstatus.app.database.table.NoteTable
import org.andstatus.app.net.social.ActivityType
import org.andstatus.app.net.social.Actor
import org.andstatus.app.origin.Origin
import org.andstatus.app.os.AsyncUtil
import org.andstatus.app.util.MyHtml
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.StringUtil
import org.andstatus.app.util.TriState
import java.util.*
import java.util.function.Function


object MyQuery {
    private val TAG: String = MyQuery::class.java.simpleName
    fun usernameField(actorInTimeline: ActorInTimeline?): String {
        return when (actorInTimeline) {
            ActorInTimeline.AT_USERNAME -> "('@' || " + ActorTable.USERNAME + ")"
            ActorInTimeline.WEBFINGER_ID -> ActorTable.WEBFINGER_ID
            ActorInTimeline.REAL_NAME -> ActorTable.REAL_NAME
            ActorInTimeline.REAL_NAME_AT_USERNAME -> "(" + ActorTable.REAL_NAME + " || ' @' || " + ActorTable.USERNAME + ")"
            ActorInTimeline.REAL_NAME_AT_WEBFINGER_ID -> "(" + ActorTable.REAL_NAME + " || ' @' || " + ActorTable.WEBFINGER_ID + ")"
            else -> ActorTable.USERNAME
        }
    }

    /**
     * Lookup the System's (AndStatus) id from the Originated system's id
     *
     * @param originId - see [NoteTable.ORIGIN_ID]
     * @param oid - see [NoteTable.NOTE_OID]
     * @return - id in our System (i.e. in the table, e.g.
     * [NoteTable._ID] ). Or 0 if nothing was found.
     */
    fun oidToId(oidEnum: OidEnum?, originId: Long, oid: String?): Long {
        return oidToId(MyContextHolder.myContextHolder.getNow(), oidEnum, originId, oid)
    }

    fun oidToId(myContext: MyContext, oidEnum: OidEnum?, originId: Long, oid: String?): Long {
        if (oid.isNullOrEmpty()) {
            return 0
        }
        val msgLog = "oidToId; $oidEnum, origin=$originId, oid=$oid"
        val sql: String = when (oidEnum) {
            OidEnum.NOTE_OID -> "SELECT " + BaseColumns._ID + " FROM " + NoteTable.TABLE_NAME +
                    " WHERE " + NoteTable.ORIGIN_ID + "=" + originId + " AND " + NoteTable.NOTE_OID +
                    "=" + quoteIfNotQuoted(oid)
            OidEnum.ACTOR_OID -> "SELECT " + BaseColumns._ID + " FROM " + ActorTable.TABLE_NAME +
                    " WHERE " + ActorTable.ORIGIN_ID + "=" + originId + " AND " + ActorTable.ACTOR_OID +
                    "=" + quoteIfNotQuoted(oid)
            OidEnum.ACTIVITY_OID -> "SELECT " + BaseColumns._ID + " FROM " + ActivityTable.TABLE_NAME +
                    " WHERE " + ActivityTable.ORIGIN_ID + "=" + originId + " AND " + ActivityTable.ACTIVITY_OID +
                    "=" + quoteIfNotQuoted(oid)
            else -> throw IllegalArgumentException("$msgLog; Unknown oidEnum")
        }
        return sqlToLong(myContext.database, msgLog, sql)
    }

    fun sqlToLong(databaseIn: SQLiteDatabase?, msgLogIn: String?, sql: String?): Long {
        val msgLog = StringUtil.notNull(msgLogIn)
        val db = databaseIn ?:  MyContextHolder.myContextHolder.getNow().database
        if (db == null) {
            MyLog.databaseIsNull { msgLog }
            return 0
        }
        if (sql.isNullOrEmpty()) {
            MyLog.v(TAG) { "$msgLog; sql is empty" }
            return 0
        }
        val msgLogSql = msgLog + if (msgLog.contains(sql)) "" else "; sql='$sql'"
        var value: Long
        var statement: SQLiteStatement? = null
        try {
            statement = db.compileStatement(sql)
            value = statement.simpleQueryForLong()
        } catch (e: SQLiteDoneException) {
            MyLog.ignored(TAG, e)
            value = 0
        } catch (e: Exception) {
            MyLog.e(TAG, msgLogSql, e)
            value = 0
        } finally {
            closeSilently(statement)
        }
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(TAG, "$msgLogSql -> $value")
        }
        return value
    }

    /**
     * @return two single quotes for empty/null strings (Use single quotes!)
     */
    fun quoteIfNotQuoted(original: String?): String {
        if (original.isNullOrEmpty()) {
            return "\'\'"
        }
        var quoted = original.trim { it <= ' ' }
        val firstQuoteIndex = quoted.indexOf('\'')
        if (firstQuoteIndex < 0) {
            return "'$quoted'"
        }
        val lastQuoteIndex = quoted.lastIndexOf('\'')
        if (firstQuoteIndex == 0 && lastQuoteIndex == quoted.length - 1) {
            // Already quoted, search quotes inside
            quoted = quoted.substring(1, lastQuoteIndex)
        }
        quoted = quoted.replace("'", "''")
        quoted = "'$quoted'"
        return quoted
    }

    /**
     * Lookup Originated system's id from the System's (AndStatus) id
     *
     * @param oe what oid we need
     * @param entityId - see [NoteTable._ID]
     * @param rebloggerActorId Is needed to find reblog by this actor
     * @return - oid in Originated system (i.e. in the table, e.g.
     * [NoteTable.NOTE_OID] empty string in case of an error
     */
    fun idToOid(myContext: MyContext, oe: OidEnum, entityId: Long, rebloggerActorId: Long): String {
        if (entityId == 0L) return ""
        val db = myContext.database
        return if (db == null) {
            MyLog.databaseIsNull { "idToOid, oe=$oe id=$entityId" }
            ""
        } else {
            idToOid(db, oe, entityId, rebloggerActorId)
        }
    }

    /**
     * Lookup Originated system's id from the System's (AndStatus) id
     *
     * @param oe what oid we need
     * @param entityId - see [NoteTable._ID]
     * @param rebloggerActorId Is needed to find reblog by this actor
     * @return - oid in Originated system (i.e. in the table, e.g.
     * [NoteTable.NOTE_OID] empty string in case of an error
     */
    fun idToOid(db: SQLiteDatabase, oe: OidEnum, entityId: Long, rebloggerActorId: Long): String {
        val method = "idToOid"
        var oid = ""
        var prog: SQLiteStatement? = null
        val sql: String
        if (entityId > 0) {
            try {
                sql = when (oe) {
                    OidEnum.NOTE_OID -> "SELECT " + NoteTable.NOTE_OID + " FROM " +
                            NoteTable.TABLE_NAME + " WHERE " + BaseColumns._ID + "=" + entityId
                    OidEnum.ACTOR_OID -> "SELECT " + ActorTable.ACTOR_OID + " FROM " +
                            ActorTable.TABLE_NAME + " WHERE " + BaseColumns._ID + "=" + entityId
                    OidEnum.REBLOG_OID -> {
                        if (rebloggerActorId == 0L) {
                            MyLog.w(TAG, "$method; rebloggerActorId was not defined")
                        }
                        ("SELECT " + ActivityTable.ACTIVITY_OID + " FROM "
                                + ActivityTable.TABLE_NAME + " WHERE "
                                + ActivityTable.NOTE_ID + "=" + entityId + " AND "
                                + ActivityTable.ACTIVITY_TYPE + "=" + ActivityType.ANNOUNCE.id + " AND "
                                + ActivityTable.ACTOR_ID + "=" + rebloggerActorId)
                    }
                    else -> throw IllegalArgumentException("$method; Unknown parameter: $oe")
                }
                prog = db.compileStatement(sql)
                oid = prog.simpleQueryForString()
                if (oid.isEmpty() && oe == OidEnum.REBLOG_OID) {
                    // This not reblogged note
                    oid = idToOid(db, OidEnum.NOTE_OID, entityId, 0)
                }
            } catch (e: SQLiteDoneException) {
                MyLog.ignored(TAG, e)
                oid = ""
            } catch (e: Exception) {
                MyLog.e(TAG, method, e)
                oid = ""
            } finally {
                closeSilently(prog)
            }
            if (MyLog.isVerboseEnabled()) {
                MyLog.v(TAG, "$method; $oe + $entityId -> $oid")
            }
        }
        return oid
    }

    /** @return ID of the Reblog/Undo reblog activity and the type of the Activity
     */
    fun noteIdToLastReblogging(db: SQLiteDatabase?, noteId: Long, actorId: Long): Pair<Long, ActivityType> {
        return noteIdToLastOfTypes(db, noteId, actorId, ActivityType.ANNOUNCE, ActivityType.UNDO_ANNOUNCE)
    }

    /** @return ID of the last LIKE/UNDO_LIKE activity and the type of the activity
     */
    fun noteIdToLastFavoriting(db: SQLiteDatabase?, noteId: Long, actorId: Long): Pair<Long, ActivityType> {
        return noteIdToLastOfTypes(db, noteId, actorId, ActivityType.LIKE, ActivityType.UNDO_LIKE)
    }

    /** @return ID of the last type1 or type2 activity and the type of the activity for the selected actor
     */
    fun noteIdToLastOfTypes(
            db: SQLiteDatabase?, noteId: Long, actorId: Long, type1: ActivityType, type2: ActivityType): Pair<Long, ActivityType> {
        val method = "noteIdIdToLastOfTypes"
        if (db == null || noteId == 0L || actorId == 0L) {
            return Pair(0L, ActivityType.EMPTY)
        }
        val sql = ("SELECT " + ActivityTable.ACTIVITY_TYPE + ", " + BaseColumns._ID
                + " FROM " + ActivityTable.TABLE_NAME
                + " WHERE " + ActivityTable.NOTE_ID + "=" + noteId + " AND "
                + ActivityTable.ACTIVITY_TYPE
                + " IN(" + type1.id + "," + type2.id + ") AND "
                + ActivityTable.ACTOR_ID + "=" + actorId
                + " ORDER BY " + ActivityTable.UPDATED_DATE + " DESC LIMIT 1")
        try {
            db.rawQuery(sql, null).use { cursor ->
                if (cursor.moveToNext()) {
                    return Pair(cursor.getLong(1), ActivityType.fromId(cursor.getLong(0)))
                }
            }
        } catch (e: Exception) {
            MyLog.i(TAG, "$method; SQL:'$sql'", e)
        }
        return Pair(0L, ActivityType.EMPTY)
    }

    fun getStargazers(db: SQLiteDatabase?, origin: Origin, noteId: Long): MutableList<Actor> {
        return noteIdToActors(db, origin, noteId, ActivityType.LIKE, ActivityType.UNDO_LIKE)
    }

    fun getRebloggers(db: SQLiteDatabase?, origin: Origin, noteId: Long): MutableList<Actor> {
        return noteIdToActors(db, origin, noteId, ActivityType.ANNOUNCE, ActivityType.UNDO_ANNOUNCE)
    }

    /** @return Actors, who did activities of one of these types with the note
     */
    fun noteIdToActors(
            db: SQLiteDatabase?, origin: Origin, noteId: Long, mainType: ActivityType, undoType: ActivityType): MutableList<Actor> {
        val method = "noteIdToActors"
        val foundActors: MutableList<Long> = ArrayList()
        val actors: MutableList<Actor> = ArrayList()
        if (db == null || !origin.isValid || noteId == 0L) {
            return actors
        }
        val sql = ("SELECT " + ActivityTable.ACTIVITY_TYPE + ", " + ActivityTable.ACTOR_ID + ", "
                + ActorTable.WEBFINGER_ID + ", " + TimelineSql.usernameField() + " AS " + ActorTable.ACTIVITY_ACTOR_NAME
                + " FROM " + ActivityTable.TABLE_NAME + " INNER JOIN " + ActorTable.TABLE_NAME
                + " ON " + ActivityTable.ACTOR_ID + "=" + ActorTable.TABLE_NAME + "." + BaseColumns._ID
                + " WHERE " + ActivityTable.NOTE_ID + "=" + noteId + " AND "
                + ActivityTable.ACTIVITY_TYPE + " IN(" + mainType.id + "," + undoType.id + ")"
                + " ORDER BY " + ActivityTable.UPDATED_DATE + " DESC")
        try {
            db.rawQuery(sql, null).use { cursor ->
                while (cursor.moveToNext()) {
                    val actorId = DbUtils.getLong(cursor, ActivityTable.ACTOR_ID)
                    if (!foundActors.contains(actorId)) {
                        foundActors.add(actorId)
                        val activityType: ActivityType = ActivityType.fromId(DbUtils.getLong(cursor, ActivityTable.ACTIVITY_TYPE))
                        if (activityType == mainType) {
                            val actor: Actor = Actor.fromId(origin, actorId)
                            actor.setRealName(DbUtils.getString(cursor, ActorTable.ACTIVITY_ACTOR_NAME))
                            actor.setWebFingerId(DbUtils.getString(cursor, ActorTable.WEBFINGER_ID))
                            actors.add(actor)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            MyLog.w(TAG, "$method; SQL:'$sql'", e)
        }
        return actors
    }

    internal fun favoritedAndReblogged(myContext: MyContext, noteId: Long, actorId: Long): ActorToNote {
        val method = "favoritedAndReblogged"
        var favoriteFound = false
        var reblogFound = false
        val actorToNote = ActorToNote()
        val db = myContext.database
        if (db == null || noteId == 0L || actorId == 0L) {
            return actorToNote
        }
        val sql = ("SELECT " + ActivityTable.ACTIVITY_TYPE + ", " + ActivityTable.SUBSCRIBED
                + " FROM " + ActivityTable.TABLE_NAME + " INNER JOIN " + ActorTable.TABLE_NAME
                + " ON " + ActivityTable.ACTOR_ID + "=" + ActorTable.TABLE_NAME + "." + BaseColumns._ID
                + " WHERE " + ActivityTable.NOTE_ID + "=" + noteId + " AND "
                + ActivityTable.ACTOR_ID + "=" + actorId
                + " ORDER BY " + ActivityTable.UPDATED_DATE + " DESC")
        try {
            db.rawQuery(sql, null).use { cursor ->
                while (cursor.moveToNext()) {
                    if (DbUtils.getTriState(cursor, ActivityTable.SUBSCRIBED) == TriState.TRUE) {
                        actorToNote.subscribed = true
                    }
                    val activityType: ActivityType = ActivityType.fromId(DbUtils.getLong(cursor, ActivityTable.ACTIVITY_TYPE))
                    when (activityType) {
                        ActivityType.LIKE, ActivityType.UNDO_LIKE -> if (!favoriteFound) {
                            favoriteFound = true
                            actorToNote.favorited = activityType == ActivityType.LIKE
                        }
                        ActivityType.ANNOUNCE, ActivityType.UNDO_ANNOUNCE -> if (!reblogFound) {
                            reblogFound = true
                            actorToNote.reblogged = activityType == ActivityType.ANNOUNCE
                        }
                        else -> {
                        }
                    }
                }
            }
        } catch (e: Exception) {
            MyLog.w(TAG, "$method; SQL:'$sql'", e)
        }
        return actorToNote
    }

    fun noteIdToUsername(actorIdColumnName: String, noteId: Long, actorInTimeline: ActorInTimeline): String {
        val method = "noteIdToUsername"
        var username = ""
        if (noteId != 0L) {
            var prog: SQLiteStatement? = null
            val sql: String
            try {
                sql = if (actorIdColumnName.contentEquals(ActivityTable.ACTOR_ID)) {
                    // TODO:
                    throw IllegalArgumentException("$method; Not implemented \"$actorIdColumnName\"")
                } else if (actorIdColumnName.contentEquals(NoteTable.AUTHOR_ID) ||
                        actorIdColumnName.contentEquals(NoteTable.IN_REPLY_TO_ACTOR_ID)) {
                    ("SELECT " + usernameField(actorInTimeline) + " FROM " + ActorTable.TABLE_NAME
                            + " INNER JOIN " + NoteTable.TABLE_NAME + " ON "
                            + NoteTable.TABLE_NAME + "." + actorIdColumnName + "=" + ActorTable.TABLE_NAME + "." + BaseColumns._ID
                            + " WHERE " + NoteTable.TABLE_NAME + "." + BaseColumns._ID + "=" + noteId)
                } else {
                    throw IllegalArgumentException("$method; Unknown name \"$actorIdColumnName\"")
                }
                val db: SQLiteDatabase? =  MyContextHolder.myContextHolder.getNow().database
                if (db == null) {
                    MyLog.databaseIsNull { method }
                    return ""
                }
                prog = db.compileStatement(sql)
                username = prog.simpleQueryForString()
            } catch (e: SQLiteDoneException) {
                MyLog.ignored(TAG, e)
                username = ""
            } catch (e: Exception) {
                MyLog.e(TAG, method, e)
                username = ""
            } finally {
                closeSilently(prog)
            }
            if (MyLog.isVerboseEnabled()) {
                MyLog.v(TAG, "$method; $actorIdColumnName: $noteId -> $username")
            }
        }
        return username
    }

    fun actorIdToWebfingerId(myContext: MyContext, actorId: Long): String {
        return actorIdToName(myContext, actorId, ActorInTimeline.WEBFINGER_ID)
    }

    fun actorIdToName(myContext: MyContext, actorId: Long, actorInTimeline: ActorInTimeline?): String {
        return idToStringColumnValue(myContext.database, ActorTable.TABLE_NAME, usernameField(actorInTimeline), actorId)
    }

    /**
     * Convenience method to get column value from [ActorTable] table
     * @param columnName without table name
     * @param systemId [ActorTable.ACTOR_ID]
     * @return 0 in case not found or error
     */
    fun actorIdToLongColumnValue(columnName: String, systemId: Long): Long {
        return idToLongColumnValue(null, ActorTable.TABLE_NAME, columnName, systemId)
    }

    /** @return 0 if id == 0 or if not found
     */
    fun idToLongColumnValue(databaseIn: SQLiteDatabase?, tableName: String, columnName: String, systemId: Long): Long {
        return if (systemId == 0L) {
            0
        } else {
            conditionToLongColumnValue(databaseIn, null, tableName, columnName, "t._id=$systemId")
        }
    }

    /**
     * Convenience method to get long column value from the 'tableName' table
     * @param tableName e.g. [NoteTable.TABLE_NAME]
     * @param columnName without table name
     * @param condition WHERE part of SQL statement
     * @return 0 in case not found or error or systemId==0
     */
    fun conditionToLongColumnValue(tableName: String, columnName: String, condition: String?): Long {
        return conditionToLongColumnValue(null, columnName, tableName, columnName, condition)
    }

    fun conditionToLongColumnValue(databaseIn: SQLiteDatabase?, msgLog: String?,
                                   tableName: String, columnName: String, condition: String?): Long {
        val sql = "SELECT t." + columnName +
                " FROM " + tableName + " AS t" +
                if (condition.isNullOrEmpty()) "" else " WHERE $condition"
        return if (tableName.isEmpty()) {
            throw IllegalArgumentException("tableName is empty: $sql")
        } else if (columnName.isEmpty()) {
            throw IllegalArgumentException("columnName is empty: $sql")
        } else {
            sqlToLong(databaseIn, msgLog, sql)
        }
    }

    fun noteIdToStringColumnValue(columnName: String, systemId: Long): String {
        return idToStringColumnValue(null, NoteTable.TABLE_NAME, columnName, systemId)
    }

    fun actorIdToStringColumnValue(columnName: String, systemId: Long): String {
        return idToStringColumnValue(null, ActorTable.TABLE_NAME, columnName, systemId)
    }

    /**
     * Convenience method to get String column value from the 'tableName' table
     *
     * @param db
     * @param tableName e.g. [NoteTable.TABLE_NAME]
     * @param columnName without table name
     * @param systemId tableName._id
     * @return not null; "" in a case not found or error or systemId==0
     */
    fun idToStringColumnValue(db: SQLiteDatabase?, tableName: String, columnName: String, systemId: Long): String {
        return if (systemId == 0L) "" else conditionToStringColumnValue(db, tableName, columnName, "_id=$systemId")
    }

    fun conditionToStringColumnValue(dbIn: SQLiteDatabase?, tableName: String, columnName: String, condition: String): String {
        val method = "cond2str"
        val db = dbIn ?:  MyContextHolder.myContextHolder.getNow().database
        if (db == null) {
            MyLog.databaseIsNull { method }
            return ""
        }
        val sql = "SELECT $columnName FROM $tableName WHERE $condition"
        require(!(tableName.isEmpty() || columnName.isEmpty())) { "$method tableName or columnName are empty" }
        require(!columnName.isEmpty()) { "columnName is empty: $sql" }

        var columnValue: String? = ""
        try {
            db.compileStatement(sql).use {
                prog -> columnValue = prog.simpleQueryForString()
            }
        } catch (e: SQLiteDoneException) {
            MyLog.ignored(TAG, e)
        } catch (e: java.lang.Exception) {
            MyLog.e(TAG, "$method table='$tableName', column='$columnName'", e)
            return ""
        }
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(TAG, "$method; '$sql' -> $columnValue")
        }

        return columnValue ?: ""
    }

    fun noteIdToActorId(noteActorIdColumnName: String, systemId: Long): Long {
        var actorId: Long = 0
        actorId = try {
            if (noteActorIdColumnName.contentEquals(ActivityTable.ACTOR_ID) ||
                    noteActorIdColumnName.contentEquals(NoteTable.AUTHOR_ID) ||
                    noteActorIdColumnName.contentEquals(NoteTable.IN_REPLY_TO_ACTOR_ID)) {
                noteIdToLongColumnValue(noteActorIdColumnName, systemId)
            } else {
                throw IllegalArgumentException("noteIdToActorId; Illegal column '$noteActorIdColumnName'")
            }
        } catch (e: Exception) {
            MyLog.e(TAG, "noteIdToActorId", e)
            return 0
        }
        return actorId
    }

    fun noteIdToOriginId(systemId: Long): Long {
        return noteIdToLongColumnValue(NoteTable.ORIGIN_ID, systemId)
    }

    fun activityIdToTriState(columnName: String, systemId: Long): TriState {
        return TriState.fromId(activityIdToLongColumnValue(columnName, systemId))
    }

    /**
     * Convenience method to get column value from [ActivityTable] table
     * @param columnName without table name
     * @param systemId  MyDatabase.NOTE_TABLE_NAME + "." + Note._ID
     * @return 0 in case not found or error
     */
    fun activityIdToLongColumnValue(columnName: String, systemId: Long): Long {
        return idToLongColumnValue(null, ActivityTable.TABLE_NAME, columnName, systemId)
    }

    fun noteIdToTriState(columnName: String, systemId: Long): TriState {
        return TriState.fromId(noteIdToLongColumnValue(columnName, systemId))
    }

    fun isSensitive(systemId: Long): Boolean {
        return noteIdToLongColumnValue(NoteTable.SENSITIVE, systemId) == 1L
    }

    /**
     * Convenience method to get column value from [NoteTable] table
     * @param columnName without table name
     * @param systemId  NoteTable._ID
     * @return 0 in case not found or error
     */
    fun noteIdToLongColumnValue(columnName: String, systemId: Long): Long {
        return when (columnName) {
            ActivityTable.ACTOR_ID,
            ActivityTable.AUTHOR_ID,
            ActivityTable.UPDATED_DATE,
            ActivityTable.LAST_UPDATE_ID -> noteIdToLongActivityColumnValue(null, columnName, systemId)
            else -> idToLongColumnValue(null, NoteTable.TABLE_NAME, columnName, systemId)
        }
    }

    /** Data from the latest activity for this note...  */
    fun noteIdToLongActivityColumnValue(databaseIn: SQLiteDatabase?, columnNameIn: String?, noteId: Long): Long {
        val method = "noteId2activity$columnNameIn"
        val columnName: String?
        val condition: String
        when (columnNameIn) {
            BaseColumns._ID, ActivityTable.ACTOR_ID -> {
                columnName = columnNameIn
                condition = (ActivityTable.ACTIVITY_TYPE + " IN("
                        + ActivityType.CREATE.id + ","
                        + ActivityType.UPDATE.id + ","
                        + ActivityType.ANNOUNCE.id + ","
                        + ActivityType.LIKE.id + ")")
            }
            ActivityTable.AUTHOR_ID -> {
                columnName = ActivityTable.ACTOR_ID
                condition = (ActivityTable.ACTIVITY_TYPE + " IN("
                        + ActivityType.CREATE.id + ","
                        + ActivityType.UPDATE.id + ")")
            }
            ActivityTable.LAST_UPDATE_ID, ActivityTable.UPDATED_DATE -> {
                columnName = if (columnNameIn == ActivityTable.LAST_UPDATE_ID) BaseColumns._ID else columnNameIn
                condition = (ActivityTable.ACTIVITY_TYPE + " IN("
                        + ActivityType.CREATE.id + ","
                        + ActivityType.UPDATE.id + ","
                        + ActivityType.DELETE.id + ")")
            }
            else -> throw IllegalArgumentException("$method; Illegal column '$columnNameIn'")
        }
        return conditionToLongColumnValue(databaseIn, method, ActivityTable.TABLE_NAME, columnName,
                ActivityTable.NOTE_ID + "=" + noteId + " AND " + condition
                        + " ORDER BY " + ActivityTable.UPDATED_DATE + " DESC LIMIT 1")
    }

    fun webFingerIdToId(myContext: MyContext, originId: Long, webFingerId: String, checkOid: Boolean): Long {
        return actorColumnValueToId(myContext, originId, ActorTable.WEBFINGER_ID, webFingerId, checkOid)
    }

    /**
     * Lookup the Actor's id based on the username in the Originating system
     *
     * @param originId - see [NoteTable.ORIGIN_ID], 0 - for all Origin-s
     * @param username - see [ActorTable.USERNAME]
     * @param checkOid true to try to retrieve a user with a realOid first
     * @return - id in our System (i.e. in the table, e.g.
     * [ActorTable._ID] ), 0 if not found
     */
    fun usernameToId(myContext: MyContext, originId: Long, username: String, checkOid: Boolean): Long {
        return actorColumnValueToId(myContext, originId, ActorTable.USERNAME, username, checkOid)
    }

    private fun actorColumnValueToId(myContext: MyContext, originId: Long, columnName: String, columnValue: String,
                                     checkOid: Boolean): Long {
        val method = "actor" + columnName + "ToId"
        val db = myContext.database
        if (db == null) {
            MyLog.databaseIsNull { method }
            return 0
        }
        var id: Long = 0
        var prog: SQLiteStatement? = null
        var sql: String? = ""
        try {
            if (checkOid) {
                sql = sql4actorColumnValueToId(originId, columnName, columnValue, true)
                prog = db.compileStatement(sql)
                id = prog.simpleQueryForLong()
                if (id == 0L) closeSilently(prog)
            }
            if (id == 0L) {
                sql = sql4actorColumnValueToId(originId, columnName, columnValue, false)
                prog = db.compileStatement(sql)
                id = prog.simpleQueryForLong()
            }
        } catch (e: SQLiteDoneException) {
            MyLog.ignored(TAG, e)
            id = 0
        } catch (e: Exception) {
            MyLog.e(TAG, "$method; SQL:'$sql'", e)
            id = 0
        } finally {
            closeSilently(prog)
        }
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(TAG, "$method; $originId+$columnValue -> $id")
        }
        return id
    }

    private fun sql4actorColumnValueToId(originId: Long, columnName: String, value: String, checkOid: Boolean): String {
        return "SELECT " + BaseColumns._ID +
                " FROM " + ActorTable.TABLE_NAME +
                " WHERE " +
                (if (originId == 0L) "" else ActorTable.ORIGIN_ID + "=" + originId + " AND ") +
                (if (checkOid) ActorTable.ACTOR_OID + " NOT LIKE('andstatustemp:%') AND " else "") +
                columnName + "='" + value + "'" +
                " ORDER BY " + BaseColumns._ID
    }

    fun getCountOfActivities(condition: String?): Long {
        val sql = ("SELECT COUNT(*) FROM " + ActivityTable.TABLE_NAME
                + if (condition.isNullOrEmpty()) "" else " WHERE $condition")
        return getLongs(sql).firstOrNull() ?: 0
    }

    fun getLongs(sql: String): Set<Long> {
        return getLongs(MyContextHolder.myContextHolder.getNow(), sql)
    }

    fun getLongs(myContext: MyContext, sql: String): Set<Long> {
        return get<Long>(myContext, sql) { cursor: Cursor -> cursor.getLong(0) }
    }

    /**
     * @return Empty set on UI thread
     */
    operator fun <T> get(myContext: MyContext, sql: String, fromCursor: Function<Cursor, T>): MutableSet<T> {
        return foldLeft(myContext,
                sql,
                HashSet(),
                { t: HashSet<T> ->
                    Function { cursor: Cursor ->
                        t.add(fromCursor.apply(cursor))
                        t
                    }
                }
        )
    }

    /**
     * @return Empty list on UI thread
     */
    fun <T> getList(myContext: MyContext, sql: String, fromCursor: Function<Cursor, T>): List<T> {
        return foldLeft(myContext,
                sql,
                ArrayList(),
                { t: ArrayList<T> ->
                    Function { cursor: Cursor ->
                        t.add(fromCursor.apply(cursor))
                        t
                    }
                }
        )
    }

    /**
     * @return identity on UI thread
     */
    fun <U> foldLeft(myContext: MyContext, sql: String, identity: U,
                     f: Function<U, Function<Cursor, U>>): U {
        return foldLeft(myContext.database, sql, identity, f)
    }

    /**
     * @return identity on UI thread
     */
    fun <U> foldLeft(database: SQLiteDatabase?, sql: String, identity: U,
                     f: Function<U, Function<Cursor, U>>): U {
        val method = "foldLeft"
        if (database == null) {
            MyLog.databaseIsNull { method }
            return identity
        }
        if (AsyncUtil.isUiThread) {
            MyLog.v(TAG) {
                "$method; Database access in UI thread: '$sql' \n${MyLog.currentStackTrace}"
            }
            return identity
        }
        var result: U = identity
        try {
            database.rawQuery(sql, null).use { cursor -> while (cursor.moveToNext()) result = f.apply(result).apply(cursor) }
        } catch (e: Exception) {
            MyLog.i(TAG, "$method; SQL:'$sql'", e)
        }
        return result
    }

    fun noteInfoForLog(myContext: MyContext, noteId: Long): String {
        val builder = MyStringBuilder()
        builder.withComma("noteId", noteId)
        val oid = idToOid(myContext, OidEnum.NOTE_OID, noteId, 0)
        builder.withCommaQuoted("oid", if (oid.isEmpty()) "empty" else oid, oid.isNotEmpty())
        val content = MyHtml.htmlToCompactPlainText(noteIdToStringColumnValue(NoteTable.CONTENT, noteId))
        builder.withCommaQuoted("content", content, true)
        val origin = myContext.origins.fromId(noteIdToLongColumnValue(NoteTable.ORIGIN_ID, noteId))
        builder.atNewLine(origin.toString())
        return builder.toString()
    }

    fun conversationOidToId(originId: Long, conversationOid: String?): Long {
        return conditionToLongColumnValue(NoteTable.TABLE_NAME, NoteTable.CONVERSATION_ID,
                NoteTable.ORIGIN_ID + "=" + originId
                        + " AND " + NoteTable.CONVERSATION_OID + "=" + quoteIfNotQuoted(conversationOid))
    }

    fun noteIdToConversationOid(myContext: MyContext, noteId: Long): String {
        if (noteId == 0L) {
            return ""
        }
        var oid = noteIdToStringColumnValue(NoteTable.CONVERSATION_OID, noteId)
        if (oid.isNotEmpty()) {
            return oid
        }
        val conversationId = noteIdToLongColumnValue(NoteTable.CONVERSATION_ID, noteId)
        if (conversationId == 0L) {
            return idToOid(myContext, OidEnum.NOTE_OID, noteId, 0)
        }
        oid = noteIdToStringColumnValue(NoteTable.CONVERSATION_OID, conversationId)
        return if (oid.isNotEmpty()) {
            oid
        } else idToOid(myContext, OidEnum.NOTE_OID, conversationId, 0)
    }

    fun dExists(db: SQLiteDatabase?, sql: String): Boolean {
        if (db == null) return false

        var exists = false
        try {
            db.rawQuery(sql, null).use { cursor -> exists = cursor.moveToFirst() }
        } catch (e: Exception) {
            MyLog.d("", "dExists \"$sql\"", e)
        }
        return exists
    }
}
