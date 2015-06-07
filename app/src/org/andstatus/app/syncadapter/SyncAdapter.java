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

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.TimelineType;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyService;
import org.andstatus.app.service.MyServiceEvent;
import org.andstatus.app.service.MyServiceEventsListener;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.service.MyServiceEventsReceiver;
import org.andstatus.app.util.MyLog;

import net.jcip.annotations.GuardedBy;

public class SyncAdapter extends AbstractThreadedSyncAdapter implements MyServiceEventsListener {

    private final Context mContext;
    private volatile CommandData mCommandData;
    private final Object syncLock = new Object();
    @GuardedBy("syncLock")
    private boolean mSyncCompleted = false;
    @GuardedBy("syncLock")
    private long mNumAuthExceptions = 0;
    @GuardedBy("syncLock")
    private long mNumIoExceptions = 0;
    @GuardedBy("syncLock")
    private long mNumParseExceptions = 0;

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
        } else if (!ma.isValidAndVerified()) {
            syncResult.stats.numAuthExceptions++;
            MyLog.d(this, method + "; Credentials failed, skipping; account:" + account.name);
            return;
        }

        synchronized(syncLock) {
            mSyncCompleted = false;
            mNumAuthExceptions = 0;
            mNumIoExceptions = 0;
            mNumParseExceptions = 0;
        }
        MyServiceEventsReceiver intentReceiver = new MyServiceEventsReceiver(this);
        boolean interrupted = true;
        try {
            MyLog.v(this, method + "; Started, account:" + account.name);
            mCommandData = new CommandData(CommandEnum.AUTOMATIC_UPDATE, account.name,
                    TimelineType.ALL, 0);
            intentReceiver.registerReceiver(mContext);	
            MyServiceManager.sendCommand(mCommandData);
            final long numIterations = 10;
            synchronized(syncLock) {
                for (int iteration = 0; iteration < numIterations; iteration++) {
                    if (mSyncCompleted) {
                        break;
                    }
                    syncLock.wait(java.util.concurrent.TimeUnit.SECONDS.toMillis(
                            MyService.MAX_COMMAND_EXECUTION_SECONDS / numIterations ));
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
                syncResult.stats.numAuthExceptions += mNumAuthExceptions;
                syncResult.stats.numIoExceptions += mNumIoExceptions;
                syncResult.stats.numParseExceptions += mNumParseExceptions;
            }
            intentReceiver.unregisterReceiver(mContext);            
        }
        MyLog.v(this, method + "; Ended, " 
                + (syncResult.hasError() ? "has error" : "ok"));
    }

    @Override
    public void onReceive(CommandData commandData, MyServiceEvent event) {
        if (event != MyServiceEvent.AFTER_EXECUTING_COMMAND 
                || mCommandData == null || !mCommandData.equals(commandData) ) {
            return;
        }
        MyLog.v(this, "onReceive; command:" + commandData.getCommand());
        synchronized (syncLock) {
            mSyncCompleted = true;
            mNumAuthExceptions += commandData.getResult().getNumAuthExceptions();
            mNumIoExceptions += commandData.getResult().getNumIoExceptions();
            mNumParseExceptions += commandData.getResult().getNumParseExceptions();
            syncLock.notifyAll();
        }
    }
}
