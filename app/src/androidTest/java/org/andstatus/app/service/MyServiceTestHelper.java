package org.andstatus.app.service;

import org.andstatus.app.account.MyAccountTest;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.net.http.HttpConnectionMock;
import org.andstatus.app.net.social.ConnectionMock;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.StringUtil;
import org.andstatus.app.util.TriState;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class MyServiceTestHelper implements MyServiceEventsListener {
    private volatile MyServiceEventsReceiver serviceConnector;
    private volatile HttpConnectionMock httpConnectionMock;
    volatile long connectionInstanceId;

    private volatile CommandData listenedCommand = CommandData.EMPTY;
    volatile long executionStartCount = 0;
    volatile long executionEndCount = 0;
    public volatile boolean serviceStopped = false;
    private MyContext myContext = MyContextHolder.get();
    
    public void setUp(String accountName) {
        MyLog.i(this, "setUp started");
        try {
            MyServiceManager.setServiceUnavailable();
            MyServiceManager.stopService();

            MyAccountTest.fixPersistentAccounts(myContext);
            boolean isSingleMockedInstance = StringUtil.isEmpty(accountName);
            if (isSingleMockedInstance) {
                httpConnectionMock = new HttpConnectionMock();
                TestSuite.setHttpConnectionMockInstance(httpConnectionMock);
                MyContextHolder.get().setExpired(() -> this.getClass().getSimpleName() + " setUp");
            }
            myContext = MyContextHolder.initialize(myContext.context(), this);

            if (!myContext.isReady()) {
                final String msg = "Context is not ready after the initialization, repeating... " + myContext;
                MyLog.w(this, msg);
                myContext.setExpired(() -> this.getClass().getSimpleName() + msg);
                myContext = MyContextHolder.initialize(myContext.context(), this);
                assertEquals("Context should be ready", true, myContext.isReady());
            }

            MyServiceManager.setServiceUnavailable();
            MyServiceManager.stopService();
            TestSuite.getMyContextForTest().setConnectionState(ConnectionState.WIFI);

            if (!isSingleMockedInstance) {
                httpConnectionMock = ConnectionMock.newFor(accountName).getHttpMock();
            }
            connectionInstanceId = httpConnectionMock.getInstanceId();

            serviceConnector = new MyServiceEventsReceiver(myContext, this);
            serviceConnector.registerReceiver(myContext.context());

            dropQueues();
            httpConnectionMock.clearPostedData();
            assertTrue(TestSuite.setAndWaitForIsInForeground(false));
        } catch (Exception e) {
            MyLog.e(this, "setUp", e);
            fail(MyLog.getStackTrace(e));
        } finally {
            MyLog.i(this, "setUp ended instanceId=" + connectionInstanceId);
        }
    }

    private void dropQueues() {
        myContext.queues().clear();
    }

    void sendListenedCommand() {
        MyServiceManager.sendCommandEvenForUnavailable(getListenedCommand());
    }
    
    boolean assertCommandExecutionStarted(String logMsg, long count0, TriState expectStarted) {
        final String method = "waitForCommandExecutionStart " + logMsg + "; " + getListenedCommand().getCommand().save();
        MyLog.v(this, method + " started, count=" + executionStartCount + ", waiting for > " + count0);
        boolean found = false;
        String locEvent = "none";
        for (int pass = 0; pass < 1000; pass++) {
            if (executionStartCount > count0) {
                found = true;
                locEvent = "count: " + executionStartCount + " > " + count0;
                break;
            }
            if (DbUtils.waitMs(method, 30)) {
                locEvent = "interrupted";
                break;
            }
        }
        String logMsgEnd = method + " ended, found=" + found + ", count=" + executionStartCount + ", waited for > " + count0;
        MyLog.v(this, logMsgEnd);
        if (!expectStarted.equals(TriState.UNKNOWN)) {
            assertEquals(logMsgEnd, expectStarted.toBoolean(false), found);
        }
        return found;
    }

    boolean waitForCommandExecutionEnded(long count0) {
        final String method = "waitForCommandExecutionEnded";
        boolean found = false;
        String locEvent = "none";
        for (int pass = 0; pass < 1000; pass++) {
            if (executionEndCount > count0) {
                found = true;
                locEvent = "count: " + executionEndCount + " > " + count0;
                break;
            }
            if (DbUtils.waitMs(method, 30)) {
                locEvent = "interrupted";
                break;
            }
        }
        MyLog.v(this, method + " ended " + getListenedCommand().getCommand().save()
                + " " + found + ", event:" + locEvent + ", count0=" + count0);
        return found;
    }

    public boolean waitForServiceStopped(boolean clearQueue) {
        final String method = "waitForServiceStopped";
        MyLog.v(this, method + " started");
        boolean stopped = false;
        for (int pass = 1; pass < 10000; pass++) {
            if (serviceStopped) {
                if (clearQueue) {
                    dropQueues();
                }
                stopped = true;
                break;
            }
            if (DbUtils.waitMs(method, 10)) {
                break;
            }
            if (pass % 500 == 0 && MyServiceManager.getServiceState() == MyServiceState.STOPPED) {
                stopped = true;
                break;
            }
        }
        MyLog.v(this, method + " ended, " + (stopped ? " stopped" : " didn't stop"));
        return stopped;
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
        SharedPreferencesUtil.putBoolean(MyPreferences.KEY_SYNC_WHILE_USING_APPLICATION, true);
        if (serviceConnector != null) {
            serviceConnector.unregisterReceiver(myContext.context());
        }
        TestSuite.setHttpConnectionMockInstance(null);
        TestSuite.getMyContextForTest().setConnectionState(ConnectionState.UNKNOWN);
        if (myContext != null) {
            myContext.accounts().initialize();
            myContext.timelines().initialize();
        }
        MyServiceManager.setServiceAvailable();
        MyLog.v(this, "tearDown ended");
    }

    CommandData getListenedCommand() {
        return listenedCommand;
    }

    void setListenedCommand(CommandData listenedCommand) {
        this.listenedCommand = listenedCommand;
        MyLog.v(this, "setListenedCommand; " + this.listenedCommand);
    }

    public HttpConnectionMock getHttp() {
        return httpConnectionMock;
    }
}
