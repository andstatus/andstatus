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

package org.andstatus.app.user;

import android.content.ContentValues;
import android.database.Cursor;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.andstatus.app.context.MyContext;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.table.ActorTable;
import org.andstatus.app.database.table.UserTable;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtils;
import org.andstatus.app.util.TriState;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

/**
 * @author yvolk@yurivolkov.com
 */
public class User {
    public static final User EMPTY = new User(0, "(empty)", TriState.FALSE, Collections.emptySet());
    public long userId = 0L;
    private String knownAs = "";
    private TriState isMyUser = TriState.UNKNOWN;
    public final Set<Long> actorIds;

    @NonNull
    public static User load(@NonNull MyContext myContext, long actorId) {
        return myContext.users().userFromActorId(actorId, () -> loadInternal(myContext, actorId));
    }

    private static User loadInternal(@NonNull MyContext myContext, long actorId) {
        if (actorId == 0 || MyAsyncTask.isUiThread()) return User.EMPTY;
        final String sql = "SELECT " + Actor.getActorAndUserSqlColumns(false)
                + " FROM " + Actor.getActorAndUserSqlTables()
                + " WHERE " + ActorTable.TABLE_NAME + "." + ActorTable._ID + "=" + actorId;
        final Function<Cursor, User> function = cursor -> fromCursor(myContext, cursor);
        return MyQuery.get(myContext, sql, function).stream().findFirst().orElse(EMPTY);
    }

    @NonNull
    public static User fromCursor(MyContext myContext, Cursor cursor) {
        final long userId = cursor.getLong(1);
        User user1 = myContext.users().users.getOrDefault(userId, User.EMPTY);
        return user1.nonEmpty() ? user1
                : new User(userId, DbUtils.getString(cursor, UserTable.KNOWN_AS),
                    DbUtils.getTriState(cursor, UserTable.IS_MY),
                    loadActors(myContext, userId));
    }

    public User(long userId, String knownAs, TriState isMyUser, Set<Long> actorIds) {
        this.userId = userId;
        this.knownAs = knownAs;
        this.isMyUser = isMyUser;
        this.actorIds = actorIds;
    }

    @NonNull
    public static Set<Long> loadActors(MyContext myContext, long userId) {
        return MyQuery.getLongs(myContext, "SELECT " + ActorTable._ID
                + " FROM " + ActorTable.TABLE_NAME
                + " WHERE " + ActorTable.USER_ID + "=" + userId);
    }

    @NonNull
    public static User getNew() {
        return new User(0, "", TriState.UNKNOWN, new HashSet<>());
    }

    public boolean nonEmpty() {
        return !isEmpty();
    }

    public boolean isEmpty() {
        return this == EMPTY || (userId == 0 && StringUtils.isEmpty(knownAs));
    }

    @Override
    public String toString() {
        if (this == EMPTY) {
            return "User:EMPTY";
        }
        String str = User.class.getSimpleName();
        String members = "id=" + userId;
        if (!StringUtils.isEmpty(knownAs)) {
            members += "; knownAs=" + knownAs;
        }
        if (isMyUser.known) {
            members += "; isMine=" + isMyUser.toBoolean(false);
        }
        return str + "{" + members + "}";
    }

    public String getKnownAs() {
        return knownAs;
    }

    public void save(MyContext myContext) {
        final ContentValues values = toContentValues(myContext);
        if (this == EMPTY || values.size() == 0) return;
        if (userId == 0) {
            userId = DbUtils.addRowWithRetry(myContext, UserTable.TABLE_NAME, values, 3);
            MyLog.v(this, "Added " + this);
        } else {
            DbUtils.updateRowWithRetry(myContext, UserTable.TABLE_NAME, userId, values, 3);
            MyLog.v(this, "Updated " + this);
        }
    }

    private ContentValues toContentValues(MyContext myContext) {
        ContentValues values = new ContentValues();
        if (StringUtils.nonEmpty(knownAs)) values.put(UserTable.KNOWN_AS, knownAs);
        if (isMyUser.known) values.put(UserTable.IS_MY, isMyUser.id);
        return values;
    }

    public void setIsMyUser(@NonNull TriState isMyUser) {
        this.isMyUser = isMyUser;
    }

    @NonNull
    public TriState isMyUser() {
        return isMyUser;
    }

    public void setKnownAs(String knownAs) {
        this.knownAs = knownAs;
    }
}
