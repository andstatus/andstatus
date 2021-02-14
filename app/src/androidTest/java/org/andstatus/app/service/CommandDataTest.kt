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
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.OidEnum;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.MyLog;
import org.junit.Before;
import org.junit.Test;

import java.util.Queue;
import java.util.concurrent.PriorityBlockingQueue;

import static org.andstatus.app.context.DemoData.demoData;
import static org.andstatus.app.context.MyContextHolder.myContextHolder;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CommandDataTest {

    @Before
    public void setUp() throws Exception {
        TestSuite.initializeWithData(this);
    }

    @Test
    public void testQueue() {
        long time0 = System.currentTimeMillis(); 
        CommandData commandData = CommandData.newUpdateStatus(demoData.getPumpioConversationAccount(), 1, 4);
        testQueueOneCommandData(commandData, time0);

        long noteId = MyQuery.oidToId(OidEnum.NOTE_OID, myContextHolder.getNow().origins()
                .fromName(demoData.conversationOriginName).getId(),
                demoData.conversationEntryNoteOid);
        long downloadDataRowId = 23;
        commandData = CommandData.newFetchAttachment(noteId, downloadDataRowId);
        testQueueOneCommandData(commandData, time0);
    }

    private void testQueueOneCommandData(CommandData commandData, long time0) {
        final String method = "testQueueOneCommandData";
        assertEquals(0, commandData.getResult().getExecutionCount());
        assertEquals(0, commandData.getResult().getLastExecutedDate());
        assertEquals(CommandResult.INITIAL_NUMBER_OF_RETRIES, commandData.getResult().getRetriesLeft());
        commandData.getResult().prepareForLaunch();
        boolean hasSoftError = true;
        commandData.getResult().incrementNumIoExceptions();
        commandData.getResult().setMessage("Error in " + commandData.hashCode());
        commandData.getResult().afterExecutionEnded();
        DbUtils.waitMs(method, 50);
        long time1 = System.currentTimeMillis();
        assertTrue(commandData.getResult().getLastExecutedDate() >= time0);
        assertTrue(commandData.getResult().getLastExecutedDate() < time1);
        assertEquals(1, commandData.getResult().getExecutionCount());
        assertEquals(CommandResult.INITIAL_NUMBER_OF_RETRIES - 1, commandData.getResult().getRetriesLeft());
        assertEquals(hasSoftError, commandData.getResult().hasSoftError());
        assertFalse(commandData.getResult().hasHardError());
        
        CommandQueue queues = myContextHolder.getNow().queues();
        queues.clear();
        queues.get(QueueType.ERROR).addToQueue(commandData);
        queues.save();
        assertEquals(1, queues.get(QueueType.ERROR).size());
        queues.load();
        assertEquals(1, queues.get(QueueType.ERROR).size());
        assertEquals(commandData, queues.getFromQueue(QueueType.ERROR, commandData));

        CommandData commandData2 = queues.get(QueueType.ERROR).queue.poll();

        assertEquals(commandData, commandData2);
        // Below fields are not included in equals
        assertEquals(commandData.getCommandId(), commandData2.getCommandId());
        assertEquals(commandData.getCreatedDate(), commandData2.getCreatedDate());

        assertEquals(commandData.getResult().getLastExecutedDate(), commandData2.getResult().getLastExecutedDate());
        assertEquals(commandData.getResult().getExecutionCount(), commandData2.getResult().getExecutionCount());
        assertEquals(commandData.getResult().getRetriesLeft(), commandData2.getResult().getRetriesLeft());
        assertEquals(commandData.getResult().hasError(), commandData2.getResult().hasError());
        assertEquals(commandData.getResult().hasSoftError(), commandData2.getResult().hasSoftError());
        assertEquals(commandData.getResult().getMessage(), commandData2.getResult().getMessage());
    }

    @Test
    public void testEquals() {
        CommandData data1 = CommandData.newSearch(SearchObjects.NOTES, myContextHolder.getNow(), Origin.EMPTY,"andstatus");
        CommandData data2 = CommandData.newSearch(SearchObjects.NOTES, myContextHolder.getNow(), Origin.EMPTY,"mustard");
        assertTrue("Hashcodes: " + data1.hashCode() + " and " + data2.hashCode(), data1.hashCode() != data2.hashCode());
        assertFalse(data1.equals(data2));
        
        data1.getResult().prepareForLaunch();
        data1.getResult().incrementNumIoExceptions();
        data1.getResult().afterExecutionEnded();
        assertFalse(data1.getResult().shouldWeRetry());
        CommandData data3 = CommandData.newSearch(SearchObjects.NOTES, myContextHolder.getNow(), Origin.EMPTY,"andstatus");
        assertTrue(data1.equals(data3));
        assertTrue(data1.hashCode() == data3.hashCode());
        assertEquals(data1, data3);
    }

    @Test
    public void testPriority() {
        Queue<CommandData> queue = new PriorityBlockingQueue<>(100);
        final MyAccount ma = demoData.getGnuSocialAccount();
        queue.add(CommandData.newActorCommand(CommandEnum.GET_FRIENDS, Actor.fromId(ma.getOrigin(), 123), ""));
        queue.add(CommandData.newActorCommand(CommandEnum.GET_TIMELINE, ma.getActor(), ma.getUsername()));
        queue.add(CommandData.newSearch(SearchObjects.NOTES, myContextHolder.getNow(), ma.getOrigin(), "q1"));
        queue.add(CommandData.newUpdateStatus(MyAccount.EMPTY, 2, 5));
        queue.add(CommandData.newTimelineCommand(CommandEnum.GET_TIMELINE, ma, TimelineType.INTERACTIONS));
        queue.add(CommandData.newUpdateStatus(MyAccount.EMPTY, 3, 6));
        queue.add(CommandData.newTimelineCommand(CommandEnum.GET_TIMELINE, ma, TimelineType.HOME).setInForeground(true));
        queue.add(CommandData.newItemCommand(CommandEnum.GET_NOTE, ma, 7823));

        assertCommand(queue, CommandEnum.GET_TIMELINE, TimelineType.HOME);
        assertCommand(queue, CommandEnum.UPDATE_NOTE);
        assertCommand(queue, CommandEnum.UPDATE_NOTE);
        assertCommand(queue, CommandEnum.GET_FRIENDS);
        assertCommand(queue, CommandEnum.GET_NOTE);
        assertCommand(queue, CommandEnum.GET_TIMELINE, TimelineType.SENT);
        assertCommand(queue, CommandEnum.GET_TIMELINE, TimelineType.SEARCH);
        assertCommand(queue, CommandEnum.GET_TIMELINE, TimelineType.INTERACTIONS);
    }

    private void assertCommand(Queue<CommandData> queue, CommandEnum commandEnum) {
        assertCommand(queue, commandEnum, TimelineType.UNKNOWN);
    }

    private void assertCommand(Queue<CommandData> queue, CommandEnum commandEnum, TimelineType timelineType) {
        final CommandData commandData = queue.poll();
        assertEquals(commandData.toString(), commandEnum, commandData.getCommand());
        if (timelineType != TimelineType.UNKNOWN) {
            assertEquals(commandData.toString(), timelineType, commandData.getTimelineType());
        }
    }

    @Test
    public void testSummary() {
        followUnfollowSummary(CommandEnum.FOLLOW);
        followUnfollowSummary(CommandEnum.UNDO_FOLLOW);
    }

    private void followUnfollowSummary(CommandEnum command) {
        MyAccount ma = demoData.getMyAccount(demoData.conversationAccountName);
        assertTrue(ma.isValid());
        long actorId = MyQuery.oidToId(OidEnum.ACTOR_OID, ma.getOrigin().getId(),
                demoData.conversationAuthorThirdActorOid);
        Actor actor = Actor.load(ma.getOrigin().myContext, actorId);
        CommandData data = CommandData.actOnActorCommand(
                command, demoData.getPumpioConversationAccount(), actor, "");
        String summary = data.toCommandSummary(myContextHolder.getNow());
        String msgLog = command.name() + "; Summary:'" + summary + "'";
        MyLog.v(this, msgLog);
        assertThat(msgLog, summary, containsString(command.getTitle(myContextHolder.getNow(),
                ma.getAccountName()) + " " + MyQuery.actorIdToWebfingerId(myContextHolder.getNow(), actorId)));
    }

}
