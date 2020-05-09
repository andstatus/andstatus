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

import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.TriState;
import org.junit.Test;

import java.util.Arrays;
import java.util.Optional;

import static org.andstatus.app.context.DemoData.demoData;
import static org.andstatus.app.context.MyContextHolder.myContextHolder;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.MatcherAssert.assertThat;
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

        CommandQueue queues = myContextHolder.getBlocking().queues();
        MyLog.i(this, method + "; Queues1:" + queues);

        assertEquals("First command shouldn't be in any queue " + queues,
                Optional.empty(), queues.inWhichQueue(cd1Home).map(q -> q.queueType));
        assertThat("Second command should be in the Main or Skip queue " + queues,
                Arrays.asList(Optional.of(QueueType.CURRENT), Optional.of(QueueType.SKIPPED)),
                hasItem(queues.inWhichQueue(cd2Interactions).map(q -> q.queueType)));

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

        MyLog.i(this, method + "; Queues2:" + queues);

        assertEquals("Third command shouldn't be in any queue " + queues,
                Optional.empty(), queues.inWhichQueue(cd3PublicForeground).map(q -> q.queueType));

        assertThat("Second command should be in the Main or Skip queue " + queues,
                Arrays.asList(Optional.of(QueueType.CURRENT), Optional.of(QueueType.SKIPPED)),
                hasItem(queues.inWhichQueue(cd2Interactions).map(q -> q.queueType)));

        CommandData cd2FromQueue = queues.getFromAnyQueue(cd2Interactions);
        assertEquals("command id " + cd2FromQueue, cd2Interactions.getCommandId(), cd2FromQueue.getCommandId());
        assertTrue("command id " + cd2FromQueue, cd2FromQueue.getCommandId() >= 0);
        
        MyLog.i(this, method + " ended");

        myContextHolder.getNow().queues().clear();
    }
}
