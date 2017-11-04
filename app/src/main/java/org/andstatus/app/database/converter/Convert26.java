/*
 * Copyright (c) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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

class Convert26 extends ConvertOneStep {
    Convert26() {
        versionTo = 27;
    }

    @Override
    protected void execute2() {

        // Table creation statements for v.27
        sql = "CREATE TABLE msg (_id INTEGER PRIMARY KEY AUTOINCREMENT,origin_id INTEGER NOT NULL,msg_oid TEXT,msg_status INTEGER NOT NULL DEFAULT 0,conversation_id INTEGER,conversation_oid TEXT,url TEXT,body TEXT,body_to_search TEXT,via TEXT,in_reply_to_msg_id INTEGER,msg_updated_date INTEGER,msg_ins_date INTEGER NOT NULL,private INTEGER NOT NULL DEFAULT 0,favorited INTEGER NOT NULL DEFAULT 0,reblogged INTEGER NOT NULL DEFAULT 0,mentioned INTEGER NOT NULL DEFAULT 0,favorite_count INTEGER NOT NULL DEFAULT 0,reblog_count INTEGER NOT NULL DEFAULT 0,reply_count INTEGER NOT NULL DEFAULT 0,msg_author_id INTEGER,in_reply_to_user_id INTEGER)";
        sql = "CREATE UNIQUE INDEX idx_msg_origin ON msg (origin_id, msg_oid)";
        sql = "CREATE INDEX idx_msg_in_reply_to_msg_id ON msg (in_reply_to_msg_id) WHERE in_reply_to_msg_id IS NOT NULL";
        sql = "CREATE INDEX idx_msg_conversation_id ON msg (conversation_id) WHERE conversation_id IS NOT NULL";
        sql = "CREATE TABLE audience (user_id INTEGER NOT NULL,msg_id INTEGER NOT NULL, CONSTRAINT pk_audience PRIMARY KEY (msg_id ASC, user_id ASC))";
        sql = "CREATE INDEX idx_audience_user ON audience (user_id)";
        sql = "CREATE TABLE user (_id INTEGER PRIMARY KEY AUTOINCREMENT,origin_id INTEGER NOT NULL,user_oid TEXT,username TEXT NOT NULL,webfinger_id TEXT NOT NULL,real_name TEXT,user_description TEXT,location TEXT,profile_url TEXT,homepage TEXT,avatar_url TEXT,banner_url TEXT,msg_count INTEGER DEFAULT 0 NOT NULL,favorited_count INTEGER DEFAULT 0 NOT NULL,following_count INTEGER DEFAULT 0 NOT NULL,followers_count INTEGER DEFAULT 0 NOT NULL,user_created_date INTEGER DEFAULT 0 NOT NULL,user_updated_date INTEGER DEFAULT 0 NOT NULL,user_ins_date INTEGER NOT NULL,user_activity_id INTEGER DEFAULT 0 NOT NULL,user_activity_date INTEGER DEFAULT 0 NOT NULL)";
        sql = "CREATE UNIQUE INDEX idx_user_origin ON user (origin_id, user_oid)";
        sql = "CREATE TABLE friendship (user_id INTEGER NOT NULL,friend_id INTEGER NOT NULL,followed BOOLEAN DEFAULT 1 NOT NULL, CONSTRAINT pk_friendship PRIMARY KEY (user_id ASC, friend_id ASC))";
        sql = "CREATE TABLE download (_id INTEGER PRIMARY KEY AUTOINCREMENT,download_type INTEGER NOT NULL,user_id INTEGER,msg_id INTEGER,content_type INTEGER NOT NULL,valid_from INTEGER NOT NULL,url TEXT NOT NULL,loaded_date INTEGER,download_status INTEGER NOT NULL DEFAULT 0,file_name TEXT)";
        sql = "CREATE INDEX idx_download_user ON download (user_id, download_status)";
        sql = "CREATE INDEX idx_download_msg ON download (msg_id, content_type, download_status)";
        sql = "CREATE TABLE origin (_id INTEGER PRIMARY KEY AUTOINCREMENT,origin_type_id INTEGER NOT NULL,origin_name TEXT NOT NULL,origin_url TEXT NOT NULL,ssl BOOLEAN DEFAULT 1 NOT NULL,ssl_mode INTEGER DEFAULT 1 NOT NULL,allow_html BOOLEAN DEFAULT 1 NOT NULL,text_limit INTEGER NOT NULL,short_url_length INTEGER NOT NULL DEFAULT 0,mention_as_webfinger_id INTEGER DEFAULT 3 NOT NULL,use_legacy_http INTEGER DEFAULT 3 NOT NULL,in_combined_global_search BOOLEAN DEFAULT 1 NOT NULL,in_combined_public_reload BOOLEAN DEFAULT 1 NOT NULL)";
        sql = "CREATE UNIQUE INDEX idx_origin_name ON origin (origin_name)";
        sql = "CREATE TABLE timeline (_id INTEGER PRIMARY KEY AUTOINCREMENT,timeline_type STRING NOT NULL,account_id INTEGER,user_id INTEGER,user_in_timeline TEXT,origin_id INTEGER,search_query TEXT,is_synced_automatically BOOLEAN DEFAULT 0 NOT NULL,displayed_in_selector INTEGER DEFAULT 0 NOT NULL,selector_order INTEGER DEFAULT 0 NOT NULL,sync_succeeded_date INTEGER,sync_failed_date INTEGER,error_message TEXT,synced_times_count INTEGER DEFAULT 0 NOT NULL,sync_failed_times_count INTEGER DEFAULT 0 NOT NULL,downloaded_items_count INTEGER DEFAULT 0 NOT NULL,new_items_count INTEGER DEFAULT 0 NOT NULL,count_since INTEGER,synced_times_count_total INTEGER DEFAULT 0 NOT NULL,sync_failed_times_count_total INTEGER DEFAULT 0 NOT NULL,downloaded_items_count_total INTEGER DEFAULT 0 NOT NULL,new_items_count_total INTEGER DEFAULT 0 NOT NULL,youngest_position TEXT,youngest_item_date INTEGER,youngest_synced_date INTEGER,oldest_position TEXT,oldest_item_date INTEGER,oldest_synced_date INTEGER,visible_item_id INTEGER,visible_y INTEGER,visible_oldest_date INTEGER)";
        sql = "CREATE TABLE command (_id INTEGER PRIMARY KEY NOT NULL,queue_type TEXT NOT NULL,command_code TEXT NOT NULL,command_created_date INTEGER NOT NULL,command_description TEXT,in_foreground BOOLEAN DEFAULT 0 NOT NULL,manually_launched BOOLEAN DEFAULT 0 NOT NULL,timeline_id INTEGER,timeline_type STRING,account_id INTEGER,user_id INTEGER,origin_id INTEGER,search_query TEXT,item_id INTEGER,username TEXT,last_executed_date INTEGER,execution_count INTEGER DEFAULT 0 NOT NULL,retries_left INTEGER DEFAULT 0 NOT NULL,num_auth_exceptions INTEGER DEFAULT 0 NOT NULL,num_io_exceptions INTEGER DEFAULT 0 NOT NULL,num_parse_exceptions INTEGER DEFAULT 0 NOT NULL,error_message TEXT,downloaded_count INTEGER DEFAULT 0 NOT NULL,progress_text TEXT)";
        sql = "CREATE TABLE activity (_id INTEGER PRIMARY KEY AUTOINCREMENT,activity_origin_id INTEGER NOT NULL,activity_oid TEXT NOT NULL,account_id INTEGER NOT NULL,activity_type INTEGER NOT NULL,actor_id INTEGER,activity_msg_id INTEGER,activity_user_id INTEGER,obj_activity_id INTEGER,subscribed INTEGER NOT NULL DEFAULT 0,notified INTEGER NOT NULL DEFAULT 0,activity_updated_date INTEGER,activity_ins_date INTEGER NOT NULL)";
        sql = "CREATE UNIQUE INDEX idx_activity_origin ON activity (activity_origin_id, activity_oid)";
        sql = "CREATE INDEX idx_activity_message ON activity (activity_msg_id) WHERE activity_msg_id IS NOT NULL";
        sql = "CREATE INDEX idx_activity_user ON activity (activity_user_id) WHERE activity_user_id IS NOT NULL";
        sql = "CREATE INDEX idx_activity_activity ON activity (obj_activity_id) WHERE obj_activity_id IS NOT NULL";
        sql = "CREATE INDEX idx_activity_ins_date ON activity (activity_ins_date)";

        progressLogger.logProgress(stepTitle + ": Preparing to convert messages");
        sql = "UPDATE msg SET msg_oid='andstatustemp:convert26-' || _id WHERE msg_oid IS NULL";
        DbUtils.execSQL(db, sql);
        sql = "UPDATE msg SET author_id=0 WHERE author_id IS NULL";
        DbUtils.execSQL(db, sql);

        sql = "DROP INDEX idx_msg_origin";
        DbUtils.execSQL(db, sql);
        sql = "DROP INDEX idx_msg_sent_date";
        DbUtils.execSQL(db, sql);
        sql = "DROP INDEX idx_msg_in_reply_to_msg_id";
        DbUtils.execSQL(db, sql);
        sql = "DROP INDEX idx_msg_conversation_id";
        DbUtils.execSQL(db, sql);
        sql = "ALTER TABLE msg RENAME TO oldmsg";
        DbUtils.execSQL(db, sql);

        sql = "CREATE TABLE msg (_id INTEGER PRIMARY KEY AUTOINCREMENT,origin_id INTEGER NOT NULL,msg_oid TEXT,msg_status INTEGER NOT NULL DEFAULT 0,conversation_id INTEGER,conversation_oid TEXT,url TEXT,body TEXT,body_to_search TEXT,via TEXT,in_reply_to_msg_id INTEGER,msg_updated_date INTEGER,msg_ins_date INTEGER NOT NULL,private INTEGER NOT NULL DEFAULT 0,favorited INTEGER NOT NULL DEFAULT 0,reblogged INTEGER NOT NULL DEFAULT 0,mentioned INTEGER NOT NULL DEFAULT 0,favorite_count INTEGER NOT NULL DEFAULT 0,reblog_count INTEGER NOT NULL DEFAULT 0,reply_count INTEGER NOT NULL DEFAULT 0,msg_author_id INTEGER,in_reply_to_user_id INTEGER)";
        DbUtils.execSQL(db, sql);

        progressLogger.logProgress(stepTitle + ": Converting messages");
        sql = "INSERT INTO msg (" +
                "_id, origin_id, msg_oid, msg_status, conversation_id, conversation_oid, url, body, body_to_search, via, in_reply_to_msg_id," +
                " msg_updated_date, msg_ins_date, private," +
                " msg_author_id, in_reply_to_user_id" +
                ") SELECT" +
                " _id, origin_id, msg_oid, msg_status, conversation_id, conversation_oid, url,  body, body_to_search, via, in_reply_to_msg_id," +
                " msg_created_date, msg_ins_date, CASE public WHEN 1 THEN 1 ELSE 0 END, " +
                " author_id, in_reply_to_user_id" +
                " FROM oldmsg";
        DbUtils.execSQL(db, sql);

        // Msg table:
        // new: favorited, reblogged, mentioned, favorite_count, reblog_count , reply_count,
        // old: sender_id, recipient_id, msg_sent_date

        progressLogger.logProgress(stepTitle + ": Creating activities");
        sql = "CREATE TABLE activity (_id INTEGER PRIMARY KEY AUTOINCREMENT,activity_origin_id INTEGER NOT NULL,activity_oid TEXT NOT NULL,account_id INTEGER NOT NULL,activity_type INTEGER NOT NULL,actor_id INTEGER,activity_msg_id INTEGER,activity_user_id INTEGER,obj_activity_id INTEGER,subscribed INTEGER NOT NULL DEFAULT 0,notified INTEGER NOT NULL DEFAULT 0,activity_updated_date INTEGER,activity_ins_date INTEGER NOT NULL)";
        DbUtils.execSQL(db, sql);

        sql = "CREATE UNIQUE INDEX idx_activity_origin ON activity (activity_origin_id, activity_oid)";
        DbUtils.execSQL(db, sql);
        sql = "CREATE INDEX idx_activity_message ON activity (activity_msg_id) WHERE activity_msg_id IS NOT NULL";
        DbUtils.execSQL(db, sql);
        sql = "CREATE INDEX idx_activity_user ON activity (activity_user_id) WHERE activity_user_id IS NOT NULL";
        DbUtils.execSQL(db, sql);
        sql = "CREATE INDEX idx_activity_activity ON activity (obj_activity_id) WHERE obj_activity_id IS NOT NULL";
        DbUtils.execSQL(db, sql);
        sql = "CREATE INDEX idx_activity_ins_date ON activity (activity_ins_date)";

        progressLogger.logProgress(stepTitle + ": Updating activities for each message");
        sql = "INSERT INTO activity (" +
            "_id, activity_origin_id, activity_oid, account_id, activity_type, actor_id, activity_msg_id," +
            " activity_updated_date, activity_ins_date" +
            ") SELECT" +
            " _id, origin_id, author_id || '-update-' || msg_oid, 0, 6,        author_id, _id," +
            " msg_created_date,      msg_ins_date" +
            " FROM oldmsg";
        DbUtils.execSQL(db, sql);

        progressLogger.logProgress(stepTitle + ": Adding Announce activities, linked to Update activities");
        sql = "INSERT INTO activity (" +
            " activity_origin_id, activity_oid, account_id, activity_type, actor_id, activity_msg_id, obj_activity_id," +
            " activity_updated_date, activity_ins_date" +
            ") SELECT" +
            " origin_id,          reblog_oid,   0,          1,             actor_id, _id,             _id," +
            " msg_sent_date,          msg_sent_date" +
            " FROM oldmsg" +
            " INNER JOIN" +
            " (SELECT user_id AS actor_id, reblog_oid, msg_id FROM msgofuser " +
                "WHERE reblogged=1 AND reblog_oid IS NOT NULL AND actor_id IS NOT NULL) ON msg_id=oldmsg._id";
        DbUtils.execSQL(db, sql);

        progressLogger.logProgress(stepTitle + ": Adding Favourite activities, linked to Update activities");
        sql = "INSERT INTO activity (" +
                " activity_origin_id, activity_oid, account_id, activity_type, actor_id, activity_msg_id, obj_activity_id," +
                " activity_updated_date, activity_ins_date" +
                ") SELECT" +
                " origin_id, actor_id || '-like-' || msg_oid, 0, 5,   actor_id, _id,             _id," +
                " msg_sent_date,          msg_sent_date" +
                " FROM oldmsg" +
                " INNER JOIN" +
                " (SELECT user_id AS actor_id, msg_id FROM msgofuser WHERE favorited=1) ON msg_id=oldmsg._id";
        DbUtils.execSQL(db, sql);

        progressLogger.logProgress(stepTitle + ": Setting Subscribed for Update activities");
        sql = "CREATE INDEX idx_msgofuser_message ON msgofuser (msg_id)";
        DbUtils.execSQL(db, sql);

        sql = "UPDATE activity SET" +
                " subscribed=2" +
                " WHERE activity_type=6 AND EXISTS" +
                " (SELECT user_id FROM msgofuser WHERE msg_id=activity.activity_msg_id AND msgofuser.subscribed=1)";
        DbUtils.execSQL(db, sql);

        progressLogger.logProgress(stepTitle + ": Marking Private messages");
        sql = "UPDATE msg SET" +
                " private=2" +
                " WHERE EXISTS" +
                " (SELECT user_id FROM msgofuser WHERE msg_id=msg._id AND msgofuser.directed=1)";
        DbUtils.execSQL(db, sql);

        // Activity table:
        // To set: account_id, activity_user_id, obj_activity_id, subscribed, notified,

        sql = "CREATE TABLE audience (user_id INTEGER NOT NULL,msg_id INTEGER NOT NULL, CONSTRAINT pk_audience PRIMARY KEY (msg_id ASC, user_id ASC))";
        DbUtils.execSQL(db, sql);

        progressLogger.logProgress(stepTitle + ": Adding Recipients of direct messages");
        sql = "INSERT INTO audience (" +
                "user_id, msg_id" +
                ") SELECT" +
                " recipient_id, _id" +
                " FROM oldmsg" +
                " WHERE recipient_id NOT NULL AND recipient_id != 0";
        DbUtils.execSQL(db, sql);

        progressLogger.logProgress(stepTitle + ": Adding Recipients of replies");
        sql = "INSERT OR IGNORE INTO audience (" +
                "user_id, msg_id" +
                ") SELECT" +
                " in_reply_to_user_id, _id" +
                " FROM oldmsg" +
                " WHERE in_reply_to_user_id NOT NULL AND in_reply_to_user_id != 0";
        DbUtils.execSQL(db, sql);

        progressLogger.logProgress(stepTitle + ": Adding Friendship");
        sql = "CREATE TABLE friendship (user_id INTEGER NOT NULL,friend_id INTEGER NOT NULL,followed BOOLEAN DEFAULT 1 NOT NULL, CONSTRAINT pk_friendship PRIMARY KEY (user_id ASC, friend_id ASC))";
        DbUtils.execSQL(db, sql);

        sql = "INSERT INTO friendship (" +
                "user_id, friend_id, followed" +
                ") SELECT" +
                " user_id, following_user_id, user_followed" +
                " FROM followinguser";
        DbUtils.execSQL(db, sql);

        progressLogger.logProgress(stepTitle + ": Converting User");
        sql = "UPDATE user SET user_created_date=0 WHERE user_created_date IS NULL";
        DbUtils.execSQL(db, sql);
        sql = "UPDATE user SET user_updated_date=0 WHERE user_updated_date IS NULL";
        DbUtils.execSQL(db, sql);

        sql = "DROP INDEX idx_user_origin";
        DbUtils.execSQL(db, sql);
        sql = "ALTER TABLE user RENAME TO olduser";
        DbUtils.execSQL(db, sql);

        sql = "CREATE TABLE user (_id INTEGER PRIMARY KEY AUTOINCREMENT,origin_id INTEGER NOT NULL,user_oid TEXT,username TEXT NOT NULL,webfinger_id TEXT NOT NULL,real_name TEXT,user_description TEXT,location TEXT,profile_url TEXT,homepage TEXT,avatar_url TEXT,banner_url TEXT,msg_count INTEGER DEFAULT 0 NOT NULL,favorited_count INTEGER DEFAULT 0 NOT NULL,following_count INTEGER DEFAULT 0 NOT NULL,followers_count INTEGER DEFAULT 0 NOT NULL,user_created_date INTEGER DEFAULT 0 NOT NULL,user_updated_date INTEGER DEFAULT 0 NOT NULL,user_ins_date INTEGER NOT NULL,user_activity_id INTEGER DEFAULT 0 NOT NULL,user_activity_date INTEGER DEFAULT 0 NOT NULL)";
        DbUtils.execSQL(db, sql);

        sql = "INSERT INTO user (" +
                "_id, origin_id, user_oid, username, webfinger_id, real_name, user_description, location, profile_url," +
                " homepage, avatar_url, banner_url, msg_count, favorited_count, following_count, followers_count," +
                " user_created_date, user_updated_date, user_ins_date, user_activity_id, user_activity_date" +
                ") SELECT" +
                " _id, origin_id, user_oid, username, webfinger_id, real_name, user_description, location, profile_url," +
                " homepage, avatar_url, banner_url, msg_count, favorited_count, following_count, followers_count," +
                " user_created_date, user_updated_date, user_ins_date, user_msg_id, user_msg_date" +
                " FROM olduser";
        DbUtils.execSQL(db, sql);

        progressLogger.logProgress(stepTitle + ": Dropping old tables and indices");
        sql = "DROP INDEX idx_msgofuser_message";
        DbUtils.execSQL(db, sql);

        sql = "DROP TABLE IF EXISTS oldmsg";
        DbUtils.execSQL(db, sql);
        sql = "DROP TABLE IF EXISTS olduser";
        DbUtils.execSQL(db, sql);
        sql = "DROP TABLE IF EXISTS followinguser";
        DbUtils.execSQL(db, sql);
        sql = "DROP TABLE IF EXISTS msgofuser";
        DbUtils.execSQL(db, sql);

        progressLogger.logProgress(stepTitle + ": Creating new indices");
        sql = "CREATE UNIQUE INDEX idx_msg_origin ON msg (origin_id, msg_oid)";
        DbUtils.execSQL(db, sql);
        sql = "CREATE INDEX idx_msg_in_reply_to_msg_id ON msg (in_reply_to_msg_id) WHERE in_reply_to_msg_id IS NOT NULL";
        DbUtils.execSQL(db, sql);
        sql = "CREATE INDEX idx_msg_conversation_id ON msg (conversation_id) WHERE conversation_id IS NOT NULL";
        DbUtils.execSQL(db, sql);
        sql = "CREATE INDEX idx_audience_user ON audience (user_id)";
        DbUtils.execSQL(db, sql);
        sql = "CREATE UNIQUE INDEX idx_user_origin ON user (origin_id, user_oid)";
        DbUtils.execSQL(db, sql);
    }
}
