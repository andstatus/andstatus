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
package org.andstatus.app.service

import android.content.SyncResult
import android.database.sqlite.SQLiteDiskIOException
import org.andstatus.app.account.DemoAccountInserter
import org.andstatus.app.account.MyAccount
import org.andstatus.app.context.DemoData
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.data.DbUtils
import org.andstatus.app.net.social.Actor
import org.andstatus.app.origin.Origin
import org.andstatus.app.os.ExceptionsCounter
import org.andstatus.app.timeline.meta.Timeline
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.TriState
import org.junit.Assert
import org.junit.Test

class MyServiceTest1 : MyServiceTest() {
    @Test
    fun testAccountSync() {
        val method = "testAccountSync"
        MyLog.i(this, "$method started")
        val myAccount: MyAccount =  MyContextHolder.myContextHolder.getNow().accounts().getFirstSucceeded()
        Assert.assertTrue("No successful account", myAccount.isValidAndSucceeded())
        val myContext: MyContext =  MyContextHolder.myContextHolder.getNow()
        myContext.timelines().filter(false, TriState.FALSE,
                TimelineType.UNKNOWN, Actor.EMPTY,  Origin.EMPTY)
                .filter { obj: Timeline -> obj.isSyncedAutomatically() }
                .forEach { timeline: Timeline -> timeline.onSyncEnded(myContext, CommandResult()) }
        var syncResult = SyncResult()
        var runner = MyServiceCommandsRunner(myContext)
        runner.setIgnoreServiceAvailability(true)
        runner.autoSyncAccount(myAccount, syncResult)
        DbUtils.waitMs(this, 5000)
        Assert.assertEquals("Requests were sent while all timelines just synced " +
                runner.toString() + "; " + mService.getHttp().toString(),
                0, mService.getHttp()?.getRequestsCounter()?.toLong())
        val myContext2: MyContext =  MyContextHolder.myContextHolder.getNow()
        val timelineToSync: Timeline = DemoAccountInserter.Companion.getAutomaticallySyncableTimeline(myContext2, myAccount)
        timelineToSync.setSyncSucceededDate(0)
        runner = MyServiceCommandsRunner(myContext2)
        runner.setIgnoreServiceAvailability(true)
        syncResult = SyncResult()
        runner.autoSyncAccount(myAccount, syncResult)
        DbUtils.waitMs(this, 5000)
        Assert.assertEquals("Timeline was not synced: " + timelineToSync + "; " +
                runner.toString() + "; " + mService.getHttp().toString(),
                1, mService.getHttp()?.getRequestsCounter()?.toLong())
        Assert.assertTrue("Service stopped", mService.waitForServiceStopped(true))
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
        mService.assertCommandExecutionStarted("First command", startCount, TriState.TRUE)
        Assert.assertTrue("First command ended executing", mService.waitForCommandExecutionEnded(endCount))
        MyLog.i(this, method + "; " + mService.getHttp().toString())
        Assert.assertEquals("connection instance Id", mService.connectionInstanceId, mService.getHttp()?.getInstanceId())
        Assert.assertEquals(mService.getHttp().toString(), 1, mService.getHttp()?.getRequestsCounter()?.toLong())
        Assert.assertTrue("Service stopped", mService.waitForServiceStopped(true))
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
        mService.assertCommandExecutionStarted("First command", startCount, TriState.TRUE)
        Assert.assertTrue("First command ended executing", mService.waitForCommandExecutionEnded(endCount))
        Assert.assertTrue(mService.getHttp().toString(),
                mService.getHttp()?.getRequestsCounter() ?: 0 > 0)
        Assert.assertTrue("Service stopped", mService.waitForServiceStopped(true))
        Assert.assertEquals("DiskIoException", 0, ExceptionsCounter.getDiskIoExceptionsCount())
        MyLog.i(this, "$method ended")
    }

    @Test
    fun testDiskIoErrorCatching() {
        val method = "testDiskIoErrorCatching"
        MyLog.i(this, "$method started")
        mService.setListenedCommand(CommandData.Companion.newAccountCommand(
                CommandEnum.RATE_LIMIT_STATUS,
                DemoData.demoData.getGnuSocialAccount()))
        mService.getHttp()?.setRuntimeException(SQLiteDiskIOException(method))
        val startCount = mService.executionStartCount
        mService.sendListenedCommand()
        mService.assertCommandExecutionStarted("First command", startCount, TriState.TRUE)
        Assert.assertTrue("Service stopped", mService.waitForServiceStopped(true))
        Assert.assertEquals("No DiskIoException", 1, ExceptionsCounter.getDiskIoExceptionsCount())
        DbUtils.waitMs(method, 3000)
        MyLog.i(this, "$method ended")
    }
}