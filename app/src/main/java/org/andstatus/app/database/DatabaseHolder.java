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

package org.andstatus.app.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;

import org.andstatus.app.context.MyContextState;
import org.andstatus.app.context.MyStorage;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.net.http.SslModeEnum;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.TriState;

/**
 * Database definitions and helper class.
 * Used mainly by {@link MyProvider}
 */
public final class DatabaseHolder extends SQLiteOpenHelper  {
    private final boolean creationEnabled;
    private boolean wasNotCreated = false;

    /**
     * Current database scheme version, defined by AndStatus developers.
     * This is used to check (and upgrade if necessary) 
     * existing database after application update.
     *
     * v.24 2016-02-27 app.v.23 several attributes added to User, https://github.com/andstatus/andstatus/issues/320
     * v.23 2015-09-02 app.v.19 msg_status added for Unsent messages
     * v.22 2015-04-04 app.v.17 use_legacy_http added to Origin
     * v.21 2015-03-14 app.v.16 mention_as_webfinger_id added to Origin, 
     *                 index on {@link MsgTable#IN_REPLY_TO_MSG_ID} added.
     * v.20 2015-02-04 app.v.15 SslMode added to Origin
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
     * v.10 2013-03-23 User table extended with columns to store information on timelines loaded.
     * v.9  2012-02-26 Totally new database design using table joins.
     *      All messages are in the same table. 
     *      Allows to have multiple User Accounts in different Originating systems (twitter.com etc. ) 
     */
    public static final int DATABASE_VERSION = 24;
    public static final String DATABASE_NAME = "andstatus.sqlite";

