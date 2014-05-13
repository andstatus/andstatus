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

package org.andstatus.app.context;

import org.andstatus.app.TimelineActivity;
import org.andstatus.app.util.MyLog;

import android.app.backup.BackupManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.text.TextUtils;

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
    public static final String KEY_SYNC_FREQUENCY_SECONDS = "fetch_frequency";
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
    public static final String KEY_SENDING_MESSAGES_LOG_ENABLED = "sending_messages_log_enabled";
    
    public static final String KEY_THEME_SIZE = "theme_size";
    public static final String KEY_THEME_COLOR = "theme_color";
    public static final String KEY_SHOW_AVATARS = "show_avatars";

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

    private static final int SYNC_FREQUENCY_DEFAULT_SECONDS = 180;
    /**
     * @return the number of seconds between two sync ("fetch"...) actions.
     */
    public static long getSyncFrequencySeconds() {
        long frequencySeconds = SYNC_FREQUENCY_DEFAULT_SECONDS;
        SharedPreferences sp = getDefaultSharedPreferences();
        if (sp != null) {
            long frequencySecondsStored = Long.parseLong(getDefaultSharedPreferences().getString(MyPreferences.KEY_SYNC_FREQUENCY_SECONDS, "0"));
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
        return java.util.concurrent.TimeUnit.SECONDS.toMillis(getSyncFrequencySeconds());
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
            Context context = MyContextHolder.get().context();
            if (context != null) {
                new BackupManager(context).dataChanged();
            }
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
    public static final String DIRECTORY_DATABASES = "databases";
    public static final String DIRECTORY_AVATARS = "avatars";
    
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
        return getDataFilesDir(type, forcedUseExternalStorage, true);
    }

    public static File getDataFilesDir(String type, Boolean forcedUseExternalStorage, boolean logged) {
        File dir = null;
        String textToLog = null;
        MyContext myContext = MyContextHolder.get();
        if (myContext.context() == null) {
            textToLog = "getDataFilesDir - no android.content.Context yet";
        } else {
            if (isStorageExternal(forcedUseExternalStorage)) {
                String state = Environment.getExternalStorageState();
                if (Environment.MEDIA_MOUNTED.equals(state)) {    
                    // We can read and write the media
                    dir = myContext.context().getExternalFilesDir(type);
                } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
                    textToLog = "getDataFilesDir - We can only read External storage";
                } else {    
                    textToLog = "getDataFilesDir - error accessing External storage";
                }                
            } else {
                dir = myContext.context().getFilesDir();
                if (!TextUtils.isEmpty(type)) {
                    dir = new File(dir, type);
                }
            }
            if (dir != null && !dir.exists()) {
                try {
                    dir.mkdirs();
                } catch (Exception e) {
                    if (logged) {
                        MyLog.e(TAG, "Error creating directory", e);
                    }
                } finally {
                    if (!dir.exists()) {
                        textToLog = "getDataFilesDir - Could not create '" + dir.getPath() + "'";
                        dir = null;
                    }
                }
            }
            if (logged && MyLog.isLoggable(TAG, MyLog.VERBOSE)) {
                MyLog.v(TAG, (isStorageExternal(forcedUseExternalStorage) ? "External" : "Internal") 
                        + " path: '" + ( (dir == null) ? "(null)" : dir ) + "'");
            }
        }
        if (logged && textToLog != null) {
            MyLog.i(TAG, textToLog);
        }
        return dir;
    }
    
    public static boolean isStorageExternal(Boolean forcedUseExternalStorage) {
        boolean useExternalStorage = false;
        if (forcedUseExternalStorage == null) {
            useExternalStorage = getBoolean(KEY_USE_EXTERNAL_STORAGE, false); 
        } else {
            useExternalStorage  = forcedUseExternalStorage;
        }
        return useExternalStorage;
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        boolean value = defaultValue;
        SharedPreferences sp = getDefaultSharedPreferences();
        if (sp != null) {
            value = getDefaultSharedPreferences().getBoolean(key, false); 
        }
        return value;
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
        return getDataFilesDir(null, null) != null;
    }

    /**
     * Load the theme according to the preferences.
     */
    public static void loadTheme(Object tag, Context context) {
        String themeColor = getDefaultSharedPreferences().getString(KEY_THEME_COLOR, "DeviceDefault");
        StringBuilder themeName = new StringBuilder("Theme.");
        themeName.append(themeColor);
        themeName.append(".AndStatus.");
        String themeSize = getDefaultSharedPreferences().getString(KEY_THEME_SIZE, "StandardSize");
        themeName.append(themeSize);
        int themeId = context.getResources().getIdentifier(themeName.toString(), "style",
                "org.andstatus.app");
        if (themeId == 0 || MyLog.isLoggable(tag, MyLog.VERBOSE)) {
            String text = "loadTheme; theme=\"" + themeName.toString() + "\"; id=" + Integer.toHexString(themeId);
            if (themeId == 0) {
                MyLog.e(tag, text);
            } else {
                MyLog.v(tag, text);
            }
        }
        context.setTheme(themeId);
    }

    /**
     * @param context
     * @param update
     * @return true if we opened previous version
     * @throws NameNotFoundException
     */
    public static boolean checkAndUpdateLastOpenedAppVersion(Context context, boolean update) {
        boolean changed = false;
        int versionCodeLast =  getDefaultSharedPreferences().getInt(KEY_VERSION_CODE_LAST, 0);
        PackageManager pm = context.getPackageManager();
        PackageInfo pi;
        try {
            pi = pm.getPackageInfo(context.getPackageName(), 0);
            int versionCode =  pi.versionCode;
            if (versionCodeLast < versionCode) {
                // Even if the User will see only the first page of the Help activity,
                // count this as showing the Change Log
                MyLog.v(TAG, "Last opened version=" + versionCodeLast + ", current is " + versionCode
                        + (update ? ", updating" : "")
                        );
                changed = true;
                if ( update && MyContextHolder.get().isReady()) {
                    getDefaultSharedPreferences().edit().putInt(KEY_VERSION_CODE_LAST, versionCode).commit();
                }
            }
        } catch (NameNotFoundException e) {
            MyLog.e(TAG, "Unable to obtain package information", e);
        }
        return changed;
    }

    public static boolean showAvatars() {
        return getDefaultSharedPreferences().getBoolean(MyPreferences.KEY_SHOW_AVATARS, true);
    }
}
