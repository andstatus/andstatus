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

package org.andstatus.app.service;

import org.andstatus.app.R;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.ActorActivity;
import org.andstatus.app.data.DataUpdater;
import org.andstatus.app.data.Friendship;
import org.andstatus.app.data.LatestActorActivities;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.OidEnum;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.database.table.ActorTable;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.social.ActivityType;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.Connection;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.RelativeTime;
import org.andstatus.app.util.StringUtils;
import org.andstatus.app.util.TriState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author yvolk@yurivolkov.com
 */
public class CommandExecutorFollowers extends CommandExecutorStrategy {
    long actorId = 0;
    String actorOid = "";
    List<Actor> actorsNew = new ArrayList<>();
    String commandSummary = "";

    @Override
    void execute() {
        commandSummary = execContext.getCommandSummary();
        try {
            TimelineType timelineType = getTimelineType();
            if (lookupActor()) return;
            switch (timelineType) {
                case FOLLOWERS:
                    syncFollowers();
                    break;
                case FRIENDS:
                    syncFriends();
                    break;
                default:
                    MyLog.e(this, "Unexpected timeline or command here: " + timelineType + " - " + commandSummary);
                    break;
            }

            TimelineSyncTracker syncTracker = new TimelineSyncTracker(execContext.getTimeline(), true);
            syncTracker.onTimelineDownloaded();

            MyLog.d(this, commandSummary + " ended, " + actorsNew.size() + " actors");
            logOk(true);
        } catch (ConnectionException e) {
            logConnectionException(e, commandSummary);
        }
    }

    private TimelineType getTimelineType() {
        TimelineType timelineType;
        switch (execContext.getCommandData().getCommand()) {
            case GET_FOLLOWERS:
                timelineType = TimelineType.FOLLOWERS;
                break;
            case GET_FRIENDS:
                timelineType = TimelineType.FRIENDS;
                break;
            default:
                timelineType = execContext.getTimeline().getTimelineType();
                break;
        }
        return timelineType;
    }

    private boolean lookupActor() {
        final String method = "lookupActor";
        actorId = execContext.getCommandData().getActorId();
        actorOid = MyQuery.idToOid(OidEnum.ACTOR_OID, actorId, 0);
        if (StringUtils.isEmpty(actorOid)) {
            execContext.getResult().incrementParseExceptions();
            MyLog.e(this, method + "; actorOid not found for id: " + actorId);
            return true;
        }
        return false;
    }

    private void syncFollowers() throws ConnectionException {
        if (execContext.getMyAccount().getConnection()
                .isApiSupported(Connection.ApiRoutineEnum.GET_FOLLOWERS)) {
            actorsNew = execContext.getMyAccount().getConnection().getFollowers(actorOid);
        } else if (execContext.getMyAccount().getConnection()
                .isApiSupported(Connection.ApiRoutineEnum.GET_FOLLOWERS_IDS)) {
            List<String> actorOidsNew =
                    execContext.getMyAccount().getConnection().getFollowersIds(actorOid);
            if (getActorsForOids(actorOidsNew, actorsNew)) return;
        } else {
            throw new ConnectionException(ConnectionException.StatusCode.UNSUPPORTED_API,
                    Connection.ApiRoutineEnum.GET_FOLLOWERS
                    + " and " + Connection.ApiRoutineEnum.GET_FOLLOWERS_IDS);
        }

        Set<Long> actorIdsOld = MyQuery.getFollowersIds(actorId);
        execContext.getResult().incrementDownloadedCount();
        broadcastProgress(execContext.getContext().getText(R.string.followers).toString()
                + ": " + actorIdsOld.size() + " -> " + actorsNew.size(), false);

        if (updateNewActorsAndTheirLatestActions(actorsNew)) return;

        for (Actor actor : actorsNew) {
            actorIdsOld.remove(actor.actorId);
            Friendship.setFollowed(execContext.myContext, actor.actorId, TriState.TRUE, actorId);
        }
        for (long actorIdOld : actorIdsOld) {
            Friendship.setFollowed(execContext.myContext, actorIdOld, TriState.FALSE, actorId);
        }
    }

    private void syncFriends() throws ConnectionException {
        if (execContext.getMyAccount().getConnection()
                .isApiSupported(Connection.ApiRoutineEnum.GET_FRIENDS)) {
            actorsNew = execContext.getMyAccount().getConnection().getFriends(actorOid);
        } else if (execContext.getMyAccount().getConnection()
                .isApiSupported(Connection.ApiRoutineEnum.GET_FRIENDS_IDS)) {
            List<String> actorOidsNew =
                    execContext.getMyAccount().getConnection().getFriendsIds(actorOid);
            if (getActorsForOids(actorOidsNew, actorsNew)) return;
        } else {
            throw new ConnectionException(ConnectionException.StatusCode.UNSUPPORTED_API,
                    Connection.ApiRoutineEnum.GET_FRIENDS
                            + " and " + Connection.ApiRoutineEnum.GET_FRIENDS_IDS);
        }

        Set<Long> actorIdsOld = MyQuery.getFriendsIds(actorId);
        execContext.getResult().incrementDownloadedCount();
        broadcastProgress(execContext.getContext().getText(R.string.friends).toString()
                + ": " + actorIdsOld.size() + " -> " + actorsNew.size(), false);

        if (updateNewActorsAndTheirLatestActions(actorsNew)) return;

        for (Actor actor : actorsNew) {
            actorIdsOld.remove(actor.actorId);
            Friendship.setFollowed(execContext.myContext, actorId, TriState.TRUE, actor.actorId);
        }
        for (long actorIdOld : actorIdsOld) {
            Friendship.setFollowed(execContext.myContext, actorId, TriState.FALSE, actorIdOld);
        }
    }

