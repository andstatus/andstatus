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

package org.andstatus.app.context;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;

import org.andstatus.app.R;
import org.andstatus.app.util.MyLog;

/**
 * Theme and style-relates utility class
 * 2015-07-12: As I found out, we cannot cache a theme here for reuse
 * @author yvolk@yurivolkov.com
 */
public class MyTheme {

    private static volatile boolean mIsThemeLight = false;

    private MyTheme() {
        // Empty
    }

    public static void forget() {
        mIsThemeLight = false;
    }

    /**
     * Load a theme according to the preferences.
     */
    public static void loadTheme(Context context) {
        String themeName = "Theme.AndStatus." + MyPreferences.getString(MyPreferences.KEY_THEME_COLOR, "Dark");
        mIsThemeLight = themeName.contains("Light");
        int themeId = getStyleId(context, themeName, R.style.Theme_AndStatus_Dark);

        context.setTheme(themeId);
        Resources.Theme theme = context.getTheme();

        if (themeId != R.style.Theme_AndStatus_DeviceDefault) {
            theme.applyStyle(
                    getStyleId(context, MyPreferences.getString(MyPreferences.KEY_ACTION_BAR_COLOR, ""), R.style.ActionBarTeal),
                    false);
            theme.applyStyle(
                    getStyleId(context, MyPreferences.getString(MyPreferences.KEY_BACKGROUND_COLOR, ""), R.style.BackgroundColorBlack),
                    false);
        }
        theme.applyStyle(
                getStyleId(context, MyPreferences.getString(MyPreferences.KEY_THEME_SIZE, ""), R.style.StandardSize),
                false);
    }

    public static boolean isThemeLight() {
        return mIsThemeLight;
    }

    private static int getStyleId(Context context, String styleName, int defaultId) {
        int styleId = 0;
        if (!TextUtils.isEmpty(styleName)) {
            styleId = context.getResources().getIdentifier(styleName, "style", "org.andstatus.app");
            if (styleId == 0 || MyLog.isVerboseEnabled()) {
                String text = "getStyleId; name:\"" + styleName + "\"; id:" + Integer.toHexString(styleId)
                        + "; default:" + Integer.toHexString(defaultId);
                if (styleId == 0) {
                    MyLog.e(context, text);
                } else {
                    MyLog.v(context, text);
                }
            }
        }
        if (styleId == 0) {
            styleId = defaultId;
        }
        return styleId;
    }


    public static void setContentView(Activity activity, int layoutId) {
        activity.setContentView(layoutId);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP && !isThemeLight()) {
            setBackgroundColor(activity);
        }
    }

    /** See http://stackoverflow.com/questions/7896615/android-how-to-get-value-of-an-attribute-in-code
    * See also {@link android.app.AlertDialog} #resolveDialogTheme for resource resolution
    */
    private static void setBackgroundColor(Activity activity) {
        TypedValue typedValue = new TypedValue();
        activity.getTheme().resolveAttribute(R.attr.myBackgroundColor, typedValue, true);
        int color = typedValue.data;
        setBackgroundColor(activity.findViewById(R.id.myLayoutParent), color);
        setBackgroundColor(activity.findViewById(R.id.myListParent), color);
        setBackgroundColor(activity.findViewById(android.R.id.list), color);
    }

    private static void setBackgroundColor(View view, int color) {
        if (view != null) {
            view.setBackground(null);
            view.setBackgroundColor(color);
        }
    }

}
