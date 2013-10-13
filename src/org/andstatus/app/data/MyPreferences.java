/**
 * Copyright (C) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.data;

import org.andstatus.app.MyContext;
import org.andstatus.app.MyContextHolder;
import org.andstatus.app.TimelineActivity;
import org.andstatus.app.util.MyLog;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;

import java.io.File;

/**
 * This is a central point of accessing SharedPreferences
 * Noninstantiable class 
 * @author yvolk@yurivolkov.com
 */
public class MyPreferences {
    private static final String TAG = MyPreferences.class.getSimpleName();
    
    /**
     * This is sort of button to start verification of credentials
     */
    public static final String KEY_VERIFY_CREDENTIALS = "verify_credentials";

    public static final String KEY_HISTORY_SIZE = "history_size";
    public static final String KEY_HISTORY_TIME = "history_time";
    /**
     * Period of automatic updates in seconds
     */
    public static final String KEY_FETCH_FREQUENCY = "fetch_frequency";
    public static final String KEY_RINGTONE_PREFERENCE = "notification_ringtone";
    public static final String KEY_CONTACT_DEVELOPER = "contact_developer";
    public static final String KEY_REPORT_BUG = "report_bug";
    public static final String KEY_CHANGE_LOG = "change_log";
    public static final String KEY_ABOUT_APPLICATION = "about_application";

    /**
     * System time when shared preferences were changed
     */
    public static final String KEY_PREFERENCES_CHANGE_TIME = "preferences_change_time";
    /**
     * Minimum logging level for the whole application (i.e. for any tag)
     */
    public static final String KEY_MIN_LOG_LEVEL = "min_log_level";
    /**
     * Use this dir: http://developer.android.com/reference/android/content/Context.html#getExternalFilesDir(java.lang.String)
     * (for API 8)
     */
    public static final String KEY_USE_EXTERNAL_STORAGE = "use_external_storage";
    /**
     * New value for #KEY_USE_EXTERNAL_STORAGE to e confirmed/processed
     */
    public static final String KEY_USE_EXTERNAL_STORAGE_NEW = "use_external_storage_new";
    /**
     * Is the timeline combined in {@link TimelineActivity} 
     */
    public static final String KEY_TIMELINE_IS_COMBINED = "timeline_is_combined";
    /**
     * Version code of last opened application (int) 
     */
    public static final String KEY_VERSION_CODE_LAST = "version_code_last";
    
    /**
     * System time when shared preferences were examined and took into account
     * by some receiver. We use this for the Service to track time when it
     * recreated alarms last time...
     */
    public static final String KEY_PREFERENCES_EXAMINE_TIME = "preferences_examine_time";

    /**
     * Notify of commands in the queue
     */
    public static final String KEY_NOTIFICATIONS_QUEUE = "notifications_queue";
    
    private MyPreferences(){
        throw new AssertionError();
    }
    
    /**
     * @return DefaultSharedPreferences for this application
     */
    public static SharedPreferences getDefaultSharedPreferences() {
        Context context = MyContextHolder.get().context();
        if (context == null) {
            MyLog.e(TAG, "getDefaultSharedPreferences - Was not initialized yet");
            for(StackTraceElement element : Thread.currentThread().getStackTrace()) { 
                MyLog.v(TAG, element.toString()); 
            }
            return null;
        } else {
            return PreferenceManager.getDefaultSharedPreferences(context);
        }
    }

    public static boolean shouldSetDefaultValues() {
        SharedPreferences sp = getSharedPreferences(PreferenceManager.KEY_HAS_SET_DEFAULT_VALUES);
        if (sp == null) {
            return false;
        } else {
            boolean areSetAlready = sp
                    .getBoolean(PreferenceManager.KEY_HAS_SET_DEFAULT_VALUES, false);
            return !areSetAlready;
        }
    }
    
    public static void setDefaultValues(int resId, boolean readAgain) {
        Context context = MyContextHolder.get().context();
        if (context == null) {
            MyLog.e(TAG, "setDefaultValues - Was not initialized yet");
        } else {
            PreferenceManager.setDefaultValues(context, resId, readAgain);
        }
    }
    
    public static SharedPreferences getSharedPreferences(String name) {
        Context context = MyContextHolder.get().context();
        if (context == null) {
            MyLog.e(TAG, "getSharedPreferences for " + name + " - were not initialized yet");
            for(StackTraceElement element : Thread.currentThread().getStackTrace()) { 
                MyLog.v(TAG, element.toString()); 
            }
            return null;
        } else {
            return context.getSharedPreferences(name, android.content.Context.MODE_PRIVATE);
        }
    }

    public static final int MILLISECONDS = 1000;
    private static final int SYNC_FREQUENCY_DEFAULT_SECONDS = 180;
    /**
     * @return the number of seconds between two sync ("fetch"...) actions.
     */
    public static long getSyncFrequencySeconds() {
        long frequencySeconds = SYNC_FREQUENCY_DEFAULT_SECONDS;
        SharedPreferences sp = getDefaultSharedPreferences();
        if (sp != null) {
            long frequencySecondsStored = Long.parseLong(getDefaultSharedPreferences().getString(MyPreferences.KEY_FETCH_FREQUENCY, "0"));
            if (frequencySecondsStored > 0) { 
                frequencySeconds = frequencySecondsStored;
            }
        }
        return frequencySeconds;
    }

    /**
     * @return the number of milliseconds between two sync ("fetch"...) actions.
     */
    public static long getSyncFrequencyMs() {
        return (getSyncFrequencySeconds() * MILLISECONDS);
    }
    
