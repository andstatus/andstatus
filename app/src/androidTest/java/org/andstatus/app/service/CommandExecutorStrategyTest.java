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

import android.test.InstrumentationTestCase;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MessageInserter;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.http.ConnectionException.StatusCode;
import org.andstatus.app.net.http.HttpConnectionMock;
import org.andstatus.app.origin.DiscoveredOrigins;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.timeline.TimelineType;
import org.andstatus.app.util.RawResourceUtils;

import java.io.IOException;
import java.util.Arrays;

public class CommandExecutorStrategyTest extends InstrumentationTestCase {

    private HttpConnectionMock httpConnectionMock;
    MyAccount ma;

    @Override
    protected void setUp() throws Exception {
        TestSuite.initializeWithData(this);

        TestSuite.setHttpConnectionMockClass(HttpConnectionMock.class);
        // In order for the the mocked connection to have effect:
        MyContextHolder.get().persistentAccounts().initialize();
        MyContextHolder.get().persistentTimelines().initialize();
        ma = MyContextHolder.get().persistentAccounts().getFirstSucceededForOriginId(
                MyContextHolder.get().persistentOrigins().fromName(TestSuite.GNUSOCIAL_TEST_ORIGIN_NAME).getId());
        assertTrue(ma.toString(), ma.isValidAndSucceeded());
        assertTrue("HttpConnection mocked", ma.getConnection().getHttp() instanceof HttpConnectionMock);
        httpConnectionMock = (HttpConnectionMock) ma.getConnection().getHttp();
    }

    public void testFetchTimeline() {
        CommandData commandData = CommandData.newTimelineCommand(CommandEnum.FETCH_TIMELINE, null, TimelineType.HOME);
        CommandExecutorStrategy strategy = CommandExecutorStrategy.getStrategy(commandData, null);
        assertEquals(CommandExecutorStrategy.class, strategy.getClass());

        commandData = CommandData.newTimelineCommand(CommandEnum.FETCH_TIMELINE, ma, TimelineType.HOME);
        strategy = CommandExecutorStrategy.getStrategy(commandData, null);
        assertEquals(TimelineDownloaderOther.class, strategy.getClass());
    }

    public void testSearch() {
        CommandData commandData = CommandData.newSearch(null, TestSuite.GLOBAL_PUBLIC_MESSAGE_TEXT);
        CommandExecutorStrategy strategy = CommandExecutorStrategy.getStrategy(commandData, null);
        assertEquals(CommandExecutorStrategy.class, strategy.getClass());

        commandData = CommandData.newSearch(ma.getOrigin(), TestSuite.GLOBAL_PUBLIC_MESSAGE_TEXT);
        strategy = CommandExecutorStrategy.getStrategy(commandData, null);
        assertEquals(TimelineDownloaderOther.class, strategy.getClass());
        strategy.execute();
        assertTrue("Requested '" + Arrays.toString(httpConnectionMock.getResults().toArray()) + "'",
                httpConnectionMock.getResults().get(0).getUrl().contains(TestSuite.GLOBAL_PUBLIC_MESSAGE_TEXT) );
    }

    public void testUpdateDestroyStatus() throws IOException {
        CommandData commandData = getCommandDataForUnsentMessage("1");
        httpConnectionMock.setResponse(RawResourceUtils.getString(this.getInstrumentation().getContext(),
                org.andstatus.app.tests.R.raw.quitter_update_status_response));
        assertEquals(0, commandData.getResult().getExecutionCount());
        CommandExecutorStrategy.executeCommand(commandData, null);
        assertEquals(1, commandData.getResult().getExecutionCount());
        assertEquals(commandData.toString(), CommandResult.INITIAL_NUMBER_OF_RETRIES - 1, commandData.getResult().getRetriesLeft());
        assertFalse(commandData.toString(), commandData.getResult().hasSoftError());
        assertFalse(commandData.toString(), commandData.getResult().hasHardError());
        long msgId = commandData.getResult().getItemId();
        assertTrue(msgId != 0);

        commandData = getCommandDataForUnsentMessage("2");
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
                CommandEnum.DESTROY_STATUS,
                TestSuite.getMyAccount(TestSuite.GNUSOCIAL_TEST_ACCOUNT_NAME),
                msgId);
        CommandExecutorStrategy.executeCommand(commandData, null);
        assertFalse(commandData.toString(), commandData.getResult().hasError());

        final long INEXISTENT_MSG_ID = -1;
        commandData = CommandData.newItemCommand(
                CommandEnum.DESTROY_STATUS,
                TestSuite.getMyAccount(TestSuite.GNUSOCIAL_TEST_ACCOUNT_NAME),
                INEXISTENT_MSG_ID);
        CommandExecutorStrategy.executeCommand(commandData, null);
        assertFalse(commandData.toString(), commandData.getResult().hasError());
        
        httpConnectionMock.setException(null);
    }

    private CommandData getCommandDataForUnsentMessage(String suffix) {
        String body = "Some text " + suffix + " to send " + System.currentTimeMillis() + "ms";
        long unsentMessageId = MessageInserter.addMessageForAccount(
                ma, body, "", DownloadStatus.SENDING);
        return CommandData.newUpdateStatus(ma, unsentMessageId);
    }

    public void testDiscoverOrigins() throws IOException {
        HttpConnectionMock http = new HttpConnectionMock();
        http.setResponse(RawResourceUtils.getString(this.getInstrumentation().getContext(),
                org.andstatus.app.tests.R.raw.get_open_instances));
        TestSuite.setHttpConnectionMockInstance(http);
        CommandData commandData = CommandData.newItemCommand(
                CommandEnum.GET_OPEN_INSTANCES,
                TestSuite.getMyAccount(TestSuite.GNUSOCIAL_TEST_ACCOUNT_NAME),
                OriginType.GNUSOCIAL.getId());
        DiscoveredOrigins.clear();
        CommandExecutorStrategy.executeCommand(commandData, null);
        assertEquals(1, commandData.getResult().getExecutionCount());
        assertFalse(commandData.getResult().hasError());
        assertTrue(commandData.getResult().getDownloadedCount() > 0);
        assertFalse(DiscoveredOrigins.get().isEmpty());
    }
    
    @Override
    protected void tearDown() throws Exception {
        TestSuite.setHttpConnectionMockInstance(null);
        TestSuite.setHttpConnectionMockClass(null);
        MyContextHolder.get().persistentAccounts().initialize();
        super.tearDown();
    }
}
