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

import io.vavr.control.Try
import org.json.JSONException
import org.json.JSONObject
import java.util.*

/**
 * @author yvolk@yurivolkov.com
 */
object JsonUtils {
    val EMPTY: JSONObject? = JSONObject()
    fun optStringInside(jso: JSONObject?, parentTag: String?, childTag: String?): String {
        if (jso.has(parentTag)) {
            val innerJso = jso.optJSONObject(parentTag)
            if (innerJso != null) {
                return optString(innerJso, childTag)
            }
        }
        return ""
    }

    /** Creates new shallow copy without the keyToRemove  */
    fun remove(jso: JSONObject?, keyToRemove: String?): JSONObject {
        if (isEmpty(jso)) return JSONObject()
        try {
            val iterator = jso.keys()
            val out = JSONObject()
            while (iterator.hasNext()) {
                val key = iterator.next()
                if (key != keyToRemove) {
                    out.put(key, jso.get(key))
                }
            }
            return out
        } catch (e: JSONException) {
            MyLog.w(JsonUtils::class.java, "remove '$keyToRemove'", e)
        }
        return jso
    }

    /** Creates new shallow copy with new key, does nothing if key is empty or null  */
    fun put(jso: JSONObject?, key: String?, value: Any?): JSONObject {
        if (StringUtil.isEmpty(key)) {
            return jso ?: JSONObject()
        }
        val out = shallowCopy(jso)
        try {
            out.put(key, value)
        } catch (e: JSONException) {
            MyLog.w(JsonUtils::class.java, "put '$key'", e)
        }
        return out
    }

    /** Creates new shallow copy  */
    fun shallowCopy(jso: JSONObject?): JSONObject {
        if (isEmpty(jso)) return JSONObject()
        try {
            val iterator = jso.keys()
            val out = JSONObject()
            while (iterator.hasNext()) {
                val key = iterator.next()
                out.put(key, jso.get(key))
            }
            return out
        } catch (e: JSONException) {
            MyLog.w(JsonUtils::class.java, "shallowCopy", e)
        }
        return jso
    }

    fun isEmpty(jso: JSONObject?): Boolean {
        return jso == null || jso.length() == 0
    }

    fun toJsonObject(jsonString: String?): Try<JSONObject?>? {
        return if (jsonString == null) Try.failure(NoSuchElementException()) else try {
            Try.success(JSONObject(jsonString))
        } catch (e: Exception) {
            Try.failure(Exception("fromJsonString: $jsonString", e))
        }
    }

    fun toString(data: JSONObject?, indentSpaces: Int): String? {
        return try {
            data.toString(indentSpaces)
        } catch (e: JSONException) {
            MyLog.w(JsonUtils::class.java, "Failed toString of $data", e)
            ""
        }
    }

    /** Return the value mapped by the given key, or "" if not present.
     * See https://stackoverflow.com/a/23377941/297710  */
    fun optString(json: JSONObject, key: String): String {
        return optString(json, key, "")
    }

    /** Return the value mapped by the given key, or fallback if not present  */
    fun optString(json: JSONObject, key: String, fallback: String?): String? {
        // http://code.google.com/p/android/issues/detail?id=13830
        return if (json.isNull(key)) fallback else json.optString(key, fallback)
    }
}