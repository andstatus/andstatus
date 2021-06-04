/*
 * Copyright (C) 2019 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.util

/** Avoiding null-s  */
object NullUtil {
    fun nonEmpty(any: Any?): Boolean {
        if (any is IsEmpty) return any.nonEmpty
        return if (any is String) !(any as String?).isNullOrEmpty() else any != null
    }

    fun <K, V> getOrDefault(map: Map<K, V>?, key: K?, defaultValue: V): V {
        if (map == null || key == null) return defaultValue
        val v = map[key]
        return v ?: defaultValue
    }
}
