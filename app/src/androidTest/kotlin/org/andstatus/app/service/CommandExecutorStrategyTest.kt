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
import org.andstatus.app.data.DemoNoteInserter
import org.andstatus.app.data.DownloadStatus
import org.andstatus.app.net.http.ConnectionException
import org.andstatus.app.net.http.ConnectionException.StatusCode
import org.andstatus.app.net.http.HttpConnectionMock
import org.andstatus.app.net.social.AActivity
import org.andstatus.app.net.social.ConnectionMock
import org.andstatus.app.origin.DiscoveredOrigins
import org.andstatus.app.origin.Origin
import org.andstatus.app.timeline.meta.TimelineType
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.io.IOException
import kotlin.properties.Delegates

class CommandExecutorStrategyTest {
    private var mock: ConnectionMock by Delegates.notNull()
    private var httpConnectionMock: HttpConnectionMock by Delegates.notNull()
    private var ma: MyAccount = MyAccount.EMPTY
    @Before
    @Throws(Exception::class)
    fun setUp() {
        TestSuite.initializeWithAccounts(this)
        ma =  MyContextHolder.myContextHolder.getNow().accounts.getFirstPreferablySucceededForOrigin(DemoData.demoData.getGnuSocialOrigin())
        mock = ConnectionMock.newFor(ma)
        httpConnectionMock = mock.getHttpMock()
        Assert.assertTrue(ma.toString(), ma.isValidAndSucceeded())
    }

    @Test
    fun testFetchTimeline() {
        var commandData: CommandData = CommandData.Companion.newTimelineCommand(CommandEnum.GET_TIMELINE, MyAccount.EMPTY, TimelineType.HOME)
        var strategy: CommandExecutorStrategy = CommandExecutorStrategy.Companion.getStrategy(commandData, null)
        Assert.assertEquals(CommandExecutorStrategy::class.java, strategy.javaClass)
        commandData = CommandData.Companion.newTimelineCommand(CommandEnum.GET_TIMELINE, ma, TimelineType.HOME)
        strategy = CommandExecutorStrategy.Companion.getStrategy(commandData, null)
        Assert.assertEquals(TimelineDownloaderOther::class.java, strategy.javaClass)
    }

    @Test
    fun testSearch() {
        val commandData1: CommandData = CommandData.Companion.newSearch(SearchObjects.NOTES,
                 MyContextHolder.myContextHolder.getNow(),  Origin.EMPTY, DemoData.demoData.globalPublicNoteText)
        Assert.assertTrue("${commandData1}; myContext:${commandData1.myContext} ", commandData1.myContext.nonEmpty)
        CommandExecutorStrategy.Companion.getStrategy(commandData1, null)

        val commandData2: CommandData = CommandData.Companion.newSearch(SearchObjects.NOTES,
                 MyContextHolder.myContextHolder.getNow(), ma.origin, DemoData.demoData.globalPublicNoteText)

        val strategy = CommandExecutorStrategy.Companion.getStrategy(commandData2, null)
        Assert.assertEquals(TimelineDownloaderOther::class.java, strategy.javaClass)
        strategy.execute()
        Assert.assertNotNull("Requested " + commandData2 +
                ", results: '" + httpConnectionMock.getResults() + "'",
            httpConnectionMock.getResults()
                .map { it.url?.toExternalForm() ?: "" }
                .find { it.contains(DemoData.demoData.globalPublicNoteText) })
    }

