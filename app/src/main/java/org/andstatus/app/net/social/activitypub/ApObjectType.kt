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
package org.andstatus.app.net.social.activitypub

import org.andstatus.app.net.social.ActivityType
import org.andstatus.app.util.JsonUtils
import org.json.JSONObject

/** @see [Activity Types](https://www.w3.org/TR/activitystreams-vocabulary/.activity-types)
 * [Actor Types](https://www.w3.org/TR/activitystreams-vocabulary/.actor-types)
 *
 */
internal enum class ApObjectType(private val id: String, compatibleType: ApObjectType?) {
    ACTIVITY("Activity", null) {
        override fun isTypeOf(jso: JSONObject?): Boolean {
            var `is` = false
            if (jso != null) {
                `is` = if (jso.has("type")) {
                    super.isTypeOf(jso) ||
                            ActivityType.from(JsonUtils.optString(jso, "type")) != ActivityType.EMPTY && jso.has("object")
                } else {
                    jso.has("object")
                }
            }
            return `is`
        }
    },
    APPLICATION("application", null), PERSON("Person", null), NOTE("Note", null), IMAGE("Image", NOTE), VIDEO("Video", NOTE), COLLECTION("Collection", null), ORDERED_COLLECTION("OrderedCollection", null), COLLECTION_PAGE("CollectionPage", null), ORDERED_COLLECTION_PAGE("OrderedCollectionPage", null), RELATIONSHIP("Relationship", null), UNKNOWN("unknown", null);

    private val compatibleType: ApObjectType =  compatibleType ?: this
    fun id(): String {
        return id
    }

    open fun isTypeOf(jso: JSONObject?): Boolean {
        var `is` = false
        if (jso != null) {
            `is` = id().equals(JsonUtils.optString(jso, "type"), ignoreCase = true)
        }
        return `is`
    }

    companion object {
        fun compatibleWith(jso: JSONObject): ApObjectType {
            val type = fromJson(jso)
            return type.compatibleType ?: type
        }

        fun fromJson(jso: JSONObject?): ApObjectType {
            for (type in values()) {
                if (type.isTypeOf(jso)) {
                    return type
                }
            }
            return UNKNOWN
        }

        fun fromId(activityType: ActivityType, oid: String): ApObjectType {
            return when (activityType) {
                ActivityType.FOLLOW, ActivityType.UNDO_FOLLOW -> PERSON
                ActivityType.LIKE, ActivityType.CREATE, ActivityType.DELETE, ActivityType.UPDATE, ActivityType.ANNOUNCE, ActivityType.UNDO_LIKE, ActivityType.UNDO_ANNOUNCE -> {
                    // TODO: Too simple...
                    if (oid.contains("/users/") && !oid.contains("/statuses/")) {
                        return PERSON
                    }
                    if (oid.contains("/activities/")) {
                        ACTIVITY
                    } else NOTE
                }
                else -> UNKNOWN
            }
        }
    }
}