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
package org.andstatus.app.data

import android.content.ContentValues
import org.andstatus.app.util.SharedPreferencesUtil

/**
 * @author yvolk@yurivolkov.com
 */
object ContentValuesUtils {
    /**
     * Move boolean value of the key from valuesIn to valuesOut and remove it from valuesIn
     * @param key
     * @param valuesIn
     * @param valuesOut  may be null
     * @return 1 for true, 0 for false and 2 for "not present"
     */
    fun moveBooleanKey(key: String?, sourceSuffix: String?, valuesIn: ContentValues?, valuesOut: ContentValues?): Int {
        var ret = 2
        if (valuesIn != null && valuesIn.containsKey(key + sourceSuffix)) {
            ret = SharedPreferencesUtil.isTrueAsInt(valuesIn[key + sourceSuffix])
            valuesIn.remove(key + sourceSuffix)
            valuesOut?.put(key, ret)
        }
        return ret
    }

    /**
     * Move String value of the key from valuesIn to valuesOut and remove it from valuesIn
     * @param key
     * @param valuesIn
     * @param valuesOut  may be null
     */
    fun moveStringKey(key: String?, sourceSuffix: String?, valuesIn: ContentValues?, valuesOut: ContentValues?) {
        if (valuesIn != null && valuesIn.containsKey(key + sourceSuffix)) {
            val value = valuesIn.getAsString(key + sourceSuffix)
            valuesIn.remove(key + sourceSuffix)
            valuesOut?.put(key, value)
        }
    }

    /**
     * Move String value of the key from valuesIn to valuesOut and remove it from valuesIn
     * @param key
     * @param valuesIn
     * @param valuesOut  may be null
     * @return Key value, 0 if not found
     */
    fun moveLongKey(key: String?, sourceSuffix: String?, valuesIn: ContentValues?, valuesOut: ContentValues?): Long {
        var keyValue: Long = 0
        if (valuesIn != null && valuesIn.containsKey(key + sourceSuffix)) {
            val value = valuesIn.getAsLong(key + sourceSuffix)
            keyValue = value ?: 0
            valuesIn.remove(key + sourceSuffix)
            valuesOut?.put(key, value)
        }
        return keyValue
    }

    fun putNotEmpty(values: ContentValues, key: String, value: String?) {
        if (!value.isNullOrEmpty()) {
            values.put(key, value)
        }
    }

    fun putNotZero(values: ContentValues, key: String, value: Long?) {
        if (value != null && value != 0L) {
            values.put(key, value)
        }
    }
}