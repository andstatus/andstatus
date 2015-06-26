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
package org.andstatus.app.service;

import org.andstatus.app.IntentExtra;
import org.andstatus.app.MyAction;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.util.InstanceId;
import org.andstatus.app.util.MyLog;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import net.jcip.annotations.GuardedBy;

/**
 * This receiver starts and stops {@link MyService} and also queries its state.
 * Android system creates new instance of this type on each Intent received. 
 * This is why we're keeping a state in static fields. 
 */
public class MyServiceManager extends BroadcastReceiver {
    private static final String TAG = MyServiceManager.class.getSimpleName();

    private final long instanceId = InstanceId.next();

    public MyServiceManager() {
        MyLog.v(this, "Created, instanceId=" + instanceId );
    }

    /**
     * Is the service started.
     * @See <a href="http://groups.google.com/group/android-developers/browse_thread/thread/8c4bd731681b8331/bf3ae8ef79cad75d">here</a>
     */
    private static volatile MyServiceState mServiceState = MyServiceState.UNKNOWN;
    /**
     * {@link System#nanoTime()} when the state was queued or received last time ( 0 - never )  
     */
    private static volatile long stateQueuedTime = 0;
    /**
     * If true, we sent state request and are waiting for reply from {@link MyService} 
     */
    private static volatile boolean waitingForServiceState = false;
    /**
     * How long are we waiting for {@link MyService} response before deciding that the service is stopped
     */
    private static final int STATE_QUERY_TIMEOUT_SECONDS = 3;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action.equals(MyAction.SERVICE_STATE.getAction())) {
            MyContextHolder.initialize(context, this);
            synchronized (mServiceState) {
                stateQueuedTime = System.nanoTime();
                waitingForServiceState = false;
                mServiceState = MyServiceState.load(intent
                        .getStringExtra(IntentExtra.SERVICE_STATE.key));
            }
            MyLog.d(this, "Notification received: Service state=" + mServiceState);
        } else if ("android.intent.action.BOOT_COMPLETED".equals(action)) {
            MyLog.d(this, "Trying to start service on boot");
            sendCommand(CommandData.getEmpty());            
        } else if ("android.intent.action.ACTION_SHUTDOWN".equals(action)) {
            // We need this to persist unsaved data in the service
            MyLog.d(this, "Stopping service on Shutdown");
            setServiceUnavailable();
            stopService();
        }
    }

    /**
     * Starts MyService  asynchronously if it is not already started
     * and send command to it.
     * 
     * @param commandData to the service or null 
     */
    public static void sendCommand(CommandData commandData) {
        if (!isServiceAvailable()) {
            // Imitate a soft service error
            commandData.getResult().incrementNumIoExceptions();
            commandData.getResult().setMessage("Service is not available");
            MyServiceEventsBroadcaster.newInstance(MyContextHolder.get(), MyServiceState.STOPPED)
            .setCommandData(commandData).setEvent(MyServiceEvent.AFTER_EXECUTING_COMMAND).broadcast();
            return;
        }
        sendCommandEvenForUnavailable(commandData);
    }

    public static void sendManualForegroundCommand(CommandData commandData) {
        sendForegroundCommand(commandData.setManuallyLaunched(true));
    }

    public static void sendForegroundCommand(CommandData commandData) {
        sendCommand(commandData.setInForeground(true));
    }

    static void sendCommandEvenForUnavailable(CommandData commandData) {
        // Using explicit Service intent, 
        // see http://stackoverflow.com/questions/18924640/starting-android-service-using-explicit-vs-implicit-intent
        Intent serviceIntent = new Intent(MyContextHolder.get().context(), MyService.class);
        if (commandData != null) {
            serviceIntent = commandData.toIntent(serviceIntent);
        }
        MyContextHolder.get().context().startService(serviceIntent);
    }

    /**
     * Stop  {@link MyService} asynchronously
     */
    public static synchronized void stopService() {
        if ( !MyContextHolder.get().isReady() ) {
            return;
        }
        // Don't do "context.stopService", because we may lose some information and (or) get Force Close
        // This is "mild" stopping
        CommandData element = new CommandData(CommandEnum.STOP_SERVICE, "");
        MyContextHolder.get().context()
                .sendBroadcast(element.toIntent(MyAction.EXECUTE_COMMAND.getIntent()));
    }

    /**
     * Returns previous service state and queries service for its current state asynchronously.
     * Doesn't start the service, so absence of the reply will mean that service is stopped 
     * @See <a href="http://groups.google.com/group/android-developers/browse_thread/thread/8c4bd731681b8331/bf3ae8ef79cad75d">here</a>
     */
    public static MyServiceState getServiceState() {
        synchronized(mServiceState) {
            long time = System.nanoTime();
            if ( waitingForServiceState && (time - stateQueuedTime) > java.util.concurrent.TimeUnit.SECONDS.toMillis(STATE_QUERY_TIMEOUT_SECONDS)) {
                // Timeout expired
                waitingForServiceState = false;
                mServiceState = MyServiceState.STOPPED;
            } else if ( !waitingForServiceState && mServiceState == MyServiceState.UNKNOWN ) {
                // State is unknown, we need to query the Service again
                waitingForServiceState = true;
                stateQueuedTime = time;
                mServiceState = MyServiceState.UNKNOWN;
                CommandData element = new CommandData(CommandEnum.BROADCAST_SERVICE_STATE, "");
                MyContextHolder.get().context()
                        .sendBroadcast(element.toIntent(MyAction.EXECUTE_COMMAND.getIntent()));
            }
        }
        return mServiceState;
    }

    private static Object serviceAvailableLock = new Object();
    @GuardedBy("serviceAvailableLock")
    private static Boolean mServiceAvailable = true;
    @GuardedBy("serviceAvailableLock")
    private static long timeWhenTheServiceWillBeAvailable = 0;

    public static boolean isServiceAvailable() {
        boolean isAvailable = MyContextHolder.get().isReady();
        if (!isAvailable) {
            boolean tryToInitialize = false;
            synchronized (serviceAvailableLock) {
                tryToInitialize = mServiceAvailable;
            }
            if (tryToInitialize && !MyContextHolder.get().initialized()) {
                MyContextHolder.initialize(null, TAG);
                isAvailable = MyContextHolder.get().isReady();
            }
        }
        if (isAvailable) {
            long availableInMillis = 0; 
            synchronized (serviceAvailableLock) {
                availableInMillis = timeWhenTheServiceWillBeAvailable - System.currentTimeMillis();
                if (!mServiceAvailable && availableInMillis <= 0) {
                    setServiceAvailable();
                }
                isAvailable = mServiceAvailable;
            }
            if (!isAvailable) {
                MyLog.v(TAG,"Service will be available in " 
                        + java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(availableInMillis) 
                        + " seconds");
            }
        } else {
            MyLog.v(TAG,"Service is unavailable: Context is not Ready");
        }
        return isAvailable;
    }
    public static void setServiceAvailable() {
        synchronized (serviceAvailableLock) {
            mServiceAvailable = true;
            timeWhenTheServiceWillBeAvailable = 0;
        }
    }
    public static void setServiceUnavailable() {
        synchronized (serviceAvailableLock) {
            mServiceAvailable = false;
            timeWhenTheServiceWillBeAvailable = System.currentTimeMillis() + 
                    java.util.concurrent.TimeUnit.SECONDS.toMillis(15 * 60);
        }
    }
}
