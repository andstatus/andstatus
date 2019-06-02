/*
 * Copyright (C) 2019 yvolk (Yuri Volkov), http://yurivolkov.com
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

import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Avoiding null-s */
public class NullUtil {

    public static boolean nonEmpty(@Nullable Object object) {
        if (object instanceof IsEmpty) return ((IsEmpty)object).nonEmpty();
        if (object instanceof String) return StringUtils.nonEmpty((String)object);

        return (object != null);
    }

    @NonNull
    public static <K, V> V getOrDefault(Map<K, V> map, K key, @NonNull V defaultValue) {
        if (map == null || key == null) return defaultValue;
        V v = map.get(key);
        return v == null ? defaultValue : v;
    }
}
