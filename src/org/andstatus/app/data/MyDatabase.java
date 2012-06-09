/* 
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

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.account.Origin;
import org.andstatus.app.appwidget.MyAppWidgetConfigure;
import org.andstatus.app.util.MyLog;

import android.content.Context;
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
    /**
     * Current database scheme version, defined by AndStatus developers.
     * This is used to check (and upgrade if necessary) 
     * existing database after application update.
     * 
     * v.9 2012-02-26 yvolk. Totally new database design using table joins.
     *      All messages are in the same table. 
     *      Allows to have multiple User Accounts in different Originating systems (twitter.com etc. ) 
     */
    static final int DATABASE_VERSION = 9;
    public static final String DATABASE_NAME = "andstatus.sqlite";

    /** TODO: Do we really need this? */
	public static final String TWITTER_DATE_FORMAT = "EEE MMM dd HH:mm:ss Z yyyy";
	
	public static final String MSG_TABLE_NAME = Msg.class.getSimpleName().toLowerCase();
	public static final String MSGOFUSER_TABLE_NAME = MsgOfUser.class.getSimpleName().toLowerCase();
	public static final String USER_TABLE_NAME = User.class.getSimpleName().toLowerCase();
	
	/**
	 * Table for both public and direct messages 
	 * i.e. for tweets, dents, notices 
	 * and also for "direct messages", "direct dents" etc.
	 */
	public static final class Msg implements BaseColumns {
	    /**
	     * These are in fact definitions for Timelines based on the table, 
	     * not for the Msg table itself.
	     * Because we always filter the table by current MyAccount (USER_ID joined through {@link MsgOfUser} ) etc.
	     */
		public static final Uri CONTENT_URI = Uri.parse("content://" + MyProvider.AUTHORITY + "/" + MSG_TABLE_NAME);
        public static final Uri CONTENT_COUNT_URI = Uri.parse("content://" + MyProvider.AUTHORITY + "/" + MSG_TABLE_NAME + "/count");
        /* like in AndroidManifest.xml */
		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/org.andstatus.provider." + MSG_TABLE_NAME;
		public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/org.andstatus.provider." + MSG_TABLE_NAME;
		
		// Table columns are below:
        /*
         * {@link BaseColumns#_ID} is primary key in this database
         * No, we can not rename the {@link BaseColumns#_ID}, it should always be "_id". 
         * See <a href="http://stackoverflow.com/questions/3359414/android-column-id-does-not-exist">Android column '_id' does not exist?</a>
         */

        /**
         * ID of the originating (source) system (twitter.com, identi.ca, ... ) where the row was created
         * 2012-02-26 Currently defaults to the "1" since we have only one system (twitter.com) yet
         */
        public static final String ORIGIN_ID =  "origin_id";
	    /**
	     * ID in the originating system
	     * The id is not unique for this table, because we have IDs from different systems in one column
	     * and IDs from different systems may overlap.
	     */
        public static final String MSG_OID = "msg_oid";
        /**
         * Author of the message = User._ID
         * If message was "Retweeted" ("Redented", ...) this is Original author (whose message was retweeted)
         */
        public static final String AUTHOR_ID = "author_id";
		/**
		 * Sender of the message = User._ID
		 */
		public static final String SENDER_ID = "sender_id";
        /**
         * Recipient of the message = User._ID
         * null for public messages
         * not null for direct messages
         */
        public static final String RECIPIENT_ID = "recipient_id";
		/**
		 * Text of the message ("TEXT" may be reserved word so it was renamed here)
		 */
		public static final String BODY = "body";
		/**
		 * String generally describing Client's software used to post this message
		 * It's like "User Agent" string in the browsers?!: "via ..."
		 * (This is "source" field in tweets)
		 */
		public static final String VIA = "via";
        /**
         * If not null: Link to the Msg._ID in this table
         */
        public static final String IN_REPLY_TO_MSG_ID = "in_reply_to_msg_id";
        /**
         * If not null: to which Sender this message is a reply = User._ID
         * This field is not necessary but speeds up IN_REPLY_TO_NAME calculation
         */
        public static final String IN_REPLY_TO_USER_ID = "in_reply_to_user_id";
        /**
         * Date and time when the row was created in the originating system.
         * We store it as long returned by {@link java.util.Date#parse(String) }. 
         * NULL means the row was not retrieved from the Internet yet
         */
		public static final String CREATED_DATE = "msg_created_date";
        /**
         * Date and time when the message was sent,
         * it's not equal to {@link MyDatabase.Msg#CREATED_DATE} for retweeted messages
         */
        public static final String SENT_DATE = "msg_sent_date";
		/**
		 * Date and time the row was inserted into this database
		 */
        public static final String INS_DATE = "msg_ins_date";

        /*
         * Derived columns (they are not stored in this table but are result of joins and aliasing)
         */
        /**
         * Alias for the primary key
         */
        public static final String MSG_ID =  "msg_id";
        
        public static final String DEFAULT_SORT_ORDER = SENT_DATE + " DESC";
	}
	
    /**
     * Link tables: Msg to a User.
     * This User may be not even mentioned in this message 
     *   (e.g. SUBSCRIBED by the User who is not sender not recipient...).
     * This table is used to filter User's timelines (based on flags: SUBSCRIBED etc.) 
     */
    public static final class MsgOfUser implements BaseColumns {
        // Table columns
        
        /**
         * Fields for joining tables
         */
        public static final String MSG_ID =  Msg.MSG_ID;
        public static final String USER_ID = User.USER_ID;
        
        /**
         * The message is in the User's Home timeline
         * i.e. it corresponds to the TIMELINE_TYPE = TIMELINE_TYPE_HOME
         */
        public static final String SUBSCRIBED = "subscribed";
        /**
         * The Msg is favorite for this User
         */
        public static final String FAVORITED = "favorited";
        /**
         * The Msg is retweeted by this User
         * In some sense RETWEETED is like FAVORITED. 
         * Main difference: visibility. RETWEETED are shown for all followers in their Home timelines. 
         */
        public static final String RETWEETED = "retweeted";
        /**
         * User is mentioned in this message
         */
        public static final String MENTIONED = "mentioned";
        /**
         * This User is not only (optionally) mentioned in this message (explicitly using "@username" form)
         * but the message has explicit reference to the User's message for which this message is a reply. 
         */
        public static final String REPLIED = "replied";
        /**
         * This is Direct message which was sent by (Sender) or to (Recipient) this User
         */
        public static final String DIRECTED = "directed";

        /*
         * Derived columns (they are not stored in this table but are result of joins and aliasing)
         */
        /** Inclusion of this column means that we should join MsgOfUser for current MyAccount (USER_ID)
         * and analyze flags in this row  
         * @see MyDatabase#TIMELINE_TYPE_NONE  */
        public static final String TIMELINE_TYPE = "timeline_type";
    }

	/**
	 * Users table (they are both senders AND recipients in the Msg table)
	 * Some of these Users are Accounts (connected to accounts in AndStatus), see {@link MyAccount#getUserId()}
	 */
	public static final class User implements BaseColumns {
		public static final Uri CONTENT_URI = Uri.parse("content://" + MyProvider.AUTHORITY + "/" + USER_TABLE_NAME);
		/* like in AndroidManifest.xml */
		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/org.andstatus.provider." + USER_TABLE_NAME;
		public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/org.andstatus.provider." + USER_TABLE_NAME;

        // Table columns
        /* {@link BaseColumns#_ID} is primary key in this database  */

		/**
         * ID of the originating (source) system (twitter.com, identi.ca, ... ) where the row was created
         * 2012-02-26 Currently defaults to the "1" since we have only one system (twitter.com) yet
         */
        public static final String ORIGIN_ID =  Msg.ORIGIN_ID;
        /**
         * ID in the originating system
         * The id is not unique for this table, because we have IDs from different systems in one column.
         */
        public static final String USER_OID = "user_oid";
        /**
         * This is called "screen_name" in Twitter API
         */
		public static final String USERNAME = "username";
        /**
         * This is called "name" in Twitter API
         */
        public static final String REAL_NAME = "real_name";
		/**
		 * To track changes of the image and update cached image 
		 */
        public static final String AVATAR_URL = "avatar_url";
        /**
         * Cached avatar image
         */
        public static final String AVATAR_BLOB = "avatar_blob";
        /**
         * User's description
         */
        public static final String DESCRIPTION = "user_description";
        /**
         * User's web home page
         */
        public static final String HOMEPAGE = "homepage";
        /**
         * Date and time when the row was created in the originating system.
         * We store it as long returned by {@link java.util.Date#parse(String) }. 
         * NULL means the row was not retrieved from the Internet yet
         * (And maybe there is no such user in the originating system...)
         */
		public static final String CREATED_DATE = "user_created_date";
        /**
         * Date and time the row was inserted into this database
         */
        public static final String INS_DATE = "user_ins_date";
		
		/*
         * Derived columns (they are not stored in this table but are result of joins)
         */
        /**
         * Alias for the primary key
         */
        public static final String USER_ID = "user_id";
        /**
         * Alias for the {@link User}'s primary key used to refer to MyAccount 
         * (e.g. in a case we need to query a Timeline for particular MyAccount (e.g. for current MyAccount) 
         */
        public static final String ACCOUNT_ID =  "account_id";
		/**
		 * Derived from {@link Msg#SENDER_ID}
		 * TODO: Whether this (and other similar...) is {@link #USERNAME} or {@link #REAL_NAME}, depends on settings 
		 */
        public static final String SENDER_NAME = "sender_name";
        /** Derived from {@link Msg#AUTHOR_ID} */
        public static final String AUTHOR_NAME = "author_name";
        /** Derived from {@link Msg#IN_REPLY_TO_USER_ID} */
        public static final String IN_REPLY_TO_NAME = "in_reply_to_name";

        public static final String DEFAULT_SORT_ORDER = USERNAME + " ASC";
	}
	
    private static final String TAG = MyProvider.class.getSimpleName();

    /**
     * These values help set timeline filters closer to the database (in ContentProvider...)
     */
    public enum TimelineTypeEnum {
        /**
         * The enum is unknown
         */
        UNKNOWN("unknown"),
        HOME("home"),
        MENTIONS("mentions"),
        /**
         * Direct messages (direct dents...)
         */
        DIRECT("direct"),
        FAVORITES("favorites"),
        /**
         * All timelines (e.g. for download...)
         */
        ALL("all");
        
        /**
         * code of the enum that is used in messages
         */
        private String code;

        private TimelineTypeEnum(String codeIn) {
            code = codeIn;
        }

        /**
         * String code for the Command to be used in messages
         */
        public String save() {
            return code;
        }
        /**
         * Returns the enum for a String action code or UNKNOWN
         */
        public static TimelineTypeEnum load(String strCode) {
            for (TimelineTypeEnum tt : TimelineTypeEnum.values()) {
                if (tt.code.equals(strCode)) {
                    return tt;
                }
            }
            return UNKNOWN;
        }
    }
    