    public DatabaseHolder(Context context, boolean creationEnabled) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.creationEnabled = creationEnabled;
        if (!creationEnabled && !context.getDatabasePath(DATABASE_NAME).exists()) {
            wasNotCreated = true;
        }
    }

    private final ThreadLocal<Boolean> onUpgradeTriggered = new ThreadLocal<>();
    public MyContextState checkState() {
        if (wasNotCreated) {
            return MyContextState.DATABASE_UNAVAILABLE;
        }
        if (MyDatabaseConverterController.isUpgradeError()) {
            return MyContextState.ERROR;
        }
        MyContextState state = MyContextState.ERROR;
        try {
            onUpgradeTriggered.set(false);
            if (MyStorage.isDataAvailable()) {
                SQLiteDatabase db = getWritableDatabase();
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
        if (!creationEnabled) {
            wasNotCreated = true;
            MyLog.e(this, "Database creation disabled");
            return;
        }

        MyLog.i(this, "Creating tables");
        execSQL(db, "CREATE TABLE " + MsgTable.TABLE_NAME + " ("
                + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," 
                + MsgTable.ORIGIN_ID + " INTEGER NOT NULL,"
                + MsgTable.MSG_OID + " TEXT,"
                + MsgTable.MSG_STATUS + " INTEGER NOT NULL DEFAULT 0,"
                + MsgTable.AUTHOR_ID + " INTEGER,"
                + MsgTable.SENDER_ID + " INTEGER,"
                + MsgTable.RECIPIENT_ID + " INTEGER,"
                + MsgTable.BODY + " TEXT,"
                + MsgTable.VIA + " TEXT,"
                + MsgTable.URL + " TEXT,"
                + MsgTable.IN_REPLY_TO_MSG_ID + " INTEGER,"
                + MsgTable.IN_REPLY_TO_USER_ID + " INTEGER,"
                + MsgTable.CREATED_DATE + " INTEGER,"
                + MsgTable.SENT_DATE + " INTEGER,"
                + MsgTable.INS_DATE + " INTEGER NOT NULL,"
                + MsgTable.PUBLIC + " BOOLEAN DEFAULT 0 NOT NULL"
                + ")");

        execSQL(db, "CREATE UNIQUE INDEX idx_msg_origin ON " + MsgTable.TABLE_NAME + " ("
                + MsgTable.ORIGIN_ID + ", "
                + MsgTable.MSG_OID
                + ")");

        execSQL(db, "CREATE INDEX idx_msg_sent_date ON " + MsgTable.TABLE_NAME + " ("
                + MsgTable.SENT_DATE
                + ")");

        execSQL(db, "CREATE INDEX idx_msg_in_reply_to_msg_id ON " + MsgTable.TABLE_NAME + " ("
                + MsgTable.IN_REPLY_TO_MSG_ID
                + ")");
        
        execSQL(db, "CREATE TABLE " + MsgOfUserTable.TABLE_NAME + " ("
                + MsgOfUserTable.USER_ID + " INTEGER NOT NULL,"
                + MsgOfUserTable.MSG_ID + " INTEGER NOT NULL,"
                + MsgOfUserTable.SUBSCRIBED + " BOOLEAN DEFAULT 0 NOT NULL,"
                + MsgOfUserTable.FAVORITED + " BOOLEAN DEFAULT 0 NOT NULL,"
                + MsgOfUserTable.REBLOGGED + " BOOLEAN DEFAULT 0 NOT NULL,"
                + MsgOfUserTable.REBLOG_OID + " TEXT,"
                + MsgOfUserTable.MENTIONED + " BOOLEAN DEFAULT 0 NOT NULL,"
                + MsgOfUserTable.REPLIED + " BOOLEAN DEFAULT 0 NOT NULL,"
                + MsgOfUserTable.DIRECTED + " BOOLEAN DEFAULT 0 NOT NULL,"
                + " CONSTRAINT pk_msgofuser PRIMARY KEY (" + MsgOfUserTable.USER_ID + " ASC, " + MsgOfUserTable.MSG_ID + " ASC)"
                + ")");
        
        execSQL(db, "CREATE TABLE " + UserTable.TABLE_NAME + " ("
                + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," 
                + UserTable.ORIGIN_ID + " INTEGER NOT NULL,"
                + UserTable.USER_OID + " TEXT,"
                + UserTable.USERNAME + " TEXT NOT NULL,"
                + UserTable.WEBFINGER_ID + " TEXT NOT NULL,"
                + UserTable.REAL_NAME + " TEXT,"
                + UserTable.DESCRIPTION + " TEXT,"
                + UserTable.LOCATION + " TEXT,"
                + UserTable.PROFILE_URL + " TEXT,"
                + UserTable.HOMEPAGE + " TEXT,"
                + UserTable.AVATAR_URL + " TEXT,"
                + UserTable.BANNER_URL + " TEXT,"
                + UserTable.MSG_COUNT + " INTEGER DEFAULT 0 NOT NULL,"
                + UserTable.FAVORITES_COUNT + " INTEGER DEFAULT 0 NOT NULL,"
                + UserTable.FOLLOWING_COUNT + " INTEGER DEFAULT 0 NOT NULL,"
                + UserTable.FOLLOWERS_COUNT + " INTEGER DEFAULT 0 NOT NULL,"
                + UserTable.CREATED_DATE + " INTEGER DEFAULT 0 NOT NULL,"
                + UserTable.UPDATED_DATE + " INTEGER DEFAULT 0 NOT NULL,"
                + UserTable.INS_DATE + " INTEGER NOT NULL,"
                + UserTable.HOME_TIMELINE_POSITION + " TEXT DEFAULT '' NOT NULL,"
                + UserTable.HOME_TIMELINE_ITEM_DATE + " INTEGER DEFAULT 0 NOT NULL,"
                + UserTable.HOME_TIMELINE_DATE + " INTEGER DEFAULT 0 NOT NULL,"
                + UserTable.FAVORITES_TIMELINE_POSITION + " TEXT DEFAULT '' NOT NULL,"
                + UserTable.FAVORITES_TIMELINE_ITEM_DATE + " INTEGER DEFAULT 0 NOT NULL,"
                + UserTable.FAVORITES_TIMELINE_DATE + " INTEGER DEFAULT 0 NOT NULL,"
                + UserTable.DIRECT_TIMELINE_POSITION + " TEXT DEFAULT '' NOT NULL,"
                + UserTable.DIRECT_TIMELINE_ITEM_DATE + " INTEGER DEFAULT 0 NOT NULL,"
                + UserTable.DIRECT_TIMELINE_DATE + " INTEGER DEFAULT 0 NOT NULL,"
                + UserTable.MENTIONS_TIMELINE_POSITION + " TEXT DEFAULT '' NOT NULL,"
                + UserTable.MENTIONS_TIMELINE_ITEM_DATE + " INTEGER DEFAULT 0 NOT NULL,"
                + UserTable.MENTIONS_TIMELINE_DATE + " INTEGER DEFAULT 0 NOT NULL,"
                + UserTable.USER_TIMELINE_POSITION + " TEXT DEFAULT '' NOT NULL,"
                + UserTable.USER_TIMELINE_ITEM_DATE + " INTEGER DEFAULT 0 NOT NULL,"
                + UserTable.USER_TIMELINE_DATE + " INTEGER DEFAULT 0 NOT NULL,"
                + UserTable.FOLLOWING_USER_DATE + " INTEGER DEFAULT 0 NOT NULL,"
                + UserTable.FOLLOWERS_USER_DATE + " INTEGER DEFAULT 0 NOT NULL,"
                + UserTable.USER_MSG_ID + " INTEGER DEFAULT 0 NOT NULL,"
                + UserTable.USER_MSG_DATE + " INTEGER DEFAULT 0 NOT NULL"
                + ")");

        execSQL(db, "CREATE UNIQUE INDEX idx_user_origin ON " + UserTable.TABLE_NAME + " ("
                + UserTable.ORIGIN_ID + ", "
                + UserTable.USER_OID
                + ")");

        execSQL(db, "CREATE TABLE " + FriendshipTable.TABLE_NAME + " ("
                + FriendshipTable.USER_ID + " INTEGER NOT NULL,"
                + FriendshipTable.FRIEND_ID + " INTEGER NOT NULL,"
                + FriendshipTable.FOLLOWED + " BOOLEAN DEFAULT 1 NOT NULL,"
                + " CONSTRAINT pk_followinguser PRIMARY KEY (" + FriendshipTable.USER_ID + " ASC, " + FriendshipTable.FRIEND_ID + " ASC)"
                + ")");

        execSQL(db, "CREATE TABLE " + DownloadTable.TABLE_NAME + " ("
                + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," 
                + DownloadTable.DOWNLOAD_TYPE + " INTEGER NOT NULL,"
                + DownloadTable.USER_ID + " INTEGER,"
                + DownloadTable.MSG_ID + " INTEGER,"
                + DownloadTable.CONTENT_TYPE + " INTEGER NOT NULL,"
                + DownloadTable.VALID_FROM + " INTEGER NOT NULL,"
                + DownloadTable.URI + " TEXT NOT NULL,"
                + DownloadTable.LOADED_DATE + " INTEGER,"
                + DownloadTable.DOWNLOAD_STATUS + " INTEGER NOT NULL DEFAULT 0,"
                + DownloadTable.FILE_NAME + " TEXT"
                + ")");

        execSQL(db, "CREATE INDEX idx_download_user ON " + DownloadTable.TABLE_NAME + " ("
                + DownloadTable.USER_ID + ", "
                + DownloadTable.DOWNLOAD_STATUS
                + ")");

        execSQL(db, "CREATE INDEX idx_download_msg ON " + DownloadTable.TABLE_NAME + " ("
                + DownloadTable.MSG_ID + ", "
                + DownloadTable.CONTENT_TYPE  + ", "
                + DownloadTable.DOWNLOAD_STATUS
                + ")");
        
        execSQL(db, "CREATE TABLE " + OriginTable.TABLE_NAME + " ("
                + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," 
                + OriginTable.ORIGIN_TYPE_ID + " INTEGER NOT NULL,"
                + OriginTable.ORIGIN_NAME + " TEXT NOT NULL,"
                + OriginTable.ORIGIN_URL + " TEXT NOT NULL,"
                + OriginTable.SSL + " BOOLEAN DEFAULT 1 NOT NULL,"
                + OriginTable.SSL_MODE + " INTEGER DEFAULT " + SslModeEnum.SECURE.getId() + " NOT NULL,"
                + OriginTable.ALLOW_HTML + " BOOLEAN DEFAULT 1 NOT NULL,"
                + OriginTable.TEXT_LIMIT + " INTEGER NOT NULL,"
                + OriginTable.SHORT_URL_LENGTH + " INTEGER NOT NULL DEFAULT 0,"
                + OriginTable.MENTION_AS_WEBFINGER_ID + " INTEGER DEFAULT " + TriState.UNKNOWN.getId() + " NOT NULL,"
                + OriginTable.USE_LEGACY_HTTP + " INTEGER DEFAULT " + TriState.UNKNOWN.getId() + " NOT NULL,"
                + OriginTable.IN_COMBINED_GLOBAL_SEARCH + " BOOLEAN DEFAULT 1 NOT NULL,"
                + OriginTable.IN_COMBINED_PUBLIC_RELOAD + " BOOLEAN DEFAULT 1 NOT NULL"
                + ")");
        
        execSQL(db, "CREATE UNIQUE INDEX idx_origin_name ON " + OriginTable.TABLE_NAME + " ("
                + OriginTable.ORIGIN_NAME
                + ")");
        
        String sqlIns = "INSERT INTO " + OriginTable.TABLE_NAME + " ("
                + BaseColumns._ID + "," 
                + OriginTable.ORIGIN_TYPE_ID + ","
                + OriginTable.ORIGIN_NAME + ","
                + OriginTable.ORIGIN_URL + ","
                + OriginTable.SSL + ","
                + OriginTable.SSL_MODE + ","
                + OriginTable.ALLOW_HTML + ","
                + OriginTable.TEXT_LIMIT + ","
                + OriginTable.SHORT_URL_LENGTH
                + ") VALUES ("
                + "%s"
                + ")";
        String[] values = {
                Long.toString(ORIGIN_ID_TWITTER) + 
                ",   1,'Twitter',        'https://api.twitter.com',  1, 1, 0,  140, 23",
                " 2, 2,'Pump.io',        '',                         1, 1, 1,    0,  0",
                " 3, 3,'Quitter.se',     'https://quitter.se',       1, 1, 1,    0,  0",
                " 4, 3,'LoadAverage',    'https://loadaverage.org',  1, 1, 1,    0,  0",
                " 5, 3,'Vinilox',        'http://status.vinilox.eu', 1, 1, 1,    0,  0",
                " 6, 3,'GNUsocial.de',   'https://gnusocial.de',     1, 1, 1,    0,  0",
                " 7, 3,'GNUsocial.no',   'https://gnusocial.no',     1, 1, 1,    0,  0",
                " 8, 3,'Quitter.no',     'https://quitter.no',       1, 1, 1,    0,  0",
                " 9, 3,'Quitter.is',     'https://quitter.is',       1, 1, 1,    0,  0",
                "10, 3,'Quitter.Espana', 'https://quitter.es',       1, 1, 1,    0,  0"
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
