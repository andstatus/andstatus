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

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.NoSuchElementException;

import io.vavr.control.Try;

/**
 * @author yvolk@yurivolkov.com
 */
public class JsonUtils {
    public final static JSONObject EMPTY = new JSONObject();

    private JsonUtils() {
        // Empty
    }

    @NonNull
    public static String optStringInside(JSONObject jso, String parentTag, String childTag) {
        if (jso.has(parentTag)) {
            JSONObject innerJso = jso.optJSONObject(parentTag);
            if (innerJso != null) {
                return optString(innerJso, childTag);
            }
        }
        return "";
    }

    /** Creates new shallow copy without the keyToRemove */
    @NonNull
    public static JSONObject remove(JSONObject jso, String keyToRemove) {
        if (isEmpty(jso)) return new JSONObject();

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

    /** Creates new shallow copy with new key, does nothing if key is empty or null */
    @NonNull
    public static JSONObject put(JSONObject jso, String key, Object value) {
        if (StringUtil.isEmpty(key)) {
            return jso == null ? new JSONObject() : jso;
        }
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
        if (isEmpty(jso)) return new JSONObject();

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

    public static boolean isEmpty(JSONObject jso) {
        return jso == null || jso.length() == 0;
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
            MyLog.w(JsonUtils.class, "Failed toString of " + data, e);
            return "";
        }
    }

    /** Return the value mapped by the given key, or "" if not present.
     * See https://stackoverflow.com/a/23377941/297710 */
    @NonNull
    public static String optString(@NonNull JSONObject json, @NonNull String key) {
        return optString(json, key, "");
    }

    /** Return the value mapped by the given key, or fallback if not present */
    public static String optString(@NonNull JSONObject json, @NonNull String key, String fallback) {
        // http://code.google.com/p/android/issues/detail?id=13830
        if (json.isNull(key))
            return fallback;
        else
            return json.optString(key, fallback);
    }
}
