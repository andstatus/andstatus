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

import android.content.Context
import org.andstatus.app.R
import org.andstatus.app.lang.SelectableEnum

enum class DisplayedInSelector(
        /** Code - identifier of the type  */
        private val code: String?,
        /** The id of the string resource with the localized name of this enum to use in UI  */
        private val titleResId: Int) : SelectableEnum {
    ALWAYS("always", R.string.always), IN_CONTEXT("in_context", R.string.in_context), NEVER("no", R.string.never);

    /** String to be used for persistence  */
    fun save(): String? {
        return code
    }

    override fun toString(): String {
        return "DisplayedInSelector:$code"
    }

    /** Localized title for UI  */
    override fun title(context: Context?): CharSequence? {
        return if (titleResId == 0 || context == null) {
            this.code
        } else {
            context.getText(titleResId)
        }
    }

    override fun isSelectable(): Boolean {
        return true
    }

    override fun getCode(): String? {
        return code
    }

    override fun getDialogTitleResId(): Int {
        return R.string.select_displayed_in_selector
    }

    companion object {
        /** Returns the enum or NEVER  */
        fun load(strCode: String?): DisplayedInSelector {
            for (value in values()) {
                if (value.code == strCode) {
                    return value
                }
            }
            return NEVER
        }
    }
}