//    public static final int TIMELINE_TYPE_NONE = 0;
//    public static final int TIMELINE_TYPE_HOME = 1;
//    public static final int TIMELINE_TYPE_MENTIONS = 2;
//    public static final int TIMELINE_TYPE_DIRECT = 3;
//    public static final int TIMELINE_TYPE_FAVORITES = 4;

    MyDatabase(Context context) {
        // We use TAG instead of 'this' which cannot be used in this context
        super(MyPreferences.initialize(context, TAG), DATABASE_NAME, null, DATABASE_VERSION);
    }

    /**
     * On datatypes in SQLite see <a href="http://www.sqlite.org/datatype3.html">Datatypes In SQLite Version 3</a>.
     * See also <a href="http://sqlite.org/autoinc.html">SQLite Autoincrement</a>.
     */
    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.i(TAG, "Creating tables");
        db.execSQL("CREATE TABLE " + MSG_TABLE_NAME + " (" 
                + Msg._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," 
                + Msg.ORIGIN_ID + " INTEGER DEFAULT " + Origin.ORIGIN_ID_DEFAULT + " NOT NULL," 
                + Msg.MSG_OID + " STRING," 
                + Msg.AUTHOR_ID + " INTEGER," 
                + Msg.SENDER_ID + " INTEGER," 
                + Msg.RECIPIENT_ID + " INTEGER," 
                + Msg.BODY + " TEXT," 
                + Msg.VIA + " TEXT," 
                + Msg.IN_REPLY_TO_MSG_ID + " INTEGER," 
                + Msg.IN_REPLY_TO_USER_ID + " INTEGER," 
                + Msg.CREATED_DATE + " INTEGER,"
                + Msg.SENT_DATE + " INTEGER,"
                + Msg.INS_DATE + " INTEGER NOT NULL"
                + ");");

        db.execSQL("CREATE UNIQUE INDEX idx_msg_origin ON " + MSG_TABLE_NAME + " (" 
                + Msg.ORIGIN_ID + ", "
                + Msg.MSG_OID
                + ");");
        
        db.execSQL("CREATE TABLE " + MSGOFUSER_TABLE_NAME + " (" 
                + MsgOfUser.USER_ID + " INTEGER NOT NULL," 
                + MsgOfUser.MSG_ID + " INTEGER NOT NULL," 
                + MsgOfUser.SUBSCRIBED + " BOOLEAN DEFAULT 0 NOT NULL," 
                + MsgOfUser.FAVORITED + " BOOLEAN DEFAULT 0 NOT NULL," 
                + MsgOfUser.RETWEETED + " BOOLEAN DEFAULT 0 NOT NULL," 
                + MsgOfUser.MENTIONED + " BOOLEAN DEFAULT 0 NOT NULL," 
                + MsgOfUser.REPLIED + " BOOLEAN DEFAULT 0 NOT NULL," 
                + MsgOfUser.DIRECTED + " BOOLEAN DEFAULT 0 NOT NULL," 
                + " CONSTRAINT pk_msgofuser PRIMARY KEY (" + MsgOfUser.USER_ID + " ASC, " + MsgOfUser.MSG_ID + " ASC)"
                + ");");
        
        db.execSQL("CREATE TABLE " + USER_TABLE_NAME + " (" 
                + User._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," 
                + User.ORIGIN_ID + " INTEGER DEFAULT " + Origin.ORIGIN_ID_DEFAULT + " NOT NULL," 
                + User.USER_OID + " STRING," 
                + User.USERNAME + " TEXT NOT NULL," 
                + User.REAL_NAME + " TEXT," 
                + User.AVATAR_URL + " TEXT," 
                + User.AVATAR_BLOB + " BLOB," 
                + User.DESCRIPTION + " TEXT," 
                + User.HOMEPAGE + " TEXT," 
                + User.CREATED_DATE + " INTEGER,"
                + User.INS_DATE + " INTEGER NOT NULL"
                + ");");

        db.execSQL("CREATE UNIQUE INDEX idx_username ON " + USER_TABLE_NAME + " (" 
                + User.ORIGIN_ID + ", "
                + User.USERNAME  
                + ");");
        
    }

    /**
     * We don't need here neither try-catch nor transactions because they are
     * being used in calling method
     * 
     * @see android.database.sqlite.SQLiteOpenHelper#getWritableDatabase
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.d(TAG, "Upgrading database from version " + oldVersion + " to version " + newVersion);
        if (oldVersion < 8) {
            db.execSQL("DROP TABLE " + "tweets" + ";");
            db.execSQL("DROP TABLE " + "directmessages" + ";");
            db.execSQL("DROP TABLE " + "users" + ";");
            Log.d(TAG, "Dropped old tables from version " + oldVersion);
            onCreate(db);
        } else {
            if (oldVersion == 8) {
                try {
                    MyLog.d(TAG, "Upgrading database from version 8 to version 9");
                    db.execSQL("DROP TABLE " + "users" + ";");
                    onCreate(db);

                    // Associate all currently loaded messages with one current
                    // User MyAccount (to be created...)
                    String username = MyPreferences.getDefaultSharedPreferences().getString(
                            MyAccount.KEY_USERNAME, "");
                    if (username.length() > 0) {
                        db.execSQL("INSERT INTO user(_id, origin_id, username, user_ins_date) VALUES(1, 1, '"
                                + username + "'," + System.currentTimeMillis() + ")");
                        db.execSQL("INSERT INTO msg (msg_oid, origin_id, body, via, msg_created_date, msg_sent_date, msg_ins_date)"
                                + " SELECT tweets._id, 1, tweets.message, tweets.source, tweets.sent, tweets.sent,"
                                + System.currentTimeMillis() + " FROM tweets");
                        db.execSQL("INSERT INTO user (username, origin_id, user_ins_date) SELECT DISTINCT author_id, 1, "
                                + System.currentTimeMillis()
                                + " FROM tweets"
                                + " WHERE (tweets.author_id <> 'null')"
                                + " AND NOT EXISTS (SELECT * FROM user WHERE username = tweets.author_id)");
                        db.execSQL("INSERT INTO msgofuser (msg_id, user_id, favorited, subscribed, mentioned)"
                                + " SELECT DISTINCT msg._id, 1, tweets.favorited, 1, CASE tweets.tweet_type WHEN 2 THEN 1 ELSE 0 END"
                                + " FROM ((tweets INNER JOIN msg ON tweets._id = msg.msg_oid)"
                                + " INNER JOIN user on tweets.author_id = user.username)");
                        db.execSQL("UPDATE msg SET author_id = (SELECT user._id FROM (tweets INNER JOIN user on tweets.author_id = user.username) WHERE tweets._id = msg.msg_oid)");
                        db.execSQL("UPDATE msg SET sender_id = author_id");

                        // Now add Users to whom replies were
                        db.execSQL("INSERT INTO user (username, origin_id, user_ins_date) SELECT DISTINCT in_reply_to_author_id, 1,"
                                + System.currentTimeMillis()
                                + " FROM tweets WHERE tweets.in_reply_to_author_id NOT NULL"
                                + " AND (tweets.in_reply_to_author_id <> 'null')"
                                + " AND NOT EXISTS (SELECT * FROM user WHERE username = tweets.in_reply_to_author_id)");
                        // Add messages (templates for messages) to which
                        // replies were
                        db.execSQL("INSERT INTO msg (msg_oid, origin_id, author_id, sender_id, msg_ins_date)"
                                + " SELECT DISTINCT tweets.in_reply_to_status_id, 1, user._id, user._id,"
                                + System.currentTimeMillis()
                                + " FROM (tweets INNER JOIN user ON tweets.in_reply_to_author_id = user.username)"
                                + " WHERE (tweets.in_reply_to_status_id IS NOT NULL)"
                                + " AND (tweets.in_reply_to_status_id <> 'null')"
                                + " AND NOT EXISTS (SELECT * FROM msg AS m2 WHERE m2.msg_oid = tweets.in_reply_to_status_id)");

                        // Add Direct Messages
                        db.execSQL("INSERT INTO msg (body, msg_oid, origin_id, author_id, sender_id, recipient_id, msg_created_date, msg_sent_date, msg_ins_date)"
                                + " SELECT DISTINCT message, directmessages._id, 1, user._id, user._id, 1, directmessages.sent, directmessages.sent, "
                                + System.currentTimeMillis()
                                + " FROM (directmessages INNER JOIN user ON directmessages.author_id = user.username)");

                        db.execSQL("INSERT INTO msgofuser (msg_id, user_id, directed)"
                                + " SELECT DISTINCT msg._id, 1, 1"
                                + " FROM ((directmessages INNER JOIN msg ON directmessages._id = msg.msg_oid)"
                                + " INNER JOIN user on directmessages.author_id = user.username)");

                    }
                    db.execSQL("DROP TABLE " + "tweets" + ";");
                    db.execSQL("DROP TABLE " + "directmessages" + ";");
                    MyLog.d(TAG, "Successfully upgraded database from version 8 to version 9");

                    MyAppWidgetConfigure.deleteWidgets(MyPreferences.getContext(),
                            "org.andstatus.app", "org.andstatus.app.appwidget.MyAppWidgetProvider");
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage());
                    // This throws an error
                    db.execSQL("Database upgrade failed");
                }

            }
            Log.i(TAG, "Successfully upgraded database from version " + oldVersion + " to version "
                    + newVersion + ".");
        }
    }
}
