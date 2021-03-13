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
package org.andstatus.app.data.converter

import org.andstatus.app.data.DbUtils
import org.andstatus.app.data.MyQuery
import org.andstatus.app.util.MyHtml

internal class Convert25 : ConvertOneStep() {
    protected override fun execute2() {
        // Table creation statements for v.26
        sql = "CREATE TABLE msg (_id INTEGER PRIMARY KEY AUTOINCREMENT,origin_id INTEGER NOT NULL,msg_oid TEXT,msg_status INTEGER NOT NULL DEFAULT 0,conversation_id INTEGER,conversation_oid TEXT,author_id INTEGER,sender_id INTEGER,recipient_id INTEGER,body TEXT,body_to_search TEXT,via TEXT,url TEXT,in_reply_to_msg_id INTEGER,in_reply_to_user_id INTEGER,msg_created_date INTEGER,msg_sent_date INTEGER,msg_ins_date INTEGER NOT NULL,public BOOLEAN DEFAULT 0 NOT NULL)"
        sql = "CREATE UNIQUE INDEX idx_msg_origin ON msg (origin_id, msg_oid)"
        sql = "CREATE INDEX idx_msg_sent_date ON msg (msg_sent_date)"
        sql = "CREATE INDEX idx_msg_in_reply_to_msg_id ON msg (in_reply_to_msg_id) WHERE in_reply_to_msg_id IS NOT NULL"
        sql = "CREATE INDEX idx_msg_conversation_id ON msg (conversation_id) WHERE conversation_id IS NOT NULL"
        sql = "CREATE TABLE msgofuser (user_id INTEGER NOT NULL,msg_id INTEGER NOT NULL,subscribed BOOLEAN DEFAULT 0 NOT NULL,favorited BOOLEAN DEFAULT 0 NOT NULL,reblogged BOOLEAN DEFAULT 0 NOT NULL,reblog_oid TEXT,mentioned BOOLEAN DEFAULT 0 NOT NULL,replied BOOLEAN DEFAULT 0 NOT NULL,directed BOOLEAN DEFAULT 0 NOT NULL, CONSTRAINT pk_msgofuser PRIMARY KEY (user_id ASC, msg_id ASC))"
        sql = "CREATE TABLE user (_id INTEGER PRIMARY KEY AUTOINCREMENT,origin_id INTEGER NOT NULL,user_oid TEXT,username TEXT NOT NULL,webfinger_id TEXT NOT NULL,real_name TEXT,user_description TEXT,location TEXT,profile_url TEXT,homepage TEXT,avatar_url TEXT,banner_url TEXT,msg_count INTEGER DEFAULT 0 NOT NULL,favorited_count INTEGER DEFAULT 0 NOT NULL,following_count INTEGER DEFAULT 0 NOT NULL,followers_count INTEGER DEFAULT 0 NOT NULL,user_created_date INTEGER DEFAULT 0 NOT NULL,user_updated_date INTEGER DEFAULT 0 NOT NULL,user_ins_date INTEGER NOT NULL,user_msg_id INTEGER DEFAULT 0 NOT NULL,user_msg_date INTEGER DEFAULT 0 NOT NULL)"
        sql = "CREATE UNIQUE INDEX idx_user_origin ON user (origin_id, user_oid)"
        sql = "CREATE TABLE followinguser (user_id INTEGER NOT NULL,following_user_id INTEGER NOT NULL,user_followed BOOLEAN DEFAULT 1 NOT NULL, CONSTRAINT pk_followinguser PRIMARY KEY (user_id ASC, following_user_id ASC))"
        sql = "CREATE TABLE download (_id INTEGER PRIMARY KEY AUTOINCREMENT,download_type INTEGER NOT NULL,user_id INTEGER,msg_id INTEGER,content_type INTEGER NOT NULL,valid_from INTEGER NOT NULL,url TEXT NOT NULL,loaded_date INTEGER,download_status INTEGER NOT NULL DEFAULT 0,file_name TEXT)"
        sql = "CREATE INDEX idx_download_user ON download (user_id, download_status)"
        sql = "CREATE INDEX idx_download_msg ON download (msg_id, content_type, download_status)"
        sql = "CREATE TABLE origin (_id INTEGER PRIMARY KEY AUTOINCREMENT,origin_type_id INTEGER NOT NULL,origin_name TEXT NOT NULL,origin_url TEXT NOT NULL,ssl BOOLEAN DEFAULT 1 NOT NULL,ssl_mode INTEGER DEFAULT 1 NOT NULL,allow_html BOOLEAN DEFAULT 1 NOT NULL,text_limit INTEGER NOT NULL,short_url_length INTEGER NOT NULL DEFAULT 0,mention_as_webfinger_id INTEGER DEFAULT 3 NOT NULL,use_legacy_http INTEGER DEFAULT 3 NOT NULL,in_combined_global_search BOOLEAN DEFAULT 1 NOT NULL,in_combined_public_reload BOOLEAN DEFAULT 1 NOT NULL)"
        sql = "CREATE UNIQUE INDEX idx_origin_name ON origin (origin_name)"
        sql = "CREATE TABLE timeline (_id INTEGER PRIMARY KEY AUTOINCREMENT,timeline_type STRING NOT NULL,account_id INTEGER,user_id INTEGER,user_in_timeline TEXT,origin_id INTEGER,search_query TEXT,is_synced_automatically BOOLEAN DEFAULT 0 NOT NULL,displayed_in_selector INTEGER DEFAULT 0 NOT NULL,selector_order INTEGER DEFAULT 0 NOT NULL,sync_succeeded_date INTEGER,sync_failed_date INTEGER,error_message TEXT,synced_times_count INTEGER DEFAULT 0 NOT NULL,sync_failed_times_count INTEGER DEFAULT 0 NOT NULL,downloaded_items_count INTEGER DEFAULT 0 NOT NULL,new_items_count INTEGER DEFAULT 0 NOT NULL,count_since INTEGER,synced_times_count_total INTEGER DEFAULT 0 NOT NULL,sync_failed_times_count_total INTEGER DEFAULT 0 NOT NULL,downloaded_items_count_total INTEGER DEFAULT 0 NOT NULL,new_items_count_total INTEGER DEFAULT 0 NOT NULL,youngest_position TEXT,youngest_item_date INTEGER,youngest_synced_date INTEGER,oldest_position TEXT,oldest_item_date INTEGER,oldest_synced_date INTEGER,visible_item_id INTEGER,visible_y INTEGER,visible_oldest_date INTEGER)"
        sql = "CREATE TABLE command (_id INTEGER PRIMARY KEY NOT NULL,queue_type TEXT NOT NULL,command_code TEXT NOT NULL,command_created_date INTEGER NOT NULL,command_description TEXT,in_foreground BOOLEAN DEFAULT 0 NOT NULL,manually_launched BOOLEAN DEFAULT 0 NOT NULL,timeline_id INTEGER,timeline_type STRING,account_id INTEGER,user_id INTEGER,origin_id INTEGER,search_query TEXT,item_id INTEGER,username TEXT,last_executed_date INTEGER,execution_count INTEGER DEFAULT 0 NOT NULL,retries_left INTEGER DEFAULT 0 NOT NULL,num_auth_exceptions INTEGER DEFAULT 0 NOT NULL,num_io_exceptions INTEGER DEFAULT 0 NOT NULL,num_parse_exceptions INTEGER DEFAULT 0 NOT NULL,error_message TEXT,downloaded_count INTEGER DEFAULT 0 NOT NULL,progress_text TEXT)"
        sql = "DROP INDEX idx_msg_in_reply_to_msg_id"
        DbUtils.execSQL(db, sql)
        sql = "ALTER TABLE msg ADD COLUMN conversation_id INTEGER"
        DbUtils.execSQL(db, sql)
        sql = "ALTER TABLE msg ADD COLUMN conversation_oid TEXT"
        DbUtils.execSQL(db, sql)
        sql = "ALTER TABLE msg ADD COLUMN body_to_search TEXT"
        DbUtils.execSQL(db, sql)
        sql = ("CREATE INDEX idx_msg_in_reply_to_msg_id ON msg (" + "in_reply_to_msg_id" + ")"
                + " WHERE " + "in_reply_to_msg_id" + " IS NOT NULL")
        DbUtils.execSQL(db, sql)
        sql = ("CREATE INDEX idx_msg_conversation_id ON msg (" + "conversation_id" + ")"
                + " WHERE " + "conversation_id" + " IS NOT NULL")
        DbUtils.execSQL(db, sql)
        var sql = "SELECT _id, body FROM msg"
        var count = 0
        db?.rawQuery(sql, null)?.use { c ->
            while (c.moveToNext()) {
                sql = ("UPDATE msg SET body_to_search=" + MyQuery.quoteIfNotQuoted(MyHtml.getContentToSearch(c.getString(1)))
                        + " WHERE _id=" + c.getLong(0))
                db?.execSQL(sql)
                count++
                if (progressLogger.loggedMoreSecondsAgoThan(10)) {
                    progressLogger.logProgress("$stepTitle: converted $count rows")
                }
            }
        }
    }

    init {
        versionTo = 26
    }
}