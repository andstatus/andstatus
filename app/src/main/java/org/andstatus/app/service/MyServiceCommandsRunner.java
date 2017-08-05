/*
 * Copyright (c) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.content.SyncResult;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.timeline.Timeline;
import org.andstatus.app.util.MyLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MyServiceCommandsRunner implements MyServiceEventsListener {

    private final MyContext myContext;
    private boolean syncStarted = false;
    private final Map<CommandData, Boolean> commands = new ConcurrentHashMap<>();
    private final Object syncLock = new Object();
    private final MyServiceEventsReceiver eventsReceiver;
    private boolean ignoreServiceAvailability = false;

    public MyServiceCommandsRunner(MyContext myContext) {
        this.myContext = myContext;
        eventsReceiver = new MyServiceEventsReceiver(myContext, this);
    }

    public void autoSyncAccount(String accountName, SyncResult syncResult) {
        final String method = "syncAccount " + accountName;
        syncStarted = false;
        if (!myContext.isReady()) {
            syncResult.stats.numIoExceptions++;
            MyLog.d(this, method + "; Context is not ready");
            return;
        }
        MyAccount ma = myContext.persistentAccounts().fromAccountName(accountName);
        if (!ma.isValid()) {
            syncResult.stats.numIoExceptions++;
            MyLog.d(this, method + "; The account was not loaded");
            return;      
        } else if (!ma.isValidAndSucceeded()) {
            syncResult.stats.numAuthExceptions++;
            MyLog.d(this, method + "; Credentials failed, skipping");
            return;
        }

        List<CommandData> commandsOnly = new ArrayList<>();
        for (Timeline timeline : myContext.persistentTimelines()
                .toAutoSyncForAccount(myContext.persistentAccounts()
                        .fromAccountName(accountName))) {
            commandsOnly.add(CommandData.newTimelineCommand(CommandEnum.GET_TIMELINE, timeline));
        }
        MyLog.v(this, method + " started, " + commandsOnly.size() + " timelines");

        sendCommands(commandsOnly);

        waitForCompletion(method, myContext);

        unregisterReceiver();

        synchronized(syncLock) {
            if (!isSyncCompleted()) {
                syncResult.stats.numIoExceptions++;
            }
            for (CommandData commandData : commands.keySet()) {
                syncResult.stats.numAuthExceptions += commandData.getResult().getNumAuthExceptions();
                syncResult.stats.numIoExceptions += commandData.getResult().getNumIoExceptions();
                syncResult.stats.numParseExceptions += commandData.getResult().getNumParseExceptions();
            }
        }

        MyLog.v(this, method + " ended, " + (syncResult.hasError() ? "has error" : "ok"));
    }

    private void sendCommands(List<CommandData> commandsOnly) {
        synchronized(syncLock) {
            syncStarted = true;
            commands.clear();
            for (CommandData commandData : commandsOnly) {
                commands.put(commandData, false);
            }
        }
        eventsReceiver.registerReceiver(myContext.context());
        for (CommandData commandData : commands.keySet()) {
            if (ignoreServiceAvailability) {
                MyServiceManager.sendCommandEvenForUnavailable(commandData);
            } else {
                MyServiceManager.sendCommand(commandData);
            }
        }
    }

    private void waitForCompletion(String method, MyContext myContext) {
        try {
            final long numIterations = commands.size() * 3L;
            synchronized(syncLock) {
                for (int iteration = 0; iteration < numIterations; iteration++) {
                    if (isSyncCompleted()) {
                        break;
                    }
                    if (!myContext.isReady()) {
                        MyLog.d(this, method + "; MyContext is not ready: " + myContext);
                        break;
                    }
                    syncLock.wait(java.util.concurrent.TimeUnit.SECONDS.toMillis(
                            MyAsyncTask.MAX_COMMAND_EXECUTION_SECONDS / numIterations ));
                }
            }
        } catch (InterruptedException e) {
            MyLog.d(this, method + "; Interrupted", e);
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void onReceive(CommandData commandData, MyServiceEvent event) {
        if (event != MyServiceEvent.AFTER_EXECUTING_COMMAND) {
            return;
        }
        Boolean executed = commands.get(commandData);
        if (executed == null) {
            return;  // Was not sent for execution
        }
        MyLog.v(this, "Synced " + (executed ? "again " : "") + commandData);
        synchronized (syncLock) {
            commands.put(commandData, true);
            if (isSyncCompleted()) {
                syncLock.notifyAll();
            }
        }
    }

    boolean isSyncCompleted() {
        return syncStarted && !commands.containsValue(false);
    }

    private void unregisterReceiver() {
        eventsReceiver.unregisterReceiver(myContext.context());
    }

    private int getCompletedCount() {
        int count = 0;
        for (Map.Entry<CommandData, Boolean> entry : commands.entrySet()) {
            if (entry.getValue()) {
                count++;
            }
        }
        return count;
    }

    @Override
    public String toString() {
        return "MyServiceCommandsRunner{" +
                "commands:" + commands.size() +
                ", completed:" + getCompletedCount() +
                '}';
    }

    void setIgnoreServiceAvailability(boolean ignoreServiceAvailability) {
        this.ignoreServiceAvailability = ignoreServiceAvailability;
    }
}
