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

import com.xorcode.andtweet.AndTweetService;
import com.xorcode.andtweet.TwitterUser;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * This is central point of accessing SharedPreferences, used by AndTweet
 * @author yuvolkov
 */
public class AndTweetPreferences {
    private static final String TAG = AndTweetPreferences.class.getSimpleName();
    /**
     * Single context object for which we will request SharedPreferences
     */
    private static Context context;
    private static String origin;

    private AndTweetPreferences(){
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
            AndTweetService.v(TAG, "Initialized by " + origin);
        } else {
            AndTweetService.v(TAG, "Already initialized by " + origin +  " (called by: " + origin_in + ")");
        }
    }

    
    /**
     * Forget everything in order to reread from the sources if it will be needed
     */
    public static void forget() {
        context = null;
        origin = null;
        TwitterUser.forget();
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
