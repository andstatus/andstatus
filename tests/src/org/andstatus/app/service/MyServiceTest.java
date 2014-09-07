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
import org.andstatus.app.account.MyAccountTest;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.TimelineTypeEnum;
import org.andstatus.app.net.HttpConnectionMock;
import org.andstatus.app.util.MyLog;

import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.PriorityBlockingQueue;

public class MyServiceTest extends InstrumentationTestCase implements MyServiceListener {
    private volatile MyAccount ma;
    private volatile HttpConnectionMock httpConnection;
    private volatile long connectionInstanceId;
    private volatile MyServiceReceiver serviceConnector;

    private volatile CommandData listentedToCommand = CommandData.getEmpty();
    
    private volatile long executionStartCount = 0;
    private volatile long executionEndCount = 0;
    private volatile boolean serviceStopped = false;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        MyLog.i(this, "setUp started");
        TestSuite.initializeWithData(this);

        MyServiceManager.setServiceUnavailable();
        MyServiceManager.stopService();

        httpConnection = new HttpConnectionMock();
        connectionInstanceId = httpConnection.getInstanceId();
        TestSuite.setHttpConnection(httpConnection);
        assertEquals("HttpConnection mocked", MyContextHolder.get().getHttpConnectionMock(),
                httpConnection);
        TestSuite.getMyContextForTest().setOnline(ConnectionRequired.WIFI);
        MyAccountTest.fixPersistentAccounts();
        // In order the mocked connection to have effect:
        MyContextHolder.get().persistentAccounts().initialize();

        ma = MyContextHolder.get().persistentAccounts()
                .fromAccountName(TestSuite.CONVERSATION_ACCOUNT_NAME);
        assertTrue(TestSuite.CONVERSATION_ACCOUNT_NAME + " exists", ma != null);
        serviceConnector = new MyServiceReceiver(this);
        serviceConnector.registerReceiver(MyContextHolder.get().context());
        
        dropQueues();
        httpConnection.clearPostedData();
        assertTrue(TestSuite.setAndWaitForIsInForeground(false));
        
