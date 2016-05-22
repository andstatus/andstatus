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
import org.andstatus.app.data.TimelineType;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;

import java.net.MalformedURLException;
import java.util.Queue;
import java.util.concurrent.PriorityBlockingQueue;

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
        
        mService.listenedCommand = new CommandData(CommandEnum.FETCH_AVATAR, "", TimelineType.UNKNOWN,
                ma.getUserId());

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
        assertTrue("Service stopped", mService.waitForServiceStopped());
        MyLog.v(this, method + " ended");
    }

    public void testAutomaticUpdates() {
        MyLog.v(this, "testAutomaticUpdates started");

        mService.listenedCommand = new CommandData(CommandEnum.AUTOMATIC_UPDATE, "", TimelineType.ALL, 0);
        long startCount = mService.executionStartCount;
        long endCount = mService.executionEndCount;
        
        mService.sendListenedToCommand();
        
        assertTrue("First command started executing", mService.waitForCommandExecutionStarted(startCount));
        assertTrue("First command ended executing", mService.waitForCommandExecutionEnded(endCount));
        assertTrue(mService.httpConnectionMock.toString(), 
                mService.httpConnectionMock.getRequestsCounter() > 1);
        assertTrue("Service stopped", mService.waitForServiceStopped());
        MyLog.v(this, "testAutomaticUpdates ended");
    }

    public void testHomeTimeline() {
        final String method = "testHomeTimeline";
        MyLog.v(this, method + " started");

        mService.listenedCommand = new CommandData(CommandEnum.FETCH_TIMELINE, ma.getAccountName(), TimelineType.HOME, 0);
        long startCount = mService.executionStartCount;
        long endCount = mService.executionEndCount;

        mService.sendListenedToCommand();
        
        assertTrue("First command started executing", mService.waitForCommandExecutionStarted(startCount));
        assertTrue("First command ended executing", mService.waitForCommandExecutionEnded(endCount));
        MyLog.v(this, method  + "; " + mService.httpConnectionMock.toString());
        assertEquals("connection instance Id", mService.connectionInstanceId, mService.httpConnectionMock.getInstanceId());
        assertEquals(mService.httpConnectionMock.toString(), 1, mService.httpConnectionMock.getRequestsCounter());
        assertTrue("Service stopped", mService.waitForServiceStopped());
        MyLog.v(this, method + " ended");
    }

    public void testRateLimitStatus() {
        MyLog.v(this, "testRateLimitStatus started");

        mService.listenedCommand = new CommandData(CommandEnum.RATE_LIMIT_STATUS, TestSuite.GNUSOCIAL_TEST_ACCOUNT_NAME, TimelineType.ALL, 0);
        long startCount = mService.executionStartCount;
        long endCount = mService.executionEndCount;

        mService.sendListenedToCommand();
        assertTrue("First command started executing", mService.waitForCommandExecutionStarted(startCount));
        assertTrue("First command ended executing", mService.waitForCommandExecutionEnded(endCount));
        assertTrue(mService.httpConnectionMock.toString(),
                mService.httpConnectionMock.getRequestsCounter() > 0);
        assertTrue("Service stopped", mService.waitForServiceStopped());
        MyLog.v(this, "testRateLimitStatus ended");
    }
    
    public void testSyncInForeground() throws InterruptedException {
        MyLog.v(this, "testSyncInForeground started");
        SharedPreferencesUtil.getDefaultSharedPreferences().edit()
                .putBoolean(MyPreferences.KEY_SYNC_WHILE_USING_APPLICATION, false).commit();
        CommandData cd1 = new CommandData(CommandEnum.FETCH_TIMELINE,
                TestSuite.TWITTER_TEST_ACCOUNT_NAME, TimelineType.DIRECT, 0);
        mService.listenedCommand = cd1;

        long startCount = mService.executionStartCount;
        long endCount = mService.executionEndCount;

        mService.sendListenedToCommand();
        assertTrue("First command started executing", mService.waitForCommandExecutionStarted(startCount));
        assertTrue("First command ended executing", mService.waitForCommandExecutionEnded(endCount));
        assertEquals(mService.httpConnectionMock.toString(),
                mService.httpConnectionMock.getRequestsCounter(), 1);

        assertTrue(TestSuite.setAndWaitForIsInForeground(true));

        CommandData cd2 = new CommandData(CommandEnum.FETCH_TIMELINE,
                TestSuite.TWITTER_TEST_ACCOUNT_NAME, TimelineType.MENTIONS, 0);
        mService.listenedCommand = cd2;
        mService.sendListenedToCommand();

        assertTrue("Service stopped", mService.waitForServiceStopped());
        assertEquals("No new data was posted while in foreground",
                mService.httpConnectionMock.getRequestsCounter(), 1);

        Queue<CommandData> queue = new CommandQueue().load().get(QueueType.CURRENT);
        assertFalse("Main queue is not empty", queue.isEmpty());
        assertFalse("First command is not in the main queue", queue.contains(cd1));
        assertTrue("The second command stayed in the main queue", queue.contains(cd2));

        CommandData cd3 = new CommandData(CommandEnum.FETCH_TIMELINE,
                TestSuite.TWITTER_TEST_ACCOUNT_NAME, TimelineType.HOME, 0)
                .setInForeground(true);
        mService.listenedCommand = cd3;

        startCount = mService.executionStartCount;
        endCount = mService.executionEndCount;
        mService.sendListenedToCommand();

        assertTrue("Foreground command started executing",
                mService.waitForCommandExecutionStarted(startCount));
        assertTrue("Foreground command ended executing", mService.waitForCommandExecutionEnded(endCount));
        assertTrue("Service stopped", mService.waitForServiceStopped());

        queue = new CommandQueue().load().get(QueueType.CURRENT);
        assertFalse("Main queue is not empty", queue.isEmpty());
        assertTrue("The second command stayed in the main queue", queue.contains(cd2));
        
        long idFound = -1;
        for (CommandData cd : queue) {
            if (cd.equals(cd2)) {
                idFound = cd.getId();
            }
        }
        assertEquals("command id", cd2.getId(), idFound);
        assertTrue("command id=" + idFound, idFound >= 0);
        
        assertFalse("Foreground command is not in main queue", queue.contains(cd3));
        MyLog.v(this, "testSyncInForeground ended");
        myTestDeleteCommand(cd2);
    }
    
    private void myTestDeleteCommand(CommandData cd2) {
        MyLog.v(this, "myTestDeleteCommand started");

        CommandData cdDelete = new CommandData(CommandEnum.DELETE_COMMAND,
        "", TimelineType.EVERYTHING, cd2.getId());
        cdDelete.setInForeground(true);
        mService.listenedCommand = cdDelete;

        assertEquals(cd2.getId(), mService.listenedCommand.itemId);
        
        long endCount = mService.executionEndCount;

        mService.sendListenedToCommand();
        assertTrue("Delete command ended executing", mService.waitForCommandExecutionEnded(endCount));

        assertTrue("Service stopped", mService.waitForServiceStopped());

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
