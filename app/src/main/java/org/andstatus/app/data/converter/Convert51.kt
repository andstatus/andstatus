/*
 * Copyright (c) 2020 yvolk (Yuri Volkov), http://yurivolkov.com
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

internal class Convert51 : ConvertOneStep() {
    override fun execute2() {
        convertActivities()
        convertActorEndpoints()
    }

    private fun convertActivities() {
        progressLogger.logProgress("$stepTitle: Converting Activities")
        dropOldIndex("idx_activity_origin")
        dropOldIndex("idx_activity_message")
        dropOldIndex("idx_activity_actor")
        dropOldIndex("idx_activity_activity")
        dropOldIndex("idx_activity_timeline")
        dropOldIndex("idx_activity_actor_timeline")
        dropOldIndex("idx_activity_subscribed_timeline")
        dropOldIndex("idx_activity_notified_timeline")
        dropOldIndex("idx_activity_notified_actor")
        dropOldIndex("idx_activity_new_notification")
        dropOldIndex("idx_activity_interacted_timeline")
        dropOldIndex("idx_activity_interacted_actor")
        sql = "ALTER TABLE activity RENAME TO oldactivity"
        DbUtils.execSQL(db, sql)
        sql = "CREATE TABLE activity (_id INTEGER PRIMARY KEY AUTOINCREMENT,activity_origin_id INTEGER NOT NULL,activity_oid TEXT NOT NULL,account_id INTEGER NOT NULL,activity_type INTEGER NOT NULL,activity_actor_id INTEGER NOT NULL,activity_note_id INTEGER NOT NULL,obj_actor_id INTEGER NOT NULL,obj_activity_id INTEGER NOT NULL,subscribed INTEGER NOT NULL DEFAULT 0,interacted INTEGER NOT NULL DEFAULT 0,interaction_event INTEGER NOT NULL DEFAULT 0,notified INTEGER NOT NULL DEFAULT 0,notified_actor_id INTEGER NOT NULL DEFAULT 0,new_notification_event INTEGER NOT NULL DEFAULT 0,activity_ins_date INTEGER NOT NULL,activity_updated_date INTEGER NOT NULL DEFAULT 0)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE UNIQUE INDEX idx_activity_origin ON activity (activity_origin_id, activity_oid)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE INDEX idx_activity_message ON activity (activity_note_id)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE INDEX idx_activity_obj_actor ON activity (obj_actor_id)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE INDEX idx_activity_activity ON activity (obj_activity_id)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE INDEX idx_activity_timeline ON activity (activity_updated_date)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE INDEX idx_activity_actor_timeline ON activity (activity_actor_id, activity_updated_date)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE INDEX idx_activity_subscribed_timeline ON activity (subscribed, activity_updated_date)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE INDEX idx_activity_notified_timeline ON activity (notified, activity_updated_date)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE INDEX idx_activity_notified_actor ON activity (notified, notified_actor_id)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE INDEX idx_activity_new_notification ON activity (new_notification_event)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE INDEX idx_activity_interacted_timeline ON activity (interacted, activity_updated_date)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE INDEX idx_activity_interacted_actor ON activity (interacted, notified_actor_id)"
        DbUtils.execSQL(db, sql)
        sql = "INSERT INTO activity (" +
                "_id,activity_origin_id,activity_oid," +
                "account_id,activity_type,activity_actor_id,activity_note_id,obj_actor_id,     obj_activity_id," +
                "subscribed,interacted,interaction_event,notified,notified_actor_id,new_notification_event," +
                "activity_ins_date,activity_updated_date) SELECT " +
                "_id,activity_origin_id,activity_oid," +
                "account_id,activity_type,actor_id,         activity_note_id,activity_actor_id,obj_activity_id," +
                "subscribed,interacted,interaction_event,notified,notified_actor_id,new_notification_event," +
                "activity_ins_date,activity_updated_date" +
                " FROM oldactivity"
        DbUtils.execSQL(db, sql)
        dropOldTable("oldactivity")
    }

    private fun convertActorEndpoints() {
        progressLogger.logProgress("$stepTitle: Converting Actor endpoints")
        sql = "ALTER TABLE actorendpoints RENAME TO oldactorendpoints"
        DbUtils.execSQL(db, sql)
        sql = "CREATE TABLE actorendpoints (actor_id INTEGER NOT NULL,endpoint_type INTEGER NOT NULL,endpoint_index INTEGER NOT NULL DEFAULT 0,endpoint_uri TEXT NOT NULL, CONSTRAINT pk_actorendpoints PRIMARY KEY (actor_id ASC, endpoint_type ASC, endpoint_index ASC))"
        DbUtils.execSQL(db, sql)
        sql = "INSERT INTO actorendpoints (" +
                "actor_id,endpoint_type,endpoint_index,endpoint_uri) SELECT " +
                "actor_id,endpoint_type,endpoint_index,endpoint_uri" +
                " FROM oldactorendpoints"
        DbUtils.execSQL(db, sql)
        dropOldTable("oldactorendpoints")
    }

    init {
        versionTo = 54
    }
}
