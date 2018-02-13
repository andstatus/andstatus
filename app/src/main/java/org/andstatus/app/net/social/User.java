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

package org.andstatus.app.net.social;

import android.content.ContentValues;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.andstatus.app.context.MyContext;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.database.table.UserTable;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtils;
import org.andstatus.app.util.TriState;

/**
 * @author yvolk@yurivolkov.com
 */
public class User {
    public static final User EMPTY = new User();
    public long userId = 0L;
    private String knownAs = "";
    private TriState isMyUser = TriState.UNKNOWN;

    public User() {
    }

    public boolean nonEmpty() {
        return !isEmpty();
    }

    public boolean isEmpty() {
        return this == EMPTY;
    }

    @Override
    public String toString() {
        if (this == EMPTY) {
            return "User:EMPTY";
        }
        String str = User.class.getSimpleName();
        String members = "id=" + userId;
        if (!TextUtils.isEmpty(knownAs)) {
            members += "; knownAs=" + knownAs;
        }
        if (isMyUser.known()) {
            members += "; isMine=" + isMyUser.toBoolean(false);
        }
        return str + "{" + members + "}";
    }

    public String getKnownAs() {
        return knownAs;
    }

    public void save(MyContext myContext) {
        if (userId == 0) {
            userId = DbUtils.addRowWithRetry(myContext, UserTable.TABLE_NAME, toContentValues(myContext), 3);
            MyLog.v(this, "Added " + this);
        } else {
            DbUtils.updateRowWithRetry(myContext, UserTable.TABLE_NAME, userId, toContentValues(myContext), 3);
            MyLog.v(this, "Updated " + this);
        }
    }

    private ContentValues toContentValues(MyContext myContext) {
        ContentValues values = new ContentValues();
        if (StringUtils.nonEmpty(knownAs)) values.put(UserTable.KNOWN_AS, knownAs);
        if (isMyUser.known()) values.put(UserTable.IS_MY, isMyUser.toBoolean(false));
        return values;
    }

    public void setIsMyUser(@NonNull TriState isMyUser) {
        this.isMyUser = isMyUser;
    }

    @NonNull
    public TriState getIsMyUser() {
        return isMyUser;
    }

    public void setKnownAs(String knownAs) {
        this.knownAs = knownAs;
    }
}
