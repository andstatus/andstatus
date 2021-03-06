/*
 * Copyright (C) 2014-2021 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.content.SyncResult
import android.database.sqlite.SQLiteDiskIOException
import org.andstatus.app.account.DemoAccountInserter
import org.andstatus.app.account.MyAccount
import org.andstatus.app.context.DemoData
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.context.TestSuite
import org.andstatus.app.data.DbUtils
import org.andstatus.app.data.DemoNoteInserter
import org.andstatus.app.net.social.Actor
import org.andstatus.app.origin.Origin
import org.andstatus.app.os.ExceptionsCounter
import org.andstatus.app.timeline.meta.Timeline
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.IgnoredInTravis2
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.SharedPreferencesUtil
import org.andstatus.app.util.TriState
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.util.*

class MyServiceTests: IgnoredInTravis2() {
    companion object {
        private var myServiceTestHelper: MyServiceTestHelper? = null

        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            var ok = false
            MyLog.i(this, "setUpClass started")
            try {
                TestSuite.initializeWithData(this)
                myServiceTestHelper = MyServiceTestHelper().also {
                    it.setUp(null)
                }
                ok = true
            } finally {
                MyLog.i(this, "setUpClass ended " +
                        (if (ok) "successfully " else "failed") +
                        " instanceId=" + if (myServiceTestHelper == null) "null" else myServiceTestHelper?.connectionInstanceId)
            }
        }

        @AfterClass
        @JvmStatic
        fun tearDownClass() {
            MyLog.i(this, "tearDownClass started" + if (myServiceTestHelper == null) ", mService:null" else "")
            myServiceTestHelper?.tearDown()
            MyLog.i(this, "tearDownClass ended")
        }
    }

    val mService: MyServiceTestHelper get() = myServiceTestHelper ?: throw IllegalStateException("MyServiceTestHelper is null")

    @Volatile
    private var mMa: MyAccount = MyAccount.EMPTY
    val ma: MyAccount get() = mMa

    @Before
    fun setUp() {
        MyLog.i(this, "setUp started")
        TestSuite.waitForIdleSync()
        mMa =  MyContextHolder.myContextHolder.getNow().accounts.getFirstSucceeded().also { myAccount ->
            assertTrue("No successfully verified accounts", myAccount.isValidAndSucceeded())
        }
        MyLog.i(this, "setUp ended")
    }

    @After
    fun tearDown() {
        MyLog.i(this, "tearDown started")
        assertTrue("Service should stop", mService.stopService(true))
        SharedPreferencesUtil.putBoolean(MyPreferences.KEY_SYNC_WHILE_USING_APPLICATION, true)
        ExceptionsCounter.forget()
        mService.getHttp().clearData()
        MyLog.i(this, "tearDown ended")
    }

    @Test
    fun testAccountSync() {
        val method = "testAccountSync"
        MyLog.i(this, "$method started")
        val myAccount: MyAccount =  MyContextHolder.myContextHolder.getNow().accounts.getFirstSucceeded()
        assertTrue("No successful account", myAccount.isValidAndSucceeded())
        val myContext: MyContext =  MyContextHolder.myContextHolder.getNow()
        myContext.timelines.filter(false, TriState.FALSE,
            TimelineType.UNKNOWN, Actor.EMPTY,  Origin.EMPTY)
            .filter { obj: Timeline -> obj.isSyncedAutomatically() }
            .forEach { timeline: Timeline -> timeline.onSyncEnded(myContext, CommandResult()) }
        var syncResult = SyncResult()
        var runner = MyServiceCommandsRunner(myContext)
        runner.setIgnoreServiceAvailability(true)
        runner.autoSyncAccount(myAccount, syncResult)
        DbUtils.waitMs(this, 5000)
        assertEquals("Requests were sent while all timelines just synced " +
                runner.toString() + "; " + mService.getHttp().toString(),
            0, mService.getHttp().getRequestsCounter())

        val myContext2: MyContext =  MyContextHolder.myContextHolder.getNow()
        val timelineToSync: Timeline = DemoAccountInserter.Companion.getAutomaticallySyncableTimeline(myContext2, myAccount)
        timelineToSync.setSyncSucceededDate(0)
        runner = MyServiceCommandsRunner(myContext2)
        runner.setIgnoreServiceAvailability(true)
        syncResult = SyncResult()
        runner.autoSyncAccount(myAccount, syncResult)
        DbUtils.waitMs(this, 5000)
        assertEquals("Timeline was not synced: " + timelineToSync + "; " +
                runner.toString() + "; " + mService.getHttp().toString(),
            1, mService.getHttp().getRequestsCounter())
        MyLog.i(this, "$method ended")
    }

    @Test
    fun testHomeTimeline() {
        val method = "testHomeTimeline"
        MyLog.i(this, "$method started")
        mService.setListenedCommand(CommandData.Companion.newTimelineCommand(
            CommandEnum.GET_TIMELINE, ma, TimelineType.HOME))
        val startCount = mService.executionStartCount
        val endCount = mService.executionEndCount
        mService.sendListenedCommand()
        mService.waitForCommandExecutionStarted("First command", startCount, TriState.TRUE)
        assertTrue("First command ended executing", mService.waitForCommandExecutionEnded(endCount))
        MyLog.i(this, method + "; " + mService.getHttp().toString())
        assertEquals("connection instance Id", mService.connectionInstanceId, mService.getHttp().getInstanceId())
        assertEquals(mService.getHttp().toString(), 1, mService.getHttp().getRequestsCounter())
        MyLog.i(this, "$method ended")
    }

    @Test
    fun testSyncInForeground() {
        val method = "testSyncInForeground"
        MyLog.i(this, "$method started")
        SharedPreferencesUtil.putBoolean(MyPreferences.KEY_SYNC_WHILE_USING_APPLICATION, false)
        val cd1Home: CommandData = CommandData.Companion.newTimelineCommand(CommandEnum.GET_TIMELINE,
            DemoData.demoData.getMyAccount(DemoData.demoData.twitterTestAccountName),
            TimelineType.HOME)
        mService.setListenedCommand(cd1Home)
        var startCount = mService.executionStartCount
        var endCount = mService.executionEndCount
        MyLog.i(this, "$method Sending first command")
        mService.sendListenedCommand()
        mService.waitForCommandExecutionStarted("First command should start", startCount, TriState.TRUE)
        assertTrue("First command should end execution", mService.waitForCommandExecutionEnded(endCount))
        assertEquals(cd1Home.toString() + " " + mService .getHttp().toString(),
            1, mService .getHttp().getRequestsCounter())
        assertTrue(TestSuite.setAndWaitForIsInForeground(true))
        MyLog.i(this, "$method; we are in a foreground")
        val cd2Interactions: CommandData = CommandData.Companion.newTimelineCommand(CommandEnum.GET_TIMELINE,
            DemoData.demoData.getMyAccount(DemoData.demoData.twitterTestAccountName),
            TimelineType.INTERACTIONS)
        mService.setListenedCommand(cd2Interactions)
        startCount = mService.executionStartCount
        MyLog.i(this, "$method Sending second command")
        mService.sendListenedCommand()
        mService.waitForCommandExecutionStarted("Second command shouldn't start", startCount, TriState.FALSE)
        MyLog.i(this, "$method; After waiting for the second command")
        assertTrue("Service should stop", mService.waitForServiceStopped(false))
        MyLog.i(this, "$method; Service stopped after the second command")
        assertEquals("No new data should be posted while in foreground",
            1, mService .getHttp().getRequestsCounter())
        val queues: CommandQueue =  MyContextHolder.myContextHolder.getBlocking().queues
        MyLog.i(this, "$method; Queues1:$queues")
        assertEquals("First command shouldn't be in any queue $queues",
            Optional.empty<Any?>(), queues.inWhichQueue(cd1Home).map { q: CommandQueue.OneQueue -> q.queueType })
        MatcherAssert.assertThat("Second command should be in the Main or Skip queue $queues",
            queues.inWhichQueue(cd2Interactions).map { q: CommandQueue.OneQueue -> q.queueType },
            Matchers.`is`(Matchers.`in`<Optional<QueueType>>(listOf(Optional.of(QueueType.CURRENT), Optional.of(QueueType.SKIPPED)))))
        val cd3PublicForeground: CommandData = CommandData.Companion.newTimelineCommand(CommandEnum.GET_TIMELINE,
            DemoData.demoData.getMyAccount(DemoData.demoData.twitterTestAccountName),
            TimelineType.PUBLIC)
            .setInForeground(true)
        mService.setListenedCommand(cd3PublicForeground)
        startCount = mService.executionStartCount
        endCount = mService.executionEndCount
        MyLog.i(this, "$method Sending third command")
        mService.sendListenedCommand()
        mService.waitForCommandExecutionStarted("Third (foreground) command", startCount, TriState.TRUE)
        assertTrue("Foreground command ended executing", mService.waitForCommandExecutionEnded(endCount))
        assertTrue("Service stopped", mService.waitForServiceStopped(false))
        MyLog.i(this, "$method; Queues2:$queues")
        assertEquals("Third command shouldn't be in any queue $queues",
            Optional.empty<Any?>(), queues.inWhichQueue(cd3PublicForeground).map { q: CommandQueue.OneQueue -> q.queueType })
        MatcherAssert.assertThat("Second command should be in the Main or Skip queue $queues",
            queues.inWhichQueue(cd2Interactions).map { q: CommandQueue.OneQueue -> q.queueType },
            Matchers.`is`(Matchers.`in`<Optional<QueueType>>(listOf(Optional.of(QueueType.CURRENT), Optional.of(QueueType.SKIPPED)))))
        val cd2FromQueue = queues.getFromAnyQueue(cd2Interactions)
        assertEquals("command id $cd2FromQueue", cd2Interactions.getCommandId(), cd2FromQueue.getCommandId())
        assertTrue("command id $cd2FromQueue", cd2FromQueue.getCommandId() >= 0)
        MyLog.i(this, "$method ended")
    }

    @Test
    fun testRateLimitStatus() {
        val method = "testRateLimitStatus"
        MyLog.i(this, "$method started")
        mService.setListenedCommand(CommandData.Companion.newAccountCommand(
            CommandEnum.RATE_LIMIT_STATUS,
            DemoData.demoData.getGnuSocialAccount()))
        val startCount = mService.executionStartCount
        val endCount = mService.executionEndCount
        mService.sendListenedCommand()
        mService.waitForCommandExecutionStarted("First command", startCount, TriState.TRUE)
        assertTrue("First command ended executing", mService.waitForCommandExecutionEnded(endCount))
        assertTrue(mService.getHttp().toString(),
            mService.getHttp().getRequestsCounter() > 0)
        assertTrue("Service should stop", mService.stopService(true))
        assertEquals("DiskIoException", 0, ExceptionsCounter.getDiskIoExceptionsCount())
        MyLog.i(this, "$method ended")
    }

    @Test
    fun testDiskIoErrorCatching() {
        val method = "testDiskIoErrorCatching"
        MyLog.i(this, "$method started")
        mService.setListenedCommand(
            CommandData.Companion.newAccountCommand(
                CommandEnum.RATE_LIMIT_STATUS,
                DemoData.demoData.getGnuSocialAccount()
            )
        )
        mService.getHttp().setRuntimeException(SQLiteDiskIOException(method))
        val startCount = mService.executionStartCount
        mService.sendListenedCommand()
        mService.waitForCommandExecutionStarted("First command", startCount, TriState.TRUE)
        assertTrue("Service should stop", mService.stopService(true))
        assertEquals("No DiskIoException", 1, ExceptionsCounter.getDiskIoExceptionsCount())
        MyLog.i(this, "$method ended")
    }

    @Test
    fun testRepeatingFailingCommand() {
        repeatingFailingCommandOne(1)
        repeatingFailingCommandOne(2)
    }

    private fun repeatingFailingCommandOne(iteration: Int) {
        val method = "repeatingFailingCommand$iteration"
        MyLog.i(this, "$method started")
        val inserter = DemoNoteInserter(ma)
        val actor = inserter.buildActor()
        inserter.onActivity(ma.actor.update(actor))
        val urlString = "http://andstatus.org/nonexistent2_avatar_" + System.currentTimeMillis() + ".png"
        AvatarDownloaderTest.changeAvatarUrl(actor, urlString)
        val startCount = mService.executionStartCount
        val endCount = mService.executionEndCount
        val requestsCounter0 = mService.getHttp().getRequestsCounter()
        setAndSendGetAvatarCommand(actor, false)
        mService.waitForCommandExecutionStarted("First command $actor", startCount, TriState.TRUE)
        setAndSendGetAvatarCommand(actor, false)
        assertTrue("First command didn't end $actor", mService.waitForCommandExecutionEnded(endCount))
        assertTrue("Request for the command wasn't sent:" +
                " ${mService.getListenedCommand()}\n${mService.getHttp()}",
            mService.getHttp().getRequestsCounter() > requestsCounter0)
        setAndSendGetAvatarCommand(actor, false)
        mService.waitForCommandExecutionStarted("Duplicated command started ${mService.getListenedCommand()}\n$actor",
            startCount + 1, TriState.FALSE)
        setAndSendGetAvatarCommand(actor, true)
        mService.waitForCommandExecutionStarted("Manually launched duplicated command didn't start" +
                " ${mService.getListenedCommand()}\n$actor", startCount + 1, TriState.TRUE)
        assertTrue("The third command didn't end ${mService.getListenedCommand()}\n$actor",
            mService.waitForCommandExecutionEnded(endCount + 1))
        MyLog.i(this, "$method ended, $actor")
    }

    // We need to generate new command in order to have new unique ID for it. This is how it works in app itself
    private fun setAndSendGetAvatarCommand(actor: Actor, manuallyLaunched: Boolean) {
        val command: CommandData = CommandData.Companion.newActorCommand(CommandEnum.GET_AVATAR, actor, "")
        if (manuallyLaunched) {
            command.setManuallyLaunched(true)
        }
        mService.setListenedCommand(command)
        mService.sendListenedCommand()
    }

}
