/*
 * Copyright (C) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.net.social.pumpio

import org.andstatus.app.util.JsonUtils
import org.json.JSONObject

/** @see [Object Types](https://www.w3.org/TR/activitystreams-vocabulary/.activity-types)
 *
 */
internal enum class PObjectType(val id: String?, compatibleType: PObjectType?) {
    ACTIVITY("activity", null) {
        override fun isTypeOf(jso: JSONObject?): Boolean {
            var `is` = false
            if (jso != null) {
                `is` = if (jso.has("objecttype")) {
                    super.isTypeOf(jso)
                } else {
                    // It may not have the "objectType" field as in the specification:
                    //   http://activitystrea.ms/specs/json/1.0/
                    jso.has("verb") && jso.has("object")
                }
            }
            return `is`
        }
    },
    APPLICATION("application", null), PERSON("person", null), COMMENT("comment", null), IMAGE("image", COMMENT), VIDEO("video", COMMENT), NOTE("note", COMMENT), COLLECTION("collection", null), UNKNOWN("unknown", null);

    private val compatibleType: PObjectType? = this
    open fun isTypeOf(jso: JSONObject?): Boolean {
        var `is` = false
        if (jso != null) {
            `is` = id.equals(JsonUtils.optString(jso, "objectType"), ignoreCase = true)
        }
        return `is`
    }

    companion object {
        fun compatibleWith(jso: JSONObject?): PObjectType? {
            val type = fromJson(jso)
            return type.compatibleType ?: UNKNOWN
        }

        fun fromJson(jso: JSONObject?): PObjectType? {
            for (type in values()) {
                if (type.isTypeOf(jso)) {
                    return type
                }
            }
            return UNKNOWN
        }
    }

    init {
        if (compatibleType != null) {
            this.compatibleType = compatibleType
        }
    }
}