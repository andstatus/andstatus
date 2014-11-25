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
import android.provider.BaseColumns;
import android.text.TextUtils;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.net.TimelinePosition;
import org.andstatus.app.util.MyLog;

import java.util.Date;


/**
 * Retrieve and save information about position of the latest downloaded timeline item. 
 * E.g. the "timeline item" is a "message" for Twitter and an "Activity" for Pump.Io.  
 */
public class LatestTimelineItem {
    private static final String TAG = LatestTimelineItem.class.getSimpleName();

    private TimelineTypeEnum timelineType;
    /**
     * The timeline is of this User, for all timeline types.
     */
    private long userId = 0;
    
    private boolean maySaveThis = false;
    
    TimelinePosition position = TimelinePosition.getEmpty();
    /**
     * 
     * 0 - none were downloaded
     */
    long timelineItemDate = 0;
    /**
     * Last date when this timeline was successfully downloaded.
     * It is used to know when it will be time for the next automatic update
     */
    long timelineDownloadedDate = 0;
    
    /**
     * We will update only what really changed
     */
    private boolean timelineItemChanged = false;
    private boolean timelineDateChanged = false;
    
    /**
     * Retrieve information about the last downloaded message from this timeline
     * @param userIdIn Should always be Id of the User of this timeline
     */
    public LatestTimelineItem(TimelineTypeEnum timelineTypeIn, long userIdIn) {
        timelineType = timelineTypeIn;
        userId = userIdIn;
        if (userId == 0) {
            throw new IllegalArgumentException(TAG + ": userId==0");
        }
        maySaveThis = !TextUtils.isEmpty(timelineType.columnNameTimelineDownloadedDate());
        
        if (maySaveThis) {
            timelineDownloadedDate = MyProvider.userIdToLongColumnValue(
                    timelineType.columnNameTimelineDownloadedDate(), userId);
            if (!TextUtils.isEmpty(timelineType.columnNameLatestTimelinePosition())) {
                timelineItemDate = MyProvider.userIdToLongColumnValue(
                        timelineType.columnNameLatestTimelineItemDate(), userId);
                if (timelineItemDate != 0) {
                    position = new TimelinePosition(MyProvider.userIdToStringColumnValue(
                            timelineType.columnNameLatestTimelinePosition(), userId));
                }
            }
        }
    }
    
    /**
     * @return Id of the last downloaded message from this timeline
     */
    public TimelinePosition getPosition() {
        return position;
    }

    /**
     * @return Sent Date of the last downloaded message from this timeline
     */
    public long getTimelineItemDate() {
        return timelineItemDate;
    }

    /**
     * @return Last date when this timeline was successfully downloaded
     */
    public long getTimelineDownloadedDate() {
        return timelineDownloadedDate;
    }

    /** A new Timeline Item was downloaded   */
    public void onNewMsg(TimelinePosition timelineItemPosition, long timelineItemDate) {
        if (timelineItemPosition != null 
                && !timelineItemPosition.isEmpty() 
                && (timelineItemDate > this.timelineItemDate)) {
            this.timelineItemDate = timelineItemDate;
            this.position = timelineItemPosition;
            timelineItemChanged = true;
        }
    }
    
    public void onTimelineDownloaded() {
        timelineDownloadedDate = System.currentTimeMillis();
        timelineDateChanged = true;
    }
    
    /**
     * Persist the info into the Database
     */
    public void save() {
        if (MyLog.isLoggable(TAG, MyLog.VERBOSE)) {
            MyLog.v(this, this.toString());
        }
        if (maySaveThis && changed()) {
            saveChanged();
        }
    }
    
    private boolean changed() {
        return timelineDateChanged || 
                (timelineItemChanged && !TextUtils.isEmpty(timelineType.columnNameLatestTimelinePosition()));
    }
    
    @Override
    public String toString() {
        return TAG + "[" + timelineType 
                    + forTheUser() 
                    + (getTimelineDownloadedDate() > 0 
                            ? " downloaded at " + (new Date(getTimelineDownloadedDate()).toString()) 
                            : " never downloaded")
                    + (changed() ? "" : " not changed")                    
                    + " latest position=" + MyProvider.quoteIfNotQuoted(position.getPosition())
                    + "]";
    }

    private String forTheUser() {
        return " for the userName=" + MyProvider.userIdToName(userId);
    }

    private void saveChanged() {
        String sql = "";
        try {
            if (timelineDateChanged) {
                sql += timelineType.columnNameTimelineDownloadedDate() + "=" + timelineDownloadedDate;
            }
            if (timelineItemChanged && !TextUtils.isEmpty(timelineType.columnNameLatestTimelinePosition())) {
                if (!TextUtils.isEmpty(sql)) {
                    sql += ", ";
                }
                sql += timelineType.columnNameLatestTimelinePosition() + "=" 
                + MyProvider.quoteIfNotQuoted(position.getPosition()) + ", "
                + timelineType.columnNameLatestTimelineItemDate() + "="
                + timelineItemDate;
            }

            sql = "UPDATE " + User.TABLE_NAME + " SET " + sql 
                    + " WHERE " + BaseColumns._ID + "=" + userId;

            SQLiteDatabase db = MyContextHolder.get().getDatabase().getReadableDatabase();
            db.execSQL(sql);
            
            timelineDateChanged = false;
            timelineItemChanged = false;
        } catch (Exception e) {
            MyLog.e(this, "save: sql='" + sql + "'", e);
        }
    }
    
    /**
     * @return true if it's time to auto update this timeline
     */
    public boolean isTimeToAutoUpdate() {
        long frequencyMs = MyPreferences.getSyncFrequencyMs();
        long passedMs = System.currentTimeMillis() - getTimelineDownloadedDate(); 
        boolean blnOut = passedMs > frequencyMs;
        
        if (blnOut && MyLog.isLoggable(TAG, MyLog.VERBOSE)) {
            MyLog.v(this, "It's time to auto update " + timelineType 
                    + forTheUser()
                    + ". Minutes passed=" + passedMs/1000/60);
        }
        return blnOut;
    }
}
