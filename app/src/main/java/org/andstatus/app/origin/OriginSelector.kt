/*
 * Copyright (c) 2018 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.origin

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
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.net.social.Actor
import org.andstatus.app.view.MyContextMenu
import org.andstatus.app.view.MySimpleAdapter
import org.andstatus.app.view.SelectorDialog
import java.util.*

/**
 * @author yvolk@yurivolkov.com
 */
class OriginSelector : SelectorDialog() {
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        setTitle(R.string.select_social_network)
        val listData = newListData()
        if (listData.isEmpty()) {
            returnSelectedItem( Origin.EMPTY)
            return
        } else if (listData.size == 1) {
            returnSelectedItem(listData.get(0))
            return
        }
        setListAdapter(newListAdapter(listData))
        listView.onItemClickListener = OnItemClickListener { parent: AdapterView<*>?, view: View?, position: Int, id: Long ->
            val selectedId = (view.findViewById<View?>(R.id.id) as TextView).text.toString().toLong()
            returnSelectedItem( MyContextHolder.myContextHolder.getNow().origins().fromId(selectedId))
        }
    }

    private fun newListData(): MutableList<Origin> {
        return getOriginsForActor()
    }

    private fun getOriginsForActor(): MutableList<Origin> {
        val actorId = Optional.ofNullable(arguments)
                .map { bundle: Bundle? -> bundle.getLong(IntentExtra.ACTOR_ID.key) }.orElse(0L)
        return Actor.Companion.load( MyContextHolder.myContextHolder.getNow(), actorId).user.knownInOrigins( MyContextHolder.myContextHolder.getNow())
    }

    private fun newListAdapter(listData: MutableList<Origin>): MySimpleAdapter {
        val list: MutableList<MutableMap<String?, String?>?> = ArrayList()
        for (item in listData) {
            val map: MutableMap<String?, String?> = HashMap()
            var visibleName: String = item.name
            if (!item.isValid()) {
                visibleName = "($visibleName)"
            }
            map[KEY_VISIBLE_NAME] = visibleName
            map[BaseColumns._ID] = java.lang.Long.toString(item.id)
            list.add(map)
        }
        return MySimpleAdapter(activity,
                list,
                R.layout.accountlist_item, arrayOf(KEY_VISIBLE_NAME, KEY_SYNC_AUTO, BaseColumns._ID), intArrayOf(R.id.visible_name, R.id.sync_auto, R.id.id), true)
    }

    private fun returnSelectedItem(item: Origin) {
        returnSelected(Intent()
                .putExtra(IntentExtra.ORIGIN_NAME.key, item.name)
                .putExtra(IntentExtra.MENU_GROUP.key,
                        myGetArguments().getInt(IntentExtra.MENU_GROUP.key, MyContextMenu.Companion.MENU_GROUP_NOTE))
        )
    }

    companion object {
        private val KEY_VISIBLE_NAME: String = "visible_name"
        private val KEY_SYNC_AUTO: String = "sync_auto"
        fun selectOriginForActor(activity: FragmentActivity, menuGroup: Int,
                                 requestCode: ActivityRequestCode, actor: Actor) {
            val selector: SelectorDialog = OriginSelector()
            selector.setRequestCode(requestCode).putLong(IntentExtra.ACTOR_ID.key, actor.actorId)
            selector.myGetArguments().putInt(IntentExtra.MENU_GROUP.key, menuGroup)
            selector.show(activity)
        }
    }
}