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

package org.andstatus.app.data.converter;

import org.andstatus.app.data.DbUtils;

class Convert27 extends ConvertOneStep {
    Convert27() {
        versionTo = 37;
    }

    @Override
    protected void execute2() {

        // Table creation statements for v.37
        sql = "CREATE TABLE origin (_id INTEGER PRIMARY KEY AUTOINCREMENT,origin_type_id INTEGER NOT NULL,origin_name TEXT NOT NULL,origin_url TEXT NOT NULL,ssl BOOLEAN NOT NULL DEFAULT 1,ssl_mode INTEGER NOT NULL DEFAULT 1,allow_html BOOLEAN NOT NULL DEFAULT 1,text_limit INTEGER NOT NULL,short_url_length INTEGER NOT NULL DEFAULT 0,mention_as_webfinger_id INTEGER NOT NULL DEFAULT 3,use_legacy_http INTEGER NOT NULL DEFAULT 3,in_combined_global_search BOOLEAN NOT NULL DEFAULT 1,in_combined_public_reload BOOLEAN NOT NULL DEFAULT 1)";
        sql = "CREATE TABLE note (_id INTEGER PRIMARY KEY AUTOINCREMENT,origin_id INTEGER NOT NULL,note_oid TEXT NOT NULL,note_status INTEGER NOT NULL DEFAULT 0,conversation_id INTEGER NOT NULL DEFAULT 0,conversation_oid TEXT,url TEXT,body TEXT,body_to_search TEXT,via TEXT,note_author_id INTEGER NOT NULL DEFAULT 0,in_reply_to_note_id INTEGER,in_reply_to_actor_id INTEGER,private INTEGER NOT NULL DEFAULT 0,favorited INTEGER NOT NULL DEFAULT 0,reblogged INTEGER NOT NULL DEFAULT 0,mentioned INTEGER NOT NULL DEFAULT 0,favorite_count INTEGER NOT NULL DEFAULT 0,reblog_count INTEGER NOT NULL DEFAULT 0,reply_count INTEGER NOT NULL DEFAULT 0,note_ins_date INTEGER NOT NULL,note_updated_date INTEGER NOT NULL DEFAULT 0)";
        sql = "CREATE UNIQUE INDEX idx_note_origin ON note (origin_id, note_oid)";
        sql = "CREATE INDEX idx_note_in_reply_to_note_id ON note (in_reply_to_note_id)";
        sql = "CREATE INDEX idx_note_conversation_id ON note (conversation_id)";
        sql = "CREATE INDEX idx_conversation_oid ON note (origin_id, conversation_oid)";
        sql = "CREATE TABLE user (_id INTEGER PRIMARY KEY AUTOINCREMENT,user_known_as TEXT NOT NULL DEFAULT '',is_my_user INTEGER NOT NULL DEFAULT 0)";
        sql = "CREATE INDEX idx_my_user ON user (is_my_user)";
        sql = "CREATE TABLE actor (_id INTEGER PRIMARY KEY AUTOINCREMENT,user_id INTEGER NOT NULL,origin_id INTEGER NOT NULL,actor_oid TEXT NOT NULL,username TEXT NOT NULL,webfinger_id TEXT NOT NULL,real_name TEXT,actor_description TEXT,location TEXT,profile_url TEXT,homepage TEXT,avatar_url TEXT,banner_url TEXT,notes_count INTEGER NOT NULL DEFAULT 0,favorited_count INTEGER NOT NULL DEFAULT 0,following_count INTEGER NOT NULL DEFAULT 0,followers_count INTEGER NOT NULL DEFAULT 0,actor_created_date INTEGER NOT NULL DEFAULT 0,actor_updated_date INTEGER NOT NULL DEFAULT 0,actor_ins_date INTEGER NOT NULL,actor_activity_id INTEGER NOT NULL DEFAULT 0,actor_activity_date INTEGER NOT NULL DEFAULT 0)";
        sql = "CREATE UNIQUE INDEX idx_actor_origin ON actor (origin_id, actor_oid)";
        sql = "CREATE INDEX idx_actor_user ON actor (user_id)";
        sql = "CREATE INDEX idx_actor_webfinger ON actor (webfinger_id)";
        sql = "CREATE TABLE audience (actor_id INTEGER NOT NULL,note_id INTEGER NOT NULL, CONSTRAINT pk_audience PRIMARY KEY (note_id ASC, actor_id ASC))";
        sql = "CREATE INDEX idx_audience_actor ON audience (actor_id)";
        sql = "CREATE INDEX idx_audience_note ON audience (note_id)";
        sql = "CREATE TABLE friendship (actor_id INTEGER NOT NULL,friend_id INTEGER NOT NULL,followed BOOLEAN NOT NULL, CONSTRAINT pk_friendship PRIMARY KEY (actor_id, friend_id))";
        sql = "CREATE INDEX idx_followers ON friendship (friend_id, actor_id)";
        sql = "CREATE TABLE download (_id INTEGER PRIMARY KEY AUTOINCREMENT,download_type INTEGER NOT NULL,actor_id INTEGER NOT NULL DEFAULT 0,note_id INTEGER NOT NULL DEFAULT 0,content_type INTEGER NOT NULL,valid_from INTEGER NOT NULL,url TEXT NOT NULL,loaded_date INTEGER NOT NULL DEFAULT 0,download_status INTEGER NOT NULL DEFAULT 0,file_name TEXT)";
        sql = "CREATE INDEX idx_download_actor ON download (actor_id, download_status)";
        sql = "CREATE INDEX idx_download_note ON download (note_id, content_type, download_status)";
        sql = "CREATE TABLE timeline (_id INTEGER PRIMARY KEY AUTOINCREMENT,timeline_type TEXT NOT NULL,actor_id INTEGER NOT NULL DEFAULT 0,actor_in_timeline TEXT,origin_id INTEGER NOT NULL DEFAULT 0,search_query TEXT,is_synced_automatically BOOLEAN NOT NULL DEFAULT 0,displayed_in_selector INTEGER NOT NULL DEFAULT 0,selector_order INTEGER NOT NULL DEFAULT 0,sync_succeeded_date INTEGER NOT NULL DEFAULT 0,sync_failed_date INTEGER NOT NULL DEFAULT 0,error_message TEXT,synced_times_count INTEGER NOT NULL DEFAULT 0,sync_failed_times_count INTEGER NOT NULL DEFAULT 0,downloaded_items_count INTEGER NOT NULL DEFAULT 0,new_items_count INTEGER NOT NULL DEFAULT 0,count_since INTEGER NOT NULL DEFAULT 0,synced_times_count_total INTEGER NOT NULL DEFAULT 0,sync_failed_times_count_total INTEGER NOT NULL DEFAULT 0,downloaded_items_count_total INTEGER NOT NULL DEFAULT 0,new_items_count_total INTEGER NOT NULL DEFAULT 0,youngest_position TEXT,youngest_item_date INTEGER NOT NULL DEFAULT 0,youngest_synced_date INTEGER NOT NULL DEFAULT 0,oldest_position TEXT,oldest_item_date INTEGER NOT NULL DEFAULT 0,oldest_synced_date INTEGER NOT NULL DEFAULT 0,visible_item_id INTEGER NOT NULL DEFAULT 0,visible_y INTEGER NOT NULL DEFAULT 0,visible_oldest_date INTEGER NOT NULL DEFAULT 0,last_changed_date INTEGER NOT NULL DEFAULT 0)";
        sql = "CREATE TABLE activity (_id INTEGER PRIMARY KEY AUTOINCREMENT,activity_origin_id INTEGER NOT NULL,activity_oid TEXT NOT NULL,account_id INTEGER NOT NULL,activity_type INTEGER NOT NULL,actor_id INTEGER NOT NULL,activity_note_id INTEGER NOT NULL,activity_actor_id INTEGER NOT NULL,obj_activity_id INTEGER NOT NULL,subscribed INTEGER NOT NULL DEFAULT 0,notified INTEGER NOT NULL DEFAULT 0,notified_actor_id INTEGER NOT NULL DEFAULT 0,new_notification_event INTEGER NOT NULL DEFAULT 0,activity_ins_date INTEGER NOT NULL,activity_updated_date INTEGER NOT NULL DEFAULT 0)";
        sql = "CREATE UNIQUE INDEX idx_activity_origin ON activity (activity_origin_id, activity_oid)";
        sql = "CREATE INDEX idx_activity_message ON activity (activity_note_id)";
        sql = "CREATE INDEX idx_activity_actor ON activity (activity_actor_id)";
        sql = "CREATE INDEX idx_activity_activity ON activity (obj_activity_id)";
        sql = "CREATE INDEX idx_activity_timeline ON activity (activity_updated_date)";
        sql = "CREATE INDEX idx_activity_actor_timeline ON activity (actor_id, activity_updated_date)";
        sql = "CREATE INDEX idx_activity_subscribed_timeline ON activity (subscribed, activity_updated_date)";
        sql = "CREATE INDEX idx_activity_notified_timeline ON activity (notified, activity_updated_date)";
        sql = "CREATE INDEX idx_activity_notified_actor ON activity (notified, notified_actor_id)";
        sql = "CREATE INDEX idx_activity_new_notification ON activity (new_notification_event)";
        sql = "CREATE TABLE command (_id INTEGER PRIMARY KEY NOT NULL,queue_type TEXT NOT NULL,command_code TEXT NOT NULL,command_created_date INTEGER NOT NULL,command_description TEXT,in_foreground BOOLEAN NOT NULL DEFAULT 0,manually_launched BOOLEAN NOT NULL DEFAULT 0,timeline_id INTEGER NOT NULL DEFAULT 0,timeline_type TEXT NOT NULL,account_id INTEGER NOT NULL DEFAULT 0,actor_id INTEGER NOT NULL DEFAULT 0,origin_id INTEGER NOT NULL DEFAULT 0,search_query TEXT,item_id INTEGER NOT NULL DEFAULT 0,username TEXT,last_executed_date INTEGER NOT NULL DEFAULT 0,execution_count INTEGER NOT NULL DEFAULT 0,retries_left INTEGER NOT NULL DEFAULT 0,num_auth_exceptions INTEGER NOT NULL DEFAULT 0,num_io_exceptions INTEGER NOT NULL DEFAULT 0,num_parse_exceptions INTEGER NOT NULL DEFAULT 0,error_message TEXT,downloaded_count INTEGER NOT NULL DEFAULT 0,progress_text TEXT)";

        progressLogger.logProgress(stepTitle + ": Converting notes");

        dropOldIndex("idx_msg_origin");
        dropOldIndex("idx_msg_in_reply_to_msg_id");
        dropOldIndex("idx_msg_conversation_id");
        dropOldIndex("idx_conversation_oid");

        sql = "CREATE TABLE note (_id INTEGER PRIMARY KEY AUTOINCREMENT,origin_id INTEGER NOT NULL,note_oid TEXT NOT NULL,note_status INTEGER NOT NULL DEFAULT 0,conversation_id INTEGER NOT NULL DEFAULT 0,conversation_oid TEXT,url TEXT,body TEXT,body_to_search TEXT,via TEXT,note_author_id INTEGER NOT NULL DEFAULT 0,in_reply_to_note_id INTEGER,in_reply_to_actor_id INTEGER,private INTEGER NOT NULL DEFAULT 0,favorited INTEGER NOT NULL DEFAULT 0,reblogged INTEGER NOT NULL DEFAULT 0,mentioned INTEGER NOT NULL DEFAULT 0,favorite_count INTEGER NOT NULL DEFAULT 0,reblog_count INTEGER NOT NULL DEFAULT 0,reply_count INTEGER NOT NULL DEFAULT 0,note_ins_date INTEGER NOT NULL,note_updated_date INTEGER NOT NULL DEFAULT 0)";
        DbUtils.execSQL(db, sql);
        sql = "CREATE UNIQUE INDEX idx_note_origin ON note (origin_id, note_oid)";
        DbUtils.execSQL(db, sql);
        sql = "CREATE INDEX idx_note_in_reply_to_note_id ON note (in_reply_to_note_id)";
        DbUtils.execSQL(db, sql);
        sql = "CREATE INDEX idx_note_conversation_id ON note (conversation_id)";
        DbUtils.execSQL(db, sql);
        sql = "CREATE INDEX idx_conversation_oid ON note (origin_id, conversation_oid)";
        DbUtils.execSQL(db, sql);

        sql = "INSERT INTO note (" +
                "_id,origin_id,note_oid,note_status,conversation_id,conversation_oid,url,body,body_to_search,via,note_author_id,in_reply_to_note_id,in_reply_to_actor_id,private,favorited,reblogged,mentioned,favorite_count,reblog_count,reply_count,note_ins_date,note_updated_date" +
                ") SELECT " +
                "_id,origin_id, msg_oid, msg_status,conversation_id,conversation_oid,url,body,body_to_search,via, msg_author_id, in_reply_to_msg_id, in_reply_to_user_id,private,favorited,reblogged,mentioned,favorite_count,reblog_count,reply_count, msg_ins_date, msg_updated_date" +
                " FROM msg";
        DbUtils.execSQL(db, sql);

        dropOldTable("msg");

        progressLogger.logProgress(stepTitle + ": Converting actors");

        dropOldIndex("idx_user_origin");
        sql = "ALTER TABLE user RENAME TO olduser";
        DbUtils.execSQL(db, sql);

        sql = "CREATE TABLE user (_id INTEGER PRIMARY KEY AUTOINCREMENT,user_known_as TEXT NOT NULL DEFAULT '',is_my_user INTEGER NOT NULL DEFAULT 0)";
        DbUtils.execSQL(db, sql);
        sql = "CREATE INDEX idx_my_user ON user (is_my_user)";
        DbUtils.execSQL(db, sql);
        sql = "CREATE TABLE actor (_id INTEGER PRIMARY KEY AUTOINCREMENT,user_id INTEGER NOT NULL,origin_id INTEGER NOT NULL,actor_oid TEXT NOT NULL,username TEXT NOT NULL,webfinger_id TEXT NOT NULL,real_name TEXT,actor_description TEXT,location TEXT,profile_url TEXT,homepage TEXT,avatar_url TEXT,banner_url TEXT,notes_count INTEGER NOT NULL DEFAULT 0,favorited_count INTEGER NOT NULL DEFAULT 0,following_count INTEGER NOT NULL DEFAULT 0,followers_count INTEGER NOT NULL DEFAULT 0,actor_created_date INTEGER NOT NULL DEFAULT 0,actor_updated_date INTEGER NOT NULL DEFAULT 0,actor_ins_date INTEGER NOT NULL,actor_activity_id INTEGER NOT NULL DEFAULT 0,actor_activity_date INTEGER NOT NULL DEFAULT 0)";
        DbUtils.execSQL(db, sql);
        sql = "CREATE UNIQUE INDEX idx_actor_origin ON actor (origin_id, actor_oid)";
        DbUtils.execSQL(db, sql);
        sql = "CREATE INDEX idx_actor_user ON actor (user_id)";
        DbUtils.execSQL(db, sql);
        sql = "CREATE INDEX idx_actor_webfinger ON actor (webfinger_id)";
        DbUtils.execSQL(db, sql);

        sql = "INSERT INTO actor (" +
                "_id,user_id,origin_id,actor_oid,username,webfinger_id,real_name,actor_description,location,profile_url,homepage,avatar_url,banner_url,notes_count,favorited_count,following_count,followers_count,actor_created_date,actor_updated_date,actor_ins_date,actor_activity_id,actor_activity_date" +
                ") SELECT " +
                "_id,    _id,origin_id, user_oid,username,webfinger_id,real_name, user_description,location,profile_url,homepage,avatar_url,banner_url,  msg_count,favorited_count,following_count,followers_count, user_created_date, user_updated_date, user_ins_date, user_activity_id, user_activity_date" +
                " FROM olduser";
        DbUtils.execSQL(db, sql);

        sql = "INSERT INTO user (" +
                "_id, user_known_as, is_my_user" +
                ") SELECT " +
                "_id,  webfinger_id, 0" +
                " FROM actor";
        DbUtils.execSQL(db, sql);

        dropOldTable("olduser");

        progressLogger.logProgress(stepTitle + ": Converting audience");

        dropOldIndex("idx_audience_user");
        dropOldIndex("idx_audience_msg");
        sql = "ALTER TABLE audience RENAME TO oldaudience";
        DbUtils.execSQL(db, sql);

        sql = "CREATE TABLE audience (actor_id INTEGER NOT NULL,note_id INTEGER NOT NULL, CONSTRAINT pk_audience PRIMARY KEY (note_id ASC, actor_id ASC))";
        DbUtils.execSQL(db, sql);
        sql = "CREATE INDEX idx_audience_actor ON audience (actor_id)";
        DbUtils.execSQL(db, sql);
        sql = "CREATE INDEX idx_audience_note ON audience (note_id)";
        DbUtils.execSQL(db, sql);

        sql = "INSERT INTO audience (" +
                "actor_id,note_id" +
                ") SELECT " +
                " user_id, msg_id" +
                " FROM oldaudience";
        DbUtils.execSQL(db, sql);

        dropOldTable("oldaudience");

        progressLogger.logProgress(stepTitle + ": Converting download");

        dropOldIndex("idx_download_user");
        dropOldIndex("idx_download_msg");
        sql = "ALTER TABLE download RENAME TO olddownload";
        DbUtils.execSQL(db, sql);

        sql = "CREATE TABLE download (_id INTEGER PRIMARY KEY AUTOINCREMENT,download_type INTEGER NOT NULL,actor_id INTEGER NOT NULL DEFAULT 0,note_id INTEGER NOT NULL DEFAULT 0,content_type INTEGER NOT NULL,valid_from INTEGER NOT NULL,url TEXT NOT NULL,loaded_date INTEGER NOT NULL DEFAULT 0,download_status INTEGER NOT NULL DEFAULT 0,file_name TEXT)";
        DbUtils.execSQL(db, sql);
        sql = "CREATE INDEX idx_download_actor ON download (actor_id, download_status)";
        DbUtils.execSQL(db, sql);
        sql = "CREATE INDEX idx_download_note ON download (note_id, content_type, download_status)";
        DbUtils.execSQL(db, sql);

        sql = "INSERT INTO download (" +
                "_id,download_type,actor_id,note_id,content_type,valid_from,url,loaded_date,download_status,file_name" +
                ") SELECT " +
                "_id,download_type, user_id, msg_id,content_type,valid_from,url,loaded_date,download_status,file_name" +
                " FROM olddownload";
        DbUtils.execSQL(db, sql);

        dropOldTable("olddownload");

        progressLogger.logProgress(stepTitle + ": Converting activity");

        dropOldIndex("idx_activity_origin");
        dropOldIndex("idx_activity_message");
        dropOldIndex("idx_activity_user");
        dropOldIndex("idx_activity_activity");
        dropOldIndex("idx_activity_timeline");
        dropOldIndex("idx_activity_actor_timeline");
        dropOldIndex("idx_activity_subscribed_timeline");
        dropOldIndex("idx_activity_notified_timeline");
        dropOldIndex("idx_activity_new_notification");
        sql = "ALTER TABLE activity RENAME TO oldactivity";
        DbUtils.execSQL(db, sql);

        sql = "CREATE TABLE activity (_id INTEGER PRIMARY KEY AUTOINCREMENT,activity_origin_id INTEGER NOT NULL,activity_oid TEXT NOT NULL,account_id INTEGER NOT NULL,activity_type INTEGER NOT NULL,actor_id INTEGER NOT NULL,activity_note_id INTEGER NOT NULL,activity_actor_id INTEGER NOT NULL,obj_activity_id INTEGER NOT NULL,subscribed INTEGER NOT NULL DEFAULT 0,notified INTEGER NOT NULL DEFAULT 0,notified_actor_id INTEGER NOT NULL DEFAULT 0,new_notification_event INTEGER NOT NULL DEFAULT 0,activity_ins_date INTEGER NOT NULL,activity_updated_date INTEGER NOT NULL DEFAULT 0)";
        DbUtils.execSQL(db, sql);
        sql = "CREATE UNIQUE INDEX idx_activity_origin ON activity (activity_origin_id, activity_oid)";
        DbUtils.execSQL(db, sql);
        sql = "CREATE INDEX idx_activity_message ON activity (activity_note_id)";
        DbUtils.execSQL(db, sql);
        sql = "CREATE INDEX idx_activity_actor ON activity (activity_actor_id)";
        DbUtils.execSQL(db, sql);
        sql = "CREATE INDEX idx_activity_activity ON activity (obj_activity_id)";
        DbUtils.execSQL(db, sql);
        sql = "CREATE INDEX idx_activity_timeline ON activity (activity_updated_date)";
        DbUtils.execSQL(db, sql);
        sql = "CREATE INDEX idx_activity_actor_timeline ON activity (actor_id, activity_updated_date)";
        DbUtils.execSQL(db, sql);
        sql = "CREATE INDEX idx_activity_subscribed_timeline ON activity (subscribed, activity_updated_date)";
        DbUtils.execSQL(db, sql);
        sql = "CREATE INDEX idx_activity_notified_timeline ON activity (notified, activity_updated_date)";
        DbUtils.execSQL(db, sql);
        sql = "CREATE INDEX idx_activity_notified_actor ON activity (notified, notified_actor_id)";
        DbUtils.execSQL(db, sql);
        sql = "CREATE INDEX idx_activity_new_notification ON activity (new_notification_event)";
        DbUtils.execSQL(db, sql);

        sql = "INSERT INTO activity (" +
                "_id,activity_origin_id,activity_oid,account_id,activity_type,actor_id,activity_note_id,activity_actor_id,obj_activity_id,subscribed,notified,new_notification_event,activity_ins_date,activity_updated_date" +
                ") SELECT " +
                "_id,activity_origin_id,activity_oid,account_id,activity_type,actor_id, activity_msg_id, activity_user_id,obj_activity_id,subscribed,notified,new_notification_event,activity_ins_date,activity_updated_date" +
                " FROM oldactivity";
        DbUtils.execSQL(db, sql);

        dropOldTable("oldactivity");

        progressLogger.logProgress(stepTitle + ": Converting timeline");

        sql = "ALTER TABLE timeline RENAME TO oldtimeline";
        DbUtils.execSQL(db, sql);

        sql = "CREATE TABLE timeline (_id INTEGER PRIMARY KEY AUTOINCREMENT,timeline_type TEXT NOT NULL,actor_id INTEGER NOT NULL DEFAULT 0,actor_in_timeline TEXT,origin_id INTEGER NOT NULL DEFAULT 0,search_query TEXT,is_synced_automatically BOOLEAN NOT NULL DEFAULT 0,displayed_in_selector INTEGER NOT NULL DEFAULT 0,selector_order INTEGER NOT NULL DEFAULT 0,sync_succeeded_date INTEGER NOT NULL DEFAULT 0,sync_failed_date INTEGER NOT NULL DEFAULT 0,error_message TEXT,synced_times_count INTEGER NOT NULL DEFAULT 0,sync_failed_times_count INTEGER NOT NULL DEFAULT 0,downloaded_items_count INTEGER NOT NULL DEFAULT 0,new_items_count INTEGER NOT NULL DEFAULT 0,count_since INTEGER NOT NULL DEFAULT 0,synced_times_count_total INTEGER NOT NULL DEFAULT 0,sync_failed_times_count_total INTEGER NOT NULL DEFAULT 0,downloaded_items_count_total INTEGER NOT NULL DEFAULT 0,new_items_count_total INTEGER NOT NULL DEFAULT 0,youngest_position TEXT,youngest_item_date INTEGER NOT NULL DEFAULT 0,youngest_synced_date INTEGER NOT NULL DEFAULT 0,oldest_position TEXT,oldest_item_date INTEGER NOT NULL DEFAULT 0,oldest_synced_date INTEGER NOT NULL DEFAULT 0,visible_item_id INTEGER NOT NULL DEFAULT 0,visible_y INTEGER NOT NULL DEFAULT 0,visible_oldest_date INTEGER NOT NULL DEFAULT 0,last_changed_date INTEGER NOT NULL DEFAULT 0)";
        DbUtils.execSQL(db, sql);

        sql = "INSERT INTO timeline (" +
                "_id,timeline_type," +
                "actor_id,actor_in_timeline,origin_id,search_query,is_synced_automatically,displayed_in_selector,selector_order,sync_succeeded_date,sync_failed_date,error_message,synced_times_count,sync_failed_times_count,downloaded_items_count,new_items_count,count_since,synced_times_count_total,sync_failed_times_count_total,downloaded_items_count_total,new_items_count_total,youngest_position,youngest_item_date,youngest_synced_date,oldest_position,oldest_item_date,oldest_synced_date,visible_item_id,visible_y,visible_oldest_date" +
                ") SELECT " +
                "_id," +
                " CASE timeline_type" +
                "   WHEN 'user' THEN 'sent'" +
                "   WHEN 'my_friends' THEN 'friends'" +
                "   WHEN 'my_followers' THEN 'followers'" +
                "   ELSE timeline_type" +
                " END," +
                " user_id, user_in_timeline,origin_id,search_query,is_synced_automatically,displayed_in_selector,selector_order,sync_succeeded_date,sync_failed_date,error_message,synced_times_count,sync_failed_times_count,downloaded_items_count,new_items_count,count_since,synced_times_count_total,sync_failed_times_count_total,downloaded_items_count_total,new_items_count_total,youngest_position,youngest_item_date,youngest_synced_date,oldest_position,oldest_item_date,oldest_synced_date,visible_item_id,visible_y,visible_oldest_date" +
                " FROM oldtimeline";
        DbUtils.execSQL(db, sql);

        dropOldTable("oldtimeline");

        progressLogger.logProgress(stepTitle + ": Converting command");

        sql = "ALTER TABLE command RENAME TO oldcommand";
        DbUtils.execSQL(db, sql);

        sql = "CREATE TABLE command (_id INTEGER PRIMARY KEY NOT NULL,queue_type TEXT NOT NULL,command_code TEXT NOT NULL,command_created_date INTEGER NOT NULL,command_description TEXT,in_foreground BOOLEAN NOT NULL DEFAULT 0,manually_launched BOOLEAN NOT NULL DEFAULT 0,timeline_id INTEGER NOT NULL DEFAULT 0,timeline_type TEXT NOT NULL,account_id INTEGER NOT NULL DEFAULT 0,actor_id INTEGER NOT NULL DEFAULT 0,origin_id INTEGER NOT NULL DEFAULT 0,search_query TEXT,item_id INTEGER NOT NULL DEFAULT 0,username TEXT,last_executed_date INTEGER NOT NULL DEFAULT 0,execution_count INTEGER NOT NULL DEFAULT 0,retries_left INTEGER NOT NULL DEFAULT 0,num_auth_exceptions INTEGER NOT NULL DEFAULT 0,num_io_exceptions INTEGER NOT NULL DEFAULT 0,num_parse_exceptions INTEGER NOT NULL DEFAULT 0,error_message TEXT,downloaded_count INTEGER NOT NULL DEFAULT 0,progress_text TEXT)";
        DbUtils.execSQL(db, sql);

        sql = "INSERT INTO command (" +
                "_id,queue_type,command_code,command_created_date,command_description,in_foreground,manually_launched,timeline_id,timeline_type,account_id,actor_id,origin_id,search_query,item_id,username,last_executed_date,execution_count,retries_left,num_auth_exceptions,num_io_exceptions,num_parse_exceptions,error_message,downloaded_count,progress_text" +
                ") SELECT " +
                "_id,queue_type,command_code,command_created_date,command_description,in_foreground,manually_launched,timeline_id,timeline_type,account_id, user_id,origin_id,search_query,item_id,username,last_executed_date,execution_count,retries_left,num_auth_exceptions,num_io_exceptions,num_parse_exceptions,error_message,downloaded_count,progress_text" +
                " FROM oldcommand";
        DbUtils.execSQL(db, sql);

        dropOldTable("oldcommand");

        progressLogger.logProgress(stepTitle + ": Converting friendship");

        dropOldIndex("idx_followers");

        sql = "ALTER TABLE friendship RENAME TO oldfriendship";
        DbUtils.execSQL(db, sql);

        sql = "CREATE TABLE friendship (actor_id INTEGER NOT NULL,friend_id INTEGER NOT NULL,followed BOOLEAN NOT NULL, CONSTRAINT pk_friendship PRIMARY KEY (actor_id, friend_id))";
        DbUtils.execSQL(db, sql);
        sql = "CREATE INDEX idx_followers ON friendship (friend_id, actor_id)";
        DbUtils.execSQL(db, sql);

        sql = "INSERT INTO friendship (" +
                "actor_id,friend_id,followed" +
                ") SELECT " +
                "user_id,friend_id,followed" +
                " FROM oldfriendship";
        DbUtils.execSQL(db, sql);

        dropOldTable("oldfriendship");
    }
}
