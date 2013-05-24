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

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.account.MyAccount.CredentialsVerified;
import org.andstatus.app.data.DataInserter;
import org.andstatus.app.data.LastMsgInfo;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyDatabase.OidEnum;
import org.andstatus.app.data.MyDatabase.TimelineTypeEnum;
import org.andstatus.app.data.MyPreferences;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.net.Connection.ApiRoutineEnum;
import org.andstatus.app.net.ConnectionException;
import org.andstatus.app.util.MyLog;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentResolver;
import android.content.Context;
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

    private ContentResolver mContentResolver;

    private Context mContext;

    /**
     * Counter. These may be "general" or Direct messages...
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

    private MyAccount ma;

    private TimelineTypeEnum mTimelineType;
    
    /**
     * The timeline parameter. Used in the {@link TimelineTypeEnum#USER} timeline.
     */
    private long mUserId = 0;
    
    public TimelineDownloader(MyAccount ma_in, Context context, TimelineTypeEnum timelineType) {
        this(ma_in, context, timelineType, 0);
    }
    
    public TimelineDownloader(MyAccount ma_in, Context context, TimelineTypeEnum timelineType, long userId) {
        mContext = context;
        mContentResolver = mContext.getContentResolver();
        ma = ma_in;
        // mApi = ma.getApi();
        mTimelineType = timelineType;
        mUserId = userId;
        
        switch (mTimelineType) {
            case HOME:
            case MENTIONS:
            case DIRECT:
            case ALL:
                break;
            case USER:
                if (mUserId == 0) {
                    Log.e(TAG, "UserId is required for the Timeline type: " + mTimelineType.save());
                }
                break;
            default:
                Log.e(TAG, "Unknown Timeline type: " + mTimelineType);
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
        
        if (mTimelineType == TimelineTypeEnum.ALL) {
            Log.e(TAG, "Invalid TimelineType for loadTimeline: " + mTimelineType);
            return ok;
        }

        String userOid =  "";
        if (mUserId != 0) {
            userOid =  MyProvider.idToOid(OidEnum.USER_OID, mUserId, 0);
        }
        
        LastMsgInfo lastMsgInfo = new LastMsgInfo(ma, mContext, mTimelineType, mUserId);
        long lastMsgId = lastMsgInfo.getLastMsgId();
        long lastMsgDate = lastMsgInfo.getLastMsgDate();
        
        if (MyLog.isLoggable(TAG, Log.DEBUG)) {
            String strLog = "Loading timeline " + mTimelineType.save() + "; account=" + ma.getUsername();
            if (mUserId != 0) {
                strLog += "; user=" + MyProvider.userIdToName(mUserId);
            }
            if (lastMsgDate > 0) {
                strLog += "; since=" + (new Date(lastMsgDate).toString())
                        + "; last time downloaded at " +  (new Date(lastMsgInfo.getTimelineDate()).toString());
            }
            MyLog.d(TAG, strLog);
        }
        
        int limit = 200;
        if ((ma.getCredentialsVerified() == CredentialsVerified.SUCCEEDED) && MyPreferences.isDataAvailable()) {
            String lastOid = MyProvider.idToOid(OidEnum.MSG_OID, lastMsgId, 0);
            JSONArray jArr = null;
            switch (mTimelineType) {
                case HOME:
                    jArr = ma.getConnection().getTimeline(ApiRoutineEnum.STATUSES_HOME_TIMELINE, lastOid, limit, null);
                    break;
                case MENTIONS:
                    jArr = ma.getConnection().getTimeline(ApiRoutineEnum.STATUSES_MENTIONS_TIMELINE, lastOid, limit, null);
                    break;
                case DIRECT:
                    jArr = ma.getConnection().getTimeline(ApiRoutineEnum.DIRECT_MESSAGES, lastOid, limit, null);
                    break;
                case USER:
                    jArr = ma.getConnection().getTimeline(ApiRoutineEnum.STATUSES_USER_TIMELINE, lastOid, limit, userOid);
                    break;
                default:
                    Log.e(TAG, "Got unhandled Timeline type: " + mTimelineType.save());
                    break;
            }
            if (jArr != null) {
                ok = true;
                try {
                    DataInserter di = new DataInserter(ma, mContext, mTimelineType, mUserId);
                    for (int index = 0; index < jArr.length(); index++) {
                        JSONObject msg = jArr.getJSONObject(index);
                        long created = 0L;
                        // This value will be SENT_DATE in the database
                        if (msg.has("created_at")) {
                            String createdAt = msg.getString("created_at");
                            if (createdAt.length() > 0) {
                                created = Date.parse(createdAt);
                            }
                        }
                        long idInserted = di.insertMsgFromJSONObject(msg);
                        // Check if this message is newer than any we got earlier
                        if (created > lastMsgDate && idInserted > lastMsgId) {
                            lastMsgDate = created;
                            lastMsgId = idInserted;
                        }
                    }
                    mMessages += di.messagesCount();
                    mMentions += di.mentionsCount();
                    mReplies += di.repliesCount();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            if (mMessages > 0) {
                // Notify all timelines, 
                // see http://stackoverflow.com/questions/6678046/when-contentresolver-notifychange-is-called-for-a-given-uri-are-contentobserv
                mContentResolver.notifyChange(MyProvider.TIMELINE_URI, null);
            }
            lastMsgInfo.saveLastMsgInfo(lastMsgId, lastMsgDate);
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
}
