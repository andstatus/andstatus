/* 
 * Copyright (c) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.andstatus.app.IntentExtra;
import org.andstatus.app.MyAction;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.syncadapter.SyncInitiator;
import org.andstatus.app.util.IdentifiableInstance;
import org.andstatus.app.util.InstanceId;
import org.andstatus.app.util.MyLog;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.andstatus.app.context.MyContextHolder.myContextHolder;

/**
 * This receiver starts and stops {@link MyService} and also queries its state.
 * Android system creates new instance of this type on each Intent received. 
 * This is why we're keeping a state in static fields. 
 */
public class MyServiceManager extends BroadcastReceiver implements IdentifiableInstance {
    private static final String TAG = MyServiceManager.class.getSimpleName();

    private final long instanceId = InstanceId.next();

    public MyServiceManager() {
        MyLog.v(this, () -> "Created, instanceId=" + instanceId );
    }

    @Override
    public long getInstanceId() {
        return instanceId;
    }

    private static class MyServiceStateInTime {
        /** Is the service started.
         * @See <a href="http://groups.google.com/group/android-developers/browse_thread/thread/8c4bd731681b8331/bf3ae8ef79cad75d">here</a>
         */
        private volatile MyServiceState stateEnum = MyServiceState.UNKNOWN;
        /**
         * {@link System#nanoTime()} when the state was queued or received last time ( 0 - never )
         */
        private volatile long stateQueuedTime = 0;
        /** If true, we sent state request and are waiting for reply from {@link MyService} */
        private volatile boolean isWaiting = false;

        public static MyServiceStateInTime getUnknown() {
            return new MyServiceStateInTime();
        }

        public static MyServiceStateInTime fromIntent(Intent intent) {
            MyServiceStateInTime state = new MyServiceStateInTime();
            state.stateQueuedTime = System.nanoTime();
            state.stateEnum = MyServiceState.load(intent.getStringExtra(IntentExtra.SERVICE_STATE.key));
            return state;
        }

    }
    private static volatile MyServiceStateInTime stateInTime = MyServiceStateInTime.getUnknown();

    /**
     * How long are we waiting for {@link MyService} response before deciding that the service is stopped
     */
    private static final int STATE_QUERY_TIMEOUT_SECONDS = 3;

    @Override
    public void onReceive(Context context, Intent intent) {
        switch (MyAction.fromIntent(intent)) {
            case BOOT_COMPLETED:
                MyLog.d(this, "Trying to start service on boot");
                sendCommand(CommandData.EMPTY);
                break;
            case SYNC:
                SyncInitiator.tryToSync(context);
                break;
            case SERVICE_STATE:
                if (isServiceAvailable()) {
                    myContextHolder.initialize(context, this);
                }
                stateInTime = MyServiceStateInTime.fromIntent(intent);
                MyLog.d(this, "Notification received: Service state=" + stateInTime.stateEnum);
                break;
            case ACTION_SHUTDOWN:
                setServiceUnavailable();
                MyLog.d(this, "Stopping service on Shutdown");
                myContextHolder.onShutDown();
                stopService();
                break;
            default:
                break;
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
            MyServiceEventsBroadcaster.newInstance(myContextHolder.getNow(), MyServiceState.STOPPED)
            .setCommandData(commandData).setEvent(MyServiceEvent.AFTER_EXECUTING_COMMAND).broadcast();
            return;
        }
        sendCommandIgnoringServiceAvailability(commandData);
    }

    public static void sendManualForegroundCommand(CommandData commandData) {
        sendForegroundCommand(commandData.setManuallyLaunched(true));
    }

    public static void sendForegroundCommand(CommandData commandData) {
        sendCommand(commandData.setInForeground(true));
    }

