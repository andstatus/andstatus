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

package org.andstatus.app.data;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.database.ActivityTable;
import org.andstatus.app.database.AudienceTable;
import org.andstatus.app.database.MsgTable;
import org.andstatus.app.database.OriginTable;
import org.andstatus.app.database.UserTable;
import org.andstatus.app.msg.KeywordsFilter;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtils;

import java.util.Arrays;

/**
 * Database provider for the MyDatabase database.
 * 
 * The code of this application accesses this class through {@link android.content.ContentResolver}.
 * ContentResolver in it's turn accesses this class.
 * 
 */
public class MyProvider extends ContentProvider {
    static final String TAG = MyProvider.class.getSimpleName();
    
    /**
     * @see android.content.ContentProvider#onCreate()
     */
    @Override
    public boolean onCreate() {
        MyContextHolder.storeContextIfNotPresent(getContext(), this);
        return MyContextHolder.get().isReady();
    }

    /**
     * Get MIME type of the content, used for the supplied Uri
     * For discussion how this may be used see:
     * <a href="http://stackoverflow.com/questions/5351669/why-use-contentprovider-gettype-to-get-mime-type">Why use ContentProvider.getType() to get MIME type</a>
     */
    @Override
    public String getType(@NonNull Uri uri) {
        return MatchedUri.fromUri(uri).getMimeType();
    }

