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

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;

/**
 * Helper class to construct sql WHERE clause selecting by UserIds
 * @author yvolk@yurivolkov.com
 */
public class AccountUserIds {
    private int mSize = 1;
    private String sqlUserIds = "";
    private long accountUserId = 0;

    /**
     * @param isCombined timeline
     * @param selectedUserId May be Account, maybe not. If the selected user is an account 
     * and timeline is combined, then ALL accounts should be included in the selection 
     */
    public AccountUserIds(boolean isCombined, long selectedUserId) {
        boolean isAccount = false;
        if (selectedUserId != 0) {
            MyAccount ma = MyContextHolder.get().persistentAccounts().fromUserId(selectedUserId);
            if (ma != null) {
                isAccount = true;
                accountUserId = ma.getUserId();
            }
        }
        // Allows to link to one or more accounts
        if (isCombined && isAccount || selectedUserId == 0) {
            StringBuilder sb = new StringBuilder();
            for (MyAccount ma : MyContextHolder.get().persistentAccounts().collection()) {
                if (sb.length() > 0) {
                    sb.append(", ");
                    mSize += 1;
                }
                sb.append(Long.toString(ma.getUserId()));
                if (accountUserId == 0) {
                    accountUserId = ma.getUserId();
                }
            }
            sqlUserIds = sb.toString();
        } else {
            sqlUserIds = Long.toString(selectedUserId);
        }
        if (mSize == 1) {
            sqlUserIds = "=" + sqlUserIds;
        } else {
            sqlUserIds = " IN (" + sqlUserIds + ")";
        }
    }

    public int size() {
        return mSize;
    }

    public String getSqlUserIds() {
        return sqlUserIds;
    }

    public long getAccountUserId() {
        return accountUserId;
    }
}