    static void sendCommandIgnoringServiceAvailability(CommandData commandData) {
        // Using explicit Service intent,
        // see http://stackoverflow.com/questions/18924640/starting-android-service-using-explicit-vs-implicit-intent
        Intent serviceIntent = new Intent(myContextHolder.getNow().context(), MyService.class);
        switch (commandData.getCommand()) {
            case STOP_SERVICE:
            case BROADCAST_SERVICE_STATE:
                serviceIntent = commandData.toIntent(serviceIntent);
                break;
            case UNKNOWN:
                break;
            default:
                CommandQueue.addToPreQueue(commandData);
                break;
        }

        try {
            myContextHolder.getNow().context().startService(serviceIntent);
        } catch (IllegalStateException e) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    // Since Android Oreo TODO: https://developer.android.com/about/versions/oreo/android-8.0-changes.html#back-all
                    // See also https://github.com/evernote/android-job/issues/254
                    myContextHolder.getNow().context().startForegroundService(serviceIntent);
                } catch (IllegalStateException e2) {
                    MyLog.e(TAG, "Failed to start MyService in the foreground", e2);
                }
            } else {
                MyLog.e(TAG, "Failed to start MyService", e);
            }
        } catch (NullPointerException e) {
            MyLog.e(TAG, "Failed to start MyService", e);
        }
    }

    /**
     * Stop  {@link MyService} asynchronously
     */
    public static synchronized void stopService() {
        if ( !myContextHolder.getNow().isReady() ) {
            return;
        }
        // Don't do "context.stopService", because we may lose some information and (or) get Force Close
        // This is "mild" stopping
        myContextHolder.getNow().context()
                .sendBroadcast(CommandData.newCommand(CommandEnum.STOP_SERVICE)
                        .toIntent(MyAction.EXECUTE_COMMAND.getIntent()));
    }

    /**
     * Returns previous service state and queries service for its current state asynchronously.
     * Doesn't start the service, so absence of the reply will mean that service is stopped 
     * @See <a href="http://groups.google.com/group/android-developers/browse_thread/thread/8c4bd731681b8331/bf3ae8ef79cad75d">here</a>
     */
    public static MyServiceState getServiceState() {
        long time = System.nanoTime();
        MyServiceStateInTime state = stateInTime;
        if ( state.isWaiting && (time - state.stateQueuedTime) > java.util.concurrent.TimeUnit.SECONDS.toMillis(STATE_QUERY_TIMEOUT_SECONDS)) {
            // Timeout expired
            state = new MyServiceStateInTime();
            state.stateEnum = MyServiceState.STOPPED;
            stateInTime = state;
        } else if ( !state.isWaiting && state.stateEnum == MyServiceState.UNKNOWN ) {
            // State is unknown, we need to query the Service again
            state = new MyServiceStateInTime();
            state.stateEnum = MyServiceState.UNKNOWN;
            state.isWaiting = true;
            state.stateQueuedTime = time;
            stateInTime = state;
            myContextHolder.getNow().context()
                    .sendBroadcast(CommandData.newCommand(CommandEnum.BROADCAST_SERVICE_STATE)
                            .toIntent(MyAction.EXECUTE_COMMAND.getIntent()));
        }
        return state.stateEnum;
    }

    private static AtomicReference<ServiceAvailability> serviceAvailability
            = new AtomicReference<>(ServiceAvailability.AVAILABLE);

    private static class ServiceAvailability {
        static final ServiceAvailability AVAILABLE = new ServiceAvailability(0);
        private final long timeWhenServiceWillBeAvailable;

        private ServiceAvailability(long timeWhenServiceWillBeAvailable) {
            this.timeWhenServiceWillBeAvailable = timeWhenServiceWillBeAvailable;
        }

        boolean isAvailable() {
            return willBeAvailableInMillis() == 0;
        }

        long willBeAvailableInMillis() {
            return timeWhenServiceWillBeAvailable == 0
                ? 0
                : Math.max(timeWhenServiceWillBeAvailable - System.currentTimeMillis(), 0);
        }

        static ServiceAvailability newUnavailable() {
            return new ServiceAvailability(System.currentTimeMillis() +
                    TimeUnit.MINUTES.toMillis(15));
        }

        ServiceAvailability checkAndGetNew() {
            return isAvailable()
                ? AVAILABLE
                : newUnavailable();
        }
    }


    public static boolean isServiceAvailable() {
        boolean isAvailable = myContextHolder.getNow().isReady();
        if (!isAvailable) {
            boolean tryToInitialize = serviceAvailability.get().isAvailable();
            if (tryToInitialize
                    && MyAsyncTask.nonUiThread()    // Don't block on UI thread
                    && !myContextHolder.getNow().initialized()) {
                myContextHolder.initialize(null, TAG).getBlocking();
                isAvailable = myContextHolder.getNow().isReady();
            }
        }
        if (isAvailable) {
            long availableInMillis = serviceAvailability.updateAndGet(ServiceAvailability::checkAndGetNew)
                    .willBeAvailableInMillis();
            isAvailable = availableInMillis == 0;
            if (!isAvailable && MyLog.isVerboseEnabled()) {
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
        serviceAvailability.set(ServiceAvailability.AVAILABLE);
    }
    public static void setServiceUnavailable() {
        serviceAvailability.set(ServiceAvailability.newUnavailable());
    }
}
