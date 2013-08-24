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
import org.andstatus.app.data.LatestMessageOfTimeline;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyDatabase.OidEnum;
import org.andstatus.app.data.MyDatabase.TimelineTypeEnum;
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.data.MyPreferences;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.net.ConnectionException;
import org.andstatus.app.net.MbMessage;
import org.andstatus.app.net.MbUser;
import org.andstatus.app.util.MyLog;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
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

    private Context mContext;

    /**
     * New messages Counter. These may be "general" or Direct messages...
     */
    private int mMessages;

    /**
     * Number of new Mentions received 
     */
    private int mMentions;
    /**
     * Number of new Replies received 
     */
    private int mReplies;
    /**
     * Total number of messages downloaded
     */
    private int mDownloaded;
    
    private MyAccount ma;

    private TimelineTypeEnum mTimelineType;
    
    /**
     * The timeline is of this User, for all timeline types.
     */
    private long mUserId = 0;
    
    public TimelineDownloader(MyAccount ma_in, Context context, TimelineTypeEnum timelineType, long userId) {
        mContext = context;
        ma = ma_in;
        mTimelineType = timelineType;
        mUserId = userId;
        if (mUserId == 0) {
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
            switch (mTimelineType) {
                case FOLLOWING_USER:
                    loadFollowingUserTimeline();
                    ok = true;
                    break;
                case ALL:
                    Log.e(TAG, "Invalid TimelineType for loadTimeline: " + mTimelineType);
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
        String userOid =  null;
        if (mUserId != 0) {
            userOid =  MyProvider.idToOid(OidEnum.USER_OID, mUserId, 0);
        }
        
        LatestMessageOfTimeline timelineMsg = new LatestMessageOfTimeline(mTimelineType, mUserId);
        
        if (MyLog.isLoggable(TAG, Log.DEBUG)) {
            String strLog = "Loading timeline " + mTimelineType.save() + "; account=" + ma.getAccountName();
            if (mUserId != 0) {
                strLog += "; user=" + MyProvider.userIdToName(mUserId);
            }
            if (timelineMsg.getLastMsgDate() > 0) {
                strLog += "; last msg at=" + (new Date(timelineMsg.getLastMsgDate()).toString())
                        + "; last time downloaded at=" +  (new Date(timelineMsg.getTimelineDate()).toString());
            }
            MyLog.d(TAG, strLog);
        }
        
        int limit = 200;
        String lastOid = MyProvider.idToOid(OidEnum.MSG_OID, timelineMsg.getLastMsgId(), 0);
        timelineMsg.onTimelineDownloaded();
        List<MbMessage> messages = ma.getConnection().getTimeline(mTimelineType.getConnectionApiRoutine(), lastOid, limit, userOid);
        if (messages.size()>0) {
            LatestUserMessages lum = new LatestUserMessages();
            DataInserter di = new DataInserter(ma, mContext, mTimelineType);
            for (MbMessage message : messages) {
                long msgId = di.insertOrUpdateMsg(message, lum);
                timelineMsg.onNewMsg(msgId, 0);
            }
            mDownloaded += di.totalMessagesDownloadedCount();
            mMessages += di.newMessagesCount();
            mMentions += di.newMentionsCount();
            mReplies += di.newRepliesCount();
            lum.save();
            timelineMsg.save();
        }
        return true;
    }

    /**
     * Load from the Internet the Ids of the Users who are following the authenticated user
     *   and store them in the local database.
     * mUserId is required to be set
     */
    private void loadFollowingUserTimeline() throws ConnectionException {
        String userOid =  MyProvider.idToOid(OidEnum.USER_OID, mUserId, 0);
        LatestMessageOfTimeline timelineMsg = new LatestMessageOfTimeline(mTimelineType, mUserId);
        
        if (MyLog.isLoggable(TAG, Log.DEBUG)) {
            String strLog = "Loading timeline " + mTimelineType.save() + "; account=" + ma.getAccountName();
            strLog += "; user=" + MyProvider.userIdToName(mUserId);
            if (timelineMsg.getTimelineDate() > 0) {
                strLog += "; last time downloaded at=" +  (new Date(timelineMsg.getTimelineDate()).toString());
            }
            MyLog.d(TAG, strLog);
        }
        
        timelineMsg.onTimelineDownloaded();
        List<String> friendOids = ma.getConnection().getFriendsIds(userOid);
        // Old list of followed users
        Set<Long> friends_old = MyProvider.getFriendsIds(mUserId);

        SQLiteDatabase db = MyPreferences.getDatabase().getWritableDatabase();

        LatestUserMessages lum = new LatestUserMessages();
        // Retrieve new list of followed users
        DataInserter di = new DataInserter(ma, mContext, mTimelineType);
        for (String friendOid : friendOids) {
            long friendId = MyProvider.oidToId(MyDatabase.OidEnum.USER_OID, ma.getOriginId(), friendOid);
            boolean isNew = true;
            if (friendId != 0) {
                friends_old.remove(friendId);
                long msgId = MyProvider.userIdToLongColumnValue(User.USER_MSG_ID, friendId);
                // The Friend doesn't have any messages sent, so let's download the latest
                isNew = (msgId == 0);
            }
            if (isNew) {
                try {
                    // This User is new, let's download his info
                    MbUser dbUser = ma.getConnection().getUser(friendOid);
                    di.insertOrUpdateUser(dbUser, lum);
                } catch (ConnectionException e) {
                    Log.w(TAG, "Failed to download a User object for oid=" + friendOid);
                }
            } else {
                FollowingUserValues fu = new FollowingUserValues(mUserId, friendId);
                fu.setFollowed(true);
                fu.update(db);
            }
        }
        
        mDownloaded += di.totalMessagesDownloadedCount();
        mMessages += di.newMessagesCount();
        mMentions += di.newMentionsCount();
        mReplies += di.newRepliesCount();
        lum.save();
        
        // Now let's remove "following" information for all users left in the Set:
        for (long notFollowingId : friends_old) {
            FollowingUserValues fu = new FollowingUserValues(mUserId, notFollowingId);
            fu.setFollowed(false);
            fu.update(db);
        }
        timelineMsg.save();
    }
    
    /**
     * Return the number of new messages, see {@link MyDatabase.Msg} .
     */
    public int messagesCount() {
        return mMessages;
    }

    /**
     * Return the number of new Replies.
     */
    public int repliesCount() {
        return mReplies;
    }

    /**
     * Return the number of new Mentions.
     */
    public int mentionsCount() {
        return mMentions;
    }

    /**
     * Return total number of downloaded Messages
     */
    public int downloadedCount() {
        return mDownloaded;
    }
}
