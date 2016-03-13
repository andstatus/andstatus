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

import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.DataInserter;
import org.andstatus.app.data.FollowingUserValues;
import org.andstatus.app.data.LatestTimelineItem;
import org.andstatus.app.data.LatestUserMessages;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyDatabase.OidEnum;
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.http.ConnectionException.StatusCode;
import org.andstatus.app.net.social.Connection.ApiRoutineEnum;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.util.MyLog;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

class TimelineDownloaderFollowing extends TimelineDownloader {
    private long userId;
    private String userOid;

    @Override
    public void download() throws ConnectionException {
        userId = execContext.getTimelineUserId();
        userOid = MyQuery.idToOid(OidEnum.USER_OID, userId, 0);
        if (TextUtils.isEmpty(userOid)) {
            MyLog.d(this, "userOid is Empty." + execContext);
            execContext.getResult().incrementParseExceptions();
        } else {
            downloadFollowed();
        }
    }

    private void downloadFollowed() throws ConnectionException {
        LatestTimelineItem latestTimelineItem = new LatestTimelineItem(execContext.getTimelineType(), userId);
        
        if (MyLog.isLoggable(this, MyLog.DEBUG)) {
            String strLog = "Loading " + execContext.getTimelineType() + "; account=" + execContext.getMyAccount().getAccountName();
            strLog += "; user='" + MyQuery.userIdToWebfingerId(userId) + "', oid=" + userOid;
            if (latestTimelineItem.getTimelineDownloadedDate() > 0) {
                strLog += "; last time downloaded at=" +  (new Date(latestTimelineItem.getTimelineDownloadedDate()).toString());
            }
            MyLog.d(this, strLog);
        }
        
        latestTimelineItem.onTimelineDownloaded();
        List<String> userOidsNew = null;
        LatestUserMessages lum = new LatestUserMessages();
        // Retrieve new list of followed users
        DataInserter di = new DataInserter(execContext);
        if (execContext.getMyAccount().getConnection().isApiSupported(ApiRoutineEnum.GET_FRIENDS)) {
            List<MbUser> usersNew =
                    execContext.getMyAccount().getConnection().getUsersFollowedBy(userOid);
            userOidsNew = new ArrayList<>();
            for (MbUser user : usersNew) {
                userOidsNew.add(user.oid);
                di.insertOrUpdateUser(user, lum);
            }
        } else if (execContext.getMyAccount().getConnection().isApiSupported(ApiRoutineEnum.GET_FRIENDS_IDS)) {
            userOidsNew = execContext.getMyAccount().getConnection().getIdsOfUsersFollowedBy(userOid);
        } else {
            throw new ConnectionException(StatusCode.UNSUPPORTED_API, ApiRoutineEnum.GET_FRIENDS 
                    + " and " + ApiRoutineEnum.GET_FRIENDS_IDS);
        }
        // Old list of followed users
        Set<Long> userIdsOld = MyQuery.getIdsOfUsersFollowedBy(userId);
        SQLiteDatabase db = MyContextHolder.get().getDatabase();
        if (db == null) {
            MyLog.v(this, "Database is null");
            return;
        }
        for (String userOidNew : userOidsNew) {
            long userIdNew = MyQuery.oidToId(MyDatabase.OidEnum.USER_OID, execContext.getMyAccount().getOriginId(), userOidNew);
            long msgId = 0;
            if (userIdNew != 0) {
                msgId = MyQuery.userIdToLongColumnValue(User.USER_MSG_ID, userIdNew);
            }
            // The Friend doesn't have any messages sent, so let's download the latest
            if (msgId == 0) {
                try {
                    // Download the Users's info + optionally his latest message
                    if (userIdNew == 0 || execContext.getMyAccount().getConnection().userObjectHasMessage()) {
                        MbUser mbUser = execContext.getMyAccount().getConnection().getUser(userOidNew, null);
                        userIdNew = di.insertOrUpdateUser(mbUser, lum);
                        msgId = MyQuery.userIdToLongColumnValue(User.USER_MSG_ID, userIdNew);
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
                FollowingUserValues fu = new FollowingUserValues(userId, userIdNew);
                fu.setFollowed(true);
                fu.update(db);
            }
        }
        
        lum.save();
        
        // Now let's remove "following" information for all users left in the Set:
        for (long notFollowedId : userIdsOld) {
            FollowingUserValues fu = new FollowingUserValues(userId, notFollowedId);
            fu.setFollowed(false);
            fu.update(db);
        }
        latestTimelineItem.save();
    }
}
