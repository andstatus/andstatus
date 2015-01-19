/* 
 * Copyright (c) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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
import org.andstatus.app.context.MyContextState;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.util.MyLog;

import java.util.Locale;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;

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
     * v.19 2014-11-15 Index on sent date added to messages
     * v.18 2014-09-21 Duplicated User.USERNAME allowed
     * v.17 2014-09-05 Attachment added. Origin "URL" instead of "host"
     * v.16 2014-05-03 Account persistence changed
     * v.15 2014-02-16 Public timeline added
     * v.14 2013-12-15 Origin table added
     * v.13 2013-12-06 Avatar table added
     * v.12 2013-08-30 Adapting for Pump.Io
     * v.11 2013-05-18 FollowingUser table added. User table extended with a column
     *      to store the date the list of Following users was loaded.
     * v.10 2013-03-23 User table extended with columns
     *      to store information on timelines loaded.
     * v.9  2012-02-26 Totally new database design using table joins.
     *      All messages are in the same table. 
     *      Allows to have multiple User Accounts in different Originating systems (twitter.com etc. ) 
     */
    public static final int DATABASE_VERSION = 19;
    public static final String DATABASE_NAME = "andstatus.sqlite";

    /**
     * Table for both public and direct messages 
     * i.e. for tweets, dents, notices 
     * and also for "direct messages", "direct dents" etc.
     */
    public static final class Msg implements BaseColumns {
        public static final String TABLE_NAME = Msg.class.getSimpleName().toLowerCase(Locale.US);

        private Msg() {
        }
        
        // Table columns are below:
        /*
         * {@link BaseColumns#_ID} is primary key in this database
         * No, we can not rename the {@link BaseColumns#_ID}, it should always be "_id". 
         * See <a href="http://stackoverflow.com/questions/3359414/android-column-id-does-not-exist">Android column '_id' does not exist?</a>
         */

        /**
         * ID of the originating (source) system (twitter.com, identi.ca, ... ) where the row was created
         * See {@link Origin#_ID}
         */
        public static final String ORIGIN_ID =  "origin_id";
        /**
         * ID in the originating system
         * The id is not unique for this table, because we have IDs from different systems in one column
         * and IDs from different systems may overlap.
         */
        public static final String MSG_OID = "msg_oid";
        /**
         * A link to the representation of the resource. Currently this is simply URL to the HTML 
         * representation of the resource (its "permalink") 
         */
        public static final String URL = "url";
        /**
         * Author of the message = User._ID
         * If message was "Reblogged" ("Retweeted", "Repeated", ...) this is Original author (whose message was reblogged)
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
         * We store it as long returned by {@link org.andstatus.app.net.Connection#dateFromJson(JSONObject, String) }. 
         * NULL means the row was not retrieved from the Internet yet
         */
        public static final String CREATED_DATE = "msg_created_date";
        /**
         * Date and time when the message was sent,
         * it's not equal to {@link MyDatabase.Msg#CREATED_DATE} for reblogged messages
         * We change the value if we reblog the message in the application 
         * or if we receive new reblog of the message
         */
        public static final String SENT_DATE = "msg_sent_date";
        /**
         * Date and time the row was inserted into this database
         */
        public static final String INS_DATE = "msg_ins_date";
        /**
         * The Msg is public
         */
        public static final String PUBLIC = "public";

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
    public static final class MsgOfUser {
        public static final String TABLE_NAME = MsgOfUser.class.getSimpleName().toLowerCase(Locale.US);
        private MsgOfUser() {
        }
        
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
         * The Msg is reblogged by this User
         * In some sense REBLOGGED is like FAVORITED. 
         * Main difference: visibility. REBLOGGED are shown for all followers in their Home timelines.
         */
        public static final String REBLOGGED = "reblogged";
        /**
         * ID in the originating system of the "reblog" message
         * null for the message that was not reblogged
         * We use THIS id when we need to "undo reblog"
         */
        public static final String REBLOG_OID = "reblog_oid";
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
    }

    /**
     * Users table (they are both senders AND recipients in the {@link Msg} table)
     * Some of these Users are Accounts (connected to accounts in AndStatus), 
     * see {@link MyAccount#getUserId()}
     */
    public static final class User implements BaseColumns {
        public static final String TABLE_NAME = User.class.getSimpleName().toLowerCase(Locale.US);

        private User() {
        }

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
         * It looks like an email address with your nickname then "@" then your server
         */
        public static final String WEBFINGER_ID = "webfinger_id";
        /**
         * This is called "name" in Twitter API
         */
        public static final String REAL_NAME = "real_name";
        /**
         * The latest url of the avatar 
         */
        public static final String AVATAR_URL = "avatar_url";
        /**
         * User's description
         */
        public static final String DESCRIPTION = "user_description";
        /**
         * User's web home page
         */
        public static final String HOMEPAGE = "homepage";
        /**
         * A link to the representation of the resource. Currently this is simply URL to the HTML 
         * representation of the resource (its "permalink") 
         */
        public static final String URL = "url";
        /**
         * Date and time when the row was created in the originating system.
         * We store it as long returned by {@link org.andstatus.app.net.Connection#dateFromJson(JSONObject, String) }. 
         * NULL means the row was not retrieved from the Internet yet
         * (And maybe there is no such User in the originating system...)
         */
        public static final String CREATED_DATE = "user_created_date";
        /**
         * Date and time the row was inserted into this database
         */
        public static final String INS_DATE = "user_ins_date";
        
        /**
         * Columns holding information on timelines downloaded: 
         * last message id and last date-time the timeline was downloaded. 
         */
        public static final String HOME_TIMELINE_POSITION = "home_timeline_position";
        public static final String HOME_TIMELINE_ITEM_DATE = "home_timeline_item_date";
        public static final String HOME_TIMELINE_DATE = "home_timeline_date";
        public static final String FAVORITES_TIMELINE_POSITION = "favorites_timeline_position";
        public static final String FAVORITES_TIMELINE_ITEM_DATE = "favorites_timeline_item_date";
        public static final String FAVORITES_TIMELINE_DATE = "favorites_timeline_date";
        public static final String DIRECT_TIMELINE_POSITION = "direct_timeline_position";
        public static final String DIRECT_TIMELINE_ITEM_DATE = "direct_timeline_item_date";
        public static final String DIRECT_TIMELINE_DATE = "direct_timeline_date";
        public static final String MENTIONS_TIMELINE_POSITION = "mentions_timeline_position";
        public static final String MENTIONS_TIMELINE_ITEM_DATE = "mentions_timeline_item_date";
        public static final String MENTIONS_TIMELINE_DATE = "mentions_timeline_date";
        public static final String USER_TIMELINE_POSITION = "user_timeline_position";
        public static final String USER_TIMELINE_ITEM_DATE = "user_timeline_item_date";
        public static final String USER_TIMELINE_DATE = "user_timeline_date";
        /**
         * For the list ("collection") of following users 
         * we store only the date-time of the last retrieval of the list 
         */
        public static final String FOLLOWING_USER_DATE = "following_user_date";
        /**
         * Id of the latest message where this User was a Sender or an Author
         */
        public static final String USER_MSG_ID = "user_msg_id";
        /**
         * Date of the latest message where this User was a Sender or an Author
         */
        public static final String USER_MSG_DATE = "user_msg_date";
        
        /*
         * Derived columns (they are not stored in this table but are result of joins)
         */
        /**
         * Alias for the primary key
         */
        public static final String USER_ID = "user_id";
        /**
         * Alias used in a timeline to distinguish messages for different users
         */
        public static final String LINKED_USER_ID = "linked_user_id";
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
        /** Derived from {@link Msg#RECIPIENT_ID} */
        public static final String RECIPIENT_NAME = "recipient_name";

        public static final String DEFAULT_SORT_ORDER = USERNAME + " ASC";
    }

    /**
     * Following users for the {@link FollowingUser#USER_ID}. 
     * I.e. this is a list of user IDs for every user the specified 
     * (by {@link FollowingUser#USER_ID}) user is following (otherwise known as their "friends"). 
     */
    public static final class FollowingUser {
        public static final String TABLE_NAME = FollowingUser.class.getSimpleName().toLowerCase(Locale.US);
        private FollowingUser() {
        }
        
        public static final String USER_ID = User.USER_ID;
        public static final String FOLLOWING_USER_ID = "following_user_id";
        /**
         * two state (1/0) flag showing if {@link FollowingUser#USER_ID} is following {@link FollowingUser#FOLLOWING_USER_ID}
         */
        public static final String USER_FOLLOWED = "user_followed";
        
        /**
         * Derived column: if the Author of the message is followed by the User
         */
        public static final String AUTHOR_FOLLOWED = "author_followed";
        /**
         * Derived column: if the Sender of the message is followed by the User
         */
        public static final String SENDER_FOLLOWED = "sender_followed";
    }

    /** Avatar, Message attachment...
     */
    public static final class Download implements BaseColumns {
        public static final String TABLE_NAME = Download.class.getSimpleName().toLowerCase(Locale.US);
        private Download() {
        }
        /** See {@link DownloadType} */
        public static final String DOWNLOAD_TYPE = "download_type";
        /** Avatar is connected to exactly one user */
        public static final String USER_ID = User.USER_ID;
        /** Attachment is connected to a message */
        public static final String MSG_ID =  Msg.MSG_ID;
        /** See {@link MyContentType} */
        public static final String CONTENT_TYPE = "content_type";
        /**
         * Date and time of the information. Used to decide which 
         * (version of) information
         * is newer (we may upload older information later...)
         */
        public static final String VALID_FROM = "valid_from";
        public static final String URL = "url";
        /**
         * Date and time there was last attempt to load avatar. The attempt may be successful or not.
         */
        public static final String LOADED_DATE = "loaded_date";
        /**
         * See {@link DownloadStatus}
         */
        public static final String DOWNLOAD_STATUS = "download_status";
        public static final String FILE_NAME = "file_name";
        
        /*
         * Derived columns (they are not stored in this table but are result of joins)
         */
        /** Alias for the primary key */
        public static final String AVATAR_ID = "avatar_id";
        public static final String IMAGE_ID = "image_id";

        public static final String AVATAR_FILE_NAME = "avatar_file_name";
        /** Alias helping to show first attached image */
        public static final String IMAGE_FILE_NAME = "image_file_name";
        public static final String IMAGE_URL = "image_url";
    }

    public static final class Origin implements BaseColumns {
        public static final String TABLE_NAME = Origin.class.getSimpleName().toLowerCase(Locale.US);
        private Origin() {
        }
        /**
         * Reference to {@link OriginType#getId()}
         */
        public static final String ORIGIN_TYPE_ID = "origin_type_id";
        public static final String ORIGIN_NAME = "origin_name";
        public static final String ORIGIN_URL = "origin_url";
        public static final String SSL = "ssl";
        public static final String ALLOW_HTML = "allow_html";
        public static final String TEXT_LIMIT = "text_limit";
        public static final String SHORT_URL_LENGTH = "short_url_length";
    }
    
    /**
     * ids in originating system
     */
    public enum OidEnum {
        /** oid of this message */
        MSG_OID,
        /** If the message was reblogged by the User,
         * then oid of the "reblog" message,  
         * else oid of the reblogged message (the first message which was reblogged)
         */
        REBLOG_OID,
        /** oid of this User */
        USER_OID
    }
    
    
    public MyDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    private ThreadLocal<Boolean> onUpgradeTriggered = new ThreadLocal<Boolean>();
    public MyContextState checkState() {
        if (MyDatabaseConverterController.isUpgradeError()) {
            return MyContextState.ERROR;
        }
        MyContextState state = MyContextState.ERROR;
        SQLiteDatabase db = null;
        try {
            onUpgradeTriggered.set(false);
            if (MyPreferences.isDataAvailable()) {
                db = getReadableDatabase();
                if (onUpgradeTriggered.get() || MyDatabaseConverterController.isUpgrading()) {
                    state = MyContextState.UPGRADING;
                } else {
                    if (db != null && db.isOpen()) {
                        state = MyContextState.READY;
                    }
                }
            }
        } catch (IllegalStateException e) {
            MyLog.v(this, e);
            if (onUpgradeTriggered.get()) {
                state = MyContextState.UPGRADING;
            }
        } catch (Exception e) {
            MyLog.d(this, "Error during checkState", e);
        }
        return state;
    }
    
    public static final long ORIGIN_ID_TWITTER =  1L;
    /**
     * On datatypes in SQLite see <a href="http://www.sqlite.org/datatype3.html">Datatypes In SQLite Version 3</a>.
     * See also <a href="http://sqlite.org/autoinc.html">SQLite Autoincrement</a>.
     */
    @Override
    public void onCreate(SQLiteDatabase db) {
        MyLog.i(this, "Creating tables");
        execSQL(db, "CREATE TABLE " + Msg.TABLE_NAME + " (" 
                + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," 
                + Msg.ORIGIN_ID + " INTEGER NOT NULL," 
                + Msg.MSG_OID + " TEXT," 
                + Msg.AUTHOR_ID + " INTEGER," 
                + Msg.SENDER_ID + " INTEGER," 
                + Msg.RECIPIENT_ID + " INTEGER," 
                + Msg.BODY + " TEXT," 
                + Msg.VIA + " TEXT," 
                + Msg.URL + " TEXT," 
                + Msg.IN_REPLY_TO_MSG_ID + " INTEGER," 
                + Msg.IN_REPLY_TO_USER_ID + " INTEGER," 
                + Msg.CREATED_DATE + " INTEGER,"
                + Msg.SENT_DATE + " INTEGER,"
                + Msg.INS_DATE + " INTEGER NOT NULL,"
                + Msg.PUBLIC + " BOOLEAN DEFAULT 0 NOT NULL" 
                + ")");

        execSQL(db, "CREATE UNIQUE INDEX idx_msg_origin ON " + Msg.TABLE_NAME + " (" 
                + Msg.ORIGIN_ID + ", "
                + Msg.MSG_OID
                + ")");

        execSQL(db, "CREATE INDEX idx_msg_sent_date ON " + Msg.TABLE_NAME + " (" 
                + Msg.SENT_DATE
                + ")");
        
        execSQL(db, "CREATE TABLE " + MsgOfUser.TABLE_NAME + " (" 
                + MsgOfUser.USER_ID + " INTEGER NOT NULL," 
                + MsgOfUser.MSG_ID + " INTEGER NOT NULL," 
                + MsgOfUser.SUBSCRIBED + " BOOLEAN DEFAULT 0 NOT NULL," 
                + MsgOfUser.FAVORITED + " BOOLEAN DEFAULT 0 NOT NULL," 
                + MsgOfUser.REBLOGGED + " BOOLEAN DEFAULT 0 NOT NULL," 
                + MsgOfUser.REBLOG_OID + " TEXT," 
                + MsgOfUser.MENTIONED + " BOOLEAN DEFAULT 0 NOT NULL," 
                + MsgOfUser.REPLIED + " BOOLEAN DEFAULT 0 NOT NULL," 
                + MsgOfUser.DIRECTED + " BOOLEAN DEFAULT 0 NOT NULL," 
                + " CONSTRAINT pk_msgofuser PRIMARY KEY (" + MsgOfUser.USER_ID + " ASC, " + MsgOfUser.MSG_ID + " ASC)"
                + ")");
        
        execSQL(db, "CREATE TABLE " + User.TABLE_NAME + " (" 
                + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," 
                + User.ORIGIN_ID + " INTEGER NOT NULL," 
                + User.USER_OID + " TEXT," 
                + User.USERNAME + " TEXT NOT NULL," 
                + User.WEBFINGER_ID + " TEXT NOT NULL," 
                + User.REAL_NAME + " TEXT," 
                + User.AVATAR_URL + " TEXT," 
                + User.DESCRIPTION + " TEXT," 
                + User.HOMEPAGE + " TEXT," 
                + User.URL + " TEXT," 
                + User.CREATED_DATE + " INTEGER,"
                + User.INS_DATE + " INTEGER NOT NULL,"
                + User.HOME_TIMELINE_POSITION + " TEXT DEFAULT '' NOT NULL," 
                + User.HOME_TIMELINE_ITEM_DATE + " INTEGER DEFAULT 0 NOT NULL," 
                + User.HOME_TIMELINE_DATE + " INTEGER DEFAULT 0 NOT NULL," 
                + User.FAVORITES_TIMELINE_POSITION + " TEXT DEFAULT '' NOT NULL," 
                + User.FAVORITES_TIMELINE_ITEM_DATE + " INTEGER DEFAULT 0 NOT NULL," 
                + User.FAVORITES_TIMELINE_DATE + " INTEGER DEFAULT 0 NOT NULL," 
                + User.DIRECT_TIMELINE_POSITION + " TEXT DEFAULT '' NOT NULL," 
                + User.DIRECT_TIMELINE_ITEM_DATE + " INTEGER DEFAULT 0 NOT NULL," 
                + User.DIRECT_TIMELINE_DATE + " INTEGER DEFAULT 0 NOT NULL," 
                + User.MENTIONS_TIMELINE_POSITION + " TEXT DEFAULT '' NOT NULL," 
                + User.MENTIONS_TIMELINE_ITEM_DATE + " INTEGER DEFAULT 0 NOT NULL," 
                + User.MENTIONS_TIMELINE_DATE + " INTEGER DEFAULT 0 NOT NULL," 
                + User.USER_TIMELINE_POSITION + " TEXT DEFAULT '' NOT NULL," 
                + User.USER_TIMELINE_ITEM_DATE + " INTEGER DEFAULT 0 NOT NULL," 
                + User.USER_TIMELINE_DATE + " INTEGER DEFAULT 0 NOT NULL," 
                + User.FOLLOWING_USER_DATE + " INTEGER DEFAULT 0 NOT NULL," 
                + User.USER_MSG_ID + " INTEGER DEFAULT 0 NOT NULL," 
                + User.USER_MSG_DATE + " INTEGER DEFAULT 0 NOT NULL" 
                + ")");

        execSQL(db, "CREATE UNIQUE INDEX idx_user_origin ON " + User.TABLE_NAME + " (" 
                + User.ORIGIN_ID + ", "
                + User.USER_OID  
                + ")");

        execSQL(db, "CREATE TABLE " + FollowingUser.TABLE_NAME + " (" 
                + FollowingUser.USER_ID + " INTEGER NOT NULL," 
                + FollowingUser.FOLLOWING_USER_ID + " INTEGER NOT NULL," 
                + FollowingUser.USER_FOLLOWED + " BOOLEAN DEFAULT 1 NOT NULL," 
                + " CONSTRAINT pk_followinguser PRIMARY KEY (" + FollowingUser.USER_ID + " ASC, " + FollowingUser.FOLLOWING_USER_ID + " ASC)"
                + ")");

        execSQL(db, "CREATE TABLE " + Download.TABLE_NAME + " (" 
                + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," 
                + Download.DOWNLOAD_TYPE + " INTEGER NOT NULL," 
                + Download.USER_ID + " INTEGER," 
                + Download.MSG_ID + " INTEGER," 
                + Download.CONTENT_TYPE + " INTEGER NOT NULL," 
                + Download.VALID_FROM + " INTEGER NOT NULL,"
                + Download.URL + " TEXT NOT NULL," 
                + Download.LOADED_DATE + " INTEGER,"
                + Download.DOWNLOAD_STATUS + " INTEGER NOT NULL DEFAULT 0," 
                + Download.FILE_NAME + " TEXT" 
                + ")");

        execSQL(db, "CREATE INDEX idx_download_user ON " + Download.TABLE_NAME + " (" 
                + Download.USER_ID + ", "
                + Download.DOWNLOAD_STATUS
                + ")");

        execSQL(db, "CREATE INDEX idx_download_msg ON " + Download.TABLE_NAME + " (" 
                + Download.MSG_ID + ", "
                + Download.CONTENT_TYPE  + ", "
                + Download.DOWNLOAD_STATUS
                + ")");
        
        execSQL(db, "CREATE TABLE " + Origin.TABLE_NAME + " (" 
                + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," 
                + Origin.ORIGIN_TYPE_ID + " INTEGER NOT NULL," 
                + Origin.ORIGIN_NAME + " TEXT NOT NULL," 
                + Origin.ORIGIN_URL + " TEXT NOT NULL," 
                + Origin.SSL + " BOOLEAN DEFAULT 0 NOT NULL," 
                + Origin.ALLOW_HTML + " BOOLEAN DEFAULT 0 NOT NULL," 
                + Origin.TEXT_LIMIT + " INTEGER NOT NULL,"
                + Origin.SHORT_URL_LENGTH + " INTEGER NOT NULL DEFAULT 0" 
                + ")");
        
        execSQL(db, "CREATE UNIQUE INDEX idx_origin_name ON " + Origin.TABLE_NAME + " (" 
                + Origin.ORIGIN_NAME
                + ")");
        
        String sqlIns = "INSERT INTO " + Origin.TABLE_NAME + " ("
                + BaseColumns._ID + "," 
                + Origin.ORIGIN_TYPE_ID + "," 
                + Origin.ORIGIN_NAME + "," 
                + Origin.ORIGIN_URL + "," 
                + Origin.SSL + "," 
                + Origin.ALLOW_HTML + "," 
                + Origin.TEXT_LIMIT + ","
                + Origin.SHORT_URL_LENGTH
                + ") VALUES ("
                + "%s"
                + ")";
        String[] values = {
                Long.toString(ORIGIN_ID_TWITTER) + 
                 ", 1, 'Twitter', 'https://api.twitter.com',    1, 1,  140, 23",
                "2, 2, 'pump.io', '',                           1, 1,    0,  0",
                "3, 3, 'Quitter.se', 'https://quitter.se',      1, 1,    0,  0",
                "4, 3, 'LoadAverage','https://loadaverage.org', 1, 1,    0,  0",
                "5, 3, 'Vinilox', 'http://status.vinilox.eu',   0, 1,    0,  0",
                "6, 3, 'GNUsocial.de', 'https://gnusocial.de',  1, 1,    0,  0",
                "7, 3, 'GNUsocial.no', 'https://gnusocial.no',  1, 1,    0,  0",
                "8, 3, 'Quitter.no', 'https://quitter.no',      1, 1,    0,  0",
                "9, 3, 'Quitter.is', 'https://quitter.is',      1, 1, 1000,  0",
                "10, 3, 'Quitter España', 'https://quitter.es', 1, 1, 1000,  0",
                "11, 3, 'Quitter.zone', 'https://quitter.zone', 1, 1,  500,  0"
        };
        for (String value : values) {
            execSQL(db, sqlIns.replace("%s", value));
        }
        
    }

    public static void execSQL(SQLiteDatabase db, String sql) {
        MyLog.v("execSQL", sql);
        db.execSQL(sql);
    }
    /**
     * We don't need here neither try-catch nor transactions because they are
     * being used in calling method
     * 
     * @see android.database.sqlite.SQLiteOpenHelper#getWritableDatabase
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)  {
        onUpgradeTriggered.set(true);
        new MyDatabaseConverterController().onUpgrade(db, oldVersion, newVersion);
    }
}
