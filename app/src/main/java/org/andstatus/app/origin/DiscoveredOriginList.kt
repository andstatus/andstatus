/**
 * Copyright (C) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.andstatus.app.origin

import android.os.Bundle
import android.view.MenuItem
import org.andstatus.app.R
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.service.CommandData
import org.andstatus.app.service.CommandEnum
import org.andstatus.app.service.MyServiceEvent
import org.andstatus.app.service.MyServiceEventsListener
import org.andstatus.app.service.MyServiceEventsReceiver
import org.andstatus.app.service.MyServiceManager

class DiscoveredOriginList : OriginList(), MyServiceEventsListener {
    var mServiceConnector: MyServiceEventsReceiver? = MyServiceEventsReceiver( MyContextHolder.myContextHolder.getNow(), this)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (DiscoveredOrigins.get().isEmpty()) {
            mSwipeLayout.isRefreshing = true
            manualSync()
        }
    }

    override fun onRefresh() {
        manualSync()
    }

    override fun getOrigins(): Iterable<Origin?>? {
        return DiscoveredOrigins.get()
    }

    private fun manualSync() {
        MyServiceManager.Companion.setServiceAvailable()
        MyServiceManager.Companion.sendForegroundCommand(
                CommandData.Companion.newOriginCommand(CommandEnum.GET_OPEN_INSTANCES,
                         MyContextHolder.myContextHolder.getNow().origins().firstOfType(OriginType.GNUSOCIAL)
                ))
    }

    override fun onResume() {
        super.onResume()
        MyServiceManager.Companion.setServiceAvailable()
        mServiceConnector.registerReceiver(this)
    }

    override fun onPause() {
        super.onPause()
        mServiceConnector.unregisterReceiver(this)
    }

    override fun onReceive(commandData: CommandData?, myServiceEvent: MyServiceEvent?) {
        if (MyServiceEvent.AFTER_EXECUTING_COMMAND == myServiceEvent && CommandEnum.GET_OPEN_INSTANCES == commandData.getCommand()) {
            fillList()
            mSwipeLayout.isRefreshing = false
        }
    }

    override fun getLayoutResourceId(): Int {
        return R.layout.my_list_swipe
    }

    override fun getMenuResourceId(): Int {
        return R.menu.discovered_origin_list
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item.getItemId()) {
            R.id.sync_menu_item -> manualSync()
            else -> {
            }
        }
        return super.onOptionsItemSelected(item)
    }
}