/* 
 * Copyright (C) 2008 Torgny Bjers
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

package com.xorcode.andtweet;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * @author torgny.bjers
 *
 */
public class AndTweetServiceManager extends BroadcastReceiver {

    public static final String TAG = "AndTweetServiceManager";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals("android.intent.action.BOOT_COMPLETED")) {
            PreferenceManager.setDefaultValues(context, R.xml.preferences, false);

            Log.d(TAG, "Starting service on boot.");
            startAndTweetService(context);
        
        } else {
            Log.e(TAG, "Received unexpected intent: " + intent.toString());
        }
    }

    /**
     * Starts AndTweetService if it is not already started.
     * 
     * @param context 
     * @return
     */
    public static void startAndTweetService(Context context) {
        SharedPreferences mSP = PreferenceManager.getDefaultSharedPreferences(context);
        if (mSP.contains("automatic_updates") && mSP.getBoolean("automatic_updates", false)) {
            Log.d(TAG, "Alarm started. Automatic updates turned on.");
            AndTweetService.startAutomaticUpdates(context);
        } else {
            Log.d(TAG, "Alarm cancelled. Automatic updates turned off.");
            AndTweetService.stopAutomaticUpdates(context);
        }
    }

}
