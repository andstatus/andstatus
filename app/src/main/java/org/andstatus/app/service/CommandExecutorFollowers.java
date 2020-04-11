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
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.RelativeTime;
import org.andstatus.app.util.StringUtil;
import org.andstatus.app.util.TriState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import io.vavr.control.Try;

/**
 * @author yvolk@yurivolkov.com
 */
public class CommandExecutorFollowers extends CommandExecutorStrategy {
    String commandSummary = "";

    public CommandExecutorFollowers(CommandExecutionContext execContext) {
        super(execContext);
    }

    @Override
    Try<Boolean> execute() {
        commandSummary = execContext.getCommandSummary();
        if (StringUtil.isEmpty(getActor().oid)) {
            return onParseException("No actorOid not for: " + getActor());
        }

        CommandEnum command = execContext.getCommandData().getCommand();
        return getNewActors(command)
        .onSuccess(actorsNew -> updateGroupMemberships(command, actorsNew))
        .map(actorsNew -> {
            TimelineSyncTracker syncTracker = new TimelineSyncTracker(execContext.getTimeline(), true);
            syncTracker.onTimelineDownloaded();

            MyLog.d(this, commandSummary + " ended, " + actorsNew.size() + " actors");
            return true;
        });
    }

    private Try<List<Actor>> getNewActors(CommandEnum command) {
        Connection.ApiRoutineEnum apiActors = command == CommandEnum.GET_FOLLOWERS
                ? Connection.ApiRoutineEnum.GET_FOLLOWERS : Connection.ApiRoutineEnum.GET_FRIENDS;
        if (isApiSupported(apiActors)) {
            return getConnection().getFriendsOrFollowers(apiActors, getActor());
        } else {
            Connection.ApiRoutineEnum apiIds = command == CommandEnum.GET_FOLLOWERS
                    ? Connection.ApiRoutineEnum.GET_FOLLOWERS_IDS : Connection.ApiRoutineEnum.GET_FRIENDS_IDS;
            if (isApiSupported(apiIds)) {
                return getConnection()
                    .getFriendsOrFollowersIds(apiIds, getActor().oid)
                    .flatMap(this::getActorsForOids);
            } else {
                return Try.failure(new ConnectionException(ConnectionException.StatusCode.UNSUPPORTED_API,
                        apiActors + " and " + apiIds));
            }
        }
    }

    private Try<List<Actor>> getActorsForOids(List<String> actorOidsNew) {
        List<Actor> actorsNew = new ArrayList<>();
        AtomicLong count = new AtomicLong();
        for (String actorOidNew : actorOidsNew) {
            getConnection().getActor(Actor.fromOid(execContext.getMyAccount().getOrigin(), actorOidNew)).map(actor -> {
                count.incrementAndGet();
                execContext.getResult().incrementDownloadedCount();
                return actor;
            }).recover(Exception.class, e -> {
                long actorId = MyQuery.oidToId(OidEnum.ACTOR_OID,
                        execContext.getMyAccount().getOriginId(), actorOidNew);
                if (actorId == 0) {
                    MyLog.i(this, "Failed to identify an Actor for oid=" + actorOidNew, e);
                    return Actor.EMPTY;
                } else {
                    Actor actor = Actor.fromTwoIds(execContext.getMyAccount().getOrigin(),
                            GroupType.UNKNOWN, actorId, actorOidNew);
                    actor.setWebFingerId(MyQuery.actorIdToWebfingerId(execContext.myContext, actorId));
                    MyLog.v(this, "Server doesn't return Actor object for " + actor , e);
                    return actor;
                }
            })
            .onSuccess(actor -> {
                broadcastProgress(count + ". "
                        + execContext.getContext().getText(R.string.get_user)
                        + ": " + actor.getUniqueNameWithOrigin(), true);
                actorsNew.add(actor);
            });
            if (logSoftErrorIfStopping()) {
                return Try.failure(new Exception(execContext.getResult().getMessage()));
            }
        }
        return Try.success(actorsNew);
    }

    private void updateGroupMemberships(CommandEnum command, List<Actor> actorsNew) {
        GroupType groupType = command == CommandEnum.GET_FOLLOWERS
                ? GroupType.FOLLOWERS : GroupType.FRIENDS;
        int actionStringRes = command == CommandEnum.GET_FOLLOWERS ? R.string.followers : R.string.friends;
        Set<Long> actorIdsOld = GroupMembership.getGroupMemberIds(execContext.myContext, getActor().actorId, groupType);
        execContext.getResult().incrementDownloadedCount();
        broadcastProgress(execContext.getContext().getText(actionStringRes).toString()
                + ": " + actorIdsOld.size() + " -> " + actorsNew.size(), false);

        if (!areAllNotesLoaded(actorsNew)) {
            if (updateNewActorsAndTheirLatestActions(actorsNew)) return;
        }

        for (Actor actor : actorsNew) {
            actorIdsOld.remove(actor.actorId);
            GroupMembership.setMember(execContext.myContext, getActor(), groupType, TriState.TRUE, actor);
        }
        for (long actorIdOld : actorIdsOld) {
            GroupMembership.setMember(execContext.myContext, getActor(), groupType,
                    TriState.FALSE, Actor.load(execContext.myContext, actorIdOld));
        }
        execContext.myContext.users().reload(getActor());
    }

    private boolean areAllNotesLoaded(List<Actor> actorsNew) {
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
        return allNotesLoaded;
    }

    /**
     * @return true if we need to interrupt process
     */
    private boolean updateNewActorsAndTheirLatestActions(List<Actor> actorsNew) {
        DataUpdater dataUpdater = new DataUpdater(execContext);
        long count = 0;
        for (Actor actor : actorsNew) {
            if (actor.hasLatestNote()) continue;

            count++;
            Exception exception = null;
            try {
                broadcastProgress(String.valueOf(count) + ". "
                        + execContext.getContext().getText(R.string.title_command_get_status)
                        + ": " + actor.getUniqueNameWithOrigin(), true);
                dataUpdater.downloadOneNoteBy(actor);
                execContext.getResult().incrementDownloadedCount();
            } catch (Exception e) {
                exception = e;
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
                            + actor.getUniqueNameWithOrigin(), exception);
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
                            exception);
                }
            }
            if (logSoftErrorIfStopping()) {
                return true;
            }
        }
        return false;
    }
}
