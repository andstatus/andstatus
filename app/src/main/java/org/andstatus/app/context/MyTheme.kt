/*
 * Copyright (C) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.context

import android.app.Activity
import android.content.Context
import android.util.TypedValue
import android.view.View
import org.andstatus.app.MyActivity
import org.andstatus.app.R
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.SharedPreferencesUtil
import org.andstatus.app.util.StringUtil

/**
 * Theme and style-relates utility class
 * 2015-07-12: As I found out, we cannot cache a theme here for reuse
 * @author yvolk@yurivolkov.com
 */
object MyTheme {
    @Volatile
    private var isLightTheme = true

    @Volatile
    private var isDeviceDefaultTheme = false
    fun forget() {
        isLightTheme = true
        isDeviceDefaultTheme = false
    }

    /**
     * Load a theme according to the preferences.
     */
    fun loadTheme(context: Context) {
        val themeName = getThemeName(context)
        isLightTheme = themeName.contains(".Light")
        isDeviceDefaultTheme = themeName.contains(".DeviceDefault")
        context.setTheme(getThemeId(context, themeName))
        applyStyles(context, false)
    }

    fun getThemeName(context: Context?): String {
        return if (context is MyActivity && (context as MyActivity?).isFinishing()) {
            "Theme.Transparent"
        } else "Theme.AndStatus." + SharedPreferencesUtil.getString(MyPreferences.KEY_THEME_COLOR, "DeviceDefault")
    }

    fun isThemeLight(): Boolean {
        return isLightTheme
    }

    fun getThemeId(context: Context?, themeName: String?): Int {
        return getStyleId(context, themeName, R.style.Theme_AndStatus_Light)
    }

    private fun getStyleId(context: Context?, styleName: String?, defaultId: Int): Int {
        var styleId = 0
        if (!StringUtil.isEmpty(styleName)) {
            styleId = context.getResources().getIdentifier(styleName, "style", "org.andstatus.app")
            if (styleId == 0 || MyLog.isVerboseEnabled()) {
                val text = ("getStyleId; name:\"" + styleName + "\"; id:" + Integer.toHexString(styleId)
                        + "; default:" + Integer.toHexString(defaultId))
                if (styleId == 0) {
                    MyLog.e(context, text)
                } else {
                    MyLog.v(context, text)
                }
            }
        }
        if (styleId == 0) {
            styleId = defaultId
        }
        return styleId
    }

    fun applyStyles(context: Context?, isDialog: Boolean) {
        val theme = context.getTheme()
        var actionBarTextStyleId = R.style.ActionBarTextWhite
        if (!isDeviceDefaultTheme) {
            theme.applyStyle(
                    getStyleId(context, SharedPreferencesUtil.getString(MyPreferences.KEY_ACTION_BAR_BACKGROUND_COLOR, ""), R.style.ActionBarMyBlue),
                    false)
            theme.applyStyle(
                    getStyleId(context, SharedPreferencesUtil.getString(MyPreferences.KEY_BACKGROUND_COLOR, ""), R.style.BackgroundColorBlack),
                    false)
            actionBarTextStyleId = getStyleId(context, SharedPreferencesUtil.getString(MyPreferences.KEY_ACTION_BAR_TEXT_COLOR, ""),
                    R.style.ActionBarTextWhite)
            theme.applyStyle(actionBarTextStyleId, false)
        }
        theme.applyStyle(if (actionBarTextStyleId == R.style.ActionBarTextWhite) R.style.ActionBarIconsWhite else R.style.ActionBarIconsBlack, false)
        theme.applyStyle(
                getStyleId(context, SharedPreferencesUtil.getString(MyPreferences.KEY_THEME_SIZE, ""), R.style.StandardSize),
                false)
        if (isDialog) {
            theme.applyStyle(R.style.AndStatusDialogStyle, true)
        }
    }

    fun setContentView(activity: Activity?, layoutId: Int) {
        activity.setContentView(layoutId)
    }

    /** See http://stackoverflow.com/questions/7896615/android-how-to-get-value-of-an-attribute-in-code
     * See also [android.app.AlertDialog] #resolveDialogTheme for resource resolution
     */
    private fun setBackgroundColor(activity: Activity?) {
        val typedValue = TypedValue()
        activity.getTheme().resolveAttribute(R.attr.myBackgroundColor, typedValue, true)
        val color = typedValue.data
        setBackgroundColor(activity.findViewById(R.id.my_layout_parent), color)
        setBackgroundColor(activity.findViewById(R.id.relative_list_parent), color)
        setBackgroundColor(activity.findViewById(android.R.id.list), color)
    }

    private fun setBackgroundColor(view: View?, color: Int) {
        if (view != null) {
            view.background = null
            view.setBackgroundColor(color)
        }
    }
}