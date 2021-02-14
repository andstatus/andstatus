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
package org.andstatus.app.origin

import android.content.Intent
import android.view.Menu
import android.view.MenuItem
import org.andstatus.app.ActivityRequestCode
import org.andstatus.app.IntentExtra
import org.andstatus.app.R
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.StringUtil

class PersistentOriginList : OriginList() {
    override fun getOrigins(): Iterable<Origin?>? {
        return MyContextHolder.Companion.myContextHolder.getNow().origins().collection()
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        var item = menu.findItem(R.id.addOriginButton)
        if (item != null) {
            item.isEnabled = addEnabled
            item.isVisible = addEnabled
        }
        // TODO: Currently no corresponding services work
        item = menu.findItem(R.id.discoverOpenInstances)
        if (item != null) {
            item.isEnabled = false
            item.isVisible = false
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item.getItemId()) {
            R.id.addOriginButton -> onAddOriginSelected("")
            R.id.discoverOpenInstances -> {
                val intent = Intent(this, DiscoveredOriginList::class.java)
                intent.action = Intent.ACTION_PICK
                startActivityForResult(intent, ActivityRequestCode.SELECT_OPEN_INSTANCE.id)
            }
            else -> {
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun onAddOriginSelected(originName: String?) {
        val intent = Intent(this, OriginEditor::class.java)
        intent.action = Intent.ACTION_INSERT
        intent.putExtra(IntentExtra.ORIGIN_NAME.key, originName)
        intent.putExtra(IntentExtra.ORIGIN_TYPE.key, originType.code)
        startActivityForResult(intent, ActivityRequestCode.EDIT_ORIGIN.id)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        MyLog.v(this) { "onActivityResult " + ActivityRequestCode.Companion.fromId(requestCode) }
        when (ActivityRequestCode.Companion.fromId(requestCode)) {
            ActivityRequestCode.EDIT_ORIGIN -> fillList()
            ActivityRequestCode.SELECT_OPEN_INSTANCE -> if (resultCode == RESULT_OK) {
                val originName = data.getStringExtra(IntentExtra.ORIGIN_NAME.key)
                if (!StringUtil.isEmpty(originName)) {
                    onAddOriginSelected(originName)
                }
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun getMenuResourceId(): Int {
        return R.menu.persistent_origin_list
    }
}