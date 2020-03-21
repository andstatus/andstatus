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
import org.andstatus.app.actor.GroupType;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.ActorActivity;
import org.andstatus.app.data.DataUpdater;
import org.andstatus.app.data.GroupMembership;
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
import org.andstatus.app.util.StringUtil;
import org.andstatus.app.util.TriState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author yvolk@yurivolkov.com
 */
public class CommandExecutorFollowers extends CommandExecutorStrategy {
    List<Actor> actorsNew = new ArrayList<>();
    String commandSummary = "";

    public CommandExecutorFollowers(CommandExecutionContext execContext) {
        super(execContext);
    }

    @Override
    void execute() {
        commandSummary = execContext.getCommandSummary();
        try {
            TimelineType timelineType = getTimelineType();
            if (isActorOidEmpty()) return;

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

    private boolean isActorOidEmpty() {
        if (StringUtil.isEmpty(getActor().oid)) {
            execContext.getResult().incrementParseExceptions();
            MyLog.e(this, "No actorOid not for: " + getActor());
            return true;
        }
        return false;
    }

    private void syncFollowers() throws ConnectionException {
        if (isApiSupported(Connection.ApiRoutineEnum.GET_FOLLOWERS)) {
            actorsNew = getConnection().getFollowers(getActor());
        } else if (isApiSupported(Connection.ApiRoutineEnum.GET_FOLLOWERS_IDS)) {
            List<String> actorOidsNew = getConnection().getFollowersIds(getActor().oid);
            if (getActorsForOids(actorOidsNew, actorsNew)) return;
        } else {
            throw new ConnectionException(ConnectionException.StatusCode.UNSUPPORTED_API,
                    Connection.ApiRoutineEnum.GET_FOLLOWERS
                    + " and " + Connection.ApiRoutineEnum.GET_FOLLOWERS_IDS);
        }

        Set<Long> actorIdsOld = GroupMembership.getGroupMemberIds(execContext.myContext, getActor().actorId, GroupType.FOLLOWERS);
        execContext.getResult().incrementDownloadedCount();
        broadcastProgress(execContext.getContext().getText(R.string.followers).toString()
                + ": " + actorIdsOld.size() + " -> " + actorsNew.size(), false);

        if (updateNewActorsAndTheirLatestActions(actorsNew)) return;

        for (Actor actor : actorsNew) {
            actorIdsOld.remove(actor.actorId);
            GroupMembership.setMember(execContext.myContext, getActor(), GroupType.FOLLOWERS, TriState.TRUE, actor);
        }
        for (long actorIdOld : actorIdsOld) {
            GroupMembership.setMember(execContext.myContext, getActor(), GroupType.FOLLOWERS,
                    TriState.FALSE, Actor.load(execContext.myContext, actorIdOld));
        }
        execContext.myContext.users().reload(getActor());
    }

    private void syncFriends() throws ConnectionException {
        if (isApiSupported(Connection.ApiRoutineEnum.GET_FRIENDS)) {
            actorsNew = getConnection().getFriends(getActor());
        } else if (isApiSupported(Connection.ApiRoutineEnum.GET_FRIENDS_IDS)) {
            List<String> actorOidsNew = getConnection().getFriendsIds(getActor().oid);
            if (getActorsForOids(actorOidsNew, actorsNew)) return;
        } else {
            throw new ConnectionException(ConnectionException.StatusCode.UNSUPPORTED_API,
                    Connection.ApiRoutineEnum.GET_FRIENDS
                            + " and " + Connection.ApiRoutineEnum.GET_FRIENDS_IDS);
        }

        Set<Long> actorIdsOld = GroupMembership.getGroupMemberIds(execContext.myContext, getActor().actorId, GroupType.FRIENDS);
        execContext.getResult().incrementDownloadedCount();
        broadcastProgress(execContext.getContext().getText(R.string.friends).toString()
                + ": " + actorIdsOld.size() + " -> " + actorsNew.size(), false);

        if (updateNewActorsAndTheirLatestActions(actorsNew)) return;

        for (Actor actor : actorsNew) {
            actorIdsOld.remove(actor.actorId);
            GroupMembership.setMember(execContext.myContext, getActor(), GroupType.FRIENDS, TriState.TRUE, actor);
        }
        for (long actorIdOld : actorIdsOld) {
            GroupMembership.setMember(execContext.myContext, getActor(), GroupType.FRIENDS,
                    TriState.FALSE, Actor.load(execContext.myContext, actorIdOld));
        }
        execContext.myContext.users().reload(getActor());
    }

    private boolean getActorsForOids(List<String> actorOidsNew, List<Actor> actorsNew) {
        long count = 0;
        for (String actorOidNew : actorOidsNew) {
            Actor actor = null;
            try {
                count++;
                actor = getConnection().getActor(Actor.fromOid(execContext.getMyAccount().getOrigin(), actorOidNew));
                execContext.getResult().incrementDownloadedCount();
            } catch (ConnectionException e) {
                long actorId = MyQuery.oidToId(OidEnum.ACTOR_OID,
                        execContext.getMyAccount().getOriginId(), actorOidNew);
                if (actorId == 0) {
                    MyLog.i(this, "Failed to identify an Actor for oid=" + actorOidNew, e);
                } else {
                    actor = Actor.fromTwoIds(execContext.getMyAccount().getOrigin(),
                            GroupType.UNKNOWN, actorId, actorOidNew);
                    actor.setWebFingerId(MyQuery.actorIdToWebfingerId(execContext.myContext, actorId));
                    MyLog.v(this, "Server doesn't return Actor object for " + actor , e);
                }
            }
            if (actor != null) {
                broadcastProgress(String.valueOf(count) + ". "
                        + execContext.getContext().getText(R.string.get_user)
                        + ": " + actor.getUniqueNameWithOrigin(), true);
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
                    + ": " + actor.getUniqueNameWithOrigin(), true);
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
                            + ": " + actor.getUniqueNameWithOrigin(), true);
                    dataUpdater.downloadOneNoteBy(actor);
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
                                + actor.getUniqueNameWithOrigin(), e1);
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
                                        + actor.getUniqueNameWithOrigin()
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
