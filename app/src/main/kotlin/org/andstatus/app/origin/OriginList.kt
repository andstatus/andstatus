/* 
 * Copyright (c) 2013-2015 yvolk (Yuri Volkov), http://yurivolkov.com
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
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.BaseAdapter
import android.widget.ListAdapter
import android.widget.TextView
import org.andstatus.app.ActivityRequestCode
import org.andstatus.app.FirstActivity
import org.andstatus.app.IntentExtra
import org.andstatus.app.R
import org.andstatus.app.context.MyContextHolder.Companion.myContextHolder
import org.andstatus.app.context.MySettingsActivity
import org.andstatus.app.list.MyListActivity
import org.andstatus.app.util.MyLog
import org.andstatus.app.view.MySimpleAdapter
import kotlin.reflect.KClass

/**
 * Select or Manage Origins
 * @author yvolk@yurivolkov.com
 */
abstract class OriginList(clazz: KClass<*>) : MyListActivity(clazz) {
    private val data: MutableList<MutableMap<String, String>> = ArrayList()
    protected var addEnabled = false
    protected var originType: OriginType = OriginType.UNKNOWN

    override fun onCreate(savedInstanceState: Bundle?) {
        mLayoutId = getLayoutResourceId()
        super.onCreate(savedInstanceState)
        if (isFinishing) return

        processNewIntent(intent)
    }

    protected open fun getLayoutResourceId(): Int {
        return R.layout.my_list
    }

    /**
     * Change the Activity according to the new intent. This procedure is done
     * both [.onCreate] and [.onNewIntent]
     */
    private fun processNewIntent(intentNew: Intent) {
        val action = intentNew.getAction()
        if (Intent.ACTION_PICK == action || Intent.ACTION_INSERT == action) {
            listView?.onItemClickListener =
                OnItemClickListener { parent: AdapterView<*>?, view: View, position: Int, id: Long ->
                    onPickOrigin(
                        parent,
                        view,
                        position,
                        id
                    )
                }
        } else {
            listView?.onItemClickListener =
                OnItemClickListener { parent: AdapterView<*>?, view: View, position: Int, id: Long ->
                    onEditOrigin(
                        parent,
                        view,
                        position,
                        id
                    )
                }
        }
        addEnabled = Intent.ACTION_PICK != action
        originType = OriginType.fromCode(intentNew.getStringExtra(IntentExtra.ORIGIN_TYPE.key))
        if (Intent.ACTION_INSERT == action) {
            supportActionBar?.setTitle(R.string.select_social_network)
        }
        val adapter: ListAdapter = MySimpleAdapter(
            this,
            data,
            R.layout.origin_list_item,
            arrayOf(KEY_VISIBLE_NAME, KEY_NAME),
            intArrayOf(R.id.visible_name, R.id.name),
            true
        )
        // Bind to our new adapter.
        setListAdapter(adapter)
        fillList()
    }

    protected fun fillList() {
        data.clear()
        fillData(data)
        data.sortWith { lhs: MutableMap<String, String>, rhs: MutableMap<String, String> ->
            lhs.get(KEY_VISIBLE_NAME)
                ?.compareTo(rhs.get(KEY_VISIBLE_NAME) ?: "") ?: 0
        }
        MyLog.v(this) { "fillList, " + data.size + " items" }
        (getListAdapter() as BaseAdapter).notifyDataSetChanged()
    }

    protected fun fillData(data: MutableList<MutableMap<String, String>>) {
        for (origin in getOrigins()) {
            if (originType == OriginType.UNKNOWN || originType == origin.originType) {
                val map: MutableMap<String, String> = HashMap()
                val visibleName = origin.name
                map[KEY_VISIBLE_NAME] = visibleName
                map[KEY_NAME] = origin.name
                map[BaseColumns._ID] = java.lang.Long.toString(origin.id)
                data.add(map)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (myContextHolder.needToRestartActivity()) {
            FirstActivity.closeAllActivities(this)
            myContextHolder.initialize(this).thenStartActivity(intent)
        }
    }

    protected abstract fun getOrigins(): Iterable<Origin>
    fun onPickOrigin(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
        val name = (view.findViewById<View?>(R.id.name) as TextView).text.toString()
        val dataToReturn = Intent()
        dataToReturn.putExtra(IntentExtra.ORIGIN_NAME.key, name)
        this@OriginList.setResult(RESULT_OK, dataToReturn)
        finish()
    }

    fun onEditOrigin(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
        val name = (view.findViewById<View?>(R.id.name) as TextView).text.toString()
        val origin: Origin = myContextHolder.getNow().origins.fromName(name)
        if (origin.isPersistent()) {
            val intent = Intent(this@OriginList, OriginEditor::class.java)
            intent.action = Intent.ACTION_EDIT
            intent.putExtra(IntentExtra.ORIGIN_NAME.key, origin.name)
            startActivityForResult(intent, ActivityRequestCode.EDIT_ORIGIN.id)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        processNewIntent(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(getMenuResourceId(), menu)
        return super.onCreateOptionsMenu(menu)
    }

    protected abstract fun getMenuResourceId(): Int
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.getItemId()) {
            android.R.id.home -> {
                MySettingsActivity.goToMySettingsAccounts(this)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            MySettingsActivity.goToMySettingsAccounts(this)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    companion object {
        protected val KEY_VISIBLE_NAME: String = "visible_name"
        protected val KEY_NAME: String = "name"
    }
}
