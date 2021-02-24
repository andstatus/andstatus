package org.andstatus.app.service

import org.andstatus.app.account.MyAccountTest
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.context.TestSuite
import org.andstatus.app.data.DbUtils
import org.andstatus.app.net.http.HttpConnectionMock
import org.andstatus.app.net.social.ConnectionMock
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.SharedPreferencesUtil
import org.andstatus.app.util.TriState
import org.junit.Assert
import java.util.function.Supplier

import kotlin.jvm.Volatile

class MyServiceTestHelper : MyServiceEventsListener {
    @Volatile
    private var serviceConnector: MyServiceEventsReceiver? = null

    @Volatile
    private var httpConnectionMock: HttpConnectionMock? = null

    @Volatile
    var connectionInstanceId: Long = 0

    @Volatile
    private var listenedCommand: CommandData? = CommandData.Companion.EMPTY

    @Volatile
    var executionStartCount: Long = 0

    @Volatile
    var executionEndCount: Long = 0

    @Volatile
    var serviceStopped = false
    private var myContext: MyContext? =  MyContextHolder.myContextHolder.getNow()
    fun setUp(accountName: String?) {
        MyLog.i(this, "setUp started")
        try {
            MyServiceManager.Companion.setServiceUnavailable()
            MyServiceManager.Companion.stopService()
            MyAccountTest.Companion.fixPersistentAccounts(myContext)
            val isSingleMockedInstance = accountName.isNullOrEmpty()
            if (isSingleMockedInstance) {
                httpConnectionMock = HttpConnectionMock()
                TestSuite.setHttpConnectionMockInstance(httpConnectionMock)
                 MyContextHolder.myContextHolder.getBlocking().setExpired(Supplier { this.javaClass.simpleName + " setUp" })
            }
            myContext =  MyContextHolder.myContextHolder.initialize(myContext.context(), this).getBlocking()
            if (!myContext.isReady()) {
                val msg = "Context is not ready after the initialization, repeating... $myContext"
                MyLog.w(this, msg)
                myContext.setExpired(Supplier { this.javaClass.simpleName + msg })
                myContext =  MyContextHolder.myContextHolder.initialize(myContext.context(), this).getBlocking()
                Assert.assertEquals("Context should be ready", true, myContext.isReady())
            }
            MyServiceManager.Companion.setServiceUnavailable()
            MyServiceManager.Companion.stopService()
            TestSuite.getMyContextForTest().connectionState = ConnectionState.WIFI
            if (!isSingleMockedInstance) {
                httpConnectionMock = ConnectionMock.Companion.newFor(accountName).getHttpMock()
            }
            connectionInstanceId = httpConnectionMock.getInstanceId()
            serviceConnector = MyServiceEventsReceiver(myContext, this)
            serviceConnector.registerReceiver(myContext.context())
            dropQueues()
            httpConnectionMock.clearPostedData()
            Assert.assertTrue(TestSuite.setAndWaitForIsInForeground(false))
        } catch (e: Exception) {
            MyLog.e(this, "setUp", e)
            Assert.fail(MyLog.getStackTrace(e))
        } finally {
            MyLog.i(this, "setUp ended instanceId=$connectionInstanceId")
        }
    }

    private fun dropQueues() {
        myContext.queues().clear()
    }

    fun sendListenedCommand() {
        MyServiceManager.Companion.sendCommandIgnoringServiceAvailability(getListenedCommand())
    }

    /** @return true if execution started
     */
    fun assertCommandExecutionStarted(logMsg: String?, count0: Long, expectStarted: TriState?): Boolean {
        val method = ("waitForCommandExecutionStart " + getListenedCommand().getTimelineType() + " "
                + logMsg + "; " + getListenedCommand().getCommand().save())
        val criteria = expectStarted.select(
                "check if count > $count0",
                "check for no new execution, count0 = $count0",
                "just waiting, count0 = $count0")
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
        val logMsgEnd = "$method ended, found=$found, count=$executionStartCount, $criteria"
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
        MyLog.v(this, method + " ended " + getListenedCommand().getCommand().save()
                + " " + found + ", event:" + locEvent + ", count0=" + count0)
        return found
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

    override fun onReceive(commandData: CommandData?, myServiceEvent: MyServiceEvent?) {
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
        MyLog.v(this, "onReceive; " + locEvent + ", " + commandData + ", event:" + myServiceEvent + ", requestsCounter:" + httpConnectionMock.getRequestsCounter())
    }

    fun tearDown() {
        MyLog.v(this, "tearDown started")
        dropQueues()
        SharedPreferencesUtil.putBoolean(MyPreferences.KEY_SYNC_WHILE_USING_APPLICATION, true)
        if (serviceConnector != null) {
            serviceConnector.unregisterReceiver(myContext.context())
        }
        TestSuite.clearHttpMocks()
        TestSuite.getMyContextForTest().connectionState = ConnectionState.UNKNOWN
         MyContextHolder.myContextHolder.getBlocking().accounts().initialize()
         MyContextHolder.myContextHolder.getBlocking().timelines().initialize()
        MyServiceManager.Companion.setServiceAvailable()
        MyLog.v(this, "tearDown ended")
    }

    fun getListenedCommand(): CommandData? {
        return listenedCommand
    }

    fun setListenedCommand(listenedCommand: CommandData?) {
        this.listenedCommand = listenedCommand
        MyLog.v(this, "setListenedCommand; " + this.listenedCommand)
    }

    fun getHttp(): HttpConnectionMock? {
        return httpConnectionMock
    }
}