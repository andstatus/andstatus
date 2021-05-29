/*
 * Copyright (c) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.lang

import android.content.Context
import android.widget.ArrayAdapter
import java.util.*

/**
 * @author yvolk@yurivolkov.com
 */
class SelectableEnumList<E> private constructor(clazz: Class<E>) where E : Enum<E>, E : SelectableEnum {
    private val list: MutableList<E> = ArrayList()
    fun getList(): MutableList<E> {
        return list
    }

    /**
     * @return -1 if the item is not found in the list
     */
    fun getIndex(other: SelectableEnum?): Int {
        for (index in list.indices) {
            val selectableEnum: SelectableEnum = list[index]
            if (selectableEnum == other) {
                return index
            }
        }
        return -1
    }

    /**
     * @return the first element if not found
     */
    operator fun get(index: Int): E {
        return list[if (index >= 0 && index < list.size) index else 0]
    }

    fun getDialogTitleResId(): Int {
        return list[0].getDialogTitleResId()
    }

    fun getSpinnerArrayAdapter(context: Context): ArrayAdapter<CharSequence?> {
        val spinnerArrayAdapter = ArrayAdapter(
                context, android.R.layout.simple_spinner_item, getTitles(context))
        spinnerArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        return spinnerArrayAdapter
    }

    private fun getTitles(context: Context?): MutableList<CharSequence?> {
        val titles: MutableList<CharSequence?> = ArrayList()
        for (selectableEnum in list) {
            titles.add(selectableEnum.title(context))
        }
        return titles
    }

    companion object {
        fun <E> newInstance(clazz: Class<E>): SelectableEnumList<E> where E : Enum<E>, E : SelectableEnum {
            return SelectableEnumList(clazz)
        }
    }

    init {
        require(SelectableEnum::class.java.isAssignableFrom(clazz)) {
            "Class '" + clazz.name +
                    "' doesn't implement SelectableEnum"
        }
        for (value in EnumSet.allOf(clazz)) {
            if (value?.isSelectable() == true) {
                list.add(value)
            }
        }
    }
}
