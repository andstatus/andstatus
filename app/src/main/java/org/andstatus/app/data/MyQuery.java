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
import android.support.annotation.NonNull;
import android.support.v4.util.Pair;
import android.text.TextUtils;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.UserInTimeline;
import org.andstatus.app.database.ActivityTable;
import org.andstatus.app.database.FriendshipTable;
import org.andstatus.app.database.MsgTable;
import org.andstatus.app.database.UserTable;
import org.andstatus.app.net.social.MbActivityType;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MyQuery {
    static final String TAG = MyQuery.class.getSimpleName();

    private MyQuery() {
        // Empty
    }

    static String userNameField(UserInTimeline userInTimeline) {
        switch (userInTimeline) {
            case AT_USERNAME:
                return "('@' || " + UserTable.USERNAME + ")";
            case WEBFINGER_ID:
                return UserTable.WEBFINGER_ID;
            case REAL_NAME:
                return UserTable.REAL_NAME;
            case REAL_NAME_AT_USERNAME:
                return "(" + UserTable.REAL_NAME + " || ' @' || " + UserTable.USERNAME + ")";
            default:
                return UserTable.USERNAME;
        }
    }

    /**
     * Lookup the System's (AndStatus) id from the Originated system's id
     * 
     * @param originId - see {@link MsgTable#ORIGIN_ID}
     * @param oid - see {@link MsgTable#MSG_OID}
     * @return - id in our System (i.e. in the table, e.g.
     *         {@link MsgTable#_ID} ). Or 0 if nothing was found.
     */
    public static long oidToId(OidEnum oidEnum, long originId, String oid) {
        return oidToId(null, oidEnum, originId, oid);
    }

    public static long oidToId(SQLiteDatabase database, OidEnum oidEnum, long originId, String oid) {
        if (TextUtils.isEmpty(oid)) {
            return 0;
        }
        String msgLog = "oidToId; " + oidEnum + ", origin=" + originId + ", oid=" + oid;
        String sql;
        switch (oidEnum) {
            case MSG_OID:
                sql = "SELECT " + BaseColumns._ID + " FROM " + MsgTable.TABLE_NAME
                        + " WHERE " + MsgTable.ORIGIN_ID + "=" + originId + " AND " + MsgTable.MSG_OID
                        + "=" + quoteIfNotQuoted(oid);
                break;
            case USER_OID:
                sql = "SELECT " + BaseColumns._ID + " FROM " + UserTable.TABLE_NAME
                        + " WHERE " + UserTable.ORIGIN_ID + "=" + originId + " AND " + UserTable.USER_OID
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
        return sqlToLong(database, msgLog, sql);
    }

    public static long sqlToLong(SQLiteDatabase databaseIn, String msgLogIn, String sql) {
        String msgLog = StringUtils.notNull(msgLogIn);
        SQLiteDatabase db = databaseIn == null ? MyContextHolder.get().getDatabase() : databaseIn;
        if (db == null) {
            MyLog.v(TAG, msgLog + "; database is null");
            return 0;
        }
        if (TextUtils.isEmpty(sql)) {
            MyLog.v(TAG, msgLog + "; sql is empty");
            return 0;
        }
        String msgLogSql = msgLog + (msgLog.contains(sql) ? "" : "; sql='" + sql +"'");
        long value = 0;
        SQLiteStatement statement = null;
        try {
            statement = db.compileStatement(sql);
            value = statement.simpleQueryForLong();
            if (value == 1 && MyLog.isVerboseEnabled()) {
                MyLog.v(TAG, msgLogSql);
            }
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
            MyLog.v(TAG, msgLog + " -> " + value);
        }
        return value;
    }

    /**
     * @return two single quotes for empty/null strings (Use single quotes!)
     */
    public static String quoteIfNotQuoted(String original) {
        if (TextUtils.isEmpty(original)) {
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
     * @param entityId - see {@link MsgTable#_ID}
     * @param rebloggerUserId Is needed to find reblog by this user
     * @return - oid in Originated system (i.e. in the table, e.g.
     *         {@link MsgTable#MSG_OID} empty string in case of an error
     */
    @NonNull
    public static String idToOid(OidEnum oe, long entityId, long rebloggerUserId) {
        SQLiteDatabase db = MyContextHolder.get().getDatabase();
        if (db == null) {
            MyLog.v(TAG, "idToOid: database is null, oe=" + oe + " id=" + entityId);
            return "";
        } else {
            return idToOid(db, oe, entityId, rebloggerUserId);
        }
    }

    /**
     * Lookup Originated system's id from the System's (AndStatus) id
     * 
     * @param oe what oid we need
     * @param entityId - see {@link MsgTable#_ID}
     * @param rebloggerUserId Is needed to find reblog by this user
     * @return - oid in Originated system (i.e. in the table, e.g.
     *         {@link MsgTable#MSG_OID} empty string in case of an error
     */
    @NonNull
    public static String idToOid(SQLiteDatabase db, OidEnum oe, long entityId, long rebloggerUserId) {
        String method = "idToOid";
        String oid = "";
        SQLiteStatement prog = null;
        String sql = "";
    
        if (entityId > 0) {
            try {
                switch (oe) {
                    case MSG_OID:
                        sql = "SELECT " + MsgTable.MSG_OID + " FROM "
                                + MsgTable.TABLE_NAME + " WHERE " + BaseColumns._ID + "=" + entityId;
                        break;
    
                    case USER_OID:
                        sql = "SELECT " + UserTable.USER_OID + " FROM "
                                + UserTable.TABLE_NAME + " WHERE " + BaseColumns._ID + "="
                                + entityId;
                        break;
    
                    case REBLOG_OID:
                        if (rebloggerUserId == 0) {
                            MyLog.e(TAG, method + ": userId was not defined");
                        }
                        sql = "SELECT " + ActivityTable.ACTIVITY_OID + " FROM "
                                + ActivityTable.TABLE_NAME + " WHERE "
                                + ActivityTable.MSG_ID + "=" + entityId + " AND "
                                + ActivityTable.ACTIVITY_TYPE + "=" + MbActivityType.ANNOUNCE.id + " AND "
                                + ActivityTable.ACTOR_ID + "=" + rebloggerUserId;
                        break;
    
                    default:
                        throw new IllegalArgumentException(method + "; Unknown parameter: " + oe);
                }
                prog = db.compileStatement(sql);
                oid = prog.simpleQueryForString();
                
                if (TextUtils.isEmpty(oid) && oe == OidEnum.REBLOG_OID) {
                    // This not reblogged message
                    oid = idToOid(db, OidEnum.MSG_OID, entityId, 0);
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
    public static Pair<Long, MbActivityType> msgIdToLastReblogging(SQLiteDatabase db, long msgId, long actorId) {
        return msgIdToLastOfTypes(db, msgId, actorId, MbActivityType.ANNOUNCE, MbActivityType.UNDO_ANNOUNCE);
    }

    /** @return ID of the last LIKE/UNDO_LIKE activity and the type of the activity */
    @NonNull
    public static Pair<Long, MbActivityType> msgIdToLastFavoriting(SQLiteDatabase db, long msgId, long actorId) {
        return msgIdToLastOfTypes(db, msgId, actorId, MbActivityType.LIKE, MbActivityType.UNDO_LIKE);
    }

    /** @return ID of the last type1 or type2 activity and the type of the activity for the selected user */
    @NonNull
    public static Pair<Long, MbActivityType> msgIdToLastOfTypes(
            SQLiteDatabase db, long msgId, long actorId, MbActivityType type1, MbActivityType type2) {
        String method = "msgIdToLastOfTypes";
        if (db == null || msgId == 0 || actorId == 0) {
            return new Pair<>(0L, MbActivityType.EMPTY);
        }
        String sql = "SELECT " + ActivityTable.ACTIVITY_TYPE + ", " + ActivityTable._ID
                + " FROM " + ActivityTable.TABLE_NAME
                + " WHERE " + ActivityTable.MSG_ID + "=" + msgId + " AND "
                + ActivityTable.ACTIVITY_TYPE
                + " IN(" + type1.id + "," + type2.id + ") AND "
                + ActivityTable.ACTOR_ID + "=" + actorId
                + " ORDER BY " + ActivityTable.UPDATED_DATE + " DESC LIMIT 1";
        try (Cursor cursor = db.rawQuery(sql, null)) {
            if (cursor.moveToNext()) {
                return new Pair<>(cursor.getLong(1), MbActivityType.fromId(cursor.getLong(0)));
            }
        } catch (Exception e) {
            MyLog.i(TAG, method + "; SQL:'" + sql + "'", e);
        }
        return new Pair<>(0L, MbActivityType.EMPTY);
    }

    public static List<MbUser> getStargazers(SQLiteDatabase db, long originId, long msgId) {
        return msgIdToActors(db, originId, msgId, MbActivityType.LIKE, MbActivityType.UNDO_LIKE);
    }

    public static List<MbUser> getRebloggers(SQLiteDatabase db, long originId, long msgId) {
        return msgIdToActors(db, originId, msgId, MbActivityType.ANNOUNCE, MbActivityType.UNDO_ANNOUNCE);
    }

    /** @return for each acted user (userId is a key): ID of the last type1 or type2 activity
     *  and the type of the activity */
    @NonNull
    public static List<MbUser> msgIdToActors(
            SQLiteDatabase db, long originId, long msgId, MbActivityType typeToReturn, MbActivityType undoType) {
        String method = "msgIdToLastOfTypes";
        final List<Long> foundActors = new ArrayList<>();
        final List<MbUser> users = new ArrayList<>();
        if (db == null || msgId == 0) {
            return users;
        }
        String sql = "SELECT " + ActivityTable.ACTIVITY_TYPE + ", " + ActivityTable.ACTOR_ID + ", "
                + UserTable.WEBFINGER_ID + ", " + TimelineSql.userNameField() + " AS " + UserTable.ACTOR_NAME
                + " FROM " + ActivityTable.TABLE_NAME + " INNER JOIN " + UserTable.TABLE_NAME
                + " ON " + ActivityTable.ACTOR_ID + "=" + UserTable.TABLE_NAME + "." + UserTable._ID
                + " WHERE " + ActivityTable.MSG_ID + "=" + msgId + " AND "
                + ActivityTable.ACTIVITY_TYPE + " IN(" + typeToReturn.id + "," + undoType.id + ")"
                + " ORDER BY " + ActivityTable.UPDATED_DATE + " DESC";
        try (Cursor cursor = db.rawQuery(sql, null)) {
            while(cursor.moveToNext()) {
                long actorId = DbUtils.getLong(cursor, ActivityTable.ACTOR_ID);
                if (!foundActors.contains(actorId)) {
                    foundActors.add(actorId);
                    MbActivityType activityType = MbActivityType.fromId(DbUtils.getLong(cursor, ActivityTable.ACTIVITY_TYPE));
                    if (activityType.equals(typeToReturn)) {
                        MbUser user = MbUser.fromOriginAndUserId(originId, actorId);
                        user.setRealName(DbUtils.getString(cursor, UserTable.ACTOR_NAME));
                        user.setWebFingerId(DbUtils.getString(cursor, UserTable.WEBFINGER_ID));
                        users.add(user);
                    }
                }
            }
        } catch (Exception e) {
            MyLog.w(TAG, method + "; SQL:'" + sql + "'", e);
        }
        return users;
    }

    @NonNull
    public static Pair<Boolean, Boolean> favoritedAndReblogged(
            SQLiteDatabase db, long msgId, long userId) {
        String method = "favoritedAndReblogged";
        boolean favoriteFound = false;
        boolean reblogFound = false;
        boolean favorited = false;
        boolean reblogged = false;
        if (db == null || msgId == 0 || userId == 0) {
            return new Pair<>(false, false);
        }
        String sql = "SELECT " + ActivityTable.ACTIVITY_TYPE
                + " FROM " + ActivityTable.TABLE_NAME + " INNER JOIN " + UserTable.TABLE_NAME
                + " ON " + ActivityTable.ACTOR_ID + "=" + UserTable.TABLE_NAME + "." + UserTable._ID
                + " WHERE " + ActivityTable.MSG_ID + "=" + msgId + " AND "
                + ActivityTable.ACTOR_ID + "=" + userId
                + " ORDER BY " + ActivityTable.UPDATED_DATE + " DESC";
        try (Cursor cursor = db.rawQuery(sql, null)) {
            while(cursor.moveToNext()) {
                MbActivityType activityType = MbActivityType.fromId(DbUtils.getLong(cursor, ActivityTable.ACTIVITY_TYPE));
                switch (activityType) {
                    case LIKE:
                    case UNDO_LIKE:
                        if (!favoriteFound) {
                            favoriteFound = true;
                            favorited = activityType == MbActivityType.LIKE;
                        }
                        break;
                    case ANNOUNCE:
                    case UNDO_ANNOUNCE:
                        if (!reblogFound) {
                            reblogFound = true;
                            reblogged = activityType == MbActivityType.ANNOUNCE;
                        }
                        break;
                    default:
                        break;
                }
            }
        } catch (Exception e) {
            MyLog.w(TAG, method + "; SQL:'" + sql + "'", e);
        }
        return new Pair<>(favorited, reblogged);
    }

    public static String msgIdToUsername(String userIdColumnName, long messageId, UserInTimeline userInTimeline) {
        final String method = "msgIdToUsername";
        String userName = "";
        if (messageId != 0) {
            SQLiteStatement prog = null;
            String sql = "";
            try {
                if (userIdColumnName.contentEquals(ActivityTable.ACTOR_ID)) {
                    // TODO:
                    throw new IllegalArgumentException( method + "; Not implemented \"" + userIdColumnName + "\"");
                } else if(userIdColumnName.contentEquals(MsgTable.AUTHOR_ID) ||
                        userIdColumnName.contentEquals(MsgTable.IN_REPLY_TO_USER_ID)) {
                    sql = "SELECT " + userNameField(userInTimeline) + " FROM " + UserTable.TABLE_NAME
                            + " INNER JOIN " + MsgTable.TABLE_NAME + " ON "
                            + MsgTable.TABLE_NAME + "." + userIdColumnName + "=" + UserTable.TABLE_NAME + "." + BaseColumns._ID
                            + " WHERE " + MsgTable.TABLE_NAME + "." + BaseColumns._ID + "=" + messageId;
                } else {
                    throw new IllegalArgumentException( method + "; Unknown name \"" + userIdColumnName + "\"");
                }
                SQLiteDatabase db = MyContextHolder.get().getDatabase();
                if (db == null) {
                    MyLog.v(TAG, method + "; Database is null");
                    return "";
                }
                prog = db.compileStatement(sql);
                userName = prog.simpleQueryForString();
            } catch (SQLiteDoneException e) {
                MyLog.ignored(TAG, e);
                userName = "";
            } catch (Exception e) {
                MyLog.e(TAG, method, e);
                userName = "";
            } finally {
                DbUtils.closeSilently(prog);
            }
            if (MyLog.isVerboseEnabled()) {
                MyLog.v(TAG, method + "; " + userIdColumnName + ": " + messageId + " -> " + userName );
            }
        }
        return userName;
    }

    public static String userIdToWebfingerId(long userId) {
        return userIdToName(userId, UserInTimeline.WEBFINGER_ID);
    }

    public static String userIdToName(long userId, UserInTimeline userInTimeline) {
        return idToStringColumnValue(UserTable.TABLE_NAME, userNameField(userInTimeline), userId);
    }

    /**
     * Convenience method to get column value from {@link UserTable} table
     * @param columnName without table name
     * @param systemId {@link UserTable#USER_ID}
     * @return 0 in case not found or error
     */
    public static long userIdToLongColumnValue(String columnName, long systemId) {
        return idToLongColumnValue(null, UserTable.TABLE_NAME, columnName, systemId);
    }

    public static long idToLongColumnValue(SQLiteDatabase databaseIn, String tableName, String columnName, long systemId) {
        if (systemId == 0) {
            return 0;
        } else {
            return conditionToLongColumnValue(databaseIn, null, tableName, columnName, "t._id=" + systemId);
        }
    }

    /**
     * Convenience method to get long column value from the 'tableName' table
     * @param tableName e.g. {@link MsgTable#TABLE_NAME}
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
                (TextUtils.isEmpty(condition) ? "" : " WHERE " + condition);
        long columnValue = 0;
        if (TextUtils.isEmpty(tableName)) {
            throw new IllegalArgumentException("tableName is empty: " + sql);
        } else if (TextUtils.isEmpty(columnName)) {
            throw new IllegalArgumentException("columnName is empty: " + sql);
        } else {
            columnValue = sqlToLong(databaseIn, msgLog, sql);
        }
        return columnValue;
    }

    @NonNull
    public static String msgIdToStringColumnValue(String columnName, long systemId) {
        return idToStringColumnValue(MsgTable.TABLE_NAME, columnName, systemId);
    }

    @NonNull
    public static String userIdToStringColumnValue(String columnName, long systemId) {
        return idToStringColumnValue(UserTable.TABLE_NAME, columnName, systemId);
    }

    /**
     * Convenience method to get String column value from the 'tableName' table
     * @param tableName e.g. {@link MsgTable#TABLE_NAME}
     * @param columnName without table name
     * @param systemId tableName._id
     * @return not null; "" in a case not found or error or systemId==0
     */
    @NonNull
    private static String idToStringColumnValue(String tableName, String columnName, long systemId) {
        return conditionToStringColumnValue(tableName, columnName, "_id=" + systemId);
    }

    @NonNull
    public static String conditionToStringColumnValue(String tableName, String columnName, String condition) {
        String method = "cond2str";
        SQLiteDatabase db = MyContextHolder.get().getDatabase();
        if (db == null) {
            MyLog.v(TAG, method + "; Database is null");
            return "";
        }
        String sql = "SELECT " + columnName + " FROM " + tableName + " WHERE " + condition;
        String columnValue = "";
        if (TextUtils.isEmpty(tableName) || TextUtils.isEmpty(columnName)) {
            throw new IllegalArgumentException(method + " tableName or columnName are empty");
        } else if (TextUtils.isEmpty(columnName)) {
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
        return TextUtils.isEmpty(columnValue) ? "" : columnValue;
    }

    public static long msgIdToUserId(String msgUserIdColumnName, long systemId) {
        long userId = 0;
        try {
            if (msgUserIdColumnName.contentEquals(ActivityTable.ACTOR_ID) ||
                    msgUserIdColumnName.contentEquals(MsgTable.AUTHOR_ID) ||
                    msgUserIdColumnName.contentEquals(MsgTable.IN_REPLY_TO_USER_ID)) {
                userId = msgIdToLongColumnValue(msgUserIdColumnName, systemId);
            } else {
                throw new IllegalArgumentException("msgIdToUserId; Illegal column '" + msgUserIdColumnName + "'");
            }
        } catch (Exception e) {
            MyLog.e(TAG, "msgIdToUserId", e);
            return 0;
        }
        return userId;
    }

    public static long msgIdToOriginId(long systemId) {
        return msgIdToLongColumnValue(MsgTable.ORIGIN_ID, systemId);
    }

    /**
     * Convenience method to get column value from {@link MsgTable} table
     * @param columnName without table name
     * @param systemId  MyDatabase.MSG_TABLE_NAME + "." + Msg._ID
     * @return 0 in case not found or error
     */
    public static long msgIdToLongColumnValue(String columnName, long systemId) {
        switch (columnName) {
            case ActivityTable.ACTOR_ID:
            case ActivityTable.AUTHOR_ID:
            case ActivityTable.UPDATED_DATE:
                return msgIdToLongActivityColumnValue(null, columnName, systemId);
            default:
                return idToLongColumnValue(null, MsgTable.TABLE_NAME, columnName, systemId);
        }
    }

    /** Data from the latest activity for this message... */
    public static long msgIdToLongActivityColumnValue(SQLiteDatabase databaseIn, String columnNameIn, long msgId) {
        final String method = "msgId2activity" + columnNameIn;
        final String columnName;
        final String condition;
        switch (columnNameIn) {
            case ActivityTable._ID:
            case ActivityTable.ACTOR_ID:
                columnName = ActivityTable.ACTOR_ID;
                condition = ActivityTable.ACTIVITY_TYPE + " IN("
                        + MbActivityType.CREATE.id + ","
                        + MbActivityType.UPDATE.id + ","
                        + MbActivityType.ANNOUNCE.id + ","
                        + MbActivityType.LIKE.id + ")";
                break;
            case ActivityTable.AUTHOR_ID:
            case ActivityTable.UPDATED_DATE:
                columnName = ActivityTable.ACTOR_ID;
                condition = ActivityTable.ACTIVITY_TYPE + " IN("
                        + MbActivityType.CREATE.id + ","
                        + MbActivityType.UPDATE.id + ","
                        + MbActivityType.DELETE.id + ")";
                break;
            default:
                throw new IllegalArgumentException( method + "; Illegal column '" + columnNameIn + "'");
        }
        return MyQuery.conditionToLongColumnValue(databaseIn, method, ActivityTable.TABLE_NAME, columnName,
                ActivityTable.MSG_ID + "=" + msgId +  " AND " + condition
                        + " ORDER BY " + ActivityTable.UPDATED_DATE + " DESC LIMIT 1");
    }

    public static long webFingerIdToId(long originId, String webFingerId) {
        return userColumnValueToId(originId, UserTable.WEBFINGER_ID, webFingerId);
    }
    
    /**
     * Lookup the User's id based on the Username in the Originating system
     * 
     * @param originId - see {@link MsgTable#ORIGIN_ID}
     * @param userName - see {@link UserTable#USERNAME}
     * @return - id in our System (i.e. in the table, e.g.
     *         {@link UserTable#_ID} ), 0 if not found
     */
    public static long userNameToId(long originId, String userName) {
        return userColumnValueToId(originId, UserTable.USERNAME, userName);
    }

    private static long userColumnValueToId(long originId, String columnName, String columnValue) {
        final String method = "user" + columnName + "ToId";
        SQLiteDatabase db = MyContextHolder.get().getDatabase();
        if (db == null) {
            MyLog.v(TAG, method + "; Database is null");
            return 0;
        }
        long id = 0;
        SQLiteStatement prog = null;
        String sql = "";
        try {
            sql = "SELECT " + BaseColumns._ID + " FROM " + UserTable.TABLE_NAME
                    + " WHERE " + UserTable.ORIGIN_ID + "=" + originId + " AND " + columnName + "='"
                    + columnValue + "'";
            prog = db.compileStatement(sql);
            id = prog.simpleQueryForLong();
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

    @NonNull
    public static Set<Long> getFollowersIds(long userId) {
        String where = FriendshipTable.FRIEND_ID + "=" + userId
                + " AND " + FriendshipTable.FOLLOWED + "=1";
        String sql = "SELECT " + FriendshipTable.USER_ID
                + " FROM " + FriendshipTable.TABLE_NAME
                + " WHERE " + where;
        return getLongs(sql);
    }

    @NonNull
    public static Set<Long> getFriendsIds(long userId) {
        String where = FriendshipTable.USER_ID + "=" + userId
                + " AND " + FriendshipTable.FOLLOWED + "=1";
        String sql = "SELECT " + FriendshipTable.FRIEND_ID
                + " FROM " + FriendshipTable.TABLE_NAME
                + " WHERE " + where;
        return getLongs(sql);
    }

    @NonNull
    public static Set<Long> getLongs(String sql) {
        final String method = "getLongs";
        Set<Long> ids = new HashSet<>();
        SQLiteDatabase db = MyContextHolder.get().getDatabase();
        if (db == null) {
            MyLog.v(TAG, method + "; Database is null");
            return ids;
        }
        try (Cursor c = db.rawQuery(sql, null)) {
            while (c.moveToNext()) {
                ids.add(c.getLong(0));
            }
        } catch (Exception e) {
            MyLog.i(TAG, method + "; SQL:'" + sql + "'", e);
        }
        return ids;
    }

    /**
     *  MyAccounts' userIDs, who follow the specified User
     */
    @NonNull
    public static Set<Long> getMyFollowersOf(long userId) {
        SelectedUserIds selectedAccounts = new SelectedUserIds(Timeline.EMPTY);

        String where = FriendshipTable.USER_ID + selectedAccounts.getSql()
                + " AND " + FriendshipTable.FRIEND_ID + "=" + userId
                + " AND " + FriendshipTable.FOLLOWED + "=1";
        String sql = "SELECT " + FriendshipTable.USER_ID
                + " FROM " + FriendshipTable.TABLE_NAME
                + " WHERE " + where;

        return getLongs(sql);
    }

    public static boolean isFollowing(long followerId, long friendId) {
        String where = FriendshipTable.USER_ID + "=" + followerId
                + " AND " + FriendshipTable.FRIEND_ID + "=" + friendId
                + " AND " + FriendshipTable.FOLLOWED + "=1";
        String sql = "SELECT " + FriendshipTable.USER_ID
                + " FROM " + FriendshipTable.TABLE_NAME
                + " WHERE " + where;

        return !getLongs(sql).isEmpty();
    }

    public static String msgInfoForLog(long msgId) {
        StringBuilder builder = new StringBuilder();
        I18n.appendWithComma(builder, "msgId:" + msgId);
        String oid = idToOid(OidEnum.MSG_OID, msgId, 0);
        I18n.appendWithComma(builder, "oid" + (TextUtils.isEmpty(oid) ? " is empty" : ":'" + oid + "'"));
        String body = MyHtml.fromHtml(msgIdToStringColumnValue(MsgTable.BODY, msgId));
        I18n.appendAtNewLine(builder, "text:'" + body + "'");
        Origin origin = MyContextHolder.get().persistentOrigins().fromId(msgIdToLongColumnValue(MsgTable.ORIGIN_ID, msgId));
        I18n.appendAtNewLine(builder, origin.toString());
        return builder.toString();
    }

    public static long conversationOidToId(long originId, String conversationOid) {
        return conditionToLongColumnValue(MsgTable.TABLE_NAME, MsgTable.CONVERSATION_ID,
                MsgTable.ORIGIN_ID + "=" + originId
                + " AND " + MsgTable.CONVERSATION_OID + "=" + quoteIfNotQuoted(conversationOid));
    }

    @NonNull
    public static String msgIdToConversationOid(long msgId) {
        if (msgId == 0) {
            return "";
        }
        String oid = msgIdToStringColumnValue(MsgTable.CONVERSATION_OID, msgId);
        if (!TextUtils.isEmpty(oid)) {
            return oid;
        }
        long conversationId = MyQuery.msgIdToLongColumnValue(MsgTable.CONVERSATION_ID, msgId);
        if (conversationId == 0) {
            return idToOid(OidEnum.MSG_OID, msgId, 0);
        }
        oid = msgIdToStringColumnValue(MsgTable.CONVERSATION_OID, conversationId);
        if (!TextUtils.isEmpty(oid)) {
            return oid;
        }
        return idToOid(OidEnum.MSG_OID, conversationId, 0);
    }
}
