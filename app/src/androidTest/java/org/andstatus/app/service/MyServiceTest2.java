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

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.TriState;
import org.junit.Test;

import java.util.Optional;

import static org.andstatus.app.context.DemoData.demoData;
import static org.andstatus.app.context.MyContextHolder.myContextHolder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MyServiceTest2 extends MyServiceTest {

    @Test
    public void testSyncInForeground() {
        final String method = "testSyncInForeground";
        MyLog.i(this, method + " started");
        SharedPreferencesUtil.putBoolean(MyPreferences.KEY_SYNC_WHILE_USING_APPLICATION, false);
        CommandData cd1Home = CommandData.newTimelineCommand(CommandEnum.GET_TIMELINE,
                demoData.getMyAccount(demoData.twitterTestAccountName),
                TimelineType.HOME);
        mService.setListenedCommand(cd1Home);

        long startCount = mService.executionStartCount;
        long endCount = mService.executionEndCount;

        MyLog.i(this, method + " Sending first command");
        mService.sendListenedCommand();
        mService.assertCommandExecutionStarted("First command should start", startCount, TriState.TRUE);
        assertTrue("First command should end execution", mService.waitForCommandExecutionEnded(endCount));
        assertEquals(cd1Home.toString() + " " + mService.getHttp().toString(),
                1, mService.getHttp().getRequestsCounter());

        assertTrue(TestSuite.setAndWaitForIsInForeground(true));
        MyLog.i(this, method + "; we are in a foreground");

        CommandData cd2Interactions = CommandData.newTimelineCommand(CommandEnum.GET_TIMELINE,
                demoData.getMyAccount(demoData.twitterTestAccountName),
                TimelineType.INTERACTIONS);
        mService.setListenedCommand(cd2Interactions);

        startCount = mService.executionStartCount;

        MyLog.i(this, method + " Sending second command");
        mService.sendListenedCommand();
        mService.assertCommandExecutionStarted("Second command shouldn't start", startCount, TriState.FALSE);
        MyLog.i(this, method + "; After waiting for the second command");
        assertTrue("Service should stop", mService.waitForServiceStopped(false));
        MyLog.i(this, method + "; Service stopped after the second command");

        assertEquals("No new data should be posted while in foreground",
                1, mService.getHttp().getRequestsCounter());

        CommandQueue queues1 = myContextHolder.getBlocking().queues();
        MyLog.i(this, method + "; Queues1:" + queues1);

        assertEquals("First command shouldn't be in any queue " + queues1,
                Optional.empty(), queues1.inWhichQueue(cd1Home).map(q -> q.queueType));
        assertEquals("Second command should be in the Skip queue " + queues1,
                Optional.of(QueueType.SKIPPED), queues1.inWhichQueue(cd2Interactions).map(q -> q.queueType));

        CommandData cd3PublicForeground = CommandData.newTimelineCommand(CommandEnum.GET_TIMELINE,
                demoData.getMyAccount(demoData.twitterTestAccountName),
                TimelineType.PUBLIC)
                .setInForeground(true);
        mService.setListenedCommand(cd3PublicForeground);

        startCount = mService.executionStartCount;
        endCount = mService.executionEndCount;

        MyLog.i(this, method + " Sending third command");
        mService.sendListenedCommand();

        mService.assertCommandExecutionStarted("Third (foreground) command", startCount, TriState.TRUE);
        assertTrue("Foreground command ended executing", mService.waitForCommandExecutionEnded(endCount));
        assertTrue("Service stopped", mService.waitForServiceStopped(false));

        CommandQueue queues2 = myContextHolder.getBlocking().queues();
        MyLog.i(this, method + "; Queues2:" + queues2);

        assertEquals("Third command shouldn't be in any queue " + queues2,
                Optional.empty(), queues2.inWhichQueue(cd3PublicForeground).map(q -> q.queueType));

        Optional<CommandQueue.OneQueue> cd2Queue = queues2.inWhichQueue(cd2Interactions);
        assertEquals("Second command should be in the Skip queue " + queues2,
                Optional.of(QueueType.SKIPPED), cd2Queue.map(q -> q.queueType));

        long idFound = -1;
        for (CommandData cd : cd2Queue.get().queue) {
            if (cd.equals(cd2Interactions)) {
                idFound = cd.getCommandId();
            }
        }
        assertEquals("command id", cd2Interactions.getCommandId(), idFound);
        assertTrue("command id=" + idFound, idFound >= 0);
        
        MyLog.i(this, method + " ended");

        myTestDeleteCommand(cd2Interactions);

        myContextHolder.getNow().queues().clear();
    }
    
    private void myTestDeleteCommand(CommandData commandIn) {
        MyLog.i(this, "myTestDeleteCommand started");
        assertTrue("Service stopped", mService.waitForServiceStopped(false));

        final CommandQueue cq1 = myContextHolder.getNow().queues();
        assertEquals("Didn't find input command in any queue", commandIn, cq1.getFromAnyQueue(commandIn));

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

        final CommandQueue cq2 = new CommandQueue(myContextHolder.getNow()).load();
        assertEquals("The DELETE command was not deleted from some queue: " + commandDelete,
                CommandData.EMPTY, cq2.getFromAnyQueue(commandDelete));
        assertEquals("The command was not deleted from some queue: " + commandIn,
                CommandData.EMPTY, cq2.getFromAnyQueue(commandIn));
        MyLog.i(this, "myTestDeleteCommand ended");
    }
}
