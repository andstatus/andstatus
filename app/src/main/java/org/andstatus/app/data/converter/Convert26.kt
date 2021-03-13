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
package org.andstatus.app.data.converter

import org.andstatus.app.account.MyAccounts
import org.andstatus.app.data.DbUtils
import org.andstatus.app.data.SqlIds

internal class Convert26 : ConvertOneStep() {
    override fun execute2() {

        // Table creation statements for v.27
        sql = "CREATE TABLE msg (_id INTEGER PRIMARY KEY AUTOINCREMENT,origin_id INTEGER NOT NULL,msg_oid TEXT NOT NULL,msg_status INTEGER NOT NULL DEFAULT 0,conversation_id INTEGER NOT NULL DEFAULT 0,conversation_oid TEXT,url TEXT,body TEXT,body_to_search TEXT,via TEXT,msg_author_id INTEGER NOT NULL DEFAULT 0,in_reply_to_msg_id INTEGER,in_reply_to_user_id INTEGER,private INTEGER NOT NULL DEFAULT 0,favorited INTEGER NOT NULL DEFAULT 0,reblogged INTEGER NOT NULL DEFAULT 0,mentioned INTEGER NOT NULL DEFAULT 0,favorite_count INTEGER NOT NULL DEFAULT 0,reblog_count INTEGER NOT NULL DEFAULT 0,reply_count INTEGER NOT NULL DEFAULT 0,msg_ins_date INTEGER NOT NULL,msg_updated_date INTEGER NOT NULL DEFAULT 0)"
        sql = "CREATE UNIQUE INDEX idx_msg_origin ON msg (origin_id, msg_oid)"
        sql = "CREATE INDEX idx_msg_in_reply_to_msg_id ON msg (in_reply_to_msg_id)"
        sql = "CREATE INDEX idx_msg_conversation_id ON msg (conversation_id)"
        sql = "CREATE INDEX idx_conversation_oid ON msg (origin_id, conversation_oid)"
        sql = "CREATE TABLE audience (user_id INTEGER NOT NULL,msg_id INTEGER NOT NULL, CONSTRAINT pk_audience PRIMARY KEY (msg_id ASC, user_id ASC))"
        sql = "CREATE INDEX idx_audience_user ON audience (user_id)"
        sql = "CREATE INDEX idx_audience_msg ON audience (msg_id)"
        sql = "CREATE TABLE user (_id INTEGER PRIMARY KEY AUTOINCREMENT,origin_id INTEGER NOT NULL,user_oid TEXT NOT NULL,username TEXT NOT NULL,webfinger_id TEXT NOT NULL,real_name TEXT,user_description TEXT,location TEXT,profile_url TEXT,homepage TEXT,avatar_url TEXT,banner_url TEXT,msg_count INTEGER NOT NULL DEFAULT 0,favorited_count INTEGER NOT NULL DEFAULT 0,following_count INTEGER NOT NULL DEFAULT 0,followers_count INTEGER NOT NULL DEFAULT 0,user_created_date INTEGER NOT NULL DEFAULT 0,user_updated_date INTEGER NOT NULL DEFAULT 0,user_ins_date INTEGER NOT NULL,user_activity_id INTEGER NOT NULL DEFAULT 0,user_activity_date INTEGER NOT NULL DEFAULT 0)"
        sql = "CREATE UNIQUE INDEX idx_user_origin ON user (origin_id, user_oid)"
        sql = "CREATE TABLE friendship (user_id INTEGER NOT NULL,friend_id INTEGER NOT NULL,followed BOOLEAN NOT NULL, CONSTRAINT pk_friendship PRIMARY KEY (user_id, friend_id))"
        sql = "CREATE INDEX idx_followers ON friendship (friend_id, user_id)"
        sql = "CREATE TABLE download (_id INTEGER PRIMARY KEY AUTOINCREMENT,download_type INTEGER NOT NULL,user_id INTEGER NOT NULL DEFAULT 0,msg_id INTEGER NOT NULL DEFAULT 0,content_type INTEGER NOT NULL,valid_from INTEGER NOT NULL,url TEXT NOT NULL,loaded_date INTEGER NOT NULL DEFAULT 0,download_status INTEGER NOT NULL DEFAULT 0,file_name TEXT)"
        sql = "CREATE INDEX idx_download_user ON download (user_id, download_status)"
        sql = "CREATE INDEX idx_download_msg ON download (msg_id, content_type, download_status)"
        sql = "CREATE TABLE origin (_id INTEGER PRIMARY KEY AUTOINCREMENT,origin_type_id INTEGER NOT NULL,origin_name TEXT NOT NULL,origin_url TEXT NOT NULL,ssl BOOLEAN NOT NULL DEFAULT 1,ssl_mode INTEGER NOT NULL DEFAULT 1,allow_html BOOLEAN NOT NULL DEFAULT 1,text_limit INTEGER NOT NULL,short_url_length INTEGER NOT NULL DEFAULT 0,mention_as_webfinger_id INTEGER NOT NULL DEFAULT 3,use_legacy_http INTEGER NOT NULL DEFAULT 3,in_combined_global_search BOOLEAN NOT NULL DEFAULT 1,in_combined_public_reload BOOLEAN NOT NULL DEFAULT 1)"
        sql = "CREATE TABLE timeline (_id INTEGER PRIMARY KEY AUTOINCREMENT,timeline_type TEXT NOT NULL,account_id INTEGER NOT NULL DEFAULT 0,user_id INTEGER NOT NULL DEFAULT 0,user_in_timeline TEXT,origin_id INTEGER NOT NULL DEFAULT 0,search_query TEXT,is_synced_automatically BOOLEAN NOT NULL DEFAULT 0,displayed_in_selector INTEGER NOT NULL DEFAULT 0,selector_order INTEGER NOT NULL DEFAULT 0,sync_succeeded_date INTEGER NOT NULL DEFAULT 0,sync_failed_date INTEGER NOT NULL DEFAULT 0,error_message TEXT,synced_times_count INTEGER NOT NULL DEFAULT 0,sync_failed_times_count INTEGER NOT NULL DEFAULT 0,downloaded_items_count INTEGER NOT NULL DEFAULT 0,new_items_count INTEGER NOT NULL DEFAULT 0,count_since INTEGER NOT NULL DEFAULT 0,synced_times_count_total INTEGER NOT NULL DEFAULT 0,sync_failed_times_count_total INTEGER NOT NULL DEFAULT 0,downloaded_items_count_total INTEGER NOT NULL DEFAULT 0,new_items_count_total INTEGER NOT NULL DEFAULT 0,youngest_position TEXT,youngest_item_date INTEGER NOT NULL DEFAULT 0,youngest_synced_date INTEGER NOT NULL DEFAULT 0,oldest_position TEXT,oldest_item_date INTEGER NOT NULL DEFAULT 0,oldest_synced_date INTEGER NOT NULL DEFAULT 0,visible_item_id INTEGER NOT NULL DEFAULT 0,visible_y INTEGER NOT NULL DEFAULT 0,visible_oldest_date INTEGER NOT NULL DEFAULT 0)"
        sql = "CREATE TABLE command (_id INTEGER PRIMARY KEY NOT NULL,queue_type TEXT NOT NULL,command_code TEXT NOT NULL,command_created_date INTEGER NOT NULL,command_description TEXT,in_foreground BOOLEAN NOT NULL DEFAULT 0,manually_launched BOOLEAN NOT NULL DEFAULT 0,timeline_id INTEGER NOT NULL DEFAULT 0,timeline_type TEXT NOT NULL,account_id INTEGER NOT NULL DEFAULT 0,user_id INTEGER NOT NULL DEFAULT 0,origin_id INTEGER NOT NULL DEFAULT 0,search_query TEXT,item_id INTEGER NOT NULL DEFAULT 0,username TEXT,last_executed_date INTEGER NOT NULL DEFAULT 0,execution_count INTEGER NOT NULL DEFAULT 0,retries_left INTEGER NOT NULL DEFAULT 0,num_auth_exceptions INTEGER NOT NULL DEFAULT 0,num_io_exceptions INTEGER NOT NULL DEFAULT 0,num_parse_exceptions INTEGER NOT NULL DEFAULT 0,error_message TEXT,downloaded_count INTEGER NOT NULL DEFAULT 0,progress_text TEXT)"
        sql = "CREATE TABLE activity (_id INTEGER PRIMARY KEY AUTOINCREMENT,activity_origin_id INTEGER NOT NULL,activity_oid TEXT NOT NULL,account_id INTEGER NOT NULL,activity_type INTEGER NOT NULL,actor_id INTEGER NOT NULL,activity_msg_id INTEGER NOT NULL,activity_user_id INTEGER NOT NULL,obj_activity_id INTEGER NOT NULL,subscribed INTEGER NOT NULL DEFAULT 0,notified INTEGER NOT NULL DEFAULT 0,new_notification_event INTEGER NOT NULL DEFAULT 0,activity_ins_date INTEGER NOT NULL,activity_updated_date INTEGER NOT NULL DEFAULT 0)"
        sql = "CREATE UNIQUE INDEX idx_activity_origin ON activity (activity_origin_id, activity_oid)"
        sql = "CREATE INDEX idx_activity_message ON activity (activity_msg_id)"
        sql = "CREATE INDEX idx_activity_user ON activity (activity_user_id)"
        sql = "CREATE INDEX idx_activity_activity ON activity (obj_activity_id)"
        sql = "CREATE INDEX idx_activity_timeline ON activity (activity_updated_date)"
        sql = "CREATE INDEX idx_activity_actor_timeline ON activity (actor_id, activity_updated_date)"
        sql = "CREATE INDEX idx_activity_subscribed_timeline ON activity (subscribed, activity_updated_date)"
        sql = "CREATE INDEX idx_activity_notified_timeline ON activity (notified, activity_updated_date)"
        sql = "CREATE INDEX idx_activity_new_notification ON activity (new_notification_event)"
        progressLogger.logProgress("$stepTitle: Preparing to convert messages")
        sql = "UPDATE msg SET msg_oid='andstatustemp:convert26-' || _id WHERE msg_oid IS NULL"
        DbUtils.execSQL(db, sql)
        sql = "UPDATE msg SET author_id=0 WHERE author_id IS NULL"
        DbUtils.execSQL(db, sql)
        sql = "UPDATE msg SET conversation_id=0 WHERE conversation_id IS NULL"
        DbUtils.execSQL(db, sql)
        sql = "UPDATE msg SET msg_created_date=0 WHERE msg_created_date IS NULL"
        DbUtils.execSQL(db, sql)
        sql = "UPDATE msg SET msg_sent_date=0 WHERE msg_sent_date IS NULL"
        DbUtils.execSQL(db, sql)
        sql = "DROP INDEX idx_msg_origin"
        DbUtils.execSQL(db, sql)
        sql = "DROP INDEX idx_msg_sent_date"
        DbUtils.execSQL(db, sql)
        sql = "DROP INDEX idx_msg_in_reply_to_msg_id"
        DbUtils.execSQL(db, sql)
        sql = "DROP INDEX idx_msg_conversation_id"
        DbUtils.execSQL(db, sql)
        sql = "ALTER TABLE msg RENAME TO oldmsg"
        DbUtils.execSQL(db, sql)
        sql = "CREATE INDEX idx_msgofuser_message ON msgofuser (msg_id)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE TABLE msg (_id INTEGER PRIMARY KEY AUTOINCREMENT,origin_id INTEGER NOT NULL,msg_oid TEXT NOT NULL,msg_status INTEGER NOT NULL DEFAULT 0,conversation_id INTEGER NOT NULL DEFAULT 0,conversation_oid TEXT,url TEXT,body TEXT,body_to_search TEXT,via TEXT,msg_author_id INTEGER NOT NULL DEFAULT 0,in_reply_to_msg_id INTEGER,in_reply_to_user_id INTEGER,private INTEGER NOT NULL DEFAULT 0,favorited INTEGER NOT NULL DEFAULT 0,reblogged INTEGER NOT NULL DEFAULT 0,mentioned INTEGER NOT NULL DEFAULT 0,favorite_count INTEGER NOT NULL DEFAULT 0,reblog_count INTEGER NOT NULL DEFAULT 0,reply_count INTEGER NOT NULL DEFAULT 0,msg_ins_date INTEGER NOT NULL,msg_updated_date INTEGER NOT NULL DEFAULT 0)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE UNIQUE INDEX idx_msg_origin ON msg (origin_id, msg_oid)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE INDEX idx_msg_in_reply_to_msg_id ON msg (in_reply_to_msg_id)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE INDEX idx_msg_conversation_id ON msg (conversation_id)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE INDEX idx_conversation_oid ON msg (origin_id, conversation_oid)"
        DbUtils.execSQL(db, sql)
        progressLogger.logProgress("$stepTitle: Converting messages")
        sql = "INSERT INTO msg (" +
                "_id, origin_id, msg_oid, msg_status, conversation_id, conversation_oid, url, body, body_to_search, via, in_reply_to_msg_id," +
                " msg_updated_date, msg_ins_date, private," +
                " msg_author_id, in_reply_to_user_id" +
                ") SELECT" +
                " _id, origin_id, msg_oid, msg_status, conversation_id, conversation_oid, url,  body, body_to_search, via, in_reply_to_msg_id," +
                " msg_created_date, msg_ins_date, CASE public WHEN 1 THEN 1 ELSE 0 END, " +
                " author_id, in_reply_to_user_id" +
                " FROM oldmsg WHERE author_id !=0"
        DbUtils.execSQL(db, sql)
        progressLogger.logProgress("$stepTitle: Creating activities")
        sql = "CREATE TABLE activity (_id INTEGER PRIMARY KEY AUTOINCREMENT,activity_origin_id INTEGER NOT NULL,activity_oid TEXT NOT NULL,account_id INTEGER NOT NULL,activity_type INTEGER NOT NULL,actor_id INTEGER NOT NULL,activity_msg_id INTEGER NOT NULL,activity_user_id INTEGER NOT NULL,obj_activity_id INTEGER NOT NULL,subscribed INTEGER NOT NULL DEFAULT 0,notified INTEGER NOT NULL DEFAULT 0,new_notification_event INTEGER NOT NULL DEFAULT 0,activity_ins_date INTEGER NOT NULL,activity_updated_date INTEGER NOT NULL DEFAULT 0)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE UNIQUE INDEX idx_activity_origin ON activity (activity_origin_id, activity_oid)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE INDEX idx_activity_message ON activity (activity_msg_id)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE INDEX idx_activity_user ON activity (activity_user_id)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE INDEX idx_activity_activity ON activity (obj_activity_id)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE INDEX idx_activity_timeline ON activity (activity_updated_date)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE INDEX idx_activity_actor_timeline ON activity (actor_id, activity_updated_date)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE INDEX idx_activity_subscribed_timeline ON activity (subscribed, activity_updated_date)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE INDEX idx_activity_notified_timeline ON activity (notified, activity_updated_date)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE INDEX idx_activity_new_notification ON activity (new_notification_event)"
        DbUtils.execSQL(db, sql)
        progressLogger.logProgress("$stepTitle: Adding an Update activity for each message")
        sql = "INSERT INTO activity (" +
                "_id, activity_origin_id, activity_oid, account_id, activity_type, actor_id, activity_msg_id," +
                " activity_user_id, obj_activity_id, activity_updated_date, activity_ins_date" +
                ") SELECT" +
                " _id, origin_id, author_id || '-update-' || msg_oid, 0, 6,        author_id, _id," +
                " 0,                0,               msg_created_date,      msg_ins_date" +
                " FROM oldmsg WHERE author_id !=0"
        DbUtils.execSQL(db, sql)
        progressLogger.logProgress("$stepTitle: Adding Announce activities, linked to Update activities")
        sql = "INSERT INTO activity (" +
                " activity_origin_id, activity_oid, account_id, activity_type, actor_id, activity_msg_id," +
                " activity_user_id,  obj_activity_id, activity_updated_date,  activity_ins_date" +
                ") SELECT" +
                " origin_id,          reblog_oid,   0,          1,             actor_id, _id," +
                " 0,                 _id,             (msg_sent_date + 1000), msg_sent_date" +
                " FROM (SELECT * FROM oldmsg WHERE author_id !=0) AS oldmsg" +
                " INNER JOIN" +
                " (SELECT user_id AS actor_id, reblog_oid, msg_id FROM msgofuser " +
                "WHERE reblogged=1 AND reblog_oid IS NOT NULL AND actor_id IS NOT NULL AND actor_id != 0) ON msg_id=oldmsg._id"
        DbUtils.execSQL(db, sql)
        val myAccountIds: SqlIds = MyAccounts.Companion.myAccountIds()
        progressLogger.logProgress("$stepTitle: Adding Favorite activities, linked to Update activities")
        sql = "INSERT INTO activity (" +
                " activity_origin_id, activity_oid, account_id, activity_type, actor_id, activity_msg_id," +
                " activity_user_id,  obj_activity_id, activity_updated_date,  activity_ins_date" +
                ") SELECT" +
                " origin_id, actor_id || '-like-' || msg_oid, 0, 5,            actor_id, _id," +
                " 0,                 _id,             (msg_sent_date + 1000), msg_sent_date" +
                " FROM (SELECT * FROM oldmsg WHERE author_id !=0) AS oldmsg" +
                " INNER JOIN" +
                " (SELECT user_id AS actor_id, msg_id FROM msgofuser WHERE favorited=1 AND actor_id IS NOT NULL " +
                "AND actor_id != 0) ON msg_id=oldmsg._id"
        DbUtils.execSQL(db, sql)
        progressLogger.logProgress("$stepTitle: Setting Favorited flag for messages")
        sql = "UPDATE msg SET" +
                " favorited=2" +
                " WHERE EXISTS " +
                " (SELECT user_id AS actor_id, msg_id FROM msgofuser WHERE favorited=1 AND actor_id IS NOT NULL" +
                " AND actor_id" + myAccountIds.getSql() + " AND msg_id=msg._id)"
        DbUtils.execSQL(db, sql)
        progressLogger.logProgress("$stepTitle: Updating account for Favorites")
        sql = "UPDATE activity SET" +
                " subscribed=2," +
                " account_id=(" +
                "SELECT user_id FROM msgofuser WHERE msg_id=activity.activity_msg_id" +
                " AND msgofuser.favorited=1 AND user_id" + myAccountIds.getSql() +
                ")" +
                " WHERE activity_type=5 AND EXISTS" +
                " (SELECT user_id FROM msgofuser WHERE msg_id=activity.activity_msg_id AND msgofuser.favorited=1" +
                " AND user_id" + myAccountIds.getSql() + ")"
        DbUtils.execSQL(db, sql)
        progressLogger.logProgress("$stepTitle: Setting Subscribed for Update activities")
        sql = "UPDATE activity SET" +
                " subscribed=2," +
                " account_id=(" +
                "SELECT user_id FROM msgofuser WHERE msg_id=activity.activity_msg_id" +
                " AND msgofuser.subscribed=1 AND user_id" + myAccountIds.getSql() +
                ")" +
                " WHERE activity_type=6 AND EXISTS" +
                " (SELECT user_id FROM msgofuser WHERE msg_id=activity.activity_msg_id AND msgofuser.subscribed=1" +
                " AND user_id" + myAccountIds.getSql() + ")"
        DbUtils.execSQL(db, sql)
        progressLogger.logProgress("$stepTitle: Marking Private messages")
        sql = "UPDATE msg SET" +
                " private=2" +
                " WHERE EXISTS" +
                " (SELECT user_id FROM msgofuser WHERE msg_id=msg._id AND msgofuser.directed=1)"
        DbUtils.execSQL(db, sql)
        progressLogger.logProgress("$stepTitle: Updating account for Private messages")
        sql = "UPDATE activity SET" +
                " notified=2," +
                " subscribed=2," +
                " account_id=(" +
                "SELECT user_id FROM msgofuser WHERE msg_id=activity.activity_msg_id" +
                " AND msgofuser.directed=1 AND user_id" + myAccountIds.getSql() +
                ")" +
                " WHERE activity_type=6 AND EXISTS" +
                " (SELECT user_id FROM msgofuser WHERE msg_id=activity.activity_msg_id AND msgofuser.directed=1" +
                " AND user_id" + myAccountIds.getSql() + ")"
        DbUtils.execSQL(db, sql)
        progressLogger.logProgress("$stepTitle: Marking Mentions")
        sql = "UPDATE msg SET" +
                " mentioned=2" +
                " WHERE EXISTS" +
                " (SELECT user_id FROM msgofuser WHERE msg_id=msg._id AND msgofuser.mentioned=1 " +
                " AND user_id" + myAccountIds.getSql() + ")"
        DbUtils.execSQL(db, sql)
        progressLogger.logProgress("$stepTitle: Updating account for Mentions")
        sql = "UPDATE activity SET" +
                " notified=2," +
                " subscribed=2," +
                " account_id=(" +
                "SELECT user_id FROM msgofuser WHERE msg_id=activity.activity_msg_id" +
                " AND msgofuser.mentioned=1 AND user_id" + myAccountIds.getSql() +
                ")" +
                " WHERE activity_type=6 AND EXISTS" +
                " (SELECT user_id FROM msgofuser WHERE msg_id=activity.activity_msg_id AND msgofuser.mentioned=1" +
                " AND user_id" + myAccountIds.getSql() + ")"
        DbUtils.execSQL(db, sql)
        sql = "CREATE TABLE audience (user_id INTEGER NOT NULL,msg_id INTEGER NOT NULL, CONSTRAINT pk_audience PRIMARY KEY (msg_id ASC, user_id ASC))"
        DbUtils.execSQL(db, sql)
        sql = "CREATE INDEX idx_audience_user ON audience (user_id)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE INDEX idx_audience_msg ON audience (msg_id)"
        DbUtils.execSQL(db, sql)
        progressLogger.logProgress("$stepTitle: Adding Recipients of private messages")
        sql = "INSERT INTO audience (" +
                "user_id, msg_id" +
                ") SELECT" +
                " recipient_id, _id" +
                " FROM oldmsg" +
                " WHERE recipient_id NOT NULL AND recipient_id != 0"
        DbUtils.execSQL(db, sql)
        progressLogger.logProgress("$stepTitle: Adding Recipients of replies")
        sql = "INSERT OR IGNORE INTO audience (" +
                "user_id, msg_id" +
                ") SELECT" +
                " in_reply_to_user_id, _id" +
                " FROM oldmsg" +
                " WHERE in_reply_to_user_id NOT NULL AND in_reply_to_user_id != 0"
        DbUtils.execSQL(db, sql)
        progressLogger.logProgress("$stepTitle: Converting Origin")
        sql = "DROP INDEX IF EXISTS idx_origin_name"
        DbUtils.execSQL(db, sql)
        sql = "ALTER TABLE origin RENAME TO oldorigin"
        DbUtils.execSQL(db, sql)
        sql = "CREATE TABLE origin (_id INTEGER PRIMARY KEY AUTOINCREMENT,origin_type_id INTEGER NOT NULL,origin_name TEXT NOT NULL,origin_url TEXT NOT NULL,ssl BOOLEAN NOT NULL DEFAULT 1,ssl_mode INTEGER NOT NULL DEFAULT 1,allow_html BOOLEAN NOT NULL DEFAULT 1,text_limit INTEGER NOT NULL,short_url_length INTEGER NOT NULL DEFAULT 0,mention_as_webfinger_id INTEGER NOT NULL DEFAULT 3,use_legacy_http INTEGER NOT NULL DEFAULT 3,in_combined_global_search BOOLEAN NOT NULL DEFAULT 1,in_combined_public_reload BOOLEAN NOT NULL DEFAULT 1)"
        DbUtils.execSQL(db, sql)
        sql = "INSERT INTO origin (" +
                " _id, origin_type_id, origin_name, origin_url, ssl, ssl_mode, allow_html, text_limit, short_url_length, mention_as_webfinger_id, use_legacy_http, in_combined_global_search, in_combined_public_reload" +
                ") SELECT" +
                " _id, origin_type_id, origin_name, origin_url, ssl, ssl_mode, allow_html, text_limit, short_url_length, mention_as_webfinger_id, use_legacy_http, in_combined_global_search, in_combined_public_reload" +
                " FROM oldorigin"
        DbUtils.execSQL(db, sql)
        progressLogger.logProgress("$stepTitle: Adding Friendship")
        sql = "CREATE TABLE friendship (user_id INTEGER NOT NULL,friend_id INTEGER NOT NULL,followed BOOLEAN NOT NULL, CONSTRAINT pk_friendship PRIMARY KEY (user_id, friend_id))"
        DbUtils.execSQL(db, sql)
        sql = "CREATE INDEX idx_followers ON friendship (friend_id, user_id)"
        DbUtils.execSQL(db, sql)
        sql = "INSERT INTO friendship (" +
                "user_id, friend_id, followed" +
                ") SELECT" +
                " user_id, following_user_id, user_followed" +
                " FROM followinguser WHERE user_id NOT NULL AND following_user_id NOT NULL AND user_id != 0 AND following_user_id !=0"
        DbUtils.execSQL(db, sql)
        progressLogger.logProgress("$stepTitle: Converting Download")
        sql = "UPDATE download SET user_id=0 WHERE user_id IS NULL"
        DbUtils.execSQL(db, sql)
        sql = "UPDATE download SET msg_id=0 WHERE msg_id IS NULL"
        DbUtils.execSQL(db, sql)
        sql = "UPDATE download SET loaded_date=0 WHERE loaded_date IS NULL"
        DbUtils.execSQL(db, sql)
        sql = "DROP INDEX IF EXISTS idx_download_user"
        DbUtils.execSQL(db, sql)
        sql = "DROP INDEX IF EXISTS idx_download_msg"
        DbUtils.execSQL(db, sql)
        sql = "ALTER TABLE download RENAME TO olddownload"
        DbUtils.execSQL(db, sql)
        sql = "CREATE TABLE download (_id INTEGER PRIMARY KEY AUTOINCREMENT,download_type INTEGER NOT NULL,user_id INTEGER NOT NULL DEFAULT 0,msg_id INTEGER NOT NULL DEFAULT 0,content_type INTEGER NOT NULL,valid_from INTEGER NOT NULL,url TEXT NOT NULL,loaded_date INTEGER NOT NULL DEFAULT 0,download_status INTEGER NOT NULL DEFAULT 0,file_name TEXT)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE INDEX idx_download_user ON download (user_id, download_status)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE INDEX idx_download_msg ON download (msg_id, content_type, download_status)"
        DbUtils.execSQL(db, sql)
        sql = "INSERT INTO download (" +
                " _id, download_type, user_id, msg_id, content_type, valid_from, url, loaded_date, download_status, file_name" +
                ") SELECT" +
                " _id, download_type, user_id, msg_id, content_type, valid_from, url, loaded_date, download_status, file_name" +
                " FROM olddownload"
        DbUtils.execSQL(db, sql)
        progressLogger.logProgress("$stepTitle: Converting User")
        sql = "UPDATE user SET user_created_date=0 WHERE user_created_date IS NULL"
        DbUtils.execSQL(db, sql)
        sql = "UPDATE user SET user_updated_date=0 WHERE user_updated_date IS NULL"
        DbUtils.execSQL(db, sql)
        sql = "DROP INDEX IF EXISTS idx_user_origin"
        DbUtils.execSQL(db, sql)
        sql = "ALTER TABLE user RENAME TO olduser"
        DbUtils.execSQL(db, sql)
        sql = "CREATE TABLE user (_id INTEGER PRIMARY KEY AUTOINCREMENT,origin_id INTEGER NOT NULL,user_oid TEXT NOT NULL,username TEXT NOT NULL,webfinger_id TEXT NOT NULL,real_name TEXT,user_description TEXT,location TEXT,profile_url TEXT,homepage TEXT,avatar_url TEXT,banner_url TEXT,msg_count INTEGER NOT NULL DEFAULT 0,favorited_count INTEGER NOT NULL DEFAULT 0,following_count INTEGER NOT NULL DEFAULT 0,followers_count INTEGER NOT NULL DEFAULT 0,user_created_date INTEGER NOT NULL DEFAULT 0,user_updated_date INTEGER NOT NULL DEFAULT 0,user_ins_date INTEGER NOT NULL,user_activity_id INTEGER NOT NULL DEFAULT 0,user_activity_date INTEGER NOT NULL DEFAULT 0)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE UNIQUE INDEX idx_user_origin ON user (origin_id, user_oid)"
        DbUtils.execSQL(db, sql)
        sql = "INSERT INTO user (" +
                "_id, origin_id, user_oid, username, webfinger_id, real_name, user_description, location, profile_url," +
                " homepage, avatar_url, banner_url, msg_count, favorited_count, following_count, followers_count," +
                " user_created_date, user_updated_date, user_ins_date, user_activity_id, user_activity_date" +
                ") SELECT" +
                " _id, origin_id, user_oid, username, webfinger_id, real_name, user_description, location, profile_url," +
                " homepage, avatar_url, banner_url, msg_count, favorited_count, following_count, followers_count," +
                " user_created_date, user_updated_date, user_ins_date, user_msg_id, user_msg_date" +
                " FROM olduser"
        DbUtils.execSQL(db, sql)
        progressLogger.logProgress("$stepTitle: Converting Timeline")
        sql = "UPDATE timeline SET account_id=0 WHERE account_id IS NULL"
        DbUtils.execSQL(db, sql)
        sql = "UPDATE timeline SET user_id=0 WHERE user_id IS NULL"
        DbUtils.execSQL(db, sql)
        sql = "UPDATE timeline SET origin_id=0 WHERE origin_id IS NULL"
        DbUtils.execSQL(db, sql)
        sql = "UPDATE timeline SET sync_succeeded_date=0 WHERE sync_succeeded_date IS NULL"
        DbUtils.execSQL(db, sql)
        sql = "UPDATE timeline SET sync_failed_date=0 WHERE sync_failed_date IS NULL"
        DbUtils.execSQL(db, sql)
        sql = "UPDATE timeline SET count_since=0 WHERE count_since IS NULL"
        DbUtils.execSQL(db, sql)
        sql = "UPDATE timeline SET youngest_item_date=0 WHERE youngest_item_date IS NULL"
        DbUtils.execSQL(db, sql)
        sql = "UPDATE timeline SET youngest_synced_date=0 WHERE youngest_synced_date IS NULL"
        DbUtils.execSQL(db, sql)
        sql = "UPDATE timeline SET oldest_item_date=0 WHERE oldest_item_date IS NULL"
        DbUtils.execSQL(db, sql)
        sql = "UPDATE timeline SET oldest_synced_date=0 WHERE oldest_synced_date IS NULL"
        DbUtils.execSQL(db, sql)
        sql = "UPDATE timeline SET visible_item_id=0 WHERE visible_item_id IS NULL"
        DbUtils.execSQL(db, sql)
        sql = "UPDATE timeline SET visible_y=0 WHERE visible_y IS NULL"
        DbUtils.execSQL(db, sql)
        sql = "UPDATE timeline SET visible_oldest_date=0 WHERE visible_oldest_date IS NULL"
        DbUtils.execSQL(db, sql)
        sql = "ALTER TABLE timeline RENAME TO oldtimeline"
        DbUtils.execSQL(db, sql)
        sql = "CREATE TABLE timeline (_id INTEGER PRIMARY KEY AUTOINCREMENT,timeline_type TEXT NOT NULL,account_id INTEGER NOT NULL DEFAULT 0,user_id INTEGER NOT NULL DEFAULT 0,user_in_timeline TEXT,origin_id INTEGER NOT NULL DEFAULT 0,search_query TEXT,is_synced_automatically BOOLEAN NOT NULL DEFAULT 0,displayed_in_selector INTEGER NOT NULL DEFAULT 0,selector_order INTEGER NOT NULL DEFAULT 0,sync_succeeded_date INTEGER NOT NULL DEFAULT 0,sync_failed_date INTEGER NOT NULL DEFAULT 0,error_message TEXT,synced_times_count INTEGER NOT NULL DEFAULT 0,sync_failed_times_count INTEGER NOT NULL DEFAULT 0,downloaded_items_count INTEGER NOT NULL DEFAULT 0,new_items_count INTEGER NOT NULL DEFAULT 0,count_since INTEGER NOT NULL DEFAULT 0,synced_times_count_total INTEGER NOT NULL DEFAULT 0,sync_failed_times_count_total INTEGER NOT NULL DEFAULT 0,downloaded_items_count_total INTEGER NOT NULL DEFAULT 0,new_items_count_total INTEGER NOT NULL DEFAULT 0,youngest_position TEXT,youngest_item_date INTEGER NOT NULL DEFAULT 0,youngest_synced_date INTEGER NOT NULL DEFAULT 0,oldest_position TEXT,oldest_item_date INTEGER NOT NULL DEFAULT 0,oldest_synced_date INTEGER NOT NULL DEFAULT 0,visible_item_id INTEGER NOT NULL DEFAULT 0,visible_y INTEGER NOT NULL DEFAULT 0,visible_oldest_date INTEGER NOT NULL DEFAULT 0)"
        DbUtils.execSQL(db, sql)
        sql = "INSERT INTO timeline (" +
                " _id, timeline_type, account_id, user_id, user_in_timeline, origin_id, search_query, " +
                " is_synced_automatically, displayed_in_selector, selector_order, sync_succeeded_date, " +
                " sync_failed_date, error_message, synced_times_count, sync_failed_times_count, downloaded_items_count," +
                " new_items_count, count_since, synced_times_count_total, sync_failed_times_count_total," +
                " downloaded_items_count_total, new_items_count_total, youngest_position, youngest_item_date," +
                " youngest_synced_date, oldest_position, oldest_item_date, oldest_synced_date," +
                " visible_item_id, visible_y, visible_oldest_date" +
                ") SELECT" +
                " _id, timeline_type, account_id, user_id, user_in_timeline, origin_id, search_query, " +
                " is_synced_automatically, displayed_in_selector, selector_order, sync_succeeded_date, " +
                " sync_failed_date, error_message, synced_times_count, sync_failed_times_count, downloaded_items_count," +
                " new_items_count, count_since, synced_times_count_total, sync_failed_times_count_total," +
                " downloaded_items_count_total, new_items_count_total, youngest_position, youngest_item_date," +
                " youngest_synced_date, oldest_position, oldest_item_date, oldest_synced_date," +
                " visible_item_id, visible_y, visible_oldest_date" +
                " FROM oldtimeline"
        DbUtils.execSQL(db, sql)
        sql = "UPDATE timeline SET timeline_type='private' WHERE timeline_type='direct'"
        DbUtils.execSQL(db, sql)
        progressLogger.logProgress("$stepTitle: Converting Command")
        sql = "UPDATE command SET timeline_id=0 WHERE timeline_id IS NULL"
        DbUtils.execSQL(db, sql)
        sql = "UPDATE command SET account_id=0 WHERE account_id IS NULL"
        DbUtils.execSQL(db, sql)
        sql = "UPDATE command SET user_id=0 WHERE user_id IS NULL"
        DbUtils.execSQL(db, sql)
        sql = "UPDATE command SET origin_id=0 WHERE origin_id IS NULL"
        DbUtils.execSQL(db, sql)
        sql = "UPDATE command SET item_id=0 WHERE item_id IS NULL"
        DbUtils.execSQL(db, sql)
        sql = "UPDATE command SET last_executed_date=0 WHERE last_executed_date IS NULL"
        DbUtils.execSQL(db, sql)
        sql = "ALTER TABLE command RENAME TO oldcommand"
        DbUtils.execSQL(db, sql)
        sql = "CREATE TABLE command (_id INTEGER PRIMARY KEY NOT NULL,queue_type TEXT NOT NULL,command_code TEXT NOT NULL,command_created_date INTEGER NOT NULL,command_description TEXT,in_foreground BOOLEAN NOT NULL DEFAULT 0,manually_launched BOOLEAN NOT NULL DEFAULT 0,timeline_id INTEGER NOT NULL DEFAULT 0,timeline_type TEXT NOT NULL,account_id INTEGER NOT NULL DEFAULT 0,user_id INTEGER NOT NULL DEFAULT 0,origin_id INTEGER NOT NULL DEFAULT 0,search_query TEXT,item_id INTEGER NOT NULL DEFAULT 0,username TEXT,last_executed_date INTEGER NOT NULL DEFAULT 0,execution_count INTEGER NOT NULL DEFAULT 0,retries_left INTEGER NOT NULL DEFAULT 0,num_auth_exceptions INTEGER NOT NULL DEFAULT 0,num_io_exceptions INTEGER NOT NULL DEFAULT 0,num_parse_exceptions INTEGER NOT NULL DEFAULT 0,error_message TEXT,downloaded_count INTEGER NOT NULL DEFAULT 0,progress_text TEXT)"
        DbUtils.execSQL(db, sql)
        sql = "INSERT INTO command (" +
                " _id, queue_type, command_code, command_created_date, command_description, in_foreground," +
                " manually_launched, timeline_id, timeline_type, account_id, user_id, origin_id, search_query," +
                " item_id, username, last_executed_date, execution_count, retries_left, num_auth_exceptions," +
                " num_io_exceptions, num_parse_exceptions, error_message, downloaded_count, progress_text" +
                ") SELECT" +
                " _id, queue_type, command_code, command_created_date, command_description, in_foreground," +
                " manually_launched, timeline_id, timeline_type, account_id, user_id, origin_id, search_query," +
                " item_id, username, last_executed_date, execution_count, retries_left, num_auth_exceptions," +
                " num_io_exceptions, num_parse_exceptions, error_message, downloaded_count, progress_text" +
                " FROM oldcommand"
        DbUtils.execSQL(db, sql)
        progressLogger.logProgress("$stepTitle: Dropping old tables and indices")
        sql = "DROP INDEX idx_msgofuser_message"
        DbUtils.execSQL(db, sql)
        dropOldTable("oldtimeline")
        dropOldTable("oldcommand")
        dropOldTable("oldmsg")
        dropOldTable("olduser")
        dropOldTable("followinguser")
        dropOldTable("msgofuser")
        dropOldTable("oldorigin")
        dropOldTable("olddownload")
    }

    init {
        versionTo = 27
    }
}