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

import org.andstatus.app.SearchObjects
import org.andstatus.app.account.MyAccount
import org.andstatus.app.context.DemoData
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.TestSuite
import org.andstatus.app.data.DbUtils
import org.andstatus.app.data.MyQuery
import org.andstatus.app.data.OidEnum
import org.andstatus.app.net.social.Actor
import org.andstatus.app.origin.Origin
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.MyLog
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.*
import java.util.concurrent.PriorityBlockingQueue

class CommandDataTest {
    @Before
    @Throws(Exception::class)
    fun setUp() {
        TestSuite.initializeWithData(this)
    }

    @Test
    fun testQueue() {
        val time0 = System.currentTimeMillis()
        var commandData: CommandData = CommandData.Companion.newUpdateStatus(DemoData.demoData.getPumpioConversationAccount(), 1, 4)
        testQueueOneCommandData(commandData, time0)
        val noteId = MyQuery.oidToId(OidEnum.NOTE_OID,  MyContextHolder.myContextHolder.getNow().origins
                .fromName(DemoData.demoData.conversationOriginName).id,
                DemoData.demoData.conversationEntryNoteOid)
        val downloadDataRowId: Long = 23
        commandData = CommandData.Companion.newFetchAttachment(noteId, downloadDataRowId)
        testQueueOneCommandData(commandData, time0)
    }

    private fun testQueueOneCommandData(commandData: CommandData, time0: Long) {
        val method = "testQueueOneCommandData"
        Assert.assertEquals(0, commandData.getResult().getExecutionCount().toLong())
        Assert.assertEquals(0, commandData.getResult().getLastExecutedDate())
        Assert.assertEquals(CommandResult.Companion.INITIAL_NUMBER_OF_RETRIES.toLong(), commandData.getResult().getRetriesLeft().toLong())
        commandData.getResult().prepareForLaunch()
        val hasSoftError = true
        commandData.getResult().incrementNumIoExceptions()
        commandData.getResult().setMessage("Error in " + commandData.hashCode())
        commandData.getResult().afterExecutionEnded()
        DbUtils.waitMs(method, 50)
        val time1 = System.currentTimeMillis()
        Assert.assertTrue(commandData.getResult().getLastExecutedDate() >= time0)
        Assert.assertTrue(commandData.getResult().getLastExecutedDate() < time1)
        Assert.assertEquals(1, commandData.getResult().getExecutionCount().toLong())
        Assert.assertEquals((CommandResult.Companion.INITIAL_NUMBER_OF_RETRIES - 1).toLong(), commandData.getResult().getRetriesLeft().toLong())
        Assert.assertEquals(hasSoftError, commandData.getResult().hasSoftError())
        Assert.assertFalse(commandData.getResult().hasHardError())
        val queues: CommandQueue =  MyContextHolder.myContextHolder.getNow().queues
        queues.clear()
        queues[QueueType.ERROR].addToQueue(commandData)
        queues.save()
        Assert.assertEquals(1, queues[QueueType.ERROR].size().toLong())
        queues.load()
        Assert.assertEquals(1, queues[QueueType.ERROR].size().toLong())
        Assert.assertEquals(commandData, queues.getFromQueue(QueueType.ERROR, commandData))
        val commandData2 = queues[QueueType.ERROR].queue.poll() ?: throw IllegalStateException("No data")
        Assert.assertEquals(commandData, commandData2)
        // Below fields are not included in equals
        Assert.assertEquals(commandData.getCommandId(), commandData2.getCommandId())
        Assert.assertEquals(commandData.getCreatedDate(), commandData2.getCreatedDate())
        Assert.assertEquals(commandData.getResult().getLastExecutedDate(), commandData2.getResult().getLastExecutedDate())
        Assert.assertEquals(commandData.getResult().getExecutionCount().toLong(), commandData2.getResult().getExecutionCount().toLong())
        Assert.assertEquals(commandData.getResult().getRetriesLeft().toLong(), commandData2.getResult().getRetriesLeft().toLong())
        Assert.assertEquals(commandData.getResult().hasError(), commandData2.getResult().hasError())
        Assert.assertEquals(commandData.getResult().hasSoftError(), commandData2.getResult().hasSoftError())
        Assert.assertEquals(commandData.getResult().getMessage(), commandData2.getResult().getMessage())
    }