    @Test
    @Throws(IOException::class)
    fun testUpdateDestroyStatus() {
        var commandData = getCommandDataForUnsentNote("1")
        mock.addResponse(org.andstatus.app.tests.R.raw.quitter_update_note_response)
        httpConnectionMock.setSameResponse(true)
        Assert.assertEquals(0, commandData.getResult().getExecutionCount().toLong())
        CommandExecutorStrategy.Companion.executeCommand(commandData, null)
        Assert.assertEquals(1, commandData.getResult().getExecutionCount().toLong())
        Assert.assertEquals(commandData.toString(), (CommandResult.Companion.INITIAL_NUMBER_OF_RETRIES - 1).toLong(), commandData.getResult().getRetriesLeft().toLong())
        Assert.assertFalse(commandData.toString(), commandData.getResult().hasSoftError())
        Assert.assertFalse(commandData.toString(), commandData.getResult().hasHardError())
        val noteId = commandData.getResult().getItemId()
        Assert.assertTrue(noteId != 0L)
        commandData = getCommandDataForUnsentNote("2")
        var errorMessage = "Request was bad"
        httpConnectionMock.setException(ConnectionException(StatusCode.UNKNOWN, errorMessage))
        CommandExecutorStrategy.Companion.executeCommand(commandData, null)
        Assert.assertEquals(1, commandData.getResult().getExecutionCount().toLong())
        Assert.assertEquals((CommandResult.Companion.INITIAL_NUMBER_OF_RETRIES - 1).toLong(), commandData.getResult().getRetriesLeft().toLong())
        Assert.assertTrue(commandData.toString(), commandData.getResult().hasSoftError())
        Assert.assertFalse(commandData.toString(), commandData.getResult().hasHardError())
        Assert.assertTrue(commandData.toString(), commandData.getResult().shouldWeRetry())
        Assert.assertTrue("Error message: '" + commandData.getResult().getMessage() + "' should contain '"
                + errorMessage + "'", commandData.getResult().getMessage().contains(errorMessage))
        httpConnectionMock.setException(null)
        CommandExecutorStrategy.Companion.executeCommand(commandData, null)
        Assert.assertEquals(commandData.toString(), 2, commandData.getResult().getExecutionCount().toLong())
        Assert.assertEquals(commandData.toString(), (CommandResult.Companion.INITIAL_NUMBER_OF_RETRIES - 2).toLong(), commandData.getResult().getRetriesLeft().toLong())
        Assert.assertFalse(commandData.toString(), commandData.getResult().hasSoftError())
        Assert.assertFalse(commandData.toString(), commandData.getResult().hasHardError())
        Assert.assertFalse(commandData.toString(), commandData.getResult().shouldWeRetry())
        errorMessage = "some text"
        httpConnectionMock.setException(ConnectionException(StatusCode.AUTHENTICATION_ERROR, errorMessage))
        CommandExecutorStrategy.Companion.executeCommand(commandData, null)
        Assert.assertEquals(3, commandData.getResult().getExecutionCount().toLong())
        Assert.assertEquals(commandData.toString(), (CommandResult.Companion.INITIAL_NUMBER_OF_RETRIES - 3).toLong(), commandData.getResult().getRetriesLeft().toLong())
        Assert.assertFalse(commandData.toString(), commandData.getResult().hasSoftError())
        Assert.assertTrue(commandData.toString(), commandData.getResult().hasHardError())
        Assert.assertFalse(commandData.toString(), commandData.getResult().shouldWeRetry())
        Assert.assertTrue("Error message: '" + commandData.getResult().getMessage() + "' should contain '"
                + errorMessage + "'", commandData.getResult().getMessage().contains(errorMessage))
        httpConnectionMock.setException(null)
        commandData = CommandData.Companion.newItemCommand(
                CommandEnum.DELETE_NOTE,
                mock.getData().getMyAccount(),
                noteId)
        CommandExecutorStrategy.Companion.executeCommand(commandData, null)
        Assert.assertFalse(commandData.toString(), commandData.getResult().hasError())
        val INEXISTENT_MSG_ID: Long = -1
        commandData = CommandData.Companion.newItemCommand(
                CommandEnum.DELETE_NOTE,
                mock.getData().getMyAccount(),
                INEXISTENT_MSG_ID)
        CommandExecutorStrategy.Companion.executeCommand(commandData, null)
        Assert.assertFalse(commandData.toString(), commandData.getResult().hasError())
        httpConnectionMock.setException(null)
    }

    private fun getCommandDataForUnsentNote(suffix: String?): CommandData {
        val body = "Some text " + suffix + " to send " + System.currentTimeMillis() + "ms"
        val activity: AActivity = DemoNoteInserter.Companion.addNoteForAccount(ma, body, "", DownloadStatus.SENDING)
        return CommandData.Companion.newUpdateStatus(ma, activity.getId(), activity.getNote().noteId)
    }

    @Test
    @Throws(IOException::class)
    fun testDiscoverOrigins() {
        val http = HttpConnectionMock()
        http.addResponse(org.andstatus.app.tests.R.raw.get_open_instances)
        TestSuite.setHttpConnectionMockInstance(http)
        val commandData: CommandData = CommandData.Companion.newOriginCommand(
                CommandEnum.GET_OPEN_INSTANCES,
                DemoData.demoData.getGnuSocialOrigin())
        DiscoveredOrigins.clear()
        CommandExecutorStrategy.Companion.executeCommand(commandData, null)
        Assert.assertEquals(1, commandData.getResult().getExecutionCount().toLong())
        Assert.assertFalse(commandData.getResult().hasError())
        Assert.assertTrue(commandData.getResult().getDownloadedCount() > 0)
        Assert.assertFalse(DiscoveredOrigins.get().isEmpty())
    }

    @After
    fun tearDown() {
        TestSuite.clearHttpMocks()
         MyContextHolder.myContextHolder.getBlocking().accounts.initialize()
    }
}
