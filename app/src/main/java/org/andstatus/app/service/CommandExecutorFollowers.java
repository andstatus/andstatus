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

import org.andstatus.app.data.DataInserter;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.social.Connection;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.util.MyLog;

import java.util.List;

/**
 * @author yvolk@yurivolkov.com
 */
public class CommandExecutorFollowers extends CommandExecutorStrategy {
    long userId = 0;
    String oid = "";

    @Override
    void execute() {
        MyLog.v(this, execContext.getCommandData().toString());
        if (!lookupUser()) {
            return;
        }
        if (execContext.getMyAccount().getConnection().isApiSupported(Connection.ApiRoutineEnum.GET_FOLLOWERS_IDS)) {
            getFollowersByIds();
        } else {
            getFollowers();
        }

    }

    private boolean lookupUser() {
        final String method = "getFollowers";
        boolean ok = true;
        userId = execContext.getCommandData().itemId;
        oid = MyQuery.idToOid(MyDatabase.OidEnum.USER_OID, userId, 0);
        if (TextUtils.isEmpty(oid)) {
            ok = false;
            execContext.getResult().incrementParseExceptions();
            MyLog.e(this, method + "; userOid not found for ID: " + userId);
        }
        return ok;
    }

    private void getFollowersByIds() {
        final String method = "getFollowersByIds";
        boolean ok = false;
        List<String> ids = null;
        boolean errorLogged = false;
        long counter = 0;
        try {
            ids = execContext.getMyAccount().getConnection().getIdsOfUsersFollowing(oid);
            ok = true;
        } catch (ConnectionException e) {
            errorLogged = true;
            logConnectionException(e, method + "; " + oid);
        }
        if (ok) {
            for (String followerOid : ids) {
                long id = MyQuery.oidToId(MyDatabase.OidEnum.USER_OID,
                        execContext.getMyAccount().getOriginId(), followerOid);
                counter++;
                MyLog.v(this, method + "; " + counter + ". " + "User oid=" + followerOid + ", id=" + id
                        + " is following id=" + userId);
                //new DataInserter(execContext).insertOrUpdateUser(user);
            }
        }
        logOk(ok || !errorLogged);
        MyLog.d(this, method + (ok ? "succeeded" : "failed")
                + ", " + counter + " followers, id=" + userId);
    }

    private void getFollowers() {
        final String method = "getFollowers";
        boolean ok = false;
        List<MbUser> users = null;
        boolean errorLogged = false;
        try {
            users = execContext.getMyAccount().getConnection().getUsersFollowing(oid);
            ok = !users.isEmpty();
        } catch (ConnectionException e) {
            errorLogged = true;
            logConnectionException(e, method + "; " + oid);
        }
        if (ok) {
            for (MbUser user : users) {
                new DataInserter(execContext).insertOrUpdateUser(user);
            }
        }
        logOk(ok || !errorLogged);
        MyLog.d(this, method + (ok ? "succeeded" : "failed") + ", id=" + userId);
    }

}
