/**
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.service

import org.andstatus.app.util.MyLog

/**
 * State of [MyService]
 */
enum class MyServiceState {
    RUNNING, STOPPING, STOPPED, UNKNOWN;

    fun save(): String {
        return this.toString()
    }

    companion object {
        /**
         * Like valueOf but doesn't throw exceptions: it returns UNKNOWN instead
         */
        fun load(str: String?): MyServiceState {
            if (str == null) return UNKNOWN

            return try {
                valueOf(str)
            } catch (e: IllegalArgumentException) {
                MyLog.v(MyServiceState::class.java, e)
                UNKNOWN
            }
        }
    }
}