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

package org.andstatus.app.data;

import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.database.table.ActorTable;
import org.andstatus.app.util.MyLog;

import java.util.Date;


/**
 * Manages minimal information about the latest downloaded message by one User. 
 * We count messages where the User is either a Sender or an Author
 */
public final class ActorActivity {
    private static final String TAG = ActorActivity.class.getSimpleName();

    private long actorId = 0;
    
    /**
     * The id of the latest downloaded Message by this User
     * 0 - none were downloaded
     */
    private long lastActivityId = 0;
    /**
     * 0 - none were downloaded
     */
    private long lastActivityDate = 0;
    
    /**
     * We will update only what really changed
     */
    private boolean changed = false;
    
    /**
     * Retrieve from the database information about the last downloaded activity by this User
     */
    public ActorActivity(long userIdIn) {
        actorId = userIdIn;
        if (actorId == 0) {
            throw new IllegalArgumentException(TAG + ": actorId==0");
        }
        lastActivityId = MyQuery.userIdToLongColumnValue(ActorTable.ACTOR_ACTIVITY_ID, actorId);
        lastActivityDate = MyQuery.userIdToLongColumnValue(ActorTable.ACTOR_ACTIVITY_DATE, actorId);
    }

    /**
     * All information is supplied in this constructor, so it doesn't lookup anything in the database
     */
    public ActorActivity(long userIdIn, long activityId, long activityDate) {
        if (userIdIn != 0 && activityId != 0) {
            actorId = userIdIn;
            onNewActivity(activityId, activityDate);
        }
    }
    
    public long getActorId() {
        return actorId;
    }
    
    /**
     * @return Id of the last downloaded message by this User
     */
    public long getLastActivityId() {
        return lastActivityId;
    }

    /**
     * @return Sent Date of the last downloaded message by this User
     */
    public long getLastActivityDate() {
        return lastActivityDate;
    }

    /** If this message is newer than any we got earlier, remember it
     * @param updatedDateIn may be 0 (will be retrieved here)
     */
    public void onNewActivity(long activityId, long updatedDateIn) {
        if (actorId == 0 || activityId == 0) {
            return; 
        }
        long activityDate = updatedDateIn;
        if (activityDate == 0) {
            activityDate = MyQuery.activityIdToLongColumnValue(ActivityTable.UPDATED_DATE, activityId);
        }
        if (activityDate > lastActivityDate) {
            lastActivityDate = activityDate;
            lastActivityId = activityId;
            changed = true;
        }
    }
    
    /**
     * Persist the info into the Database
     * @return true if succeeded
     */
    public boolean save() {
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(this, "User " + actorId + ": " + MyQuery.actorIdToWebfingerId(actorId)
                    + " Latest activity update at " + (new Date(getLastActivityDate()).toString())
                    + (changed ? "" : " not changed")                    
                    );
        }
        if (!changed) {
            return true;
        }

        // As a precaution compare with stored values ones again
        long activityDate = MyQuery.userIdToLongColumnValue(ActorTable.ACTOR_ACTIVITY_DATE, actorId);
        if (activityDate > lastActivityDate) {
            lastActivityDate = activityDate;
            lastActivityId = MyQuery.userIdToLongColumnValue(ActorTable.ACTOR_ACTIVITY_ID, actorId);
            if (MyLog.isVerboseEnabled()) {
                MyLog.v(this, "There is newer information in the database. User " + actorId + ": "
                        + MyQuery.actorIdToWebfingerId(actorId)
                        + " Latest activity at " + (new Date(getLastActivityDate()).toString()));
            }
            changed = false;
            return true;
        }

        String sql = "";
        try {
            sql += ActorTable.ACTOR_ACTIVITY_ID + "=" + lastActivityId;
            sql += ", " + ActorTable.ACTOR_ACTIVITY_DATE + "=" + lastActivityDate;

            sql = "UPDATE " + ActorTable.TABLE_NAME + " SET " + sql
                    + " WHERE " + BaseColumns._ID + "=" + actorId;

            SQLiteDatabase db = MyContextHolder.get().getDatabase();
            if (db == null) {
                MyLog.v(this, "Database is null");
                return false;
            }
            db.execSQL(sql);
            
            changed = false;
        } catch (Exception e) {
            MyLog.e(this, "save: sql='" + sql + "'", e);
            return false;
        }
        return true;
    }
}
