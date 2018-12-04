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

package org.andstatus.app.service;

import org.andstatus.app.SearchObjects;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.DemoNoteInserter;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.http.ConnectionException.StatusCode;
import org.andstatus.app.net.http.HttpConnectionMock;
import org.andstatus.app.net.social.AActivity;
import org.andstatus.app.net.social.ConnectionMockable;
import org.andstatus.app.origin.DiscoveredOrigins;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.timeline.meta.TimelineType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;

import static org.andstatus.app.context.DemoData.demoData;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CommandExecutorStrategyTest {
    private HttpConnectionMock httpConnectionMock;
    private MyAccount ma;

    @Before
    public void setUp() throws Exception {
        TestSuite.initializeWithData(this);

        TestSuite.setHttpConnectionMockClass(HttpConnectionMock.class);
        // In order for the the mocked connection to have effect:
        MyContextHolder.get().accounts().initialize();
        MyContextHolder.get().timelines().initialize();
        ma = MyContextHolder.get().accounts().getFirstSucceededForOrigin(
                MyContextHolder.get().origins().fromName(demoData.gnusocialTestOriginName));
        assertTrue(ma.toString(), ma.isValidAndSucceeded());
        httpConnectionMock = ConnectionMockable.getHttpMock(ma);
    }

    @Test
    public void testFetchTimeline() {
        CommandData commandData = CommandData.newTimelineCommand(CommandEnum.GET_TIMELINE, MyAccount.EMPTY, TimelineType.HOME);
        CommandExecutorStrategy strategy = CommandExecutorStrategy.getStrategy(commandData, null);
        assertEquals(CommandExecutorStrategy.class, strategy.getClass());

        commandData = CommandData.newTimelineCommand(CommandEnum.GET_TIMELINE, ma, TimelineType.HOME);
        strategy = CommandExecutorStrategy.getStrategy(commandData, null);
        assertEquals(TimelineDownloaderOther.class, strategy.getClass());
    }

    @Test
    public void testSearch() {
        CommandData commandData = CommandData.newSearch(SearchObjects.NOTES,
                MyContextHolder.get(), Origin.EMPTY, demoData.globalPublicNoteText);
        CommandExecutorStrategy strategy = CommandExecutorStrategy.getStrategy(commandData, null);
        assertEquals(CommandExecutorStrategy.class, strategy.getClass());

        commandData = CommandData.newSearch(SearchObjects.NOTES,
                MyContextHolder.get(), ma.getOrigin(), demoData.globalPublicNoteText);
        strategy = CommandExecutorStrategy.getStrategy(commandData, null);
        assertEquals(TimelineDownloaderOther.class, strategy.getClass());
        strategy.execute();
        assertTrue("Requested '" + Arrays.toString(httpConnectionMock.getResults().toArray()) + "'",
                httpConnectionMock.getResults().get(0).getUrl().contains(demoData.globalPublicNoteText) );
    }

    @Test
    public void testUpdateDestroyStatus() throws IOException {
        CommandData commandData = getCommandDataForUnsentNote("1");
        httpConnectionMock.addResponse(org.andstatus.app.tests.R.raw.quitter_update_note_response);
        httpConnectionMock.setSameResponse(true);
        assertEquals(0, commandData.getResult().getExecutionCount());
        CommandExecutorStrategy.executeCommand(commandData, null);
        assertEquals(1, commandData.getResult().getExecutionCount());
        assertEquals(commandData.toString(), CommandResult.INITIAL_NUMBER_OF_RETRIES - 1, commandData.getResult().getRetriesLeft());
        assertFalse(commandData.toString(), commandData.getResult().hasSoftError());
        assertFalse(commandData.toString(), commandData.getResult().hasHardError());
        long noteId = commandData.getResult().getItemId();
        assertTrue(noteId != 0);

        commandData = getCommandDataForUnsentNote("2");
        String errorMessage = "Request was bad";
        httpConnectionMock.setException(new ConnectionException(StatusCode.UNKNOWN, errorMessage));
        CommandExecutorStrategy.executeCommand(commandData, null);
        assertEquals(1, commandData.getResult().getExecutionCount());
        assertEquals(CommandResult.INITIAL_NUMBER_OF_RETRIES - 1, commandData.getResult().getRetriesLeft());
        assertTrue(commandData.toString(), commandData.getResult().hasSoftError());
        assertFalse(commandData.toString(), commandData.getResult().hasHardError());
        assertTrue(commandData.toString(), commandData.getResult().shouldWeRetry());
        assertTrue("Error message: '" + commandData.getResult().getMessage() + "' should contain '"
                + errorMessage + "'", commandData.getResult().getMessage().contains(errorMessage));
        
        httpConnectionMock.setException(null);
        CommandExecutorStrategy.executeCommand(commandData, null);
        assertEquals(commandData.toString(), 2, commandData.getResult().getExecutionCount());
        assertEquals(commandData.toString(), CommandResult.INITIAL_NUMBER_OF_RETRIES - 2, commandData.getResult().getRetriesLeft());
        assertFalse(commandData.toString(), commandData.getResult().hasSoftError());
        assertFalse(commandData.toString(), commandData.getResult().hasHardError());
        assertFalse(commandData.toString(), commandData.getResult().shouldWeRetry());
        
        errorMessage = "some text";
        httpConnectionMock.setException(new ConnectionException(StatusCode.AUTHENTICATION_ERROR, errorMessage));
        CommandExecutorStrategy.executeCommand(commandData, null);
        assertEquals(3, commandData.getResult().getExecutionCount());
        assertEquals(commandData.toString(), CommandResult.INITIAL_NUMBER_OF_RETRIES - 3, commandData.getResult().getRetriesLeft());
        assertFalse(commandData.toString(), commandData.getResult().hasSoftError());
        assertTrue(commandData.toString(), commandData.getResult().hasHardError());
        assertFalse(commandData.toString(), commandData.getResult().shouldWeRetry());
        assertTrue("Error message: '" + commandData.getResult().getMessage() + "' should contain '"
                + errorMessage + "'", commandData.getResult().getMessage().contains(errorMessage));

        httpConnectionMock.setException(null);
        commandData = CommandData.newItemCommand(
                CommandEnum.DELETE_NOTE,
                demoData.getGnuSocialAccount(),
                noteId);
        CommandExecutorStrategy.executeCommand(commandData, null);
        assertFalse(commandData.toString(), commandData.getResult().hasError());

        final long INEXISTENT_MSG_ID = -1;
        commandData = CommandData.newItemCommand(
                CommandEnum.DELETE_NOTE,
                demoData.getGnuSocialAccount(),
                INEXISTENT_MSG_ID);
        CommandExecutorStrategy.executeCommand(commandData, null);
        assertFalse(commandData.toString(), commandData.getResult().hasError());
        
        httpConnectionMock.setException(null);
    }

    private CommandData getCommandDataForUnsentNote(String suffix) {
        String body = "Some text " + suffix + " to send " + System.currentTimeMillis() + "ms";
        AActivity activity = DemoNoteInserter.addNoteForAccount(ma, body, "", DownloadStatus.SENDING);
        return CommandData.newUpdateStatus(ma, activity.getId(), activity.getNote().noteId);
    }

    @Test
    public void testDiscoverOrigins() throws IOException {
        HttpConnectionMock http = new HttpConnectionMock();
        http.addResponse(org.andstatus.app.tests.R.raw.get_open_instances);
        TestSuite.setHttpConnectionMockInstance(http);
        CommandData commandData = CommandData.newOriginCommand(
                CommandEnum.GET_OPEN_INSTANCES,
                demoData.getGnuSocialOrigin());
        DiscoveredOrigins.clear();
        CommandExecutorStrategy.executeCommand(commandData, null);
        assertEquals(1, commandData.getResult().getExecutionCount());
        assertFalse(commandData.getResult().hasError());
        assertTrue(commandData.getResult().getDownloadedCount() > 0);
        assertFalse(DiscoveredOrigins.get().isEmpty());
    }
    
    @After
    public void tearDown() throws Exception {
        TestSuite.setHttpConnectionMockInstance(null);
        TestSuite.setHttpConnectionMockClass(null);
        MyContextHolder.get().accounts().initialize();
    }
}
