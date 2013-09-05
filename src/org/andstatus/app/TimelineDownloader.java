/* 
 * Copyright (c) 2012-2013 yvolk (Yuri Volkov), http://yurivolkov.com
 * Copyright (C) 2008 Torgny Bjers
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

package org.andstatus.app;

import java.util.Date;
import java.util.List;
import java.util.Set;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.account.MyAccount.CredentialsVerificationStatus;
import org.andstatus.app.data.DataInserter;
import org.andstatus.app.data.FollowingUserValues;
import org.andstatus.app.data.LatestUserMessages;
import org.andstatus.app.data.LatestTimelineItem;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyDatabase.OidEnum;
import org.andstatus.app.data.MyDatabase.TimelineTypeEnum;
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.data.MyPreferences;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.net.ConnectionException;
import org.andstatus.app.net.MbMessage;
import org.andstatus.app.net.MbUser;
import org.andstatus.app.net.ConnectionException.StatusCode;
import org.andstatus.app.net.TimelinePosition;
import org.andstatus.app.util.MyLog;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;
import android.util.Log;


/**
 * Downloads ("loads") Timelines 
 *  (i.e. Tweets and Messages) from the Internet 
 *  (e.g. from twitter.com server) into local JSON objects.
 * Then Store them into database using {@link DataInserter}
 * 
 * @author yvolk, torgny.bjers
 */
public class TimelineDownloader {
    private static final String TAG = TimelineDownloader.class.getSimpleName();

    private Context context;

    /**
     * New messages Counter. These may be "general" or Direct messages...
     */
    private int newMessagesCount;

    /**
     * Number of new Mentions received 
     */
    private int newMentionsCount;
    /**
     * Number of new Replies received 
     */
    private int newRepliesCount;
    /**
     * Total number of messages downloaded
     */
    private int totalMessagesDownloadedCount;
    
    private MyAccount ma;

    private TimelineTypeEnum timelineType;
    
    /**
     * The timeline is of this User, for all timeline types.
     */
    private long userId = 0;
    
    public TimelineDownloader(MyAccount ma_in, Context context, TimelineTypeEnum timelineType, long userId) {
        this.context = context;
        ma = ma_in;
        this.timelineType = timelineType;
        this.userId = userId;
        if (userId == 0) {
            throw new IllegalArgumentException(TAG + ": userId==0");
        }
    }
    
    /**
     * Load the Timeline from the Internet
     * and store it in the local database.
     * 
     * @throws ConnectionException
     */
    public boolean loadTimeline() throws ConnectionException {
        boolean ok = false;
        if ((ma.getCredentialsVerified() == CredentialsVerificationStatus.SUCCEEDED) && MyPreferences.isDataAvailable()) {
            switch (timelineType) {
                case FOLLOWING_USER:
                    loadFollowedByUserTimeline();
                    ok = true;
                    break;
                case ALL:
                    Log.e(TAG, "Invalid TimelineType for loadTimeline: " + timelineType);
                default:
                    ok = loadMsgTimeline();
            }
        }
        return ok;
    }

    /**
     * Load the Timeline from the Internet
     * and store it in the local database.
     * 
     * @throws ConnectionException
     */
    private boolean loadMsgTimeline() throws ConnectionException {
        LatestTimelineItem latestTimelineItem = new LatestTimelineItem(timelineType, userId);
        
        if (MyLog.isLoggable(TAG, Log.DEBUG)) {
            String strLog = "Loading timeline " + timelineType.save() + "; account=" + ma.getAccountName()
                    + "; user=" + MyProvider.userIdToName(userId);
            if (latestTimelineItem.getTimelineItemDate() > 0) {
                strLog += "; last Timeline item at=" + (new Date(latestTimelineItem.getTimelineItemDate()).toString())
                        + "; last time downloaded at=" +  (new Date(latestTimelineItem.getTimelineDownloadedDate()).toString());
            }
            MyLog.d(TAG, strLog);
        }
        String userOid =  MyProvider.idToOid(OidEnum.USER_OID, userId, 0);
        if (TextUtils.isEmpty(userOid)) {
            throw new ConnectionException("User oId is not found for id=" + userId);
        }
        
        int toDownload = 200;
        TimelinePosition lastPosition = latestTimelineItem.getPosition();
        LatestUserMessages latestUserMessages = new LatestUserMessages();
        latestTimelineItem.onTimelineDownloaded();
        for (boolean done = false; !done || toDownload > 0; ) {
            try {
                int limit = ma.getConnection().fixedDownloadLimit(toDownload); 
                List<MbMessage> messages = ma.getConnection().getTimeline(timelineType.getConnectionApiRoutine(), lastPosition, limit, userOid);
                if (messages.size() < 2) {  // We may assume that we downloaded the same message...
                    toDownload = 0;
                }
                if (messages.size() > 0) {
                    toDownload -= messages.size();
                    DataInserter di = new DataInserter(ma, context, timelineType);
                    for (MbMessage message : messages) {
                        latestTimelineItem.onNewMsg(message.timelineItemPosition, message.timelineItemDate);
                        if (!message.isEmpty()) {
                            di.insertOrUpdateMsg(message, latestUserMessages);
                        }
                    }
                    totalMessagesDownloadedCount += di.totalMessagesDownloadedCount();
                    newMessagesCount += di.newMessagesCount();
                    newMentionsCount += di.newMentionsCount();
                    newRepliesCount += di.newRepliesCount();
                    lastPosition = latestTimelineItem.getPosition();
                }
                done = true;
            } catch (ConnectionException e) {
                if (e.getStatusCode() != StatusCode.NOT_FOUND) {
                    throw e;
                }
                Log.d(TAG, "The timeline was not found, last position='" + lastPosition +"'");
                lastPosition = TimelinePosition.getEmpty();
            }
        }
        latestUserMessages.save();
        latestTimelineItem.save();
        return true;
    }

