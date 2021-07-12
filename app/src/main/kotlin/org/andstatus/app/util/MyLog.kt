/*
 * Copyright (C) 2011-2017 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.text.format.DateFormat
import android.util.Log
import io.vavr.control.Try
import org.andstatus.app.context.MyLocale.MY_DEFAULT_LOCALE
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.context.MyStorage
import org.andstatus.app.data.DbUtils
import org.andstatus.app.util.Taggable.Companion.anyToTruncatedTag
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.io.Writer
import java.nio.charset.StandardCharsets
import java.text.FieldPosition
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Supplier

/**
 * There is a need to turn debug (and maybe even verbose) logging on and off
 * dynamically at any time, plus sometimes we need to start debug logging on
 * boot. For possible solutions see e.g.:
 * http://stackoverflow.com/questions/2018263/android-logging
 * http://stackoverflow.com/questions/6650439/android-set-default-log-level-to-debug
 * http://stackoverflow.com/questions/4050417/android-production-logging-best-practice
 * I could not find existing way (the way that won't require programming) to change Android
 * application logging level:
 * - on boot
 * - at any time without connecting it to the PC.
 * So it looks like possible way to do this is to:
 * 1. Create new persistent Preference &quot;Minimum logging level&quot;
 * with list of values:
 * &quot;INFO&quot; (default, in order not to affect general users...),
 * &quot;DEBUG&quot; and &quot;VERBOSE&quot;.
 * 2. Create custom MyLog class that
 * honors the &quot;Minimum logging level&quot; preference. Use this class
 * throughout the application.
 *
 * @author yvolk@yurivolkov.com
 */
object MyLog {
    private val TAG: String = MyLog::class.java.simpleName

    /**
     * Use this tag to change logging level of the whole application
     * Is used in isLoggable(APPTAG, ... ) calls
     */
    val APPTAG: String = "AndStatus"
    const val DEBUG = Log.DEBUG
    const val ERROR = Log.ERROR
    const val VERBOSE = Log.VERBOSE
    const val WARN = Log.WARN
    const val INFO = Log.INFO
    private const val IGNORED = VERBOSE - 1

    @Volatile
    private var initialized = false

    /**
     * Cached value of the persistent preference
     */
    @Volatile
    private var minLogLevel = VERBOSE

    // TODO: Should be one object for atomic updates. start ---
    private val logToFileEnabled: AtomicBoolean = AtomicBoolean(false)
    private var logFileName: String? = null
    // end ---

    fun logSharedPreferencesValue(anyTag: Any?, key: String?) {
        val sp = SharedPreferencesUtil.getDefaultSharedPreferences()
        if (sp == null || !isLoggable(anyTag, DEBUG)) {
            return
        }
        var value: String? = "(not set)"
        if (sp.contains(key)) {
            value = try {
                sp.getString(key, "")
            } catch (e1: ClassCastException) {
                ignored(anyTag, e1)
                try {
                    java.lang.Boolean.toString(sp.getBoolean(key, false))
                } catch (e2: ClassCastException) {
                    ignored(anyTag, e2)
                    "??"
                }
            }
        }
        d(anyTag, "SharedPreference: $key='$value'")
    }

    fun e(anyTag: Any?, msg: String?, tr: Throwable?): Int {
        val tag = anyToTruncatedTag(anyTag)
        logToFile(ERROR, tag, msg, tr)
        return Log.e(tag, withOptionalPrefix(msg), tr)
    }

    fun e(anyTag: Any?, tr: Throwable?): Int {
        val tag = anyToTruncatedTag(anyTag)
        logToFile(ERROR, tag, null, tr)
        return Log.e(tag, withOptionalPrefix(""), tr)
    }

    fun e(anyTag: Any?, msg: String?): Int {
        val tag = anyToTruncatedTag(anyTag)
        logToFile(ERROR, tag, msg, null)
        return Log.e(tag, withOptionalPrefix(msg))
    }

    fun i(anyTag: Any?, msg: String?, tr: Throwable?): Int {
        val tag = anyToTruncatedTag(anyTag)
        logToFile(INFO, tag, msg, tr)
        return Log.i(tag, withOptionalPrefix(msg), tr)
    }

    fun i(anyTag: Any?, tr: Throwable?): Int {
        val tag = anyToTruncatedTag(anyTag)
        logToFile(INFO, tag, null, tr)
        return Log.i(tag, withOptionalPrefix(""), tr)
    }

    fun i(anyTag: Any?, msg: String?): Int {
        val tag = anyToTruncatedTag(anyTag)
        logToFile(INFO, tag, msg, null)
        return Log.i(tag, withOptionalPrefix(msg))
    }

