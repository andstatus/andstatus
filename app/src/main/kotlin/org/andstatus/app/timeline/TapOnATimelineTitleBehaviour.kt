/**
 * Copyright (C) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.andstatus.app.timeline

import org.andstatus.app.util.MyLog

enum class TapOnATimelineTitleBehaviour(private val code: Long) {
    SWITCH_TO_DEFAULT_TIMELINE(1), GO_TO_THE_TOP(2), SELECT_TIMELINE(3), DISABLED(4);

    fun save(): String? {
        return java.lang.Long.toString(code)
    }

    companion object {
        val DEFAULT: TapOnATimelineTitleBehaviour? = SELECT_TIMELINE
        private val TAG: String? = TapOnATimelineTitleBehaviour::class.simpleName!!

        /**
         * Returns the enum or [.DEFAULT]
         */
        fun load(strCode: String?): TapOnATimelineTitleBehaviour? {
            try {
                if (!strCode.isNullOrEmpty()) {
                    return load(strCode.toLong())
                }
            } catch (e: NumberFormatException) {
                MyLog.v(TAG, "Error converting '$strCode'", e)
            }
            return DEFAULT
        }

        fun load(code: Long): TapOnATimelineTitleBehaviour? {
            for (`val` in values()) {
                if (`val`.code == code) {
                    return `val`
                }
            }
            return DEFAULT
        }
    }
}
