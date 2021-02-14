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

package org.andstatus.app.util;

import android.content.SharedPreferences;
import android.text.format.DateFormat;
import android.util.Log;

import androidx.annotation.NonNull;

import net.jcip.annotations.GuardedBy;

import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.context.MyStorage;
import org.andstatus.app.data.DbUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.FieldPosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import io.vavr.control.Try;

import static org.andstatus.app.util.RelativeTime.DATETIME_MILLIS_NEVER;
import static org.andstatus.app.util.RelativeTime.SOME_TIME_AGO;

/**
 * There is a need to turn debug (and maybe even verbose) logging on and off
 * dynamically at any time, plus sometimes we need to start debug logging on
 * boot. For possible solutions see e.g.:
 *  http://stackoverflow.com/questions/2018263/android-logging
 *  http://stackoverflow.com/questions/6650439/android-set-default-log-level-to-debug
 *  http://stackoverflow.com/questions/4050417/android-production-logging-best-practice 
 * I could not find existing way (the way that won't require programming) to change Android
 * application logging level: 
 *  - on boot 
 *  - at any time without connecting it to the PC. 
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
public class MyLog {
    private static final String TAG = MyLog.class.getSimpleName();
    public static final int MAX_TAG_LENGTH = 23;

    /**
     * Use this tag to change logging level of the whole application
     * Is used in isLoggable(APPTAG, ... ) calls
     */
    public static final String APPTAG = "AndStatus";
    public static final int DEBUG = Log.DEBUG;
    public static final int ERROR = Log.ERROR;
    public static final int VERBOSE = Log.VERBOSE;
    public static final int WARN = Log.WARN;
    public static final int INFO = Log.INFO;
    private static final int IGNORED = VERBOSE - 1;

    private static volatile boolean initialized = false;

    /** 
     * Cached value of the persistent preference
     */
    private static volatile int minLogLevel = VERBOSE;

    private final static AtomicBoolean logToFileEnabled = new AtomicBoolean(false);
    @GuardedBy("logToFileEnabled")
    private static String logFileName = null;

    private MyLog() {
        // Empty
    }

    public static void logSharedPreferencesValue(Object objTag, String key) {
        SharedPreferences sp = SharedPreferencesUtil.getDefaultSharedPreferences();
        if (sp == null || !isLoggable(objTag, DEBUG )) {
            return;
        }
        String value = "(not set)";
        if (sp.contains(key)) {
            try {
                value = sp.getString(key, "");
            } catch (ClassCastException e1) {
                MyLog.ignored(objTag, e1);
                try {
                    value = Boolean.toString(sp.getBoolean(key, false));
                } catch (ClassCastException e2) {
                    MyLog.ignored(objTag, e2);
                    value = "??";
                }
            }
        }
        d(objTag, "SharedPreference: " + key + "='" + value + "'");
    }
    
    public static int e(Object objTag, String msg, Throwable tr) {
        String tag = objToTruncatedTag(objTag);
        logToFile(ERROR, tag, msg, tr);
        return Log.e(tag, withOptionalPrefix(msg), tr);
    }

    public static int e(Object objTag, Throwable tr) {
        String tag = objToTruncatedTag(objTag);
        logToFile(ERROR, tag, null, tr);
        return Log.e(tag, withOptionalPrefix(""), tr);
    }
    
    public static int e(Object objTag, String msg) {
        String tag = objToTruncatedTag(objTag);
        logToFile(ERROR, tag, msg, null);
        return Log.e(tag, withOptionalPrefix(msg));
    }

    public static int i(Object objTag, String msg, Throwable tr) {
        String tag = objToTruncatedTag(objTag);
        logToFile(INFO, tag, msg, tr);
        return Log.i(tag, withOptionalPrefix(msg), tr);
    }
    
    public static int i(Object objTag, Throwable tr) {
        String tag = objToTruncatedTag(objTag);
        logToFile(INFO, tag, null, tr);
        return Log.i(tag, withOptionalPrefix(""), tr);
    }
    
    public static int i(Object objTag, String msg) {
        String tag = objToTruncatedTag(objTag);
        logToFile(INFO, tag, msg, null);
        return Log.i(tag, withOptionalPrefix(msg));
    }

    public static int w(Object objTag, String msg) {
        String tag = objToTruncatedTag(objTag);
        logToFile(WARN, tag, msg, null);
        return Log.w(tag, withOptionalPrefix(msg));
    }

    public static int w(Object objTag, String msg, Throwable tr) {
        String tag = objToTruncatedTag(objTag);
        logToFile(WARN, tag, msg, tr);
        return Log.w(tag, withOptionalPrefix(msg), tr);
    }

    /**
     * Shortcut for debugging messages of the application
     */
    public static int d(Object objTag, String msg) {
        String tag = objToTruncatedTag(objTag);
        int i = 0;
        if (isLoggable(tag, DEBUG)) {
            logToFile(DEBUG, tag, msg, null);
            i = Log.d(tag, withOptionalPrefix(msg));
        }
        return i;
    }

    /**
     * Shortcut for debugging messages of the application
     */
    public static int d(Object objTag, String msg, Throwable tr) {
        String tag = objToTruncatedTag(objTag);
        int i = 0;
        if (isLoggable(tag, DEBUG)) {
            logToFile(DEBUG, tag, msg, tr);
            i = Log.d(tag, withOptionalPrefix(msg), tr);
        }
        return i;
    }

    /**
     * Shortcut for verbose messages of the application
     */
    public static int v(Object objTag, Throwable tr) {
        String tag = objToTruncatedTag(objTag);
        int i = 0;
        if (isLoggable(tag, Log.VERBOSE)) {
            logToFile(VERBOSE, tag, null, tr);
            i = Log.v(tag, withOptionalPrefix(""), tr);
        }
        return i;
    }

    public static int v(Object objTag, String msg) {
        String tag = objToTruncatedTag(objTag);
        int i = 0;
        if (isLoggable(tag, Log.VERBOSE)) {
            logToFile(VERBOSE, tag, msg, null);
            i = Log.v(tag, withOptionalPrefix(msg));
        }
        return i;
    }

    public static int v(Object objTag, Supplier<String> supplier) {
        String tag = objToTruncatedTag(objTag);
        if (!isLoggable(tag, Log.VERBOSE)) return 0;

        String msg = supplier.get();
        logToFile(VERBOSE, tag, msg, null);
        return Log.v(tag, withOptionalPrefix(msg));
    }

    public static int v(Object objTag, String msg, Throwable tr) {
        String tag = objToTruncatedTag(objTag);
        int i = 0;
        if (isLoggable(tag, Log.VERBOSE)) {
            logToFile(VERBOSE, tag, msg, tr);
            i = Log.v(tag, withOptionalPrefix(msg), tr);
        }
        return i;
    }

    /**
     * This will be ignored
     */
    public static int ignored(Object objTag, Throwable tr) {
        String tag = objToTruncatedTag(objTag);
        int i = 0;
        if (isLoggable(tag, IGNORED)) {
            i = Log.i(tag, withOptionalPrefix(""), tr);
        }
        return i;
    }

    /** For now let's not add any prefix */
    private static String withOptionalPrefix(String msg) {
        return msg;
    }

    /** Truncated to {@link #MAX_TAG_LENGTH} */
    @NonNull
    static String objToTruncatedTag(Object objTag) {
        final String tag = MyStringBuilder.objToTag(objTag);
        return (tag.length() > MAX_TAG_LENGTH) ? tag.substring(0, MAX_TAG_LENGTH) : tag;
    }

    public static boolean isDebugEnabled() {
        return isLoggable(DEBUG);
    }

    public static boolean isVerboseEnabled() {
        return isLoggable(VERBOSE);
    }

    public static boolean isLoggable(int level) {
        return isLoggable(APPTAG, level);
    }
    
    /**
     * 
     * @param objTag If tag is empty then {@link #APPTAG} is used
     * @param level {@link android.util.Log#INFO} ...
     * @return
     */
    public static boolean isLoggable(Object objTag, int level) {
        checkInit();
        if (level < VERBOSE) {
            return false;
        } else if (level >= minLogLevel) {
            return true;
        } else {
            String tag = objToTruncatedTag(objTag);
            if (StringUtil.isEmpty(tag)) {
                tag = APPTAG;
            }
            return Log.isLoggable(tag, level);
        }
    }
    
    /**
     * Initialize using a double-check idiom 
     */
    private static void checkInit() {
        if (initialized) {
            return;
        }
        getMinLogLevel().onSuccess(i -> {
            minLogLevel = i;
            setLogToFile(MyPreferences.isLogEverythingToFile());
            initialized = true;
        });
        if (initialized && Log.INFO >= minLogLevel) {
            Log.i(TAG, MyPreferences.KEY_MIN_LOG_LEVEL + "='" + minLogLevel +"'");
        }
    }

    public static Try<Integer> getMinLogLevel() {
        if (initialized) {
            return Try.success(minLogLevel);
        }
        SharedPreferences sp = SharedPreferencesUtil.getDefaultSharedPreferences();
        if (sp == null) return Try.failure(new Exception("SharedPreferences are null"));

        final int defaultLevel = Log.INFO;
        return Try.of(() -> sp.getString(MyPreferences.KEY_MIN_LOG_LEVEL, String.valueOf(defaultLevel)))
                .map(Integer::parseInt)
                .recover(Exception.class, e -> sp.getInt(MyPreferences.KEY_MIN_LOG_LEVEL, defaultLevel))
                .recover(Exception.class, e -> defaultLevel);
    }

    public static void setMinLogLevel(int minLogLevel) {
        SharedPreferencesUtil.putString(MyPreferences.KEY_MIN_LOG_LEVEL, Integer.toString(minLogLevel));
        forget();
    }
    
    /**
     * Mark to reread from the sources if it will be needed
     */
    public static void forget() {
        initialized = false;
    }
    
    /**
     * from org.apache.commons.lang3.exception.ExceptionUtils
     */
    public static String getStackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw, true);
        throwable.printStackTrace(pw); // NOSONAR
        return sw.getBuffer().toString();
    }

    public static boolean writeStringToFile(String string, String filename) {
        return writeStringToFile(string, filename, false, true);
    }
    
    private static boolean writeStringToFile(String string, String filename, boolean append, boolean logged) {
        boolean ok = false;
        if (StringUtil.isEmpty(filename)) {
            if (logged) {
                MyLog.v("writeStringToFile", "Empty filename");
            }
            return false;
        }
        File file = getFileInLogDir(filename, logged);
        if (file == null) {
            return false;
        }
        FileOutputStream fileOutputStream = null;
        Writer out = null;
        try {
            fileOutputStream = new FileOutputStream(file.getAbsolutePath(), append);
            out = new BufferedWriter(new OutputStreamWriter(fileOutputStream, StandardCharsets.UTF_8));
            out.write(string);
            ok = true;
        } catch (Exception e) {
            if (logged) {
                MyLog.d(TAG, filename, e);
            }
        } finally {
            DbUtils.closeSilently(out, filename);
            DbUtils.closeSilently(fileOutputStream, filename);
        }
        return ok;
    }

    public static File getFileInLogDir(String filename, boolean logged) {
        File dir1 = MyStorage.getLogsDir(logged);
        if (dir1 == null || filename == null) { 
            return null; 
        }
        return new File(dir1, filename);
    }

    public static void setNextLogFileName() {
        setNextLogFileName(true);
    }
    
    public static void setLogToFile(boolean logEnabled) {
        logToFileEnabled.set(logEnabled);
        if (logToFileEnabled.get()) {
            setNextLogFileName(false);
        } else { 
            synchronized (logToFileEnabled) {
                logFileName = null;
            }
        }
    }

    private static void setNextLogFileName(boolean changeExisting) {
        String filename = currentDateTimeFormatted() + "_log.txt";
        synchronized (logToFileEnabled) {
            if (logFileName == null || changeExisting) {
                logFileName = filename; 
            }
        }
    }
    
    public static boolean isLogToFileEnabled() {
        return logToFileEnabled.get();
    }
    
    static void logToFile(int logLevel, String tag, String msg, Throwable tr) {
        if(!isLogToFileEnabled()) return;

        StringBuilder builder = new StringBuilder();
        builder.append(currentDateTimeForLogLine());
        builder.append(" ");
        builder.append(logLevelToString(logLevel));
        builder.append("/");
        builder.append(tag);
        builder.append(":");
        if (!StringUtil.isEmpty(msg)) {
            builder.append(" ");
            builder.append(msg);
        }
        if (tr != null) {
            builder.append(" ");
            builder.append(tr.toString());
            builder.append("\n");
            builder.append(getStackTrace(tr));
        }
        builder.append("\n");
        writeRawStringToLogFile(builder);
    }

    private static final Object logFileWriterLock = new Object();

    private static void writeRawStringToLogFile(StringBuilder builder) {
        synchronized (logFileWriterLock) {
            writeStringToFile(builder.toString(), getMostRecentLogFileName(), true, false);
        }
    }
    
    private static String getMostRecentLogFileName() {
        String filename = getLogFilename();
        if (!FileUtils.exists(getFileInLogDir(filename, false))) {
            setNextLogFileName(true);
            filename = getLogFilename();
        }
        return filename;
    }
    
    public static String getLogFilename() {
        synchronized (logToFileEnabled) {
            return logFileName;
        }
    }
    
    static String logLevelToString(int logLevel) {
        switch (logLevel) {
            case DEBUG:
                return "D";
            case ERROR:
                return "E";
            case INFO:
                return "I";
            case VERBOSE:
                return "V";
            default:
                return Integer.toString(logLevel);
        }
    }

    public static String currentDateTimeForLogLine() {
        final SimpleDateFormat logDateFormat = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);
        return logDateFormat.format(new Date(System.currentTimeMillis()));
    }

    public static String currentDateTimeFormatted() {
        return DateFormat.format("yyyy-MM-dd-HH-mm-ss", new Date(System.currentTimeMillis())).toString();
    }

    public static String trimmedString(String input, int maxLength) {
        String out;
        if (input != null) {
            String trimmed  = input.trim();
            if (trimmed.length() > maxLength) {
                out = trimmed.substring(0, maxLength-1) + "…";
            } else {
                out = trimmed; 
            }
        } else {
            out = "(null)";
        }
        return out;
    }

    public static void logNetworkLevelMessage(Object objTag, String namePrefix, Object jsonMessage, String textData) {
        if (jsonMessage != null && MyPreferences.isLogNetworkLevelMessages()) {
            String fileName = getSeparateLogFileName(namePrefix, objTag);
            logJson(objTag, namePrefix, jsonMessage, fileName);
            StringUtil.optNotEmpty(textData).ifPresent(txt -> writeStringToFile(txt, fileName + ".txt"));
        }
    }
   
    public static void logJson(Object objTag, String namePrefix, @NonNull Object jso, String fileName) {
        String logFileName = StringUtil.notEmpty(fileName, getSeparateLogFileName(namePrefix, objTag));
        try {
            boolean isEmpty = false;
            Object jso2 = jso;
            if (jso instanceof String) {
                if (StringUtil.isEmpty((String) jso)) {
                    return;
                }
                jso2 = (new JSONTokener((String) jso)).nextValue();
             }
            String strJso;
            if (jso2 instanceof JSONObject) {
                JSONObject jso3 = (JSONObject) jso2;
                isEmpty = jso3.length() == 0;
                strJso = jso3.toString(2);
            } else if (jso2 instanceof JSONArray) {
                JSONArray jsa = ((JSONArray) jso2);
                isEmpty = jsa.length() == 0;
                strJso = jsa.toString(2);
            } else {
                strJso = jso.toString();
            }
            if (!isEmpty) {
                writeStringToFile(strJso, logFileName + ".json");
            } else {
                v(objTag, () -> namePrefix + "; jso: " + strJso);
            }
        } catch (JSONException ignored1) {
            ignored(objTag, ignored1);
            try {
                writeStringToFile(jso.toString(), logFileName + "_invalid.json");
                v(objTag, () -> namePrefix + "; invalid obj: " + jso.toString());
            } catch (Exception ignored2) {
                ignored(objTag, ignored2);
            }
        }
    }

    private static String getSeparateLogFileName(String namePrefix, Object objTag) {
        return uniqueDateTimeFormatted() + "_" + namePrefix + "_" + MyStringBuilder.objToTag(objTag);
    }

    public static String uniqueDateTimeFormatted() {
        return formatDateTime(uniqueCurrentTimeMS());
    }

    public static String formatDateTime(long time) {
        for (int ind = 0; ind < 2; ind++) {
            // see http://stackoverflow.com/questions/16763968/android-text-format-dateformat-hh-is-not-recognized-like-with-java-text-simple
            String formatString = ind==0 ? "yyyy-MM-dd-HH-mm-ss-SSS" : "yyyy-MM-dd-kk-mm-ss-SSS";
            SimpleDateFormat format = new SimpleDateFormat(formatString);
            StringBuffer buffer = new StringBuffer();
            format.format(new Date(time), buffer, new FieldPosition(0));
            String strTime = buffer.toString();
            if (!strTime.contains("HH")) {
                return strTime;
            }
        }
        return Long.toString(time); // Fallback for a case above doesn't work
    }

    // see http://stackoverflow.com/a/9191383/297710
    private static final AtomicLong LAST_TIME_MS = new AtomicLong();

    public static long uniqueCurrentTimeMS() {
        long now = System.currentTimeMillis();
        while (true) {
            long lastTime = LAST_TIME_MS.get();
            if (lastTime >= now) {
                now = lastTime + 1;
            }
            if (LAST_TIME_MS.compareAndSet(lastTime, now)) {
                return now;
            }
        }
    }

    @NonNull
    public static String debugFormatOfDate(long date) {
        return date == DATETIME_MILLIS_NEVER
                ? "NEVER"
                : date == SOME_TIME_AGO
                    ? "SOME_TIME_AGO"
                    : formatDateTime(date);
    }

    public static String databaseIsNull(Supplier<Object> message) {
        if (!isVerboseEnabled()) return "Database is null";

        String msgLog = "Database is null. " + message.get() + "\n" + getStackTrace(new Exception());
        v(TAG, msgLog);
        return msgLog;
    }
}
