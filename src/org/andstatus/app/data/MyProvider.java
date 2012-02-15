/* 
 * Copyright (C) 2008 Torgny Bjers
 * Copyright (C) 2012 yvolk (Yuri Volkov), http://yurivolkov.com
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

import java.util.Arrays;
import java.util.HashMap;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import org.andstatus.app.TimelineActivity;
import org.andstatus.app.data.MyDatabase.DirectMessages;
import org.andstatus.app.data.MyDatabase.Tweets;
import org.andstatus.app.data.MyDatabase.Users;
import org.andstatus.app.util.MyLog;

/**
 * Database provider for the MyDatabase database.
 * 
 * The code of this application accesses this class through {@link android.content.ContentResolver}.
 * ContentResolver in it's turn accesses this class.
 * 
 */
public class MyProvider extends ContentProvider {

    private static final String TAG = MyProvider.class.getSimpleName();

    private static HashMap<String, String> sTweetsProjectionMap;

    private static HashMap<String, String> sUsersProjectionMap;

    private static HashMap<String, String> sDirectMessagesProjectionMap;

    /**
     * "Authority", represented by this ContentProvider subclass 
     *   and declared in the application's manifest.
     *   
     * As Android documentation states:
     * "The authority therefore must be unique. 
     *  Typically, as in this example, it's the fully qualified name of a ContentProvider subclass.
     *  The path part of a URI may be used by a content provider to identify particular data subsets,
     *  but those paths are not declared in the manifest."
     * (see <a href="http://developer.android.com/guide/topics/manifest/provider-element.html">&lt;provider&gt;</a>)
     */
    public static final String AUTHORITY = MyProvider.class.getName();

    private static final UriMatcher sUriMatcher;
    /**
     * Matched codes, returned by {@link UriMatcher#match(Uri)}
     */
    private static final int TWEETS = 1;
    private static final int TWEETS_COUNT = 2;
    private static final int TWEETS_SEARCH = 3;
    private static final int TWEET_ID = 4;
    private static final int USERS = 5;
    private static final int USER_ID = 6;
    private static final int DIRECTMESSAGES = 7;
    private static final int DIRECTMESSAGE_ID = 8;
    private static final int DIRECTMESSAGES_COUNT = 9;

    /**
     * @see android.content.ContentProvider#onCreate()
     */
    @Override
    public boolean onCreate() {
        MyPreferences.initialize(getContext(), this);
        return (MyPreferences.getDatabase() == null) ? false : true;
    }

