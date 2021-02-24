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
package org.andstatus.app.util

import android.os.Bundle
import org.andstatus.app.IntentExtra

/**
 * @author yvolk@yurivolkov.com
 */
object BundleUtils {
    @JvmOverloads
    fun fromBundle(bundle: Bundle?, intentExtra: IntentExtra, defaultValue: Long = 0): Long {
        return bundle?.getLong(intentExtra.key, defaultValue) ?: defaultValue
    }

    fun toBundle(key: String?, value: Long?): Bundle {
        return toBundle(null, key, value)
    }

    /**
     * Removes key if value is null
     */
    fun toBundle(bundleIn: Bundle?, key: String?, value: Long?): Bundle {
        val bundle = bundleIn ?: Bundle()
        if (!key.isNullOrEmpty()) {
            if (value == null) {
                bundle.remove(key)
            } else {
                bundle.putLong(key, value)
            }
        }
        return bundle
    }

    fun putNotEmpty(bundle: Bundle, intentExtra: IntentExtra, value: String?) {
        if (!value.isNullOrEmpty()) {
            bundle.putString(intentExtra.key, value)
        }
    }

    fun putNotZero(bundle: Bundle, intentExtra: IntentExtra, value: Long?) {
        if (value != null && value != 0L) {
            bundle.putLong(intentExtra.key, value)
        }
    }

    fun getString(bundle: Bundle?, intentExtra: IntentExtra?): String {
        var out = ""
        if (bundle != null && intentExtra != null) {
            val value = bundle.getString(intentExtra.key)
            if (!value.isNullOrEmpty()) {
                out = value
            }
        }
        return out
    }

    fun hasKey(bundle: Bundle?, key: String?): Boolean {
        return bundle != null && bundle[key] != null
    }
}