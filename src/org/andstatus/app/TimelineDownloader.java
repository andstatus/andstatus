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
import java.util.Set;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.account.MyAccount.CredentialsVerified;
import org.andstatus.app.data.DataInserter;
import org.andstatus.app.data.FollowingUserValues;
import org.andstatus.app.data.LatestUserMessages;
import org.andstatus.app.data.TimelineMsg;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyDatabase.OidEnum;
import org.andstatus.app.data.MyDatabase.TimelineTypeEnum;
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.data.MyPreferences;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.net.ConnectionException;
import org.andstatus.app.util.MyLog;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
        if ((ma.getCredentialsVerified() == CredentialsVerified.SUCCEEDED) && MyPreferences.isDataAvailable()) {
            switch (mTimelineType) {
                case FOLLOWING_USER:
                    ok = loadFollowingUserTimeline();
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
        boolean ok = false;
        
        String userOid =  null;
        if (mUserId != 0) {
            userOid =  MyProvider.idToOid(OidEnum.USER_OID, mUserId, 0);
        }
        
        TimelineMsg timelineMsg = new TimelineMsg(mTimelineType, mUserId);
        
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
        JSONArray jArr = ma.getConnection().getTimeline(mTimelineType.getConnectionApiRoutine(), lastOid, limit, userOid);
        if (jArr != null) {
            ok = true;
            try {
                LatestUserMessages lum = new LatestUserMessages();
                DataInserter di = new DataInserter(ma, mContext, mTimelineType);
                for (int index = 0; index < jArr.length(); index++) {
                    JSONObject msg = jArr.getJSONObject(index);
                    long msgId = di.insertMsgFromJSONObject(msg, lum);
                    timelineMsg.onNewMsg(msgId, 0);
                }
                mDownloaded += di.downloadedCount();
                mMessages += di.messagesCount();
                mMentions += di.mentionsCount();
                mReplies += di.repliesCount();
                lum.save();
            } catch (JSONException e) {
                ok = false;
                e.printStackTrace();
            }
        }
        if (ok) {
            timelineMsg.save();
        }
        return ok;
    }

    /**
     * Load the User Ids from the Internet and store them in the local database.
     * mUserId is required to be set
     * 
     * @throws ConnectionException
     */
    private boolean loadFollowingUserTimeline() throws ConnectionException {
        boolean ok = false;
        
        String userOid =  MyProvider.idToOid(OidEnum.USER_OID, mUserId, 0);
        TimelineMsg timelineMsg = new TimelineMsg(mTimelineType, mUserId);
        
        if (MyLog.isLoggable(TAG, Log.DEBUG)) {
            String strLog = "Loading timeline " + mTimelineType.save() + "; account=" + ma.getAccountName();
            strLog += "; user=" + MyProvider.userIdToName(mUserId);
            if (timelineMsg.getTimelineDate() > 0) {
                strLog += "; last time downloaded at=" +  (new Date(timelineMsg.getTimelineDate()).toString());
            }
            MyLog.d(TAG, strLog);
        }
        
        timelineMsg.onTimelineDownloaded();
        JSONArray jArr = ma.getConnection().getFriendsIds(userOid);
        if (jArr != null) {
            ok = true;
            
            try {
                // Old list of followed users
                Set<Long> friends_old = MyProvider.getFriendsIds(mUserId);

                SQLiteDatabase db = MyPreferences.getDatabase().getWritableDatabase();

                LatestUserMessages lum = new LatestUserMessages();
                // Retrieve new list of followed users
                DataInserter di = new DataInserter(ma, mContext, mTimelineType);
                for (int index = 0; index < jArr.length(); index++) {
                    String friendOid = jArr.getString(index);
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
                            JSONObject dbUser = ma.getConnection().getUser(friendOid);
                            di.insertUserFromJSONObject(dbUser, lum);
                        } catch (ConnectionException e) {
                            Log.w(TAG, "Failed to download a User object for oid=" + friendOid);
                        }
                    } else {
                        FollowingUserValues fu = new FollowingUserValues(mUserId, friendId);
                        fu.setFollowed(true);
                        fu.update(db);
                    }
                }
                
                mDownloaded += di.downloadedCount();
                mMessages += di.messagesCount();
                mMentions += di.mentionsCount();
                mReplies += di.repliesCount();
                lum.save();
                
                // Now let's remove "following" information for all users left in the Set:
                for (long notFollowingId : friends_old) {
                    FollowingUserValues fu = new FollowingUserValues(mUserId, notFollowingId);
                    fu.setFollowed(false);
                    fu.update(db);
                }
                
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        if (ok) {
            timelineMsg.save();
        }
        return ok;
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
