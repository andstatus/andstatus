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

package org.andstatus.app.database.converter;

import org.andstatus.app.data.DbUtils;
import org.andstatus.app.util.MyLog;

class Convert23 extends ConvertOneStep {
    @Override
    protected void execute2() {
        versionTo = 24;

        sql = "DROP TABLE IF EXISTS newuser";
        DbUtils.execSQL(db, sql);

        sql = "UPDATE user SET user_created_date = 0 WHERE user_created_date IS NULL";
        DbUtils.execSQL(db, sql);
        sql = "UPDATE user SET user_oid = ('andstatustemp:' || _id) WHERE user_oid IS NULL";
        DbUtils.execSQL(db, sql);

        sql = "CREATE TABLE newuser (_id INTEGER PRIMARY KEY AUTOINCREMENT,origin_id INTEGER NOT NULL,user_oid TEXT,username TEXT NOT NULL,webfinger_id TEXT NOT NULL,real_name TEXT,user_description TEXT,location TEXT,profile_url TEXT,homepage TEXT,avatar_url TEXT,banner_url TEXT,msg_count INTEGER DEFAULT 0 NOT NULL,favorited_count INTEGER DEFAULT 0 NOT NULL,following_count INTEGER DEFAULT 0 NOT NULL,followers_count INTEGER DEFAULT 0 NOT NULL,user_created_date INTEGER,user_updated_date INTEGER,user_ins_date INTEGER NOT NULL,home_timeline_position TEXT DEFAULT '' NOT NULL,home_timeline_item_date INTEGER DEFAULT 0 NOT NULL,home_timeline_date INTEGER DEFAULT 0 NOT NULL,favorites_timeline_position TEXT DEFAULT '' NOT NULL,favorites_timeline_item_date INTEGER DEFAULT 0 NOT NULL,favorites_timeline_date INTEGER DEFAULT 0 NOT NULL,direct_timeline_position TEXT DEFAULT '' NOT NULL,direct_timeline_item_date INTEGER DEFAULT 0 NOT NULL,direct_timeline_date INTEGER DEFAULT 0 NOT NULL,mentions_timeline_position TEXT DEFAULT '' NOT NULL,mentions_timeline_item_date INTEGER DEFAULT 0 NOT NULL,mentions_timeline_date INTEGER DEFAULT 0 NOT NULL,user_timeline_position TEXT DEFAULT '' NOT NULL,user_timeline_item_date INTEGER DEFAULT 0 NOT NULL,user_timeline_date INTEGER DEFAULT 0 NOT NULL,following_user_date INTEGER DEFAULT 0 NOT NULL,followers_user_date INTEGER DEFAULT 0 NOT NULL,user_msg_id INTEGER DEFAULT 0 NOT NULL,user_msg_date INTEGER DEFAULT 0 NOT NULL)";
        DbUtils.execSQL(db, sql);
        sql = "INSERT INTO newuser (" +
                " _id, origin_id, user_oid, username, webfinger_id, real_name, user_description, location," +
                " profile_url, homepage, avatar_url, banner_url," +
                " msg_count, favorited_count, following_count, followers_count," +
                " user_created_date, user_updated_date, user_ins_date," +
                " home_timeline_position, home_timeline_item_date, home_timeline_date, favorites_timeline_position, favorites_timeline_item_date, favorites_timeline_date, direct_timeline_position, direct_timeline_item_date, direct_timeline_date, mentions_timeline_position, mentions_timeline_item_date, mentions_timeline_date, user_timeline_position, user_timeline_item_date, user_timeline_date," +
                " following_user_date, followers_user_date, user_msg_id, user_msg_date" +
                ") SELECT " +
                " _id, origin_id, user_oid, username, webfinger_id, real_name, user_description, NULL," +
                " url,         homepage, avatar_url, NULL," +
                "         0,               0,               0,               0," +
                " user_created_date,                 0, user_ins_date," +
                " home_timeline_position, home_timeline_item_date, home_timeline_date, favorites_timeline_position, favorites_timeline_item_date, favorites_timeline_date, direct_timeline_position, direct_timeline_item_date, direct_timeline_date, mentions_timeline_position, mentions_timeline_item_date, mentions_timeline_date, user_timeline_position, user_timeline_item_date, user_timeline_date," +
                " following_user_date,                   0, user_msg_id, user_msg_date" +
                " FROM user";
        DbUtils.execSQL(db, sql);

        sql = "DROP INDEX idx_user_origin";
        DbUtils.execSQL(db, sql);
        sql = "DROP TABLE user";
        DbUtils.execSQL(db, sql);

        sql = "ALTER TABLE newuser RENAME TO user";
        DbUtils.execSQL(db, sql);
        try {
            sql = "CREATE UNIQUE INDEX idx_user_origin ON user (origin_id, user_oid)";
            DbUtils.execSQL(db, sql);
        } catch (Exception e) {
            MyLog.i(this, "Couldn't create unique constraint", e);
            sql = "CREATE INDEX idx_user_origin ON user (origin_id, user_oid)";
            DbUtils.execSQL(db, sql);
        }
    }
}
