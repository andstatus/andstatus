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

package org.andstatus.app.data;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteStatement;
import android.provider.BaseColumns;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import org.andstatus.app.actor.GroupType;
import org.andstatus.app.context.ActorInTimeline;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.database.table.ActorTable;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.net.social.ActivityType;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyStringBuilder;
import org.andstatus.app.util.StringUtils;
import org.andstatus.app.util.TriState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class MyQuery {
    private static final String TAG = MyQuery.class.getSimpleName();

    private MyQuery() {
        // Empty
    }

    static String usernameField(ActorInTimeline actorInTimeline) {
        switch (actorInTimeline) {
            case AT_USERNAME:
                return "('@' || " + ActorTable.USERNAME + ")";
            case WEBFINGER_ID:
                return ActorTable.WEBFINGER_ID;
            case REAL_NAME:
                return ActorTable.REAL_NAME;
            case REAL_NAME_AT_USERNAME:
                return "(" + ActorTable.REAL_NAME + " || ' @' || " + ActorTable.USERNAME + ")";
            case REAL_NAME_AT_WEBFINGER_ID:
                return "(" + ActorTable.REAL_NAME + " || ' @' || " + ActorTable.WEBFINGER_ID + ")";
            default:
                return ActorTable.USERNAME;
        }
    }

    /**
     * Lookup the System's (AndStatus) id from the Originated system's id
     * 
     * @param originId - see {@link NoteTable#ORIGIN_ID}
     * @param oid - see {@link NoteTable#NOTE_OID}
     * @return - id in our System (i.e. in the table, e.g.
     *         {@link NoteTable#_ID} ). Or 0 if nothing was found.
     */
    public static long oidToId(OidEnum oidEnum, long originId, String oid) {
        return oidToId(MyContextHolder.get(), oidEnum, originId, oid);
    }

    public static long oidToId(@NonNull MyContext myContext, OidEnum oidEnum, long originId, String oid) {
        if (StringUtils.isEmpty(oid)) {
            return 0;
        }
        String msgLog = "oidToId; " + oidEnum + ", origin=" + originId + ", oid=" + oid;
        String sql;
        switch (oidEnum) {
            case NOTE_OID:
                sql = "SELECT " + BaseColumns._ID + " FROM " + NoteTable.TABLE_NAME
                        + " WHERE " + NoteTable.ORIGIN_ID + "=" + originId + " AND " + NoteTable.NOTE_OID
                        + "=" + quoteIfNotQuoted(oid);
                break;
            case ACTOR_OID:
                sql = "SELECT " + BaseColumns._ID + " FROM " + ActorTable.TABLE_NAME
                        + " WHERE " + ActorTable.ORIGIN_ID + "=" + originId + " AND " + ActorTable.ACTOR_OID
                        + "=" + quoteIfNotQuoted(oid);
                break;
            case ACTIVITY_OID:
                sql = "SELECT " + BaseColumns._ID + " FROM " + ActivityTable.TABLE_NAME
                        + " WHERE " + ActivityTable.ORIGIN_ID + "=" + originId + " AND " + ActivityTable.ACTIVITY_OID
                        + "=" + quoteIfNotQuoted(oid);
                break;
            default:
                throw new IllegalArgumentException(msgLog + "; Unknown oidEnum");
        }
        return sqlToLong(myContext.getDatabase(), msgLog, sql);
    }

    public static long sqlToLong(SQLiteDatabase databaseIn, String msgLogIn, String sql) {
        String msgLog = StringUtils.notNull(msgLogIn);
        SQLiteDatabase db = databaseIn == null ? MyContextHolder.get().getDatabase() : databaseIn;
        if (db == null) {
            MyLog.databaseIsNull(() -> msgLog);
            return 0;
        }
        if (StringUtils.isEmpty(sql)) {
            MyLog.v(TAG, () -> msgLog + "; sql is empty");
            return 0;
        }
        String msgLogSql = msgLog + (msgLog.contains(sql) ? "" : "; sql='" + sql +"'");
        long value = 0;
        SQLiteStatement statement = null;
        try {
            statement = db.compileStatement(sql);
            value = statement.simpleQueryForLong();
        } catch (SQLiteDoneException e) {
            MyLog.ignored(TAG, e);
            value = 0;
        } catch (Exception e) {
            MyLog.e(TAG, msgLogSql, e);
            value = 0;
        } finally {
            DbUtils.closeSilently(statement);
        }
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(TAG, msgLogSql + " -> " + value);
        }
        return value;
    }

    /**
     * @return two single quotes for empty/null strings (Use single quotes!)
     */
    public static String quoteIfNotQuoted(String original) {
        if (StringUtils.isEmpty(original)) {
            return "\'\'";
        }
        String quoted = original.trim();
        int firstQuoteIndex = quoted.indexOf('\'');
        if (firstQuoteIndex < 0) {
            return '\'' + quoted + '\'';
        }
        int lastQuoteIndex = quoted.lastIndexOf('\'');
        if (firstQuoteIndex == 0 && lastQuoteIndex == quoted.length()-1) {
            // Already quoted, search quotes inside
            quoted = quoted.substring(1, lastQuoteIndex);
        }
        quoted = quoted.replace("'", "''");
        quoted = '\'' + quoted + '\'';
        return quoted;
    }

    /**
     * Lookup Originated system's id from the System's (AndStatus) id
     * 
     * @param oe what oid we need
     * @param entityId - see {@link NoteTable#_ID}
     * @param rebloggerActorId Is needed to find reblog by this actor
     * @return - oid in Originated system (i.e. in the table, e.g.
     *         {@link NoteTable#NOTE_OID} empty string in case of an error
     */
    @NonNull
    public static String idToOid(MyContext myContext, OidEnum oe, long entityId, long rebloggerActorId) {
        SQLiteDatabase db = myContext.getDatabase();
        if (db == null) {
            MyLog.databaseIsNull(() -> "idToOid, oe=" + oe + " id=" + entityId);
            return "";
        } else {
            return idToOid(db, oe, entityId, rebloggerActorId);
        }
    }

    /**
     * Lookup Originated system's id from the System's (AndStatus) id
     * 
     * @param oe what oid we need
     * @param entityId - see {@link NoteTable#_ID}
     * @param rebloggerActorId Is needed to find reblog by this actor
     * @return - oid in Originated system (i.e. in the table, e.g.
     *         {@link NoteTable#NOTE_OID} empty string in case of an error
     */
    @NonNull
    public static String idToOid(SQLiteDatabase db, OidEnum oe, long entityId, long rebloggerActorId) {
        String method = "idToOid";
        String oid = "";
        SQLiteStatement prog = null;
        String sql = "";
    
        if (entityId > 0) {
            try {
                switch (oe) {
                    case NOTE_OID:
                        sql = "SELECT " + NoteTable.NOTE_OID + " FROM "
                                + NoteTable.TABLE_NAME + " WHERE " + BaseColumns._ID + "=" + entityId;
                        break;
    
                    case ACTOR_OID:
                        sql = "SELECT " + ActorTable.ACTOR_OID + " FROM "
                                + ActorTable.TABLE_NAME + " WHERE " + BaseColumns._ID + "="
                                + entityId;
                        break;
    
                    case REBLOG_OID:
                        if (rebloggerActorId == 0) {
                            MyLog.e(TAG, method + ": actorId was not defined");
                        }
                        sql = "SELECT " + ActivityTable.ACTIVITY_OID + " FROM "
                                + ActivityTable.TABLE_NAME + " WHERE "
                                + ActivityTable.NOTE_ID + "=" + entityId + " AND "
                                + ActivityTable.ACTIVITY_TYPE + "=" + ActivityType.ANNOUNCE.id + " AND "
                                + ActivityTable.ACTOR_ID + "=" + rebloggerActorId;
                        break;
    
                    default:
                        throw new IllegalArgumentException(method + "; Unknown parameter: " + oe);
                }
                prog = db.compileStatement(sql);
                oid = prog.simpleQueryForString();
                
                if (StringUtils.isEmpty(oid) && oe == OidEnum.REBLOG_OID) {
                    // This not reblogged note
                    oid = idToOid(db, OidEnum.NOTE_OID, entityId, 0);
                }
                
            } catch (SQLiteDoneException e) {
                MyLog.ignored(TAG, e);
                oid = "";
            } catch (Exception e) {
                MyLog.e(TAG, method, e);
                oid = "";
            } finally {
                DbUtils.closeSilently(prog);
            }
            if (MyLog.isVerboseEnabled()) {
                MyLog.v(TAG, method + ": " + oe + " + " + entityId + " -> " + oid);
            }
        }
        return oid;
    }

    /** @return ID of the Reblog/Undo reblog activity and the type of the Activity */
    public static Pair<Long, ActivityType> noteIdToLastReblogging(SQLiteDatabase db, long noteId, long actorId) {
        return noteIdToLastOfTypes(db, noteId, actorId, ActivityType.ANNOUNCE, ActivityType.UNDO_ANNOUNCE);
    }

    /** @return ID of the last LIKE/UNDO_LIKE activity and the type of the activity */
    @NonNull
    public static Pair<Long, ActivityType> noteIdToLastFavoriting(SQLiteDatabase db, long noteId, long actorId) {
        return noteIdToLastOfTypes(db, noteId, actorId, ActivityType.LIKE, ActivityType.UNDO_LIKE);
    }

    /** @return ID of the last type1 or type2 activity and the type of the activity for the selected actor */
    @NonNull
    public static Pair<Long, ActivityType> noteIdToLastOfTypes(
            SQLiteDatabase db, long noteId, long actorId, ActivityType type1, ActivityType type2) {
        String method = "noteIdIdToLastOfTypes";
        if (db == null || noteId == 0 || actorId == 0) {
            return new Pair<>(0L, ActivityType.EMPTY);
        }
        String sql = "SELECT " + ActivityTable.ACTIVITY_TYPE + ", " + ActivityTable._ID
                + " FROM " + ActivityTable.TABLE_NAME
                + " WHERE " + ActivityTable.NOTE_ID + "=" + noteId + " AND "
                + ActivityTable.ACTIVITY_TYPE
                + " IN(" + type1.id + "," + type2.id + ") AND "
                + ActivityTable.ACTOR_ID + "=" + actorId
                + " ORDER BY " + ActivityTable.UPDATED_DATE + " DESC LIMIT 1";
        try (Cursor cursor = db.rawQuery(sql, null)) {
            if (cursor.moveToNext()) {
                return new Pair<>(cursor.getLong(1), ActivityType.fromId(cursor.getLong(0)));
            }
        } catch (Exception e) {
            MyLog.i(TAG, method + "; SQL:'" + sql + "'", e);
        }
        return new Pair<>(0L, ActivityType.EMPTY);
    }

    public static List<Actor> getStargazers(SQLiteDatabase db, @NonNull Origin origin, long noteId) {
        return noteIdToActors(db, origin, noteId, ActivityType.LIKE, ActivityType.UNDO_LIKE);
    }

    public static List<Actor> getRebloggers(SQLiteDatabase db, @NonNull Origin origin, long noteId) {
        return noteIdToActors(db, origin, noteId, ActivityType.ANNOUNCE, ActivityType.UNDO_ANNOUNCE);
    }

    /** @return for each actor (actorId is a key): ID of the last type1 or type2 activity
     *  and the type of the activity */
    @NonNull
    public static List<Actor> noteIdToActors(
            SQLiteDatabase db, @NonNull Origin origin, long noteId, ActivityType typeToReturn, ActivityType undoType) {
        String method = "noteIdToActors";
        final List<Long> foundActors = new ArrayList<>();
        final List<Actor> actors = new ArrayList<>();
        if (db == null || !origin.isValid() || noteId == 0) {
            return actors;
        }
        String sql = "SELECT " + ActivityTable.ACTIVITY_TYPE + ", " + ActivityTable.ACTOR_ID + ", "
                + ActorTable.WEBFINGER_ID + ", " + TimelineSql.usernameField() + " AS " + ActorTable.ACTIVITY_ACTOR_NAME
                + " FROM " + ActivityTable.TABLE_NAME + " INNER JOIN " + ActorTable.TABLE_NAME
                + " ON " + ActivityTable.ACTOR_ID + "=" + ActorTable.TABLE_NAME + "." + ActorTable._ID
                + " WHERE " + ActivityTable.NOTE_ID + "=" + noteId + " AND "
                + ActivityTable.ACTIVITY_TYPE + " IN(" + typeToReturn.id + "," + undoType.id + ")"
                + " ORDER BY " + ActivityTable.UPDATED_DATE + " DESC";
        try (Cursor cursor = db.rawQuery(sql, null)) {
            while(cursor.moveToNext()) {
                long actorId = DbUtils.getLong(cursor, ActivityTable.ACTOR_ID);
                if (!foundActors.contains(actorId)) {
                    foundActors.add(actorId);
                    ActivityType activityType = ActivityType.fromId(DbUtils.getLong(cursor, ActivityTable.ACTIVITY_TYPE));
                    if (activityType.equals(typeToReturn)) {
                        Actor actor = Actor.fromId(origin, actorId);
                        actor.setRealName(DbUtils.getString(cursor, ActorTable.ACTIVITY_ACTOR_NAME));
                        actor.setWebFingerId(DbUtils.getString(cursor, ActorTable.WEBFINGER_ID));
                        actors.add(actor);
                    }
                }
            }
        } catch (Exception e) {
            MyLog.w(TAG, method + "; SQL:'" + sql + "'", e);
        }
        return actors;
    }

    @NonNull
    public static ActorToNote favoritedAndReblogged(@NonNull MyContext myContext, long noteId, long actorId) {
        String method = "favoritedAndReblogged";
        boolean favoriteFound = false;
        boolean reblogFound = false;
        ActorToNote actorToNote = new ActorToNote();
        SQLiteDatabase db = myContext.getDatabase();
        if (db == null || noteId == 0 || actorId == 0) {
            return actorToNote;
        }
        String sql = "SELECT " + ActivityTable.ACTIVITY_TYPE + ", " + ActivityTable.SUBSCRIBED
                + " FROM " + ActivityTable.TABLE_NAME + " INNER JOIN " + ActorTable.TABLE_NAME
                + " ON " + ActivityTable.ACTOR_ID + "=" + ActorTable.TABLE_NAME + "." + ActorTable._ID
                + " WHERE " + ActivityTable.NOTE_ID + "=" + noteId + " AND "
                + ActivityTable.ACTOR_ID + "=" + actorId
                + " ORDER BY " + ActivityTable.UPDATED_DATE + " DESC";
        try (Cursor cursor = db.rawQuery(sql, null)) {
            while(cursor.moveToNext()) {
                if (DbUtils.getTriState(cursor, ActivityTable.SUBSCRIBED) == TriState.TRUE) {
                    actorToNote.subscribed = true;
                }
                ActivityType activityType = ActivityType.fromId(DbUtils.getLong(cursor, ActivityTable.ACTIVITY_TYPE));
                switch (activityType) {
                    case LIKE:
                    case UNDO_LIKE:
                        if (!favoriteFound) {
                            favoriteFound = true;
                            actorToNote.favorited = activityType == ActivityType.LIKE;
                        }
                        break;
                    case ANNOUNCE:
                    case UNDO_ANNOUNCE:
                        if (!reblogFound) {
                            reblogFound = true;
                            actorToNote.reblogged = activityType == ActivityType.ANNOUNCE;
                        }
                        break;
                    default:
                        break;
                }
            }
        } catch (Exception e) {
            MyLog.w(TAG, method + "; SQL:'" + sql + "'", e);
        }
        return actorToNote;
    }

    public static String noteIdToUsername(String actorIdColumnName, long noteId, ActorInTimeline actorInTimeline) {
        final String method = "noteIdToUsername";
        String username = "";
        if (noteId != 0) {
            SQLiteStatement prog = null;
            String sql = "";
            try {
                if (actorIdColumnName.contentEquals(ActivityTable.ACTOR_ID)) {
                    // TODO:
                    throw new IllegalArgumentException( method + "; Not implemented \"" + actorIdColumnName + "\"");
                } else if(actorIdColumnName.contentEquals(NoteTable.AUTHOR_ID) ||
                        actorIdColumnName.contentEquals(NoteTable.IN_REPLY_TO_ACTOR_ID)) {
                    sql = "SELECT " + usernameField(actorInTimeline) + " FROM " + ActorTable.TABLE_NAME
                            + " INNER JOIN " + NoteTable.TABLE_NAME + " ON "
                            + NoteTable.TABLE_NAME + "." + actorIdColumnName + "=" + ActorTable.TABLE_NAME + "." + BaseColumns._ID
                            + " WHERE " + NoteTable.TABLE_NAME + "." + BaseColumns._ID + "=" + noteId;
                } else {
                    throw new IllegalArgumentException( method + "; Unknown name \"" + actorIdColumnName + "\"");
                }
                SQLiteDatabase db = MyContextHolder.get().getDatabase();
                if (db == null) {
                    MyLog.databaseIsNull(() -> method);
                    return "";
                }
                prog = db.compileStatement(sql);
                username = prog.simpleQueryForString();
            } catch (SQLiteDoneException e) {
                MyLog.ignored(TAG, e);
                username = "";
            } catch (Exception e) {
                MyLog.e(TAG, method, e);
                username = "";
            } finally {
                DbUtils.closeSilently(prog);
            }
            if (MyLog.isVerboseEnabled()) {
                MyLog.v(TAG, method + "; " + actorIdColumnName + ": " + noteId + " -> " + username );
            }
        }
        return username;
    }

    @NonNull
    public static String actorIdToWebfingerId(MyContext myContext, long actorId) {
        return actorIdToName(myContext, actorId, ActorInTimeline.WEBFINGER_ID);
    }

    @NonNull
    public static String actorIdToName(@NonNull MyContext myContext, long actorId, ActorInTimeline actorInTimeline) {
        return idToStringColumnValue(myContext.getDatabase(), ActorTable.TABLE_NAME, usernameField(actorInTimeline), actorId);
    }

    /**
     * Convenience method to get column value from {@link ActorTable} table
     * @param columnName without table name
     * @param systemId {@link ActorTable#ACTOR_ID}
     * @return 0 in case not found or error
     */
    public static long actorIdToLongColumnValue(String columnName, long systemId) {
        return idToLongColumnValue(null, ActorTable.TABLE_NAME, columnName, systemId);
    }

    /** @return 0 if id == 0 or if not found */
    public static long idToLongColumnValue(SQLiteDatabase databaseIn, String tableName, String columnName, long systemId) {
        if (systemId == 0) {
            return 0;
        } else {
            return conditionToLongColumnValue(databaseIn, null, tableName, columnName, "t._id=" + systemId);
        }
    }

    /**
     * Convenience method to get long column value from the 'tableName' table
     * @param tableName e.g. {@link NoteTable#TABLE_NAME}
     * @param columnName without table name
     * @param condition WHERE part of SQL statement
     * @return 0 in case not found or error or systemId==0
     */
    public static long conditionToLongColumnValue(String tableName, String columnName, String condition) {
        return conditionToLongColumnValue(null, columnName, tableName, columnName, condition);
    }

    public static long conditionToLongColumnValue(SQLiteDatabase databaseIn, String msgLog,
                                                  String tableName, String columnName, String condition) {
        String sql = "SELECT t." + columnName +
                " FROM " + tableName + " AS t" +
                (StringUtils.isEmpty(condition) ? "" : " WHERE " + condition);
        long columnValue = 0;
        if (StringUtils.isEmpty(tableName)) {
            throw new IllegalArgumentException("tableName is empty: " + sql);
        } else if (StringUtils.isEmpty(columnName)) {
            throw new IllegalArgumentException("columnName is empty: " + sql);
        } else {
            columnValue = sqlToLong(databaseIn, msgLog, sql);
        }
        return columnValue;
    }

    @NonNull
    public static String noteIdToStringColumnValue(String columnName, long systemId) {
        return idToStringColumnValue(null, NoteTable.TABLE_NAME, columnName, systemId);
    }

    @NonNull
    public static String actorIdToStringColumnValue(String columnName, long systemId) {
        return idToStringColumnValue(null, ActorTable.TABLE_NAME, columnName, systemId);
    }

    /**
     * Convenience method to get String column value from the 'tableName' table
     *
     * @param db
     * @param tableName e.g. {@link NoteTable#TABLE_NAME}
     * @param columnName without table name
     * @param systemId tableName._id
     * @return not null; "" in a case not found or error or systemId==0
     */
    @NonNull
    public static String idToStringColumnValue(SQLiteDatabase db, String tableName, String columnName, long systemId) {
        return (systemId == 0) ? "" : conditionToStringColumnValue(db, tableName, columnName, "_id=" + systemId);
    }

    @NonNull
    public static String conditionToStringColumnValue(SQLiteDatabase dbIn, String tableName, String columnName, String condition) {
        String method = "cond2str";
        SQLiteDatabase db = dbIn == null ? MyContextHolder.get().getDatabase() : dbIn;
        if (db == null) {
            MyLog.databaseIsNull(() -> method);
            return "";
        }
        String sql = "SELECT " + columnName + " FROM " + tableName + " WHERE " + condition;
        String columnValue = "";
        if (StringUtils.isEmpty(tableName) || StringUtils.isEmpty(columnName)) {
            throw new IllegalArgumentException(method + " tableName or columnName are empty");
        } else if (StringUtils.isEmpty(columnName)) {
            throw new IllegalArgumentException("columnName is empty: " + sql);
        } else {
            try (SQLiteStatement prog = db.compileStatement(sql)) {
                columnValue = prog.simpleQueryForString();
            } catch (SQLiteDoneException e) {
                MyLog.ignored(TAG, e);
            } catch (Exception e) {
                MyLog.e(TAG, method + " table='" + tableName + "', column='" + columnName + "'", e);
                return "";
            }
            if (MyLog.isVerboseEnabled()) {
                MyLog.v(TAG, method + "; '" + sql + "' -> " + columnValue );
            }
        }
        return StringUtils.isEmpty(columnValue) ? "" : columnValue;
    }

    public static long noteIdToActorId(String noteActorIdColumnName, long systemId) {
        long actorId = 0;
        try {
            if (noteActorIdColumnName.contentEquals(ActivityTable.ACTOR_ID) ||
                    noteActorIdColumnName.contentEquals(NoteTable.AUTHOR_ID) ||
                    noteActorIdColumnName.contentEquals(NoteTable.IN_REPLY_TO_ACTOR_ID)) {
                actorId = noteIdToLongColumnValue(noteActorIdColumnName, systemId);
            } else {
                throw new IllegalArgumentException("noteIdToActorId; Illegal column '" + noteActorIdColumnName + "'");
            }
        } catch (Exception e) {
            MyLog.e(TAG, "noteIdToActorId", e);
            return 0;
        }
        return actorId;
    }

    public static long noteIdToOriginId(long systemId) {
        return noteIdToLongColumnValue(NoteTable.ORIGIN_ID, systemId);
    }

    public static TriState activityIdToTriState(String columnName, long systemId) {
        return TriState.fromId(activityIdToLongColumnValue(columnName, systemId));
    }

    /**
     * Convenience method to get column value from {@link ActivityTable} table
     * @param columnName without table name
     * @param systemId  MyDatabase.NOTE_TABLE_NAME + "." + Note._ID
     * @return 0 in case not found or error
     */
    public static long activityIdToLongColumnValue(String columnName, long systemId) {
        return idToLongColumnValue(null, ActivityTable.TABLE_NAME, columnName, systemId);
    }

    public static TriState noteIdToTriState(String columnName, long systemId) {
        return TriState.fromId(noteIdToLongColumnValue(columnName, systemId));
    }

    /**
     * Convenience method to get column value from {@link NoteTable} table
     * @param columnName without table name
     * @param systemId  NoteTable._ID
     * @return 0 in case not found or error
     */
    public static long noteIdToLongColumnValue(String columnName, long systemId) {
        switch (columnName) {
            case ActivityTable.ACTOR_ID:
            case ActivityTable.AUTHOR_ID:
            case ActivityTable.UPDATED_DATE:
            case ActivityTable.LAST_UPDATE_ID:
                return noteIdToLongActivityColumnValue(null, columnName, systemId);
            default:
                return idToLongColumnValue(null, NoteTable.TABLE_NAME, columnName, systemId);
        }
    }

    /** Data from the latest activity for this note... */
    public static long noteIdToLongActivityColumnValue(SQLiteDatabase databaseIn, String columnNameIn, long noteId) {
        final String method = "noteId2activity" + columnNameIn;
        final String columnName;
        final String condition;
        switch (columnNameIn) {
            case ActivityTable._ID:
            case ActivityTable.ACTOR_ID:
                columnName = columnNameIn;
                condition = ActivityTable.ACTIVITY_TYPE + " IN("
                        + ActivityType.CREATE.id + ","
                        + ActivityType.UPDATE.id + ","
                        + ActivityType.ANNOUNCE.id + ","
                        + ActivityType.LIKE.id + ")";
                break;
            case ActivityTable.AUTHOR_ID:
                columnName = ActivityTable.ACTOR_ID;
                condition = ActivityTable.ACTIVITY_TYPE + " IN("
                        + ActivityType.CREATE.id + ","
                        + ActivityType.UPDATE.id + ")";
                break;
            case ActivityTable.LAST_UPDATE_ID:
            case ActivityTable.UPDATED_DATE:
                columnName = columnNameIn.equals(ActivityTable.LAST_UPDATE_ID) ? ActivityTable._ID : columnNameIn;
                condition = ActivityTable.ACTIVITY_TYPE + " IN("
                        + ActivityType.CREATE.id + ","
                        + ActivityType.UPDATE.id + ","
                        + ActivityType.DELETE.id + ")";
                break;
            default:
                throw new IllegalArgumentException( method + "; Illegal column '" + columnNameIn + "'");
        }
        return MyQuery.conditionToLongColumnValue(databaseIn, method, ActivityTable.TABLE_NAME, columnName,
                ActivityTable.NOTE_ID + "=" + noteId +  " AND " + condition
                        + " ORDER BY " + ActivityTable.UPDATED_DATE + " DESC LIMIT 1");
    }

    public static long webFingerIdToId(MyContext myContext, long originId, String webFingerId, boolean checkOid) {
        return actorColumnValueToId(myContext, originId, ActorTable.WEBFINGER_ID, webFingerId, checkOid);
    }
    
    /**
     * Lookup the Actor's id based on the username in the Originating system
     * 
     * @param originId - see {@link NoteTable#ORIGIN_ID}, 0 - for all Origin-s
     * @param username - see {@link ActorTable#USERNAME}
     * @param checkOid true to try to retrieve a user with a realOid first
     * @return - id in our System (i.e. in the table, e.g.
     *         {@link ActorTable#_ID} ), 0 if not found
     */
    public static long usernameToId(MyContext myContext, long originId, String username, boolean checkOid) {
        return actorColumnValueToId(myContext, originId, ActorTable.USERNAME, username, checkOid);
    }

    private static long actorColumnValueToId(MyContext myContext, long originId, String columnName, String columnValue,
                                             boolean checkOid) {
        final String method = "actor" + columnName + "ToId";
        SQLiteDatabase db = myContext.getDatabase();
        if (db == null) {
            MyLog.databaseIsNull(() -> method);
            return 0;
        }
        long id = 0;
        SQLiteStatement prog = null;
        String sql = "";
        try {
            if (checkOid) {
                sql = sql4actorColumnValueToId(originId, columnName, columnValue, true);
                prog = db.compileStatement(sql);
                id = prog.simpleQueryForLong();
                if (id == 0) DbUtils.closeSilently(prog);
            }
            if (id == 0) {
                sql = sql4actorColumnValueToId(originId, columnName, columnValue, false);
                prog = db.compileStatement(sql);
                id = prog.simpleQueryForLong();
            }
        } catch (SQLiteDoneException e) {
            MyLog.ignored(MyQuery.TAG, e);
            id = 0;
        } catch (Exception e) {
            MyLog.e(MyQuery.TAG, method + ": SQL:'" + sql + "'", e);
            id = 0;
        } finally {
            DbUtils.closeSilently(prog);
        }
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(MyQuery.TAG, method + ":" + originId + "+" + columnValue + " -> " + id);
        }
        return id;
    }

    private static String sql4actorColumnValueToId(long originId, String columnName, String value, boolean checkOid) {
      return "SELECT " + ActorTable._ID +
                    " FROM " + ActorTable.TABLE_NAME +
                    " WHERE " +
                    (originId == 0 ? "" : ActorTable.ORIGIN_ID + "=" + originId + " AND ") +
                    (checkOid ? ActorTable.ACTOR_OID + " NOT LIKE('andstatustemp:%') AND " : "") +
                    columnName + "='" + value + "'" +
                    " ORDER BY " + ActorTable._ID;
    }

    public static boolean isGroupMember(MyContext myContext, long parentActorId, GroupType groupType, long memberId) {
        return getGroupMemberIds(myContext, parentActorId, groupType).contains(memberId);
    }

    @NonNull
    public static Set<Long> getGroupMemberIds(MyContext myContext, long parentActorId, GroupType groupType) {
        return getLongs(myContext, GroupMembership.selectMemberIds(parentActorId, groupType,false));
    }

    public static long getCountOfActivities(@NonNull String condition) {
        String sql = "SELECT COUNT(*) FROM " + ActivityTable.TABLE_NAME
                + (StringUtils.isEmpty(condition) ? "" : " WHERE " + condition);
        Set<Long> numbers = getLongs(sql);
        return numbers.isEmpty() ? 0 : numbers.iterator().next();
    }

    @NonNull
    public static Set<Long> getLongs(String sql) {
        return getLongs(MyContextHolder.get(), sql);
    }

    @NonNull
    public static Set<Long> getLongs(MyContext myContext, String sql) {
        return get(myContext, sql, cursor -> cursor.getLong(0));
    }

    /**
     * @return Empty set on UI thread
     */
    @NonNull
    public static <T> Set<T> get(@NonNull MyContext myContext, @NonNull String sql, Function<Cursor, T> fromCursor) {
        return foldLeft(myContext,
                    sql,
                    new HashSet<>(),
                    t -> cursor -> { t.add(fromCursor.apply(cursor)); return t; }
                );
    }

    /**
     * @return Empty list on UI thread
     */
    @NonNull
    public static <T> List<T> getList(@NonNull MyContext myContext, @NonNull String sql, Function<Cursor, T> fromCursor) {
        return foldLeft(myContext,
                sql,
                new ArrayList<>(),
                t -> cursor -> { t.add(fromCursor.apply(cursor)); return t; }
        );
    }

    /**
     * @return identity on UI thread
     */
    @NonNull
    public static <U> U foldLeft(@NonNull MyContext myContext, @NonNull String sql, @NonNull U identity,
                                 @NonNull Function<U, Function<Cursor, U>> f) {
        return foldLeft(myContext.getDatabase(), sql, identity, f);
    }

    /**
     * @return identity on UI thread
     */
    @NonNull
    public static <U> U foldLeft(SQLiteDatabase database, @NonNull String sql, @NonNull U identity,
                                 @NonNull Function<U, Function<Cursor, U>> f) {
        final String method = "foldLeft";
        if (database == null) {
            MyLog.databaseIsNull(() -> method);
            return identity;
        }
        if (MyAsyncTask.isUiThread()) {
            if (MyLog.isVerboseEnabled()) {
                MyLog.v(TAG, () -> method + "; Database access in UI thread: '" + sql + "'\n"
                        + MyLog.getStackTrace(new IllegalAccessException()));
            }
            return identity;
        }
        U result = identity;
        try (Cursor cursor = database.rawQuery(sql, null)) {
            while (cursor.moveToNext()) result = f.apply(result).apply(cursor);
        } catch (Exception e) {
            MyLog.i(TAG, method + "; SQL:'" + sql + "'", e);
        }
        return result;
    }

    public static String noteInfoForLog(MyContext myContext, long noteId) {
        MyStringBuilder builder = new MyStringBuilder();
        builder.withComma("noteId", noteId);
        String oid = idToOid(myContext, OidEnum.NOTE_OID, noteId, 0);
        builder.withCommaQuoted("oid", StringUtils.isEmpty(oid) ? "empty" : oid, StringUtils.nonEmpty(oid));
        String content = MyHtml.htmlToCompactPlainText(noteIdToStringColumnValue(NoteTable.CONTENT, noteId));
        builder.withCommaQuoted("content", content, true);
        Origin origin = myContext.origins().fromId(noteIdToLongColumnValue(NoteTable.ORIGIN_ID, noteId));
        builder.atNewLine(origin.toString());
        return builder.toString();
    }

    public static long conversationOidToId(long originId, String conversationOid) {
        return conditionToLongColumnValue(NoteTable.TABLE_NAME, NoteTable.CONVERSATION_ID,
                NoteTable.ORIGIN_ID + "=" + originId
                + " AND " + NoteTable.CONVERSATION_OID + "=" + quoteIfNotQuoted(conversationOid));
    }

    @NonNull
    public static String noteIdToConversationOid(MyContext myContext, long noteId) {
        if (noteId == 0) {
            return "";
        }
        String oid = noteIdToStringColumnValue(NoteTable.CONVERSATION_OID, noteId);
        if (!StringUtils.isEmpty(oid)) {
            return oid;
        }
        long conversationId = MyQuery.noteIdToLongColumnValue(NoteTable.CONVERSATION_ID, noteId);
        if (conversationId == 0) {
            return idToOid(myContext, OidEnum.NOTE_OID, noteId, 0);
        }
        oid = noteIdToStringColumnValue(NoteTable.CONVERSATION_OID, conversationId);
        if (!StringUtils.isEmpty(oid)) {
            return oid;
        }
        return idToOid(myContext, OidEnum.NOTE_OID, conversationId, 0);
    }

    public static boolean dExists(SQLiteDatabase db, String sql) {
        boolean exists = false;
        try (Cursor cursor = db.rawQuery(sql, null)) {
            exists = cursor.moveToFirst();
        } catch (Exception e) {
            MyLog.d("", "dExists \"" + sql + "\"", e);
        }
        return exists;
    }
}
