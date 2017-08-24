/*
 * Copyright (C) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.support.annotation.NonNull;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.AudienceTable;

import java.util.HashSet;
import java.util.Set;

public class Audience {
    private final Set<MbUser> recipients = new HashSet<>();

    public static Audience fromMsgId(long originId, long msgId) {
        String where = AudienceTable.MSG_ID + "=" + msgId;
        String sql = "SELECT " + AudienceTable.USER_ID
                + " FROM " + AudienceTable.TABLE_NAME
                + " WHERE " + where;
        Audience audience = new Audience();
        for (long recipientId : MyQuery.getLongs(sql)) {
            audience.add(MbUser.fromOriginAndUserId(originId, recipientId));
        }
        return audience;
    }

    public MbUser getFirst() {
        if (recipients.isEmpty()) {
            return MbUser.EMPTY;
        }
        return recipients.iterator().next();
    }

    public String getUserNames() {
        StringBuilder sb = new StringBuilder();
        for (MbUser user : recipients) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(user.getTimelineUserName());
        }
        return sb.toString();
    }

    public Set<MbUser> getRecipients() {
        return recipients;
    }

    public boolean nonEmpty() {
        return !isEmpty();
    }

    public boolean isEmpty() {
        return recipients.isEmpty();
    }

    public void addAll(@NonNull Audience audience) {
        for (MbUser user : audience.recipients) {
            add(user);
        }
    }

    public void add(@NonNull MbUser user) {
        recipients.add(user);
    }

    public boolean hasMyAccount(MyContext myContext) {
        return getMyAccount(myContext).isValid();
    }

    @NonNull
    public MyAccount getMyAccount(MyContext myContext) {
        for (MbUser user : recipients) {
            MyAccount myAccount = myContext.persistentAccounts().fromMbUser(user);
            if (myAccount.isValid()) {
                return myAccount;
            }
        }
        return MyAccount.EMPTY;
    }

    @NonNull
    public boolean has(MbUser mbUser) {
        for (MbUser user : recipients) {
            if (user.equals(mbUser)) {
                return true;
            }
        }
        return false;
    }

    public boolean has(long userId) {
        for (MbUser user : recipients) {
            if (user.userId == userId) {
                return true;
            }
        }
        return false;
    }
}
