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

import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.DataInserter;
import org.andstatus.app.data.FollowingUserValues;
import org.andstatus.app.data.LatestUserMessages;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.social.Connection;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.util.MyLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author yvolk@yurivolkov.com
 */
public class CommandExecutorFollowers extends CommandExecutorStrategy {
    long userId = 0;
    String userOid = "";

    @Override
    void execute() {
        MyLog.v(this, execContext.getCommandData().toString());
        if (!lookupUser()) {
            return;
        }
        try {
            getFollowers();
        } catch (ConnectionException e) {
            logConnectionException(e, "Getting followers for id:" + userId);
        }
    }

    private boolean lookupUser() {
        final String method = "getFollowers";
        boolean ok = true;
        userId = execContext.getCommandData().itemId;
        userOid = MyQuery.idToOid(MyDatabase.OidEnum.USER_OID, userId, 0);
        if (TextUtils.isEmpty(userOid)) {
            ok = false;
            execContext.getResult().incrementParseExceptions();
            MyLog.e(this, method + "; userOid not found for ID: " + userId);
        }
        return ok;
    }

    private void getFollowers() throws ConnectionException {
        final String method = "getFollowers";

        List<String> userOidsNew = new ArrayList<>();
        List<Long> userIdsNew = new ArrayList<>();
        LatestUserMessages lum = new LatestUserMessages();

        boolean usersLoaded = false;
        boolean messagesLoaded = false;
        DataInserter di = new DataInserter(execContext);
        if (execContext.getMyAccount().getConnection()
                .isApiSupported(Connection.ApiRoutineEnum.GET_FOLLOWERS)) {
            usersLoaded = true;
            List<MbUser> usersNew =
                    execContext.getMyAccount().getConnection().getUsersFollowing(userOid);
            for (MbUser mbUser : usersNew) {
                userOidsNew.add(mbUser.oid);
                userIdsNew.add(di.insertOrUpdateUser(mbUser, lum));
                if (mbUser.hasLatestMessage()) {
                    messagesLoaded = true;
                }
            }
        } else if (execContext.getMyAccount().getConnection()
                .isApiSupported(Connection.ApiRoutineEnum.GET_FOLLOWERS_IDS)) {
            userOidsNew = execContext.getMyAccount().getConnection().getIdsOfUsersFollowing(userOid);
        } else {
            throw new ConnectionException(ConnectionException.StatusCode.UNSUPPORTED_API,
                    Connection.ApiRoutineEnum.GET_FOLLOWERS
                    + " and " + Connection.ApiRoutineEnum.GET_FOLLOWERS_IDS);
        }
        Set<Long> userIdsOld = MyQuery.getIdsOfUsersFollowing(userId);
		execContext.getResult().incrementDownloadedCount();

        SQLiteDatabase db = MyContextHolder.get().getDatabase();
        if (db == null) {
            MyLog.v(this, "Database is null");
            return;
        }

        if (!usersLoaded) {
            for (String userOidNew : userOidsNew) {
                try {
                    MbUser mbUser = execContext.getMyAccount().getConnection().getUser(userOidNew, null);
                    if (mbUser.hasLatestMessage()) {
                        messagesLoaded = true;
                    }
                    userIdsNew.add(di.insertOrUpdateUser(mbUser, lum));
                } catch (ConnectionException e) {
                    MyLog.i(this, "Failed to download User object for oid=" + userOidNew, e);
                }
                if (logSoftErrorIfStopping()) {
                    return;
                }
            }
        }

        if (!messagesLoaded) {
            for (String userOidNew : userOidsNew) {
                try {
                    di.downloadOneMessageBy(userOidNew, lum);
                } catch (ConnectionException e) {
                    MyLog.i(this, "Failed to download User's message for oid=" + userOidNew, e);
                }
                if (logSoftErrorIfStopping()) {
                    return;
                }
            }
        }

        // Set "follow" flag for all new users
        for (long userIdNew : userIdsNew) {
            userIdsOld.remove(userIdNew);
            FollowingUserValues fu = new FollowingUserValues(userIdNew, userId);
            fu.setFollowed(true);
            fu.update(db);
        }
        lum.save();

        // Remove "following" information for all old users, who are not in the new list:
        for (long userIdOld : userIdsOld) {
            FollowingUserValues fu = new FollowingUserValues(userIdOld, userId);
            fu.setFollowed(false);
            fu.update(db);
        }

        logOk(true);
        MyLog.d(this, method + "ended, " + userIdsNew.size() + " followers of id=" + userId);
    }
}