    fun w(anyTag: Any?, msg: String?): Int {
        val tag = anyToTruncatedTag(anyTag)
        logToFile(WARN, tag, msg, null)
        return Log.w(tag, withOptionalPrefix(msg))
    }

    fun w(anyTag: Any?, msg: String?, tr: Throwable?): Int {
        val tag = anyToTruncatedTag(anyTag)
        logToFile(WARN, tag, msg, tr)
        return Log.w(tag, withOptionalPrefix(msg), tr)
    }

    /**
     * Shortcut for debugging messages of the application
     */
    fun d(anyTag: Any?, msg: String?): Int {
        val tag = anyToTruncatedTag(anyTag)
        var i = 0
        if (isLoggable(tag, DEBUG)) {
            logToFile(DEBUG, tag, msg, null)
            i = Log.d(tag, withOptionalPrefix(msg))
        }
        return i
    }

    /**
     * Shortcut for debugging messages of the application
     */
    fun d(anyTag: Any?, msg: String?, tr: Throwable?): Int {
        val tag = anyToTruncatedTag(anyTag)
        var i = 0
        if (isLoggable(tag, DEBUG)) {
            logToFile(DEBUG, tag, msg, tr)
            i = Log.d(tag, withOptionalPrefix(msg), tr)
        }
        return i
    }

    /**
     * Shortcut for verbose messages of the application
     */
    fun v(anyTag: Any?, tr: Throwable?): Int {
        val tag = anyToTruncatedTag(anyTag)
        var i = 0
        if (isLoggable(tag, Log.VERBOSE)) {
            logToFile(VERBOSE, tag, null, tr)
            i = Log.v(tag, withOptionalPrefix(""), tr)
        }
        return i
    }

    fun v(anyTag: Any?, msg: String?): Int {
        val tag = anyToTruncatedTag(anyTag)
        var i = 0
        if (isLoggable(tag, Log.VERBOSE)) {
            logToFile(VERBOSE, tag, msg, null)
            i = Log.v(tag, withOptionalPrefix(msg))
        }
        return i
    }

    fun v(anyTag: Any?, supplier: () -> String?): Int {
        val tag = anyToTruncatedTag(anyTag)
        if (!isLoggable(tag, Log.VERBOSE)) return 0
        val msg = supplier()
        logToFile(VERBOSE, tag, msg, null)
        return Log.v(tag, withOptionalPrefix(msg))
    }

    fun v(anyTag: Any?, msg: String?, tr: Throwable?): Int {
        val tag = anyToTruncatedTag(anyTag)
        var i = 0
        if (isLoggable(tag, Log.VERBOSE)) {
            logToFile(VERBOSE, tag, msg, tr)
            i = Log.v(tag, withOptionalPrefix(msg), tr)
        }
        return i
    }

    /**
     * This will be ignored
     */
    fun ignored(anyTag: Any?, tr: Throwable?): Int {
        val tag = anyToTruncatedTag(anyTag)
        var i = 0
        if (isLoggable(tag, IGNORED)) {
            i = Log.i(tag, withOptionalPrefix(""), tr)
        }
        return i
    }

    /** For now let's not add any prefix  */
    private fun withOptionalPrefix(msg: String?): String {
        return msg ?: ""
    }

    fun isDebugEnabled(): Boolean {
        return isLoggable(DEBUG)
    }

    fun isVerboseEnabled(): Boolean {
        return isLoggable(VERBOSE)
    }

    fun isLoggable(level: Int): Boolean {
        return isLoggable(APPTAG, level)
    }

    /**
     *
     * @param anyTag If tag is empty then [.APPTAG] is used
     * @param level [android.util.Log.INFO] ...
     * @return
     */
    fun isLoggable(anyTag: Any?, level: Int): Boolean {
        checkInit()
        return if (level < VERBOSE) {
            false
        } else if (level >= minLogLevel) {
            true
        } else {
            var tag: String? = anyToTruncatedTag(anyTag)
            if (tag.isNullOrEmpty()) {
                tag = APPTAG
            }
            Log.isLoggable(tag, level)
        }
    }

    /**
     * Initialize using a double-check idiom
     */
    private fun checkInit() {
        if (initialized) {
            return
        }
        getMinLogLevel().onSuccess { i: Int ->
            minLogLevel = i
            setLogToFile(MyPreferences.isLogEverythingToFile())
            initialized = true
        }
        if (initialized && Log.INFO >= minLogLevel) {
            Log.i(TAG, MyPreferences.KEY_MIN_LOG_LEVEL + "='" + minLogLevel + "'")
        }
    }

