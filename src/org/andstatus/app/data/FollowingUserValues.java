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

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import org.andstatus.app.data.MyDatabase.FollowingUser;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;

/**
 * Helper class to update the "Following User" information (see {@link MyDatabase.FollowingUser}) 
 * @author yvolk@yurivolkov.com
 */
public class FollowingUserValues {
    public long userId;
    public long followingUserId;
    private ContentValues contentValues = new ContentValues();

    /**
     * Move all keys that belong to {@link FollowingUser} table from values to the newly created ContentValues.
     * @param userId - first part of key
     * @param followingUserId - second part of key
     * @param values - all other fields (currently only 1)
     * @return
     */
    public static FollowingUserValues valueOf(long userId, long followingUserId, ContentValues values) {
        FollowingUserValues userValues = new FollowingUserValues(userId, followingUserId);
        MyProvider.moveBooleanKey(FollowingUser.USER_FOLLOWED, values, userValues.contentValues);
        return userValues;
    }
    
    public FollowingUserValues(long userId, long followingUserId) {
        this.userId = userId;
        this.followingUserId = followingUserId;
    }
    
    /**
     * Explicitly set the "following" flag 
     */
    public void setFollowed(boolean followed) {
        contentValues.put(FollowingUser.USER_FOLLOWED, followed);
    }
    
    /**
     * Update information in the database 
     */
    public void update(SQLiteDatabase db) {
        boolean followed = false;
        if (userId != 0 && followingUserId != 0 && contentValues.containsKey(FollowingUser.USER_FOLLOWED)) {
            // This works for API 17 but not for API 10:
            // followed = contentValues.getAsBoolean(FollowingUser.USER_FOLLOWED);
            followed = SharedPreferencesUtil.isTrue(contentValues.get(FollowingUser.USER_FOLLOWED));
        } else {
            // Don't change anything as there is no information
            return;
        }
        for (int pass=0; pass<5; pass++) {
            try {
                tryToUpdate(db, followed);
                break;
       // This is since API 11, see http://developer.android.com/reference/android/database/sqlite/SQLiteDatabaseLockedException.html
       //     } catch (SQLiteDatabaseLockedException e) {
            } catch (SQLiteException e) {
                MyLog.i(this, "update, Database is locked, pass=" + pass, e);
                try {
                    Thread.sleep(Math.round((Math.random() + 1) * 200));
                } catch (InterruptedException e2) {
                    MyLog.e(this, e2);
                }
            }
        }
    }

    private void tryToUpdate(SQLiteDatabase db, boolean followed) {
        // TODO: create universal dExists method...
        String where = MyDatabase.FollowingUser.USER_ID + "=" + userId
                + " AND " + MyDatabase.FollowingUser.FOLLOWING_USER_ID + "=" + followingUserId;
        String sql = "SELECT * FROM " + FollowingUser.TABLE_NAME + " WHERE " + where;

        Cursor c = null;
        boolean exists = false;
        try {
            c = db.rawQuery(sql, null);
            if (c != null && c.getCount() > 0) {
                exists = true;
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }

        if (exists) {
            db.update(FollowingUser.TABLE_NAME, contentValues, where,
                    null);
        } else if (followed) {
            // There was no such row
            ContentValues cv = new ContentValues(contentValues);
            // Add Key fields
            cv.put(FollowingUser.USER_ID, userId);
            cv.put(FollowingUser.FOLLOWING_USER_ID, followingUserId);
            
            db.insert(FollowingUser.TABLE_NAME, null, cv);
        }
    }
}
