/*
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.data

import org.andstatus.app.graphics.CacheName
import org.andstatus.app.util.MyLog

enum class DownloadType(private val code: Long, val filePrefix: String, val cacheName: CacheName) {
    AVATAR(1, "av", CacheName.AVATAR),
    ATTACHMENT(2, "at", CacheName.ATTACHED_IMAGE),
    UNKNOWN(0, "un", CacheName.ATTACHED_IMAGE);

    fun save(): String {
        return java.lang.Long.toString(code)
    }

    companion object {
        private val TAG: String = DownloadType::class.simpleName!!
        fun load(strCode: String?): DownloadType {
            try {
                return load(strCode?.toLong() ?: 0)
            } catch (e: NumberFormatException) {
                MyLog.v(TAG, "Error converting '$strCode'", e)
            }
            return UNKNOWN
        }

        fun load(code: Long): DownloadType {
            for (value in values()) {
                if (value.code == code) {
                    return value
                }
            }
            return UNKNOWN
        }
    }
}
