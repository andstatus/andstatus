/* 
 * Copyright (C) 2008 Torgny Bjers
 * Copyright (c) 2012 yvolk (Yuri Volkov), http://yurivolkov.com
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

import org.andstatus.app.TimelineActivity;
import org.andstatus.app.util.MyLog;

import android.content.Context;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.provider.BaseColumns;
import android.util.Log;


/**
 * Database definitions and helper class.
 * Used mainly by {@link MyProvider}
 */
public final class MyDatabase extends SQLiteOpenHelper  {

	public static final String TWITTER_DATE_FORMAT = "EEE MMM dd HH:mm:ss Z yyyy";
    static final String TWEETS_TABLE_NAME = "tweets";
    static final String USERS_TABLE_NAME = "users";
    static final String DIRECTMESSAGES_TABLE_NAME = "directmessages";
    public static final String DATABASE_NAME = "andstatus.sqlite";
    /**
     * Current database scheme version, defined by AndStatus developers.
     * This is used to check (and upgrade if necessary) 
     * existing database after application update.
     */
    static final int DATABASE_VERSION = 8;

	/**
	 * Tweets table
	 * 
	 */
	public static final class Tweets implements BaseColumns {
		public static final Uri CONTENT_URI = Uri.parse("content://" + MyProvider.AUTHORITY + "/tweets");
		public static final Uri SEARCH_URI = Uri.parse("content://" + MyProvider.AUTHORITY + "/tweets/search");
        public static final Uri CONTENT_COUNT_URI = Uri.parse("content://" + MyProvider.AUTHORITY + "/tweets/count");
		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/org.andstatus.provider.status";
		public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/org.andstatus.provider.status";
		public static final String DEFAULT_SORT_ORDER = "sent DESC";

		// Table columns
		public static final String AUTHOR_ID = "author_id";
		public static final String MESSAGE = "message";
		public static final String SOURCE = "source";
		public static final String TWEET_TYPE = "tweet_type";
		public static final String IN_REPLY_TO_STATUS_ID = "in_reply_to_status_id";
		public static final String IN_REPLY_TO_AUTHOR_ID = "in_reply_to_author_id";
		public static final String FAVORITED = "favorited";
		public static final String CREATED_DATE = "created";
		public static final String SENT_DATE = "sent";

	}

	/**
	 * Direct Messages table
	 */
	public static final class DirectMessages implements BaseColumns {
		public static final Uri CONTENT_URI = Uri.parse("content://" + MyProvider.AUTHORITY + "/directmessages");
		public static final Uri SEARCH_URI = Uri.parse("content://" + MyProvider.AUTHORITY + "/directmessages/search");
        public static final Uri CONTENT_COUNT_URI = Uri.parse("content://" + MyProvider.AUTHORITY + "/directmessages/count");
		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/org.andstatus.provider.directmessage";
		public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/org.andstatus.provider.directmessage";
		public static final String DEFAULT_SORT_ORDER = "sent DESC";

		// Table columns
		public static final String AUTHOR_ID = "author_id";
		public static final String MESSAGE = "message";
		public static final String CREATED_DATE = "created";
		public static final String SENT_DATE = "sent";
	}

	/**
	 * Users table (they are both senders AND recipients)
	 */
	public static final class Users implements BaseColumns {
		public static final Uri CONTENT_URI = Uri.parse("content://" + MyProvider.AUTHORITY + "/users");
		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/org.andstatus.provider.user";
		public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/org.andstatus.provider.user";
		public static final String DEFAULT_SORT_ORDER = "author_id ASC";

		// Table columns
		public static final String AUTHOR_ID = "author_id";
		public static final String FOLLOWING = "following";
		public static final String AVATAR_IMAGE = "avatar_blob";
		public static final String MODIFIED_DATE = "modified";
		public static final String CREATED_DATE = "created";
	}
	
    private static final String TAG = MyProvider.class.getSimpleName();

    MyDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        MyPreferences.initialize(context, this);
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
                        + ", " + TimelineActivity.TIMELINE_TYPE_HOME + ", "
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
