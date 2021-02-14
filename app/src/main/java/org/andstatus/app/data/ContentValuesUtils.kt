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

package org.andstatus.app.data;

import android.content.ContentValues;

import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.StringUtil;

/**
 * @author yvolk@yurivolkov.com
 */
public class ContentValuesUtils {

    private ContentValuesUtils() {
        // Empty
    }

    /**
     * Move boolean value of the key from valuesIn to valuesOut and remove it from valuesIn
     * @param key
     * @param valuesIn
     * @param valuesOut  may be null
     * @return 1 for true, 0 for false and 2 for "not present"
     */
    public static int moveBooleanKey(String key, String sourceSuffix, ContentValues valuesIn, ContentValues valuesOut) {
        int ret = 2;
        if (valuesIn != null && valuesIn.containsKey(key + sourceSuffix)) {
            ret = SharedPreferencesUtil.isTrueAsInt(valuesIn.get(key + sourceSuffix));
            valuesIn.remove(key + sourceSuffix);
            if (valuesOut != null) {
                valuesOut.put(key, ret);
            }
        }
        return ret;
    }

    /**
     * Move String value of the key from valuesIn to valuesOut and remove it from valuesIn
     * @param key
     * @param valuesIn
     * @param valuesOut  may be null
     */
    public static void moveStringKey(String key, String sourceSuffix, ContentValues valuesIn, ContentValues valuesOut) {
        if (valuesIn != null && valuesIn.containsKey(key + sourceSuffix)) {
            String value = valuesIn.getAsString(key + sourceSuffix);
            valuesIn.remove(key + sourceSuffix);
            if (valuesOut != null) {
                valuesOut.put(key, value);
            }
        }
    }

    /**
     * Move String value of the key from valuesIn to valuesOut and remove it from valuesIn
     * @param key
     * @param valuesIn
     * @param valuesOut  may be null
     * @return Key value, 0 if not found
     */
    public static long moveLongKey(String key, String sourceSuffix, ContentValues valuesIn, ContentValues valuesOut) {
        long keyValue = 0;
        if (valuesIn != null && valuesIn.containsKey(key + sourceSuffix)) {
            Long value = valuesIn.getAsLong(key + sourceSuffix);
            keyValue = value == null ? 0 : value;
            valuesIn.remove(key + sourceSuffix);
            if (valuesOut != null) {
                valuesOut.put(key, value);
            }
        }
        return keyValue;
    }

    public static void putNotEmpty(ContentValues values, String key, String value) {
        if (!StringUtil.isEmpty(value)) {
            values.put(key, value);
        }
    }

    public static void putNotZero(ContentValues values, String key, Long value) {
        if (value != null && value != 0) {
            values.put(key, value);
        }
    }

}
