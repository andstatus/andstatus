package org.andstatus.app.service;

import android.test.InstrumentationTestCase;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.util.SharedPreferencesUtil;

import java.util.Queue;
import java.util.concurrent.PriorityBlockingQueue;

public class CommandDataTest extends InstrumentationTestCase {
    private static final String TEST_QUEUE_FILE_NAME = "test_queue_file_name";

    @Override
    protected void setUp() throws Exception {
        TestSuite.initialize(this);
    }
    
    public void testQueue() {
        Queue<CommandData> queue = new PriorityBlockingQueue<CommandData>(100);
        String body = "Some text to send " + System.currentTimeMillis() + "ms"; 
        CommandData commandData = CommandData.updateStatus(TestSuite.CONVERSATION_ACCOUNT_NAME, 
                body, 0, 0);

        assertEquals(0, commandData.getResult().getExecutionCount());
        assertEquals(CommandResult.MAX_RETRIES, commandData.getResult().getRetriesLeft());
        commandData.getResult().onLaunched();
        commandData.getResult().onExecuted();
        assertEquals(1, commandData.getResult().getExecutionCount());
        assertEquals(CommandResult.MAX_RETRIES - 1, commandData.getResult().getRetriesLeft());
        assertFalse(commandData.getResult().hasSoftError());
        assertFalse(commandData.getResult().hasHardError());
        
        queue.add(commandData);
        assertEquals(1, CommandData.saveQueue(MyContextHolder.get().context(), queue, TEST_QUEUE_FILE_NAME));
        queue.clear();
        assertEquals(1, CommandData.loadQueue(MyContextHolder.get().context(), queue, TEST_QUEUE_FILE_NAME));

        CommandData commandData2 = queue.poll();
        assertEquals(commandData, commandData2);
        assertEquals(commandData.getResult().getExecutionCount(), commandData2.getResult().getExecutionCount());
        assertEquals(commandData.getResult().getRetriesLeft(), commandData2.getResult().getRetriesLeft());
    }

    @Override
    protected void tearDown() throws Exception {
        SharedPreferencesUtil.delete(MyContextHolder.get().context(), TEST_QUEUE_FILE_NAME);
        super.tearDown();
    }

}
