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
package org.andstatus.app.data.checker

import android.provider.BaseColumns
import org.andstatus.app.data.ActorSql
import org.andstatus.app.data.DbUtils
import org.andstatus.app.data.MyProvider
import org.andstatus.app.database.table.ActorTable
import org.andstatus.app.net.social.Actor
import org.andstatus.app.user.User
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.StringUtil
import org.andstatus.app.util.TriState
import java.util.*
import java.util.stream.IntStream

/**
 * @author yvolk@yurivolkov.com
 */
internal class CheckUsers : DataChecker() {
    private class CheckResults {
        var actorsToMergeUsers: MutableMap<String, MutableSet<Actor>> = HashMap()
        var usersToSave: MutableSet<User> = HashSet()
        var actorsWithoutUsers: MutableSet<Actor> = HashSet()
        var actorsToFixWebFingerId: MutableSet<Actor> = HashSet()
        var actorsWithoutOrigin: MutableSet<Actor> = HashSet()
        var problems: MutableList<String> = ArrayList()
    }

    override fun fixInternal(): Long {
        val results = getResults()
        logResults(results)
        if (countOnly) return results.problems.size.toLong()
        var changedCount: Long = 0
        for (user in results.usersToSave) {
            user.save(myContext)
            changedCount++
        }
        changedCount += results.actorsToMergeUsers.values.stream().mapToLong { actors: MutableSet<Actor> -> mergeUsers(actors) }.sum()
        for (actor in results.actorsWithoutOrigin) {
            val parent = actor.getParent()
            if (parent.nonEmpty) {
                // The error affects development database only
                val groupUsername: String = actor.groupType.name + ".of." + parent.getUsername() + "." + parent.actorId
                val groupTempOid = StringUtil.toTempOid(groupUsername)
                val sql = "UPDATE " + ActorTable.TABLE_NAME + " SET " +
                        ActorTable.ORIGIN_ID + "=" + parent.origin.id + ", " +
                        ActorTable.USERNAME + "='" + groupUsername + "', " +
                        ActorTable.ACTOR_OID + "='" + groupTempOid + "'" +
                        " WHERE " + BaseColumns._ID + "=" + actor.actorId
                myContext.getDatabase()?.execSQL(sql)
                changedCount++
            } else {
                MyLog.w(this, "Couldn't fix origin for $actor")
            }
        }
        for (actor in results.actorsToFixWebFingerId) {
            val sql = ("UPDATE " + ActorTable.TABLE_NAME + " SET " + ActorTable.WEBFINGER_ID + "='"
                    + actor.getWebFingerId() + "' WHERE " + BaseColumns._ID + "=" + actor.actorId)
            myContext.getDatabase()?.execSQL(sql)
            changedCount++
        }
        for (actor1 in results.actorsWithoutUsers) {
            val actor = actor1.lookupUser()
            actor.saveUser()
            val sql = ("UPDATE " + ActorTable.TABLE_NAME + " SET " + ActorTable.USER_ID + "="
                    + actor.user.userId + " WHERE " + BaseColumns._ID + "=" + actor.actorId)
            myContext.getDatabase()?.execSQL(sql)
            changedCount++
        }
        return changedCount
    }

    private fun logResults(results: CheckResults) {
        if (results.problems.isEmpty()) {
            MyLog.d(this, "No problems found")
            return
        }
        MyLog.i(this, "Problems found: " + results.problems.size)
        IntStream.range(0, results.problems.size)
                .mapToObj { i: Int -> (i + 1).toString() + ". " + results.problems[i] }
                .forEachOrdered { s: String? -> MyLog.i(this, s) }
    }

    private fun getResults(): CheckResults {
        val results = CheckResults()
        val sql = ("SELECT " + ActorSql.selectFullProjection()
                + " FROM " + ActorSql.tables(isFullProjection = true, userOnly = false, userIsOptional = true)
                + " ORDER BY " + ActorTable.WEBFINGER_ID + " COLLATE NOCASE")
        var rowsCount: Long = 0
        MyLog.v(this) { sql }
        myContext.getDatabase()?.rawQuery(sql, null).use { c ->
            var key = ""
            var actors: MutableSet<Actor> = HashSet()
            while (c?.moveToNext() == true) {
                rowsCount++
                val actor: Actor = Actor.fromCursor(myContext, c, false)
                val webFingerId = DbUtils.getString(c, ActorTable.WEBFINGER_ID)
                if (actor.isWebFingerIdValid() && actor.getWebFingerId() != webFingerId) {
                    results.actorsToFixWebFingerId.add(actor)
                    results.problems.add("Fix webfingerId: '$webFingerId' $actor")
                }
                if (myContext.accounts().fromWebFingerId(actor.getWebFingerId()).isValid
                        && actor.user.isMyUser().untrue) {
                    actor.user.setIsMyUser(TriState.TRUE)
                    results.usersToSave.add(actor.user)
                    results.problems.add("Fix user isMy: $actor")
                } else if (actor.user.userId == 0L) {
                    results.actorsWithoutUsers.add(actor)
                    results.problems.add("Fix userId==0: $actor")
                }
                if (actor.origin.isEmpty) {
                    results.actorsWithoutOrigin.add(actor)
                    results.problems.add("Fix no Origin: $actor")
                }
                if (key.isEmpty() || actor.getWebFingerId() != key) {
                    if (shouldMerge(actors)) {
                        results.actorsToMergeUsers[key] = actors
                        results.problems.add("Fix merge users 1 \"$key\": $actors")
                    }
                    key = actor.getWebFingerId()
                    actors = HashSet()
                }
                actors.add(actor)
            }
            if (shouldMerge(actors)) {
                results.actorsToMergeUsers[key] = actors
                results.problems.add("Fix merge users 2 \"$key\": $actors")
            }
        }
        logger.logProgress("Check completed, " + rowsCount + " actors checked." +
                " Users of actors to be merged: " + results.actorsToMergeUsers.size +
                ", to fix WebfingerId: " + results.actorsToFixWebFingerId.size +
                ", to add users: " + results.actorsWithoutUsers.size +
                ", to save users: " + results.usersToSave.size
        )
        return results
    }

    private fun shouldMerge(actors: MutableSet<Actor>): Boolean {
        return actors.size > 1 && (actors.stream().anyMatch { a: Actor -> a.user.userId == 0L }
                || actors.stream().mapToLong { a: Actor -> a.user.userId }.distinct().count() > 1)
    }

    private fun mergeUsers(actors: MutableSet<Actor>): Long {
        val userWith = actors.stream().map { a: Actor -> a.user }.reduce { a: User, b: User -> if (a.userId < b.userId) a else b }
                .orElse(User.EMPTY)
        return if (userWith.userId == 0L) 0 else actors.stream().map { actor: Actor ->
            val logMsg = "Linking $actor with $userWith"
            if (logger.loggedMoreSecondsAgoThan(5)) logger.logProgress(logMsg)
            MyProvider.update(myContext, ActorTable.TABLE_NAME,
                    ActorTable.USER_ID + "=" + userWith.userId,
                    BaseColumns._ID + "=" + actor.actorId
            )
            if (actor.user.userId != 0L && actor.user.userId != userWith.userId && userWith.isMyUser().isTrue) {
                actor.user.setIsMyUser(TriState.FALSE)
                actor.user.save(myContext)
            }
            1
        }.count()
    }
}