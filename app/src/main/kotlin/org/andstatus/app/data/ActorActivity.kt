/*
 * Copyright (C) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.data

import android.database.sqlite.SQLiteDatabase
import android.provider.BaseColumns
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.database.table.ActivityTable
import org.andstatus.app.database.table.ActorTable
import org.andstatus.app.util.MyLog
import java.util.*

/**
 * Manages minimal information about the latest downloaded note by one Actor (or a User represented by the Actor).
 * We count notes where the Actor is either a Actor or an Author
 *
 * All information is supplied in this constructor, so it doesn't lookup anything in the database
 */
class ActorActivity(actorIdIn: Long, activityId: Long, activityDate: Long) {
    private var actorId: Long = 0

    /**
     * The id of the latest downloaded Note by this Actor
     * 0 - none were downloaded
     */
    private var lastActivityId: Long = 0

    /**
     * 0 - none were downloaded
     */
    private var lastActivityDate: Long = 0

    /**
     * We will update only what really changed
     */
    private var changed = false

    init {
        if (actorIdIn != 0L && activityId != 0L) {
            actorId = actorIdIn
            onNewActivity(activityId, activityDate)
        }
    }

    fun getActorId(): Long {
        return actorId
    }

    /**
     * @return Id of the last downloaded note by this Actor
     */
    fun getLastActivityId(): Long {
        return lastActivityId
    }

    /**
     * @return Sent Date of the last downloaded note by this Actor
     */
    fun getLastActivityDate(): Long {
        return lastActivityDate
    }

    /** If this note is newer than any we got earlier, remember it
     * @param updatedDateIn may be 0 (will be retrieved here)
     */
    fun onNewActivity(activityId: Long, updatedDateIn: Long) {
        if (actorId == 0L || activityId == 0L) {
            return
        }
        var activityDate = updatedDateIn
        if (activityDate == 0L) {
            activityDate = MyQuery.activityIdToLongColumnValue(ActivityTable.UPDATED_DATE, activityId)
        }
        if (activityDate > lastActivityDate) {
            lastActivityDate = activityDate
            lastActivityId = activityId
            changed = true
        }
    }

    /**
     * Persist the info into the Database
     * @return true if succeeded
     */
    fun save(): Boolean {
        MyLog.v(this
        ) {
            ("actorId " + actorId + ": " +
                    MyQuery.actorIdToWebfingerId( MyContextHolder.myContextHolder.getNow(), actorId)
                    + " Latest activity update at " + Date(getLastActivityDate()).toString()
                    + if (changed) "" else " not changed")
        }
        if (!changed) {
            return true
        }

        // As a precaution compare with stored values ones again
        val activityDate = MyQuery.actorIdToLongColumnValue(ActorTable.ACTOR_ACTIVITY_DATE, actorId)
        if (activityDate > lastActivityDate) {
            lastActivityDate = activityDate
            lastActivityId = MyQuery.actorIdToLongColumnValue(ActorTable.ACTOR_ACTIVITY_ID, actorId)
            MyLog.v(this) {
                ("There is newer information in the database. Actor " + actorId + ": "
                        + MyQuery.actorIdToWebfingerId( MyContextHolder.myContextHolder.getNow(), actorId)
                        + " Latest activity at " + Date(getLastActivityDate()).toString())
            }
            changed = false
            return true
        }
        var sql = ""
        try {
            sql += ActorTable.ACTOR_ACTIVITY_ID + "=" + lastActivityId
            sql += ", " + ActorTable.ACTOR_ACTIVITY_DATE + "=" + lastActivityDate
            sql = ("UPDATE " + ActorTable.TABLE_NAME + " SET " + sql
                    + " WHERE " + BaseColumns._ID + "=" + actorId)
            val db: SQLiteDatabase? =  MyContextHolder.myContextHolder.getNow().database
            if (db == null) {
                MyLog.databaseIsNull { "Save $this" }
                return false
            }
            db.execSQL(sql)
            changed = false
        } catch (e: Exception) {
            MyLog.w(this, "save: sql='$sql'", e)
            return false
        }
        return true
    }
}
