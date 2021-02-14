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
package org.andstatus.app.timeline.meta

import android.content.Intent
import android.os.Bundle
import android.provider.BaseColumns
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import org.andstatus.app.ActivityRequestCode
import org.andstatus.app.IntentExtra
import org.andstatus.app.R
import org.andstatus.app.account.MyAccount
import org.andstatus.app.net.social.Actor
import org.andstatus.app.origin.Origin
import org.andstatus.app.util.TriState
import org.andstatus.app.view.MySimpleAdapter
import org.andstatus.app.view.SelectorDialog
import java.util.*
import java.util.stream.Collectors

/**
 * @author yvolk@yurivolkov.com
 */
class TimelineSelector : SelectorDialog() {
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        requireArguments()
        setTitle(R.string.dialog_title_select_timeline)
        val currentTimeline = myContext.timelines().fromId(arguments.getLong(IntentExtra.TIMELINE_ID.key, 0))
        val currentMyAccount = myContext.accounts().fromAccountName(
                arguments.getString(IntentExtra.ACCOUNT_NAME.key))
        val timelines = myContext.timelines().filter(
                true,
                TriState.Companion.fromBoolean(currentTimeline.isCombined),
                TimelineType.UNKNOWN, currentMyAccount.actor, Origin.Companion.EMPTY).collect(Collectors.toSet())
        if (!currentTimeline.isCombined && currentMyAccount.isValid) {
            timelines.addAll(myContext.timelines().filter(
                    true,
                    TriState.Companion.fromBoolean(currentTimeline.isCombined),
                    TimelineType.UNKNOWN, Actor.Companion.EMPTY, currentMyAccount.origin).collect(Collectors.toSet()))
        }
        if (timelines.isEmpty()) {
            returnSelectedTimeline(Timeline.Companion.EMPTY)
            return
        } else if (timelines.size == 1) {
            returnSelectedTimeline(timelines.iterator().next())
            return
        }
        val viewItems: MutableList<ManageTimelinesViewItem?> = ArrayList()
        for (timeline2 in timelines) {
            val viewItem = ManageTimelinesViewItem(myContext, timeline2,
                    myContext.accounts().currentAccount, true)
            viewItems.add(viewItem)
        }
        viewItems.sort(ManageTimelinesViewItemComparator(R.id.displayedInSelector, true, false))
        removeDuplicates(viewItems)
        listAdapter = newListAdapter(viewItems)
        listView.onItemClickListener = OnItemClickListener { parent: AdapterView<*>?, view: View?, position: Int, id: Long ->
            val timelineId = (view.findViewById<View?>(R.id.id) as TextView).text.toString().toLong()
            returnSelectedTimeline(myContext.timelines().fromId(timelineId))
        }
    }

    private fun removeDuplicates(timelines: MutableList<ManageTimelinesViewItem?>?) {
        val unique: MutableMap<String?, ManageTimelinesViewItem?> = HashMap()
        var removeSomething = false
        for (viewItem in timelines) {
            val key: String = viewItem.timelineTitle.toString()
            if (unique.containsKey(key)) {
                removeSomething = true
            } else {
                unique[key] = viewItem
            }
        }
        if (removeSomething) {
            timelines.retainAll(unique.values)
        }
    }

    private fun newListAdapter(listData: MutableList<ManageTimelinesViewItem?>?): MySimpleAdapter? {
        val list: MutableList<MutableMap<String?, String?>?> = ArrayList()
        val syncText = getText(R.string.synced_abbreviated).toString()
        for (viewItem in listData) {
            val map: MutableMap<String?, String?> = HashMap()
            map[KEY_VISIBLE_NAME] = viewItem.timelineTitle.toString()
            map[KEY_SYNC_AUTO] = if (viewItem.timeline.isSyncedAutomatically) syncText else ""
            map[BaseColumns._ID] = java.lang.Long.toString(viewItem.timeline.id)
            list.add(map)
        }
        return MySimpleAdapter(activity,
                list,
                R.layout.accountlist_item, arrayOf(KEY_VISIBLE_NAME, KEY_SYNC_AUTO, BaseColumns._ID), intArrayOf(R.id.visible_name, R.id.sync_auto, R.id.id), true)
    }

    private fun returnSelectedTimeline(timeline: Timeline?) {
        returnSelected(Intent().putExtra(IntentExtra.TIMELINE_ID.key, timeline.getId()))
    }

    companion object {
        private val KEY_VISIBLE_NAME: String? = "visible_name"
        private val KEY_SYNC_AUTO: String? = "sync_auto"
        fun selectTimeline(activity: FragmentActivity?, requestCode: ActivityRequestCode?,
                           timeline: Timeline?, currentMyAccount: MyAccount?) {
            val selector: SelectorDialog = TimelineSelector()
            selector.setRequestCode(requestCode)
            selector.arguments.putLong(IntentExtra.TIMELINE_ID.key, timeline.getId())
            selector.arguments.putString(IntentExtra.ACCOUNT_NAME.key, currentMyAccount.getAccountName())
            selector.show(activity)
        }
    }
}