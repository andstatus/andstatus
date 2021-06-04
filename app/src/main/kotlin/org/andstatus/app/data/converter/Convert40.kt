/*
 * Copyright (c) 2018 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.database.Cursor
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.data.DbUtils
import org.andstatus.app.data.DownloadType
import org.andstatus.app.data.MyContentType
import org.andstatus.app.data.MyQuery
import org.andstatus.app.util.UriUtils
import java.util.*
import java.util.function.Consumer
import java.util.function.Function

internal class Convert40 : ConvertOneStep() {
    private class Data private constructor(val id: Long, val uri: String, val contentType: MyContentType) {
        companion object {
            fun fromCursor(cursor: Cursor): Data {
                val uri = DbUtils.getString(cursor, "url")
                return Data(
                        DbUtils.getLong(cursor, "_id"),
                        uri,
                        MyContentType.fromUri(DownloadType.ATTACHMENT,
                                 MyContextHolder.myContextHolder.getNow().context.getContentResolver(),
                                UriUtils.fromString(uri),
                                DbUtils.getString(cursor, "media_type"))
                )
            }
        }
    }

    override fun execute2() {

        // Table creation statements for versionTo
        sql = "CREATE TABLE origin (_id INTEGER PRIMARY KEY AUTOINCREMENT,origin_type_id INTEGER NOT NULL,origin_name TEXT NOT NULL,origin_url TEXT NOT NULL,ssl BOOLEAN NOT NULL DEFAULT 1,ssl_mode INTEGER NOT NULL DEFAULT 1,allow_html BOOLEAN NOT NULL DEFAULT 1,text_limit INTEGER NOT NULL,short_url_length INTEGER NOT NULL DEFAULT 0,mention_as_webfinger_id INTEGER NOT NULL DEFAULT 3,use_legacy_http INTEGER NOT NULL DEFAULT 3,in_combined_global_search BOOLEAN NOT NULL DEFAULT 1,in_combined_public_reload BOOLEAN NOT NULL DEFAULT 1)"
        sql = "CREATE TABLE note (_id INTEGER PRIMARY KEY AUTOINCREMENT,origin_id INTEGER NOT NULL,note_oid TEXT NOT NULL,note_status INTEGER NOT NULL DEFAULT 0,conversation_id INTEGER NOT NULL DEFAULT 0,conversation_oid TEXT,url TEXT,note_name TEXT,content TEXT,content_to_search TEXT,via TEXT,note_author_id INTEGER NOT NULL DEFAULT 0,in_reply_to_note_id INTEGER,in_reply_to_actor_id INTEGER,public INTEGER NOT NULL DEFAULT 0,favorited INTEGER NOT NULL DEFAULT 0,reblogged INTEGER NOT NULL DEFAULT 0,favorite_count INTEGER NOT NULL DEFAULT 0,reblog_count INTEGER NOT NULL DEFAULT 0,reply_count INTEGER NOT NULL DEFAULT 0,attachments_count INTEGER NOT NULL DEFAULT 0,note_ins_date INTEGER NOT NULL,note_updated_date INTEGER NOT NULL DEFAULT 0)"
        sql = "CREATE UNIQUE INDEX idx_note_origin ON note (origin_id, note_oid)"
        sql = "CREATE INDEX idx_note_in_reply_to_note_id ON note (in_reply_to_note_id)"
        sql = "CREATE INDEX idx_note_conversation_id ON note (conversation_id)"
        sql = "CREATE INDEX idx_conversation_oid ON note (origin_id, conversation_oid)"
        sql = "CREATE TABLE user (_id INTEGER PRIMARY KEY AUTOINCREMENT,user_known_as TEXT NOT NULL DEFAULT '',is_my_user INTEGER NOT NULL DEFAULT 0)"
        sql = "CREATE INDEX idx_my_user ON user (is_my_user)"
        sql = "CREATE TABLE actor (_id INTEGER PRIMARY KEY AUTOINCREMENT,user_id INTEGER NOT NULL,origin_id INTEGER NOT NULL,actor_oid TEXT NOT NULL,username TEXT NOT NULL,webfinger_id TEXT NOT NULL,real_name TEXT,actor_description TEXT,location TEXT,profile_url TEXT,homepage TEXT,avatar_url TEXT,banner_url TEXT,notes_count INTEGER NOT NULL DEFAULT 0,favorited_count INTEGER NOT NULL DEFAULT 0,following_count INTEGER NOT NULL DEFAULT 0,followers_count INTEGER NOT NULL DEFAULT 0,actor_created_date INTEGER NOT NULL DEFAULT 0,actor_updated_date INTEGER NOT NULL DEFAULT 0,actor_ins_date INTEGER NOT NULL,actor_activity_id INTEGER NOT NULL DEFAULT 0,actor_activity_date INTEGER NOT NULL DEFAULT 0)"
        sql = "CREATE UNIQUE INDEX idx_actor_origin ON actor (origin_id, actor_oid)"
        sql = "CREATE INDEX idx_actor_user ON actor (user_id)"
        sql = "CREATE INDEX idx_actor_webfinger ON actor (webfinger_id)"
        sql = "CREATE TABLE audience (actor_id INTEGER NOT NULL,note_id INTEGER NOT NULL, CONSTRAINT pk_audience PRIMARY KEY (note_id ASC, actor_id ASC))"
        sql = "CREATE INDEX idx_audience_actor ON audience (actor_id)"
        sql = "CREATE INDEX idx_audience_note ON audience (note_id)"
        sql = "CREATE TABLE friendship (actor_id INTEGER NOT NULL,friend_id INTEGER NOT NULL,followed BOOLEAN NOT NULL, CONSTRAINT pk_friendship PRIMARY KEY (actor_id, friend_id))"
        sql = "CREATE INDEX idx_followers ON friendship (friend_id, actor_id)"
        sql = "CREATE TABLE download (_id INTEGER PRIMARY KEY AUTOINCREMENT,download_type INTEGER NOT NULL,download_number INTEGER NOT NULL DEFAULT 0,actor_id INTEGER NOT NULL DEFAULT 0,note_id INTEGER NOT NULL DEFAULT 0,content_type INTEGER NOT NULL DEFAULT 0,media_type TEXT NOT NULL,url TEXT NOT NULL,download_status INTEGER NOT NULL DEFAULT 0,width INTEGER NOT NULL DEFAULT 0,height INTEGER NOT NULL DEFAULT 0,duration INTEGER NOT NULL DEFAULT 0,file_name TEXT,file_size INTEGER NOT NULL DEFAULT 0,downloaded_date INTEGER NOT NULL DEFAULT 0)"
        sql = "CREATE INDEX idx_download_actor ON download (actor_id, download_status)"
        sql = "CREATE INDEX idx_download_note ON download (note_id, download_number)"
        sql = "CREATE INDEX idx_download_downloaded_date ON download (downloaded_date)"
        sql = "CREATE TABLE timeline (_id INTEGER PRIMARY KEY AUTOINCREMENT,timeline_type TEXT NOT NULL,actor_id INTEGER NOT NULL DEFAULT 0,actor_in_timeline TEXT,origin_id INTEGER NOT NULL DEFAULT 0,search_query TEXT,is_synced_automatically BOOLEAN NOT NULL DEFAULT 0,displayed_in_selector INTEGER NOT NULL DEFAULT 0,selector_order INTEGER NOT NULL DEFAULT 0,sync_succeeded_date INTEGER NOT NULL DEFAULT 0,sync_failed_date INTEGER NOT NULL DEFAULT 0,error_message TEXT,synced_times_count INTEGER NOT NULL DEFAULT 0,sync_failed_times_count INTEGER NOT NULL DEFAULT 0,downloaded_items_count INTEGER NOT NULL DEFAULT 0,new_items_count INTEGER NOT NULL DEFAULT 0,count_since INTEGER NOT NULL DEFAULT 0,synced_times_count_total INTEGER NOT NULL DEFAULT 0,sync_failed_times_count_total INTEGER NOT NULL DEFAULT 0,downloaded_items_count_total INTEGER NOT NULL DEFAULT 0,new_items_count_total INTEGER NOT NULL DEFAULT 0,youngest_position TEXT,youngest_item_date INTEGER NOT NULL DEFAULT 0,youngest_synced_date INTEGER NOT NULL DEFAULT 0,oldest_position TEXT,oldest_item_date INTEGER NOT NULL DEFAULT 0,oldest_synced_date INTEGER NOT NULL DEFAULT 0,visible_item_id INTEGER NOT NULL DEFAULT 0,visible_y INTEGER NOT NULL DEFAULT 0,visible_oldest_date INTEGER NOT NULL DEFAULT 0,last_changed_date INTEGER NOT NULL DEFAULT 0)"
        sql = "CREATE TABLE activity (_id INTEGER PRIMARY KEY AUTOINCREMENT,activity_origin_id INTEGER NOT NULL,activity_oid TEXT NOT NULL,account_id INTEGER NOT NULL,activity_type INTEGER NOT NULL,actor_id INTEGER NOT NULL,activity_note_id INTEGER NOT NULL,activity_actor_id INTEGER NOT NULL,obj_activity_id INTEGER NOT NULL,subscribed INTEGER NOT NULL DEFAULT 0,interacted INTEGER NOT NULL DEFAULT 0,interaction_event INTEGER NOT NULL DEFAULT 0,notified INTEGER NOT NULL DEFAULT 0,notified_actor_id INTEGER NOT NULL DEFAULT 0,new_notification_event INTEGER NOT NULL DEFAULT 0,activity_ins_date INTEGER NOT NULL,activity_updated_date INTEGER NOT NULL DEFAULT 0)"
        sql = "CREATE UNIQUE INDEX idx_activity_origin ON activity (activity_origin_id, activity_oid)"
        sql = "CREATE INDEX idx_activity_message ON activity (activity_note_id)"
        sql = "CREATE INDEX idx_activity_actor ON activity (activity_actor_id)"
        sql = "CREATE INDEX idx_activity_activity ON activity (obj_activity_id)"
        sql = "CREATE INDEX idx_activity_timeline ON activity (activity_updated_date)"
        sql = "CREATE INDEX idx_activity_actor_timeline ON activity (actor_id, activity_updated_date)"
        sql = "CREATE INDEX idx_activity_subscribed_timeline ON activity (subscribed, activity_updated_date)"
        sql = "CREATE INDEX idx_activity_notified_timeline ON activity (notified, activity_updated_date)"
        sql = "CREATE INDEX idx_activity_notified_actor ON activity (notified, notified_actor_id)"
        sql = "CREATE INDEX idx_activity_new_notification ON activity (new_notification_event)"
        sql = "CREATE INDEX idx_activity_interacted_timeline ON activity (interacted, activity_updated_date)"
        sql = "CREATE INDEX idx_activity_interacted_actor ON activity (interacted, notified_actor_id)"
        sql = "CREATE TABLE command (_id INTEGER PRIMARY KEY NOT NULL,queue_type TEXT NOT NULL,command_code TEXT NOT NULL,command_created_date INTEGER NOT NULL,command_description TEXT,in_foreground BOOLEAN NOT NULL DEFAULT 0,manually_launched BOOLEAN NOT NULL DEFAULT 0,timeline_id INTEGER NOT NULL DEFAULT 0,timeline_type TEXT NOT NULL,account_id INTEGER NOT NULL DEFAULT 0,actor_id INTEGER NOT NULL DEFAULT 0,origin_id INTEGER NOT NULL DEFAULT 0,search_query TEXT,item_id INTEGER NOT NULL DEFAULT 0,username TEXT,last_executed_date INTEGER NOT NULL DEFAULT 0,execution_count INTEGER NOT NULL DEFAULT 0,retries_left INTEGER NOT NULL DEFAULT 0,num_auth_exceptions INTEGER NOT NULL DEFAULT 0,num_io_exceptions INTEGER NOT NULL DEFAULT 0,num_parse_exceptions INTEGER NOT NULL DEFAULT 0,error_message TEXT,downloaded_count INTEGER NOT NULL DEFAULT 0,progress_text TEXT)"
        progressLogger.logProgress("$stepTitle: Converting download")
        sql = "ALTER TABLE download ADD COLUMN content_type INTEGER NOT NULL DEFAULT 0"
        DbUtils.execSQL(db, sql)
        sql = "SELECT * FROM download"
        MyQuery.foldLeft(db, sql, HashSet(), { set: MutableSet<Data> ->
            Function { cursor: Cursor ->
                set.add(Data.fromCursor(cursor))
                set
            }
        }).forEach(Consumer { data: Data ->
            DbUtils.execSQL(db,
                    if (data.uri.isEmpty()) "DELETE FROM download WHERE _id=" + data.id else "UPDATE download SET" +
                            " content_type=" + data.contentType.save() +
                            " WHERE _id=" + data.id)
        }
        )
    }

    init {
        versionTo = 42
    }
}
