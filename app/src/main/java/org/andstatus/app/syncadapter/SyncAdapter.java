/*
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

import net.jcip.annotations.GuardedBy;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyServiceEvent;
import org.andstatus.app.service.MyServiceEventsListener;
import org.andstatus.app.service.MyServiceEventsReceiver;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.timeline.Timeline;
import org.andstatus.app.util.MyLog;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SyncAdapter extends AbstractThreadedSyncAdapter implements MyServiceEventsListener {

    private final Context mContext;
    private final Map<CommandData, Boolean> commands = new ConcurrentHashMap<>();
    private final Object syncLock = new Object();
    @GuardedBy("syncLock")
    private boolean mSyncCompleted = false;

    public SyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        this.mContext = context;
        MyLog.v(this, "created, context:" + context.getClass().getCanonicalName());
    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority,
            ContentProviderClient provider, SyncResult syncResult) {
        String method = "onPerformSync";
        if (!MyServiceManager.isServiceAvailable()) {
            syncResult.stats.numIoExceptions++;
            MyLog.d(this, method + "; Service not available, account:" + account.name);
            return;
        }
        MyContextHolder.initialize(mContext, this);
        if (!MyContextHolder.get().isReady()) {
            syncResult.stats.numIoExceptions++;
            MyLog.d(this, method + "; Context is not ready, account:" + account.name);
            return;
        }
        MyAccount ma = MyContextHolder.get().persistentAccounts().fromAccountName(account.name);
        if (!ma.isValid()) {
            syncResult.stats.numIoExceptions++;
            MyLog.d(this, method + "; The account was not loaded, account:" + account.name);
            return;      
        } else if (!ma.isValidAndSucceeded()) {
            syncResult.stats.numAuthExceptions++;
            MyLog.d(this, method + "; Credentials failed, skipping; account:" + account.name);
            return;
        }

        synchronized(syncLock) {
            mSyncCompleted = false;
        }
        MyServiceEventsReceiver intentReceiver = new MyServiceEventsReceiver(this);
        boolean interrupted = true;
        try {
            for (Timeline timeline : MyContextHolder.get().persistentTimelines()
                    .toSyncForAccount(MyContextHolder.get().persistentAccounts()
                            .fromAccountName(account.name))) {
                commands.put(CommandData.newTimelineCommand(CommandEnum.FETCH_TIMELINE, timeline),
                        false);
            }
            MyLog.v(this, method + " started, account:" + account.name + ", " + commands.size() + " timelines");
            intentReceiver.registerReceiver(mContext);
            for (CommandData commandData : commands.keySet()) {
                MyServiceManager.sendCommand(commandData);
            }
            final long numIterations = commands.size() * 3;
            synchronized(syncLock) {
                for (int iteration = 0; iteration < numIterations; iteration++) {
                    if (mSyncCompleted) {
                        break;
                    }
                    syncLock.wait(java.util.concurrent.TimeUnit.SECONDS.toMillis(
                            MyAsyncTask.MAX_COMMAND_EXECUTION_SECONDS / numIterations ));
                }
            }
            interrupted = false;
        } catch (InterruptedException e) {
            MyLog.d(this, method + "; Interrupted", e);
        } finally {
            synchronized(syncLock) {
                if (interrupted || !mSyncCompleted) {
                    syncResult.stats.numIoExceptions++;
                }
                for (CommandData commandData : commands.keySet()) {
                    syncResult.stats.numAuthExceptions += commandData.getResult().getNumAuthExceptions();
                    syncResult.stats.numIoExceptions += commandData.getResult().getNumIoExceptions();
                    syncResult.stats.numParseExceptions += commandData.getResult().getNumParseExceptions();
                }
            }
            intentReceiver.unregisterReceiver(mContext);            
        }
        MyLog.v(this, method + "; Ended, " 
                + (syncResult.hasError() ? "has error" : "ok"));
    }

    @Override
    public void onReceive(CommandData commandData, MyServiceEvent event) {
        if (event != MyServiceEvent.AFTER_EXECUTING_COMMAND) {
            return;
        }
        Boolean executed = commands.get(commandData);
        if (executed == null) {
            return;
        }
        MyLog.v(this, "Synced " + (executed == false ? "" : "again ") + commandData);
        synchronized (syncLock) {
            commands.put(commandData, true);
            if (!commands.containsValue(false)) {
                mSyncCompleted = true;
                syncLock.notifyAll();
            }
        }
    }
}
