/*
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.context.DemoData;
import org.andstatus.app.timeline.TimelineType;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.junit.Test;

import java.util.Queue;

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
                DemoData.getMyAccount(DemoData.TWITTER_TEST_ACCOUNT_NAME),
                TimelineType.DIRECT);
        mService.setListenedCommand(cd1);

        long startCount = mService.executionStartCount;
        long endCount = mService.executionEndCount;

        mService.sendListenedToCommand();
        assertTrue("First command didn't start executing", mService.waitForCommandExecutionStarted(startCount));
        assertTrue("First command didn't end executing", mService.waitForCommandExecutionEnded(endCount));
        assertEquals(cd1.toString() + " " + mService.httpConnectionMock.toString(),
                1, mService.httpConnectionMock.getRequestsCounter());

        assertTrue(TestSuite.setAndWaitForIsInForeground(true));
        MyLog.i(this, method + "; we are in a foreground");

        CommandData cd2 = CommandData.newTimelineCommand(CommandEnum.GET_TIMELINE,
                DemoData.getMyAccount(DemoData.TWITTER_TEST_ACCOUNT_NAME),
                TimelineType.MENTIONS);
        mService.setListenedCommand(cd2);

        startCount = mService.executionStartCount;
        mService.sendListenedToCommand();
        assertFalse("Second command started execution", mService.waitForCommandExecutionStarted(startCount));
        MyLog.i(this, method + "; After waiting for the second command");
        assertTrue("Service stopped", mService.waitForServiceStopped(false));
        MyLog.i(this, method + "; Service stopped after the second command");
        assertEquals("No new data was posted while in foreground",
                mService.httpConnectionMock.getRequestsCounter(), 1);

        Queue<CommandData> queue = new CommandQueue().load().get(QueueType.CURRENT);
        MyLog.i(this, method + "; Queue loaded, size:" + queue.size());
        assertFalse("Main queue is empty", queue.isEmpty());
        assertFalse("First command is in the main queue", queue.contains(cd1));
        assertTrue("The second command is not in the main queue", queue.contains(cd2));

        CommandData cd3 = CommandData.newTimelineCommand(CommandEnum.GET_TIMELINE,
                DemoData.getMyAccount(DemoData.TWITTER_TEST_ACCOUNT_NAME),
                TimelineType.HOME)
                .setInForeground(true);
        mService.setListenedCommand(cd3);

        startCount = mService.executionStartCount;
        endCount = mService.executionEndCount;
        mService.sendListenedToCommand();

        assertTrue("Foreground command started executing",
                mService.waitForCommandExecutionStarted(startCount));
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
        new CommandQueue().clear();
        MyLog.i(this, method + " ended");
        myTestDeleteCommand(cd2);
    }
    
    private void myTestDeleteCommand(CommandData cd2) {
        MyLog.i(this, "myTestDeleteCommand started");

        CommandData cdDelete = CommandData.newItemCommand(
                CommandEnum.DELETE_COMMAND,
                null,
                cd2.getCommandId());
        cdDelete.setInForeground(true);
        mService.setListenedCommand(cdDelete);

        assertEquals(cd2.getCommandId(), mService.getListenedCommand().itemId);
        
        long endCount = mService.executionEndCount;

        mService.sendListenedToCommand();
        assertTrue("Delete command ended executing", mService.waitForCommandExecutionEnded(endCount));

        assertTrue("Service stopped", mService.waitForServiceStopped(false));

        Queue<CommandData> queue = new CommandQueue().load().get(QueueType.CURRENT);
        assertFalse("The second command was deleted from the main queue", queue.contains(cd2));
        MyLog.i(this, "myTestDeleteCommand ended");
    }
}
