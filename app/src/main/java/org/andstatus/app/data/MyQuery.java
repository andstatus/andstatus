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

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteStatement;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.UserInTimeline;
import org.andstatus.app.data.MyDatabase.FollowingUser;
import org.andstatus.app.data.MyDatabase.Msg;
import org.andstatus.app.data.MyDatabase.MsgOfUser;
import org.andstatus.app.data.MyDatabase.OidEnum;
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MyQuery {
    static final String TAG = MyQuery.class.getSimpleName();

    private MyQuery() {
        // Empty
    }

    /**
     * Move boolean value of the key from valuesIn to valuesOut and remove it from valuesIn
     * @param key
     * @param valuesIn
     * @param valuesOut  may be null
     * @return 1 for true, 0 for false and 2 for "not present" 
     */
    protected static int moveBooleanKey(String key, String sourceSuffix, ContentValues valuesIn, ContentValues valuesOut) {
        int ret = 2;
        if (valuesIn != null && valuesIn.containsKey(key + sourceSuffix)) {
            ret = SharedPreferencesUtil.isTrueAsInt(valuesIn.get(key + sourceSuffix));
            valuesIn.remove(key + sourceSuffix);
            if (valuesOut != null) {
                valuesOut.put(key, ret);
            }
        }
        return ret;
    }

    /**
     * Move String value of the key from valuesIn to valuesOut and remove it from valuesIn
     * @param key
     * @param valuesIn
     * @param valuesOut  may be null
     */
    static void moveStringKey(String key, String sourceSuffix, ContentValues valuesIn, ContentValues valuesOut) {
        if (valuesIn != null && valuesIn.containsKey(key + sourceSuffix)) {
            String value = valuesIn.getAsString(key + sourceSuffix);
            valuesIn.remove(key + sourceSuffix);
            if (valuesOut != null) {
                valuesOut.put(key, value);
            }
        }
    }

    /**
     * Move String value of the key from valuesIn to valuesOut and remove it from valuesIn
     * @param key
     * @param valuesIn
     * @param valuesOut  may be null
     * @return Key value, 0 if not found
     */
    static long moveLongKey(String key, String sourceSuffix, ContentValues valuesIn, ContentValues valuesOut) {
        long keyValue = 0;
        if (valuesIn != null && valuesIn.containsKey(key + sourceSuffix)) {
            Long value = valuesIn.getAsLong(key + sourceSuffix);
            keyValue = value == null ? 0 : value;
            valuesIn.remove(key + sourceSuffix);
            if (valuesOut != null) {
                valuesOut.put(key, value);
            }
        }
        return keyValue;
    }

    static String userNameField(UserInTimeline userInTimeline) {
        switch (userInTimeline) {
            case AT_USERNAME:
                return "('@' || " + MyDatabase.User.USERNAME + ")";
            case WEBFINGER_ID:
                return MyDatabase.User.WEBFINGER_ID;
            case REAL_NAME:
                return MyDatabase.User.REAL_NAME;
            case REAL_NAME_AT_USERNAME:
                return "(" + MyDatabase.User.REAL_NAME + " || ' @' || " + MyDatabase.User.USERNAME + ")";
            default:
                return MyDatabase.User.USERNAME;
        }
    }

    /**
     * Lookup the System's (AndStatus) id from the Originated system's id
     * 
     * @param originId - see {@link MyDatabase.Msg#ORIGIN_ID}
     * @param oid - see {@link MyDatabase.Msg#MSG_OID}
     * @return - id in our System (i.e. in the table, e.g.
     *         {@link MyDatabase.Msg#_ID} ). Or 0 if nothing was found.
     */
    public static long oidToId(MyDatabase.OidEnum oidEnum, long originId, String oid) {
        return oidToId(null, oidEnum, originId, oid);
    }

    static long oidToId(SQLiteDatabase database, MyDatabase.OidEnum oidEnum, long originId, String oid) {
        if (TextUtils.isEmpty(oid)) {
            return 0;
        }
        String msgLog = "oidToId; " + originId + "+" + oid + ", oidEnum=" + oidEnum;
        String sql;
        switch (oidEnum) {
            case MSG_OID:
                sql = "SELECT " + BaseColumns._ID + " FROM " + Msg.TABLE_NAME
                        + " WHERE " + Msg.ORIGIN_ID + "=" + originId + " AND " + Msg.MSG_OID
                        + "=" + quoteIfNotQuoted(oid);
                break;

            case USER_OID:
                sql = "SELECT " + BaseColumns._ID + " FROM " + User.TABLE_NAME
                        + " WHERE " + User.ORIGIN_ID + "=" + originId + " AND " + User.USER_OID
                        + "=" + quoteIfNotQuoted(oid);
                break;

            default:
                throw new IllegalArgumentException(msgLog + "; Unknown oidEnum");
        }
        return sqlToLong(database, msgLog, sql);
    }

    private static long sqlToLong(SQLiteDatabase databaseIn, String msgLog, String sql) {
        SQLiteDatabase db = databaseIn == null ? MyContextHolder.get().getDatabase() : databaseIn;
        if (db == null) {
            MyLog.v(MyProvider.TAG, msgLog + "; database is null");
            return 0;
        }
        String msgLogSql = msgLog + "; sql='" + sql +"'";
        long value = 0;
        SQLiteStatement statement = null;
        try {
            statement = db.compileStatement(sql);
            value = statement.simpleQueryForLong();
            if ((value == 1 || value == 388)
                && MyLog.isVerboseEnabled()) {
                MyLog.v(MyProvider.TAG, msgLogSql);
            }
        } catch (SQLiteDoneException e) {
            MyLog.ignored(MyProvider.TAG, e);
            value = 0;
        } catch (Exception e) {
            MyLog.e(MyProvider.TAG, msgLogSql, e);
            value = 0;
        } finally {
            DbUtils.closeSilently(statement);
        }
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(MyProvider.TAG, msgLog + " -> " + value);
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
     * @param entityId - see {@link MyDatabase.Msg#_ID}
     * @param rebloggerUserId Is needed to find reblog by this user
     * @return - oid in Originated system (i.e. in the table, e.g.
     *         {@link MyDatabase.Msg#MSG_OID} empty string in case of an error
     */
    @NonNull
    public static String idToOid(OidEnum oe, long entityId, long rebloggerUserId) {
        SQLiteDatabase db = MyContextHolder.get().getDatabase();
        if (db == null) {
            MyLog.v(MyProvider.TAG, "idToOid: database is null, oe=" + oe + " id=" + entityId);
            return "";
        } else {
            return idToOid(db, oe, entityId, rebloggerUserId);
        }
    }

    /**
     * Lookup Originated system's id from the System's (AndStatus) id
     * 
     * @param oe what oid we need
     * @param entityId - see {@link MyDatabase.Msg#_ID}
     * @param rebloggerUserId Is needed to find reblog by this user
     * @return - oid in Originated system (i.e. in the table, e.g.
     *         {@link MyDatabase.Msg#MSG_OID} empty string in case of an error
     */
    @NonNull
    static String idToOid(SQLiteDatabase db, OidEnum oe, long entityId, long rebloggerUserId) {
        String method = "idToOid";
        String oid = "";
        SQLiteStatement prog = null;
        String sql = "";
    
        if (entityId > 0) {
            try {
                switch (oe) {
                    case MSG_OID:
                        sql = "SELECT " + MyDatabase.Msg.MSG_OID + " FROM "
                                + Msg.TABLE_NAME + " WHERE " + BaseColumns._ID + "=" + entityId;
                        break;
    
                    case USER_OID:
                        sql = "SELECT " + MyDatabase.User.USER_OID + " FROM "
                                + User.TABLE_NAME + " WHERE " + BaseColumns._ID + "="
                                + entityId;
                        break;
    
                    case REBLOG_OID:
                        if (rebloggerUserId == 0) {
                            MyLog.e(MyProvider.TAG, method + ": userId was not defined");
                        }
                        sql = "SELECT " + MyDatabase.MsgOfUser.REBLOG_OID + " FROM "
                                + MsgOfUser.TABLE_NAME + " WHERE " 
                                + MsgOfUser.MSG_ID + "=" + entityId + " AND "
                                + MsgOfUser.USER_ID + "=" + rebloggerUserId;
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
                MyLog.ignored(MyProvider.TAG, e);
                oid = "";
            } catch (Exception e) {
                MyLog.e(MyProvider.TAG, method, e);
                oid = "";
            } finally {
                DbUtils.closeSilently(prog);
            }
            if (MyLog.isVerboseEnabled()) {
                MyLog.v(MyProvider.TAG, method + ": " + oe + " + " + entityId + " -> " + oid);
            }
        }
        return oid;
    }

    public static String msgIdToUsername(String userIdColumnName, long messageId, UserInTimeline userInTimeline) {
        final String method = "msgIdToUsername";
        String userName = "";
        if (messageId != 0) {
            SQLiteStatement prog = null;
            String sql = "";
            try {
                if (userIdColumnName.contentEquals(MyDatabase.Msg.SENDER_ID) ||
                        userIdColumnName.contentEquals(MyDatabase.Msg.AUTHOR_ID) ||
                        userIdColumnName.contentEquals(MyDatabase.Msg.IN_REPLY_TO_USER_ID) ||
                        userIdColumnName.contentEquals(MyDatabase.Msg.RECIPIENT_ID)) {
                    sql = "SELECT " + userNameField(userInTimeline) + " FROM " + User.TABLE_NAME
                            + " INNER JOIN " + Msg.TABLE_NAME + " ON "
                            + Msg.TABLE_NAME + "." + userIdColumnName + "=" + User.TABLE_NAME + "." + BaseColumns._ID
                            + " WHERE " + Msg.TABLE_NAME + "." + BaseColumns._ID + "=" + messageId;
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
                MyLog.ignored(MyProvider.TAG, e);
                userName = "";
            } catch (Exception e) {
                MyLog.e(MyProvider.TAG, method, e);
                userName = "";
            } finally {
                DbUtils.closeSilently(prog);
            }
            if (MyLog.isVerboseEnabled()) {
                MyLog.v(MyProvider.TAG, method + "; " + userIdColumnName + ": " + messageId + " -> " + userName );
            }
        }
        return userName;
    }

    public static String userIdToWebfingerId(long userId) {
        return userIdToName(userId, UserInTimeline.WEBFINGER_ID);
    }

    public static String userIdToName(long userId, UserInTimeline userInTimeline) {
        return idToStringColumnValue(MyDatabase.User.TABLE_NAME, userNameField(userInTimeline), userId);
    }

    /**
     * Convenience method to get column value from {@link MyDatabase.User} table
     * @param columnName without table name
     * @param systemId {@link MyDatabase.User#USER_ID}
     * @return 0 in case not found or error
     */
    public static long userIdToLongColumnValue(String columnName, long systemId) {
        return idToLongColumnValue(User.TABLE_NAME, columnName, systemId);
    }

    private static long idToLongColumnValue(String tableName, String columnName, long systemId) {
        if (systemId == 0) {
            return 0;
        } else {
            return conditionToLongColumnValue(tableName, columnName, "t._id=" + systemId);
        }
    }

    /**
     * Convenience method to get long column value from the 'tableName' table
     * @param tableName e.g. {@link Msg#TABLE_NAME} 
     * @param columnName without table name
     * @param condition WHERE part of SQL statement
     * @return 0 in case not found or error or systemId==0
     */
    public static long conditionToLongColumnValue(String tableName, String columnName, String condition) {
        final String method = "conditionToLongColumnValue";
        String msgLog = method + "; table='" + tableName + "', column='" + columnName + "'"
                + " where '" + condition + "'";
        long columnValue = 0;
        if (TextUtils.isEmpty(tableName) || TextUtils.isEmpty(columnName)) {
            throw new IllegalArgumentException(msgLog + " tableName or columnName are empty");
        } else if (!TextUtils.isEmpty(condition)) {
            String sql = "SELECT t." + columnName
                    + " FROM " + tableName + " AS t"
                    + " WHERE " + condition;
            columnValue = sqlToLong(null, msgLog, sql);
        }
        return columnValue;
    }

    @NonNull
    public static String msgIdToStringColumnValue(String columnName, long systemId) {
        return idToStringColumnValue(Msg.TABLE_NAME, columnName, systemId);
    }

    @NonNull
    public static String userIdToStringColumnValue(String columnName, long systemId) {
        return idToStringColumnValue(User.TABLE_NAME, columnName, systemId);
    }

    /**
     * Convenience method to get String column value from the 'tableName' table
     * @param tableName e.g. {@link Msg#TABLE_NAME} 
     * @param columnName without table name
     * @param systemId tableName._id
     * @return not null; "" in a case not found or error or systemId==0
     */
    @NonNull
    private static String idToStringColumnValue(String tableName, String columnName, long systemId) {
        String method = "idToStringColumnValue";
        String columnValue = "";
        if (TextUtils.isEmpty(tableName) || TextUtils.isEmpty(columnName)) {
            throw new IllegalArgumentException(method + " tableName or columnName are empty");
        } else if (systemId != 0) {
            SQLiteStatement prog = null;
            String sql = "";
            try {
                sql = "SELECT t." + columnName
                        + " FROM " + tableName + " AS t"
                        + " WHERE t._id=" + systemId;
                SQLiteDatabase db = MyContextHolder.get().getDatabase();
                if (db == null) {
                    MyLog.v(TAG, method + "; Database is null");
                    return "";
                }
                prog = db.compileStatement(sql);
                columnValue = prog.simpleQueryForString();
            } catch (SQLiteDoneException e) {
                MyLog.ignored(MyProvider.TAG, e);
            } catch (Exception e) {
                MyLog.e(MyProvider.TAG, method + " table='" + tableName 
                        + "', column='" + columnName + "'", e);
                return "";
            } finally {
                DbUtils.closeSilently(prog);
            }
            if (MyLog.isVerboseEnabled()) {
                MyLog.v(MyProvider.TAG, method + " table=" + tableName + ", column=" + columnName + ", id=" + systemId + " -> " + columnValue );
            }
        }
        return TextUtils.isEmpty(columnValue) ? "" : columnValue;
    }

    public static long msgIdToUserId(String msgUserIdColumnName, long systemId) {
        long userId = 0;
        try {
            if (msgUserIdColumnName.contentEquals(MyDatabase.Msg.SENDER_ID) ||
                    msgUserIdColumnName.contentEquals(MyDatabase.Msg.AUTHOR_ID) ||
                    msgUserIdColumnName.contentEquals(MyDatabase.Msg.IN_REPLY_TO_USER_ID) ||
                    msgUserIdColumnName.contentEquals(MyDatabase.Msg.RECIPIENT_ID)) {
                userId = msgIdToLongColumnValue(msgUserIdColumnName, systemId);
            } else {
                throw new IllegalArgumentException("msgIdToUserId; Unknown name \"" + msgUserIdColumnName);
            }
        } catch (Exception e) {
            MyLog.e(MyProvider.TAG, "msgIdToUserId", e);
            return 0;
        }
        return userId;
    }

    public static long msgIdToOriginId(long systemId) {
        return msgIdToLongColumnValue(Msg.ORIGIN_ID, systemId);
    }

    /**
     * Convenience method to get column value from {@link MyDatabase.Msg} table
     * @param columnName without table name
     * @param systemId  MyDatabase.MSG_TABLE_NAME + "." + Msg._ID
     * @return 0 in case not found or error
     */
    public static long msgIdToLongColumnValue(String columnName, long systemId) {
        return idToLongColumnValue(Msg.TABLE_NAME, columnName, systemId);
    }

    public static long webFingerIdToId(long originId, String webFingerId) {
        return userColumnValueToId(originId, User.WEBFINGER_ID, webFingerId);
    }
    
    /**
     * Lookup the User's id based on the Username in the Originating system
     * 
     * @param originId - see {@link MyDatabase.Msg#ORIGIN_ID}
     * @param userName - see {@link MyDatabase.User#USERNAME}
     * @return - id in our System (i.e. in the table, e.g.
     *         {@link MyDatabase.User#_ID} ), 0 if not found
     */
    public static long userNameToId(long originId, String userName) {
        return userColumnValueToId(originId, User.USERNAME, userName);
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
            sql = "SELECT " + BaseColumns._ID + " FROM " + User.TABLE_NAME
                    + " WHERE " + User.ORIGIN_ID + "=" + originId + " AND " + columnName + "='"
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
    public static Set<Long> getIdsOfUsersFollowing(long userId) {
        String where = MyDatabase.FollowingUser.FOLLOWED_USER_ID + "=" + userId
                + " AND " + MyDatabase.FollowingUser.USER_FOLLOWED + "=1";
        String sql = "SELECT " + MyDatabase.FollowingUser.USER_ID
                + " FROM " + FollowingUser.TABLE_NAME
                + " WHERE " + where;
        return getLongs(sql);
    }

    @NonNull
    public static Set<Long> getIdsOfUsersFollowedBy(long userId) {
        String where = MyDatabase.FollowingUser.USER_ID + "=" + userId
                + " AND " + MyDatabase.FollowingUser.USER_FOLLOWED + "=1";
        String sql = "SELECT " + MyDatabase.FollowingUser.FOLLOWED_USER_ID
                + " FROM " + FollowingUser.TABLE_NAME 
                + " WHERE " + where;
        return getLongs(sql);
    }

    @NonNull
    private static Set<Long> getLongs(String sql) {
        Set<Long> ids = new HashSet<>();
        SQLiteDatabase db = MyContextHolder.get().getDatabase();
        if (db == null) {
            MyLog.v(TAG, "getLongs; Database is null");
            return ids;
        }
        Cursor c = null;
        try {
            c = db.rawQuery(sql, null);
            while (c.moveToNext()) {
                ids.add(c.getLong(0));
            }
        } finally {
            DbUtils.closeSilently(c);
        }
        return ids;
    }

    /**
     * IDs of MyAccounts' userIDs, who follow the specified User
     */
    @NonNull
    public static Set<Long> getMyFollowersOf(long userId) {
        SelectedUserIds selectedAccounts = new SelectedUserIds(true,
                MyContextHolder.get().persistentAccounts().getCurrentAccountUserId());

        String where = MyDatabase.FollowingUser.USER_ID + selectedAccounts.getSql()
                + " AND " + MyDatabase.FollowingUser.FOLLOWED_USER_ID + "=" + userId
                + " AND " + MyDatabase.FollowingUser.USER_FOLLOWED + "=1";
        String sql = "SELECT " + FollowingUser.USER_ID
                + " FROM " + FollowingUser.TABLE_NAME
                + " WHERE " + where;

        return getLongs(sql);
    }

    /**
     * Newest replies are the first
     */
    public static List<Long> getReplyIds(long msgId) {
        List<Long> replies = new ArrayList<>();
        String sql = "SELECT " + MyDatabase.Msg._ID 
                + " FROM " + Msg.TABLE_NAME 
                + " WHERE " + MyDatabase.Msg.IN_REPLY_TO_MSG_ID + "=" + msgId
                + " ORDER BY " + Msg.CREATED_DATE + " DESC";
        
        SQLiteDatabase db = MyContextHolder.get().getDatabase();
        if (db == null) {
            MyLog.v(TAG, "getReplyIds; Database is null");
            return replies;
        }
        Cursor c = null;
        try {
            c = db.rawQuery(sql, null);
            while (c.moveToNext()) {
                replies.add(c.getLong(0));
            }
        } finally {
            DbUtils.closeSilently(c);
        }
        return replies;
    }

    public static List<Long> getRebloggers(long msgId) {
        List<Long> rebloggers = new ArrayList<>();
        String sql = "SELECT " + MsgOfUser.USER_ID
                + " FROM " + MsgOfUser.TABLE_NAME
                + " WHERE " + MsgOfUser.MSG_ID + "=" + msgId
                + " AND " + MsgOfUser.REBLOGGED + "=1";

        SQLiteDatabase db = MyContextHolder.get().getDatabase();
        if (db == null) {
            MyLog.v(TAG, "getRebloggers; Database is null");
            return rebloggers;
        }
        Cursor c = null;
        try {
            c = db.rawQuery(sql, null);
            while (c.moveToNext()) {
                rebloggers.add(c.getLong(0));
            }
        } finally {
            DbUtils.closeSilently(c);
        }
        return rebloggers;
    }
}
