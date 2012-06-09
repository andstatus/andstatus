/**
 * Copyright (C) 2010-2012 yvolk (Yuri Volkov), http://yurivolkov.com
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

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.util.MyLog;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.File;

/**
 * This is central point of accessing SharedPreferences and other global objects, used by AndStatus
 * @author yvolk
 */
public class MyPreferences {
    private static final String TAG = MyPreferences.class.getSimpleName();
    private static boolean misInitialized = false;
    /**
     * Single context object for which we will request SharedPreferences
     */
    private static Context context;
    /**
     * Name of the object that initialized the class
     */
    private static String origin;
    private static MyDatabase db;
    
    /**
     * This is sort of button to start verification of credentials
     */
    public static final String KEY_VERIFY_CREDENTIALS = "verify_credentials";

    public static final String KEY_HISTORY_SIZE = "history_size";
    public static final String KEY_HISTORY_TIME = "history_time";
    public static final String KEY_FETCH_FREQUENCY = "fetch_frequency";
    public static final String KEY_AUTOMATIC_UPDATES = "automatic_updates";
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
     * System time when shared preferences were examined and took into account
     * by some receiver. We use this for the Service to track time when it
     * recreated alarms last time...
     */
    public static final String KEY_PREFERENCES_EXAMINE_TIME = "preferences_examine_time";

    private MyPreferences(){
    }
    
    /**
     * 
     * @param context_in
     * @param object - object that initialized the class 
     */
    public static Context initialize(Context context_in, java.lang.Object object ) {
        String origin_in = object.getClass().getSimpleName();
        if (origin_in.contentEquals("String")) {
            origin_in = origin_in.toString();
        }
        if (!misInitialized) {
            // Maybe we should use context_in.getApplicationContext() ??
            context = context_in.getApplicationContext();
            origin = origin_in;
            
            /* This may be useful to know from where the class was initialized
            StackTraceElement[] elements = Thread.currentThread().getStackTrace(); 
            for(int i=0; i<elements.length; i++) { 
                Log.v(TAG, elements[i].toString()); 
            }
            */
            
            MyLog.v(TAG, "Initialized by " + origin + " context: " + context.getClass().getName());
            misInitialized = true;
            
            MyAccount.initialize();
        } else {
            MyLog.v(TAG, "Already initialized by " + origin +  " (called by: " + origin_in + ")");
        }
        return getContext();
    }

    /**
     * Forget everything in order to reread from the sources if it will be needed
     * e.g. after configuration changes
     */
    public static void forget() {
        misInitialized = false;
        MyAccount.forget();
        if (db != null) {
            try {
                db.close();
            } catch (Exception e) {
                Log.e(TAG, "Error closing database " + e.getMessage());
            } finally {
                db = null;
            }
        }
        MyLog.forget();
        context = null;
        origin = null;
    }
    
    public static boolean isInitialized() {
        return (misInitialized);
    }
    
    /**
     * @return DefaultSharedPreferences for this application
     */
    public static SharedPreferences getDefaultSharedPreferences() {
        if (context == null) {
            Log.e(TAG, "getDefaultSharedPreferences - Was not initialized yet");
            return null;
        } else {
            return PreferenceManager.getDefaultSharedPreferences(context);
        }
    }

    public static void setDefaultValues(int resId, boolean readAgain) {
        if (context == null) {
            Log.e(TAG, "setDefaultValues - Was not initialized yet");
        } else {
            PreferenceManager.setDefaultValues(context, resId, readAgain);
        }
    }
    
    public static SharedPreferences getSharedPreferences(String name, int mode) {
        if (context == null) {
            Log.e(TAG, "getSharedPreferences - Was not initialized yet");
            return null;
        } else {
            return context.getSharedPreferences(name, mode);
        }
    }

    /**
     *  Remember when last changes to the preferences were made
     */
    public static void setPreferencesChangedNow() {
        if (isInitialized()) {
            getDefaultSharedPreferences()
            .edit()
            .putLong(MyPreferences.KEY_PREFERENCES_CHANGE_TIME,
                    java.lang.System.currentTimeMillis()).commit();
        }
    }
    
    public static Context getContext() {
        return context;
    }

    public static MyDatabase getDatabase() {
        if (db == null) {
            if (context == null) {
                Log.e(TAG, "getDatabase - Was not initialized yet");
            } else {
                db = new MyDatabase(context);
            }
        }
        return db;
    }
    
    /**
     * Standard directory in which to place databases
     */
    public static String DIRECTORY_DATABASES = "databases";
    
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
     * 
     * @return directory, already created for you OR null in case of error
     * @see <a href="http://developer.android.com/guide/topics/data/data-storage.html#filesExternal">filesExternal</a>
     */
    public static File getDataFilesDir(String type) {
        File baseDir = null;
        String pathToAppend = "";
        File dir = null;
        String textToLog = null;
        boolean useExternalStorage = false;
        if (context == null) {
            textToLog = "getDataFilesDir - Was not initialized yet";
        } else {
            useExternalStorage = getDefaultSharedPreferences().getBoolean(KEY_USE_EXTERNAL_STORAGE, false); 
            if (useExternalStorage) {
                String state = Environment.getExternalStorageState();
                if (Environment.MEDIA_MOUNTED.equals(state)) {    
                    // We can read and write the media
                    baseDir = Environment.getExternalStorageDirectory();
                    pathToAppend = "Android/data/" + context.getPackageName() + "/files";
                } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
                    textToLog = "getDataFilesDir - We can only read External storage";
                } else {    
                    textToLog = "getDataFilesDir - error accessing External storage";
                }                
            } else {
                baseDir = Environment.getDataDirectory();
                pathToAppend = "data/" + context.getPackageName();
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
            if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
                MyLog.v(TAG, (useExternalStorage ? "External" : "Internal") + " path: '" 
                + ( (dir == null) ? "(null)" : dir.getPath()) + "'");
            }
        }
        if (textToLog != null) {
            Log.i(TAG, textToLog);
        }
        
        return dir;
    }
    
    /**
     * Simple check that allows to prevent data access errors
     */
    public static boolean isDataAvailable() {
        return (getDataFilesDir(null) != null);
    }

    /**
     * Returns true not only for boolean, but for "1" also
     * @param o
     * @return  1 = true, 0 - false or null
     */
    public static int isTrue(Object o) {
        boolean is = false;
        try {
            if (o != null) {
                String val = o.toString();
                is = Boolean.parseBoolean(val);
                if (!is) {
                    if ( val.compareTo("1") == 0) {
                        is = true;
                    }
                }
            }
        } catch (Exception e) {}
        return (is ? 1 : 0);
    }
    

    /**
     * Load the theme according to the preferences.
     */
    public static void loadTheme(String TAG, android.content.Context context) {
        boolean light = MyPreferences.getDefaultSharedPreferences().getBoolean("appearance_light_theme", false);
        StringBuilder themeName = new StringBuilder();
        String name = MyPreferences.getDefaultSharedPreferences().getString("theme", "AndStatus");
        if (name.indexOf("Theme.") > -1) {
            name = name.substring(name.indexOf("Theme."));
        }
        themeName.append("Theme.");
        if (light) {
            themeName.append("Light.");
        }
        themeName.append(name);
        int themeId = (int) context.getResources().getIdentifier(themeName.toString(), "style",
                "org.andstatus.app");
        if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "loadTheme; theme=\"" + themeName.toString() + "\"; id=" + Integer.toHexString(themeId));
        }
        context.setTheme(themeId);
    }
    
}