    fun getMinLogLevel(): Try<Int> {
        if (initialized) {
            return Try.success(minLogLevel)
        }
        val sp = SharedPreferencesUtil.getDefaultSharedPreferences()
                ?: return Try.failure(Exception("SharedPreferences are null"))
        val defaultLevel = Log.INFO
        return Try.of { sp.getString(MyPreferences.KEY_MIN_LOG_LEVEL, null) }
                .map { s -> s?.toInt() ?: defaultLevel }
                .recover(Exception::class.java) { sp.getInt(MyPreferences.KEY_MIN_LOG_LEVEL, defaultLevel) }
                .recover(Exception::class.java) { defaultLevel }
    }

    fun setMinLogLevel(minLogLevel: Int) {
        SharedPreferencesUtil.putString(MyPreferences.KEY_MIN_LOG_LEVEL, Integer.toString(minLogLevel))
        forget()
    }

    /**
     * Mark to reread from the sources if it will be needed
     */
    fun forget() {
        initialized = false
    }

    val currentStackTrace: String get() = getStackTrace(Exception())

    /**
     * from org.apache.commons.lang3.exception.ExceptionUtils
     */
    fun getStackTrace(throwable: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw, true)
        throwable.printStackTrace(pw) // NOSONAR
        return sw.buffer.toString()
    }

    fun writeStringToFile(string: String, filename: String?): Boolean {
        return writeStringToFile(string, filename, false, true)
    }

    private fun writeStringToFile(string: String, filename: String?, append: Boolean, logged: Boolean): Boolean {
        var ok = false
        if (filename.isNullOrEmpty()) {
            if (logged) {
                v("writeStringToFile", "Empty filename")
            }
            return false
        }
        val file = getFileInLogDir(filename, logged) ?: return false
        var fileOutputStream: FileOutputStream? = null
        var out: Writer? = null
        try {
            fileOutputStream = FileOutputStream(file.absolutePath, append)
            out = BufferedWriter(OutputStreamWriter(fileOutputStream, StandardCharsets.UTF_8))
            out.write(string)
            ok = true
        } catch (e: Exception) {
            if (logged) {
                d(TAG, filename, e)
            }
        } finally {
            DbUtils.closeSilently(out, filename)
            DbUtils.closeSilently(fileOutputStream, filename)
        }
        return ok
    }

    fun getFileInLogDir(filename: String?, logged: Boolean): File? {
        val dir1 = MyStorage.getLogsDir(logged)
        return if (dir1 == null || filename.isNullOrEmpty()) {
            null
        } else File(dir1, filename)
    }

    fun setNextLogFileName() {
        setNextLogFileName(true)
    }

    fun setLogToFile(logEnabled: Boolean) {
        logToFileEnabled.set(logEnabled)
        if (logToFileEnabled.get()) {
            setNextLogFileName(false)
        } else {
            synchronized(logToFileEnabled) { logFileName = null }
        }
    }

    private fun setNextLogFileName(changeExisting: Boolean) {
        val filename = currentDateTimeFormatted() + "_log.txt"
        synchronized(logToFileEnabled) {
            if (logFileName == null || changeExisting) {
                logFileName = filename
            }
        }
    }

    fun isLogToFileEnabled(): Boolean {
        return logToFileEnabled.get()
    }

    fun logToFile(logLevel: Int, tag: String?, msg: String?, tr: Throwable?) {
        if (!isLogToFileEnabled()) return
        val builder = StringBuilder()
        builder.append(currentDateTimeForLogLine())
        builder.append(" ")
        builder.append(logLevelToString(logLevel))
        builder.append("/")
        builder.append(tag)
        builder.append(":")
        if (!msg.isNullOrEmpty()) {
            builder.append(" ")
            builder.append(msg)
        }
        if (tr != null) {
            builder.append(" ")
            builder.append(tr.toString())
            builder.append("\n")
            builder.append(getStackTrace(tr))
        }
        builder.append("\n")
        writeRawStringToLogFile(builder)
    }

    private val logFileWriterLock: Any = Any()
    private fun writeRawStringToLogFile(builder: StringBuilder?) {
        synchronized(logFileWriterLock) {
            writeStringToFile(builder.toString(), getMostRecentLogFileName(), true, false)
        }
    }

    private fun getMostRecentLogFileName(): String {
        var filename = getLogFilename()
        getFileInLogDir(filename, false)?.let {
            if (!FileUtils.exists(it)) {
                setNextLogFileName(true)
                filename = getLogFilename()
            }
        }
        return filename
    }

    fun getLogFilename(): String {
        synchronized(logToFileEnabled) {
            return logFileName ?: ""
        }
    }

    fun logLevelToString(logLevel: Int): String {
        return when (logLevel) {
            DEBUG -> "D"
            ERROR -> "E"
            INFO -> "I"
            VERBOSE -> "V"
            else -> Integer.toString(logLevel)
        }
    }

    fun currentDateTimeForLogLine(): String {
        val logDateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", MY_DEFAULT_LOCALE)
        return logDateFormat.format(Date(System.currentTimeMillis()))
    }

    fun currentDateTimeFormatted(): String {
        return DateFormat.format("yyyy-MM-dd-HH-mm-ss", Date(System.currentTimeMillis())).toString()
    }

    fun trimmedString(input: String?, maxLength: Int): String {
        val out: String
        out = if (input != null) {
            val trimmed = input.trim { it <= ' ' }
            if (trimmed.length > maxLength) {
                trimmed.substring(0, maxLength - 1) + "…"
            } else {
                trimmed
            }
        } else {
            "(null)"
        }
        return out
    }

    fun logNetworkLevelMessage(anyTag: Any?, namePrefix: String?, jsonMessage: Any?, textData: String?) {
        if (jsonMessage != null && MyPreferences.isLogNetworkLevelMessages()) {
            val fileName = getSeparateLogFileName(namePrefix, anyTag)
            logJson(anyTag, namePrefix, jsonMessage, fileName)
            StringUtil.optNotEmpty(textData).ifPresent { txt: String -> writeStringToFile(txt, "$fileName.txt") }
        }
    }

    fun logJson(anyTag: Any?, namePrefix: String?, jso: Any, fileName: String?) {
        val logFileName = StringUtil.notEmpty(fileName, getSeparateLogFileName(namePrefix, anyTag))
        try {
            var isEmpty = false
            var jso2: Any? = jso
            if (jso is String) {
                if (jso.isEmpty()) {
                    return
                }
                jso2 = JSONTokener(jso).nextValue()
            }
            val strJso: String
            if (jso2 is JSONObject) {
                isEmpty = jso2.length() == 0
                strJso = jso2.toString(2)
            } else if (jso2 is JSONArray) {
                isEmpty = jso2.length() == 0
                strJso = jso2.toString(2)
            } else {
                strJso = jso.toString()
            }
            if (!isEmpty) {
                writeStringToFile(strJso, "$logFileName.json")
            } else {
                v(anyTag) { "$namePrefix; jso: $strJso" }
            }
        } catch (ignored1: JSONException) {
            ignored(anyTag, ignored1)
            try {
                writeStringToFile(jso.toString(), logFileName + "_invalid.json")
                v(anyTag) { "$namePrefix; invalid obj: $jso" }
            } catch (ignored2: Exception) {
                ignored(anyTag, ignored2)
            }
        }
    }

    private fun getSeparateLogFileName(namePrefix: String?, anyTag: Any?): String {
        return uniqueDateTimeFormatted() + "_" + namePrefix + "_" + Taggable.anyToTag(anyTag)
    }

    fun uniqueDateTimeFormatted(): String {
        return formatDateTime(uniqueCurrentTimeMS())
    }

    fun formatDateTime(time: Long): String {
        for (ind in 0..1) {
            // see http://stackoverflow.com/questions/16763968/android-text-format-dateformat-hh-is-not-recognized-like-with-java-text-simple
            val formatString = if (ind == 0) "yyyy-MM-dd-HH-mm-ss-SSS" else "yyyy-MM-dd-kk-mm-ss-SSS"
            val format = SimpleDateFormat(formatString, MY_DEFAULT_LOCALE)
            val buffer = StringBuffer()
            format.format(Date(time), buffer, FieldPosition(0))
            val strTime = buffer.toString()
            if (!strTime.contains("HH")) {
                return strTime
            }
        }
        return java.lang.Long.toString(time) // Fallback for a case above doesn't work
    }

    // see http://stackoverflow.com/a/9191383/297710
    private val LAST_TIME_MS: AtomicLong = AtomicLong()
    fun uniqueCurrentTimeMS(): Long {
        var now = System.currentTimeMillis()
        while (true) {
            val lastTime = LAST_TIME_MS.get()
            if (lastTime >= now) {
                now = lastTime + 1
            }
            if (LAST_TIME_MS.compareAndSet(lastTime, now)) {
                return now
            }
        }
    }

    fun debugFormatOfDate(date: Long): String {
        return if (date == RelativeTime.DATETIME_MILLIS_NEVER) "NEVER"
        else if (date == RelativeTime.SOME_TIME_AGO) "SOME_TIME_AGO"
        else formatDateTime(date)
    }

    fun databaseIsNull(message: Supplier<Any?>): String {
        if (!isVerboseEnabled()) return "Database is null"
        val msgLog = "Database is null. ${message.get()} \n$currentStackTrace"
        v(TAG, msgLog)
        return msgLog
    }
}
