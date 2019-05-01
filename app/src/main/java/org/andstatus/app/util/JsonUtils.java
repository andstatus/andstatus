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

package org.andstatus.app.util;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.NoSuchElementException;

import androidx.annotation.NonNull;
import io.vavr.control.Try;

/**
 * @author yvolk@yurivolkov.com
 */
public class JsonUtils {

    private JsonUtils() {
        // Empty
    }

    @NonNull
    public static String optStringInside(JSONObject jso, String parentTag, String childTag) {
        String str = "";
        if (jso.has(parentTag)) {
            JSONObject image = jso.optJSONObject(parentTag);
            if (image != null) {
                str = image.optString(childTag);
            }
        }
        return str;
    }

    /** Creates new shallow copy without the keyToRemove */
    @NonNull
    public static JSONObject remove(JSONObject jso, String keyToRemove) {
        if (jso == null || jso.length() == 0) return new JSONObject();

        try {
            Iterator<String> iterator = jso.keys();
            JSONObject out = new JSONObject();
            while (iterator.hasNext()) {
                String key = iterator.next();
                if (!key.equals(keyToRemove)) {
                    out.put(key, jso.get(key));
                }
            }
            return out;
        } catch (JSONException e) {
            MyLog.w(JsonUtils.class, "remove '" + keyToRemove + "'", e);
        }
        return jso;
    }

    /** Creates new shallow copy with new key */
    @NonNull
    public static JSONObject put(JSONObject jso, String key, Object value) {
        JSONObject out = shallowCopy(jso);
        try {
            out.put(key, value);
        } catch (JSONException e) {
            MyLog.w(JsonUtils.class, "put '" + key + "'", e);
        }
        return out;
    }

    /** Creates new shallow copy */
    @NonNull
    public static JSONObject shallowCopy(JSONObject jso) {
        if (jso == null || jso.length() == 0) return new JSONObject();

        try {
            Iterator<String> iterator = jso.keys();
            JSONObject out = new JSONObject();
            while (iterator.hasNext()) {
                String key = iterator.next();
                out.put(key, jso.get(key));
            }
            return out;
        } catch (JSONException e) {
            MyLog.w(JsonUtils.class, "shallowCopy", e);
        }
        return jso;
    }

    public static Try<JSONObject> toJsonObject(String jsonString) {
        if (jsonString == null) return Try.failure(new NoSuchElementException());

        try {
            return Try.success(new JSONObject(jsonString));
        } catch (Exception e) {
            return Try.failure(new Exception("fromJsonString: " + jsonString, e));
        }
    }

    public static String toString(JSONObject data, int indentSpaces) {
        try {
            return data.toString(indentSpaces);
        } catch (JSONException e) {
            MyLog.e(JsonUtils.class, "Failed toString of " + data, e);
            return "";
        }
    }
}
