/**
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

import org.andstatus.app.R;
import org.andstatus.app.msg.TimelineActivity;
import org.andstatus.app.util.MyLog;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.backup.BackupManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.View;

import java.io.File;
import java.util.Locale;

/**
 * This is a central point of accessing SharedPreferences
 * @author yvolk@yurivolkov.com
 */
public class MyPreferences {
    private static final String TAG = MyPreferences.class.getSimpleName();

    public static final String KEY_USER_IN_TIMELINE = "user_in_timeline";
    
    public static final String KEY_HISTORY_SIZE = "history_size";
    public static final String KEY_HISTORY_TIME = "history_time";
    /**
     * Period of automatic updates in seconds
     */
    public static final String KEY_SYNC_FREQUENCY_SECONDS = "fetch_frequency";
    public static final String KEY_SYNC_INDICATOR_ON_TIMELINE = "sync_indicator_on_timeline";
    public static final String KEY_SYNC_WHILE_USING_APPLICATION = "sync_while_using_application";
    public static final String KEY_DOWNLOAD_ATTACHMENTS_OVER_WIFI_ONLY = "download_attachments_over_wifi_only";
    public static final String KEY_CONNECTION_TIMEOUT_SECONDS = "connection_timeout";
    public static final String KEY_RINGTONE_PREFERENCE = "notification_ringtone";
    public static final String KEY_COMMANDS_QUEUE = "commands_queue";

    public static final String KEY_USE_KITKAT_MEDIA_CHOOSER = "use_kitkat_media_chooser";
    public static final String KEY_DEBUGGING_INFO_IN_UI = "debugging_info_in_ui";
    
    /**
     * System time when shared preferences were changed
     */
    public static final String KEY_PREFERENCES_CHANGE_TIME = "preferences_change_time";
    public static final String KEY_DATA_PRUNED_DATE = "data_pruned_date";
    /**
     * Minimum logging level for the whole application (i.e. for any tag)
     */
    public static final String KEY_MIN_LOG_LEVEL = "min_log_level";
    public static final String KEY_SENDING_MESSAGES_LOG_ENABLED = "sending_messages_log_enabled";
    public static final String KEY_LOG_NETWORK_LEVEL_MESSAGES = "log_network_level_messages";
    public static final String KEY_LOG_EVERYTHING_TO_FILE = "log_everything_to_file";
    
    public static final String KEY_THEME_SIZE = "theme_size";
    public static final String KEY_THEME_COLOR = "theme_color";
    public static final String KEY_ACTION_BAR_COLOR = "action_bar_color";
    public static final String KEY_BACKGROUND_COLOR = "background_color";
    public static final String KEY_SHOW_AVATARS = "show_avatars";
    public static final String KEY_SHOW_ATTACHED_IMAGES = "show_attached_images";
    public static final String KEY_ATTACH_IMAGES = "attach_images";
    public static final String KEY_SHOW_ORIGIN = "show_origin";

    public static final String KEY_CUSTOM_LOCALE = "custom_locale";
    public static final String CUSTOM_LOCALE_DEFAULT = "default";
    
    public static final String KEY_USE_EXTERNAL_STORAGE = "use_external_storage";
    /**
     * New value for #KEY_USE_EXTERNAL_STORAGE to be confirmed/processed
     */
    public static final String KEY_USE_EXTERNAL_STORAGE_NEW = "use_external_storage_new";
    public static final String KEY_ENABLE_ANDROID_BACKUP = "enable_android_backup";
    /**
     * Is the timeline combined in {@link TimelineActivity} 
     */
    public static final String KEY_TIMELINE_IS_COMBINED = "timeline_is_combined";
    /**
     * Version code of last opened application (int) 
     */
    public static final String KEY_VERSION_CODE_LAST = "version_code_last";

    public static final String KEY_NOTIFICATIONS_ENABLED = "notifications_enabled";
	public static final String KEY_NOTIFY_OF_HOME_TIMELINE = "notifications_timeline";
	public static final String KEY_NOTIFY_OF_MENTIONS = "notifications_mentions";
	public static final String KEY_NOTIFY_OF_DIRECT_MESSAGES = "notifications_messages";
    public static final String KEY_NOTIFY_OF_COMMANDS_IN_THE_QUEUE = "notifications_queue";
    public static final String KEY_NOTIFICATION_ICON_ALTERNATIVE = "notification_icon_alternative";

