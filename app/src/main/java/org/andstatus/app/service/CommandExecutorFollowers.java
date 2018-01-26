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

import android.text.TextUtils;

import org.andstatus.app.R;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.DataUpdater;
import org.andstatus.app.data.FriendshipValues;
import org.andstatus.app.data.LatestUserActivities;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.OidEnum;
import org.andstatus.app.data.UserActivity;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.database.table.UserTable;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.social.Connection;
import org.andstatus.app.net.social.MbActivityType;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.RelativeTime;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author yvolk@yurivolkov.com
 */
public class CommandExecutorFollowers extends CommandExecutorStrategy {
    long userId = 0;
    String userOid = "";
    List<MbUser> usersNew = new ArrayList<>();
    String commandSummary = "";

    @Override
    void execute() {
        commandSummary = execContext.getCommandSummary();
        try {
            TimelineType timelineType = getTimelineType();
            if (lookupUser()) return;
            switch (timelineType) {
                case FOLLOWERS:
                case MY_FOLLOWERS:
                    syncFollowers();
                    break;
                case FRIENDS:
                case MY_FRIENDS:
                    syncFriends();
                    break;
                default:
                    MyLog.e(this, "Unexpected timeline or command here: " + timelineType + " - " + commandSummary);
                    break;
            }

            TimelineSyncTracker syncTracker = new TimelineSyncTracker(execContext.getTimeline(), true);
            syncTracker.onTimelineDownloaded();

            MyLog.d(this, commandSummary + " ended, " + usersNew.size() + " users");
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

    private boolean lookupUser() {
        final String method = "lookupUser";
        userId = execContext.getCommandData().getUserId();
        userOid = MyQuery.idToOid(OidEnum.USER_OID, userId, 0);
        if (TextUtils.isEmpty(userOid)) {
            execContext.getResult().incrementParseExceptions();
            MyLog.e(this, method + "; userOid not found for id: " + userId);
            return true;
        }
        return false;
    }

    private void syncFollowers() throws ConnectionException {
        if (execContext.getMyAccount().getConnection()
                .isApiSupported(Connection.ApiRoutineEnum.GET_FOLLOWERS)) {
            usersNew = execContext.getMyAccount().getConnection().getFollowers(userOid);
        } else if (execContext.getMyAccount().getConnection()
                .isApiSupported(Connection.ApiRoutineEnum.GET_FOLLOWERS_IDS)) {
            List<String> userOidsNew =
                    execContext.getMyAccount().getConnection().getFollowersIds(userOid);
            if (getUsersForOids(userOidsNew, usersNew)) return;
        } else {
            throw new ConnectionException(ConnectionException.StatusCode.UNSUPPORTED_API,
                    Connection.ApiRoutineEnum.GET_FOLLOWERS
                    + " and " + Connection.ApiRoutineEnum.GET_FOLLOWERS_IDS);
        }

        Set<Long> userIdsOld = MyQuery.getFollowersIds(userId);
        execContext.getResult().incrementDownloadedCount();
        broadcastProgress(execContext.getContext().getText(R.string.followers).toString()
                + ": " + userIdsOld.size() + " -> " + usersNew.size(), false);

        if (updateNewUsersAndTheirLatestMessages(usersNew)) return;

        for (MbUser mbUser : usersNew) {
            userIdsOld.remove(mbUser.userId);
            FriendshipValues.setFollowed(mbUser.userId, userId);
        }
        for (long userIdOld : userIdsOld) {
            FriendshipValues.setNotFollowed(userIdOld, userId);
        }
    }

    private void syncFriends() throws ConnectionException {
        if (execContext.getMyAccount().getConnection()
                .isApiSupported(Connection.ApiRoutineEnum.GET_FRIENDS)) {
            usersNew = execContext.getMyAccount().getConnection().getFriends(userOid);
        } else if (execContext.getMyAccount().getConnection()
                .isApiSupported(Connection.ApiRoutineEnum.GET_FRIENDS_IDS)) {
            List<String> userOidsNew =
                    execContext.getMyAccount().getConnection().getFriendsIds(userOid);
            if (getUsersForOids(userOidsNew, usersNew)) return;
        } else {
            throw new ConnectionException(ConnectionException.StatusCode.UNSUPPORTED_API,
                    Connection.ApiRoutineEnum.GET_FRIENDS
                            + " and " + Connection.ApiRoutineEnum.GET_FRIENDS_IDS);
        }

        Set<Long> userIdsOld = MyQuery.getFriendsIds(userId);
        execContext.getResult().incrementDownloadedCount();
        broadcastProgress(execContext.getContext().getText(R.string.friends).toString()
                + ": " + userIdsOld.size() + " -> " + usersNew.size(), false);

        if (updateNewUsersAndTheirLatestMessages(usersNew)) return;

        for (MbUser mbUser : usersNew) {
            userIdsOld.remove(mbUser.userId);
            FriendshipValues.setFollowed(userId, mbUser.userId);
        }
        for (long userIdOld : userIdsOld) {
            FriendshipValues.setNotFollowed(userId, userIdOld);
        }
    }

    private boolean getUsersForOids(List<String> userOidsNew, List<MbUser> usersNew) {
        long count = 0;
        for (String userOidNew : userOidsNew) {
            MbUser mbUser = null;
            try {
                count++;
                mbUser = execContext.getMyAccount().getConnection().getUser(userOidNew, null);
                execContext.getResult().incrementDownloadedCount();
            } catch (ConnectionException e) {
                long userId = MyQuery.oidToId(OidEnum.USER_OID,
                        execContext.getMyAccount().getOriginId(), userOidNew);
                if (userId == 0) {
                    MyLog.i(this, "Failed to identify a User for oid=" + userOidNew, e);
                } else {
                    mbUser = MbUser.fromOriginAndUserOid(execContext.getMyAccount().getOrigin(), userOidNew);
                    mbUser.userId = userId;
                    mbUser.setWebFingerId(MyQuery.userIdToWebfingerId(userId));
                    MyLog.v(this, "Server doesn't return User object for " + mbUser , e);
                }
            }
            if (mbUser != null) {
                broadcastProgress(String.valueOf(count) + ". "
                        + execContext.getContext().getText(R.string.get_user)
                        + ": " + mbUser.getNamePreferablyWebFingerId(), true);
                usersNew.add(mbUser);
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
    private boolean updateNewUsersAndTheirLatestMessages(List<MbUser> usersNew) {
        DataUpdater di = new DataUpdater(execContext);
        boolean allMessagesLoaded = true;
        long count = 0;
        for (MbUser user : usersNew) {
            count++;
            broadcastProgress(String.valueOf(count) + ". " + execContext.getContext().getText(R.string.button_save)
                    + ": " + user.getNamePreferablyWebFingerId(), true);
            di.onActivity(user.update(execContext.getMyAccount().getUser()), false);
            if (!user.hasLatestMessage()) {
                allMessagesLoaded = false;
            }
        }
        di.saveLum();
        if (!allMessagesLoaded) {
            count = 0;
            for (MbUser mbUser : usersNew) {
                if (mbUser.hasLatestMessage()) {
                    continue;
                }
                count++;
                ConnectionException e1 = null;
                try {
                    broadcastProgress(String.valueOf(count) + ". "
                            + execContext.getContext().getText(R.string.title_command_get_status)
                            + ": " + mbUser.getNamePreferablyWebFingerId(), true);
                    di.downloadOneMessageBy(mbUser.oid);
                    execContext.getResult().incrementDownloadedCount();
                } catch (ConnectionException e) {
                    e1 = e;
                }
                long lastActivityId = MyQuery.userIdToLongColumnValue(UserTable.USER_ACTIVITY_ID, mbUser.userId);
                if (lastActivityId == 0) {
                    lastActivityId = MyQuery.conditionToLongColumnValue(execContext.getMyContext().getDatabase(),
                            "getLatestActivity",
                            ActivityTable.TABLE_NAME,
                            ActivityTable._ID,
                            ActivityTable.ACTOR_ID + "=" + mbUser.userId
                                    + " AND " + ActivityTable.ACTIVITY_TYPE + " IN("
                                    + MbActivityType.FOLLOW.id + ","
                                    + MbActivityType.CREATE.id + ","
                                    + MbActivityType.UPDATE.id + ","
                                    + MbActivityType.ANNOUNCE.id + ","
                                    + MbActivityType.LIKE.id + ")"
                                    + " ORDER BY " + ActivityTable.UPDATED_DATE + " DESC LIMIT 1");
                    if (lastActivityId == 0) {
                        MyLog.v(this, "Failed to find User's activity for "
                                + mbUser.getNamePreferablyWebFingerId(), e1);
                    } else {
                        long updatedDate = MyQuery.idToLongColumnValue(
                                execContext.getMyContext().getDatabase(),
                                ActivityTable.TABLE_NAME,
                                ActivityTable.UPDATED_DATE,
                                lastActivityId);
                        LatestUserActivities lum = new LatestUserActivities();
                        lum.onNewUserActivity(new UserActivity(mbUser.userId, lastActivityId, updatedDate));
                        lum.save();
                        MyLog.v(this, "Server didn't return User's activity for "
                                        + mbUser.getNamePreferablyWebFingerId()
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
