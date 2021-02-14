package org.andstatus.app.context;

import org.andstatus.app.data.DbUtils;
import org.andstatus.app.os.AsyncTaskLauncher;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.os.UiThreadExecutor;
import org.andstatus.app.util.MyLog;
import org.hamcrest.core.StringStartsWith;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

public class CompletableFutureTest {

    static class TestData {
        static final TestData EMPTY = new TestData(0, Collections.emptyList());

        final long stepNumber;
        final List<String> messages;

        TestData(long stepNumber, List<String> messages) {
            this.stepNumber = stepNumber;
            this.messages = messages;
        }

        TestData next() {
            long nextStep = stepNumber + 1;
            String message = "Step " + nextStep + " at " + MyLog.uniqueDateTimeFormatted();
            MyLog.i(this, "Start: " +  message);
            ArrayList<String> nextMessages = new ArrayList<>(messages);
            nextMessages.add(message);
            DbUtils.waitMs(this, 500);
            MyLog.i(this, "End: " +  message);
            return new TestData(nextStep, nextMessages);
        }

        @Override
        public String toString() {
            return "TestData{" +
                    "stepNumber=" + stepNumber +
                    ", messages=" + messages +
                    '}';
        }
    }

    @Test
    public void asyncStagesTest1() throws ExecutionException, InterruptedException {
        String method = "asyncStagesTest1";
        DbUtils.waitMs(this, 1000);

        MyLog.i(this, method + " started");
        TestData data1 = TestData.EMPTY.next();
        CompletableFuture<TestData> future1 = CompletableFuture.completedFuture(data1);
        MyLog.i(this, method + " completed future1 created");

        CompletableFuture<TestData> future2 = future1.thenCompose(
                testData -> CompletableFuture.supplyAsync(testData::next,
                        AsyncTaskLauncher.getExecutor(MyAsyncTask.PoolEnum.LONG_UI)));
        MyLog.i(this, method + " future2 created");

        CompletableFuture<TestData> future3 = future2.thenCompose(
                testData -> CompletableFuture.supplyAsync(testData::next, UiThreadExecutor.INSTANCE));
        MyLog.i(this, method + " future3 created");

        TestData finalData = future3
                .whenCompleteAsync((testData, throwable) -> assertFuture3("async1", testData),
                        AsyncTaskLauncher.getExecutor(MyAsyncTask.PoolEnum.QUICK_UI))
                .whenCompleteAsync((testData, throwable) -> assertFuture3("async2", testData), UiThreadExecutor.INSTANCE)
                .whenCompleteAsync((testData, throwable) -> assertFuture3("async3", testData),
                        AsyncTaskLauncher.getExecutor(MyAsyncTask.PoolEnum.LONG_UI))
                .thenCompose(testData -> CompletableFuture.supplyAsync(testData::next,
                        AsyncTaskLauncher.getExecutor(MyAsyncTask.PoolEnum.SYNC)))
                .get();
        MyLog.i(this, method + " async work completed");
        assertEquals("step 4 completed " + finalData, 4, finalData.stepNumber);

        MyLog.i(this, method + " asyncStagesTest completed");
    }

    private void assertFuture3(String message, TestData testData) {
        MyLog.i(this, message + " assertFuture3 started");
        assertEquals("future3 completed " + testData, 3, testData.stepNumber);
        assertEquals("future3 messages " + testData, 3, testData.messages.size());
        assertThat("future3 messages " + testData, testData.messages.get(1), StringStartsWith.startsWith("Step 2"));
        assertThat("future3 messages " + testData, testData.messages.get(2), StringStartsWith.startsWith("Step 3"));
        DbUtils.waitMs(message, 1000);
        MyLog.i(this, message + " assertFuture3 completed");
    }

}
