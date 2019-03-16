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
            mLocale = strLocale.equals(CUSTOM_LOCALE_DEFAULT)
                    ? null
                    : new Locale(I18n.localeToLanguage(strLocale), I18n.localeToCountry(strLocale));
            Locale locale = mLocale == null ? mDefaultLocale : mLocale;
            Locale.setDefault(locale);
            updateConfiguration(contextWrapper, locale);
        }
        ACRA.getErrorReporter().putCustomData("locale",
                strLocale + ", " +
                (mLocale == null ? "" :  mLocale.getDisplayName() + ", default=") +
                    (mDefaultLocale == null ? "(null)" : mDefaultLocale.getDisplayName()));
    }

    private static void updateConfiguration(ContextWrapper contextWrapper, Locale locale) {
        Configuration configIn = contextWrapper.getBaseContext().getResources().getConfiguration();
        if (!configIn.getLocales().get(0).equals(locale)) {
            Configuration configCustom = getCustomizeConfiguration(contextWrapper.getBaseContext(), locale);
            contextWrapper.getBaseContext().getResources().updateConfiguration(configCustom,
                    contextWrapper.getBaseContext().getResources().getDisplayMetrics());
        }
    }

    static Configuration onConfigurationChanged(ContextWrapper contextWrapper, Configuration newConfig) {
        if (mLocale == null || mDefaultLocale == null) {
            mDefaultLocale = newConfig.getLocales().get(0);
        }
        MyTheme.forget();
        return mLocale == null ? newConfig : getCustomizeConfiguration(contextWrapper.getBaseContext(), mLocale);
    }

    // Based on https://stackoverflow.com/questions/39705739/android-n-change-language-programmatically/40849142
    public static Context wrap(Context context) {
        return mLocale == null ? context : wrap(context, mLocale);
    }

    private static ContextWrapper wrap(Context context, Locale newLocale) {
        Configuration configuration = getCustomizeConfiguration(context, newLocale);
        return new ContextWrapper(context.createConfigurationContext(configuration));
    }

    private static Configuration getCustomizeConfiguration(Context context, Locale newLocale) {
        Configuration configuration = context.getResources().getConfiguration();
        configuration.setLocale(newLocale);

        LocaleList localeList = new LocaleList(newLocale);
        LocaleList.setDefault(localeList);
        configuration.setLocales(localeList);
        return configuration;
    }
}
