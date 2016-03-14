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
        boolean ok = false;
        boolean errorLogged = false;

        List<String> userOidsNew = new ArrayList<>();
        LatestUserMessages lum = new LatestUserMessages();

        DataInserter di = new DataInserter(execContext);
        if (execContext.getMyAccount().getConnection()
                .isApiSupported(Connection.ApiRoutineEnum.GET_FOLLOWERS)) {
            List<MbUser> usersNew =
                    execContext.getMyAccount().getConnection().getUsersFollowing(userOid);
            for (MbUser user : usersNew) {
                userOidsNew.add(user.oid);
                di.insertOrUpdateUser(user, lum);
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
        for (String userOidNew : userOidsNew) {
            long userIdNew = MyQuery.oidToId(MyDatabase.OidEnum.USER_OID,
                    execContext.getMyAccount().getOriginId(), userOidNew);
            long msgId = 0;
            if (userIdNew != 0) {
                msgId = MyQuery.userIdToLongColumnValue(MyDatabase.User.USER_MSG_ID, userIdNew);
            }
            // The User doesn't have any messages sent, so let's download the latest
            if (msgId == 0) {
                try {
                    // Download the Users's info + optionally his latest message
                    if (userIdNew == 0 || execContext.getMyAccount().getConnection().userObjectHasMessage()) {
                        MbUser mbUser = execContext.getMyAccount().getConnection().getUser(userOidNew, null);
                        userIdNew = di.insertOrUpdateUser(mbUser, lum);
                        msgId = MyQuery.userIdToLongColumnValue(MyDatabase.User.USER_MSG_ID, userIdNew);
                    }
                    if (userIdNew != 0 && msgId == 0) {
                        di.downloadOneMessageBy(userOidNew, lum);
                    }
                } catch (ConnectionException e) {
                    MyLog.i(this, "Failed to download the User object or his message for oid=" + userOidNew, e);
                }
            }
            if (userIdNew != 0) {
                userIdsOld.remove(userIdNew);
                FollowingUserValues fu = new FollowingUserValues(userIdNew, userId);
                fu.setFollowed(true);
                fu.update(db);
            }
            if (logSoftErrorIfStopping()) {
                return;
            }
        }

        lum.save();

        // Now let's remove "following" information for all users left in the Set:
        for (long notFollowingId : userIdsOld) {
            FollowingUserValues fu = new FollowingUserValues(notFollowingId, userId);
            fu.setFollowed(false);
            fu.update(db);
        }

        logOk(ok || !errorLogged);
        MyLog.d(this, method + (ok ? "succeeded" : "failed")
                + ", " + userOidsNew  + " followers, id=" + userId);
    }
}