    /**
     * Delete a record(s) from the database.
     * 
     * @see android.content.ContentProvider#delete(android.net.Uri,
     *      java.lang.String, java.lang.String[])
     */
    @Override
    public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
        SQLiteDatabase db = MyContextHolder.get().getDatabase();
        if (db == null) {
            MyLog.v(this, "delete; Database is null");
            return 0;
        }
        int count;
        ParsedUri uriParser = ParsedUri.fromUri(uri);
        switch (uriParser.matched()) {
            case MSG:
                count = deleteMessages(db, selection, selectionArgs, false);
                break;

            case MSG_ITEM:
                count = deleteMessage(db, uriParser.getMessageId(), false);
                break;
                
            case USER:
                count = deleteUsers(db, selection, selectionArgs);
                break;

            case USER_ITEM:
                count = deleteUsers(db, BaseColumns._ID + "=" + uriParser.getUserId(), null);
                break;

            default:
                throw new IllegalArgumentException(uriParser.toString());
        }
        return count;
    }

    private static int deleteMessage(SQLiteDatabase db, long msgId, boolean inTransaction) {
        int count;
        DownloadData.deleteAllOfThisMsg(db, msgId);
        count = deleteMessages(db, BaseColumns._ID + "=" + msgId, null, inTransaction);
        return count;
    }

    private static int deleteMessages(SQLiteDatabase db, String selection, String[] selectionArgs, boolean inTransaction) {
        int count = 0;
        String sqlDesc = "";
        if (!inTransaction) {
            db.beginTransaction();
        }
        try {
            String descSuffix = "; args=" + Arrays.toString(selectionArgs);
            // Delete all related records

            // Audience
            String selectionG = " EXISTS ("
                    + "SELECT * FROM " + MsgTable.TABLE_NAME + " WHERE ("
                    + MsgTable.TABLE_NAME + "." + BaseColumns._ID + "=" + AudienceTable.TABLE_NAME + "." + AudienceTable.MSG_ID
                    + ") AND ("
                    + selection
                    + "))";
            sqlDesc = selectionG + descSuffix;
            count = db.delete(AudienceTable.TABLE_NAME, selectionG, selectionArgs);

            // Activities
            selectionG = " EXISTS ("
                    + "SELECT * FROM " + MsgTable.TABLE_NAME + " WHERE ("
                    + MsgTable.TABLE_NAME + "." + BaseColumns._ID + "=" + ActivityTable.TABLE_NAME + "." + ActivityTable.MSG_ID
                    + ") AND ("
                    + selection
                    + "))";
            sqlDesc = selectionG + descSuffix;
            count = db.delete(ActivityTable.TABLE_NAME, selectionG, selectionArgs);

            // Now delete messages themselves
            sqlDesc = selection + descSuffix;
            count = db.delete(MsgTable.TABLE_NAME, selection, selectionArgs);
            if (!inTransaction) {
                db.setTransactionSuccessful();
            }
        } catch(Exception e) {
            MyLog.d(TAG, "; SQL='" + sqlDesc + "'", e);
        } finally {
            if (!inTransaction) {
                db.endTransaction();
            }
        }
        return count;
    }

    private int deleteUsers(SQLiteDatabase db, String selection, String[] selectionArgs) {
        int count;
        // TODO: Delete related records also... 
        count = db.delete(UserTable.TABLE_NAME, selection, selectionArgs);
        return count;
    }

    public static int deleteActivity(MyContext myContext, long activityId, long msgId, boolean inTransaction) {
        SQLiteDatabase db = MyContextHolder.get().getDatabase();
        if (db == null) {
            MyLog.v(MyProvider.TAG, "deleteReblog; Database is null");
            return 0;
        }
        int count = db.delete(ActivityTable.TABLE_NAME, BaseColumns._ID + "=" + activityId, null);
        if (count > 0 && msgId != 0) {
            // Was this the last activity for this message?
            long activityId2 = MyQuery.conditionToLongColumnValue(db, null, ActivityTable.TABLE_NAME,
                    BaseColumns._ID, ActivityTable.MSG_ID + "=" + msgId);
            if (activityId2 == 0) {
                // Delete message if no more its activities left
                deleteMessage(db, msgId, inTransaction);
            } else {
                updateMessageReblogged(myContext, msgId);
            }
        }
        return count;
    }

    public static void updateMessageReblogged(MyContext myContext, long msgId) {
        final String method = "updateMessageReblogged-" + msgId;
        SQLiteDatabase db = MyContextHolder.get().getDatabase();
        if (db == null) {
            MyLog.v(MyProvider.TAG, method + "; Database is null");
            return;
        }
        // TODO: Implement
    }

    public static void updateMessageFavorited(MyContext myContext, long originId, long msgId) {
        final String method = "updateMessageFavorited-" + msgId;
        SQLiteDatabase db = myContext.getDatabase();
        if (db == null) {
            MyLog.v(MyProvider.TAG, method + "; Database is null");
            return;
        }
        int favorited = 0;
        for (MbUser stargazer : MyQuery.getStargazers(db, originId, msgId)) {
            if (myContext.persistentAccounts().fromUser(stargazer).isValid()) {
                favorited = 1;
                break;
            }
        }
        String sql = "UPDATE " + MsgTable.TABLE_NAME + " SET " + MsgTable.FAVORITED + "=" + favorited
                + " WHERE " + MsgTable._ID + "=" + msgId;
        try {
            db.execSQL(sql);
        } catch (Exception e) {
            MyLog.w(TAG, method + "; SQL:'" + sql + "'", e);
        }
    }

    /**
     * Insert a new record into the database.
     * 
     * @see android.content.ContentProvider#insert(android.net.Uri,
     *      android.content.ContentValues)
     */
    @Override
    public Uri insert(@NonNull Uri uri, ContentValues initialValues) {

        ContentValues values;
        FriendshipValues friendshipValues = null;
        long accountUserId = 0;
        
        long rowId;
        Uri newUri = null;
        try {
            SQLiteDatabase db = MyContextHolder.get().getDatabase();
            if (db == null) {
                MyLog.v(this, "insert; Database is null");
                return null;
            }

            String table;

            if (initialValues != null) {
                values = new ContentValues(initialValues);
            } else {
                values = new ContentValues();
            }

            ParsedUri uriParser = ParsedUri.fromUri(uri);
            switch (uriParser.matched()) {
                case MSG_ITEM:
                    accountUserId = uriParser.getAccountUserId();
                    
                    table = MsgTable.TABLE_NAME;
                    if (!values.containsKey(MsgTable.BODY)) {
                        values.put(MsgTable.BODY, "");
                    }
                    if (!values.containsKey(MsgTable.VIA)) {
                        values.put(MsgTable.VIA, "");
                    }
                    values.put(MsgTable.INS_DATE, MyLog.uniqueCurrentTimeMS());
                    
                    break;
                    
                case ORIGIN_ITEM:
                    table = OriginTable.TABLE_NAME;
                    break;

                case USER_ITEM:
                    table = UserTable.TABLE_NAME;
                    values.put(UserTable.INS_DATE, MyLog.uniqueCurrentTimeMS());
                    accountUserId = uriParser.getAccountUserId();
                    friendshipValues = FriendshipValues.valueOf(accountUserId, 0, values);
                    break;
                    
                default:
                    throw new IllegalArgumentException(uriParser.toString());
            }

            rowId = db.insert(table, null, values);
            if (rowId == -1) {
                throw new SQLException("Failed to insert row into " + uri);
            }
            if ( UserTable.TABLE_NAME.equals(table)) {
                optionallyLoadAvatar(rowId, values);
            }
            
            if (friendshipValues != null) {
                friendshipValues.friendId =  rowId;
                friendshipValues.update(db);
            }

            switch (uriParser.matched()) {
                case MSG_ITEM:
                    newUri = MatchedUri.getMsgUri(accountUserId, rowId);
                    break;
                case ORIGIN_ITEM:
                    newUri = MatchedUri.getOriginUri(rowId);
                    break;
                case USER_ITEM:
                    newUri = MatchedUri.getUserUri(accountUserId, rowId);
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
          MyLog.e(this, "Insert " + uri, e);
        }
        return newUri;
    }

    private void optionallyLoadAvatar(long userId, ContentValues values) {
        if (MyPreferences.getShowAvatars() && values.containsKey(UserTable.AVATAR_URL)) {
            AvatarData.getForUser(userId).requestDownload();
        }
    }
    
    /**
     * Get a cursor to the database
     * 
     * @see android.content.ContentProvider#query(android.net.Uri,
     *      java.lang.String[], java.lang.String, java.lang.String[],
     *      java.lang.String)
     */
    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selectionIn, String[] selectionArgsIn,
            String sortOrder) {
        final int PAGE_SIZE = 400;
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        boolean built = false;
        String selection = selectionIn;
        String limit = null;
        String[] selectionArgs = selectionArgsIn; 
        String sql = "";

        ParsedUri uriParser = ParsedUri.fromUri(uri);
        switch (uriParser.matched()) {
            case TIMELINE:
                qb.setDistinct(true);
                qb.setTables(TimelineSql.tablesForTimeline(uri, projection));
                qb.setProjectionMap(ProjectionMap.MSG);
                break;

            case TIMELINE_ITEM:
                qb.setTables(TimelineSql.tablesForTimeline(uri, projection));
                qb.setProjectionMap(ProjectionMap.MSG);
                qb.appendWhere(ProjectionMap.ACTIVITY_TABLE_ALIAS + "."
                        + ActivityTable.MSG_ID + "=" + uriParser.getMessageId());
                break;

            case TIMELINE_SEARCH:
                qb.setTables(TimelineSql.tablesForTimeline(uri, projection));
                qb.setProjectionMap(ProjectionMap.MSG);
                String rawQuery = uriParser.getSearchQuery();
                if (StringUtils.nonEmpty(rawQuery)) {
                    if (StringUtils.nonEmpty(selection)) {
                        selection = " AND (" + selection + ")";
                    } else {
                        selection = "";
                    }
                    KeywordsFilter searchQuery  = new KeywordsFilter(rawQuery);
                    // TODO: Search in MyDatabase.User.USERNAME also
                    selection = "(" + UserTable.AUTHOR_NAME + " LIKE ?  OR "
                            + searchQuery.getSqlSelection(MsgTable.BODY_TO_SEARCH)
                            + ")" + selection;

                    selectionArgs = searchQuery.prependSqlSelectionArgs(selectionArgs);
                    selectionArgs = StringUtils.addBeforeArray(selectionArgs, "%" + rawQuery + "%");
                }
                break;

            case MSG_COUNT:
                sql = "SELECT count(*) FROM " + MsgTable.TABLE_NAME + " AS " + ProjectionMap.MSG_TABLE_ALIAS;
                if (StringUtils.nonEmpty(selection)) {
                    sql += " WHERE " + selection;
                }
                break;

            case MSG:
                qb.setTables(MsgTable.TABLE_NAME + " AS " + ProjectionMap.MSG_TABLE_ALIAS);
                qb.setProjectionMap(ProjectionMap.MSG);
                break;

            case USER:
            case USERLIST:
            case USERLIST_SEARCH:
                qb.setTables(UserListSql.tablesForList(uri, projection));
                qb.setProjectionMap(ProjectionMap.USER);
                rawQuery = uriParser.getSearchQuery();
                if (StringUtils.nonEmpty(rawQuery)) {
                    if (StringUtils.nonEmpty(selection)) {
                        selection = " AND (" + selection + ")";
                    } else {
                        selection = "";
                    }
                    selection = "(" + UserTable.WEBFINGER_ID + " LIKE ?" +
                            " OR " + UserTable.REAL_NAME + " LIKE ? )" + selection;

                    selectionArgs = StringUtils.addBeforeArray(selectionArgs, "%" + rawQuery + "%");
                    selectionArgs = StringUtils.addBeforeArray(selectionArgs, "%" + rawQuery + "%");
                }
                limit =  String.valueOf(PAGE_SIZE);
                break;

            case USERLIST_ITEM:
                qb.setTables(UserListSql.tablesForList(uri, projection));
                qb.setProjectionMap(ProjectionMap.USER);
                qb.appendWhere(BaseColumns._ID + "=" + uriParser.getUserId());
                break;

            case USER_ITEM:
                qb.setTables(UserTable.TABLE_NAME);
                qb.setProjectionMap(ProjectionMap.USER);
                qb.appendWhere(BaseColumns._ID + "=" + uriParser.getUserId());
                break;

            default:
                throw new IllegalArgumentException(uriParser.toString());
        }

        // If no sort order is specified use the default
        String orderBy;
        if (TextUtils.isEmpty(sortOrder)) {
            switch (uriParser.matched()) {
                case TIMELINE:
                case TIMELINE_ITEM:
                case TIMELINE_SEARCH:
                    orderBy = ActivityTable.getTimeSortOrder(uriParser.getTimelineType(), false);
                    break;

                case MSG_COUNT:
                    orderBy = "";
                    break;

                case USER:
                case USERLIST:
                case USERLIST_ITEM:
                case USERLIST_SEARCH:
                case USER_ITEM:
                    orderBy = UserTable.DEFAULT_SORT_ORDER;
                    break;

                default:
                    throw new IllegalArgumentException(uriParser.toString());
            }
        } else {
            orderBy = sortOrder;
        }

        Cursor c = null;
        if (MyContextHolder.get().isReady()) {
            // Get the database and run the query
            SQLiteDatabase db = MyContextHolder.get().getDatabase();
            boolean logQuery = MyLog.isVerboseEnabled();
            try {
                if (sql.length() == 0) {
                    sql = qb.buildQuery(projection, selection, null, null, orderBy, limit);
                    built = true;
                }
                // Here we substitute ?-s in selection with values from selectionArgs
                c = db.rawQuery(sql, selectionArgs);
                if (c == null) {
                    MyLog.e(this, "Null cursor returned");
                    logQuery = true;
                }
            } catch (Exception e) {
                logQuery = true;
                MyLog.e(this, "Database query failed", e);
            }

            if (logQuery) {
                String msg = "query, SQL=\"" + sql + "\"";
                if (selectionArgs != null && selectionArgs.length > 0) {
                    msg += "; selectionArgs=" + Arrays.toString(selectionArgs);
                }
                MyLog.v(TAG, msg);
                if (built) {
                    msg = "uri=" + uri + "; projection=" + Arrays.toString(projection)
                    + "; selection=" + selection + "; sortOrder=" + sortOrder
                    + "; qb.getTables=" + qb.getTables() + "; orderBy=" + orderBy;
                    MyLog.v(TAG, msg);
                }
            }
        }

        if (c != null) {
            c.setNotificationUri(getContext().getContentResolver(), uri);
        }
        return c;
    }

    /**
     * Update objects (one or several records) in the database
     */
    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        SQLiteDatabase db = MyContextHolder.get().getDatabase();
        if (db == null) {
            MyLog.v(this, "update; Database is null");
            return 0;
        }
        int count = 0;
        ParsedUri uriParser = ParsedUri.fromUri(uri);
        long accountUserId;
        switch (uriParser.matched()) {
            case MSG:
                count = db.update(MsgTable.TABLE_NAME, values, selection, selectionArgs);
                break;

            case MSG_ITEM:
                accountUserId = uriParser.getAccountUserId();
                long rowId = uriParser.getMessageId();
                if (values.size() > 0) {
                    count = db.update(MsgTable.TABLE_NAME, values, BaseColumns._ID + "=" + rowId
                            + (StringUtils.nonEmpty(selection) ? " AND (" + selection + ')' : ""),
                            selectionArgs);
                }
                break;

            case USER:
                count = db.update(UserTable.TABLE_NAME, values, selection, selectionArgs);
                break;

            case USER_ITEM:
                accountUserId = uriParser.getAccountUserId();
                long selectedUserId = uriParser.getUserId();
                FriendshipValues friendshipValues = FriendshipValues.valueOf(accountUserId, selectedUserId, values);
                if (values.size() > 0) {
                    count = db.update(UserTable.TABLE_NAME, values, BaseColumns._ID + "=" + selectedUserId
                                    + (StringUtils.nonEmpty(selection) ? " AND (" + selection + ')' : ""),
                            selectionArgs);
                }
                friendshipValues.update(db);
                optionallyLoadAvatar(selectedUserId, values);
                break;

            default:
                throw new IllegalArgumentException(uriParser.toString());
        }

        return count;
    }
}
