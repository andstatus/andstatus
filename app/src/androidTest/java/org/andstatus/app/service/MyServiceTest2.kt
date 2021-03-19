/*
 * Copyright (C) 2014-2018 yvolk (Yuri Volkov), http://yurivolkov.com
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

import org.andstatus.app.context.DemoData
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.context.TestSuite
import org.andstatus.app.service.CommandData
import org.andstatus.app.service.CommandEnum
import org.andstatus.app.service.CommandQueue.OneQueue
import org.andstatus.app.service.QueueType
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.SharedPreferencesUtil
import org.andstatus.app.util.TriState
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Assert
import org.junit.Test
import java.util.*

class MyServiceTest2 : MyServiceTest() {
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
        mService.assertCommandExecutionStarted("First command should start", startCount, TriState.TRUE)
        Assert.assertTrue("First command should end execution", mService.waitForCommandExecutionEnded(endCount))
        Assert.assertEquals(cd1Home.toString() + " " + mService.http.toString(),
                1, mService.http.requestsCounter.toLong())
        Assert.assertTrue(TestSuite.setAndWaitForIsInForeground(true))
        MyLog.i(this, "$method; we are in a foreground")
        val cd2Interactions: CommandData = CommandData.Companion.newTimelineCommand(CommandEnum.GET_TIMELINE,
                DemoData.demoData.getMyAccount(DemoData.demoData.twitterTestAccountName),
                TimelineType.INTERACTIONS)
        mService.setListenedCommand(cd2Interactions)
        startCount = mService.executionStartCount
        MyLog.i(this, "$method Sending second command")
        mService.sendListenedCommand()
        mService.assertCommandExecutionStarted("Second command shouldn't start", startCount, TriState.FALSE)
        MyLog.i(this, "$method; After waiting for the second command")
        Assert.assertTrue("Service should stop", mService.waitForServiceStopped(false))
        MyLog.i(this, "$method; Service stopped after the second command")
        Assert.assertEquals("No new data should be posted while in foreground",
                1, mService.http.requestsCounter.toLong())
        val queues: CommandQueue =  MyContextHolder.myContextHolder.getBlocking().queues()
        MyLog.i(this, "$method; Queues1:$queues")
        Assert.assertEquals("First command shouldn't be in any queue $queues",
                Optional.empty<Any?>(), queues.inWhichQueue(cd1Home).map { q: OneQueue? -> q.queueType })
        MatcherAssert.assertThat("Second command should be in the Main or Skip queue $queues",
                queues.inWhichQueue(cd2Interactions).map { q: OneQueue? -> q.queueType },
                Matchers.`is`(Matchers.`in`<Optional<QueueType>>(Arrays.asList(Optional.of(QueueType.CURRENT), Optional.of(QueueType.SKIPPED)))))
        val cd3PublicForeground: CommandData = CommandData.Companion.newTimelineCommand(CommandEnum.GET_TIMELINE,
                DemoData.demoData.getMyAccount(DemoData.demoData.twitterTestAccountName),
                TimelineType.PUBLIC)
                .setInForeground(true)
        mService.setListenedCommand(cd3PublicForeground)
        startCount = mService.executionStartCount
        endCount = mService.executionEndCount
        MyLog.i(this, "$method Sending third command")
        mService.sendListenedCommand()
        mService.assertCommandExecutionStarted("Third (foreground) command", startCount, TriState.TRUE)
        Assert.assertTrue("Foreground command ended executing", mService.waitForCommandExecutionEnded(endCount))
        Assert.assertTrue("Service stopped", mService.waitForServiceStopped(false))
        MyLog.i(this, "$method; Queues2:$queues")
        Assert.assertEquals("Third command shouldn't be in any queue $queues",
                Optional.empty<Any?>(), queues.inWhichQueue(cd3PublicForeground).map { q: OneQueue? -> q.queueType })
        MatcherAssert.assertThat("Second command should be in the Main or Skip queue $queues",
                queues.inWhichQueue(cd2Interactions).map { q: OneQueue? -> q.queueType },
                Matchers.`is`(Matchers.`in`<Optional<QueueType>>(Arrays.asList(Optional.of(QueueType.CURRENT), Optional.of(QueueType.SKIPPED)))))
        val cd2FromQueue = queues.getFromAnyQueue(cd2Interactions)
        Assert.assertEquals("command id $cd2FromQueue", cd2Interactions.getCommandId(), cd2FromQueue.getCommandId())
        Assert.assertTrue("command id $cd2FromQueue", cd2FromQueue.getCommandId() >= 0)
        MyLog.i(this, "$method ended")
         MyContextHolder.myContextHolder.getNow().queues().clear()
    }
}