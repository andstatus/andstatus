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

import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.DataUpdater;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.http.ConnectionException.StatusCode;
import org.andstatus.app.net.social.AActivity;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.TimelinePosition;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.RelativeTime;
import org.andstatus.app.util.StringUtil;
import org.andstatus.app.util.TriState;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;

class TimelineDownloaderOther extends TimelineDownloader {
    private static final int YOUNGER_NOTES_TO_DOWNLOAD_MAX = 200;
    private static final int OLDER_NOTES_TO_DOWNLOAD_MAX = 40;
    private static final int LATEST_NOTES_TO_DOWNLOAD_MAX = 20;

    TimelineDownloaderOther(CommandExecutionContext execContext) {
        super(execContext);
    }

    @Override
    public void download() throws ConnectionException {
        if (!getTimeline().isSyncable()) {
            throw new IllegalArgumentException("Timeline cannot be synced: " + getTimeline());
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
        
        if (MyLog.isLoggable(this, MyLog.DEBUG)) {
            String strLog = "Loading "
            + (downloadingLatest ? "latest " : "")
            + execContext.getCommandData().toCommandSummary(execContext.getMyContext());
            if (syncTracker.getPreviousItemDate() > 0) { strLog +=
                "; last Timeline item at=" + (new Date(syncTracker.getPreviousItemDate()).toString())
                + "; last time downloaded at=" +  (new Date(syncTracker.getPreviousSyncedDate()).toString());
            }
            MyLog.d(this, strLog);
        }
        Actor actor = getActorWithOid();
        int toDownload = downloadingLatest ? LATEST_NOTES_TO_DOWNLOAD_MAX :
                (isSyncYounger() ? YOUNGER_NOTES_TO_DOWNLOAD_MAX : OLDER_NOTES_TO_DOWNLOAD_MAX);
        TimelinePosition previousPosition = syncTracker.getPreviousPosition();
        syncTracker.onTimelineDownloaded();

        DataUpdater dataUpdater = new DataUpdater(execContext);
        for (int loopCounter=0; loopCounter < 100; loopCounter++ ) {
            try {
                int limit = getConnection().fixedDownloadLimit(
                        toDownload, getTimeline().getTimelineType().getConnectionApiRoutine());
                List<AActivity> activities;
                switch (getTimeline().getTimelineType()) {
                    case SEARCH:
                        activities = getConnection().searchNotes(
                                isSyncYounger() ? previousPosition : TimelinePosition.EMPTY,
                                isSyncYounger() ? TimelinePosition.EMPTY : previousPosition,
                                limit, getTimeline().getSearchQuery());
                        break;
                    default:
                        activities = getConnection().getTimeline(
                                getTimeline().getTimelineType().getConnectionApiRoutine(),
                                isSyncYounger() ? previousPosition : TimelinePosition.EMPTY,
                                isSyncYounger() ? TimelinePosition.EMPTY : previousPosition,
                                limit, actor);
                        break;
                }
                for (AActivity activity : activities) {
                    toDownload--;
                    syncTracker.onNewMsg(activity.getTimelinePosition(), activity.getUpdatedDate());
                    if (!activity.isSubscribedByMe().equals(TriState.FALSE)
                        && activity.getUpdatedDate() > 0
                        && execContext.getTimeline().getTimelineType().isSubscribedByMe()
                        && execContext.myContext.users().isMe(execContext.getTimeline().actor)
                            ) {
                        activity.setSubscribedByMe(TriState.TRUE);
                    }
                    dataUpdater.onActivity(activity, false);
                }
                if (toDownload <= 0 || activities.isEmpty() || previousPosition.equals(syncTracker.getPreviousPosition())) {
                    break;
                }
                previousPosition = syncTracker.getPreviousPosition();
            } catch (ConnectionException e) {
                if (e.getStatusCode() != StatusCode.NOT_FOUND) {
                    throw e;
                }
                if (previousPosition.isEmpty()) {
                    throw ConnectionException.hardConnectionException("No last position", e);
                }
                MyLog.d(this, "The timeline was not found, last position='" + previousPosition +"'", e);
                previousPosition = TimelinePosition.EMPTY;
            }
        }
        dataUpdater.saveLum();
    }

    @NonNull
    private Actor getActorWithOid() throws ConnectionException {
        if (getActor().actorId == 0) {
            if (getTimeline().myAccountToSync.isValid()) {
                return getTimeline().myAccountToSync.getActor();
            }
        } else {
            Actor actor = Actor.load(execContext.myContext, getActor().actorId);
            if (StringUtil.isEmpty(actor.oid)) {
                throw new ConnectionException("No ActorOid for " + actor + ", timeline:" + getTimeline());
            }
            return actor;
        }
        if (getTimeline().getTimelineType().isForUser()) {
            throw new ConnectionException("No actor for the timeline:" + getTimeline());
        }
        return Actor.EMPTY;
    }
}
