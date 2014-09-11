package org.andstatus.app.service;

import junit.framework.TestCase;

import org.andstatus.app.account.MyAccountTest;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.TimelineTypeEnum;
import org.andstatus.app.net.HttpConnectionMock;
import org.andstatus.app.util.MyLog;

public class MyServiceTestHelper implements MyServiceListener {
    private volatile MyServiceReceiver serviceConnector;
    public volatile HttpConnectionMock httpConnectionMock;
    public volatile long connectionInstanceId;

    public volatile CommandData listentedToCommand = CommandData.getEmpty();
    public volatile long executionStartCount = 0;
    public volatile long executionEndCount = 0;
    public volatile boolean serviceStopped = false;
    
    public void setUp() {
        MyLog.i(this, "setUp started");

        MyServiceManager.setServiceUnavailable();
        MyServiceManager.stopService();

        httpConnectionMock = new HttpConnectionMock();
        connectionInstanceId = httpConnectionMock.getInstanceId();
        
        TestSuite.setHttpConnectionMock(httpConnectionMock);
        TestCase.assertEquals("HttpConnection mocked", MyContextHolder.get().getHttpConnectionMock(),
                httpConnectionMock);
        TestSuite.getMyContextForTest().setOnline(ConnectionRequired.WIFI);
        MyAccountTest.fixPersistentAccounts();
        // In order for the mocked connection to have effect:
        MyContextHolder.get().persistentAccounts().initialize();

        serviceConnector = new MyServiceReceiver(this);
        serviceConnector.registerReceiver(MyContextHolder.get().context());
        
        dropQueues();
        httpConnectionMock.clearPostedData();
        TestCase.assertTrue(TestSuite.setAndWaitForIsInForeground(false));

        MyLog.i(this, "setUp ended instanceId=" + connectionInstanceId);
    }

    private void dropQueues() {
        listentedToCommand = new CommandData(CommandEnum.DROP_QUEUES, "", TimelineTypeEnum.UNKNOWN, 0);
        long endCount = executionEndCount;
        sendListenedToCommand();
        TestCase.assertTrue("Drop queues command ended executing", waitForCommandExecutionEnded(endCount));
    }

    public void sendListenedToCommand() {
        MyServiceManager.sendCommandEvenForUnavailable(listentedToCommand);
    }
    
    public boolean waitForCommandExecutionStarted(long count0) {
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

    public boolean waitForCommandExecutionEnded(long count0) {
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

    public boolean waitForServiceStopped() {
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
        MyLog.v(this, "onReceive; " + locEvent + ", " + commandData + ", event:" + myServiceEvent + ", postedCounter:" + httpConnectionMock.getPostedCounter());

    }
    
    public void tearDown() {
        MyLog.v(this, "tearDown started");
        dropQueues();
        MyPreferences.getDefaultSharedPreferences().edit()
                .putBoolean(MyPreferences.KEY_SYNC_WHILE_USING_APPLICATION, true).commit();
        
        serviceConnector.unregisterReceiver(MyContextHolder.get().context());
        TestSuite.setHttpConnectionMock(null);
        TestSuite.getMyContextForTest().setOnline(ConnectionRequired.ANY);
        MyContextHolder.get().persistentAccounts().initialize();
        MyLog.v(this, "tearDown ended");
    }
}
