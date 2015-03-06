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

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.MyDatabase.Msg;
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.util.MyLog;

import java.util.Date;


/**
 * Manages minimal information about the latest downloaded message by one User. 
 * We count messages where the User is either a Sender or an Author
 */
public final class UserMsg {
    private static final String TAG = UserMsg.class.getSimpleName();

    /**
     * The User
     */
    private long userId = 0;
    
    /**
     * The id of the latest downloaded Message by this User
     * 0 - none were downloaded
     */
    private long lastMsgId = 0;
    /**
     * 0 - none were downloaded
     */
    private long lastMsgDate = 0;
    
    /**
     * We will update only what really changed
     */
    private boolean changed = false;
    
    /**
     * Retrieve from the database information about the last downloaded message by this User
     */
    public UserMsg(long userIdIn) {
        userId = userIdIn;
        if (userId == 0) {
            throw new IllegalArgumentException(TAG + ": userId==0");
        }
        lastMsgId = MyProvider.userIdToLongColumnValue(User.USER_MSG_ID, userId);
        lastMsgDate = MyProvider.userIdToLongColumnValue(User.USER_MSG_DATE, userId);
    }

    /**
     * All information is supplied in this constructor, so it doesn't lookup anything in the database
     */
    public UserMsg(long userIdIn, long msgId, long msgDate) {
        userId = userIdIn;
        if (userId == 0) {
            throw new IllegalArgumentException(TAG + ": userId==0");
        }
        onNewMsg(msgId, msgDate);
    }
    
    public long getUserId() {
        return userId;
    }
    
    /**
     * @return Id of the last downloaded message by this User
     */
    public long getLastMsgId() {
        return lastMsgId;
    }

    /**
     * @return Sent Date of the last downloaded message by this User
     */
    public long getLastMsgDate() {
        return lastMsgDate;
    }

    /** If this message is newer than any we got earlier, remember it
     * @param msgDate may be 0 (will be retrieved here) 
     */
    public void onNewMsg(long msgId, long msgDateIn) {
        if (msgId == 0) {
            return; 
        }
        long msgDate = msgDateIn;
        if (msgDate == 0) {
            msgDate = MyProvider.msgIdToLongColumnValue(Msg.SENT_DATE, msgId);
        }
        if (msgDate > lastMsgDate) {
            lastMsgDate = msgDate;
            lastMsgId = msgId;
            changed = true;
        }
    }
    
    /**
     * Persist the info into the Database
     * @return true if succeeded
     */
    public boolean save() {
        boolean ok = true;
        if (MyLog.isLoggable(TAG, MyLog.VERBOSE)) {
            MyLog.v(this, "User=" + MyProvider.userIdToWebfingerId(userId) 
                    + " Latest msg at " + (new Date(getLastMsgDate()).toString())
                    + (changed ? "" : " not changed")                    
                    );
        }
        if (!changed) {
            return ok;
        }

        // As a precaution compare with stored values ones again
        long msgDate = MyProvider.userIdToLongColumnValue(User.USER_MSG_DATE, userId);
        if (msgDate > lastMsgDate) {
            lastMsgDate = msgDate;
            lastMsgId = MyProvider.userIdToLongColumnValue(User.USER_MSG_ID, userId);
            MyLog.v(this, "There is newer information in the database. User=" + MyProvider.userIdToWebfingerId(userId) 
                    + " Latest msg at " + (new Date(getLastMsgDate()).toString()));
            changed = false;
            return ok;
        }

        String sql = "";
        try {
            sql += User.USER_MSG_ID + "=" + lastMsgId;
            sql += ", " + User.USER_MSG_DATE + "=" + lastMsgDate;

            sql = "UPDATE " + User.TABLE_NAME + " SET " + sql 
                    + " WHERE " + BaseColumns._ID + "=" + userId;

            SQLiteDatabase db = MyContextHolder.get().getDatabase().getReadableDatabase();
            db.execSQL(sql);
            
            changed = false;
        } catch (Exception e) {
            ok = false;
            MyLog.e(this, "save: sql='" + sql + "'", e);
        }
        return ok;
    }
}
