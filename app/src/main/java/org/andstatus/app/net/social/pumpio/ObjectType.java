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

package org.andstatus.app.net.social.pumpio;

import org.json.JSONObject;

/** @see <a href="https://www.w3.org/TR/activitystreams-vocabulary/#activity-types">Object Types</a>
 * */
enum ObjectType {
    ACTIVITY("activity", null) {
        @Override
        public boolean isMyType(JSONObject jso) {
            boolean is = false;
            if (jso != null) {
                if (jso.has("objecttype")) {
                    is = super.isMyType(jso);
                } else {
                    // It may not have the "objectType" field as in the specification:
                    //   http://activitystrea.ms/specs/json/1.0/
                    is = jso.has("verb") && jso.has("object");
                }
            }
            return is;
        }
    },
    APPLICATION("application", null),
    PERSON("person", null),
    COMMENT("comment", null),
    IMAGE("image", COMMENT),
    NOTE("note", COMMENT),
    COLLECTION("collection", null),
    UNKNOWN("unknown", null);
    
    private String id;
    private ObjectType compatibleType = this;

    ObjectType(String fieldName, ObjectType compatibleType) {
        this.id = fieldName;
        if (compatibleType != null) {
            this.compatibleType = compatibleType;
        }
    }
    
    public String id() {
        return id;
    }
    
    public boolean isMyType(JSONObject jso) {
        boolean is = false;
        if (jso != null) {
            is = id().equalsIgnoreCase(jso.optString("objectType"));
        }
        return is;
    }

    public static ObjectType compatibleWith(JSONObject jso) {
        ObjectType type = fromJson(jso);
        return type.compatibleType == null ? UNKNOWN : type.compatibleType;
    }

    public static ObjectType fromJson(JSONObject jso) {
        for(ObjectType type : ObjectType.values()) {
            if (type.isMyType(jso)) {
                return type;
            }
        }
        return UNKNOWN;
    }
}