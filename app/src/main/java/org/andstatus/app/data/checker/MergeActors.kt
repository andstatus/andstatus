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
package org.andstatus.app.data.checker

import android.provider.BaseColumns
import org.andstatus.app.data.MyProvider
import org.andstatus.app.database.table.ActivityTable
import org.andstatus.app.database.table.ActorTable
import org.andstatus.app.database.table.AudienceTable
import org.andstatus.app.database.table.NoteTable
import org.andstatus.app.net.social.AActivity
import org.andstatus.app.net.social.ActivityType
import org.andstatus.app.net.social.Actor
import org.andstatus.app.util.MyLog
import java.util.*
import java.util.concurrent.ConcurrentSkipListSet
import java.util.stream.IntStream

/**
 * @author yvolk@yurivolkov.com
 */
internal class MergeActors : DataChecker() {
    public override fun fixInternal(): Long {
        var changedCount = 0
        val actorsToMerge: MutableList<AActivity?> = ArrayList(getActorsToMerge())
        for (activity in actorsToMerge) {
            MyLog.d(this, "Problems found: " + actorsToMerge.size)
            IntStream.range(0, actorsToMerge.size)
                    .mapToObj { i: Int ->
                        (Integer.toString(i) + ". To merge " + actorsToMerge[i].getObjActor()
                                + " with " + actorsToMerge[i].getActor())
                    }
                    .forEachOrdered { s: String? -> MyLog.d(this, s) }
            if (!countOnly) {
                mergeActor(activity)
            }
            changedCount++
        }
        return changedCount
    }

    private fun getActorsToMerge(): MutableSet<AActivity?>? {
        val method = "getActorsToMerge"
        val mergeActivities: MutableSet<AActivity?> = ConcurrentSkipListSet()
        val sql = ("SELECT " + BaseColumns._ID
                + ", " + ActorTable.ORIGIN_ID
                + ", " + ActorTable.ACTOR_OID
                + ", " + ActorTable.WEBFINGER_ID
                + " FROM " + ActorTable.TABLE_NAME
                + " ORDER BY " + ActorTable.ORIGIN_ID
                + ", " + ActorTable.ACTOR_OID)
        var rowsCount: Long = 0
        myContext.database.rawQuery(sql, null).use { c ->
            var prev: Actor? = null
            while (c.moveToNext()) {
                rowsCount++
                val actor: Actor = Actor.Companion.fromOid(myContext.origins().fromId(c.getLong(1)),
                        c.getString(2))
                actor.actorId = c.getLong(0)
                actor.webFingerId = c.getString(3)
                prev = if (isTheSameActor(prev, actor)) {
                    val activity = whomToMerge(prev, actor)
                    mergeActivities.add(activity)
                    activity.actor
                } else {
                    actor
                }
            }
        }
        logger.logProgress(method + " ended, " + rowsCount + " actors, " + mergeActivities.size + " to be merged")
        return mergeActivities
    }

    private fun isTheSameActor(prev: Actor?, actor: Actor?): Boolean {
        if (prev == null || actor == null) {
            return false
        }
        if (prev.origin != actor.origin) {
            return false
        }
        return if (prev.oid != actor.oid) {
            false
        } else true
    }

    private fun whomToMerge(prev: Actor, actor: Actor): AActivity {
        val activity: AActivity = AActivity.Companion.from(Actor.EMPTY, ActivityType.UPDATE)
        activity.setObjActor(actor)
        var mergeWith = prev
        if (myContext.accounts().fromActorId(actor.actorId).isValid) {
            mergeWith = actor
            activity.setObjActor(prev)
        }
        activity.setActor(mergeWith)
        return activity
    }

    private fun mergeActor(activity: AActivity?) {
        val logMsg = "Merging " + activity.getObjActor() + " into " + activity.getActor()
        logger.logProgress(logMsg)
        updateColumn(logMsg, activity, ActivityTable.TABLE_NAME, ActivityTable.ACTOR_ID, false)
        updateColumn(logMsg, activity, ActivityTable.TABLE_NAME, ActivityTable.OBJ_ACTOR_ID, false)
        updateColumn(logMsg, activity, NoteTable.TABLE_NAME, NoteTable.AUTHOR_ID, false)
        updateColumn(logMsg, activity, NoteTable.TABLE_NAME, NoteTable.IN_REPLY_TO_ACTOR_ID, false)
        updateColumn(logMsg, activity, AudienceTable.TABLE_NAME, AudienceTable.ACTOR_ID, true)
        MyProvider.Companion.deleteActor(myContext, activity.getObjActor().actorId)
    }

    private fun updateColumn(logMsg: String?, activity: AActivity?, tableName: String?, column: String?, ignoreError: Boolean) {
        var sql = ""
        try {
            sql = ("UPDATE "
                    + tableName
                    + " SET "
                    + column + "=" + activity.getActor().actorId
                    + " WHERE "
                    + column + "=" + activity.getObjActor().actorId)
            myContext.database.execSQL(sql)
        } catch (e: Exception) {
            if (!ignoreError) {
                logger.logProgress("Error: " + e.message + ", SQL:" + sql)
                MyLog.e(this, "$logMsg, SQL:$sql", e)
            }
        }
    }
}