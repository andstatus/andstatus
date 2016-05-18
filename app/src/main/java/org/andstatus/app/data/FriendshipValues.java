/**
 * Copyright (C) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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
import android.database.sqlite.SQLiteDatabaseLockedException;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.database.FriendshipTable;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;

/**
 * Helper class to update the "Friendship" information (see {@link FriendshipTable})
 * @author yvolk@yurivolkov.com
 */
public class FriendshipValues {
    public long userId;
    public long friendId;
    private ContentValues contentValues = new ContentValues();

    /**
     * Move all keys that belong to {@link FriendshipTable} table from values to the newly created ContentValues.
     * @param userId - first part of key
     * @param friendId - second part of key
     * @param values - all other fields (currently only 1)
     * @return
     */
    public static FriendshipValues valueOf(long userId, long friendId, ContentValues values) {
        FriendshipValues userValues = new FriendshipValues(userId, friendId);
        MyQuery.moveBooleanKey(FriendshipTable.FOLLOWED, "", values, userValues.contentValues);
        return userValues;
    }

    public static void setFollowed(long followerId, long friendId) {
        setFollowed(followerId, friendId, true);
    }

    public static void setNotFollowed(long followerId, long friendId) {
        setFollowed(followerId, friendId, false);
    }

    private static void setFollowed(long followerId, long friendId, boolean followed) {
        FriendshipValues fu = new FriendshipValues(followerId, friendId);
        fu.setFollowed(followed);
        fu.update(MyContextHolder.get().getDatabase());
    }

    public FriendshipValues(long userId, long friendId) {
        this.userId = userId;
        this.friendId = friendId;
    }
    
    /**
     * Explicitly set the "followed" flag
     */
    public void setFollowed(boolean followed) {
        contentValues.put(FriendshipTable.FOLLOWED, followed);
    }
    
    /**
     * Update information in the database 
     */
    public void update(SQLiteDatabase db) {
        boolean followed = false;
        if (db != null &&  userId != 0 && friendId != 0 && contentValues.containsKey(FriendshipTable.FOLLOWED)) {
            followed = SharedPreferencesUtil.isTrue(contentValues.get(FriendshipTable.FOLLOWED));
        } else {
            // Don't change anything as there is no information
            return;
        }
        for (int pass=0; pass<5; pass++) {
            try {
                tryToUpdate(db, followed);
                break;
            } catch (SQLiteDatabaseLockedException e) {
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
        String where = FriendshipTable.USER_ID + "=" + userId
                + " AND " + FriendshipTable.FRIEND_ID + "=" + friendId;
        String sql = "SELECT * FROM " + FriendshipTable.TABLE_NAME + " WHERE " + where;

        Cursor cursor = null;
        boolean exists = false;
        try {
            cursor = db.rawQuery(sql, null);
            exists = cursor.moveToFirst();
        } finally {
            DbUtils.closeSilently(cursor);
        }

        if (exists) {
            db.update(FriendshipTable.TABLE_NAME, contentValues, where,
                    null);
        } else if (followed) {
            // There was no such row
            ContentValues cv = new ContentValues(contentValues);
            // Add Key fields
            cv.put(FriendshipTable.USER_ID, userId);
            cv.put(FriendshipTable.FRIEND_ID, friendId);
            
            db.insert(FriendshipTable.TABLE_NAME, null, cv);
        }
    }
}
