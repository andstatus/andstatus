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
package org.andstatus.app.account

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
import org.andstatus.app.context.MyContextHolder.Companion.myContextHolder
import org.andstatus.app.net.social.Actor
import org.andstatus.app.origin.Origin
import org.andstatus.app.view.MyContextMenu
import org.andstatus.app.view.MySimpleAdapter
import org.andstatus.app.view.SelectorDialog
import java.util.*
import java.util.function.Predicate
import java.util.stream.Collectors

/**
 * @author yvolk@yurivolkov.com
 */
class AccountSelector : SelectorDialog() {
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        setTitle(R.string.label_accountselector)
        val listData = newListData()
        if (listData.isEmpty()) {
            returnSelectedAccount(MyAccount.EMPTY)
            return
        } else if (listData.size == 1) {
            returnSelectedAccount(listData[0])
            return
        }
        setListAdapter(newListAdapter(listData))
        listView?.onItemClickListener = OnItemClickListener { _: AdapterView<*>?, view: View, _: Int, _: Long ->
            val actorId = (view.findViewById<View?>(R.id.id) as TextView).text.toString().toLong()
            returnSelectedAccount(myContextHolder.getNow().accounts.fromActorId(actorId))
        }
    }

    private fun newListData(): MutableList<MyAccount> {
        val originId = Optional.ofNullable(arguments)
            .map { bundle: Bundle -> bundle.getLong(IntentExtra.ORIGIN_ID.key) }.orElse(0L)
        val origin: Origin = myContextHolder.getNow().origins.fromId(originId)
        val origins: MutableList<Origin> = if (origin.isValid) mutableListOf(origin) else getOriginsForActor()
        val predicate = if (origins.isEmpty()) Predicate { true } else
            Predicate { ma: MyAccount -> origins.contains(ma.origin) }
        return myContextHolder.getNow().accounts.get().stream()
            .filter(predicate)
            .collect(Collectors.toList())
    }

    private fun getOriginsForActor(): MutableList<Origin> {
        val actorId = Optional.ofNullable(arguments)
            .map { bundle: Bundle -> bundle.getLong(IntentExtra.ACTOR_ID.key) }.orElse(0L)
        return Actor.load(myContextHolder.getNow(), actorId)
            .user.knownInOrigins(myContextHolder.getNow())
    }

    private fun newListAdapter(listData: MutableList<MyAccount>): MySimpleAdapter {
        val list: MutableList<MutableMap<String, String>> = ArrayList()
        val syncText = getText(R.string.synced_abbreviated).toString()
        for (ma in listData) {
            val map: MutableMap<String, String> = HashMap()
            var visibleName = ma.accountName
            if (!ma.isValidAndSucceeded()) {
                visibleName = "($visibleName)"
            }
            map[KEY_VISIBLE_NAME] = visibleName
            map[KEY_SYNC_AUTO] = if (ma.isSyncedAutomatically && ma.isValidAndSucceeded()) syncText else ""
            map[BaseColumns._ID] = ma.actorId.toString()
            list.add(map)
        }
        return MySimpleAdapter(
            activity ?: throw IllegalStateException("No activity"),
            list,
            R.layout.accountlist_item, arrayOf(KEY_VISIBLE_NAME, KEY_SYNC_AUTO, BaseColumns._ID),
            intArrayOf(R.id.visible_name, R.id.sync_auto, R.id.id), true
        )
    }

    private fun returnSelectedAccount(ma: MyAccount) {
        returnSelected(
            Intent()
                .putExtra(IntentExtra.ACCOUNT_NAME.key, ma.accountName)
                .putExtra(
                    IntentExtra.MENU_GROUP.key,
                    myGetArguments().getInt(IntentExtra.MENU_GROUP.key, MyContextMenu.MENU_GROUP_NOTE)
                )
        )
    }

    companion object {
        private val KEY_VISIBLE_NAME: String = "visible_name"
        private val KEY_SYNC_AUTO: String = "sync_auto"
        fun selectAccountOfOrigin(activity: FragmentActivity, requestCode: ActivityRequestCode, originId: Long) {
            val selector: SelectorDialog = AccountSelector()
            selector.setRequestCode(requestCode).putLong(IntentExtra.ORIGIN_ID.key, originId)
            selector.show(activity)
        }

        fun selectAccountForActor(
            activity: FragmentActivity, menuGroup: Int,
            requestCode: ActivityRequestCode, actor: Actor
        ) {
            val selector: SelectorDialog = AccountSelector()
            selector.setRequestCode(requestCode).putLong(IntentExtra.ACTOR_ID.key, actor.actorId)
            selector.myGetArguments().putInt(IntentExtra.MENU_GROUP.key, menuGroup)
            selector.show(activity)
        }
    }
}
