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
import org.andstatus.app.database.DatabaseHolder.OidEnum;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;

import java.util.Queue;
import java.util.concurrent.PriorityBlockingQueue;

public class CommandDataTest extends InstrumentationTestCase {

    @Override
    protected void setUp() throws Exception {
        TestSuite.initializeWithData(this);
    }
    
    public void testQueue() throws InterruptedException {
        long time0 = System.currentTimeMillis(); 
        String body = "Some text to send " + time0 + "ms";
        CommandData commandData = CommandData.updateStatus(TestSuite.CONVERSATION_ACCOUNT_NAME, 1);
        testQueueOneCommandData(commandData, time0);

        long msgId = MyQuery.oidToId(OidEnum.MSG_OID, MyContextHolder.get().persistentOrigins()
                .fromName(TestSuite.CONVERSATION_ORIGIN_NAME).getId(),
                TestSuite.CONVERSATION_ENTRY_MESSAGE_OID);
        long downloadDataRowId = 23;
        commandData = CommandData.fetchAttachment(msgId, downloadDataRowId);
        testQueueOneCommandData(commandData, time0);
    }

    private void testQueueOneCommandData(CommandData commandData, long time0)
            throws InterruptedException {
        assertEquals(0, commandData.getResult().getExecutionCount());
        assertEquals(0, commandData.getResult().getLastExecutedDate());
        assertEquals(CommandResult.INITIAL_NUMBER_OF_RETRIES, commandData.getResult().getRetriesLeft());
        commandData.getResult().prepareForLaunch();
        boolean hasSoftError = true;
        commandData.getResult().incrementNumIoExceptions();
        commandData.getResult().setMessage("Error in " + commandData.hashCode());
        commandData.getResult().afterExecutionEnded();
        Thread.sleep(50);
        long time1 = System.currentTimeMillis();
        assertTrue(commandData.getResult().getLastExecutedDate() >= time0);
        assertTrue(commandData.getResult().getLastExecutedDate() < time1);
        assertEquals(1, commandData.getResult().getExecutionCount());
        assertEquals(CommandResult.INITIAL_NUMBER_OF_RETRIES - 1, commandData.getResult().getRetriesLeft());
        assertEquals(hasSoftError, commandData.getResult().hasSoftError());
        assertFalse(commandData.getResult().hasHardError());
        
        
        
        Queue<CommandData> queue = new PriorityBlockingQueue<>(100);
        queue.add(commandData);
        assertEquals(1, CommandData.saveQueue(MyContextHolder.get().context(), queue, QueueType.TEST));
        queue.clear();
        assertEquals(1, CommandData.loadQueue(MyContextHolder.get().context(), queue, QueueType.TEST));

        CommandData commandData2 = queue.poll();
        assertEquals(commandData, commandData2);
        assertEquals(commandData.getId(), commandData2.getId());
        assertEquals(commandData.getResult().getLastExecutedDate(), commandData2.getResult().getLastExecutedDate());
        assertEquals(commandData.getResult().getExecutionCount(), commandData2.getResult().getExecutionCount());
        assertEquals(commandData.getResult().getRetriesLeft(), commandData2.getResult().getRetriesLeft());
        assertEquals(commandData.getResult().hasError(), commandData2.getResult().hasError());
        assertEquals(commandData.getResult().hasSoftError(), commandData2.getResult().hasSoftError());
        assertEquals(commandData.getResult().getMessage(), commandData2.getResult().getMessage());
    }

    public void testEquals() {
        CommandData data1 = CommandData.searchCommand("", "andstatus");
        CommandData data2 = CommandData.searchCommand("", "mustard");
        assertTrue("Hashcodes: " + data1.hashCode() + " and " + data2.hashCode(), data1.hashCode() != data2.hashCode());
        assertFalse(data1.equals(data2));
        
        data1.getResult().prepareForLaunch();
        data1.getResult().incrementNumIoExceptions();
        data1.getResult().afterExecutionEnded();
        assertFalse(data1.getResult().shouldWeRetry());
        CommandData data3 = CommandData.searchCommand("", "andstatus");
        assertTrue(data1.equals(data3));
        assertTrue(data1.hashCode() == data3.hashCode());
        assertEquals(data1, data3);
    }
    
    public void testPriority() {
        Queue<CommandData> queue = new PriorityBlockingQueue<CommandData>(100);
        queue.add(CommandData.searchCommand(TestSuite.GNUSOCIAL_TEST_ACCOUNT_NAME, "q1"));
        queue.add(CommandData.updateStatus("", 2));
        queue.add(new CommandData(CommandEnum.AUTOMATIC_UPDATE, ""));
        queue.add(CommandData.updateStatus("", 3));
        queue.add(new CommandData(CommandEnum.GET_STATUS, ""));
        
        assertEquals(CommandEnum.UPDATE_STATUS, queue.poll().getCommand());
        assertEquals(CommandEnum.UPDATE_STATUS, queue.poll().getCommand());
        assertEquals(CommandEnum.GET_STATUS, queue.poll().getCommand());
        assertEquals(CommandEnum.SEARCH_MESSAGE, queue.poll().getCommand());
        assertEquals(CommandEnum.AUTOMATIC_UPDATE, queue.poll().getCommand());
    }
    
    public void testSummary() {
        followUnfollowSummary(CommandEnum.FOLLOW_USER);
        followUnfollowSummary(CommandEnum.STOP_FOLLOWING_USER);
    }

    private void followUnfollowSummary(CommandEnum command) {
        MyAccount ma = MyContextHolder.get().persistentAccounts()
                .fromAccountName(TestSuite.CONVERSATION_ACCOUNT_NAME);
        assertTrue(ma.isValid());
        long userId = MyQuery.oidToId(OidEnum.USER_OID, ma.getOrigin().getId(),
                TestSuite.CONVERSATION_MEMBER_USER_OID);
        CommandData data = new CommandData(command, 
                TestSuite.CONVERSATION_ACCOUNT_NAME, userId);
        String summary = data.toCommandSummary(MyContextHolder.get());
        String msgLog = command.name() + "; Summary:'" + summary + "'";
        MyLog.v(this, msgLog);
        assertTrue(msgLog, summary.contains(command.getTitle(MyContextHolder.get(),
                ma.getAccountName()) + " " + MyQuery.userIdToWebfingerId(userId)));
    }
    
    @Override
    protected void tearDown() throws Exception {
        SharedPreferencesUtil.delete(MyContextHolder.get().context(), QueueType.TEST.getFilename());
        super.tearDown();
    }

}
