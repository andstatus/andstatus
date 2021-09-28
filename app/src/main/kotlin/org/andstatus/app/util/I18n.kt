/*
 * Copyright (C) 2010-2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.content.Context
import java.text.MessageFormat
import java.util.regex.Pattern

/**
 * i18n - Internationalization utilities
 */
object I18n {

    /**
     * The function enables to have different localized message formats
     * (actually, any number of them)
     * for different quantities of something.
     *
     * E.g. in Russian we need at least three different messages notifying User
     * about the number of new tweets:
     * 1 твит  (same for 21, 31, ...)
     * 2 твита ( same for 3, 4, 22, ... )
     * 5 твитов (same for 5, ... 11, 12, 20 ...)
     * ...
     * see /res/values-ru/arrays.xml (R.array.appwidget_message_patterns)
     *
     * @author yvolk@yurivolkov.com
     */
    fun formatQuantityMessage(context: Context, messageFormatResourceId: Int,
                              quantityOfSomething: Long, arrayPatterns: Int, arrayFormats: Int): String {
        val toMatch = java.lang.Long.toString(quantityOfSomething)
        val p = context.getResources().getStringArray(arrayPatterns)
        val f = context.getResources().getStringArray(arrayFormats)
        var subformat = "{0} ???"
        for (i in p.indices) {
            val pattern = Pattern.compile(p[i])
            val m = pattern.matcher(toMatch)
            if (m.matches()) {
                subformat = f[i]
                break
            }
        }
        val msf = MessageFormat(subformat)
        val submessage = msf.format(arrayOf<Any?>(quantityOfSomething))
        return if (messageFormatResourceId == 0) {
            submessage
        } else {
            val mf = MessageFormat(context.getText(messageFormatResourceId).toString())
            mf.format(arrayOf<Any?>(submessage))
        }
    }

    fun trimTextAt(text: CharSequence?, maxLength: Int): CharSequence {
        if (text.isNullOrEmpty() || maxLength < 1) {
            return ""
        }
        if (text.length <= maxLength) {
            return text
        }
        if (text.length == maxLength + 1 && isSpace(text.get(maxLength))) {
            return text.subSequence(0, maxLength)
        }
        if (maxLength == 1 && !isSpace(text.get(0))) {
            return text.subSequence(0, 1)
        }
        var lastSpace = maxLength - 1
        while (lastSpace > 1) {
            if (isSpace(text.get(lastSpace))) {
                break
            }
            lastSpace--
        }
        val trimmed = text.subSequence(0, lastSpace)
        return "$trimmed…"
    }

    private fun isSpace(charAt: Char): Boolean {
        return " ,.;:()[]{}-_=+\"'".indexOf(charAt) >= 0
    }

    fun localeToLanguage(locale: String?): String {
        if (locale.isNullOrEmpty()) {
            return ""
        }
        val indHyphen = locale.indexOf('-')
        return if (indHyphen < 1) {
            locale
        } else locale.substring(0, indHyphen)
    }

    fun localeToCountry(locale: String?): String {
        if (locale.isNullOrEmpty()) {
            return ""
        }
        val indHyphen = locale.indexOf("-r")
        return if (indHyphen < 0) {
            ""
        } else locale.substring(indHyphen + 2)
    }

    fun formatBytes(bytes: Long): String {
        if (bytes == 0L) {
            return "0"
        }
        if (bytes < 10000) {
            return java.lang.Long.toString(bytes) + "B"
        }
        val kB = Math.round(1.0 * bytes / 1024)
        if (kB < 10000) {
            return java.lang.Long.toString(kB) + "KB"
        }
        val mB = Math.round(1.0 * kB / 1024)
        if (mB < 10000) {
            return java.lang.Long.toString(mB) + "MB"
        }
        val gB = Math.round(1.0 * mB / 1024)
        return java.lang.Long.toString(gB) + "GB"
    }

    fun notZero(value: Long): String {
        return if (value == 0L) {
            ""
        } else {
            java.lang.Long.toString(value)
        }
    }

    fun succeededText(succeeded: Boolean): String {
        return if (succeeded) " succeeded" else " failed"
    }
}
