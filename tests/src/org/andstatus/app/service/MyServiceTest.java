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
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.TimelineTypeEnum;
import org.andstatus.app.net.HttpConnectionMock;
import org.andstatus.app.util.MyLog;

import java.net.MalformedURLException;

public class MyServiceTest extends InstrumentationTestCase implements MyServiceListener {
    private MyAccount ma;
    private HttpConnectionMock httpConnection;
    MyServiceReceiver serviceConnector;

    private CommandEnum commandToListen = CommandEnum.AUTOMATIC_UPDATE;
    private long itemIdToListen = 0;
    private String accountNameToListen = "";
    private TimelineTypeEnum timelineTypeToListen = TimelineTypeEnum.ALL;

    private long executionStartCount = 0;
    private long executionEndCount = 0;
    private boolean serviceStopped = false;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        MyLog.i(this, "setUp started");
        TestSuite.initializeWithData(this);

        MyServiceManager.setServiceUnavailable();
        MyServiceManager.stopService();

        httpConnection = new HttpConnectionMock();
        TestSuite.setHttpConnection(httpConnection);
        assertEquals("HttpConnection mocked", MyContextHolder.get().getHttpConnectionMock(),
                httpConnection);
        // In order the mocked connection to have effect:
        MyContextHolder.get().persistentAccounts().initialize();

        ma = MyContextHolder.get().persistentAccounts()
                .fromAccountName(TestSuite.CONVERSATION_ACCOUNT_NAME);
        assertTrue(TestSuite.CONVERSATION_ACCOUNT_NAME + " exists", ma != null);
        serviceConnector = new MyServiceReceiver(this);
        serviceConnector.registerReceiver(MyContextHolder.get().context());
        MyLog.i(this, "setUp ended");
    }

    public void testRepeatingFailingCommand() throws MalformedURLException {
        String urlString = "http://andstatus.org/nonexistent2_avatar.png";
        AvatarDownloaderTest.changeAvatarUrl(ma, urlString);

        commandToListen = CommandEnum.DROP_QUEUES;
        accountNameToListen = "";
        timelineTypeToListen = TimelineTypeEnum.UNKNOWN;
        itemIdToListen = 0;
        sendListenedToCommand();

        commandToListen = CommandEnum.FETCH_AVATAR;
        itemIdToListen = ma.getUserId();

        long startCount = executionStartCount;
        long endCount = executionEndCount;
        httpConnection.clearPostedData();

        sendListenedToCommand();
        assertTrue("First command started executing", waitForCommandExecutionStarted(startCount));
        sendListenedToCommand();
        assertTrue("First command ended executing", waitForCommandExecutionEnded(endCount));
        assertTrue("Data was posted " + httpConnection.getPostedCounter() + " times",
                httpConnection.getPostedCounter() == 0);
        serviceStopped = false;
        sendListenedToCommand();
        assertFalse("Duplicated commands started executing",
                waitForCommandExecutionStarted(startCount + 1));
        MyServiceManager.stopService();
        assertTrue("Service stopped", waitForServiceStopped());
    }

    private void sendListenedToCommand() {
        CommandData data = new CommandData(commandToListen, accountNameToListen,
                timelineTypeToListen, itemIdToListen);
        MyServiceManager.sendCommandEvenForUnavailable(data);
    }

    public void testAutomaticUpdates() {
        commandToListen = CommandEnum.DROP_QUEUES;
        accountNameToListen = "";
        timelineTypeToListen = TimelineTypeEnum.ALL;
        itemIdToListen = 0;
        httpConnection.clearPostedData();
        sendListenedToCommand();

        commandToListen = CommandEnum.AUTOMATIC_UPDATE;
        itemIdToListen = ma.getUserId();

        long startCount = executionStartCount;
        long endCount = executionEndCount;

        sendListenedToCommand();
        assertTrue("First command started executing", waitForCommandExecutionStarted(startCount));
        assertTrue("First command ended executing", waitForCommandExecutionEnded(endCount));
        assertTrue("Data was posted " + httpConnection.getPostedCounter() + " times",
                httpConnection.getPostedCounter() > 1);
    }

    public void testHomeTimeline() {
        commandToListen = CommandEnum.DROP_QUEUES;
        accountNameToListen = ma.getAccountName();
        timelineTypeToListen = TimelineTypeEnum.HOME;
        itemIdToListen = 0;
        httpConnection.clearPostedData();
        sendListenedToCommand();

        commandToListen = CommandEnum.FETCH_TIMELINE;
        itemIdToListen = ma.getUserId();

        long startCount = executionStartCount;
        long endCount = executionEndCount;

        sendListenedToCommand();
        assertTrue("First command started executing", waitForCommandExecutionStarted(startCount));
        assertTrue("First command ended executing", waitForCommandExecutionEnded(endCount));
        assertTrue("Data was posted " + httpConnection.getPostedCounter() + " times",
                httpConnection.getPostedCounter() == 1);
    }

    public void testRateLimitStatus() {
        commandToListen = CommandEnum.DROP_QUEUES;
        accountNameToListen = "";
        timelineTypeToListen = TimelineTypeEnum.ALL;
        itemIdToListen = 0;
        httpConnection.clearPostedData();
        sendListenedToCommand();

        commandToListen = CommandEnum.RATE_LIMIT_STATUS;
        itemIdToListen = 0;

        long startCount = executionStartCount;
        long endCount = executionEndCount;

        sendListenedToCommand();
        assertTrue("First command started executing", waitForCommandExecutionStarted(startCount));
        assertTrue("First command ended executing", waitForCommandExecutionEnded(endCount));
        assertTrue("Data was posted " + httpConnection.getPostedCounter() + " times",
                httpConnection.getPostedCounter() > 0);
    }

    private boolean waitForCommandExecutionStarted(long startCount) {
        for (int pass = 0; pass < 1000; pass++) {
            if (executionStartCount > startCount) {
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

    private boolean waitForCommandExecutionEnded(long endCount) {
        for (int pass = 0; pass < 1000; pass++) {
            if (executionEndCount > endCount) {
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
        switch (myServiceEvent) {
            case BEFORE_EXECUTING_COMMAND:
                if (commandData.getCommand() == commandToListen
                        && commandData.itemId == itemIdToListen) {
                    executionStartCount++;
                }
                break;
            case AFTER_EXECUTING_COMMAND:
                if (commandData.getCommand() == commandToListen
                        && commandData.itemId == itemIdToListen) {
                    executionEndCount++;
                }
                break;
            case ON_STOP:
                serviceStopped = true;
                break;
            default:
                break;
        }

    }

    @Override
    protected void tearDown() throws Exception {
        serviceConnector.unregisterReceiver(MyContextHolder.get().context());
        TestSuite.setHttpConnection(null);
        MyContextHolder.get().persistentAccounts().initialize();
        super.tearDown();
    }
}
