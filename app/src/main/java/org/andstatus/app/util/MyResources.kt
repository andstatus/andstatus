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
package org.andstatus.app.util

import android.content.res.Resources.NotFoundException
import android.content.res.Resources.Theme
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt

/**
 * @author yvolk@yurivolkov.com
 */
object MyResources {
    @ColorInt
    @Throws(NotFoundException::class)
    fun getColorByAttribute(@AttrRes resId: Int, @AttrRes altResId: Int, theme: Theme): Int {
        val value = TypedValue()
        if (!theme.resolveAttribute(resId, value, true) && !theme.resolveAttribute(altResId, value, true)) {
            throw NotFoundException(
                    "Failed to resolve attribute IDs #0x" + Integer.toHexString(resId) + "and " + Integer.toHexString(altResId)
                            + " type #0x" + Integer.toHexString(value.type))
        }
        return value.data
    }
}