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

package org.andstatus.app;

import org.andstatus.app.MyService.CommandEnum;
import org.andstatus.app.util.MyLog;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * This receiver starts and stops {@link MyService}
 * @author torgny.bjers
 */
public class MyServiceManager extends BroadcastReceiver {
    private static final String TAG = MyServiceManager.class.getSimpleName();

    /**
     * Is the service started.
     * @See <a href="http://groups.google.com/group/android-developers/browse_thread/thread/8c4bd731681b8331/bf3ae8ef79cad75d">here</a>
     */
    private static boolean isStarted = false;

    /**
     * If true repeating alarms will be ignored
     */
    private static boolean ignoreAlarms = false;
    
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
            MyLog.d(TAG, "Starting service on boot.");
            // Assume preferences were changed
            startAndStatusService(context, new MyService.CommandData(
                    CommandEnum.PREFERENCES_CHANGED, ""));
        } else if (intent.getAction().equals("android.intent.action.ACTION_SHUTDOWN")) {
            // This system broadcast is Since: API Level 4
            // We need this to persist unsaved data
            MyLog.d(TAG, "Stopping service on Shutdown");
            stopAndStatusService(context, true);
        } else if (intent.getAction().equals(MyService.ACTION_ALARM)) {
            if (ignoreAlarms) {
                MyLog.d(TAG, "Repeating Alarm: Ignore");
            } else {
                MyLog.d(TAG, "Repeating Alarm: Automatic update");
                startAndStatusService(context, new MyService.CommandData(
                        CommandEnum.AUTOMATIC_UPDATE, ""));
            }
        } else if (intent.getAction().equals(MyService.ACTION_SERVICE_STOPPED)) {
            MyLog.d(TAG, "Notification received: Service stopped");
            isStarted = false;
        } else {
            Log.e(TAG, "Received unexpected intent: " + intent.toString());
        }
    }

    /**
     * Starts MyService if it is not already started
     * and schedule Automatic updates according to the preferences.
     * 
     * @param context 
     * @param commandData to the service or null 
     */
    public static void startAndStatusService(Context context, MyService.CommandData commandData) {
        isStarted = true;
        ignoreAlarms = false;
        Intent serviceIntent = new Intent(IMyService.class.getName());
        if (commandData != null) {
            serviceIntent = commandData.toIntent(serviceIntent);
        }
        context.startService(serviceIntent);
    }

    /**
     * Stop  {@link MyService}
     * @param context
     * @param ignoreAlarms - if true repeating alarms will be ignored also
     */
    public static void stopAndStatusService(Context context, boolean ignoreAlarms_in) {
        isStarted = false;
        ignoreAlarms = ignoreAlarms_in;
        context.stopService(new Intent(IMyService.class.getName()));
    }

    /**
     * Is the service started. 
     * @See <a href="http://groups.google.com/group/android-developers/browse_thread/thread/8c4bd731681b8331/bf3ae8ef79cad75d">here</a>
     */
    public static boolean isStarted() {
        return isStarted;
    }
    
}
