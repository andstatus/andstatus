/**
 * Copyright (C) 2011-2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

import org.andstatus.app.data.MyPreferences;

import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

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
 * @author yvolk
 */
public class MyLog {
    private static final String TAG = MyLog.class.getSimpleName();

    /**
     * Use this tag to change logging level of the whole application
     * Is used in isLoggable(APPTAG, ... ) calls
     */
    public static final String APPTAG = "AndStatus";

    private static volatile boolean initialized = false;
    
    /** 
     * Cached value of the persistent preference
     */
    private static volatile int minLogLevel = Log.INFO;

    /**
     * Shortcut for debugging messages of the application
     */
    public static int d(String tag, String msg) {
        int i = 0;
        if (isLoggable(tag, Log.DEBUG)) {
            i = Log.d(tag, msg);
        }
        return i;
    }

    /**
     * Shortcut for debugging messages of the application
     */
    public static int d(String tag, String msg, Throwable tr) {
        int i = 0;
        if (isLoggable(tag, Log.DEBUG)) {
            i = Log.d(tag, msg, tr);
        }
        return i;
    }

    /**
     * Shortcut for verbose messages of the application
     */
    public static int v(String tag, String msg) {
        int i = 0;
        if (isLoggable(tag, Log.VERBOSE)) {
            i = Log.v(tag, msg);
        }
        return i;
    }

    /**
     * 
     * @param tag If tag is empty then {@link #APPTAG} is used
     * @param level {@link android.util.Log#INFO} ...
     * @return
     */
    public static boolean isLoggable(String tag, int level) {
        boolean is = false;
        checkInit();
        if (level >= minLogLevel) {
            is = true;
        } else {
            if (TextUtils.isEmpty(tag)) {
                tag = APPTAG;
            }
            if (tag.length() > 23) {
                tag = tag.substring(0, 22);
            }
            is = Log.isLoggable(tag, level);
        }
        
        return is;
    }
    
    /**
     * Initialize using a double-check idiom 
     */
    private static void checkInit() {
        if (initialized) return;
        synchronized (APPTAG) {
            if (initialized) return;
            // The class was not initialized yet.
            String val = "(not set)";
            try {
                SharedPreferences sp = MyPreferences.getDefaultSharedPreferences();  
                if (sp != null) {
                    try {
                        /**
                         * Due to the Android bug
                         * ListPreference operate with String values only...
                         * See http://code.google.com/p/android/issues/detail?id=2096
                         */
                        val = sp.getString(MyPreferences.KEY_MIN_LOG_LEVEL, String.valueOf(Log.ASSERT));  
                        minLogLevel = Integer.parseInt(val);  
                    } catch (java.lang.ClassCastException e) {
                        val = sp.getString(MyPreferences.KEY_MIN_LOG_LEVEL,"(empty)");  
                        Log.e(TAG, MyPreferences.KEY_MIN_LOG_LEVEL + "='" + val +"'");
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "Error in isLoggable");
            }
            if (Log.INFO >= minLogLevel) {
                Log.i(TAG, MyPreferences.KEY_MIN_LOG_LEVEL + "='" + val +"'");
            }
            initialized = true;
        }
    }

    /**
     * Mark to reread from the sources if it will be needed
     */
    public static void forget() {
        initialized = false;
    }
    
}