    @Test
    fun testEquals() {
        val data1: CommandData = CommandData.Companion.newSearch(SearchObjects.NOTES,  MyContextHolder.myContextHolder.getNow(),  Origin.EMPTY, "andstatus")
        val data2: CommandData = CommandData.Companion.newSearch(SearchObjects.NOTES,  MyContextHolder.myContextHolder.getNow(),  Origin.EMPTY, "mustard")
        Assert.assertTrue("Hashcodes: " + data1.hashCode() + " and " + data2.hashCode(), data1.hashCode() != data2.hashCode())
        Assert.assertFalse(data1 == data2)
        data1.getResult().prepareForLaunch()
        data1.getResult().incrementNumIoExceptions()
        data1.getResult().afterExecutionEnded()
        Assert.assertFalse(data1.getResult().shouldWeRetry())
        val data3: CommandData = CommandData.Companion.newSearch(SearchObjects.NOTES,  MyContextHolder.myContextHolder.getNow(),  Origin.EMPTY, "andstatus")
        Assert.assertTrue(data1 == data3)
        Assert.assertTrue(data1.hashCode() == data3.hashCode())
        Assert.assertEquals(data1, data3)
    }

    @Test
    fun testPriority() {
        val queue: Queue<CommandData> = PriorityBlockingQueue(100)
        val ma: MyAccount = DemoData.demoData.getGnuSocialAccount()
        queue.add(CommandData.Companion.newActorCommand(CommandEnum.GET_FRIENDS, Actor.Companion.fromId(ma.origin, 123), ""))
        queue.add(CommandData.Companion.newActorCommand(CommandEnum.GET_TIMELINE, ma.actor, ma.username))
        queue.add(CommandData.Companion.newSearch(SearchObjects.NOTES,  MyContextHolder.myContextHolder.getNow(), ma.origin, "q1"))
        queue.add(CommandData.Companion.newUpdateStatus(MyAccount.EMPTY, 2, 5))
        queue.add(CommandData.Companion.newTimelineCommand(CommandEnum.GET_TIMELINE, ma, TimelineType.INTERACTIONS))
        queue.add(CommandData.Companion.newUpdateStatus(MyAccount.EMPTY, 3, 6))
        queue.add(CommandData.Companion.newTimelineCommand(CommandEnum.GET_TIMELINE, ma, TimelineType.HOME).setInForeground(true))
        queue.add(CommandData.Companion.newItemCommand(CommandEnum.GET_NOTE, ma, 7823))
        assertCommand(queue, CommandEnum.GET_TIMELINE, TimelineType.HOME)
        assertCommand(queue, CommandEnum.UPDATE_NOTE)
        assertCommand(queue, CommandEnum.UPDATE_NOTE)
        assertCommand(queue, CommandEnum.GET_FRIENDS)
        assertCommand(queue, CommandEnum.GET_NOTE)
        assertCommand(queue, CommandEnum.GET_TIMELINE, TimelineType.SENT)
        assertCommand(queue, CommandEnum.GET_TIMELINE, TimelineType.SEARCH)
        assertCommand(queue, CommandEnum.GET_TIMELINE, TimelineType.INTERACTIONS)
    }

    private fun assertCommand(queue: Queue<CommandData>, commandEnum: CommandEnum, timelineType: TimelineType = TimelineType.UNKNOWN) {
        val commandData = queue.poll() ?: throw IllegalStateException("No data")
        Assert.assertEquals(commandData.toString(), commandEnum, commandData.command)
        if (timelineType != TimelineType.UNKNOWN) {
            Assert.assertEquals(commandData.toString(), timelineType, commandData.getTimelineType())
        }
    }

    @Test
    fun testSummary() {
        followUnfollowSummary(CommandEnum.FOLLOW)
        followUnfollowSummary(CommandEnum.UNDO_FOLLOW)
    }

    private fun followUnfollowSummary(command: CommandEnum) {
        val ma: MyAccount = DemoData.demoData.getMyAccount(DemoData.demoData.conversationAccountName)
        Assert.assertTrue(ma.isValid)
        val actorId = MyQuery.oidToId(OidEnum.ACTOR_OID, ma.origin.id,
                DemoData.demoData.conversationAuthorThirdActorOid)
        val actor: Actor = Actor.Companion.load(ma.myContext, actorId)
        val data: CommandData = CommandData.Companion.actOnActorCommand(
                command, DemoData.demoData.getPumpioConversationAccount(), actor, "")
        val summary = data.toCommandSummary( MyContextHolder.myContextHolder.getNow())
        val msgLog = command.name + "; Summary:'" + summary + "'"
        MyLog.v(this, msgLog)
        MatcherAssert.assertThat(msgLog, summary, CoreMatchers.containsString(command.getTitle( MyContextHolder.myContextHolder.getNow(),
                ma.getAccountName()).toString() + " " + MyQuery.actorIdToWebfingerId( MyContextHolder.myContextHolder.getNow(), actorId)))
    }
}