    public static final String KEY_ENTER_SENDS_MESSAGE = "enter_sends_message";
    public static final String KEY_OLD_MESSAGES_FIRST_IN_CONVERSATION = "old_messages_first_in_conversation";
    public static final String KEY_SYNC_AFTER_MESSAGE_WAS_SENT = "sync_after_message_was_sent";
    public static final String KEY_MARK_REPLIES_IN_TIMELINE = "mark_replies_in_timeline";

    private MyPreferences(){
        // Non instantiable
    }
    
    private static volatile boolean defaultSharedPreferencesLocked = false;
    public static SharedPreferences getDefaultSharedPreferences() {
        if (defaultSharedPreferencesLocked) {
            return null;
        }
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

    public static void lockDefaultSharedPreferences() {
        defaultSharedPreferencesLocked = true;
    }

    public static void unlockDefaultSharedPreferences() {
        defaultSharedPreferencesLocked = false;
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
    
    public static void setDefaultValues() {
        Context context = MyContextHolder.get().context();
        if (context == null) {
            MyLog.e(TAG, "setDefaultValues - Was not initialized yet");
        } else {
            for (MyPreferencesGroupsEnum item : MyPreferencesGroupsEnum.values()) {
                if (item != MyPreferencesGroupsEnum.UNKNOWN) {
                    PreferenceManager.setDefaultValues(context, item.getPreferencesXmlResId(), false);
                }
            }
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

    private static final long SYNC_FREQUENCY_DEFAULT_SECONDS = 180;
    /**
     * @return the number of seconds between two sync ("fetch"...) actions.
     */
    public static long getSyncFrequencySeconds() {
        return getLongStoredAsString(KEY_SYNC_FREQUENCY_SECONDS, SYNC_FREQUENCY_DEFAULT_SECONDS);
    }

    private static long getLongStoredAsString(String key, long defaultValue) {
        long longValue = defaultValue;
        try {
            long longValueStored = Long.parseLong(getString(key, "0"));
            if (longValueStored > 0) { 
                longValue = longValueStored;
            }
        } catch (NumberFormatException e) {
            MyLog.v(TAG, e);
        }
        return longValue;
    }

    public static String getString(String key, String defaultValue) {
        String longValue = defaultValue;
        SharedPreferences sp = getDefaultSharedPreferences();
        if (sp != null) {
            longValue = sp.getString(key, defaultValue);
        }
        return longValue;
    }
    
    private static final long CONNNECTION_TIMEOUT_DEFAULT_SECONDS = 30;
    public static int getConnectionTimeoutMs() {
        return (int) java.util.concurrent.TimeUnit.SECONDS.toMillis(getLongStoredAsString(
                KEY_CONNECTION_TIMEOUT_SECONDS, CONNNECTION_TIMEOUT_DEFAULT_SECONDS));
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
        putLong(KEY_PREFERENCES_CHANGE_TIME, System.currentTimeMillis());
        Context context = MyContextHolder.get().context();
        if (context != null && getBoolean(KEY_ENABLE_ANDROID_BACKUP, false)) {
            new BackupManager(context).dataChanged();
        }
    }

    public static boolean isEnLocale() {
        Locale locale = mLocale;
        if (locale == null) {
            locale = mDefaultLocale;
        }
        return  locale == null ? true : (locale.getLanguage().isEmpty() ? true : locale.getLanguage().startsWith("en")); 
    }
    
    private static volatile Locale mLocale = null;
    private static volatile Locale mDefaultLocale = null;
    public static void setLocale(ContextWrapper contextWrapper) {
        if (mDefaultLocale == null) {
            mDefaultLocale = contextWrapper.getBaseContext().getResources().getConfiguration().locale;
        }
        String strLocale = getString(KEY_CUSTOM_LOCALE, CUSTOM_LOCALE_DEFAULT);
        if (!strLocale.equals(CUSTOM_LOCALE_DEFAULT) || mLocale != null) {
            Configuration config = contextWrapper.getBaseContext().getResources().getConfiguration();
            if (strLocale.equals(CUSTOM_LOCALE_DEFAULT)) {
                customizeConfig(contextWrapper, config, mDefaultLocale);
                mLocale = null;
            } else {
                mLocale = new Locale(strLocale);
                customizeConfig(contextWrapper, config, mLocale);
            }
        }
    }
    
    public static Configuration onConfigurationChanged(ContextWrapper contextWrapper, Configuration newConfig) {
        if (mLocale == null || mDefaultLocale == null) {
            mDefaultLocale = newConfig.locale;
        }
        MyTheme.forget();
        return customizeConfig(contextWrapper, newConfig, mLocale);
    }
    
    private static Configuration customizeConfig(ContextWrapper contextWrapper, Configuration newConfig, Locale locale) {
        Configuration configCustom = newConfig;
        if (locale != null && !newConfig.locale.equals(locale)) {
            Locale.setDefault(locale);
            configCustom = new Configuration(newConfig);
            setLocale(configCustom, locale);
            contextWrapper.getBaseContext().getResources().updateConfiguration(configCustom, contextWrapper.getBaseContext().getResources().getDisplayMetrics());            
        }
        return configCustom;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private static void setLocale(Configuration configCustom, Locale locale) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            configCustom.setLocale(locale);
        } else {
            configCustom.locale = locale;
        }
    }    
    
	public static void putLong(String key, long value) {
        SharedPreferences sp = getDefaultSharedPreferences();
        if (sp != null) {
            sp.edit().putLong(key, value).commit();
        }
    }

    public static void putBoolean(String key, boolean value) {
        SharedPreferences sp = getDefaultSharedPreferences();
        if (sp != null) {
            sp.edit().putBoolean(key, value).commit();
        }
    }
	
    /**
     * @return System time when AndStatus preferences were last time changed. 
     * We take into account here time when accounts were added/removed...
     */
    public static long getPreferencesChangeTime() {
		return getLong(KEY_PREFERENCES_CHANGE_TIME);
    }
    
    public static long getLong(String key) {
        long value = 0;
        SharedPreferences sp = getDefaultSharedPreferences();
        if (sp != null) {
            value = sp.getLong(key, 0);
        }
        return value;
    }
	
	/**
     * Standard directory in which to place databases
     */
    public static final String DIRECTORY_DATABASES = "databases";
    public static final String DIRECTORY_DOWNLOADS = "downloads";
    
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
        final String method = "getDataFilesDir";
        boolean logEnabled = false;
        File dir = null;
        StringBuilder textToLog = new StringBuilder();
        MyContext myContext = MyContextHolder.get();
        if (myContext.context() == null) {
            textToLog.append("No android.content.Context yet");
        } else {
            if (isStorageExternal(forcedUseExternalStorage)) {
                if (isWritableExternalStorageAvailable(textToLog)) {
                    try {
                        dir = myContext.context().getExternalFilesDir(type);
                    } catch (NullPointerException e) {
                        // I noticed this exception once, but that time it was related to SD card malfunction...
                        if (logged) {
                            MyLog.e(TAG, method, e);
                        }
                    }
                }
            }
            if (dir == null) {
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
                        MyLog.e(TAG, method + "; Error creating directory", e);
                    }
                } finally {
                    if (!dir.exists()) {
                        textToLog.append("Could not create '" + dir.getPath() + "'");
                        dir = null;
                    }
                }
            }
            if (logged && logEnabled && MyLog.isVerboseEnabled()) {
                MyLog.v(TAG, method + "; " + (isStorageExternal(forcedUseExternalStorage) ? "External" : "Internal")
                        + " path: '" + ((dir == null) ? "(null)" : dir) + "'");
            }
        }
        if (logged && textToLog.length() > 0) {
            MyLog.i(TAG, method + "; " + textToLog);
        }
        return dir;
    }

