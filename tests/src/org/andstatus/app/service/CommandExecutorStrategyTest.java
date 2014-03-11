package org.andstatus.app.service;

import android.test.InstrumentationTestCase;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.net.HttpConnectionMock;
import org.andstatus.app.util.TriState;

public class CommandExecutorStrategyTest extends InstrumentationTestCase {

    @Override
    protected void setUp() throws Exception {
        TestSuite.initializeWithData(this);
    }

    public void testCommandExecutorStrategy() {
        HttpConnectionMock httpConnection = new HttpConnectionMock();
        TestSuite.setHttpConnection(httpConnection);
        assertEquals("HttpConnection mocked", MyContextHolder.get().getHttpConnectionMock(), httpConnection);
        
        // In order the mocked connection to have effect:
        MyContextHolder.get().persistentAccounts().initialize();
        MyAccount ma = MyAccount.Builder.newOrExistingFromAccountName(
                TestSuite.STATUSNET_TEST_ACCOUNT_NAME, TriState.UNKNOWN).getAccount();
        assertTrue(ma.getUserId() != 0);
        
        CommandData commandData = new CommandData(CommandEnum.FETCH_TIMELINE, "");
        CommandExecutorStrategy strategy = CommandExecutorStrategy.getStrategy(commandData, null);
        assertEquals(CommandExecutorAllAccounts.class, strategy.getClass());

        commandData = CommandData.searchCommand("", TestSuite.GLOBAL_PUBLIC_MESSAGE_TEXT);
        strategy = CommandExecutorStrategy.getStrategy(commandData, null);
        assertEquals(CommandExecutorAllOrigins.class, strategy.getClass());

        commandData = CommandData.searchCommand(ma.getAccountName(), TestSuite.GLOBAL_PUBLIC_MESSAGE_TEXT);
        strategy = CommandExecutorStrategy.getStrategy(commandData, null);
        assertEquals(CommandExecutorSearch.class, strategy.getClass());
        strategy.execute();
        assertTrue("Requested '" + httpConnection.getPathString() + "'", httpConnection.getPathString().contains(TestSuite.GLOBAL_PUBLIC_MESSAGE_TEXT) );
        
        commandData = CommandData.updateStatus(TestSuite.STATUSNET_TEST_ACCOUNT_NAME, 
                "Some text to send", 0, 0);
        assertEquals(0, commandData.getResult().getExecutionCount());
        CommandExecutorStrategy.executeCommand(commandData, null);
        assertEquals(1, commandData.getResult().getExecutionCount());
        assertEquals(CommandResult.MAX_RETRIES - 1, commandData.getResult().getRetriesLeft());
        CommandExecutorStrategy.executeCommand(commandData, null);
        assertEquals(2, commandData.getResult().getExecutionCount());
        assertEquals(CommandResult.MAX_RETRIES - 2, commandData.getResult().getRetriesLeft());
        
        TestSuite.setHttpConnection(null);
        MyContextHolder.get().persistentAccounts().initialize();
    }
}
