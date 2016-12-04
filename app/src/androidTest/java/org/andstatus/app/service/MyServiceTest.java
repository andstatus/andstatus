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

import android.content.SyncResult;
import android.database.sqlite.SQLiteDiskIOException;
import android.test.InstrumentationTestCase;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.context.Travis;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.os.ExceptionsCounter;
import org.andstatus.app.timeline.Timeline;
import org.andstatus.app.timeline.TimelineType;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.TriState;

import java.net.MalformedURLException;
import java.util.Queue;

@Travis
public class MyServiceTest extends InstrumentationTestCase {
    private MyServiceTestHelper mService;
    private volatile MyAccount ma;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        MyLog.i(this, "setUp started");
        TestSuite.initializeWithData(this);

        mService = new MyServiceTestHelper();
        mService.setUp(null);
        ma = MyContextHolder.get().persistentAccounts().getFirstSucceeded();
        assertTrue("No successfully verified accounts", ma.isValidAndSucceeded());
        
        MyLog.i(this, "setUp ended instanceId=" + mService.connectionInstanceId);
    }

    public void testRepeatingFailingCommand() throws MalformedURLException {
        final String method = "testRepeatingFailingCommand";
        MyLog.v(this, method + " started");

        String urlString = "http://andstatus.org/nonexistent2_avatar_" + System.currentTimeMillis() +  ".png";
        AvatarDownloaderTest.changeAvatarUrl(ma, urlString);
        
        mService.setListenedCommand(CommandData.newUserCommand(
                CommandEnum.FETCH_AVATAR,
                null, ma.getOrigin(),
                ma.getUserId(),
                ""));

        long startCount = mService.executionStartCount;
        long endCount = mService.executionEndCount;

        mService.sendListenedToCommand();
        assertTrue("First command started executing", mService.waitForCommandExecutionStarted(startCount));
        mService.sendListenedToCommand();
        assertTrue("First command ended executing", mService.waitForCommandExecutionEnded(endCount));
        assertEquals(mService.httpConnectionMock.toString(), 1, mService.httpConnectionMock.getRequestsCounter());
        mService.sendListenedToCommand();
        assertFalse("Duplicated command didn't start executing",
                mService.waitForCommandExecutionStarted(startCount + 1));
        mService.getListenedCommand().setManuallyLaunched(true);
        mService.sendListenedToCommand();
        assertTrue("Manually launched duplicated command started executing",
                mService.waitForCommandExecutionStarted(startCount + 1));
        assertTrue("The third command ended executing", mService.waitForCommandExecutionEnded(endCount+1));
        assertTrue("Service stopped", mService.waitForServiceStopped(true));
        MyLog.v(this, method + " ended");
    }

    public void testAccountSync() {
        final String method = "testAccountSync";
        MyLog.v(this, method + " started");

        MyAccount myAccount = MyContextHolder.get().persistentAccounts().getFirstSucceeded();
        assertTrue("No successful account", myAccount != null);

        MyContext myContext = MyContextHolder.get();
        for (Timeline timeline : myContext.persistentTimelines().getFiltered(false, TriState.FALSE,
                myAccount, null)) {
            if (timeline.isSyncedAutomatically()) {
                if (timeline.isTimeToAutoSync()) {
                    timeline.setSyncSucceededDate(System.currentTimeMillis());
                }
            }
        }
        SyncResult syncResult = new SyncResult();
        MyServiceCommandsRunner runner = new MyServiceCommandsRunner(myContext);
        runner.setIgnoreServiceAvailability(true);
        runner.autoSyncAccount(myAccount.getAccountName(), syncResult);
        assertTrue(runner.toString(), runner.isSyncCompleted());
        assertEquals("Requests were sent while all timelines just synced " +
                runner.toString() + "; " + mService.httpConnectionMock.toString(),
                0, mService.httpConnectionMock.getRequestsCounter());

        myContext = MyContextHolder.get();
        Timeline timelineToSync = null;
        for (Timeline timeline : myContext.persistentTimelines().getFiltered(false, TriState.FALSE,
                myAccount, null)) {
            if (timeline.isSyncedAutomatically()) {
                timelineToSync = timeline;
                break;
            }
        }
        assertTrue("No synced automatically timeline for " + myAccount, timelineToSync != null);
        timelineToSync.setSyncSucceededDate(0);

        runner = new MyServiceCommandsRunner(myContext);
        runner.setIgnoreServiceAvailability(true);
        syncResult = new SyncResult();
        runner.autoSyncAccount(myAccount.getAccountName(), syncResult);
        assertTrue(runner.toString(), runner.isSyncCompleted());
        assertEquals("Timeline was not synced: " + timelineToSync + "; " +
                runner.toString() + "; " + mService.httpConnectionMock.toString(),
                1, mService.httpConnectionMock.getRequestsCounter());

        assertTrue("Service stopped", mService.waitForServiceStopped(true));
        MyLog.v(this, method + " ended");
    }


    public void testHomeTimeline() {
        final String method = "testHomeTimeline";
        MyLog.v(this, method + " started");

        mService.setListenedCommand(CommandData.newTimelineCommand(
                CommandEnum.FETCH_TIMELINE, ma, TimelineType.HOME));
        long startCount = mService.executionStartCount;
        long endCount = mService.executionEndCount;

        mService.sendListenedToCommand();
        
        assertTrue("First command started executing", mService.waitForCommandExecutionStarted(startCount));
        assertTrue("First command ended executing", mService.waitForCommandExecutionEnded(endCount));
        MyLog.v(this, method  + "; " + mService.httpConnectionMock.toString());
        assertEquals("connection instance Id", mService.connectionInstanceId, mService.httpConnectionMock.getInstanceId());
        assertEquals(mService.httpConnectionMock.toString(), 1, mService.httpConnectionMock.getRequestsCounter());
        assertTrue("Service stopped", mService.waitForServiceStopped(true));
        MyLog.v(this, method + " ended");
    }

    public void testRateLimitStatus() {
        final String method = "testRateLimitStatus";
        MyLog.v(this, method + " started");

        mService.setListenedCommand(CommandData.newAccountCommand(
                CommandEnum.RATE_LIMIT_STATUS,
                TestSuite.getMyAccount(TestSuite.GNUSOCIAL_TEST_ACCOUNT_NAME)));
        long startCount = mService.executionStartCount;
        long endCount = mService.executionEndCount;

        mService.sendListenedToCommand();
        assertTrue("First command started executing", mService.waitForCommandExecutionStarted(startCount));
        assertTrue("First command ended executing", mService.waitForCommandExecutionEnded(endCount));
        assertTrue(mService.httpConnectionMock.toString(),
                mService.httpConnectionMock.getRequestsCounter() > 0);
        assertTrue("Service stopped", mService.waitForServiceStopped(true));
        assertEquals("DiskIoException", 0, ExceptionsCounter.getDiskIoExceptionsCount());
        MyLog.v(this, method + " ended");
    }
    
    public void testSyncInForeground() throws InterruptedException {
        final String method = "testSyncInForeground";
        MyLog.v(this, method + " started");
        SharedPreferencesUtil.getDefaultSharedPreferences().edit()
                .putBoolean(MyPreferences.KEY_SYNC_WHILE_USING_APPLICATION, false).commit();
        CommandData cd1 = CommandData.newTimelineCommand(CommandEnum.FETCH_TIMELINE,
                TestSuite.getMyAccount(TestSuite.TWITTER_TEST_ACCOUNT_NAME),
                TimelineType.DIRECT);
        mService.setListenedCommand(cd1);

        long startCount = mService.executionStartCount;
        long endCount = mService.executionEndCount;

        mService.sendListenedToCommand();
        assertTrue("First command didn't start executing", mService.waitForCommandExecutionStarted(startCount));
        assertTrue("First command didn't end executing", mService.waitForCommandExecutionEnded(endCount));
        assertEquals(cd1.toString() + " " + mService.httpConnectionMock.toString(),
                1, mService.httpConnectionMock.getRequestsCounter());

        assertTrue(TestSuite.setAndWaitForIsInForeground(true));
        MyLog.v(this, method + "; we are in a foreground");

        CommandData cd2 = CommandData.newTimelineCommand(CommandEnum.FETCH_TIMELINE,
                TestSuite.getMyAccount(TestSuite.TWITTER_TEST_ACCOUNT_NAME),
                TimelineType.MENTIONS);
        mService.setListenedCommand(cd2);

        startCount = mService.executionStartCount;
        endCount = mService.executionEndCount;
        mService.sendListenedToCommand();
        assertFalse("Second command started execution", mService.waitForCommandExecutionStarted(startCount));
        MyLog.v(this, method + "; After waiting for the second command");
        assertTrue("Service stopped", mService.waitForServiceStopped(false));
        MyLog.v(this, method + "; Service stopped after the second command");
        assertEquals("No new data was posted while in foreground",
                mService.httpConnectionMock.getRequestsCounter(), 1);

        Queue<CommandData> queue = new CommandQueue().load().get(QueueType.CURRENT);
        MyLog.v(this, method + "; Queue loaded, size:" + queue.size());
        assertFalse("Main queue is empty", queue.isEmpty());
        assertFalse("First command is in the main queue", queue.contains(cd1));
        assertTrue("The second command is not in the main queue", queue.contains(cd2));

        CommandData cd3 = CommandData.newTimelineCommand(CommandEnum.FETCH_TIMELINE,
                TestSuite.getMyAccount(TestSuite.TWITTER_TEST_ACCOUNT_NAME),
                TimelineType.HOME)
                .setInForeground(true);
        mService.setListenedCommand(cd3);

        startCount = mService.executionStartCount;
        endCount = mService.executionEndCount;
        mService.sendListenedToCommand();

        assertTrue("Foreground command started executing",
                mService.waitForCommandExecutionStarted(startCount));
        assertTrue("Foreground command ended executing", mService.waitForCommandExecutionEnded(endCount));
        assertTrue("Service stopped", mService.waitForServiceStopped(false));

        queue = new CommandQueue().load().get(QueueType.CURRENT);
        assertFalse("Main queue is not empty", queue.isEmpty());
        assertTrue("The second command stayed in the main queue", queue.contains(cd2));
        
        long idFound = -1;
        for (CommandData cd : queue) {
            if (cd.equals(cd2)) {
                idFound = cd.getCommandId();
            }
        }
        assertEquals("command id", cd2.getCommandId(), idFound);
        assertTrue("command id=" + idFound, idFound >= 0);
        
        assertFalse("Foreground command is not in main queue", queue.contains(cd3));
        new CommandQueue().clear();
        MyLog.v(this, method + " ended");
        myTestDeleteCommand(cd2);
    }
    
    private void myTestDeleteCommand(CommandData cd2) {
        MyLog.v(this, "myTestDeleteCommand started");

        CommandData cdDelete = CommandData.newItemCommand(
                CommandEnum.DELETE_COMMAND,
                null,
                cd2.getCommandId());
        cdDelete.setInForeground(true);
        mService.setListenedCommand(cdDelete);

        assertEquals(cd2.getCommandId(), mService.getListenedCommand().itemId);
        
        long endCount = mService.executionEndCount;

        mService.sendListenedToCommand();
        assertTrue("Delete command ended executing", mService.waitForCommandExecutionEnded(endCount));

        assertTrue("Service stopped", mService.waitForServiceStopped(false));

        Queue<CommandData> queue = new CommandQueue().load().get(QueueType.CURRENT);
        assertFalse("The second command was deleted from the main queue", queue.contains(cd2));
        MyLog.v(this, "myTestDeleteCommand ended");
    }

    public void testDiskIoErrorCatching() throws InterruptedException {
        final String method = "testDiskIoErrorCatching";
        MyLog.v(this, method + " started");

        mService.setListenedCommand(CommandData.newAccountCommand(
                CommandEnum.RATE_LIMIT_STATUS,
                TestSuite.getMyAccount(TestSuite.GNUSOCIAL_TEST_ACCOUNT_NAME)));
        mService.httpConnectionMock.setRuntimeException(new SQLiteDiskIOException(method));
        long startCount = mService.executionStartCount;
        mService.sendListenedToCommand();

        assertTrue("First command started executing", mService.waitForCommandExecutionStarted(startCount));
        assertTrue("Service stopped", mService.waitForServiceStopped(true));
        assertEquals("No DiskIoException", 1, ExceptionsCounter.getDiskIoExceptionsCount());
        DbUtils.waitMs(method, 3000);
        MyLog.v(this, method + " ended");
    }

    @Override
    protected void tearDown() throws Exception {
        MyLog.v(this, "tearDown started");
        mService.tearDown();
        super.tearDown();
        MyLog.v(this, "tearDown ended");
    }
}
