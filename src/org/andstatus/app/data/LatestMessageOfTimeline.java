/**
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

package org.andstatus.app.data;

import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;
import android.util.Log;

import org.andstatus.app.data.MyDatabase.Msg;
import org.andstatus.app.data.MyDatabase.TimelineTypeEnum;
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.util.MyLog;

import java.util.Date;


/**
 * Retrieve and save information about the latest downloaded message from this timeline
 */
public class LatestMessageOfTimeline {
    private static final String TAG = LatestMessageOfTimeline.class.getSimpleName();

    private TimelineTypeEnum timelineType;
    /**
     * The timeline is of this User, for all timeline types.
     */
    private long userId = 0;
    
    /**
     * The id of the latest Message, downloaded for this timeline
     * 0 - none were downloaded
     */
    long lastMsgId = 0;
    /**
     * 0 - none were downloaded
     */
    long lastMsgDate = 0;
    /**
     * Last date when this timeline was successfully downloaded.
     * It is used to know when it will be time for the next automatic update
     */
    long timelineDate = 0;
    
    /**
     * We will update only what really changed
     */
    private boolean lastMsgChanged = false;
    private boolean timelineDateChanged = false;
    
    /**
     * Retrieve information about the last downloaded message from this timeline
     * @param userId_in Should always be Id of the User of this timeline
     */
    public LatestMessageOfTimeline(TimelineTypeEnum timelineType_in, long userId_in) {
        timelineType = timelineType_in;
        userId = userId_in;
        if (userId == 0) {
            throw new IllegalArgumentException(TAG + ": userId==0");
        }
        
        timelineDate = MyProvider.userIdToLongColumnValue(timelineType.columnNameTimelineDate(), userId);
        if (!TextUtils.isEmpty(timelineType.columnNameLatestMsgId())) {
            lastMsgId = MyProvider.userIdToLongColumnValue(timelineType.columnNameLatestMsgId(), userId);
            lastMsgDate = MyProvider.msgIdToLongColumnValue(Msg.SENT_DATE, lastMsgId);
            if (lastMsgDate == 0) {
                lastMsgId = 0;
            }
        }
    }
    
    /**
     * @return Id of the last downloaded message from this timeline
     */
    public long getLastMsgId() {
        return lastMsgId;
    }

    /**
     * @return Sent Date of the last downloaded message from this timeline
     */
    public long getLastMsgDate() {
        return lastMsgDate;
    }

    /**
     * @return Last date when this timeline was successfully downloaded
     */
    public long getTimelineDate() {
        return timelineDate;
    }

    /** If this message is newer than any we got earlier, remember it
     * @param msgId
     * @param msgDate may be 0 (will be retrieved here) 
     */
    public void onNewMsg(long msgId, long msgDate) {
        if (msgId != 0) {
            if (msgDate == 0) {
                msgDate = MyProvider.msgIdToLongColumnValue(Msg.SENT_DATE, msgId);
            }
            if (msgDate > lastMsgDate) {
                lastMsgDate = msgDate;
                lastMsgId = msgId;
                lastMsgChanged = true;
            }
        }
    }
    
    public void onTimelineDownloaded() {
        timelineDate = System.currentTimeMillis();
        timelineDateChanged = true;
    }
    
    /**
     * Persist the info into the Database
     */
    public void save() {
        boolean changed = timelineDateChanged || 
                (lastMsgChanged && !TextUtils.isEmpty(timelineType.columnNameLatestMsgId()));
        if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
            MyLog.v(TAG, "Timeline " + timelineType.save() 
                    + " for the user=" + MyProvider.userIdToName(userId) 
                    + " downloaded at " + (new Date(getTimelineDate()).toString())
                    + (changed ? "" : " not changed")                    
                    );
        }
        if (!changed) {
            return;
        }

        String sql = "";
        try {
            if (timelineDateChanged) {
                sql += timelineType.columnNameTimelineDate() + "=" + timelineDate;
            }
            if (lastMsgChanged && !TextUtils.isEmpty(timelineType.columnNameLatestMsgId())) {
                if (!TextUtils.isEmpty(sql)) {
                    sql += ", ";
                }
                sql += timelineType.columnNameLatestMsgId() + "=" + lastMsgId;
            }

            sql = "UPDATE " + MyDatabase.USER_TABLE_NAME + " SET " + sql 
                    + " WHERE " + User._ID + "=" + userId;

            SQLiteDatabase db = MyPreferences.getDatabase().getReadableDatabase();
            db.execSQL(sql);
            
            timelineDateChanged = false;
            lastMsgChanged = false;
        } catch (Exception e) {
            Log.e(TAG, "save: sql=" + sql + "; error=" + e.toString());
        }
    
    }
    
    /**
     * @return true if it's time to auto update this timeline
     */
    public boolean isTimeToAutoUpdate() {
        long frequencyMs = MyPreferences.getSyncFrequencyMs();
        long passedMs = System.currentTimeMillis() - getTimelineDate(); 
        boolean blnOut = (passedMs > frequencyMs);
        
        if (blnOut && MyLog.isLoggable(TAG, Log.VERBOSE)) {
            MyLog.v(TAG, "It's time to auto update " + timelineType.save() 
                    + " for the user=" + MyProvider.userIdToName(userId)
                    + ". Minutes passed=" + passedMs/1000/60);
        }
        return blnOut;
    }
}
