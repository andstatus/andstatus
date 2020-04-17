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

package org.andstatus.app.service;

import androidx.annotation.NonNull;

import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.DataUpdater;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.http.ConnectionException.StatusCode;
import org.andstatus.app.net.social.AActivity;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.InputTimelinePage;
import org.andstatus.app.net.social.TimelinePosition;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.RelativeTime;
import org.andstatus.app.util.StringUtil;
import org.andstatus.app.util.TriState;

import java.util.Date;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import io.vavr.control.Try;

class TimelineDownloaderOther extends TimelineDownloader {
    private static final int YOUNGER_NOTES_TO_DOWNLOAD_MAX = 200;
    private static final int OLDER_NOTES_TO_DOWNLOAD_MAX = 40;
    private static final int LATEST_NOTES_TO_DOWNLOAD_MAX = 20;

    TimelineDownloaderOther(CommandExecutionContext execContext) {
        super(execContext);
    }

    @Override
    public Try<Boolean> download() {
        if (!getTimeline().isSyncable()) {
            return Try.failure(new IllegalArgumentException("Timeline cannot be synced: " + getTimeline()));
        }

        TimelineSyncTracker syncTracker = new TimelineSyncTracker(getTimeline(), isSyncYounger());
        long hours = MyPreferences.getDontSynchronizeOldNotes();
        boolean downloadingLatest = false;
        if (hours > 0 && RelativeTime.moreSecondsAgoThan(syncTracker.getPreviousSyncedDate(),
                TimeUnit.HOURS.toSeconds(hours))) {
            downloadingLatest = true;
            syncTracker.clearPosition();
        } else if (syncTracker.getPreviousPosition().isEmpty()) {
            downloadingLatest = true;
        }

        Try<Actor> tryActor = getActorWithOid();
        int toDownload = downloadingLatest ? LATEST_NOTES_TO_DOWNLOAD_MAX :
                (isSyncYounger() ? YOUNGER_NOTES_TO_DOWNLOAD_MAX : OLDER_NOTES_TO_DOWNLOAD_MAX);
        TimelinePosition positionToRequest = syncTracker.getPreviousPosition();
        if (MyLog.isLoggable(this, MyLog.DEBUG)) {
            String strLog = "Loading "
            + (downloadingLatest ? "latest " : "")
            + execContext.getCommandData().toCommandSummary(execContext.getMyContext());
            if (syncTracker.getPreviousItemDate() > 0) { strLog +=
                "; last Timeline item at=" + (new Date(syncTracker.getPreviousItemDate()).toString())
                + "; last time downloaded at=" +  (new Date(syncTracker.getPreviousSyncedDate()).toString());
            }
            strLog += "; Position to request: " + positionToRequest.getPosition();
            MyLog.d(this, strLog);
        }
        syncTracker.onTimelineDownloaded();

        DataUpdater dataUpdater = new DataUpdater(execContext);
        for (int loopCounter=0; loopCounter < 100; loopCounter++ ) {
                int limit = getConnection().fixedDownloadLimit(
                        toDownload, getTimeline().getTimelineType().getConnectionApiRoutine());
                syncTracker.onPositionRequested(positionToRequest);
                Try<InputTimelinePage> tryPage;
                switch (getTimeline().getTimelineType()) {
                    case SEARCH:
                        tryPage = getConnection().searchNotes(isSyncYounger(),
                                isSyncYounger() ? positionToRequest : TimelinePosition.EMPTY,
                                isSyncYounger() ? TimelinePosition.EMPTY : positionToRequest,
                                limit, getTimeline().getSearchQuery());
                        break;
                    default:
                        TimelinePosition positionToRequest2 = positionToRequest;
                        tryPage = tryActor.flatMap(actor ->
                                getConnection().getTimeline(isSyncYounger(),
                                getTimeline().getTimelineType().getConnectionApiRoutine(),
                                isSyncYounger() ? positionToRequest2 : TimelinePosition.EMPTY,
                                isSyncYounger() ? TimelinePosition.EMPTY : positionToRequest2,
                                limit, actor));
                        break;
                }
                if (tryPage.isSuccess()) {
                    InputTimelinePage page = tryPage.get();
                    syncTracker.onNewPage(page);
                    for (AActivity activity : page.activities) {
                        syncTracker.onNewActivity(activity.getTimelinePosition(), activity.getUpdatedDate());
                        if (!activity.isSubscribedByMe().equals(TriState.FALSE)
                                && activity.getUpdatedDate() > 0
                                && execContext.getTimeline().getTimelineType().isSubscribedByMe()
                                && execContext.myContext.users().isMe(execContext.getTimeline().actor)
                        ) {
                            activity.setSubscribedByMe(TriState.TRUE);
                        }
                        dataUpdater.onActivity(activity, false);
                    }
                    Optional<TimelinePosition> optPositionToRequest = syncTracker.getNextPositionToRequest();
                    if ( toDownload - syncTracker.getDownloadedCounter() <= 0 || !optPositionToRequest.isPresent()) {
                        break;
                    }
                    positionToRequest = optPositionToRequest.get();
                }

                if (tryPage.isFailure()) {
                    if (ConnectionException.of(tryPage.getCause()).getStatusCode() != StatusCode.NOT_FOUND) {
                        return Try.failure(tryPage.getCause());
                    }
                    Optional<TimelinePosition> optPositionToRequest = syncTracker.onNotFound();
                    if (!optPositionToRequest.isPresent()) {
                        return Try.failure(ConnectionException.fromStatusCode(StatusCode.NOT_FOUND,
                                "Timeline was not found at " + syncTracker.requestedPositions));
                    }
                    MyLog.d(this, "Trying default timeline position");
                    positionToRequest = optPositionToRequest.get();
                }
        }
        dataUpdater.saveLum();
        return Try.success(true);
    }

    @NonNull
    private Try<Actor> getActorWithOid() {
        if (getActor().actorId == 0) {
            if (getTimeline().myAccountToSync.isValid()) {
                return Try.success(getTimeline().myAccountToSync.getActor());
            }
        } else {
            Actor actor = Actor.load(execContext.myContext, getActor().actorId);
            if (StringUtil.isEmpty(actor.oid)) {
                return Try.failure(new ConnectionException("No ActorOid for " + actor + ", timeline:" + getTimeline()));
            }
            return Try.success(actor);
        }
        if (getTimeline().getTimelineType().isForUser()) {
            return Try.failure(new ConnectionException("No actor for the timeline:" + getTimeline()));
        }
        return Try.success(Actor.EMPTY);
    }
}
