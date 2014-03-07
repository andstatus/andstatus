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
import org.andstatus.app.net.ConnectionException;
import org.andstatus.app.net.MbTimelineItem;
import org.andstatus.app.net.MbTimelineItem.ItemType;
import org.andstatus.app.net.MbUser;
import org.andstatus.app.net.Connection.ApiRoutineEnum;
import org.andstatus.app.net.ConnectionException.StatusCode;
import org.andstatus.app.net.TimelinePosition;
import org.andstatus.app.util.MyLog;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

public class TimelineDownloaderUser extends TimelineDownloader {
    private static final String TAG = TimelineDownloaderUser.class.getSimpleName();

    @Override
    public void download() throws ConnectionException {
        String userOid =  MyProvider.idToOid(OidEnum.USER_OID, counters.getTimelineUserId(), 0);
        LatestTimelineItem latestTimelineItem = new LatestTimelineItem(counters.getTimelineType(), counters.getTimelineUserId());
        
        if (MyLog.isLoggable(TAG, MyLog.DEBUG)) {
            String strLog = "Loading " + counters.getTimelineType() + "; account=" + counters.getMyAccount().getAccountName();
            strLog += "; user=" + MyProvider.userIdToName(counters.getTimelineUserId());
            if (latestTimelineItem.getTimelineDownloadedDate() > 0) {
                strLog += "; last time downloaded at=" +  (new Date(latestTimelineItem.getTimelineDownloadedDate()).toString());
            }
            MyLog.d(TAG, strLog);
        }
        
        latestTimelineItem.onTimelineDownloaded();
        List<String> followedUsersOids = null;
        List<MbUser> followedUsers = null;
        LatestUserMessages lum = new LatestUserMessages();
        // Retrieve new list of followed users
        DataInserter di = new DataInserter(counters);
        if (counters.getMyAccount().getConnection().isApiSupported(ApiRoutineEnum.GET_FRIENDS)) {
            followedUsers = counters.getMyAccount().getConnection().getUsersFollowedBy(userOid);
            followedUsersOids = new ArrayList<String>();
            for (MbUser followedUser : followedUsers) {
                followedUsersOids.add(followedUser.oid);
                di.insertOrUpdateUser(followedUser, lum);
            }
        } else if (counters.getMyAccount().getConnection().isApiSupported(ApiRoutineEnum.GET_FRIENDS_IDS)) {
            followedUsersOids = counters.getMyAccount().getConnection().getIdsOfUsersFollowedBy(userOid);
        } else {
            throw new ConnectionException(StatusCode.UNSUPPORTED_API, ApiRoutineEnum.GET_FRIENDS 
                    + " and " + ApiRoutineEnum.GET_FRIENDS_IDS);
        }
        // Old list of followed users
        Set<Long> followedIdsOld = MyProvider.getIdsOfUsersFollowedBy(counters.getTimelineUserId());
        SQLiteDatabase db = MyContextHolder.get().getDatabase().getWritableDatabase();
        for (String followedUserOid : followedUsersOids) {
            long friendId = MyProvider.oidToId(MyDatabase.OidEnum.USER_OID, counters.getMyAccount().getOriginId(), followedUserOid);
            long msgId = 0;
            if (friendId != 0) {
                followedIdsOld.remove(friendId);
                msgId = MyProvider.userIdToLongColumnValue(User.USER_MSG_ID, friendId);
            }
            // The Friend doesn't have any messages sent, so let's download the latest
            if (msgId == 0) {
                try {
                    // Download the Users's info + optionally his latest message
                    if (friendId == 0 || counters.getMyAccount().getConnection().userObjectHasMessage()) {
                        MbUser mbUser = counters.getMyAccount().getConnection().getUser(followedUserOid);
                        friendId = di.insertOrUpdateUser(mbUser, lum);
                        msgId = MyProvider.userIdToLongColumnValue(User.USER_MSG_ID, friendId);
                    } 
                    if (friendId != 0 && msgId == 0) {
                        downloadOneMessageBy(followedUserOid, lum);
                    }
                } catch (ConnectionException e) {
                    MyLog.i(TAG, "Failed to download the User object or his message for oid=" + followedUserOid, e);
                }
            }
            if (friendId != 0) {
                FollowingUserValues fu = new FollowingUserValues(counters.getTimelineUserId(), friendId);
                fu.setFollowed(true);
                fu.update(db);
            }
        }
        
        lum.save();
        
        // Now let's remove "following" information for all users left in the Set:
        for (long notFollowingId : followedIdsOld) {
            FollowingUserValues fu = new FollowingUserValues(counters.getTimelineUserId(), notFollowingId);
            fu.setFollowed(false);
            fu.update(db);
        }
        latestTimelineItem.save();
    }

    private void downloadOneMessageBy(String userOid, LatestUserMessages lum) throws ConnectionException {
        counters.setTimelineType(TimelineTypeEnum.USER);
        List<MbTimelineItem> messages = counters.getMyAccount().getConnection().getTimeline(
                counters.getTimelineType().getConnectionApiRoutine(), TimelinePosition.getEmpty(), 1, userOid);
        DataInserter di = new DataInserter(counters);
        for (MbTimelineItem item : messages) {
            if (item.getType() == ItemType.MESSAGE) {
                di.insertOrUpdateMsg(item.mbMessage, lum);
                break;
            }
        }
    }
}
