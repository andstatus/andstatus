/* 
 * Copyright (c) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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
import org.andstatus.app.MyService.ServiceState;
import org.andstatus.app.data.MyDatabase.TimelineTypeEnum;
import org.andstatus.app.data.MyPreferences;
import org.andstatus.app.util.MyLog;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * This receiver starts and stops {@link MyService} and also queries its state.
 * Android system creates new instance of this type on each Intent received. 
 * This is why we're keeping a state in static fields. 
 */
public class MyServiceManager extends BroadcastReceiver {
    private static final String TAG = MyServiceManager.class.getSimpleName();

    /**
     * Is the service started.
     * @See <a href="http://groups.google.com/group/android-developers/browse_thread/thread/8c4bd731681b8331/bf3ae8ef79cad75d">here</a>
     */
    private static volatile MyService.ServiceState mServiceState = ServiceState.UNKNOWN;
    /**
     * {@link System#nanoTime()} when the state was queued or received last time ( 0 - never )  
     */
    private static volatile long stateQueuedTime = 0;
    /**
     * If true, we sent state request and are waiting for reply from {@link MyService} 
     */
    private static boolean waitingForServiceState = false;
    /**
     * How long are we waiting for {@link MyService} response before deciding that the service is stopped
     */
    private static final int STATE_QUERY_TIMEOUT_SECONDS = 3;

    /**
     * If true repeating alarms will be ignored
     */
    private static boolean ignoreAlarms = false;
    
    
    private int instanceId;
    
    public MyServiceManager() {
        instanceId = MyPreferences.nextInstanceId();
        MyLog.v(TAG, TAG + " created, instanceId=" + instanceId);
    }
    
    @Override
    public void onReceive(Context context, Intent intent) {
        MyPreferences.initialize(context, this);
        String action = intent.getAction(); 
        if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
            MyLog.d(TAG, "Starting service on boot.");
            startMyService(new CommandData(
                    CommandEnum.BOOT_COMPLETED, ""));
        } else if (action.equals("android.intent.action.ACTION_SHUTDOWN")) {
            // This system broadcast is Since: API Level 4
            // We need this to persist unsaved data
            MyLog.d(TAG, "Stopping service on Shutdown");
            stopMyService(true);
        } else if (action.equals(MyService.ACTION_ALARM)) {
            if (ignoreAlarms) {
                MyLog.d(TAG, "Repeating Alarm: Ignore");
            } else {
                MyLog.d(TAG, "Repeating Alarm: Automatic update");
                startMyService(new CommandData(
                        CommandEnum.AUTOMATIC_UPDATE, "", TimelineTypeEnum.ALL, 0));
            }
        } else if (action.equals(MyService.ACTION_SERVICE_STATE)) {
            synchronized(mServiceState) {
                stateQueuedTime = System.nanoTime();
                waitingForServiceState = false;
                mServiceState = MyService.ServiceState.load(intent.getStringExtra(MyService.EXTRA_SERVICE_STATE));
            }
            MyLog.d(TAG, "Notification received: Service state=" + mServiceState);
        } else {
            Log.e(TAG, "Received unexpected intent: " + intent.toString());
        }
    }

    /**
     * Starts MyService  asynchronously if it is not already started
     * and schedule Automatic updates according to the preferences.
     * 
     * @param context 
     * @param commandData to the service or null 
     */
    public static void startMyService(CommandData commandData) {
        ignoreAlarms = false;
        Intent serviceIntent = new Intent(IMyService.class.getName());
        if (commandData != null) {
            serviceIntent = commandData.toIntent(serviceIntent);
        }
        MyPreferences.getContext().startService(serviceIntent);
    }

    /**
     * Stop  {@link MyService} asynchronously
     * @param context
     * @param ignoreAlarms - if true repeating alarms will be ignored also after this call
     */
    public static void stopMyService(boolean ignoreAlarms_in) {
        ignoreAlarms = ignoreAlarms_in;
        // Don't do this, because we may loose some information and (or) get Force Close
        // context.stopService(new Intent(IMyService.class.getName()));
        
        //This is "mild" stopping
        CommandData element = new CommandData(CommandEnum.STOP_SERVICE, "");
        MyPreferences.getContext().sendBroadcast(element.toIntent());
    }

    /**
     * Returns previous service state and queries service for its current state asynchronously.
     * Doesn't start the service, so absence of the reply will mean that service is stopped 
     * @See <a href="http://groups.google.com/group/android-developers/browse_thread/thread/8c4bd731681b8331/bf3ae8ef79cad75d">here</a>
     */
    public static ServiceState getServiceState() {
        synchronized(mServiceState) {
           long time = System.nanoTime();
           if ( waitingForServiceState && (time - stateQueuedTime) > (STATE_QUERY_TIMEOUT_SECONDS * 1000000000L) ) {
               // Timeout expired
               waitingForServiceState = false;
               mServiceState = ServiceState.STOPPED;
           } else if ( !waitingForServiceState && mServiceState == ServiceState.UNKNOWN ) {
               // State is unknown, we need to query the Service again
               waitingForServiceState = true;
               stateQueuedTime = time;
               mServiceState = ServiceState.UNKNOWN;
               CommandData element = new CommandData(CommandEnum.BROADCAST_SERVICE_STATE, "");
               MyPreferences.getContext().sendBroadcast(element.toIntent());
           }
        }
        
        return mServiceState;
    }
    
}
