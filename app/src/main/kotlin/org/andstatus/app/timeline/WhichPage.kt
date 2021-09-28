/*
 * Copyright (c) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.timeline

import android.content.Context
import android.os.Bundle
import org.andstatus.app.IntentExtra
import org.andstatus.app.R
import org.andstatus.app.util.MyLog

/**
 * @author yvolk@yurivolkov.com
 */
enum class WhichPage(private val code: Long, private val titleResId: Int) {
    ANY(8, R.string.page_current),
    CURRENT(1, R.string.page_current),
    YOUNGER(2, R.string.page_younger),
    YOUNGEST(3, R.string.page_youngest),
    TOP(4, R.string.page_top_of),
    OLDER(6, R.string.page_older),
    EMPTY(7, R.string.page_empty);

    fun toBundle(): Bundle {
        return save(Bundle())
    }

    fun save(bundle: Bundle): Bundle {
        bundle.putString(IntentExtra.WHICH_PAGE.key, save())
        return bundle
    }

    fun save(): String {
        return code.toString()
    }

    fun getTitle(context: Context?): CharSequence {
        return if (titleResId == 0 || context == null) {
            name
        } else {
            context.getText(titleResId)
        }
    }

    fun isYoungest(): Boolean {
        return when (this) {
            TOP, YOUNGEST -> true
            else -> false
        }
    }

    companion object {
        private val TAG: String = WhichPage::class.simpleName!!
        fun load(strCode: String?, defaultPage: WhichPage): WhichPage {
            if (strCode != null) {
                try {
                    return load(strCode.toLong())
                } catch (e: NumberFormatException) {
                    MyLog.v(TAG, "Error converting '$strCode'", e)
                }
            }
            return defaultPage
        }

        fun load(code: Long): WhichPage {
            for (`val` in values()) {
                if (`val`.code == code) {
                    return `val`
                }
            }
            return EMPTY
        }

        fun load(args: Bundle?): WhichPage {
            return if (args != null) {
                load(args.getString(IntentExtra.WHICH_PAGE.key), EMPTY)
            } else EMPTY
        }
    }
}
