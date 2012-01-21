/* 
 * Copyright (C) 2008 Torgny Bjers
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

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDiskIOException;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

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
 * @author torgny.bjers
 */
public class MyProvider extends ContentProvider {

    private static final String TAG = MyProvider.class.getSimpleName();

    private static final String DATABASE_DIRECTORY = "andstatus";

    private static final String DATABASE_NAME = "andstatus.sqlite";

    /**
     * Current database scheme version, defined by AndStatus developers.
     * This is used to check (and upgrade if necessary) 
     * existing database after application update.
     */
    private static final int DATABASE_VERSION = 8;

    private static final String TWEETS_TABLE_NAME = "tweets";

    private static final String USERS_TABLE_NAME = "users";

    private static final String DIRECTMESSAGES_TABLE_NAME = "directmessages";

    private DatabaseHelper mOpenHelper;

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
     * Database helper for MyProvider.
     * 
     * @author torgny.bjers
     */
    private static class DatabaseHelper extends SQLiteOpenHelper {
        private static final String TAG = MyProvider.class.getSimpleName();

        private SQLiteDatabase mDatabase;

        private boolean mIsInitializing = false;

        private boolean mUseExternalStorage = false;

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
            MyPreferences.initialize(context, this);
            SharedPreferences sp = MyPreferences.getDefaultSharedPreferences();
            mUseExternalStorage = sp.getBoolean("storage_use_external", false);
        }

