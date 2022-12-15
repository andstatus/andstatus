/*
 * Copyright (C) 2021 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.service

import org.andstatus.app.account.MyAccountTest
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.context.TestSuite
import org.andstatus.app.data.DbUtils
import org.andstatus.app.net.http.HttpConnectionOAuthStub
import org.andstatus.app.net.social.ConnectionStub
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.SharedPreferencesUtil
import org.andstatus.app.util.StopWatch
import org.andstatus.app.util.TriState
import org.junit.Assert

class MyServiceTestHelper : MyServiceEventsListener {
    @Volatile
    private var serviceConnector: MyServiceEventsReceiver? = null

    @Volatile
    private var httpConnectionStub: HttpConnectionOAuthStub? = null

    @Volatile
    var connectionInstanceId: Long = 0

    private val commandMonitor = CommandMonitor()

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
                httpConnectionStub = HttpConnectionOAuthStub()
                TestSuite.setHttpConnectionStubInstance(httpConnectionStub)
                MyContextHolder.myContextHolder.getBlocking().setExpired { this::class.simpleName + " setUp" }
            }
            myContext = MyContextHolder.myContextHolder.initialize(myContext.context, this).getBlocking()
            if (!myContext.isReady) {
                val msg = "Context is not ready after the initialization, repeating... $myContext"
                MyLog.w(this, msg)
                myContext.setExpired { this::class.simpleName + msg }
                myContext = MyContextHolder.myContextHolder.initialize(myContext.context, this).getBlocking()
                Assert.assertEquals("Context should be ready", true, myContext.isReady)
            }
            MyServiceManager.setServiceUnavailable()
            Assert.assertTrue("Couldn't stop MyService", stopService(false))
            TestSuite.getMyContextForTest().connectionState = ConnectionState.WIFI
            if (!isSingleStubbedInstance) {
                httpConnectionStub = ConnectionStub.newFor(accountName).getHttpStub()
            }
            connectionInstanceId = httpConnectionStub?.getInstanceId() ?: 0
            serviceConnector = MyServiceEventsReceiver(myContext, this).also {
                it.registerReceiver(myContext.context)
            }
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

    fun sendCommand(command: CommandData, onSending: (CommandCounter) -> String): CommandCounter =
        commandMonitor.setCommand(command).also {
            MyLog.i(this, onSending(it))
            MyServiceManager.Companion.sendCommandIgnoringServiceAvailability(it.command)
        }

    /** @return true if execution started
     */
    fun waitForCommandStart(logMsg: String?, count0: CommandCounter, expectStarted: TriState): Boolean {
        val method = this::waitForCommandStart.name
        val stopWatch: StopWatch = StopWatch.createStarted()
        val criteria = expectStarted.select(
            "check if count > ${count0.startCount}",
            "check for no new execution, count0 = ${count0.startCount}",
            "just waiting, count0 = ${count0.startCount}"
        )
        MyLog.v(this, "$method; started. $logMsg count=${commandMonitor.startCount}, $criteria, ${commandMonitor.command}")
        var found = false
        var locEvent = "none"
        for (pass in 0..999) {
            if (commandMonitor.command != count0.command) {
                locEvent = "other command is monitored"
                break
            }
            if (commandMonitor.startCount > count0.startCount) {
                found = true
                locEvent = "count: ${commandMonitor.startCount} > ${count0.startCount}"
                break
            }
            if (DbUtils.waitMs(method, 30)) {
                locEvent = "interrupted"
                break
            }
        }
        val logMsgEnd = "$method; ended, found:$found; $logMsg; count:${commandMonitor.startCount}, $criteria; " +
            "waiting ended on:$locEvent, ${stopWatch.time} ms, ${commandMonitor.command}"
        MyLog.v(this, logMsgEnd)
        if (expectStarted != TriState.UNKNOWN) {
            Assert.assertEquals(logMsgEnd, expectStarted.toBoolean(false), found)
        }
        return found
    }

    fun waitForCommandEnd(count0: CommandCounter): Boolean {
        val method = this::waitForCommandEnd.name
        val stopWatch: StopWatch = StopWatch.createStarted()
        MyLog.v(this, "$method; started. count:${commandMonitor.endCount}")
        var found = false
        var locEvent = "none, count:${commandMonitor.endCount}, count0:${count0.endCount}"
        for (pass in 0..999) {
            if (commandMonitor.command != count0.command) {
                locEvent = "other command is monitored"
                break
            }
            if (commandMonitor.endCount > count0.endCount) {
                found = true
                locEvent = "count: ${commandMonitor.endCount} > ${count0.endCount}"
                break
            }
            if (DbUtils.waitMs(method, 30)) {
                locEvent = "interrupted"
                break
            }
        }
        MyLog.v(
            this, "$method; ended." +
                " found:" + found + ", event:" + locEvent +
                ", ${stopWatch.time} ms, " + commandMonitor.command
        )
        return found
    }

    fun waitForCondition(predicate: MyServiceTestHelper.() -> Boolean): Boolean {
        val method = "waitForCondition"
        val stopWatch: StopWatch = StopWatch.createStarted()
        var found = false
        var locEvent = "none"
        for (pass in 0..999) {
            if (predicate(this)) {
                found = true
                locEvent = "matched"
                break
            }
            if (DbUtils.waitMs(method, 30)) {
                locEvent = "interrupted"
                break
            }
        }
        MyLog.v(this, "$method ended, matched:$found, event:$locEvent, ${stopWatch.time} ms")
        return found
    }

    fun stopService(clearQueue: Boolean): Boolean {
        MyServiceManager.stopService()
        return waitForServiceStop(clearQueue)
    }

    fun waitForServiceStop(clearQueue: Boolean): Boolean {
        val method = "waitForServiceStopped"
        val stopWatch: StopWatch = StopWatch.createStarted()
        MyLog.v(this, "$method started")
        var stopped = false
        var prevCheckTime = 0L
        do {
            if (serviceStopped) {
                if (clearQueue) {
                    dropQueues()
                }
                stopped = true
                break
            }
            if (DbUtils.waitMs(method, 100)) break
            if (stopWatch.time > prevCheckTime) {
                prevCheckTime += 1000
                if (MyServiceManager.getServiceState() == MyServiceState.STOPPED) {
                    stopped = true
                    break
                }
            }
        } while (stopWatch.notPassedSeconds(130)) // TODO: fix org.andstatus.app.net.http.MyHttpClientFactory to decrease this
        MyLog.v(this, method + " ended, " + (if (stopped) "stopped" else "didn't stop") +
                ", ${stopWatch.time} ms")
        return stopped
    }

    override fun onReceive(commandData: CommandData, myServiceEvent: MyServiceEvent) {
        when (myServiceEvent) {
            MyServiceEvent.BEFORE_EXECUTING_COMMAND -> {
                commandMonitor.onStart(commandData)
                serviceStopped = false
            }
            MyServiceEvent.AFTER_EXECUTING_COMMAND -> commandMonitor.onEnd(commandData)
            MyServiceEvent.ON_STOP -> serviceStopped = true
            else -> {
            }
        }
        MyLog.v(this, "onReceive; $myServiceEvent, $commandData")
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

    fun getHttp(): HttpConnectionOAuthStub {
        return httpConnectionStub ?: throw IllegalStateException("No httpConnectionStub")
    }
}