    /**
     *  Event: Preferences have changed right now
     *  Remember when last changes to the preferences were made
     */
    public static void onPreferencesChanged() {
        SharedPreferences sp = getDefaultSharedPreferences();
        if (sp != null) {
            sp.edit()
            .putLong(KEY_PREFERENCES_CHANGE_TIME,
                    java.lang.System.currentTimeMillis()).commit();
        }
    }
    
    /**
     * @return System time when AndStatus preferences were last time changed. 
     * We take into account here time when accounts were added/removed...
     */
    public static long getPreferencesChangeTime() {
        long preferencesChangeTime = 0;
        SharedPreferences sp = getDefaultSharedPreferences();
        if (sp != null) {
            preferencesChangeTime = getDefaultSharedPreferences().getLong(KEY_PREFERENCES_CHANGE_TIME, 0);            
        }
        return preferencesChangeTime;
    }
    
    /**
     * Standard directory in which to place databases
     */
    private static String DIRECTORY_DATABASES = "databases";
    
    /**
     * This function works just like {@link android.content.Context#getExternalFilesDir
     * Context.getExternalFilesDir}, but it takes {@link #KEY_USE_EXTERNAL_STORAGE} into account,
     * so it returns directory either on internal or external storage.
     * 
     * @param type The type of files directory to return.  May be null for
     * the root of the files directory or one of
     * the following Environment constants for a subdirectory:
     * {@link android.os.Environment#DIRECTORY_PICTURES Environment.DIRECTORY_...} (since API 8),
     * {@link #DIRECTORY_DATABASES}
     * @param forcedUseExternalStorage if not null, use this value instead of stored in preferences as {@link #KEY_USE_EXTERNAL_STORAGE}
     * 
     * @return directory, already created for you OR null in case of error
     * @see <a href="http://developer.android.com/guide/topics/data/data-storage.html#filesExternal">filesExternal</a>
     */
    public static File getDataFilesDir(String type, Boolean forcedUseExternalStorage) {
        File baseDir = null;
        String pathToAppend = "";
        File dir = null;
        String textToLog = null;
        boolean useExternalStorage = false;
        MyContext myContext = MyContextHolder.get();
        if (myContext.context() == null) {
            textToLog = "getDataFilesDir - no android.content.Context yet";
        } else {
            if (forcedUseExternalStorage == null) {
                useExternalStorage = getDefaultSharedPreferences().getBoolean(KEY_USE_EXTERNAL_STORAGE, false); 
            } else {
                useExternalStorage = forcedUseExternalStorage;
            }
            if (useExternalStorage) {
                String state = Environment.getExternalStorageState();
                if (Environment.MEDIA_MOUNTED.equals(state)) {    
                    // We can read and write the media
                    baseDir = Environment.getExternalStorageDirectory();
                    pathToAppend = "Android/data/" + myContext.context().getPackageName() + "/files";
                } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
                    textToLog = "getDataFilesDir - We can only read External storage";
                } else {    
                    textToLog = "getDataFilesDir - error accessing External storage";
                }                
            } else {
                baseDir = Environment.getDataDirectory();
                pathToAppend = "data/" + myContext.context().getPackageName();
            }
            if (baseDir != null) {
                if (type != null) {
                    pathToAppend = pathToAppend + "/" + type;
                }
                dir = new File(baseDir, pathToAppend);
                if (!dir.exists()) {
                    dir.mkdirs();
                    if (!dir.exists()) {
                        textToLog = "getDataFilesDir - Could not create '" + dir.getPath() + "'";
                        dir = null;
                    }
                }
            }
            if (MyLog.isLoggable(TAG, MyLog.VERBOSE)) {
                MyLog.v(TAG, (useExternalStorage ? "External" : "Internal") + " path: '" 
                + ( (dir == null) ? "(null)" 
                + "; baseDir=" + baseDir
                + "; path to append=" + pathToAppend: dir.getPath()) + "'");
            }
        }
        if (textToLog != null) {
            MyLog.i(TAG, textToLog);
        }
        
        return dir;
    }

    /**
     * Extends {@link android.content.ContextWrapper#getDatabasePath(java.lang.String)}
     * @param name The name of the database for which you would like to get its path.
     * @param forcedUseExternalStorage if not null, use this value instead of stored in preferences as {@link #KEY_USE_EXTERNAL_STORAGE}
     * @return
     */
    public static File getDatabasePath(String name, Boolean forcedUseExternalStorage) {
        File dbDir = getDataFilesDir(MyPreferences.DIRECTORY_DATABASES, forcedUseExternalStorage);
        File dbAbsolutePath = null;
        if (dbDir != null) {
            dbAbsolutePath = new File(dbDir.getPath() + "/" + name);
        }
        return dbAbsolutePath;
    }
    
    /**
     * Simple check that allows to prevent data access errors
     */
    public static boolean isDataAvailable() {
        return (getDataFilesDir(null, null) != null);
    }

    /**
     * Load the theme according to the preferences.
     */
    public static void loadTheme(String TAG, Context context) {
        boolean light = getDefaultSharedPreferences().getBoolean("appearance_light_theme", false);
        StringBuilder themeName = new StringBuilder();
        String name = getDefaultSharedPreferences().getString("theme", "AndStatus");
        if (name.indexOf("Theme.") > -1) {
            name = name.substring(name.indexOf("Theme."));
        }
        themeName.append("Theme.");
        if (light) {
            themeName.append("Light.");
        }
        themeName.append(name);
        int themeId = context.getResources().getIdentifier(themeName.toString(), "style",
                "org.andstatus.app");
        if (MyLog.isLoggable(TAG, MyLog.VERBOSE)) {
            MyLog.v(TAG, "loadTheme; theme=\"" + themeName.toString() + "\"; id=" + Integer.toHexString(themeId));
        }
        context.setTheme(themeId);
    }
    
}
