package org.andstatus.app.service

import org.andstatus.app.account.MyAccountTest
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.context.TestSuite
import org.andstatus.app.data.DbUtils
import org.andstatus.app.net.http.HttpConnectionStub
import org.andstatus.app.net.social.ConnectionStub
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.SharedPreferencesUtil
import org.andstatus.app.util.TriState
import org.junit.Assert

class MyServiceTestHelper : MyServiceEventsListener {
    @Volatile
    private var serviceConnector: MyServiceEventsReceiver? = null

    @Volatile
    private var httpConnectionStub: HttpConnectionStub? = null

    @Volatile
    var connectionInstanceId: Long = 0

    @Volatile
    private var listenedCommand: CommandData = CommandData.Companion.EMPTY

    @Volatile
    var executionStartCount: Long = 0

    @Volatile
    var executionEndCount: Long = 0

    @Volatile
    var serviceStopped = false
    private var myContext: MyContext = MyContextHolder.myContextHolder.getNow()

    fun setUp(accountName: String?) {
        MyLog.i(this, "setUp started")
        try {
            MyServiceManager.setServiceUnavailable()
            MyServiceManager.stopService()
            MyAccountTest.fixPersistentAccounts(myContext)
            val isSingleStubbedInstance = accountName.isNullOrEmpty()
            if (isSingleStubbedInstance) {
                httpConnectionStub = HttpConnectionStub()
                TestSuite.setHttpConnectionStubInstance(httpConnectionStub)
                MyContextHolder.myContextHolder.getBlocking().setExpired { this.javaClass.simpleName + " setUp" }
            }
            myContext = MyContextHolder.myContextHolder.initialize(myContext.context, this).getBlocking()
            if (!myContext.isReady) {
                val msg = "Context is not ready after the initialization, repeating... $myContext"
                MyLog.w(this, msg)
                myContext.setExpired { this.javaClass.simpleName + msg }
                myContext = MyContextHolder.myContextHolder.initialize(myContext.context, this).getBlocking()
                Assert.assertEquals("Context should be ready", true, myContext.isReady)
            }
            MyServiceManager.setServiceUnavailable()
            MyServiceManager.stopService()
            TestSuite.getMyContextForTest().connectionState = ConnectionState.WIFI
            if (!isSingleStubbedInstance) {
                httpConnectionStub = ConnectionStub.newFor(accountName).getHttpStub()
            }
            connectionInstanceId = httpConnectionStub?.getInstanceId() ?: 0
            serviceConnector = MyServiceEventsReceiver(myContext, this).also {
                it.registerReceiver(myContext.context)
            }
            Assert.assertTrue("Couldn't stop MyService", waitForServiceStopped(false))
            httpConnectionStub?.clearData()
            Assert.assertTrue(TestSuite.setAndWaitForIsInForeground(false))
        } catch (e: Exception) {
            MyLog.e(this, "setUp", e)
            Assert.fail(MyLog.getStackTrace(e))
        } finally {
            MyLog.i(this, "setUp ended instanceId=$connectionInstanceId")
        }
    }

    private fun dropQueues() {
        myContext.queues.clear()
    }

    fun sendListenedCommand() {
        MyServiceManager.Companion.sendCommandIgnoringServiceAvailability(getListenedCommand())
    }

    /** @return true if execution started
     */
    fun assertCommandExecutionStarted(logMsg: String?, count0: Long, expectStarted: TriState): Boolean {
        val method = ("waitForCommandExecutionStart " + getListenedCommand().getTimelineType() + " "
                + logMsg + "; " + getListenedCommand().command.save())
        val criteria = expectStarted.select(
            "check if count > $count0",
            "check for no new execution, count0 = $count0",
            "just waiting, count0 = $count0"
        )
        MyLog.v(this, "$method started, count=$executionStartCount, $criteria")
        var found = false
        var locEvent = "none"
        for (pass in 0..999) {
            if (executionStartCount > count0) {
                found = true
                locEvent = "count: $executionStartCount > $count0"
                break
            }
            if (DbUtils.waitMs(method, 30)) {
                locEvent = "interrupted"
                break
            }
        }
        val logMsgEnd = "$method ended, found=$found, count=$executionStartCount, $criteria; waiting ended on:$locEvent"
        MyLog.v(this, logMsgEnd)
        if (expectStarted != TriState.UNKNOWN) {
            Assert.assertEquals(logMsgEnd, expectStarted.toBoolean(false), found)
        }
        return found
    }

