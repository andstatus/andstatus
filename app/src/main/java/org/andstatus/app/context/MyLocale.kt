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
package org.andstatus.app.context

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import org.acra.ACRA
import org.andstatus.app.util.I18n
import org.andstatus.app.util.SharedPreferencesUtil
import java.util.*

/**
 * @author yvolk@yurivolkov.com
 */
object MyLocale {
    private val CUSTOM_LOCALE_DEFAULT: String = "default"
    internal val MY_DEFAULT_LOCALE: Locale = Locale.US

    @Volatile
    private var mCustomLocale: Locale? = null

    @Volatile
    private var mDeviceDefaultLocale: Locale? = null

    fun isEnLocale(): Boolean {
        val locale = getAppLocale()
        return locale.language.isEmpty() || locale.language.startsWith("en")
    }

    private fun getAppLocale(): Locale {
        return mCustomLocale ?: getDeviceDefaultLocale()
    }

    private fun getDeviceDefaultLocale(): Locale {
        val locale1 = mDeviceDefaultLocale
        if (locale1 == null) {
            // See https://stackoverflow.com/a/59209993/297710
            return LocaleList.getDefault()[0].also {
                mDeviceDefaultLocale = it
            }
        }
        return locale1
    }

    fun setLocale(contextWrapper: ContextWrapper) {
        val strLocale = SharedPreferencesUtil.getString(MyPreferences.KEY_CUSTOM_LOCALE, CUSTOM_LOCALE_DEFAULT)
        if (strLocale != CUSTOM_LOCALE_DEFAULT || mCustomLocale != null) {
            mCustomLocale = if (strLocale == CUSTOM_LOCALE_DEFAULT) null else Locale(I18n.localeToLanguage(strLocale), I18n.localeToCountry(strLocale))
            val locale = getAppLocale()
            Locale.setDefault(locale)
            val config = contextWrapper.getBaseContext().resources.configuration
            config.setLocale(locale)
        }
        ACRA.getErrorReporter().putCustomData("locale",
                strLocale + ", " + (mCustomLocale?.getDisplayName() ?: "( custom not set)") +
                        ", default=" + getDeviceDefaultLocale().getDisplayName())
    }

    fun onConfigurationChanged(contextWrapper: ContextWrapper, newConfig: Configuration): Configuration {
        mDeviceDefaultLocale = newConfig.getLocales()[0]
        MyTheme.forget()
        return mCustomLocale?.let { toCustomized(newConfig, it) } ?: newConfig
    }

    // Based on https://stackoverflow.com/questions/39705739/android-n-change-language-programmatically/40849142
    fun onAttachBaseContext(context: Context): Context {
        if (mCustomLocale == null) return context
        Locale.setDefault(getAppLocale())
        val configuration = toCustomized(context.getResources().configuration, getAppLocale())
        return context.createConfigurationContext(configuration)
    }

    private fun toCustomized(configuration: Configuration?, newLocale: Locale?): Configuration {
        val custom = Configuration(configuration)
        custom.setLocale(newLocale)
        return custom
    }

    fun applyOverrideConfiguration(baseContext: Context, overrideConfiguration: Configuration?): Configuration? {
        if (overrideConfiguration != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            val uiMode = overrideConfiguration.uiMode
            overrideConfiguration.setTo(baseContext.getResources().configuration)
            overrideConfiguration.uiMode = uiMode
        }
        return overrideConfiguration
    }
}