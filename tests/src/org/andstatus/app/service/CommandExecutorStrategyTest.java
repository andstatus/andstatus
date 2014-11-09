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
import org.andstatus.app.net.ConnectionException;
import org.andstatus.app.net.ConnectionException.StatusCode;
import org.andstatus.app.net.HttpConnectionMock;
import org.andstatus.app.util.RawResourceUtils;
import org.andstatus.app.util.TriState;

import java.util.Arrays;

public class CommandExecutorStrategyTest extends InstrumentationTestCase {

    private HttpConnectionMock httpConnectionMock;
    MyAccount ma;

    @Override
    protected void setUp() throws Exception {
        TestSuite.initializeWithData(this);

        httpConnectionMock = new HttpConnectionMock();
        TestSuite.setHttpConnectionMock(httpConnectionMock);
        assertEquals("HttpConnection mocked", MyContextHolder.get().getHttpConnectionMock(), httpConnectionMock);
        // In order the mocked connection to have effect:
        MyContextHolder.get().persistentAccounts().initialize();
        ma = MyAccount.Builder.newOrExistingFromAccountName(
                MyContextHolder.get(), 
                TestSuite.STATUSNET_TEST_ACCOUNT_NAME, TriState.UNKNOWN).getAccount();
        assertTrue(ma.getUserId() != 0);
    }

    public void testFetchTimeline() {
        CommandData commandData = new CommandData(CommandEnum.FETCH_TIMELINE, "");
        CommandExecutorStrategy strategy = CommandExecutorStrategy.getStrategy(commandData, null);
        assertEquals(CommandExecutorAllAccounts.class, strategy.getClass());
    }

    public void testSearch() {
        CommandData commandData = CommandData.searchCommand("", TestSuite.GLOBAL_PUBLIC_MESSAGE_TEXT);
        CommandExecutorStrategy strategy = CommandExecutorStrategy.getStrategy(commandData, null);
        assertEquals(CommandExecutorAllOrigins.class, strategy.getClass());

        commandData = CommandData.searchCommand(ma.getAccountName(), TestSuite.GLOBAL_PUBLIC_MESSAGE_TEXT);
        strategy = CommandExecutorStrategy.getStrategy(commandData, null);
        assertEquals(CommandExecutorSearch.class, strategy.getClass());
        strategy.execute();
        assertTrue("Requested '" + Arrays.toString(httpConnectionMock.getPostedPaths().toArray()) + "'", httpConnectionMock.getPostedPaths().get(0).contains(TestSuite.GLOBAL_PUBLIC_MESSAGE_TEXT) );
    }

    public void testUpdateDestroyStatus() {
        String body = "Some text to send " + System.currentTimeMillis() + "ms"; 
        httpConnectionMock.setResponse(RawResourceUtils.getJSONObject(this.getInstrumentation().getContext(), 
                org.andstatus.app.tests.R.raw.update_status_response_status_net));
       
        CommandData commandData = CommandData.updateStatus(TestSuite.STATUSNET_TEST_ACCOUNT_NAME, 
                body, 0, 0, null);
        assertEquals(0, commandData.getResult().getExecutionCount());
        CommandExecutorStrategy.executeCommand(commandData, null);
        assertEquals(1, commandData.getResult().getExecutionCount());
        assertEquals(CommandResult.INITIAL_NUMBER_OF_RETRIES - 1, commandData.getResult().getRetriesLeft());
        assertFalse(commandData.getResult().hasSoftError());
        assertFalse(commandData.getResult().hasHardError());
        long msgId = commandData.getResult().getItemId();
        assertTrue(msgId != 0);

        String errorMessage = "Request was bad";
        httpConnectionMock.setException(new ConnectionException(StatusCode.UNKNOWN, errorMessage));
        CommandExecutorStrategy.executeCommand(commandData, null);
        assertEquals(2, commandData.getResult().getExecutionCount());
        assertEquals(CommandResult.INITIAL_NUMBER_OF_RETRIES - 2, commandData.getResult().getRetriesLeft());
        assertTrue(commandData.getResult().hasSoftError());
        assertFalse(commandData.getResult().hasHardError());
        assertTrue(commandData.getResult().shouldWeRetry());
        assertTrue("Error message: '" + commandData.getResult().getMessage() + "' should contain '"
                + errorMessage + "'", commandData.getResult().getMessage().contains(errorMessage));
        
        httpConnectionMock.setException(null);
        CommandExecutorStrategy.executeCommand(commandData, null);
        assertEquals(3, commandData.getResult().getExecutionCount());
        assertEquals(CommandResult.INITIAL_NUMBER_OF_RETRIES - 3, commandData.getResult().getRetriesLeft());
        assertFalse(commandData.getResult().hasSoftError());
        assertFalse(commandData.getResult().hasHardError());
        assertFalse(commandData.getResult().shouldWeRetry());
        
        errorMessage = "some text";
        httpConnectionMock.setException(new ConnectionException(StatusCode.AUTHENTICATION_ERROR, errorMessage));
        CommandExecutorStrategy.executeCommand(commandData, null);
        assertEquals(4, commandData.getResult().getExecutionCount());
        assertEquals(CommandResult.INITIAL_NUMBER_OF_RETRIES - 4, commandData.getResult().getRetriesLeft());
        assertFalse(commandData.getResult().hasSoftError());
        assertTrue(commandData.getResult().hasHardError());
        assertFalse(commandData.getResult().shouldWeRetry());
        assertTrue("Error message: '" + commandData.getResult().getMessage() + "' should contain '"
                + errorMessage + "'", commandData.getResult().getMessage().contains(errorMessage));

        httpConnectionMock.setException(null);
        commandData = new CommandData(CommandEnum.DESTROY_STATUS, TestSuite.STATUSNET_TEST_ACCOUNT_NAME, msgId);
        CommandExecutorStrategy.executeCommand(commandData, null);
        assertFalse(commandData.getResult().hasError());

        final long INEXISTENT_MSG_ID = -1;
        commandData = new CommandData(CommandEnum.DESTROY_STATUS, TestSuite.STATUSNET_TEST_ACCOUNT_NAME, INEXISTENT_MSG_ID);
        CommandExecutorStrategy.executeCommand(commandData, null);
        assertFalse(commandData.getResult().hasError());
        
        httpConnectionMock.setException(null);
    }
    
    @Override
    protected void tearDown() throws Exception {
        TestSuite.setHttpConnectionMock(null);
        MyContextHolder.get().persistentAccounts().initialize();
        super.tearDown();
    }
}
