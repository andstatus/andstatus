/*
 * Copyright (C) 2014-2018 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.support.annotation.NonNull;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.TriState;
import org.junit.Test;

import java.util.Queue;

import static org.andstatus.app.context.DemoData.demoData;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MyServiceTest2 extends MyServiceTest {

    @Test
    public void testSyncInForeground() throws InterruptedException {
        final String method = "testSyncInForeground";
        MyLog.i(this, method + " started");
        SharedPreferencesUtil.putBoolean(MyPreferences.KEY_SYNC_WHILE_USING_APPLICATION, false);
        CommandData cd1 = CommandData.newTimelineCommand(CommandEnum.GET_TIMELINE,
                demoData.getMyAccount(demoData.twitterTestAccountName),
                TimelineType.PRIVATE);
        mService.setListenedCommand(cd1);

        long startCount = mService.executionStartCount;
        long endCount = mService.executionEndCount;

        mService.sendListenedCommand();
        mService.assertCommandExecutionStarted("First command didn't start", startCount, TriState.TRUE);
        assertTrue("First command didn't end executing", mService.waitForCommandExecutionEnded(endCount));
        assertEquals(cd1.toString() + " " + mService.getHttp().toString(),
                1, mService.getHttp().getRequestsCounter());

        assertTrue(TestSuite.setAndWaitForIsInForeground(true));
        MyLog.i(this, method + "; we are in a foreground");

        CommandData cd2 = CommandData.newTimelineCommand(CommandEnum.GET_TIMELINE,
                demoData.getMyAccount(demoData.twitterTestAccountName),
                TimelineType.INTERACTIONS);
        mService.setListenedCommand(cd2);

        startCount = mService.executionStartCount;
        mService.sendListenedCommand();
        mService.assertCommandExecutionStarted("Second command started execution", startCount, TriState.FALSE);
        MyLog.i(this, method + "; After waiting for the second command");
        assertTrue("Service stopped", mService.waitForServiceStopped(false));
        MyLog.i(this, method + "; Service stopped after the second command");
        assertEquals("No new data was posted while in foreground",
                1, mService.getHttp().getRequestsCounter());

        Queue<CommandData> queue = new CommandQueue().load().get(QueueType.CURRENT);
        MyLog.i(this, method + "; Queue loaded, size:" + queue.size());
        assertFalse("Main queue is empty", queue.isEmpty());
        assertFalse("First command is in the main queue", queue.contains(cd1));
        assertTrue("The second command is not in the main queue", queue.contains(cd2));

        CommandData cd3 = CommandData.newTimelineCommand(CommandEnum.GET_TIMELINE,
                demoData.getMyAccount(demoData.twitterTestAccountName),
                TimelineType.HOME)
                .setInForeground(true);
        mService.setListenedCommand(cd3);

        startCount = mService.executionStartCount;
        endCount = mService.executionEndCount;
        mService.sendListenedCommand();

        mService.assertCommandExecutionStarted("Foreground command", startCount, TriState.TRUE);
        assertTrue("Foreground command ended executing", mService.waitForCommandExecutionEnded(endCount));
        assertTrue("Service stopped", mService.waitForServiceStopped(false));

        queue = new CommandQueue().load().get(QueueType.CURRENT);
        assertFalse("Main queue is not empty", queue.isEmpty());
        assertTrue("The second command stayed in the main queue", queue.contains(cd2));
        
        long idFound = -1;
        for (CommandData cd : queue) {
            if (cd.equals(cd2)) {
                idFound = cd.getCommandId();
            }
        }
        assertEquals("command id", cd2.getCommandId(), idFound);
        assertTrue("command id=" + idFound, idFound >= 0);
        
        assertFalse("Foreground command is not in main queue", queue.contains(cd3));
        MyLog.i(this, method + " ended");

        myTestDeleteCommand(cd2);

        new CommandQueue().clear();
    }
    
    private void myTestDeleteCommand(CommandData commandIn) {
        MyLog.i(this, "myTestDeleteCommand started");
        assertTrue("Service stopped", mService.waitForServiceStopped(false));

        final CommandQueue cq1 = new CommandQueue().load();
        assertEquals("Didn't find input command in any queue", commandIn, getFromAnyQueue(cq1, commandIn));

        CommandData commandDelete = CommandData.newItemCommand(
                CommandEnum.DELETE_COMMAND,
                MyAccount.EMPTY,
                commandIn.getCommandId());
        commandDelete.setInForeground(true);
        mService.setListenedCommand(commandDelete);
        assertEquals(commandIn.getCommandId(), mService.getListenedCommand().itemId);
        
        long endCount = mService.executionEndCount;

        mService.sendListenedCommand();
        assertTrue("Delete command ended executing", mService.waitForCommandExecutionEnded(endCount));
        assertTrue("Service stopped", mService.waitForServiceStopped(false));

        final CommandQueue cq2 = new CommandQueue().load();
        assertEquals("The DELETE command was not deleted from some queue: " + commandDelete,
                CommandData.EMPTY, getFromAnyQueue(cq2, commandDelete));
        assertEquals("The command was not deleted from some queue: " + commandIn,
                CommandData.EMPTY, getFromAnyQueue(cq2, commandIn));
        MyLog.i(this, "myTestDeleteCommand ended");
    }

    @NonNull
    private static CommandData getFromAnyQueue(CommandQueue commandQueue, CommandData dataIn) {
        for (QueueType queueType : QueueType.values()) {
            CommandData dataOut = getFromQueue(commandQueue, queueType, dataIn);
            if (dataOut != CommandData.EMPTY) return dataOut;
        }
        return CommandData.EMPTY;
    }

    @NonNull
    static CommandData getFromQueue(CommandQueue commandQueue, QueueType queueType, CommandData dataIn) {
        Queue queue = commandQueue.get(queueType);
        if (queue == null) return CommandData.EMPTY;
        for (Object data : queue) {
            if (dataIn.equals(data)) return (CommandData) data;
        }
        return CommandData.EMPTY;
    }
}
