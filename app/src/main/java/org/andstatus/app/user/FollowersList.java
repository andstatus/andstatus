/*
 * Copyright (c) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import org.andstatus.app.R;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.MyLog;

/**
 * @author yvolk@yurivolkov.com
 */
public class FollowersList extends UserList {

    private long getFollowedUserId() {
        return centralItemId;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.userlist, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.sync_menu_item:
                manualSyncWithInternet(true);
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    protected void manualSyncWithInternet(boolean manuallyLaunched) {
        final String method = "manualSync";
        long originId = MyQuery.userIdToLongColumnValue(MyDatabase.User.ORIGIN_ID, getFollowedUserId());
        if (originId == 0) {
            MyLog.e(this, "Unknown origin for userId=" + getFollowedUserId());
            return;
        }
        if (!ma.isValid() || ma.getOriginId() != originId) {
            ma = MyContextHolder.get().persistentAccounts().fromUserId(getFollowedUserId());
            if (!ma.isValid()) {
                ma = MyContextHolder.get().persistentAccounts().findFirstSucceededMyAccountByOriginId(originId);
            }
        }

        MyServiceManager.sendForegroundCommand(
                (new CommandData(CommandEnum.GET_FOLLOWERS, ma.getAccountName(), getFollowedUserId()))
                        .setManuallyLaunched(manuallyLaunched));
    }

    @Override
    protected SyncLoader newSyncLoader(Bundle args) {
        return new FollowersListLoader(mUserListType, getMa(), getFollowedUserId(), mIsListCombined);
    }
}