        @Override
        public synchronized SQLiteDatabase getWritableDatabase() {
            if (!mUseExternalStorage) {
                return super.getWritableDatabase();
            }

            if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                throw new SQLiteDiskIOException("Cannot access external storage: not mounted");
            }

            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED_READ_ONLY)) {
                throw new SQLiteDiskIOException("Cannot access external storage: mounted read only");
            }

            if (mDatabase != null && mDatabase.isOpen() && !mDatabase.isReadOnly()) {
                return mDatabase;
            }

            if (mIsInitializing) {
                throw new IllegalStateException("getWritableDatabase called recursively");
            }

            boolean success = false;
            SQLiteDatabase db = null;
            try {
                mIsInitializing = true;
                File storage = Environment.getExternalStorageDirectory();
                String path = storage.getAbsolutePath();
                File dir = new File(path, DATABASE_DIRECTORY);
                dir.mkdir();
                File file = new File(dir.getAbsolutePath(), DATABASE_NAME);
                db = SQLiteDatabase.openOrCreateDatabase(file, null);
                int version = db.getVersion();
                if (version != DATABASE_VERSION) {
                    db.beginTransaction();
                    try {
                        if (version == 0) {
                            onCreate(db);
                        } else {
                            onUpgrade(db, version, DATABASE_VERSION);
                        }
                        db.setVersion(DATABASE_VERSION);
                        db.setTransactionSuccessful();
                    } finally {
                        db.endTransaction();
                    }
                }
                onOpen(db);
                success = true;
                return db;
            } finally {
                mIsInitializing = false;
                if (success) {
                    if (mDatabase != null) {
                        try {
                            mDatabase.close();
                        } catch (Exception e) {
                        }
                    }
                    mDatabase = db;
                } else {
                    if (db != null)
                        db.close();
                }
            }
        }

        @Override
        public synchronized SQLiteDatabase getReadableDatabase() {
            if (!mUseExternalStorage) {
                return super.getReadableDatabase();
            }

            if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                throw new SQLiteDiskIOException("Cannot access external storage: not mounted");
            }

            if (mDatabase != null && mDatabase.isOpen()) {
                return mDatabase;
            }

            if (mIsInitializing) {
                throw new IllegalStateException("getReadableDatabase called recursively");
            }

            try {
                return getWritableDatabase();
            } catch (SQLiteException e) {
                Log.e(TAG, "Couldn't open " + DATABASE_NAME + " for writing (will try read-only):",
                        e);
            }

            SQLiteDatabase db = null;
            try {
                mIsInitializing = true;
                File storage = Environment.getExternalStorageDirectory();
                String path = storage.getAbsolutePath();
                File dir = new File(path, DATABASE_DIRECTORY);
                dir.mkdir();
                File file = new File(dir.getAbsolutePath(), DATABASE_NAME);
                db = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null,
                        SQLiteDatabase.OPEN_READONLY);
                if (db.getVersion() != DATABASE_VERSION) {
                    throw new SQLiteException("Can't upgrade read-only database from version "
                            + db.getVersion() + " to " + DATABASE_VERSION + ": "
                            + file.getAbsolutePath());
                }
                onOpen(db);
                Log.w(TAG, "Opened " + DATABASE_NAME + " in read-only mode");
                mDatabase = db;
                return mDatabase;
            } finally {
                mIsInitializing = false;
                if (db != null && db != mDatabase)
                    db.close();
            }
        }

        @Override
        public synchronized void close() {
            super.close();
            if (mUseExternalStorage && mDatabase != null && mDatabase.isOpen()) {
                mDatabase.close();
                mDatabase = null;
            }
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            MyLog.d(TAG, "Creating tables");
            db.execSQL("CREATE TABLE " + TWEETS_TABLE_NAME + " (" + Tweets._ID
                    + " INTEGER PRIMARY KEY," + Tweets.AUTHOR_ID + " TEXT," + Tweets.MESSAGE
                    + " TEXT," + Tweets.SOURCE + " TEXT," + Tweets.TWEET_TYPE + " INTEGER,"
                    + Tweets.IN_REPLY_TO_STATUS_ID + " INTEGER," + Tweets.IN_REPLY_TO_AUTHOR_ID
                    + " TEXT," + Tweets.FAVORITED + " INTEGER," + Tweets.SENT_DATE + " INTEGER,"
                    + Tweets.CREATED_DATE + " INTEGER" + ");");

            db.execSQL("CREATE TABLE " + DIRECTMESSAGES_TABLE_NAME + " (" + DirectMessages._ID
                    + " INTEGER PRIMARY KEY," + DirectMessages.AUTHOR_ID + " TEXT,"
                    + DirectMessages.MESSAGE + " TEXT," + DirectMessages.SENT_DATE + " INTEGER,"
                    + DirectMessages.CREATED_DATE + " INTEGER" + ");");

            db.execSQL("CREATE TABLE " + USERS_TABLE_NAME + " (" + Users._ID
                    + " INTEGER PRIMARY KEY," + Users.AUTHOR_ID + " TEXT," + Users.FOLLOWING
                    + " INTEGER," + Users.AVATAR_IMAGE + " BLOB," + Users.CREATED_DATE
                    + " INTEGER," + Users.MODIFIED_DATE + " INTEGER" + ");");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            MyLog.d(TAG, "Upgrading database from version " + oldVersion + " to version "
                    + newVersion);
            if (oldVersion < 7) {
                db.beginTransaction();
                try {
                    /*
                     * Upgrading tweets table: - Add column TWEET_TYPE
                     */
                    db.execSQL("CREATE TEMPORARY TABLE " + TWEETS_TABLE_NAME + "_backup ("
                            + Tweets._ID + " INTEGER PRIMARY KEY," + Tweets.AUTHOR_ID + " TEXT,"
                            + Tweets.MESSAGE + " TEXT," + Tweets.SOURCE + " TEXT,"
                            + Tweets.IN_REPLY_TO_STATUS_ID + " INTEGER,"
                            + Tweets.IN_REPLY_TO_AUTHOR_ID + " TEXT," + Tweets.SENT_DATE
                            + " INTEGER," + Tweets.CREATED_DATE + " INTEGER" + ");");
                    db.execSQL("INSERT INTO " + TWEETS_TABLE_NAME + "_backup SELECT " + Tweets._ID
                            + ", " + Tweets.AUTHOR_ID + ", " + Tweets.MESSAGE + ", "
                            + Tweets.SOURCE + ", " + Tweets.IN_REPLY_TO_AUTHOR_ID + ", "
                            + Tweets.IN_REPLY_TO_STATUS_ID + ", " + Tweets.SENT_DATE + ", "
                            + Tweets.CREATED_DATE + " FROM " + TWEETS_TABLE_NAME + ";");
                    db.execSQL("DROP TABLE " + TWEETS_TABLE_NAME + ";");
                    db.execSQL("CREATE TABLE " + TWEETS_TABLE_NAME + " (" + Tweets._ID
                            + " INTEGER PRIMARY KEY," + Tweets.AUTHOR_ID + " TEXT,"
                            + Tweets.MESSAGE + " TEXT," + Tweets.SOURCE + " TEXT,"
                            + Tweets.TWEET_TYPE + " INTEGER," + Tweets.IN_REPLY_TO_STATUS_ID
                            + " INTEGER," + Tweets.IN_REPLY_TO_AUTHOR_ID + " TEXT,"
                            + Tweets.FAVORITED + " INTEGER," + Tweets.SENT_DATE + " INTEGER,"
                            + Tweets.CREATED_DATE + " INTEGER" + ");");
                    db.execSQL("INSERT INTO " + TWEETS_TABLE_NAME + " SELECT " + Tweets._ID + ", "
                            + Tweets.AUTHOR_ID + ", " + Tweets.MESSAGE + ", " + Tweets.SOURCE
                            + ", " + Tweets.TIMELINE_TYPE_HOME + ", "
                            + Tweets.IN_REPLY_TO_AUTHOR_ID + ", " + Tweets.IN_REPLY_TO_STATUS_ID
                            + ", null, " + Tweets.SENT_DATE + ", " + Tweets.CREATED_DATE + " FROM "
                            + TWEETS_TABLE_NAME + "_backup;");
                    db.execSQL("DROP TABLE " + TWEETS_TABLE_NAME + "_backup;");

                    /*
                     * Upgrading users table: - Add column AVATAR_IMAGE
                     */
                    db.execSQL("CREATE TEMPORARY TABLE " + USERS_TABLE_NAME + "_backup ("
                            + Users._ID + " INTEGER PRIMARY KEY," + Users.AUTHOR_ID + " TEXT,"
                            + Users.CREATED_DATE + " INTEGER," + Users.MODIFIED_DATE + " INTEGER"
                            + ");");
                    db.execSQL("INSERT INTO " + USERS_TABLE_NAME + "_backup SELECT " + Users._ID
                            + ", " + Users.AUTHOR_ID + ", " + Users.CREATED_DATE + ", "
                            + Users.MODIFIED_DATE + " FROM " + USERS_TABLE_NAME + ";");
                    db.execSQL("DROP TABLE " + USERS_TABLE_NAME + ";");
                    db.execSQL("CREATE TABLE " + USERS_TABLE_NAME + " (" + Users._ID
                            + " INTEGER PRIMARY KEY," + Users.AUTHOR_ID + " TEXT,"
                            + Users.AVATAR_IMAGE + " BLOB," + Users.FOLLOWING + " INTEGER,"
                            + Users.CREATED_DATE + " INTEGER," + Users.MODIFIED_DATE + " INTEGER"
                            + ");");
                    db.execSQL("INSERT INTO " + USERS_TABLE_NAME + " SELECT " + Users._ID + ", "
                            + Users.AUTHOR_ID + ", null, null, " + Users.CREATED_DATE + ", "
                            + Users.MODIFIED_DATE + " FROM " + USERS_TABLE_NAME + "_backup;");
                    db.execSQL("DROP TABLE " + USERS_TABLE_NAME + "_backup;");
                    db.setTransactionSuccessful();
                    Log.d(TAG, "Successfully upgraded database from version " + oldVersion
                            + " to version " + newVersion + ".");
                } catch (SQLException e) {
                    Log.e(TAG, "Could not upgrade database from version " + oldVersion
                            + " to version " + newVersion, e);
                } finally {
                    db.endTransaction();
                }
            } else if (oldVersion < 8) {
                try {
                    /*
                     * Upgrading users table: - Add column FOLLOWING
                     */
                    db.execSQL("CREATE TEMPORARY TABLE " + USERS_TABLE_NAME + "_backup ("
                            + Users._ID + " INTEGER PRIMARY KEY," + Users.AUTHOR_ID + " TEXT,"
                            + Users.AVATAR_IMAGE + " BLOB," + Users.CREATED_DATE + " INTEGER,"
                            + Users.MODIFIED_DATE + " INTEGER" + ");");
                    db.execSQL("INSERT INTO " + USERS_TABLE_NAME + "_backup SELECT " + Users._ID
                            + ", " + Users.AUTHOR_ID + ", " + Users.AVATAR_IMAGE + ", "
                            + Users.CREATED_DATE + ", " + Users.MODIFIED_DATE + " FROM "
                            + USERS_TABLE_NAME + ";");
                    db.execSQL("DROP TABLE " + USERS_TABLE_NAME + ";");
                    db.execSQL("CREATE TABLE " + USERS_TABLE_NAME + " (" + Users._ID
                            + " INTEGER PRIMARY KEY," + Users.AUTHOR_ID + " TEXT,"
                            + Users.FOLLOWING + " INTEGER," + Users.AVATAR_IMAGE + " BLOB,"
                            + Users.CREATED_DATE + " INTEGER," + Users.MODIFIED_DATE + " INTEGER"
                            + ");");
                    db.execSQL("INSERT INTO " + USERS_TABLE_NAME + " SELECT " + Users._ID + ", "
                            + Users.AUTHOR_ID + ", null, " + Users.AVATAR_IMAGE + ", "
                            + Users.CREATED_DATE + ", " + Users.MODIFIED_DATE + " FROM "
                            + USERS_TABLE_NAME + "_backup;");
                    db.execSQL("DROP TABLE " + USERS_TABLE_NAME + "_backup;");

                    /*
                     * Upgrading tweets table: - Add column FAVORITED
                     */
                    db.execSQL("CREATE TEMPORARY TABLE " + TWEETS_TABLE_NAME + "_backup ("
                            + Tweets._ID + " INTEGER PRIMARY KEY," + Tweets.AUTHOR_ID + " TEXT,"
                            + Tweets.MESSAGE + " TEXT," + Tweets.SOURCE + " TEXT,"
                            + Tweets.TWEET_TYPE + " INTEGER," + Tweets.IN_REPLY_TO_STATUS_ID
                            + " INTEGER," + Tweets.IN_REPLY_TO_AUTHOR_ID + " TEXT,"
                            + Tweets.SENT_DATE + " INTEGER," + Tweets.CREATED_DATE + " INTEGER"
                            + ");");
                    db.execSQL("INSERT INTO " + TWEETS_TABLE_NAME + "_backup SELECT " + Tweets._ID
                            + ", " + Tweets.AUTHOR_ID + ", " + Tweets.MESSAGE + ", "
                            + Tweets.SOURCE + ", " + Tweets.TWEET_TYPE + ", "
                            + Tweets.IN_REPLY_TO_AUTHOR_ID + ", " + Tweets.IN_REPLY_TO_STATUS_ID
                            + ", " + Tweets.SENT_DATE + ", " + Tweets.CREATED_DATE + " FROM "
                            + TWEETS_TABLE_NAME + ";");
                    db.execSQL("DROP TABLE " + TWEETS_TABLE_NAME + ";");
                    db.execSQL("CREATE TABLE " + TWEETS_TABLE_NAME + " (" + Tweets._ID
                            + " INTEGER PRIMARY KEY," + Tweets.AUTHOR_ID + " TEXT,"
                            + Tweets.MESSAGE + " TEXT," + Tweets.SOURCE + " TEXT,"
                            + Tweets.TWEET_TYPE + " INTEGER," + Tweets.IN_REPLY_TO_STATUS_ID
                            + " INTEGER," + Tweets.IN_REPLY_TO_AUTHOR_ID + " TEXT,"
                            + Tweets.FAVORITED + " INTEGER," + Tweets.SENT_DATE + " INTEGER,"
                            + Tweets.CREATED_DATE + " INTEGER" + ");");
                    db.execSQL("INSERT INTO " + TWEETS_TABLE_NAME + " SELECT " + Tweets._ID + ", "
                            + Tweets.AUTHOR_ID + ", " + Tweets.MESSAGE + ", " + Tweets.SOURCE
                            + ", " + Tweets.TWEET_TYPE + ", " + Tweets.IN_REPLY_TO_AUTHOR_ID + ", "
                            + Tweets.IN_REPLY_TO_STATUS_ID + ", null, " + Tweets.SENT_DATE + ", "
                            + Tweets.CREATED_DATE + " FROM " + TWEETS_TABLE_NAME + "_backup;");
                    db.execSQL("DROP TABLE " + TWEETS_TABLE_NAME + "_backup;");

                    db.setTransactionSuccessful();
                    Log.d(TAG, "Successfully upgraded database from version " + oldVersion
                            + " to version " + newVersion + ".");
                } catch (SQLException e) {
                    Log.e(TAG, "Could not upgrade database from version " + oldVersion
                            + " to version " + newVersion, e);
                } finally {
                    db.endTransaction();
                }
            }
        }
    }

    /**
     * @see android.content.ContentProvider#onCreate()
     */
    @Override
    public boolean onCreate() {
        mOpenHelper = new DatabaseHelper(getContext());
        return (mOpenHelper == null) ? false : true;
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
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count;
        switch (sUriMatcher.match(uri)) {
            case TWEETS:
                count = db.delete(TWEETS_TABLE_NAME, selection, selectionArgs);
                break;

            case TWEET_ID:
                String tweetId = uri.getPathSegments().get(1);
                count = db.delete(TWEETS_TABLE_NAME, Tweets._ID + "=" + tweetId
                        + (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : ""),
                        selectionArgs);
                break;

            case DIRECTMESSAGES:
                count = db.delete(DIRECTMESSAGES_TABLE_NAME, selection, selectionArgs);
                break;

            case DIRECTMESSAGE_ID:
                String messageId = uri.getPathSegments().get(1);
                count = db.delete(DIRECTMESSAGES_TABLE_NAME, DirectMessages._ID + "=" + messageId
                        + (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : ""),
                        selectionArgs);
                break;

            case USERS:
                count = db.delete(USERS_TABLE_NAME, selection, selectionArgs);
                break;

            case USER_ID:
                String userId = uri.getPathSegments().get(1);
                count = db.delete(USERS_TABLE_NAME, Users._ID + "=" + userId
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
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();

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
                table = TWEETS_TABLE_NAME;
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
                    values.put(Tweets.TWEET_TYPE, Tweets.TIMELINE_TYPE_HOME);
                if (values.containsKey(Tweets.IN_REPLY_TO_AUTHOR_ID) == false)
                    values.put(Tweets.IN_REPLY_TO_AUTHOR_ID, "");
                if (values.containsKey(Tweets.FAVORITED) == false)
                    values.put(Tweets.FAVORITED, 0);
                break;

            case DIRECTMESSAGES:
                table = DIRECTMESSAGES_TABLE_NAME;
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
                table = USERS_TABLE_NAME;
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
                qb.setTables(TWEETS_TABLE_NAME);
                qb.setProjectionMap(sTweetsProjectionMap);
                break;

            case TWEETS_COUNT:
                sql = "SELECT count(*) FROM " + TWEETS_TABLE_NAME;
                if (selection != null && selection.length() > 0) {
                    sql += " WHERE " + selection;
                }
                break;

            case TWEET_ID:
                qb.setTables(TWEETS_TABLE_NAME);
                qb.setProjectionMap(sTweetsProjectionMap);
                qb.appendWhere(Tweets._ID + "=" + uri.getPathSegments().get(1));
                break;

            case TWEETS_SEARCH:
                qb.setTables(TWEETS_TABLE_NAME);
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
                qb.setTables(DIRECTMESSAGES_TABLE_NAME);
                qb.setProjectionMap(sDirectMessagesProjectionMap);
                break;

            case DIRECTMESSAGE_ID:
                qb.setTables(DIRECTMESSAGES_TABLE_NAME);
                qb.setProjectionMap(sDirectMessagesProjectionMap);
                qb.appendWhere(DirectMessages._ID + "=" + uri.getPathSegments().get(1));
                break;

            case DIRECTMESSAGES_COUNT:
                sql = "SELECT count(*) FROM " + DIRECTMESSAGES_TABLE_NAME;
                if (selection != null && selection.length() > 0) {
                    sql += " WHERE " + selection;
                }
                break;

            case USERS:
                qb.setTables(USERS_TABLE_NAME);
                qb.setProjectionMap(sUsersProjectionMap);
                break;

            case USER_ID:
                qb.setTables(USERS_TABLE_NAME);
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
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
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
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count;
        switch (sUriMatcher.match(uri)) {
            case TWEETS:
                count = db.update(TWEETS_TABLE_NAME, values, selection, selectionArgs);
                break;

            case TWEET_ID:
                String noteId = uri.getPathSegments().get(1);
                count = db.update(TWEETS_TABLE_NAME, values, Tweets._ID + "=" + noteId
                        + (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : ""),
                        selectionArgs);
                break;

            case DIRECTMESSAGES:
                count = db.update(DIRECTMESSAGES_TABLE_NAME, values, selection, selectionArgs);
                break;

            case DIRECTMESSAGE_ID:
                String messageId = uri.getPathSegments().get(1);
                count = db.update(DIRECTMESSAGES_TABLE_NAME, values, DirectMessages._ID + "="
                        + messageId
                        + (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : ""),
                        selectionArgs);
                break;

            case USERS:
                count = db.update(USERS_TABLE_NAME, values, selection, selectionArgs);
                break;

            case USER_ID:
                String userId = uri.getPathSegments().get(1);
                count = db.update(USERS_TABLE_NAME, values, Users._ID + "=" + userId
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

        sUriMatcher.addURI(AUTHORITY, TWEETS_TABLE_NAME, TWEETS);
        sUriMatcher.addURI(AUTHORITY, TWEETS_TABLE_NAME + "/#", TWEET_ID);
        sUriMatcher.addURI(AUTHORITY, TWEETS_TABLE_NAME + "/search/*",
                TWEETS_SEARCH);
        sUriMatcher.addURI(AUTHORITY, TWEETS_TABLE_NAME + "/count", TWEETS_COUNT);

        sUriMatcher.addURI(AUTHORITY, USERS_TABLE_NAME, USERS);
        sUriMatcher.addURI(AUTHORITY, USERS_TABLE_NAME + "/#", USER_ID);

        sUriMatcher.addURI(AUTHORITY, DIRECTMESSAGES_TABLE_NAME, DIRECTMESSAGES);
        sUriMatcher.addURI(AUTHORITY, DIRECTMESSAGES_TABLE_NAME + "/#",
                DIRECTMESSAGE_ID);
        sUriMatcher.addURI(AUTHORITY, DIRECTMESSAGES_TABLE_NAME + "/count",
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
