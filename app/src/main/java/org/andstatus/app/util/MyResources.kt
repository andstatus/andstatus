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

import android.content.res.Resources;
import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import android.util.TypedValue;

/**
 * @author yvolk@yurivolkov.com
 */
public class MyResources {

    private MyResources() {
        // Empty
    }

    @ColorInt
    public static int getColorByAttribute(@AttrRes int resId, @AttrRes int altResId, @NonNull Resources.Theme theme)
            throws Resources.NotFoundException {
        TypedValue value = new TypedValue();
        if (!theme.resolveAttribute(resId, value, true) && !theme.resolveAttribute(altResId, value, true)) {
            throw new Resources.NotFoundException(
                    "Failed to resolve attribute IDs #0x" + Integer.toHexString(resId) + "and "  + Integer.toHexString(altResId)
                            +  " type #0x" + Integer.toHexString(value.type));
        }
        return value.data;
    }
}
