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

import android.content.Intent;
import android.test.InstrumentationTestCase;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.util.MyLog;

import java.net.MalformedURLException;

public class MyServiceTest extends InstrumentationTestCase implements MyServiceListener {
    private MyAccount ma;
    MyServiceReceiver serviceConnector;
    private long executionStartCount = 0;
    private long executionEndCount = 0;
    private boolean serviceStopped = false;;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        MyLog.i(this, "setUp started");
        TestSuite.initializeWithData(this);
        ma = MyContextHolder.get().persistentAccounts().fromAccountName(TestSuite.CONVERSATION_ACCOUNT_NAME);
        assertTrue(TestSuite.CONVERSATION_ACCOUNT_NAME + " exists", ma != null);
        serviceConnector = new MyServiceReceiver(this);
        serviceConnector.registerReceiver(MyContextHolder.get().context());
        MyLog.i(this, "setUp ended");
    }
    
    public void testRepeatingFailingCommand() throws MalformedURLException {
        String urlString = "http://andstatus.org/nonexistent2_avatar.png";
        AvatarDownloaderTest.changeAvatarUrl(ma, urlString);
        MyServiceManager.setServiceUnavailable();
        MyServiceManager.stopService();
        MyService myService = new MyService();
        myService.setContext(MyContextHolder.get().context());
        long startCount = executionStartCount;
        long endCount = executionEndCount;
        myService.onCreate();
        myService.initialize();
        myService.clearQueues();
        sendNewFetchAvatarCommand(myService, 1);
        assertTrue("First command started executing", waitForCommandExecutionStarted(startCount));
        sendNewFetchAvatarCommand(myService, 2);
        assertTrue("First command ended executing", waitForCommandExecutionEnded(endCount));
        serviceStopped = false;
        sendNewFetchAvatarCommand(myService, 3);
        assertFalse("Duplicated commands started executing", waitForCommandExecutionStarted(startCount+1));
        MyServiceManager.stopService();
        assertTrue("Service stopped", waitForServiceStopped());
    }

    private void sendNewFetchAvatarCommand(MyService myService, int startId) {
        CommandData data = new CommandData(CommandEnum.FETCH_AVATAR, "",  ma.getUserId());
        myService.onStartCommand(data.toIntent(new Intent()), 0, startId);
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
                if (commandData.getCommand() == CommandEnum.FETCH_AVATAR 
                && commandData.itemId == ma.getUserId() ) {
                    executionStartCount++;
                }
                break;
            case AFTER_EXECUTING_COMMAND:
                if (commandData.getCommand() == CommandEnum.FETCH_AVATAR
                        && commandData.itemId == ma.getUserId()) {
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
        super.tearDown();
    }
}
