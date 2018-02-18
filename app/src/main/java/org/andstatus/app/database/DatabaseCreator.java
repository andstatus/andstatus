/*
 * Copyright (c) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;

import org.andstatus.app.data.DbUtils;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.database.table.ActorTable;
import org.andstatus.app.database.table.AudienceTable;
import org.andstatus.app.database.table.CommandTable;
import org.andstatus.app.database.table.DownloadTable;
import org.andstatus.app.database.table.FriendshipTable;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.database.table.OriginTable;
import org.andstatus.app.database.table.TimelineTable;
import org.andstatus.app.database.table.UserTable;
import org.andstatus.app.util.MyLog;

/**
 * @author yvolk@yurivolkov.com
 */
public class DatabaseCreator {
    /**
     * Current database scheme version, defined by AndStatus developers.
     * This is used to check (and upgrade if necessary) existing database after application update.
     *
     * v.28 2018-02-18 app.v.37 UserTable added, one-to-many linked to ActorTable
     * v.27 2017-11-04 app.v.36 Moving to ActivityStreams data model.
     *                 ActivityTable and AudienceTable added, MsOfUserTable dropped. Others refactored.
     * v.26 2016-11-27 app.v.31 Conversation ID added to MsgTable, see https://github.com/andstatus/andstatus/issues/361
     * v.25 2016-06-07 app.v.27 TimelineTable and CommandTable added
     * v.24 2016-02-27 app.v.23 several attributes added to User, https://github.com/andstatus/andstatus/issues/320
     * v.23 2015-09-02 app.v.19 msg_status added for Unsent messages
     * v.22 2015-04-04 app.v.17 use_legacy_http added to Origin
     * v.21 2015-03-14 app.v.16 mention_as_webfinger_id added to Origin,
     *                 index on {@link NoteTable#IN_REPLY_TO_NOTE_ID} added.
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
    public static final int DATABASE_VERSION = 28;
    public static final long ORIGIN_ID_TWITTER =  1L;

    private final SQLiteDatabase db;

    public DatabaseCreator(SQLiteDatabase db) {
        this.db = db;
    }

    /**
     * On data types in SQLite see <a href="http://www.sqlite.org/datatype3.html">Datatypes In SQLite Version 3</a>.
     * See also <a href="http://sqlite.org/autoinc.html">SQLite Autoincrement</a>.
     */
    public DatabaseCreator create() {
        MyLog.i(this, "Creating tables");
        OriginTable.create(db);
        NoteTable.create(db);
        UserTable.create(db);
        ActorTable.create(db);
        AudienceTable.create(db);
        FriendshipTable.create(db);
        DownloadTable.create(db);
        TimelineTable.create(db);
        ActivityTable.create(db);
        CommandTable.create(db);
        return this;
    }

    void insertData() {
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
                " 6, 3,'GNUsocial.de',   'https://gnusocial.de',     1, 1, 1,    0,  0",
                " 7, 3,'GNUsocial.no',   'https://gnusocial.no',     1, 1, 1,    0,  0",
                " 8, 3,'Quitter.no',     'https://quitter.no',       1, 1, 1,    0,  0",
                " 9, 3,'Quitter.is',     'https://quitter.is',       1, 1, 1,    0,  0",
                "10, 3,'Quitter.Espana', 'https://quitter.es',       1, 1, 1,    0,  0",
                "11, 4,'Mastodon.social','https://mastodon.social',  1, 1, 1,    0,  0",
                "12, 4,'Mastodon.cloud', 'https://mastodon.cloud',   1, 1, 1,    0,  0",
                "13, 4,'mstdn.jp',       'https://mstdn.jp',         1, 1, 1,    0,  0",
                "14, 4,'Pawoo',          'https://pawoo.net',        1, 1, 1,    0,  0",
                "15, 4,'friends.nico',   'https://friends.nico',     1, 1, 1,    0,  0",
                "16, 4,'Mastodon.xyz',   'https://mastodon.xyz',     1, 1, 1,    0,  0"
        };
        for (String value : values) {
            DbUtils.execSQL(db, sqlIns.replace("%s", value));
        }
    }
}
