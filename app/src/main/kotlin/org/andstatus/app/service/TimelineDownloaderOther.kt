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
package org.andstatus.app.service

import io.vavr.control.Try
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.data.DataUpdater
import org.andstatus.app.net.http.ConnectionException
import org.andstatus.app.net.http.ConnectionException.StatusCode
import org.andstatus.app.net.social.Actor
import org.andstatus.app.net.social.InputTimelinePage
import org.andstatus.app.net.social.TimelinePosition
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.RelativeTime
import org.andstatus.app.util.TriState
import java.util.*
import java.util.concurrent.TimeUnit

internal class TimelineDownloaderOther(execContext: CommandExecutionContext) : TimelineDownloader(execContext) {
    override fun download(): Try<Boolean> {
        if (!getTimeline().isSyncable()) {
            return Try.failure(IllegalArgumentException("Timeline cannot be synced: ${getTimeline()}"))
        }
        val syncTracker = TimelineSyncTracker(getTimeline(), isSyncYounger())
        val hours = MyPreferences.getDontSynchronizeOldNotes()
        var downloadingLatest = false
        if (hours > 0 && RelativeTime.moreSecondsAgoThan(syncTracker.getPreviousSyncedDate(),
                        TimeUnit.HOURS.toSeconds(hours))) {
            downloadingLatest = true
            syncTracker.clearPosition()
        } else if (syncTracker.getPreviousPosition().isEmpty) {
            downloadingLatest = true
        }
        val tryActor = getActorWithOid()
        val toDownload = if (downloadingLatest) LATEST_NOTES_TO_DOWNLOAD_MAX else
            if (isSyncYounger()) YOUNGER_NOTES_TO_DOWNLOAD_MAX else OLDER_NOTES_TO_DOWNLOAD_MAX
        var positionToRequest = syncTracker.getPreviousPosition()
        if (MyLog.isDebugEnabled()) {
            var strLog = ("Loading "
                    + (if (downloadingLatest) "latest " else "")
                    + execContext.commandData.toCommandSummary(execContext.myContext))
            if (syncTracker.getPreviousItemDate() > 0) {
                strLog += ("; last Timeline item at=" + Date(syncTracker.getPreviousItemDate()).toString()
                        + "; last time downloaded at=" + Date(syncTracker.getPreviousSyncedDate()).toString())
            }
            strLog += "; Position to request: " + positionToRequest.getPosition()
            MyLog.d(TAG, strLog)
        }
        syncTracker.onTimelineDownloaded()
        val dataUpdater = DataUpdater(execContext)
        for (loopCounter in 0..99) {
            val limit = getConnection().fixedDownloadLimit(
                    toDownload, getTimeline().timelineType.connectionApiRoutine)
            syncTracker.onPositionRequested(positionToRequest)
            var tryPage: Try<InputTimelinePage>
            tryPage = when (getTimeline().timelineType) {
                TimelineType.SEARCH -> getConnection().searchNotes(isSyncYounger(),
                        if (isSyncYounger()) positionToRequest else TimelinePosition.EMPTY,
                        if (isSyncYounger()) TimelinePosition.EMPTY else positionToRequest,
                        limit, getTimeline().getSearchQuery())
                else -> {
                    val positionToRequest2 = positionToRequest
                    tryActor.flatMap { actor: Actor ->
                        getConnection().getTimeline(isSyncYounger(),
                                getTimeline().timelineType.connectionApiRoutine,
                                if (isSyncYounger()) positionToRequest2 else TimelinePosition.EMPTY,
                                if (isSyncYounger()) TimelinePosition.EMPTY else positionToRequest2,
                                limit, actor)
                    }
                }
            }
            if (tryPage.isSuccess()) {
                val page = tryPage.get()
                syncTracker.onNewPage(page)
                for (activity in page.items) {
                    syncTracker.onNewActivity(activity.getUpdatedDate(), activity.getPrevTimelinePosition(),
                            activity.getNextTimelinePosition())
                    if (activity.isSubscribedByMe() != TriState.FALSE
                            && activity.getUpdatedDate() > 0 && execContext.getTimeline().timelineType.isSubscribedByMe()
                            && execContext.myContext.users().isMe(execContext.getTimeline().actor)) {
                        activity.setSubscribedByMe(TriState.TRUE)
                    }
                    dataUpdater.onActivity(activity, false)
                }
                val optPositionToRequest = syncTracker.getNextPositionToRequest()
                if (toDownload - syncTracker.getDownloadedCounter() <= 0 || !optPositionToRequest.isPresent) {
                    break
                }
                positionToRequest = optPositionToRequest.get()
            }
            if (tryPage.isFailure()) {
                if (ConnectionException.of(tryPage.getCause()).getStatusCode() != StatusCode.NOT_FOUND) {
                    return Try.failure(tryPage.getCause())
                }
                val optPositionToRequest = syncTracker.onNotFound()
                if (!optPositionToRequest.isPresent) {
                    return Try.failure(ConnectionException.fromStatusCode(StatusCode.NOT_FOUND,
                            "Timeline was not found at " + syncTracker.requestedPositions))
                }
                MyLog.d(TAG, "Trying default timeline position")
                positionToRequest = optPositionToRequest.get()
            }
        }
        dataUpdater.saveLum()
        return Try.success(true)
    }

    private fun getActorWithOid(): Try<Actor> {
        if (getActor().actorId == 0L) {
            if (getTimeline().myAccountToSync.isValid) {
                return Try.success(getTimeline().myAccountToSync.actor)
            }
        } else {
            val actor: Actor = Actor.load(execContext.myContext, getActor().actorId)
            return if (actor.oid.isEmpty()) {
                Try.failure(ConnectionException("No ActorOid for $actor, timeline:${getTimeline()}"))
            } else Try.success(actor)
        }
        return if (getTimeline().timelineType.isForUser()) {
            Try.failure(ConnectionException("No actor for the timeline:${getTimeline()}"))
        } else Try.success(Actor.EMPTY)
    }

    companion object {
        private val TAG: String = TimelineDownloaderOther::class.java.simpleName
        private const val YOUNGER_NOTES_TO_DOWNLOAD_MAX = 200
        private const val OLDER_NOTES_TO_DOWNLOAD_MAX = 40
        private const val LATEST_NOTES_TO_DOWNLOAD_MAX = 20
    }
}
