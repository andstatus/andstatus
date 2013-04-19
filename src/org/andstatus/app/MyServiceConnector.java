/* 
 * Copyright (c) 2012 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import org.andstatus.app.MyService.CommandData;
import org.andstatus.app.MyService.CommandEnum;
import org.andstatus.app.util.MyLog;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Encapsulates interaction with {@link MyService}
 * 
 * @author yvolk
 */
public class MyServiceConnector {
    private static final String TAG = MyServiceConnector.class.getSimpleName();

    // Handler message codes
    public static final int MSG_TWEETS_CHANGED = 1;
    public static final int MSG_DATA_LOADING = 2;
    
    /**
     * These two codes are not used anywhere.
     */
    private static final int MSG_DIRECT_MESSAGES_CHANGED = 7;
    private static final int MSG_REPLIES_CHANGED = 9;

    public static final int MSG_UPDATED_TITLE = 10;

    private int instanceId;

    /**
     * Activity to which the {@link MyServiceConnector} is attached
     */
    protected Activity mActivity;
    /**
     * Handler of the {@link #mActivity}
     */
    protected Handler mHandler;
    
    /**
     * Is connected to the application service?
     */
    protected boolean mIsBound;

    /**
     * See {@link #mServiceCallback} also
     */
    protected IMyService mService;

    public MyServiceConnector(int instanceId) {
        this.instanceId = instanceId;
    }

    /**
     * Initialize service and bind to it.
     */
    public void bindToService(Activity activity, Handler handler) {
        if (!mIsBound) {
            mIsBound = true;
            mActivity = activity;
            mHandler = handler;
            Intent serviceIntent = new Intent(IMyService.class.getName());
            if (MyServiceManager.getServiceState() != MyService.ServiceState.RUNNING) {
                // Ensure that MyService is running
                MyServiceManager.startMyService(new CommandData(CommandEnum.EMPTY, ""));
            }
            // startService(serviceIntent);
            mActivity.bindService(serviceIntent, mServiceConnection, 0);
        }
    }

    /**
     * Disconnect and unregister the service.
     */
    protected void disconnectService() {
        if (mIsBound) {
            if (mService != null) {
                try {
                    mService.unregisterCallback(mServiceCallback);
                } catch (RemoteException e) {
                    // Service crashed, not much we can do.
                }
                mService = null;
            }
            mActivity.unbindService(mServiceConnection);
            // MyServiceManager.stopAndStatusService(this);
            
            // and release references
            mActivity = null;
            mHandler = null;
            mIsBound = false;
        }
    }

    /**
     * Service connection handler.
     */
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "onServiceConnected");
            }
            mService = IMyService.Stub.asInterface(service);
            // We want to monitor the service for as long as we are
            // connected to it.
            try {
                mService.registerCallback(mServiceCallback);
                // Push the queue
                sendCommand(null);
            } catch (RemoteException e) {
                // Service has already crashed, nothing much we can do
                // except hope that it will restart.
                mService = null;
            }
        }

        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        }
    };

    /**
     * Intents queue to be send to the MyService
     */
    private BlockingQueue<CommandData> mCommands = new ArrayBlockingQueue<CommandData>(100,
            true);

    /**
     * Send broadcast with the command (in the form of Intent) to the
     * AndStatus Service after it will be connected to this activity. We
     * should wait for the connection because otherwise we won't receive
     * callback from the service Plus this method restarts this Activity if
     * command is PUT_BOOLEAN_PREFERENCE with KEY_PREFERENCES_CHANGE_TIME
     * 
     * @param commandData Intent to send, null if we only want to push the
     *            queue
     */
    public synchronized void sendCommand(CommandData commandData) {
        if (commandData != null) {
            if (!mCommands.contains(commandData)) {
                if (!mCommands.offer(commandData)) {
                    Log.e(TAG, "mCommands is full?");
                }
            }
        }
        if (mService != null) {
            // Service is connected, so we can send queued Intents
            if (mCommands.size() > 0) {
                if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
                    Log.v(TAG, "Sendings " + mCommands.size() + " commands to MyService");
                }
                while (true) {
                    MyService.CommandData element = mCommands.poll();
                    if (element == null) {
                        break;
                    }
                    mActivity.sendBroadcast(element.toIntent());
                }
            }
        }
    }

    /**
     * Service callback handler.
     */
    protected IMyServiceCallback mServiceCallback = new IMyServiceCallback.Stub() {
        /**
         * Msg changed callback method
         * 
         * @param value
         * @throws RemoteException
         */
        public void tweetsChanged(int value) throws RemoteException {
            if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "tweetsChanged value=" + value);
            }
            mHandler.sendMessage(mHandler.obtainMessage(MSG_TWEETS_CHANGED, value, 0));
        }

        /**
         * dataLoading callback method.
         * 
         * @param value
         * @throws RemoteException
         */
        public void dataLoading(int value) throws RemoteException {
            if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "dataLoading value=" + value + ", instanceId=" + instanceId);
            }
            mHandler.sendMessage(mHandler.obtainMessage(MSG_DATA_LOADING, value, 0));
        }

        /**
         * Messages changed callback method
         * 
         * @param value
         * @throws RemoteException
         */
        public void messagesChanged(int value) throws RemoteException {
            mHandler.sendMessage(mHandler.obtainMessage(MSG_DIRECT_MESSAGES_CHANGED, value, 0));
        }

        /**
         * Replies changed callback method
         * 
         * @param value
         * @throws RemoteException
         */
        public void repliesChanged(int value) throws RemoteException {
            mHandler.sendMessage(mHandler.obtainMessage(MSG_REPLIES_CHANGED, value, 0));
        }

        public void rateLimitStatus(int remaining_hits, int hourly_limit)
                throws RemoteException {
            mHandler.sendMessage(mHandler.obtainMessage(MSG_UPDATED_TITLE, remaining_hits,
                    hourly_limit));
        }
    };

}

