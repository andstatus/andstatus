/*
* Copyright (C) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
* Based on the sample: com.example.android.samplesync.syncadapter
* Copyright (C) 2010 The Android Open Source Project
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

package org.andstatus.app.syncadapter;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.SyncResult;
import android.os.Bundle;

import org.andstatus.app.CommandData;
import org.andstatus.app.MyService;
import org.andstatus.app.MyServiceListener;
import org.andstatus.app.MyServiceReceiver;
import org.andstatus.app.MyService.CommandEnum;
import org.andstatus.app.MyServiceManager;
import org.andstatus.app.data.MyPreferences;
import org.andstatus.app.data.MyDatabase.TimelineTypeEnum;
import org.andstatus.app.util.MyLog;

import java.util.Timer;
import java.util.TimerTask;

/**
 * SyncAdapter implementation. Its only purpose for now is to properly initialize {@link MyService}.
 */
public class SyncAdapter extends AbstractThreadedSyncAdapter implements MyServiceListener {

    static final String TAG = SyncAdapter.class.getSimpleName();

    private final Context context;
    private volatile CommandData commandData;
    private volatile boolean syncCompleted = false;
    private volatile SyncResult syncResult;
    
    private MyServiceReceiver intentReceiver;
    
    public SyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        this.context = context;
        MyPreferences.initialize(context, this);
        MyLog.d(TAG, "created, context=" + context.getClass().getCanonicalName());
    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority,
            ContentProviderClient provider, SyncResult syncResult) {
        Timer timer = new Timer();
        intentReceiver = new MyServiceReceiver(this);
        syncCompleted = false;
        try {
            this.syncResult = syncResult;
            MyLog.d(TAG, "onPerformSync started, account=" + account.name
                    + (MyServiceManager.isServiceAvailable() ? "" : "; ignoring"));
            intentReceiver.registerReceiver(context);
            commandData = new CommandData(CommandEnum.AUTOMATIC_UPDATE, account.name,
                    TimelineTypeEnum.ALL, 0);
            MyServiceManager.sendCommand(commandData);
            synchronized (syncResult) {
                if (!syncCompleted) {
                    timer.schedule(
                        new TimerTask() {
                            @Override
                            public void run() {
                                synchronized (SyncAdapter.this.syncResult) {
                                    SyncAdapter.this.syncResult.stats.numIoExceptions++;
                                    MyLog.d(TAG, "onPerformSync timeout");
                                    SyncAdapter.this.syncResult.notifyAll();
                                }
                            }
                        }, 180 * MyPreferences.MILLISECONDS);
                    syncResult.wait();
                }
            }
            MyLog.d(TAG, "onPerformSync ended, " + (syncResult.hasError() ? "has error" : "ok"));
        } catch (InterruptedException e) {
            MyLog.d(TAG, "onPerformSync interrupted");
        } finally {
            timer.cancel();
            intentReceiver.unregisterReceiver(context);            
        }
    }

    @Override
    public void onReceive(CommandData commandData) {
        MyLog.d(TAG, "onReceive, command=" + commandData.command);
        synchronized (syncResult) {
            if (this.commandData == null || this.commandData.equals(commandData)) {
                syncCompleted = true;
                syncResult.stats.numAuthExceptions += commandData.commandResult.numAuthExceptions;
                syncResult.stats.numIoExceptions += commandData.commandResult.numIoExceptions;
                syncResult.stats.numParseExceptions += commandData.commandResult.numParseExceptions;
                syncResult.notifyAll();
            }
        }
    }
}
