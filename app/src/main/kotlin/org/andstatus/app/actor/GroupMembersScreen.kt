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
import org.andstatus.app.service.CommandData
import org.andstatus.app.service.MyServiceManager

/**
 * @author yvolk@yurivolkov.com
 */
class GroupMembersScreen : ActorsScreen(GroupMembersScreen::class) {

    override fun syncWithInternet(manuallyLaunched: Boolean) {
        showSyncing("syncWithInternet", getText(R.string.options_menu_sync))
        CommandData.newActorCommand(actorsScreenType.syncCommand, centralActor, "")
            .setManuallyLaunched(manuallyLaunched)
            .let(MyServiceManager::sendForegroundCommand)
    }

    override fun newSyncLoader(args: Bundle?): ActorsLoader {
        return GroupMembersLoader(myContext, actorsScreenType, parsedUri.getOrigin(myContext),
            centralActor, parsedUri.searchQuery)
    }
}
