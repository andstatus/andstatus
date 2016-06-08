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

import android.test.InstrumentationTestCase;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.timeline.TimelineType;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;

import java.net.MalformedURLException;
import java.util.Queue;

public class MyServiceTest extends InstrumentationTestCase {
    private final MyServiceTestHelper mService = new MyServiceTestHelper();
    private volatile MyAccount ma;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        MyLog.i(this, "setUp started");
        TestSuite.initializeWithData(this);

        mService.setUp(null);
        ma = MyContextHolder.get().persistentAccounts()
                .fromAccountName(TestSuite.CONVERSATION_ACCOUNT_NAME);
        assertTrue(TestSuite.CONVERSATION_ACCOUNT_NAME + " exists", ma.isValid());
        
        MyLog.i(this, "setUp ended instanceId=" + mService.connectionInstanceId);
    }

    public void testRepeatingFailingCommand() throws MalformedURLException {
        final String method = "testRepeatingFailingCommand";
        MyLog.v(this, method + " started");

        String urlString = "http://andstatus.org/nonexistent2_avatar_" + System.currentTimeMillis() +  ".png";
        AvatarDownloaderTest.changeAvatarUrl(ma, urlString);
        
        mService.listenedCommand = CommandData.newUserCommand(
                CommandEnum.FETCH_AVATAR,
                ma.getOrigin(),
                ma.getUserId(),
                "");

        long startCount = mService.executionStartCount;
        long endCount = mService.executionEndCount;

        mService.sendListenedToCommand();
        assertTrue("First command started executing", mService.waitForCommandExecutionStarted(startCount));
        mService.sendListenedToCommand();
        assertTrue("First command ended executing", mService.waitForCommandExecutionEnded(endCount));
        assertEquals(mService.httpConnectionMock.toString(), 1, mService.httpConnectionMock.getRequestsCounter());
        mService.sendListenedToCommand();
        assertFalse("Duplicated command didn't start executing",
                mService.waitForCommandExecutionStarted(startCount + 1));
        mService.listenedCommand.setManuallyLaunched(true);
        mService.sendListenedToCommand();
        assertTrue("Manually launched duplicated command started executing",
                mService.waitForCommandExecutionStarted(startCount + 1));
        assertTrue("The third command ended executing", mService.waitForCommandExecutionEnded(endCount+1));
        assertTrue("Service stopped", mService.waitForServiceStopped(true));
        MyLog.v(this, method + " ended");
    }

    public void testAutomaticUpdates() {
        MyLog.v(this, "testAutomaticUpdates started");

        mService.listenedCommand = CommandData.newTimelineCommand(
                CommandEnum.AUTOMATIC_UPDATE, null, TimelineType.ALL);
        long startCount = mService.executionStartCount;
        long endCount = mService.executionEndCount;
        
        mService.sendListenedToCommand();
        
        assertTrue("First command started executing", mService.waitForCommandExecutionStarted(startCount));
        assertTrue("First command ended executing", mService.waitForCommandExecutionEnded(endCount));
        assertTrue(mService.httpConnectionMock.toString(), 
                mService.httpConnectionMock.getRequestsCounter() > 1);
        assertTrue("Service stopped", mService.waitForServiceStopped(true));
        MyLog.v(this, "testAutomaticUpdates ended");
    }

    public void testHomeTimeline() {
        final String method = "testHomeTimeline";
        MyLog.v(this, method + " started");

        mService.listenedCommand = CommandData.newTimelineCommand(
                CommandEnum.FETCH_TIMELINE, ma, TimelineType.HOME);
        long startCount = mService.executionStartCount;
        long endCount = mService.executionEndCount;

        mService.sendListenedToCommand();
        
        assertTrue("First command started executing", mService.waitForCommandExecutionStarted(startCount));
        assertTrue("First command ended executing", mService.waitForCommandExecutionEnded(endCount));
        MyLog.v(this, method  + "; " + mService.httpConnectionMock.toString());
        assertEquals("connection instance Id", mService.connectionInstanceId, mService.httpConnectionMock.getInstanceId());
        assertEquals(mService.httpConnectionMock.toString(), 1, mService.httpConnectionMock.getRequestsCounter());
        assertTrue("Service stopped", mService.waitForServiceStopped(true));
        MyLog.v(this, method + " ended");
    }

    public void testRateLimitStatus() {
        MyLog.v(this, "testRateLimitStatus started");

        mService.listenedCommand = CommandData.newTimelineCommand(
                CommandEnum.RATE_LIMIT_STATUS,
                TestSuite.getMyAccount(TestSuite.GNUSOCIAL_TEST_ACCOUNT_NAME),
                TimelineType.ALL);
        long startCount = mService.executionStartCount;
        long endCount = mService.executionEndCount;

        mService.sendListenedToCommand();
        assertTrue("First command started executing", mService.waitForCommandExecutionStarted(startCount));
        assertTrue("First command ended executing", mService.waitForCommandExecutionEnded(endCount));
        assertTrue(mService.httpConnectionMock.toString(),
                mService.httpConnectionMock.getRequestsCounter() > 0);
        assertTrue("Service stopped", mService.waitForServiceStopped(true));
        MyLog.v(this, "testRateLimitStatus ended");
    }
    
    public void testSyncInForeground() throws InterruptedException {
        final String method = "testSyncInForeground";
        MyLog.v(this, method + " started");
        SharedPreferencesUtil.getDefaultSharedPreferences().edit()
                .putBoolean(MyPreferences.KEY_SYNC_WHILE_USING_APPLICATION, false).commit();
        CommandData cd1 = CommandData.newTimelineCommand(CommandEnum.FETCH_TIMELINE,
                TestSuite.getMyAccount(TestSuite.TWITTER_TEST_ACCOUNT_NAME),
                TimelineType.DIRECT);
        mService.listenedCommand = cd1;

        long startCount = mService.executionStartCount;
        long endCount = mService.executionEndCount;

        mService.sendListenedToCommand();
        assertTrue("First command didn't start executing", mService.waitForCommandExecutionStarted(startCount));
        assertTrue("First command didn't end executing", mService.waitForCommandExecutionEnded(endCount));
        assertEquals(mService.httpConnectionMock.toString(),
                mService.httpConnectionMock.getRequestsCounter(), 1);

        assertTrue(TestSuite.setAndWaitForIsInForeground(true));
        MyLog.v(this, method + "; we are in a foreground");

        CommandData cd2 = CommandData.newTimelineCommand(CommandEnum.FETCH_TIMELINE,
                TestSuite.getMyAccount(TestSuite.TWITTER_TEST_ACCOUNT_NAME),
                TimelineType.MENTIONS);
        mService.listenedCommand = cd2;

        startCount = mService.executionStartCount;
        endCount = mService.executionEndCount;
        mService.sendListenedToCommand();
        assertFalse("Second command started execution", mService.waitForCommandExecutionStarted(startCount));
        MyLog.v(this, method + "; After waiting for the second command");
        assertTrue("Service stopped", mService.waitForServiceStopped(false));
        MyLog.v(this, method + "; Service stopped after the second command");
        assertEquals("No new data was posted while in foreground",
                mService.httpConnectionMock.getRequestsCounter(), 1);

        Queue<CommandData> queue = new CommandQueue().load().get(QueueType.CURRENT);
        MyLog.v(this, method + "; Queue loaded, size:" + queue.size());
        assertFalse("Main queue is empty", queue.isEmpty());
        assertFalse("First command is in the main queue", queue.contains(cd1));
        assertTrue("The second command is not in the main queue", queue.contains(cd2));

        CommandData cd3 = CommandData.newTimelineCommand(CommandEnum.FETCH_TIMELINE,
                TestSuite.getMyAccount(TestSuite.TWITTER_TEST_ACCOUNT_NAME),
                TimelineType.HOME)
                .setInForeground(true);
        mService.listenedCommand = cd3;

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
        MyLog.v(this, method + " ended");
        myTestDeleteCommand(cd2);
    }
    
    private void myTestDeleteCommand(CommandData cd2) {
        MyLog.v(this, "myTestDeleteCommand started");

        CommandData cdDelete = CommandData.newItemCommand(
                CommandEnum.DELETE_COMMAND,
                null,
                cd2.getCommandId());
        cdDelete.setInForeground(true);
        mService.listenedCommand = cdDelete;

        assertEquals(cd2.getCommandId(), mService.listenedCommand.itemId);
        
        long endCount = mService.executionEndCount;

        mService.sendListenedToCommand();
        assertTrue("Delete command ended executing", mService.waitForCommandExecutionEnded(endCount));

        assertTrue("Service stopped", mService.waitForServiceStopped(false));

        Queue<CommandData> queue = new CommandQueue().load().get(QueueType.CURRENT);
        assertFalse("The second command was deleted from the main queue", queue.contains(cd2));
        MyLog.v(this, "myTestDeleteCommand ended");
    }

    @Override
    protected void tearDown() throws Exception {
        MyLog.v(this, "tearDown started");
        mService.tearDown();
        super.tearDown();
        MyLog.v(this, "tearDown ended");
    }
}