    public static boolean isWritableExternalStorageAvailable(StringBuilder textToLog) {
        String state = Environment.getExternalStorageState();
        boolean available = false;
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            available = true;
        } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            if (textToLog != null) {
                textToLog.append("We can only read External storage");
            }
        } else {    
            if (textToLog != null) {
                textToLog.append("Error accessing External storage, state='" + state + "'");
            }
        }
        return available;
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

    public static boolean isSyncWhileUsingApplicationEnabled() {
        return getBoolean(KEY_SYNC_WHILE_USING_APPLICATION, true);
    }
    
    public static boolean getBoolean(String key, boolean defaultValue) {
        boolean value = defaultValue;
        SharedPreferences sp = getDefaultSharedPreferences();
        if (sp != null) {
            value = sp.getBoolean(key, defaultValue);
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
        File dbDir = getDataFilesDir(DIRECTORY_DATABASES, forcedUseExternalStorage);
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
        return getBoolean(KEY_SHOW_AVATARS, true);
    }

    public static boolean showAttachedImages() {
        return getBoolean(KEY_SHOW_ATTACHED_IMAGES, true);
    }

    public static boolean showOrigin() {
        return getBoolean(KEY_SHOW_ORIGIN, false);
    }

    public static UserInTimeline userInTimeline() {
        return UserInTimeline.load(getString(KEY_USER_IN_TIMELINE, ""));
    }
}
