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

package org.andstatus.app.context;

import android.content.ContextWrapper;
import android.content.res.Configuration;

import org.acra.ACRA;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.SharedPreferencesUtil;

import java.util.Locale;

/**
 * @author yvolk@yurivolkov.com
 */
public class MyLocale {
    private static final String TAG = MyLocale.class.getSimpleName();
    private static final String CUSTOM_LOCALE_DEFAULT = "default";

    private static volatile Locale mLocale = null;
    private static volatile Locale mDefaultLocale = null;

    private MyLocale() {
        // Non instantiable
    }

    public static boolean isEnLocale() {
        Locale locale = mLocale;
        if (locale == null) {
            locale = mDefaultLocale;
        }
        return  locale == null || locale.getLanguage().isEmpty() || locale.getLanguage().startsWith("en");
    }

    static void setLocale(ContextWrapper contextWrapper) {
        if (mDefaultLocale == null) {
            mDefaultLocale = contextWrapper.getBaseContext().getResources().getConfiguration().getLocales().get(0);
        }
        String strLocale = SharedPreferencesUtil.getString(MyPreferences.KEY_CUSTOM_LOCALE, CUSTOM_LOCALE_DEFAULT);
        if (!strLocale.equals(CUSTOM_LOCALE_DEFAULT) || mLocale != null) {
            Configuration config = contextWrapper.getBaseContext().getResources().getConfiguration();
            if (strLocale.equals(CUSTOM_LOCALE_DEFAULT)) {
                customizeConfig(contextWrapper, config, mDefaultLocale);
                mLocale = null;
            } else {
                mLocale = new Locale(I18n.localeToLanguage(strLocale), I18n.localeToCountry(strLocale));
                customizeConfig(contextWrapper, config, mLocale);
            }
        }
        ACRA.getErrorReporter().putCustomData("locale",
                strLocale + ", " +
                (mLocale == null ? "" :  mLocale.getDisplayName() + ", default=") +
                    (mDefaultLocale == null ? "(null)" : mDefaultLocale.getDisplayName()));
    }

    static Configuration onConfigurationChanged(ContextWrapper contextWrapper, Configuration newConfig) {
        if (mLocale == null || mDefaultLocale == null) {
            mDefaultLocale = newConfig.locale;
        }
        MyTheme.forget();
        return customizeConfig(contextWrapper, newConfig, mLocale);
    }

    private static Configuration customizeConfig(ContextWrapper contextWrapper, Configuration newConfig, Locale locale) {
        Configuration configCustom = newConfig;
        if (locale != null && !newConfig.locale.equals(locale)) {
            Locale.setDefault(locale);
            configCustom = new Configuration(newConfig);
            setLocale(configCustom, locale);
            contextWrapper.getBaseContext().getResources().updateConfiguration(configCustom, contextWrapper.getBaseContext().getResources().getDisplayMetrics());
        }
        return configCustom;
    }

    private static void setLocale(Configuration configCustom, Locale locale) {
        configCustom.setLocale(locale);
    }
}
