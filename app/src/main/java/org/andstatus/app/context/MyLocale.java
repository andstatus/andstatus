/*
 * Copyright (c) 2019 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Configuration;
import android.os.LocaleList;

import org.acra.ACRA;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.SharedPreferencesUtil;

import java.util.Locale;

/**
 * @author yvolk@yurivolkov.com
 */
public class MyLocale {
    private static final String CUSTOM_LOCALE_DEFAULT = "default";

    private static volatile Locale mCustomLocale = null;
    private static volatile Locale mDeviceDefaultLocale = null;

    private MyLocale() {
        // Non instantiable
    }

    public static boolean isEnLocale() {
        Locale locale = getAppLocale();
        return  locale == null || locale.getLanguage().isEmpty() || locale.getLanguage().startsWith("en");
    }

    public static Locale getAppLocale() {
        return mCustomLocale == null ? getDeviceDefaultLocale() : mCustomLocale;
    }

    public static Locale getDeviceDefaultLocale() {
        if (mDeviceDefaultLocale == null) {
            // See https://stackoverflow.com/a/59209993/297710
            mDeviceDefaultLocale = LocaleList.getDefault().get(0);
        }
        return mDeviceDefaultLocale;
    }

    static void setLocale(ContextWrapper contextWrapper) {
        String strLocale = SharedPreferencesUtil.getString(MyPreferences.KEY_CUSTOM_LOCALE, CUSTOM_LOCALE_DEFAULT);
        if (!strLocale.equals(CUSTOM_LOCALE_DEFAULT) || mCustomLocale != null) {
            mCustomLocale = strLocale.equals(CUSTOM_LOCALE_DEFAULT)
                    ? null
                    : new Locale(I18n.localeToLanguage(strLocale), I18n.localeToCountry(strLocale));
            Locale locale = getAppLocale();
            Locale.setDefault(locale);
            updateConfiguration(contextWrapper, locale);
        }
        ACRA.getErrorReporter().putCustomData("locale",
                strLocale + ", " +
                (mCustomLocale == null ? "" :  mCustomLocale.getDisplayName() + ", default=") +
                    (getDeviceDefaultLocale() == null ? "(null)" : getDeviceDefaultLocale().getDisplayName()));
    }

    private static void updateConfiguration(ContextWrapper contextWrapper, Locale locale) {
        Configuration config = contextWrapper.getBaseContext().getResources().getConfiguration();
        config.setLocale(locale);
    }

    static Configuration onConfigurationChanged(ContextWrapper contextWrapper, Configuration newConfig) {
        mDeviceDefaultLocale = newConfig.getLocales().get(0);
        MyTheme.forget();
        return mCustomLocale == null ? newConfig : toCustomized(newConfig, mCustomLocale);
    }

    // Based on https://stackoverflow.com/questions/39705739/android-n-change-language-programmatically/40849142
    public static Context wrap(Context context) {
        if (mCustomLocale == null) return context;

        Configuration configuration = toCustomized(context.getResources().getConfiguration(), mCustomLocale);
        Context configurationContext = context.createConfigurationContext(configuration);
        return new ContextWrapper(configurationContext);
    }

    private static Configuration toCustomized(Configuration configuration, Locale newLocale) {
        Configuration custom = new Configuration(configuration);
        custom.setLocale(newLocale);
        return custom;
    }
}
