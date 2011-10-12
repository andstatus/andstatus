/**
 * Copyright (C) 2010-2011 yvolk (Yuri Volkov), http://yurivolkov.com
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
package com.xorcode.andtweet.data;

import com.xorcode.andtweet.TwitterUser;
import com.xorcode.andtweet.util.MyLog;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * This is central point of accessing SharedPreferences, used by AndTweet
 * @author yuvolkov
 */
public class MyPreferences {
    private static final String TAG = MyPreferences.class.getSimpleName();
    /**
     * Single context object for which we will request SharedPreferences
     */
    private static Context context;
    private static String origin;
    
    public static final String KEY_OAUTH = "oauth";
    /**
     * Is this user NOT temporal?
     * (TODO: refactor the key name and invert value)
     */
    public static final String KEY_IS_NOT_TEMPORAL = "was_authenticated";
    /**
     * Was current user ( user set in global preferences) authenticated last
     * time credentials were verified? CredentialsVerified.NEVER - after changes
     * of password/OAuth...
     */
    public static final String KEY_CREDENTIALS_VERIFIED = "credentials_verified";
    /**
     * This is sort of button to start verification of credentials
     */
    public static final String KEY_VERIFY_CREDENTIALS = "verify_credentials";
    /**
     * Process of authentication was started (by {@link #PreferencesActivity})
     */
    public static final String KEY_AUTHENTICATING = "authenticating";
    /**
     * Current User
     */
    public static final String KEY_TWITTER_USERNAME = "twitter_username";
    /**
     * New Username typed / selected in UI
     * It doesn't immediately change "Current User"
     */
    public static final String KEY_TWITTER_USERNAME_NEW = "twitter_username_new";
    public static final String KEY_TWITTER_PASSWORD = "twitter_password";
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
     * @param origin - object that initialized the class 
     */
    public static void initialize(Context context_in, java.lang.Object object ) {
        String origin_in = object.getClass().getSimpleName();
        if (context == null) {
            // Maybe we should use context_in.getApplicationContext() ??
            context = context_in.getApplicationContext();
            origin = origin_in;
            TwitterUser.initialize();
            MyLog.v(TAG, "Initialized by " + origin);
        } else {
            MyLog.v(TAG, "Already initialized by " + origin +  " (called by: " + origin_in + ")");
        }
    }

    
    /**
     * Forget everything in order to reread from the sources if it will be needed
     */
    public static void forget() {
        context = null;
        origin = null;
        TwitterUser.forget();
        MyLog.forget();
    }
    
    public static boolean isInitialized() {
        return (context != null);
    }
    
    /**
     * @return DefaultSharedPreferences for this application
     */
    public static SharedPreferences getDefaultSharedPreferences() {
        if (context == null) {
            Log.e(TAG, "Was not initialized yet");
            return null;
        } else {
            return PreferenceManager.getDefaultSharedPreferences(context);
        }
    }

    public static void setDefaultValues(int resId, boolean readAgain) {
        if (context == null) {
            Log.e(TAG, "Was not initialized yet");
        } else {
            PreferenceManager.setDefaultValues(context, resId, readAgain);
        }
    }
    
    public static SharedPreferences getSharedPreferences(String name, int mode) {
        if (context == null) {
            Log.e(TAG, "Was not initialized yet");
            return null;
        } else {
            return context.getSharedPreferences(name, mode);
        }
    }

    public static Context getContext() {
        return context;
    }
    
}
