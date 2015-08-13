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
public class SelectedUserIds {
    private int mSize = 0;
    private String sqlUserIds = "";

    /**
     * @param isCombined timeline
     * @param selectedUserId May be an Account, maybe not. If the selected user is an account
     * and timeline is combined, then ALL accounts of ALL Social networks should be included in the selection
     * TODO: Social network scope selection ( i.e. for one {@link org.andstatus.app.origin.Origin} )
     */
    public SelectedUserIds(boolean isCombined, long selectedUserId) {
        boolean isAccount = MyContextHolder.get().persistentAccounts().isAccountUserId(selectedUserId);
        // Allows to link to one or more accounts
        if (isCombined && isAccount) {
            StringBuilder sb = new StringBuilder();
            for (MyAccount ma : MyContextHolder.get().persistentAccounts().collection()) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                mSize += 1;
                sb.append(Long.toString(ma.getUserId()));
            }
            sqlUserIds = sb.toString();
        } else if (selectedUserId != 0) {
            mSize = 1;
            sqlUserIds = Long.toString(selectedUserId);
        }
    }

    public int size() {
        return mSize;
    }

    public String getList() {
        return sqlUserIds;
    }

    public String getSql() {
        if (mSize == 1) {
            return "=" + sqlUserIds;
        } else if (mSize > 1) {
            return " IN (" + sqlUserIds + ")";
        }
        return "";
    }
}
