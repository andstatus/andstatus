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

import android.support.annotation.NonNull;
import android.view.View;
import android.view.ViewGroup;

/**
 * @author yvolk@yurivolkov.com
 */
public class ViewUtils {
    private ViewUtils() {
        // Non instantiable
    }

    public static int getHeightWithMargins(@NonNull View view) {
        int height = view.getMeasuredHeight();
        if (ViewGroup.MarginLayoutParams.class.isAssignableFrom(view.getLayoutParams().getClass())) {
            ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
            if (height == 0) {
                height += layoutParams.height;
            }
            height += layoutParams.topMargin + layoutParams.bottomMargin;
        }
        return height;
    }

    public static int getWidthWithMargins(@NonNull View view) {
        int width = view.getMeasuredWidth();
        if (ViewGroup.MarginLayoutParams.class.isAssignableFrom(view.getLayoutParams().getClass())) {
            ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
            if (width == 0) {
                width += layoutParams.width;
            }
            width += layoutParams.leftMargin + layoutParams.rightMargin;
        }
        return width;
    }
}
