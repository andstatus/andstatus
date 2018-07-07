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

package org.andstatus.app.actor;

import android.os.Bundle;

import org.andstatus.app.R;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyServiceManager;

/**
 * @author yvolk@yurivolkov.com
 */
public class FollowersList extends ActorList {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    private long getFollowedActorId() {
        return centralItemId;
    }

    protected void syncWithInternet(boolean manuallyLaunched) {
        final String method = "syncWithInternet";
        showSyncing(method, getText(R.string.options_menu_sync));
        CommandEnum command = mActorListType == ActorListType.FOLLOWERS ?
                CommandEnum.GET_FOLLOWERS : CommandEnum.GET_FRIENDS;
        MyServiceManager.sendForegroundCommand(
                (CommandData.newActorCommand(command,
                        getFollowedActorId(), "")).setManuallyLaunched(manuallyLaunched));
    }

    @Override
    protected ActorListLoader newSyncLoader(Bundle args) {
        return new FriendsAndFollowersListLoader(mActorListType, getCurrentMyAccount(), getFollowedActorId(),
                getParsedUri().getSearchQuery());
    }
}