    fun waitForCommandExecutionEnded(count0: Long): Boolean {
        val method = "waitForCommandExecutionEnded"
        var found = false
        var locEvent = "none"
        for (pass in 0..999) {
            if (executionEndCount > count0) {
                found = true
                locEvent = "count: $executionEndCount > $count0"
                break
            }
            if (DbUtils.waitMs(method, 30)) {
                locEvent = "interrupted"
                break
            }
        }
        MyLog.v(
            this, method + " ended " + getListenedCommand().command.save()
                    + " " + found + ", event:" + locEvent + ", count0=" + count0
        )
        return found
    }

    fun stopService(clearQueue: Boolean): Boolean {
        MyServiceManager.stopService()
        return waitForServiceStopped(clearQueue)
    }

    fun waitForServiceStopped(clearQueue: Boolean): Boolean {
        val method = "waitForServiceStopped"
        MyLog.v(this, "$method started")
        var stopped = false
        for (pass in 1..9999) {
            if (serviceStopped) {
                if (clearQueue) {
                    dropQueues()
                }
                stopped = true
                break
            }
            if (DbUtils.waitMs(method, 10)) {
                break
            }
            if (pass % 500 == 0 && MyServiceManager.Companion.getServiceState() == MyServiceState.STOPPED) {
                stopped = true
                break
            }
        }
        MyLog.v(this, method + " ended, " + if (stopped) " stopped" else " didn't stop")
        return stopped
    }

    override fun onReceive(commandData: CommandData, myServiceEvent: MyServiceEvent) {
        var locEvent = "ignored"
        when (myServiceEvent) {
            MyServiceEvent.BEFORE_EXECUTING_COMMAND -> {
                if (commandData == getListenedCommand()) {
                    executionStartCount++
                    locEvent = "execution started"
                }
                serviceStopped = false
            }
            MyServiceEvent.AFTER_EXECUTING_COMMAND -> if (commandData == getListenedCommand()) {
                executionEndCount++
                locEvent = "execution ended"
            }
            MyServiceEvent.ON_STOP -> {
                serviceStopped = true
                locEvent = "service stopped"
            }
            else -> {
            }
        }
        MyLog.v(
            this, "onReceive; " + locEvent + ", " + commandData + ", event:" + myServiceEvent +
                    ", requestsCounter:" + httpConnectionStub?.getRequestsCounter()
        )
    }

    fun tearDown() {
        MyLog.v(this, "tearDown started")
        dropQueues()
        SharedPreferencesUtil.putBoolean(MyPreferences.KEY_SYNC_WHILE_USING_APPLICATION, true)
        serviceConnector?.unregisterReceiver(myContext.context)
        TestSuite.clearHttpStubs()
        TestSuite.getMyContextForTest().connectionState = ConnectionState.UNKNOWN
        MyContextHolder.myContextHolder.getBlocking().accounts.initialize()
        MyContextHolder.myContextHolder.getBlocking().timelines.initialize()
        MyServiceManager.Companion.setServiceAvailable()
        MyLog.v(this, "tearDown ended")
    }

    fun getListenedCommand(): CommandData {
        return listenedCommand
    }

    fun setListenedCommand(listenedCommand: CommandData) {
        this.listenedCommand = listenedCommand
        MyLog.v(this, "setListenedCommand; " + this.listenedCommand)
    }

    fun getHttp(): HttpConnectionStub? {
        return httpConnectionStub
    }
}