    /**
     * Get MIME type of the content, used for the supplied Uri
     * For discussion how this may be used see:
     * <a href="http://stackoverflow.com/questions/5351669/why-use-contentprovider-gettype-to-get-mime-type">Why use ContentProvider.getType() to get MIME type</a>
     * 
     * @see android.content.ContentProvider#getType(android.net.Uri)
     */
    @Override
    public String getType(Uri uri) {
        switch (sUriMatcher.match(uri)) {
            case TWEETS:
            case TWEETS_SEARCH:
            case TWEETS_COUNT:
                return Tweets.CONTENT_TYPE;

            case TWEET_ID:
                return Tweets.CONTENT_ITEM_TYPE;

            case DIRECTMESSAGES:
                return DirectMessages.CONTENT_TYPE;

            case DIRECTMESSAGE_ID:
                return DirectMessages.CONTENT_ITEM_TYPE;

            case USERS:
                return Users.CONTENT_TYPE;

            case USER_ID:
                return Users.CONTENT_ITEM_TYPE;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    /**
     * Delete a record from the database.
     * 
     * @see android.content.ContentProvider#delete(android.net.Uri,
     *      java.lang.String, java.lang.String[])
     */
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        SQLiteDatabase db = MyPreferences.getDatabase().getWritableDatabase();
        int count;
        switch (sUriMatcher.match(uri)) {
            case TWEETS:
                count = db.delete(MyDatabase.TWEETS_TABLE_NAME, selection, selectionArgs);
                break;

            case TWEET_ID:
                String tweetId = uri.getPathSegments().get(1);
                count = db.delete(MyDatabase.TWEETS_TABLE_NAME, Tweets._ID + "=" + tweetId
                        + (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : ""),
                        selectionArgs);
                break;

            case DIRECTMESSAGES:
                count = db.delete(MyDatabase.DIRECTMESSAGES_TABLE_NAME, selection, selectionArgs);
                break;

            case DIRECTMESSAGE_ID:
                String messageId = uri.getPathSegments().get(1);
                count = db.delete(MyDatabase.DIRECTMESSAGES_TABLE_NAME, DirectMessages._ID + "=" + messageId
                        + (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : ""),
                        selectionArgs);
                break;

            case USERS:
                count = db.delete(MyDatabase.USERS_TABLE_NAME, selection, selectionArgs);
                break;

            case USER_ID:
                String userId = uri.getPathSegments().get(1);
                count = db.delete(MyDatabase.USERS_TABLE_NAME, Users._ID + "=" + userId
                        + (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : ""),
                        selectionArgs);
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    /**
     * Insert a new record into the database.
     * 
     * @see android.content.ContentProvider#insert(android.net.Uri,
     *      android.content.ContentValues)
     */
    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {

        ContentValues values;
        long rowId;
        // 2010-07-21 yvolk: "now" is calculated exactly like it is in other
        // parts of the code
        Long now = System.currentTimeMillis();
        SQLiteDatabase db = MyPreferences.getDatabase().getWritableDatabase();

        String table;
        String nullColumnHack;
        Uri contentUri;

        if (initialValues != null) {
            values = new ContentValues(initialValues);
        } else {
            values = new ContentValues();
        }

        switch (sUriMatcher.match(uri)) {
            case TWEETS:
                table = MyDatabase.TWEETS_TABLE_NAME;
                nullColumnHack = Tweets.MESSAGE;
                contentUri = Tweets.CONTENT_URI;
                /**
                 * Add default values for missed required fields
                 */
                if (values.containsKey(Tweets.CREATED_DATE) == false)
                    values.put(Tweets.CREATED_DATE, now);
                if (values.containsKey(Tweets.SENT_DATE) == false)
                    values.put(Tweets.SENT_DATE, now);
                if (values.containsKey(Tweets.AUTHOR_ID) == false)
                    values.put(Tweets.AUTHOR_ID, "");
                if (values.containsKey(Tweets.MESSAGE) == false)
                    values.put(Tweets.MESSAGE, "");
                if (values.containsKey(Tweets.SOURCE) == false)
                    values.put(Tweets.SOURCE, "");
                if (values.containsKey(Tweets.TWEET_TYPE) == false)
                    values.put(Tweets.TWEET_TYPE, TimelineActivity.TIMELINE_TYPE_HOME);
                if (values.containsKey(Tweets.IN_REPLY_TO_AUTHOR_ID) == false)
                    values.put(Tweets.IN_REPLY_TO_AUTHOR_ID, "");
                if (values.containsKey(Tweets.FAVORITED) == false)
                    values.put(Tweets.FAVORITED, 0);
                break;

            case DIRECTMESSAGES:
                table = MyDatabase.DIRECTMESSAGES_TABLE_NAME;
                nullColumnHack = DirectMessages.MESSAGE;
                contentUri = DirectMessages.CONTENT_URI;
                if (values.containsKey(DirectMessages.CREATED_DATE) == false)
                    values.put(DirectMessages.CREATED_DATE, now);
                if (values.containsKey(DirectMessages.SENT_DATE) == false)
                    values.put(DirectMessages.SENT_DATE, now);
                if (values.containsKey(DirectMessages.AUTHOR_ID) == false)
                    values.put(DirectMessages.AUTHOR_ID, "");
                if (values.containsKey(DirectMessages.MESSAGE) == false)
                    values.put(DirectMessages.MESSAGE, "");
                break;

            case USERS:
                table = MyDatabase.USERS_TABLE_NAME;
                nullColumnHack = Users.AUTHOR_ID;
                contentUri = Users.CONTENT_URI;
                if (values.containsKey(Users.MODIFIED_DATE) == false)
                    values.put(Users.MODIFIED_DATE, now);
                if (values.containsKey(Users.CREATED_DATE) == false)
                    values.put(Users.CREATED_DATE, now);
                if (values.containsKey(Users.AUTHOR_ID) == false)
                    values.put(Users.AUTHOR_ID, "");
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        rowId = db.insert(table, nullColumnHack, values);
        if (rowId > 0) {
            Uri newUri = ContentUris.withAppendedId(contentUri, rowId);
            return newUri;
        }

        throw new SQLException("Failed to insert row into " + uri);
    }

    /**
     * Get a cursor to the database
     * 
     * @see android.content.ContentProvider#query(android.net.Uri,
     *      java.lang.String[], java.lang.String, java.lang.String[],
     *      java.lang.String)
     */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        String sql = "";

        int matchedCode = sUriMatcher.match(uri);
        switch (matchedCode) {
            case TWEETS:
                qb.setTables(MyDatabase.TWEETS_TABLE_NAME);
                qb.setProjectionMap(sTweetsProjectionMap);
                break;

            case TWEETS_COUNT:
                sql = "SELECT count(*) FROM " + MyDatabase.TWEETS_TABLE_NAME;
                if (selection != null && selection.length() > 0) {
                    sql += " WHERE " + selection;
                }
                break;

            case TWEET_ID:
                qb.setTables(MyDatabase.TWEETS_TABLE_NAME);
                qb.setProjectionMap(sTweetsProjectionMap);
                qb.appendWhere(Tweets._ID + "=" + uri.getPathSegments().get(1));
                break;

            case TWEETS_SEARCH:
                qb.setTables(MyDatabase.TWEETS_TABLE_NAME);
                qb.setProjectionMap(sTweetsProjectionMap);
                String s1 = uri.getLastPathSegment();
                if (s1 != null) {
                    // These two lines don't work:
                    // qb.appendWhere(Tweets.AUTHOR_ID + " LIKE '%" + s1 +
                    // "%' OR " + Tweets.MESSAGE + " LIKE '%" + s1 + "%'");
                    // qb.appendWhere(Tweets.AUTHOR_ID + " LIKE \"%" + s1 +
                    // "%\" OR " + Tweets.MESSAGE + " LIKE \"%" + s1 + "%\"");
                    // ...so we have to use selectionArgs

                    // 1. This works:
                    // qb.appendWhere(Tweets.AUTHOR_ID + " LIKE ?  OR " +
                    // Tweets.MESSAGE + " LIKE ?");

                    // 2. This works also, but yvolk likes it more :-)
                    if (selection != null && selection.length() > 0) {
                        selection = " AND (" + selection + ")";
                    } else {
                        selection = "";
                    }
                    selection = "(" + Tweets.AUTHOR_ID + " LIKE ?  OR " + Tweets.MESSAGE
                            + " LIKE ?)" + selection;

                    selectionArgs = addBeforeArray(selectionArgs, "%" + s1 + "%");
                    selectionArgs = addBeforeArray(selectionArgs, "%" + s1 + "%");
                }
                break;

            case DIRECTMESSAGES:
                qb.setTables(MyDatabase.DIRECTMESSAGES_TABLE_NAME);
                qb.setProjectionMap(sDirectMessagesProjectionMap);
                break;

            case DIRECTMESSAGE_ID:
                qb.setTables(MyDatabase.DIRECTMESSAGES_TABLE_NAME);
                qb.setProjectionMap(sDirectMessagesProjectionMap);
                qb.appendWhere(DirectMessages._ID + "=" + uri.getPathSegments().get(1));
                break;

            case DIRECTMESSAGES_COUNT:
                sql = "SELECT count(*) FROM " + MyDatabase.DIRECTMESSAGES_TABLE_NAME;
                if (selection != null && selection.length() > 0) {
                    sql += " WHERE " + selection;
                }
                break;

            case USERS:
                qb.setTables(MyDatabase.USERS_TABLE_NAME);
                qb.setProjectionMap(sUsersProjectionMap);
                break;

            case USER_ID:
                qb.setTables(MyDatabase.USERS_TABLE_NAME);
                qb.setProjectionMap(sUsersProjectionMap);
                qb.appendWhere(Users._ID + "=" + uri.getPathSegments().get(1));
                break;

            default:
                throw new IllegalArgumentException("Unknown URI \"" + uri + "\"; matchedCode="
                        + matchedCode);
        }

        // If no sort order is specified use the default
        String orderBy;
        if (TextUtils.isEmpty(sortOrder)) {
            switch (matchedCode) {
                case TWEETS:
                case TWEET_ID:
                    orderBy = Tweets.DEFAULT_SORT_ORDER;
                    break;

                case TWEETS_COUNT:
                case DIRECTMESSAGES_COUNT:
                    orderBy = "";
                    break;

                case DIRECTMESSAGES:
                case DIRECTMESSAGE_ID:
                    orderBy = DirectMessages.DEFAULT_SORT_ORDER;
                    break;

                case USERS:
                case USER_ID:
                    orderBy = Users.DEFAULT_SORT_ORDER;
                    break;

                default:
                    throw new IllegalArgumentException("Unknown URI \"" + uri + "\"; matchedCode="
                            + matchedCode);
            }
        } else {
            orderBy = sortOrder;
        }

        // Get the database and run the query
        SQLiteDatabase db = MyPreferences.getDatabase().getReadableDatabase();
        Cursor c = null;
        boolean logQuery = MyLog.isLoggable(TAG, Log.VERBOSE);
        try {
            if (sql.length() > 0) {
                c = db.rawQuery(sql, selectionArgs);
            } else {
                c = qb.query(db, projection, selection, selectionArgs, null, null, orderBy);
            }
        } catch (Exception e) {
            logQuery = true;
            Log.e(TAG, "Database query failed");
            e.printStackTrace();
        }

        if (logQuery) {
            if (sql.length() > 0) {
                Log.v(TAG, "query, SQL=\"" + sql + "\"");
                if (selectionArgs != null && selectionArgs.length > 0) {
                    Log.v(TAG, "; selectionArgs=" + Arrays.toString(selectionArgs));
                }
            } else {
                Log.v(TAG, "query, uri=" + uri + "; projection=" + Arrays.toString(projection));
                Log.v(TAG, "; selection=" + selection);
                Log.v(TAG, "; selectionArgs=" + Arrays.toString(selectionArgs) + "; sortOrder="
                        + sortOrder);
                Log.v(TAG, "; qb.getTables=" + qb.getTables() + "; orderBy=" + orderBy);
            }
        }
        
        if (c != null) {
            // Tell the cursor what Uri to watch, so it knows when its source data
            // changes
            c.setNotificationUri(getContext().getContentResolver(), uri);
        }
        return c;
    }

    private static String[] addBeforeArray(String[] array, String s) {
        int length = 0;
        if (array != null) {
            length = array.length;
        }
        String ans[] = new String[length + 1];
        if (length > 0) {
            System.arraycopy(array, 0, ans, 1, array.length);
        }
        ans[0] = s;
        return ans;
    }

    /**
     * Update a record in the database
     * 
     * @see android.content.ContentProvider#update(android.net.Uri,
     *      android.content.ContentValues, java.lang.String, java.lang.String[])
     */
    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        SQLiteDatabase db = MyPreferences.getDatabase().getWritableDatabase();
        int count;
        switch (sUriMatcher.match(uri)) {
            case TWEETS:
                count = db.update(MyDatabase.TWEETS_TABLE_NAME, values, selection, selectionArgs);
                break;

            case TWEET_ID:
                String noteId = uri.getPathSegments().get(1);
                count = db.update(MyDatabase.TWEETS_TABLE_NAME, values, Tweets._ID + "=" + noteId
                        + (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : ""),
                        selectionArgs);
                break;

            case DIRECTMESSAGES:
                count = db.update(MyDatabase.DIRECTMESSAGES_TABLE_NAME, values, selection, selectionArgs);
                break;

            case DIRECTMESSAGE_ID:
                String messageId = uri.getPathSegments().get(1);
                count = db.update(MyDatabase.DIRECTMESSAGES_TABLE_NAME, values, DirectMessages._ID + "="
                        + messageId
                        + (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : ""),
                        selectionArgs);
                break;

            case USERS:
                count = db.update(MyDatabase.USERS_TABLE_NAME, values, selection, selectionArgs);
                break;

            case USER_ID:
                String userId = uri.getPathSegments().get(1);
                count = db.update(MyDatabase.USERS_TABLE_NAME, values, Users._ID + "=" + userId
                        + (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : ""),
                        selectionArgs);
                break;

            default:
                throw new IllegalArgumentException("Unknown URI \"" + uri + "\"");
        }

        return count;
    }

    // Static Definitions for UriMatcher and Projection Maps
    static {
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

        sUriMatcher.addURI(AUTHORITY, MyDatabase.TWEETS_TABLE_NAME, TWEETS);
        sUriMatcher.addURI(AUTHORITY, MyDatabase.TWEETS_TABLE_NAME + "/#", TWEET_ID);
        sUriMatcher.addURI(AUTHORITY, MyDatabase.TWEETS_TABLE_NAME + "/search/*",
                TWEETS_SEARCH);
        sUriMatcher.addURI(AUTHORITY, MyDatabase.TWEETS_TABLE_NAME + "/count", TWEETS_COUNT);

        sUriMatcher.addURI(AUTHORITY, MyDatabase.USERS_TABLE_NAME, USERS);
        sUriMatcher.addURI(AUTHORITY, MyDatabase.USERS_TABLE_NAME + "/#", USER_ID);

        sUriMatcher.addURI(AUTHORITY, MyDatabase.DIRECTMESSAGES_TABLE_NAME, DIRECTMESSAGES);
        sUriMatcher.addURI(AUTHORITY, MyDatabase.DIRECTMESSAGES_TABLE_NAME + "/#",
                DIRECTMESSAGE_ID);
        sUriMatcher.addURI(AUTHORITY, MyDatabase.DIRECTMESSAGES_TABLE_NAME + "/count",
                DIRECTMESSAGES_COUNT);

        sTweetsProjectionMap = new HashMap<String, String>();
        sTweetsProjectionMap.put(Tweets._ID, Tweets._ID);
        sTweetsProjectionMap.put(Tweets.AUTHOR_ID, Tweets.AUTHOR_ID);
        sTweetsProjectionMap.put(Tweets.MESSAGE, Tweets.MESSAGE);
        sTweetsProjectionMap.put(Tweets.SOURCE, Tweets.SOURCE);
        sTweetsProjectionMap.put(Tweets.TWEET_TYPE, Tweets.TWEET_TYPE);
        sTweetsProjectionMap.put(Tweets.IN_REPLY_TO_STATUS_ID, Tweets.IN_REPLY_TO_STATUS_ID);
        sTweetsProjectionMap.put(Tweets.IN_REPLY_TO_AUTHOR_ID, Tweets.IN_REPLY_TO_AUTHOR_ID);
        sTweetsProjectionMap.put(Tweets.FAVORITED, Tweets.FAVORITED);
        sTweetsProjectionMap.put(Tweets.SENT_DATE, Tweets.SENT_DATE);
        sTweetsProjectionMap.put(Tweets.CREATED_DATE, Tweets.CREATED_DATE);

        sDirectMessagesProjectionMap = new HashMap<String, String>();
        sDirectMessagesProjectionMap.put(DirectMessages._ID, DirectMessages._ID);
        sDirectMessagesProjectionMap.put(DirectMessages.AUTHOR_ID, DirectMessages.AUTHOR_ID);
        sDirectMessagesProjectionMap.put(DirectMessages.MESSAGE, DirectMessages.MESSAGE);
        sDirectMessagesProjectionMap.put(DirectMessages.SENT_DATE, DirectMessages.SENT_DATE);
        sDirectMessagesProjectionMap.put(DirectMessages.CREATED_DATE, DirectMessages.CREATED_DATE);

        sUsersProjectionMap = new HashMap<String, String>();
        sUsersProjectionMap.put(Users._ID, Users._ID);
        sUsersProjectionMap.put(Users.AUTHOR_ID, Users.AUTHOR_ID);
        sUsersProjectionMap.put(Users.AVATAR_IMAGE, Users.AVATAR_IMAGE);
        sUsersProjectionMap.put(Users.CREATED_DATE, Users.CREATED_DATE);
        sUsersProjectionMap.put(Users.MODIFIED_DATE, Users.MODIFIED_DATE);
    }
}
