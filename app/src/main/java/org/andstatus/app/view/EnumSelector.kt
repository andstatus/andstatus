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
package org.andstatus.app.view

import android.content.Intent
import android.os.Bundle
import android.widget.AdapterView.OnItemClickListener
import org.andstatus.app.ActivityRequestCode
import org.andstatus.app.IntentExtra
import org.andstatus.app.R
import org.andstatus.app.lang.SelectableEnum
import org.andstatus.app.lang.SelectableEnumList
import java.util.*

/**
 * @author yvolk@yurivolkov.com
 */
class EnumSelector<E> : SelectorDialog() where E : Enum<E?>?, E : SelectableEnum? {
    private var enumList: SelectableEnumList<E?>? = null
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        if (enumList == null || enumList.getDialogTitleResId() == 0) {  // We don't save a state of the dialog
            dismiss()
            return
        }
        setTitle(enumList.getDialogTitleResId())
        listAdapter = newListAdapter()
        getListView().onItemClickListener = OnItemClickListener { parent, view, position, id -> returnSelected(Intent().putExtra(IntentExtra.SELECTABLE_ENUM.key, enumList.get(position).getCode())) }
    }

    private fun newListAdapter(): MySimpleAdapter? {
        val list: MutableList<MutableMap<String?, String?>?> = ArrayList()
        for (value in enumList.getList()) {
            val map: MutableMap<String?, String?> = HashMap()
            map[KEY_VISIBLE_NAME] = value.title(activity).toString()
            list.add(map)
        }
        return MySimpleAdapter(activity,
                list,
                R.layout.accountlist_item, arrayOf(KEY_VISIBLE_NAME), intArrayOf(R.id.visible_name), true)
    }

    companion object {
        private val KEY_VISIBLE_NAME: String? = "visible_name"
        fun <E> newInstance(
                requestCode: ActivityRequestCode?, clazz: Class<E?>?): SelectorDialog? where E : Enum<E?>?, E : SelectableEnum? {
            val selector: EnumSelector<*> = EnumSelector<Any?>()
            selector.setRequestCode(requestCode)
            selector.enumList = SelectableEnumList.Companion.newInstance(clazz)
            return selector
        }
    }
}