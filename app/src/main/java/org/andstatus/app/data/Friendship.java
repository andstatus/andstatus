/*
 * Copyright (C) 2018 yvolk (Yuri Volkov), http://yurivolkov.com
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
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabaseLockedException;

import org.andstatus.app.context.MyContext;
import org.andstatus.app.database.table.FriendshipTable;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.TriState;

/**
 * Helper class to update the "Friendship" information (see {@link FriendshipTable})
 * @author yvolk@yurivolkov.com
 */
public class Friendship {
    private long friendId;
    private TriState followedBy = TriState.UNKNOWN;
    private long actorId;

    public static void setFollowed(MyContext myContext, Actor actor, TriState followed, Actor friend) {
        if (actor.actorId == 0 || friend.actorId == 0) return;

        Friendship fu = new Friendship(actor.actorId, friend.actorId);
        fu.followedBy = followed.isTrue && actor.isSameUser(friend)
                ? TriState.FALSE
                : followed;
        fu.update(myContext.getDatabase());
    }

    private Friendship(long actorId, long friendId) {
        this.actorId = actorId;
        this.friendId = friendId;
    }
    
    /**
     * Update information in the database 
     */
    private void update(SQLiteDatabase db) {
        if (followedBy.unknown || actorId == 0 || friendId == 0 || db == null) return;
        boolean followed = followedBy.toBoolean(false);
        for (int pass=0; pass<5; pass++) {
            try {
                tryToUpdate(db, followed);
                break;
            } catch (SQLiteDatabaseLockedException e) {
                MyLog.i(this, "update, Database is locked, pass=" + pass, e);
                if (DbUtils.waitBetweenRetries("update")) {
                    break;
                }
            }
        }
    }

    private void tryToUpdate(SQLiteDatabase db, boolean followed) {
        String where = FriendshipTable.ACTOR_ID + "=" + actorId
                + " AND " + FriendshipTable.FRIEND_ID + "=" + friendId;
        String sql = "SELECT * FROM " + FriendshipTable.TABLE_NAME + " WHERE " + where;

        ContentValues cv = new ContentValues();
        cv.put(FriendshipTable.FOLLOWED, followed ? "1" : "0");
        if (MyQuery.dExists(db, sql)) {
            db.update(FriendshipTable.TABLE_NAME, cv, where, null);
        } else if (followed) {
            cv.put(FriendshipTable.ACTOR_ID, actorId);
            cv.put(FriendshipTable.FRIEND_ID, friendId);
            db.insert(FriendshipTable.TABLE_NAME, null, cv);
        }
    }

}
