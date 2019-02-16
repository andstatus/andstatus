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

package org.andstatus.app.net.social.activitypub;

import org.andstatus.app.net.social.ActivityType;
import org.json.JSONObject;

import static org.andstatus.app.net.social.ActivityType.EMPTY;

/** @see <a href="https://www.w3.org/TR/activitystreams-vocabulary/#activity-types">Object Types</a>
 * */
enum ApObjectType {
    ACTIVITY("Activity", null) {
        @Override
        public boolean isTypeOf(JSONObject jso) {
            boolean is = false;
            if (jso != null) {
                if (jso.has("type")) {
                    is = super.isTypeOf(jso) ||
                            (ActivityType.from(jso.optString("type")) != EMPTY && jso.has("object"));
                } else {
                    is = jso.has("object");
                }
            }
            return is;
        }
    },
    APPLICATION("application", null),
    PERSON("Person", null),
    COMMENT("comment", null),
    IMAGE("image", COMMENT),
    VIDEO("video", COMMENT),
    NOTE("Note", COMMENT),
    COLLECTION("collection", null),
    RELATIONSHIP("Relationship", null),
    UNKNOWN("unknown", null);

    private String id;
    private ApObjectType compatibleType = this;

    ApObjectType(String fieldName, ApObjectType compatibleType) {
        this.id = fieldName;
        if (compatibleType != null) {
            this.compatibleType = compatibleType;
        }
    }
    
    public String id() {
        return id;
    }
    
    public boolean isTypeOf(JSONObject jso) {
        boolean is = false;
        if (jso != null) {
            is = id().equalsIgnoreCase(jso.optString("type"));
        }
        return is;
    }

    public static ApObjectType compatibleWith(JSONObject jso) {
        ApObjectType type = fromJson(jso);
        return type.compatibleType == null ? UNKNOWN : type.compatibleType;
    }

    public static ApObjectType fromJson(JSONObject jso) {
        for(ApObjectType type : ApObjectType.values()) {
            if (type.isTypeOf(jso)) {
                return type;
            }
        }
        return UNKNOWN;
    }

    public static ApObjectType fromId(ActivityType activityType, String oid) {
        switch (activityType) {
            case FOLLOW:
            case UNDO_FOLLOW:
                return ApObjectType.PERSON;
            case LIKE:
            case CREATE:
            case DELETE:
            case UPDATE:
            case ANNOUNCE:
            case UNDO_LIKE:
            case UNDO_ANNOUNCE:
                // TODO: Too simple...
                if (oid.contains("/users/")) {
                    return ApObjectType.PERSON;
                }

                return ApObjectType.NOTE;
            default:
                return ApObjectType.UNKNOWN;
        }
    }
}