/*
 * Copyright (C) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.timeline.meta

import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import android.view.MenuItem
import android.view.View
import org.andstatus.app.R
import org.andstatus.app.account.MyAccount
import org.andstatus.app.timeline.ContextMenuHeader
import org.andstatus.app.timeline.LoadableListActivity
import org.andstatus.app.timeline.meta.TimelineTitle.Companion.from
import org.andstatus.app.util.MyLog
import org.andstatus.app.view.MyContextMenu

class ManageTimelinesContextMenu(listActivity: LoadableListActivity<ManageTimelinesViewItem>) : MyContextMenu(listActivity, 0) {

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenuInfo?) {
        val method = "onCreateContextMenu"
        super.onCreateContextMenu(menu, v, menuInfo)
        val viewItem = getViewItem()
        var order = 0
        try {
            val title: TimelineTitle = from(getMyContext(), viewItem.timeline)
            ContextMenuHeader(getActivity(), menu)
                    .setTitle(title.title)
                    .setSubtitle(title.subTitle)
            ManageTimelinesContextMenuItem.OPEN_TIMELINE.addTo(menu, ++order, R.string.show_timeline_messages)
            if (viewItem.timeline.isSyncable()) {
                ManageTimelinesContextMenuItem.SYNC_NOW.addTo(menu, ++order, R.string.options_menu_sync)
            }
            if (!viewItem.timeline.isRequired()) {
                ManageTimelinesContextMenuItem.DELETE.addTo(menu, ++order, R.string.button_delete)
            }
            if (getMyContext().timelines().getDefault() != viewItem.timeline) {
                ManageTimelinesContextMenuItem.MAKE_DEFAULT.addTo(menu, ++order, R.string.set_as_default_timeline)
            }
            ManageTimelinesContextMenuItem.FORGET_SYNC_EVENTS.addTo(menu, ++order, R.string.forget_sync_events)
        } catch (e: Exception) {
            MyLog.w(this, method, e)
        }
    }

    fun onContextItemSelected(item: MenuItem): Boolean {
        val contextMenuItem: ManageTimelinesContextMenuItem = ManageTimelinesContextMenuItem.fromId(item.itemId)
        MyLog.v(this) {
            "onContextItemSelected: " + contextMenuItem +
                    "; timeline=" + getViewItem().timeline
        }
        return contextMenuItem.execute(this, getViewItem())
    }

    private fun getViewItem(): ManageTimelinesViewItem {
        return if (mViewItem.isEmpty) {
            ManageTimelinesViewItem(listActivity.myContext, Timeline.EMPTY,
                    MyAccount.EMPTY, false)
        } else mViewItem as ManageTimelinesViewItem
    }
}