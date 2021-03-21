/*
 * Copyright (c) 2016-2019 yvolk (Yuri Volkov), http://yurivolkov.com
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
import java.util.*

/**
 * @author yvolk@yurivolkov.com
 */
object StringUtil {
    private val TEMP_OID_PREFIX: String = "andstatustemp:"

    fun stripTempPrefix(oid: String?): String? {
        return if (isTemp(oid)) oid?.substring(TEMP_OID_PREFIX.length) else oid
    }

    fun toTempOid(oid: String?): String {
        return toTempOidIf(true, oid) ?: throw IllegalArgumentException("oid was $oid")
    }

    fun toTempOidIf(transformToTempOid: Boolean, oid: String?): String? {
        return if (!transformToTempOid || isTemp(oid)) oid else TEMP_OID_PREFIX + oid
    }

    fun nonEmptyNonTemp(string: String?): Boolean {
        return !isEmptyOrTemp(string)
    }

    fun isEmptyOrTemp(string: String?): Boolean {
        return string.isNullOrEmpty() || string.startsWith(TEMP_OID_PREFIX)
    }

    fun isTemp(string: String?): Boolean {
        return !string.isNullOrEmpty() && string.startsWith(TEMP_OID_PREFIX)
    }

    /** empty and null strings are treated as the same  */
    fun equalsNotEmpty(first: String?, second: String?): Boolean {
        return notEmpty(first, "") == notEmpty(second, "")
    }

    fun notEmpty(value: String?, valueIfEmpty: String): String {
        return if (value.isNullOrEmpty()) valueIfEmpty else value
    }

    fun optNotEmpty(value: Any?): Optional<String> {
        return Optional.ofNullable(value)
            .map { obj: Any? -> obj?.toString() ?: "" }
            .map { obj: String -> obj.trim() }
            .filter { s: String -> s.isNotEmpty() }
    }

    fun notNull(value: String?): String {
        return value ?: ""
    }

    fun toLong(s: String?): Long {
        var value: Long = 0
        try {
            value = s?.toLong() ?: 0
        } catch (e: NumberFormatException) {
            MyLog.ignored(s, e)
        }
        return value
    }

    /**
     * From http://stackoverflow.com/questions/767759/occurrences-of-substring-in-a-string
     */
    fun countOfOccurrences(str: String, findStr: String): Int {
        var lastIndex = 0
        var count = 0
        while (lastIndex != -1) {
            lastIndex = str.indexOf(findStr, lastIndex)
            if (lastIndex != -1) {
                count++
                lastIndex += findStr.length
            }
        }
        return count
    }

    fun addBeforeArray(array: Array<String>?, s: String): Array<String> {
        val out = mutableListOf(s)
        array?.let { out.addAll(array)}
        return out.toTypedArray()
    }

    fun isFilled(value: String?): Boolean {
        return !value.isNullOrEmpty()
    }

    fun isNewFilledValue(oldValue: String?, newValue: String?): Boolean {
        return isFilled(newValue) && (oldValue == null || oldValue != newValue)
    }

    /** Doesn't throw exceptions  */
    fun format(context: Context?, resourceId: Int, vararg args: Any?): String {
        if (resourceId == 0) return ""
        return if (context == null) "Error no context resourceId=" + resourceId + argsToString(args) else try {
            format(context.getText(resourceId).toString(), *args)
        } catch (e2: Exception) {
            val msg = "Error formatting resourceId=" + resourceId + argsToString(args)
            MyLog.w(context, msg, e2)
            msg
        }
    }

    /** Doesn't throw exceptions  */
    fun format(format: String?, vararg args: Any?): String {
        if (args.isEmpty()) {
            return format?: ""
        }
        if (!format.isNullOrEmpty()) {
            try {
                return String.format(format, *args)
            } catch (e: Exception) {
                MyLog.w(StringUtil::class.java, "Error formatting \"" + format + "\"" + argsToString(args), e)
            }
        }
        return notEmpty(format, "(no format)") + argsToString(args)
    }

    private fun argsToString(args: Array<out Any?>): String {
        return " " + if (args.size == 1) args[0].toString() else args.contentToString()
    }
}