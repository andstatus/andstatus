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

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.andstatus.app.IntentExtra;

/**
 * @author yvolk@yurivolkov.com
 */
public class BundleUtils {

    private BundleUtils() {
    }

    public static long fromBundle(Bundle bundle, @NonNull IntentExtra intentExtra) {
        return fromBundle(bundle, intentExtra, 0);
    }

    public static long fromBundle(Bundle bundle, @NonNull IntentExtra intentExtra, long defaultValue) {
        if (bundle != null) {
            return bundle.getLong(intentExtra.key, defaultValue);
        }
        return defaultValue;
    }

    public static Bundle toBundle(String key, Long value) {
        return toBundle(null, key, value);
    }

    /**
     * Removes key if value is null
     */
    @NonNull
    public static Bundle toBundle(Bundle bundleIn, String key, Long value) {
        Bundle bundle = bundleIn == null ? new Bundle() : bundleIn;
        if (!TextUtils.isEmpty(key)) {
            if (value == null) {
                bundle.remove(key);
            } else {
                bundle.putLong(key, value);
            }
        }
        return bundle;
    }

    public static void putNotEmpty(Bundle bundle, IntentExtra intentExtra, String value) {
        if (!TextUtils.isEmpty(value)) {
            bundle.putString(intentExtra.key, value);
        }
    }

    public static void putNotZero(Bundle bundle, IntentExtra intentExtra, Long value) {
        if (value != null && value != 0) {
            bundle.putLong(intentExtra.key, value);
        }
    }

    @NonNull
    public static String getString(Bundle bundle, IntentExtra intentExtra) {
        String out = "";
        if (bundle != null && intentExtra != null) {
            String value = bundle.getString(intentExtra.key);
            if (!TextUtils.isEmpty(value)) {
                out = value;
            }
        }
        return out;
    }
}
