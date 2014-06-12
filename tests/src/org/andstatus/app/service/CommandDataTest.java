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
    
    public void testQueue() throws InterruptedException {
        Queue<CommandData> queue = new PriorityBlockingQueue<CommandData>(100);
        long time0 = System.currentTimeMillis(); 
        String body = "Some text to send " + time0 + "ms"; 
        CommandData commandData = CommandData.updateStatus(TestSuite.CONVERSATION_ACCOUNT_NAME, 
                body, 0, 0);

        assertEquals(0, commandData.getResult().getExecutionCount());
        assertEquals(0, commandData.getResult().getLastExecutedDate());
        assertEquals(CommandResult.INITIAL_NUMBER_OF_RETRIES, commandData.getResult().getRetriesLeft());
        commandData.getResult().onLaunched();
        commandData.getResult().onExecuted();
        Thread.sleep(50);
        long time1 = System.currentTimeMillis();
        assertTrue(commandData.getResult().getLastExecutedDate() >= time0);
        assertTrue(commandData.getResult().getLastExecutedDate() < time1);
        assertEquals(1, commandData.getResult().getExecutionCount());
        assertEquals(CommandResult.INITIAL_NUMBER_OF_RETRIES - 1, commandData.getResult().getRetriesLeft());
        assertFalse(commandData.getResult().hasSoftError());
        assertFalse(commandData.getResult().hasHardError());
        
        queue.add(commandData);
        assertEquals(1, CommandData.saveQueue(MyContextHolder.get().context(), queue, TEST_QUEUE_FILE_NAME));
        queue.clear();
        assertEquals(1, CommandData.loadQueue(MyContextHolder.get().context(), queue, TEST_QUEUE_FILE_NAME));

        CommandData commandData2 = queue.poll();
        assertEquals(commandData, commandData2);
        assertEquals(commandData.getResult().getCreatedDate(), commandData2.getResult().getCreatedDate());
        assertEquals(commandData.getResult().getLastExecutedDate(), commandData2.getResult().getLastExecutedDate());
        assertEquals(commandData.getResult().getExecutionCount(), commandData2.getResult().getExecutionCount());
        assertEquals(commandData.getResult().getRetriesLeft(), commandData2.getResult().getRetriesLeft());
    }

    @Override
    protected void tearDown() throws Exception {
        SharedPreferencesUtil.delete(MyContextHolder.get().context(), TEST_QUEUE_FILE_NAME);
        super.tearDown();
    }

}
