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
package org.andstatus.app.service

import android.provider.BaseColumns
import io.vavr.control.Try
import org.andstatus.app.R
import org.andstatus.app.actor.GroupType
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.data.ActorActivity
import org.andstatus.app.data.DataUpdater
import org.andstatus.app.data.GroupMembership
import org.andstatus.app.data.LatestActorActivities
import org.andstatus.app.data.MyQuery
import org.andstatus.app.data.OidEnum
import org.andstatus.app.database.table.ActivityTable
import org.andstatus.app.database.table.ActorTable
import org.andstatus.app.net.http.ConnectionException
import org.andstatus.app.net.http.ConnectionException.StatusCode
import org.andstatus.app.net.social.ActivityType
import org.andstatus.app.net.social.Actor
import org.andstatus.app.net.social.ApiRoutineEnum
import org.andstatus.app.net.social.InputActorPage
import org.andstatus.app.net.social.TimelinePosition
import org.andstatus.app.service.CommandEnum
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.RelativeTime
import org.andstatus.app.util.StringUtil
import org.andstatus.app.util.TriState
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer

/**
 * @author yvolk@yurivolkov.com
 */
class CommandExecutorFollowers(execContext: CommandExecutionContext?) : CommandExecutorStrategy(execContext) {
    var commandSummary: String? = ""
    public override fun execute(): Try<Boolean?>? {
        commandSummary = execContext.commandSummary
        if (StringUtil.isEmpty(actor.oid)) {
            return onParseException("No actorOid not for: $actor")
        }
        val command = execContext.commandData.command
        return getNewActors(command)
                .onSuccess(Consumer { actorsNew: MutableList<Actor?>? -> updateGroupMemberships(command, actorsNew) })
                .map { actorsNew: MutableList<Actor?>? ->
                    val syncTracker = TimelineSyncTracker(execContext.timeline, true)
                    syncTracker.onTimelineDownloaded()
                    MyLog.d(this, commandSummary + " ended, " + actorsNew.size + " actors")
                    true
                }
    }

    private fun getNewActors(command: CommandEnum?): Try<MutableList<Actor?>?>? {
        val apiActors = if (command == CommandEnum.GET_FOLLOWERS) ApiRoutineEnum.GET_FOLLOWERS else ApiRoutineEnum.GET_FRIENDS
        return if (isApiSupported(apiActors)) {
            getNewActors(apiActors)
        } else {
            val apiIds = if (command == CommandEnum.GET_FOLLOWERS) ApiRoutineEnum.GET_FOLLOWERS_IDS else ApiRoutineEnum.GET_FRIENDS_IDS
            if (isApiSupported(apiIds)) {
                connection
                        .getFriendsOrFollowersIds(apiIds, actor.oid)
                        .flatMap { actorOidsNew: MutableList<String?>? -> getActorsForOids(actorOidsNew) }
            } else {
                Try.failure(ConnectionException(StatusCode.UNSUPPORTED_API,
                        "$apiActors and $apiIds"))
            }
        }
    }

    private fun getNewActors(apiActors: ApiRoutineEnum?): Try<MutableList<Actor?>?>? {
        val actors: MutableList<Actor?> = ArrayList()
        val requested: MutableList<TimelinePosition?> = ArrayList()
        val positionToRequest = AtomicReference<TimelinePosition?>(TimelinePosition.Companion.EMPTY)
        for (pageNum in 0..99) {
            if (requested.contains(positionToRequest.get())) break
            val tried = connection.getFriendsOrFollowers(apiActors, positionToRequest.get(), actor)
            if (tried.isFailure) return tried.map { page: InputActorPage? -> page.items }
            requested.add(positionToRequest.get())
            tried.onSuccess { page: InputActorPage? ->
                actors.addAll(page.items)
                if (page.firstPosition.nonEmpty() && !requested.contains(page.firstPosition)) {
                    positionToRequest.set(page.firstPosition)
                } else if (page.olderPosition.nonEmpty() && !requested.contains(page.olderPosition)) {
                    positionToRequest.set(page.olderPosition)
                }
            }
        }
        return Try.success(actors)
    }

    private fun getActorsForOids(actorOidsNew: MutableList<String?>?): Try<MutableList<Actor?>?>? {
        val actorsNew: MutableList<Actor?> = ArrayList()
        val count = AtomicLong()
        for (actorOidNew in actorOidsNew) {
            connection.getActor(Actor.Companion.fromOid(execContext.myAccount.origin, actorOidNew)).map { actor: Actor? ->
                count.incrementAndGet()
                execContext.result.incrementDownloadedCount()
                actor
            }.recover(Exception::class.java) { e: Exception? ->
                val actorId = MyQuery.oidToId(OidEnum.ACTOR_OID,
                        execContext.myAccount.originId, actorOidNew)
                if (actorId == 0L) {
                    MyLog.i(this, "Failed to identify an Actor for oid=$actorOidNew", e)
                    return@recover Actor.Companion.EMPTY
                } else {
                    val actor: Actor = Actor.Companion.fromTwoIds(execContext.myAccount.origin,
                            GroupType.UNKNOWN, actorId, actorOidNew)
                    actor.webFingerId = MyQuery.actorIdToWebfingerId(execContext.myContext, actorId)
                    MyLog.v(this, "Server doesn't return Actor object for $actor", e)
                    return@recover actor
                }
            }
                    .onSuccess { actor: Actor? ->
                        broadcastProgress(count.toString() + ". "
                                + execContext.context.getText(R.string.get_user)
                                + ": " + actor.getUniqueNameWithOrigin(), true)
                        actorsNew.add(actor)
                    }
            if (logSoftErrorIfStopping()) {
                return Try.failure(Exception(execContext.result.message))
            }
        }
        return Try.success(actorsNew)
    }