    private boolean getActorsForOids(List<String> actorOidsNew, List<Actor> actorsNew) {
        long count = 0;
        for (String actorOidNew : actorOidsNew) {
            Actor actor = null;
            try {
                count++;
                actor = execContext.getMyAccount().getConnection().getActor(actorOidNew, null);
                execContext.getResult().incrementDownloadedCount();
            } catch (ConnectionException e) {
                long actorId = MyQuery.oidToId(OidEnum.ACTOR_OID,
                        execContext.getMyAccount().getOriginId(), actorOidNew);
                if (actorId == 0) {
                    MyLog.i(this, "Failed to identify an Actor for oid=" + actorOidNew, e);
                } else {
                    actor = Actor.fromOriginAndActorOid(execContext.getMyAccount().getOrigin(), actorOidNew);
                    actor.actorId = actorId;
                    actor.setWebFingerId(MyQuery.actorIdToWebfingerId(actorId));
                    MyLog.v(this, "Server doesn't return Actor object for " + actor , e);
                }
            }
            if (actor != null) {
                broadcastProgress(String.valueOf(count) + ". "
                        + execContext.getContext().getText(R.string.get_user)
                        + ": " + actor.getNamePreferablyWebFingerId(), true);
                actorsNew.add(actor);
            }
            if (logSoftErrorIfStopping()) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return true if we need to interrupt process
     */
    private boolean updateNewActorsAndTheirLatestActions(List<Actor> actorsNew) {
        DataUpdater dataUpdater = new DataUpdater(execContext);
        boolean allNotesLoaded = true;
        long count = 0;
        final Actor myAccountActor = execContext.getMyAccount().getActor();
        for (Actor actor : actorsNew) {
            count++;
            broadcastProgress(String.valueOf(count) + ". " + execContext.getContext().getText(R.string.button_save)
                    + ": " + actor.getNamePreferablyWebFingerId(), true);
            dataUpdater.onActivity(myAccountActor.update(actor), false);
            if (!actor.hasLatestNote()) {
                allNotesLoaded = false;
            }
        }
        dataUpdater.saveLum();
        if (!allNotesLoaded) {
            count = 0;
            for (Actor actor : actorsNew) {
                if (actor.hasLatestNote()) {
                    continue;
                }
                count++;
                ConnectionException e1 = null;
                try {
                    broadcastProgress(String.valueOf(count) + ". "
                            + execContext.getContext().getText(R.string.title_command_get_status)
                            + ": " + actor.getNamePreferablyWebFingerId(), true);
                    dataUpdater.downloadOneNoteBy(actor.oid);
                    execContext.getResult().incrementDownloadedCount();
                } catch (ConnectionException e) {
                    e1 = e;
                }
                long lastActivityId = MyQuery.actorIdToLongColumnValue(ActorTable.ACTOR_ACTIVITY_ID, actor.actorId);
                if (lastActivityId == 0) {
                    lastActivityId = MyQuery.conditionToLongColumnValue(execContext.getMyContext().getDatabase(),
                            "getLatestActivity",
                            ActivityTable.TABLE_NAME,
                            ActivityTable._ID,
                            ActivityTable.ACTOR_ID + "=" + actor.actorId
                                    + " AND " + ActivityTable.ACTIVITY_TYPE + " IN("
                                    + ActivityType.FOLLOW.id + ","
                                    + ActivityType.CREATE.id + ","
                                    + ActivityType.UPDATE.id + ","
                                    + ActivityType.ANNOUNCE.id + ","
                                    + ActivityType.LIKE.id + ")"
                                    + " ORDER BY " + ActivityTable.UPDATED_DATE + " DESC LIMIT 1");
                    if (lastActivityId == 0) {
                        MyLog.v(this, "Failed to find Actor's activity for "
                                + actor.getNamePreferablyWebFingerId(), e1);
                    } else {
                        long updatedDate = MyQuery.idToLongColumnValue(
                                execContext.getMyContext().getDatabase(),
                                ActivityTable.TABLE_NAME,
                                ActivityTable.UPDATED_DATE,
                                lastActivityId);
                        LatestActorActivities lum = new LatestActorActivities();
                        lum.onNewActorActivity(new ActorActivity(actor.actorId, lastActivityId, updatedDate));
                        lum.save();
                        MyLog.v(this, "Server didn't return Actor's activity for "
                                        + actor.getNamePreferablyWebFingerId()
                                        + " found activity " + RelativeTime.
                                        getDifference(MyContextHolder.get().context(), updatedDate),
                                e1);
                    }
                }
                if (logSoftErrorIfStopping()) {
                    return true;
                }
            }
        }
        return false;
    }
}
