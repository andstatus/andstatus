package org.andstatus.app.service;

import android.text.TextUtils;

import junit.framework.TestCase;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.account.MyAccountTest;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.net.http.HttpConnectionMock;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;

public class MyServiceTestHelper implements MyServiceEventsListener {
    private volatile MyServiceEventsReceiver serviceConnector;
    public volatile HttpConnectionMock httpConnectionMock;
    public volatile long connectionInstanceId;

    private volatile CommandData listenedCommand = CommandData.getEmpty();
    public volatile long executionStartCount = 0;
    public volatile long executionEndCount = 0;
    public volatile boolean serviceStopped = false;
    private MyContext myContext = MyContextHolder.get();
    
    public void setUp(String accountName) {
        MyLog.i(this, "setUp started");

        MyServiceManager.setServiceUnavailable();
        MyServiceManager.stopService();

        boolean isSingleMockedInstance = TextUtils.isEmpty(accountName);
        if (isSingleMockedInstance) {
            httpConnectionMock = new HttpConnectionMock();
            TestSuite.setHttpConnectionMockInstance(httpConnectionMock);
        } else {
            TestSuite.setHttpConnectionMockClass(HttpConnectionMock.class);
        }
        TestSuite.getMyContextForTest().setConnectionState(ConnectionState.WIFI);
        MyAccountTest.fixPersistentAccounts(myContext);
        // In order for the mocked connection to have an effect:
        myContext.persistentAccounts().initialize();
        myContext.persistentTimelines().initialize();
        MyAccount ma = myContext.persistentAccounts().fromAccountName(accountName);
        TestCase.assertTrue("HttpConnection mocked", ma.getConnection().getHttp() instanceof HttpConnectionMock);
        if (!isSingleMockedInstance) {
            httpConnectionMock = (HttpConnectionMock) ma.getConnection().getHttp();
        }
        connectionInstanceId = httpConnectionMock.getInstanceId();

        serviceConnector = new MyServiceEventsReceiver(myContext, this);
        serviceConnector.registerReceiver(myContext.context());
        
        dropQueues();
        httpConnectionMock.clearPostedData();
        TestCase.assertTrue(TestSuite.setAndWaitForIsInForeground(false));

        MyLog.i(this, "setUp ended instanceId=" + connectionInstanceId);
    }

    private void dropQueues() {
        new CommandQueue().clear();
    }

    public void sendListenedToCommand() {
        MyServiceManager.sendCommandEvenForUnavailable(getListenedCommand());
    }
    
    public boolean waitForCommandExecutionStarted(long count0) {
        final String method = "waitForCommandExecutionStarted";
        boolean found = false;
        String locEvent = "none";
        for (int pass = 0; pass < 1000; pass++) {
            if (executionStartCount > count0) {
                found = true;
                locEvent = "count: " + executionStartCount + " > " + count0;
                break;
            }
            DbUtils.waitMs(method, 30);
            if (Thread.currentThread().isInterrupted()) {
                locEvent = "interrupted";
                break;
            }
        }
        MyLog.v(this, "waitForCommandExecutionStarted " + getListenedCommand().getCommand().save()
                + " " + found + ", event:" + locEvent + ", count0=" + count0);
        return found;
    }

    public boolean waitForCommandExecutionEnded(long count0) {
        final String method = "waitForCommandExecutionEnded";
        boolean found = false;
        String locEvent = "none";
        for (int pass = 0; pass < 1000; pass++) {
            if (executionEndCount > count0) {
                found = true;
                locEvent = "count: " + executionEndCount + " > " + count0;
                break;
            }
            DbUtils.waitMs(method, 30);
            if (Thread.currentThread().isInterrupted()) {
                locEvent = "interrupted";
                break;
            }
        }
        MyLog.v(this, "waitForCommandExecutionEnded " + getListenedCommand().getCommand().save()
                + " " + found + ", event:" + locEvent + ", count0=" + count0);
        return found;
    }

    public boolean waitForServiceStopped(boolean clearQueue) {
        final String method = "waitForServiceStopped";
        for (int pass = 0; pass < 10000; pass++) {
            if (serviceStopped) {
                if (clearQueue) {
                    new CommandQueue().clear();
                }
                return true;
            }
            DbUtils.waitMs(method, 10);
            if (Thread.currentThread().isInterrupted()) {
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
                if (commandData.equals(getListenedCommand())) {
                    executionStartCount++;
                    locEvent = "execution started";
                }
                serviceStopped = false;
                break;
            case AFTER_EXECUTING_COMMAND:
                if (commandData.equals(getListenedCommand())) {
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
        MyLog.v(this, "onReceive; " + locEvent + ", " + commandData + ", event:" + myServiceEvent + ", requestsCounter:" + httpConnectionMock.getRequestsCounter());

    }
    
    public void tearDown() {
        MyLog.v(this, "tearDown started");
        dropQueues();
        SharedPreferencesUtil.getDefaultSharedPreferences().edit()
                .putBoolean(MyPreferences.KEY_SYNC_WHILE_USING_APPLICATION, true).commit();
        
        serviceConnector.unregisterReceiver(myContext.context());
        TestSuite.setHttpConnectionMockClass(null);
        TestSuite.setHttpConnectionMockInstance(null);
        TestSuite.getMyContextForTest().setConnectionState(ConnectionState.UNKNOWN);
        myContext.persistentAccounts().initialize();
        myContext.persistentTimelines().initialize();
        MyLog.v(this, "tearDown ended");
    }

    public CommandData getListenedCommand() {
        return listenedCommand;
    }

    public void setListenedCommand(CommandData listenedCommand) {
        this.listenedCommand = listenedCommand;
        MyLog.v(this, "setListenedCommand; " + this.listenedCommand);
    }
}
