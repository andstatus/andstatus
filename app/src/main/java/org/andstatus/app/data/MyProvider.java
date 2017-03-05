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

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.database.MsgOfUserTable;
import org.andstatus.app.database.MsgTable;
import org.andstatus.app.database.OriginTable;
import org.andstatus.app.database.UserTable;
import org.andstatus.app.msg.KeywordsFilter;
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
                count = deleteMessages(db, selection, selectionArgs);
                break;

            case MSG_ITEM:
                DownloadData.deleteAllOfThisMsg(db, uriParser.getMessageId());
                count = deleteMessages(db, BaseColumns._ID + "=" + uriParser.getMessageId(), null);
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

    private int deleteMessages(SQLiteDatabase db, String selection, String[] selectionArgs) {
        int count = 0;
        String sqlDesc = "";
        db.beginTransaction();
        try {
            // Delete all related records from MyDatabase.MsgOfUser for these messages
            String selectionG = " EXISTS ("
                    + "SELECT * FROM " + MsgTable.TABLE_NAME + " WHERE ("
                    + MsgTable.TABLE_NAME + "." + BaseColumns._ID + "=" + MsgOfUserTable.TABLE_NAME + "." + MsgOfUserTable.MSG_ID
                    + ") AND ("
                    + selection
                    + "))";
            String descSuffix = "; args=" + Arrays.toString(selectionArgs);
            sqlDesc = selectionG + descSuffix;
            count = db.delete(MsgOfUserTable.TABLE_NAME, selectionG, selectionArgs);
            // Now delete messages themselves
            sqlDesc = selection + descSuffix;
            count = db.delete(MsgTable.TABLE_NAME, selection, selectionArgs);
            db.setTransactionSuccessful();
        } catch(Exception e) {
            MyLog.d(TAG, "; SQL='" + sqlDesc + "'", e);
        } finally {
            db.endTransaction();
        }
        return count;
    }

    private int deleteUsers(SQLiteDatabase db, String selection, String[] selectionArgs) {
        int count;
        // TODO: Delete related records also... 
        count = db.delete(UserTable.TABLE_NAME, selection, selectionArgs);
        return count;
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
        MsgOfUserValues msgOfUserValues = new MsgOfUserValues(0);
        MsgOfUserValues otherUserValues = new MsgOfUserValues(0);
        FriendshipValues friendshipValues = null;
        long accountUserId = 0;
        
        long rowId;
        Uri newUri = null;
        try {
            Long now = System.currentTimeMillis();
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
                    /**
                     * Add default values for missed required fields
                     */
                    if (!values.containsKey(MsgTable.AUTHOR_ID) && values.containsKey(MsgTable.SENDER_ID)) {
                        values.put(MsgTable.AUTHOR_ID, values.get(MsgTable.SENDER_ID).toString());
                    }
                    if (!values.containsKey(MsgTable.BODY)) {
                        values.put(MsgTable.BODY, "");
                    }
                    if (!values.containsKey(MsgTable.VIA)) {
                        values.put(MsgTable.VIA, "");
                    }
                    values.put(MsgTable.INS_DATE, now);
                    
                    msgOfUserValues = MsgOfUserValues.valueOf(accountUserId, values);
                    otherUserValues = MsgOfUserValues.valuesOfOtherUser(values);
                    break;
                    
                case ORIGIN_ITEM:
                    table = OriginTable.TABLE_NAME;
                    break;

                case USER_ITEM:
                    table = UserTable.TABLE_NAME;
                    values.put(UserTable.INS_DATE, now);
                    accountUserId = uriParser.getAccountUserId();
                    friendshipValues = FriendshipValues.valueOf(accountUserId, 0, values);
                    break;
                    
                default:
                    throw new IllegalArgumentException(uriParser.toString());
            }

            rowId = db.insert(table, null, values);
            if (rowId == -1) {
                throw new SQLException("Failed to insert row into " + uri);
            } else if ( UserTable.TABLE_NAME.equals(table)) {
                optionallyLoadAvatar(rowId, values);
            }
            
            msgOfUserValues.setMsgId(rowId);
            msgOfUserValues.insert(db);
            otherUserValues.setMsgId(rowId);
            otherUserValues.insert(db);

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
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        boolean built = false;
        String selection = selectionIn;
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
                qb.appendWhere(ProjectionMap.MSG_TABLE_ALIAS + "." + BaseColumns._ID + "=" + uriParser.getMessageId());
                break;

            case TIMELINE_SEARCH:
                qb.setTables(TimelineSql.tablesForTimeline(uri, projection));
                qb.setProjectionMap(ProjectionMap.MSG);
                String rawQuery = uriParser.getSearchQuery();
                if (!TextUtils.isEmpty(rawQuery)) {
                    if (!TextUtils.isEmpty(selection)) {
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
                if (!TextUtils.isEmpty(selection)) {
                    sql += " WHERE " + selection;
                }
                break;

            case MSG:
                qb.setTables(MsgTable.TABLE_NAME + " AS " + ProjectionMap.MSG_TABLE_ALIAS);
                qb.setProjectionMap(ProjectionMap.MSG);
                break;

            case USER:
            case USERLIST:
                qb.setTables(UserListSql.tablesForList(uri, projection));
                qb.setProjectionMap(ProjectionMap.USER);
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
                    orderBy = MsgTable.DESC_SORT_ORDER;
                    break;

                case MSG_COUNT:
                    orderBy = "";
                    break;

                case USER:
                case USERLIST:
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
                    sql = qb.buildQuery(projection, selection, null, null, orderBy, null);
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
                MsgOfUserValues msgOfUserValues = MsgOfUserValues.valueOf(accountUserId, values);
                msgOfUserValues.setMsgId(rowId);
                MsgOfUserValues otherUserValues = MsgOfUserValues.valuesOfOtherUser(values);
                otherUserValues.setMsgId(rowId);
                if (values.size() > 0) {
                    count = db.update(MsgTable.TABLE_NAME, values, BaseColumns._ID + "=" + rowId
                            + (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : ""),
                            selectionArgs);
                }
                count += msgOfUserValues.update(db);
                otherUserValues.update(db);
                break;

            case USER:
                count = db.update(UserTable.TABLE_NAME, values, selection, selectionArgs);
                break;

            case USER_ITEM:
                accountUserId = uriParser.getAccountUserId();
                long selectedUserId = uriParser.getUserId();
                FriendshipValues friendshipValues = FriendshipValues.valueOf(accountUserId, selectedUserId, values);
                count = db.update(UserTable.TABLE_NAME, values, BaseColumns._ID + "=" + selectedUserId
                        + (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : ""),
                        selectionArgs);
                friendshipValues.update(db);
                optionallyLoadAvatar(selectedUserId, values);
                break;

            default:
                throw new IllegalArgumentException(uriParser.toString());
        }

        return count;
    }
}
