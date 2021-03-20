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
package org.andstatus.app.list

import android.view.View
import android.widget.ListAdapter
import android.widget.ListView
import androidx.fragment.app.ListFragment
import org.andstatus.app.R

/**
 * Support library doesn't have ListActivity, so we recreated it using one of two options:
 * 1. Fragments
 * 2. ListView
 * And now it looks like we don't need ListFragment at all!
 * @author yvolk@yurivolkov.com
 */
open class MyListActivity : MyBaseListActivity() {
    private var listFragment: ListFragment? = null

    override var listView: ListView? = null
        get() {
            if (field == null) {
                findFragment()
                field = listFragment?.listView ?: findViewById<View?>(android.R.id.list) as ListView?
            }
            return field
        }

    override fun setListAdapter(adapter: ListAdapter) {
        findFragment()
        listFragment?.setListAdapter(adapter) ?: listView?.setAdapter(adapter)
    }

    override fun getListAdapter(): ListAdapter {
        findFragment()
        return listFragment?.listAdapter ?: listView?.adapter ?: super.getListAdapter()
    }

    private fun findFragment() {
        if (listFragment == null) {
            listFragment = supportFragmentManager.findFragmentById(R.id.relative_list_parent) as ListFragment?
        }
    }
}