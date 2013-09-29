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

package org.andstatus.app.net;

import org.json.JSONObject;

enum PumpioObjectType {
    ACTIVITY("activity", null) {
        @Override
        public boolean isMyType(JSONObject jso) {
            boolean is = false;
            if (jso != null) {
                 is = jso.has("verb");
                 // It may not have the "objectType" field as in the specification:
                 //   http://activitystrea.ms/specs/json/1.0/
            }
            return is;
        }
    },
    PERSON("person", null),
    COMMENT("comment", null),
    IMAGE("image", COMMENT),
    NOTE("note", COMMENT),
    UNKNOWN("unknown", null);
    
    private String id;
    private PumpioObjectType compatibleType = this;
    PumpioObjectType(String fieldName, PumpioObjectType compatibleType) {
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
    
    public static PumpioObjectType compatibleWith(JSONObject jso) {
        for(PumpioObjectType type : PumpioObjectType.values()) {
            if (type.isMyType(jso)) {
                return type.compatibleType;
            }
        }
        return UNKNOWN;
    }
}