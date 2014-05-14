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

public class CommandExecutorStrategyTest extends InstrumentationTestCase {

    private HttpConnectionMock httpConnection;
    MyAccount ma;

    @Override
    protected void setUp() throws Exception {
        TestSuite.initializeWithData(this);

        httpConnection = new HttpConnectionMock();
        TestSuite.setHttpConnection(httpConnection);
        assertEquals("HttpConnection mocked", MyContextHolder.get().getHttpConnectionMock(), httpConnection);
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
        assertTrue("Requested '" + httpConnection.getPathString() + "'", httpConnection.getPathString().contains(TestSuite.GLOBAL_PUBLIC_MESSAGE_TEXT) );
    }

    public void testUpdateDestroyStatus() {
        String body = "Some text to send " + System.currentTimeMillis() + "ms"; 
        httpConnection.setResponse(RawResourceUtils.getJSONObject(this.getInstrumentation().getContext(), 
                org.andstatus.app.tests.R.raw.update_status_response_status_net));
       
        CommandData commandData = CommandData.updateStatus(TestSuite.STATUSNET_TEST_ACCOUNT_NAME, 
                body, 0, 0);
        assertEquals(0, commandData.getResult().getExecutionCount());
        CommandExecutorStrategy.executeCommand(commandData, null);
        assertEquals(1, commandData.getResult().getExecutionCount());
        assertEquals(CommandResult.MAX_RETRIES - 1, commandData.getResult().getRetriesLeft());
        assertFalse(commandData.getResult().hasSoftError());
        assertFalse(commandData.getResult().hasHardError());
        long msgId = commandData.getResult().getItemId();
        assertTrue(msgId != 0);

        httpConnection.setException(new ConnectionException(StatusCode.UNKNOWN, "Request was bad"));
        CommandExecutorStrategy.executeCommand(commandData, null);
        assertEquals(2, commandData.getResult().getExecutionCount());
        assertEquals(CommandResult.MAX_RETRIES - 2, commandData.getResult().getRetriesLeft());
        assertTrue(commandData.getResult().hasSoftError());
        assertFalse(commandData.getResult().hasHardError());
        assertTrue(commandData.getResult().shouldWeRetry());
        
        httpConnection.setException(null);
        CommandExecutorStrategy.executeCommand(commandData, null);
        assertEquals(3, commandData.getResult().getExecutionCount());
        assertEquals(CommandResult.MAX_RETRIES - 3, commandData.getResult().getRetriesLeft());
        assertFalse(commandData.getResult().hasSoftError());
        assertFalse(commandData.getResult().hasHardError());
        assertFalse(commandData.getResult().shouldWeRetry());
        
        httpConnection.setException(new ConnectionException(StatusCode.AUTHENTICATION_ERROR, "some text"));
        CommandExecutorStrategy.executeCommand(commandData, null);
        assertEquals(4, commandData.getResult().getExecutionCount());
        assertEquals(CommandResult.MAX_RETRIES - 4, commandData.getResult().getRetriesLeft());
        assertFalse(commandData.getResult().hasSoftError());
        assertTrue(commandData.getResult().hasHardError());
        assertFalse(commandData.getResult().shouldWeRetry());

        httpConnection.setException(null);
        commandData = new CommandData(CommandEnum.DESTROY_STATUS, TestSuite.STATUSNET_TEST_ACCOUNT_NAME, msgId);
        CommandExecutorStrategy.executeCommand(commandData, null);
        assertFalse(commandData.getResult().hasError());

        final long INEXISTENT_MSG_ID = -1;
        commandData = new CommandData(CommandEnum.DESTROY_STATUS, TestSuite.STATUSNET_TEST_ACCOUNT_NAME, INEXISTENT_MSG_ID);
        CommandExecutorStrategy.executeCommand(commandData, null);
        assertFalse(commandData.getResult().hasError());
        
        httpConnection.setException(null);
    }
    
    @Override
    protected void tearDown() throws Exception {
        TestSuite.setHttpConnection(null);
        MyContextHolder.get().persistentAccounts().initialize();
        super.tearDown();
    }
}