    private fun updateGroupMemberships(command: CommandEnum?, actorsNew: MutableList<Actor?>?) {
        val groupType = if (command == CommandEnum.GET_FOLLOWERS) GroupType.FOLLOWERS else GroupType.FRIENDS
        val actionStringRes = if (groupType == GroupType.FOLLOWERS) R.string.followers else R.string.friends
        val actorIdsOld: MutableSet<Long?> = GroupMembership.Companion.getGroupMemberIds(execContext.myContext, actor.actorId, groupType)
        execContext.result.incrementDownloadedCount()
        broadcastProgress(execContext.context.getText(actionStringRes).toString()
                + ": " + actorIdsOld.size + " -> " + actorsNew.size, false)
        val loadLatestNotes = actorsNew.size < 30 // TODO: Too long...
        if (loadLatestNotes && !areAllNotesLoaded(actorsNew)) {
            if (updateNewActorsAndTheirLatestActions(actorsNew)) return
        } else {
            val dataUpdater = DataUpdater(execContext)
            for (actor in actorsNew) {
                val activity = execContext.myAccount.actor.update(actor)
                dataUpdater.onActivity(activity)
            }
        }
        for (actor in actorsNew) {
            actorIdsOld.remove(actor.actorId)
            GroupMembership.Companion.setMember(execContext.myContext, getActor(), groupType, TriState.TRUE, actor)
        }
        for (actorIdOld in actorIdsOld) {
            GroupMembership.Companion.setMember(execContext.myContext, actor, groupType,
                    TriState.FALSE, Actor.Companion.load(execContext.myContext, actorIdOld))
        }
        execContext.myContext.users().reload(actor)
    }

    private fun areAllNotesLoaded(actorsNew: MutableList<Actor?>?): Boolean {
        val dataUpdater = DataUpdater(execContext)
        var allNotesLoaded = true
        var count: Long = 0
        val myAccountActor = execContext.myAccount.actor
        for (actor in actorsNew) {
            count++
            broadcastProgress(count.toString() + ". " + execContext.context.getText(R.string.button_save)
                    + ": " + actor.getUniqueNameWithOrigin(), true)
            dataUpdater.onActivity(myAccountActor.update(actor), false)
            if (!actor.hasLatestNote()) {
                allNotesLoaded = false
            }
        }
        dataUpdater.saveLum()
        return allNotesLoaded
    }

    /**
     * @return true if we need to interrupt process
     */
    private fun updateNewActorsAndTheirLatestActions(actorsNew: MutableList<Actor?>?): Boolean {
        val dataUpdater = DataUpdater(execContext)
        var count: Long = 0
        for (actor in actorsNew) {
            if (actor.hasLatestNote()) continue
            count++
            var exception: Exception? = null
            try {
                broadcastProgress(count.toString() + ". "
                        + execContext.context.getText(R.string.title_command_get_status)
                        + ": " + actor.getUniqueNameWithOrigin(), true)
                dataUpdater.downloadOneNoteBy(actor)
                execContext.result.incrementDownloadedCount()
            } catch (e: Exception) {
                exception = e
            }
            var lastActivityId = MyQuery.actorIdToLongColumnValue(ActorTable.ACTOR_ACTIVITY_ID, actor.actorId)
            if (lastActivityId == 0L) {
                lastActivityId = MyQuery.conditionToLongColumnValue(execContext.getMyContext().database,
                        "getLatestActivity",
                        ActivityTable.TABLE_NAME,
                        BaseColumns._ID,
                        ActivityTable.ACTOR_ID + "=" + actor.actorId
                                + " AND " + ActivityTable.ACTIVITY_TYPE + " IN("
                                + ActivityType.FOLLOW.id + ","
                                + ActivityType.CREATE.id + ","
                                + ActivityType.UPDATE.id + ","
                                + ActivityType.ANNOUNCE.id + ","
                                + ActivityType.LIKE.id + ")"
                                + " ORDER BY " + ActivityTable.UPDATED_DATE + " DESC LIMIT 1")
                if (lastActivityId == 0L) {
                    MyLog.v(this, "Failed to find Actor's activity for "
                            + actor.getUniqueNameWithOrigin(), exception)
                } else {
                    val updatedDate = MyQuery.idToLongColumnValue(
                            execContext.getMyContext().database,
                            ActivityTable.TABLE_NAME,
                            ActivityTable.UPDATED_DATE,
                            lastActivityId)
                    val lum = LatestActorActivities()
                    lum.onNewActorActivity(ActorActivity(actor.actorId, lastActivityId, updatedDate))
                    lum.save()
                    MyLog.v(this, "Server didn't return Actor's activity for "
                            + actor.getUniqueNameWithOrigin()
                            + " found activity " + RelativeTime.getDifference(MyContextHolder.Companion.myContextHolder.getNow().context(), updatedDate),
                            exception)
                }
            }
            if (logSoftErrorIfStopping()) {
                return true
            }
        }
        return false
    }
}