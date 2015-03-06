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
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.MyDatabase.OidEnum;
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.data.TimelineTypeEnum;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.social.MbTimelineItem;
import org.andstatus.app.net.social.MbTimelineItem.ItemType;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.net.social.Connection.ApiRoutineEnum;
import org.andstatus.app.net.http.ConnectionException.StatusCode;
import org.andstatus.app.net.social.TimelinePosition;
import org.andstatus.app.util.MyLog;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

class TimelineDownloaderFollowing extends TimelineDownloader {

    @Override
    public void download() throws ConnectionException {
        String userOid =  MyProvider.idToOid(OidEnum.USER_OID, execContext.getTimelineUserId(), 0);
        if (TextUtils.isEmpty(userOid)) {
            MyLog.d(this, "userOid is Empty." + execContext);
            execContext.getResult().incrementParseExceptions();
        } else {
            downloadFollowingFor(userOid);
        }
    }

    private void downloadFollowingFor(String userOid) throws ConnectionException {
        LatestTimelineItem latestTimelineItem = new LatestTimelineItem(execContext.getTimelineType(), execContext.getTimelineUserId());
        
        if (MyLog.isLoggable(this, MyLog.DEBUG)) {
            String strLog = "Loading " + execContext.getTimelineType() + "; account=" + execContext.getMyAccount().getAccountName();
            strLog += "; user='" + MyProvider.userIdToWebfingerId(execContext.getTimelineUserId()) + "', oid=" + userOid;
            if (latestTimelineItem.getTimelineDownloadedDate() > 0) {
                strLog += "; last time downloaded at=" +  (new Date(latestTimelineItem.getTimelineDownloadedDate()).toString());
            }
            MyLog.d(this, strLog);
        }
        
        latestTimelineItem.onTimelineDownloaded();
        List<String> followedUsersOids = null;
        List<MbUser> followedUsers = null;
        LatestUserMessages lum = new LatestUserMessages();
        // Retrieve new list of followed users
        DataInserter di = new DataInserter(execContext);
        if (execContext.getMyAccount().getConnection().isApiSupported(ApiRoutineEnum.GET_FRIENDS)) {
            followedUsers = execContext.getMyAccount().getConnection().getUsersFollowedBy(userOid);
            followedUsersOids = new ArrayList<String>();
            for (MbUser followedUser : followedUsers) {
                followedUsersOids.add(followedUser.oid);
                di.insertOrUpdateUser(followedUser, lum);
            }
        } else if (execContext.getMyAccount().getConnection().isApiSupported(ApiRoutineEnum.GET_FRIENDS_IDS)) {
            followedUsersOids = execContext.getMyAccount().getConnection().getIdsOfUsersFollowedBy(userOid);
        } else {
            throw new ConnectionException(StatusCode.UNSUPPORTED_API, ApiRoutineEnum.GET_FRIENDS 
                    + " and " + ApiRoutineEnum.GET_FRIENDS_IDS);
        }
        // Old list of followed users
        Set<Long> followedIdsOld = MyProvider.getIdsOfUsersFollowedBy(execContext.getTimelineUserId());
        SQLiteDatabase db = MyContextHolder.get().getDatabase().getWritableDatabase();
        for (String followedUserOid : followedUsersOids) {
            long friendId = MyProvider.oidToId(MyDatabase.OidEnum.USER_OID, execContext.getMyAccount().getOriginId(), followedUserOid);
            long msgId = 0;
            if (friendId != 0) {
                followedIdsOld.remove(friendId);
                msgId = MyProvider.userIdToLongColumnValue(User.USER_MSG_ID, friendId);
            }
            // The Friend doesn't have any messages sent, so let's download the latest
            if (msgId == 0) {
                try {
                    // Download the Users's info + optionally his latest message
                    if (friendId == 0 || execContext.getMyAccount().getConnection().userObjectHasMessage()) {
                        MbUser mbUser = execContext.getMyAccount().getConnection().getUser(followedUserOid);
                        friendId = di.insertOrUpdateUser(mbUser, lum);
                        msgId = MyProvider.userIdToLongColumnValue(User.USER_MSG_ID, friendId);
                    } 
                    if (friendId != 0 && msgId == 0) {
                        downloadOneMessageBy(followedUserOid, lum);
                    }
                } catch (ConnectionException e) {
                    MyLog.i(this, "Failed to download the User object or his message for oid=" + followedUserOid, e);
                }
            }
            if (friendId != 0) {
                FollowingUserValues fu = new FollowingUserValues(execContext.getTimelineUserId(), friendId);
                fu.setFollowed(true);
                fu.update(db);
            }
        }
        
        lum.save();
        
        // Now let's remove "following" information for all users left in the Set:
        for (long notFollowingId : followedIdsOld) {
            FollowingUserValues fu = new FollowingUserValues(execContext.getTimelineUserId(), notFollowingId);
            fu.setFollowed(false);
            fu.update(db);
        }
        latestTimelineItem.save();
    }

    private void downloadOneMessageBy(String userOid, LatestUserMessages lum) throws ConnectionException {
        execContext.setTimelineType(TimelineTypeEnum.USER);
        List<MbTimelineItem> messages = execContext.getMyAccount().getConnection().getTimeline(
                execContext.getTimelineType().getConnectionApiRoutine(), TimelinePosition.getEmpty(), 1, userOid);
        DataInserter di = new DataInserter(execContext);
        for (MbTimelineItem item : messages) {
            if (item.getType() == ItemType.MESSAGE) {
                di.insertOrUpdateMsg(item.mbMessage, lum);
                break;
            }
        }
    }
}