    /**
     * Load from the Internet the Ids of the Users who are following the authenticated user
     *   and store them in the local database.
     * mUserId is required to be set
     */
    private void loadFollowedByUserTimeline() throws ConnectionException {
        String userOid =  MyProvider.idToOid(OidEnum.USER_OID, userId, 0);
        LatestTimelineItem latestTimelineItem = new LatestTimelineItem(timelineType, userId);
        
        if (MyLog.isLoggable(TAG, Log.DEBUG)) {
            String strLog = "Loading timeline " + timelineType.save() + "; account=" + ma.getAccountName();
            strLog += "; user=" + MyProvider.userIdToName(userId);
            if (latestTimelineItem.getTimelineDownloadedDate() > 0) {
                strLog += "; last time downloaded at=" +  (new Date(latestTimelineItem.getTimelineDownloadedDate()).toString());
            }
            MyLog.d(TAG, strLog);
        }
        
        latestTimelineItem.onTimelineDownloaded();
        List<String> followedUsersOids = ma.getConnection().getIdsOfUsersFollowedBy(userOid);
        // Old list of followed users
        Set<Long> followedIds_old = MyProvider.getIdsOfUsersFollowedBy(userId);

        SQLiteDatabase db = MyPreferences.getDatabase().getWritableDatabase();

        LatestUserMessages lum = new LatestUserMessages();
        // Retrieve new list of followed users
        DataInserter di = new DataInserter(ma, context, timelineType);
        for (String followedUserOid : followedUsersOids) {
            long friendId = MyProvider.oidToId(MyDatabase.OidEnum.USER_OID, ma.getOriginId(), followedUserOid);
            boolean isNew = true;
            if (friendId != 0) {
                followedIds_old.remove(friendId);
                long msgId = MyProvider.userIdToLongColumnValue(User.USER_MSG_ID, friendId);
                // The Friend doesn't have any messages sent, so let's download the latest
                isNew = (msgId == 0);
            }
            if (isNew) {
                try {
                    // This User is new, let's download his info
                    MbUser dbUser = ma.getConnection().getUser(followedUserOid);
                    di.insertOrUpdateUser(dbUser, lum);
                } catch (ConnectionException e) {
                    Log.w(TAG, "Failed to download a User object for oid=" + followedUserOid);
                }
            } else {
                FollowingUserValues fu = new FollowingUserValues(userId, friendId);
                fu.setFollowed(true);
                fu.update(db);
            }
        }
        
        totalMessagesDownloadedCount += di.totalMessagesDownloadedCount();
        newMessagesCount += di.newMessagesCount();
        newMentionsCount += di.newMentionsCount();
        newRepliesCount += di.newRepliesCount();
        lum.save();
        
        // Now let's remove "following" information for all users left in the Set:
        for (long notFollowingId : followedIds_old) {
            FollowingUserValues fu = new FollowingUserValues(userId, notFollowingId);
            fu.setFollowed(false);
            fu.update(db);
        }
        latestTimelineItem.save();
    }
    
    public int newMessagesCount() {
        return newMessagesCount;
    }

    public int newRepliesCount() {
        return newRepliesCount;
    }

    public int newMentionsCount() {
        return newMentionsCount;
    }

    public int totalMessagesDownloadedCount() {
        return totalMessagesDownloadedCount;
    }
}
