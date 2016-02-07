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

/**
 * @author yvolk@yurivolkov.com
 */
public class BundleUtils {

    private BundleUtils() {
    }

    public static long fromBundle(Bundle bundle, String key) {
        return fromBundle(bundle, key, 0);
    }

    public static long fromBundle(Bundle bundle, String key, long defaultValue) {
        if (bundle != null && !TextUtils.isEmpty(key)) {
            return bundle.getLong(key, defaultValue);
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
}