        MyLog.i(this, "setUp ended instanceId=" + connectionInstanceId);
    }

    public void testRepeatingFailingCommand() throws MalformedURLException {
        final String method = "testRepeatingFailingCommand";
        MyLog.v(this, method + " started");

        String urlString = "http://andstatus.org/nonexistent2_avatar_" + System.currentTimeMillis() +  ".png";
        AvatarDownloaderTest.changeAvatarUrl(ma, urlString);
        
        listentedToCommand = new CommandData(CommandEnum.FETCH_AVATAR, "", TimelineTypeEnum.UNKNOWN, ma.getUserId());

        long startCount = executionStartCount;
        long endCount = executionEndCount;

        sendListenedToCommand();
        assertTrue("First command started executing", waitForCommandExecutionStarted(startCount));
        sendListenedToCommand();
        assertTrue("First command ended executing", waitForCommandExecutionEnded(endCount));
        assertTrue("Data was posted " + httpConnection.getPostedCounter() + " times",
                httpConnection.getPostedCounter() == 0);
        sendListenedToCommand();
        assertFalse("Duplicated commands started executing",
                waitForCommandExecutionStarted(startCount + 1));
        assertTrue("Service stopped", waitForServiceStopped());
        MyLog.v(this, method + " ended");
    }

    public void testAutomaticUpdates() {
        MyLog.v(this, "testAutomaticUpdates started");

        listentedToCommand = new CommandData(CommandEnum.AUTOMATIC_UPDATE, "", TimelineTypeEnum.ALL, 0);
        long startCount = executionStartCount;
        long endCount = executionEndCount;
        
        sendListenedToCommand();
        
        assertTrue("First command started executing", waitForCommandExecutionStarted(startCount));
        assertTrue("First command ended executing", waitForCommandExecutionEnded(endCount));
        assertTrue("Data was posted " + httpConnection.getPostedCounter() + " times",
                httpConnection.getPostedCounter() > 1);
        assertTrue("Service stopped", waitForServiceStopped());
        MyLog.v(this, "testAutomaticUpdates ended");
    }

    public void testHomeTimeline() {
        final String method = "testHomeTimeline";
        MyLog.v(this, method + " started");

        listentedToCommand = new CommandData(CommandEnum.FETCH_TIMELINE, ma.getAccountName(), TimelineTypeEnum.HOME, 0);
        long startCount = executionStartCount;
        long endCount = executionEndCount;

        sendListenedToCommand();
        
        assertTrue("First command started executing", waitForCommandExecutionStarted(startCount));
        assertTrue("First command ended executing", waitForCommandExecutionEnded(endCount));
        String message = "Data was posted " + httpConnection.getPostedCounter() + " times; "
                + Arrays.toString(httpConnection.getPathStringList().toArray(new String[]{}));
        MyLog.v(this, method  + "; " + message);
        assertEquals("connection istance Id", connectionInstanceId, httpConnection.getInstanceId());
        assertTrue(message, httpConnection.getPostedCounter() == 1);
        assertTrue("Service stopped", waitForServiceStopped());
        MyLog.v(this, method + " ended");
    }

    public void testRateLimitStatus() {
        MyLog.v(this, "testRateLimitStatus started");

        listentedToCommand = new CommandData(CommandEnum.RATE_LIMIT_STATUS, TestSuite.STATUSNET_TEST_ACCOUNT_NAME, TimelineTypeEnum.ALL, 0);
        long startCount = executionStartCount;
        long endCount = executionEndCount;

        sendListenedToCommand();
        assertTrue("First command started executing", waitForCommandExecutionStarted(startCount));
        assertTrue("First command ended executing", waitForCommandExecutionEnded(endCount));
        assertTrue("Data was posted " + httpConnection.getPostedCounter() + " times",
                httpConnection.getPostedCounter() > 0);
        assertTrue("Service stopped", waitForServiceStopped());
        MyLog.v(this, "testRateLimitStatus ended");
    }
    
    public void testSyncInForeground() throws InterruptedException {
        MyLog.v(this, "testSyncInForeground started");
        MyPreferences.getDefaultSharedPreferences().edit()
                .putBoolean(MyPreferences.KEY_SYNC_WHILE_USING_APPLICATION, false).commit();
        CommandData cd1 = new CommandData(CommandEnum.FETCH_TIMELINE,
                TestSuite.TWITTER_TEST_ACCOUNT_NAME, TimelineTypeEnum.DIRECT, 0);
        listentedToCommand = cd1;

        long startCount = executionStartCount;
        long endCount = executionEndCount;

        sendListenedToCommand();
        assertTrue("First command started executing", waitForCommandExecutionStarted(startCount));
        assertTrue("First command ended executing", waitForCommandExecutionEnded(endCount));
        assertTrue("Data was posted " + httpConnection.getPostedCounter() + " times",
                httpConnection.getPostedCounter() == 1);

        assertTrue(TestSuite.setAndWaitForIsInForeground(true));
        sendListenedToCommand();

        CommandData cd2 = new CommandData(CommandEnum.FETCH_TIMELINE,
                TestSuite.TWITTER_TEST_ACCOUNT_NAME, TimelineTypeEnum.MENTIONS, 0);
        listentedToCommand = cd2;
        sendListenedToCommand();

        assertTrue("Service stopped", waitForServiceStopped());
        assertTrue("No new data was posted while in foreround " + httpConnection.getPostedCounter()
                + " times",
                httpConnection.getPostedCounter() == 1);

        Queue<CommandData> queue = new PriorityBlockingQueue<CommandData>(100);
        CommandData.loadQueue(MyContextHolder.get().context(), queue,
                MyService.COMMANDS_QUEUE_FILENAME);
        assertFalse("Main queue is not empty", queue.isEmpty());
        assertTrue("The command stayed in the main queue", queue.contains(cd1));
        assertTrue("The command stayed in the main queue", queue.contains(cd2));

        CommandData cd3 = new CommandData(CommandEnum.FETCH_TIMELINE,
                TestSuite.TWITTER_TEST_ACCOUNT_NAME, TimelineTypeEnum.HOME, 0)
                .setInForeground(true);
        listentedToCommand = cd3;

        startCount = executionStartCount;
        endCount = executionEndCount;
        sendListenedToCommand();

        assertTrue("Foreground command started executing",
                waitForCommandExecutionStarted(startCount));
        assertTrue("Foreground command ended executing", waitForCommandExecutionEnded(endCount));
        assertTrue("Service stopped", waitForServiceStopped());

        queue = new PriorityBlockingQueue<CommandData>(100);
        CommandData.loadQueue(MyContextHolder.get().context(), queue,
                MyService.COMMANDS_QUEUE_FILENAME);
        assertFalse("Main queue is not empty", queue.isEmpty());
        assertFalse("First duplicated command was found in retry queue", queue.contains(cd1));
        assertTrue("The command stayed in the main queue", queue.contains(cd2));
        assertFalse("Foreground command is not in main queue", queue.contains(cd3));

        MyLog.v(this, "testSyncInForeground ended");
    }

    private void dropQueues() {
        listentedToCommand = new CommandData(CommandEnum.DROP_QUEUES, "", TimelineTypeEnum.UNKNOWN, 0);
        long endCount = executionEndCount;
        sendListenedToCommand();
        assertTrue("Drop queues command ended executing", waitForCommandExecutionEnded(endCount));
    }

    private void sendListenedToCommand() {
        MyServiceManager.sendCommandEvenForUnavailable(listentedToCommand);
    }
    
    private boolean waitForCommandExecutionStarted(long count0) {
        boolean found = false;
        String locEvent = "none";
        for (int pass = 0; pass < 1000; pass++) {
            if (executionStartCount > count0) {
                found = true;
                locEvent = "count: " + executionStartCount + " > " + count0;
                break;
            }
            try {
                Thread.sleep(30);
            } catch (InterruptedException e) {
                e.printStackTrace();
                locEvent = "interrupted";
                break;
            }
        }
        MyLog.v(this, "waitForCommandExecutionStarted " + listentedToCommand.getCommand().save()
                + " " + found + ", event:" + locEvent + ", count0=" + count0);
        return found;
    }

    private boolean waitForCommandExecutionEnded(long count0) {
        boolean found = false;
        String locEvent = "none";
        for (int pass = 0; pass < 1000; pass++) {
            if (executionEndCount > count0) {
                found = true;
                locEvent = "count: " + executionEndCount + " > " + count0;
                break;
            }
            try {
                Thread.sleep(30);
            } catch (InterruptedException e) {
                e.printStackTrace();
                locEvent = "interrupted";
                break;
            }
        }
        MyLog.v(this, "waitForCommandExecutionEnded " + listentedToCommand.getCommand().save()
                + " " + found + ", event:" + locEvent + ", count0=" + count0);
        return found;
    }

    private boolean waitForServiceStopped() {
        for (int pass = 0; pass < 10000; pass++) {
            if (serviceStopped) {
                return true;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
                return false;
            }
        }
        return false;
    }

    @Override
    public void onReceive(CommandData commandData, MyServiceEvent myServiceEvent) {
        String locEvent = "ignored";
        switch (myServiceEvent) {
            case BEFORE_EXECUTING_COMMAND:
                if (commandData.equals(listentedToCommand)) {
                    executionStartCount++;
                    locEvent = "execution started";
                }
                serviceStopped = false;
                break;
            case AFTER_EXECUTING_COMMAND:
                if (commandData.equals(listentedToCommand)) {
                    executionEndCount++;
                    locEvent = "execution ended";
                }
                break;
            case ON_STOP:
                serviceStopped = true;
                locEvent = "service stopped";
                break;
            default:
                break;
        }
        MyLog.v(this, "onReceive; " + locEvent + ", " + commandData + ", event:" + myServiceEvent + ", postedCounter:" + httpConnection.getPostedCounter());

    }
    
    @Override
    protected void tearDown() throws Exception {
        MyLog.v(this, "tearDown started");
        dropQueues();
        MyPreferences.getDefaultSharedPreferences().edit()
                .putBoolean(MyPreferences.KEY_SYNC_WHILE_USING_APPLICATION, true).commit();
        
        serviceConnector.unregisterReceiver(MyContextHolder.get().context());
        TestSuite.setHttpConnection(null);
        TestSuite.getMyContextForTest().setOnline(ConnectionRequired.ANY);
        MyContextHolder.get().persistentAccounts().initialize();
        super.tearDown();
        MyLog.v(this, "tearDown ended");
    }
}
