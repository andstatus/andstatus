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

import android.app.Activity
import android.view.View
import android.view.ViewGroup.MarginLayoutParams

/**
 * @author yvolk@yurivolkov.com
 */
object ViewUtils {
    fun getHeightWithMargins(view: View): Int {
        var height = view.measuredHeight
        if (MarginLayoutParams::class.java.isAssignableFrom(view.layoutParams.javaClass)) {
            val layoutParams = view.layoutParams as MarginLayoutParams
            if (height == 0) {
                height += layoutParams.height
            }
            height += layoutParams.topMargin + layoutParams.bottomMargin
        }
        return height
    }

    fun getWidthWithMargins(view: View): Int {
        var width = view.measuredWidth
        if (MarginLayoutParams::class.java.isAssignableFrom(view.layoutParams.javaClass)) {
            val layoutParams = view.layoutParams as MarginLayoutParams
            if (width == 0) {
                width += layoutParams.width
            }
            width += layoutParams.leftMargin + layoutParams.rightMargin
        }
        return width
    }

    /**
     * @return true if succeeded
     */
    fun showView(activity: Activity?, viewId: Int, show: Boolean): Boolean {
        return activity != null && showView(activity.findViewById(viewId), show)
    }

    /**
     * @return true if succeeded
     */
    fun showView(parentView: View?, viewId: Int, show: Boolean): Boolean {
        return parentView != null && showView(parentView.findViewById(viewId), show)
    }

    /**
     * @return true if succeeded
     */
    fun showView(view: View?, show: Boolean): Boolean {
        val success = view != null
        if (success) {
            if (show) {
                if (view.getVisibility() != View.VISIBLE) {
                    view.setVisibility(View.VISIBLE)
                }
            } else {
                if (view.getVisibility() != View.GONE) {
                    view.setVisibility(View.GONE)
                }
            }
        }
        return success
    }

    fun isVisible(activity: Activity, viewId: Int): Boolean {
        val view = activity.findViewById<View?>(viewId) ?: return false
        return view.visibility == View.VISIBLE
    }
}