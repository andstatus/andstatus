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
package org.andstatus.app.actor

import android.os.Bundle
import org.andstatus.app.R
import org.andstatus.app.net.social.Actor
import org.andstatus.app.service.CommandData
import org.andstatus.app.service.CommandEnum
import org.andstatus.app.service.MyServiceManager

/**
 * @author yvolk@yurivolkov.com
 */
class FollowersScreen : ActorsScreen() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    private fun getFollowedActorId(): Long {
        return centralItemId
    }

    override fun syncWithInternet(manuallyLaunched: Boolean) {
        val method = "syncWithInternet"
        showSyncing(method, getText(R.string.options_menu_sync))
        val command = if (actorsScreenType == ActorsScreenType.FOLLOWERS) CommandEnum.GET_FOLLOWERS else CommandEnum.GET_FRIENDS
        MyServiceManager.Companion.sendForegroundCommand(
                CommandData.Companion.newActorCommand(command, Actor.Companion.load(myContext, getFollowedActorId()), "")
                        .setManuallyLaunched(manuallyLaunched))
    }

    override fun newSyncLoader(args: Bundle?): ActorsLoader {
        return FriendsAndFollowersLoader(myContext, actorsScreenType, parsedUri.getOrigin(myContext),
                getFollowedActorId(), parsedUri.searchQuery)
    }
}