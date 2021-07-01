package org.andstatus.app.context

import org.andstatus.app.data.DbUtils
import org.andstatus.app.os.UiThreadExecutor
import org.andstatus.app.util.MyLog
import org.hamcrest.MatcherAssert
import org.hamcrest.core.StringStartsWith
import org.junit.Assert
import org.junit.Test
import java.util.*
import java.util.concurrent.CompletableFuture

class CompletableFutureTest {
    internal class TestData(val stepNumber: Long, val messages: MutableList<String>) {

        operator fun next(): TestData {
            val nextStep = stepNumber + 1
            val message = "Step " + nextStep + " at " + MyLog.uniqueDateTimeFormatted()
            MyLog.i(this, "Start: $message")
            val nextMessages = ArrayList(messages)
            nextMessages.add(message)
            DbUtils.waitMs(this, 500)
            MyLog.i(this, "End: $message")
            return TestData(nextStep, nextMessages)
        }

        override fun toString(): String {
            return "TestData{" +
                    "stepNumber=" + stepNumber +
                    ", messages=" + messages +
                    '}'
        }

        companion object {
            val EMPTY: TestData = TestData(0, mutableListOf())
        }
    }

    @Test
    fun asyncStagesTest1() {
        val method = "asyncStagesTest1"
        DbUtils.waitMs(this, 1000)
        MyLog.i(this, "$method started")
        val data1 = TestData.EMPTY.next()
        val future1 = CompletableFuture.completedFuture(data1)
        MyLog.i(this, "$method completed future1 created")
        val future2 = future1.thenCompose { testData: TestData ->
            CompletableFuture.supplyAsync { testData.next() }
        }
        MyLog.i(this, "$method future2 created")
        val future3 = future2.thenCompose { testData: TestData -> CompletableFuture.supplyAsync({ testData.next() }, UiThreadExecutor.Companion.INSTANCE) }
        MyLog.i(this, "$method future3 created")
        val finalData = future3
                .whenCompleteAsync { testData: TestData, throwable: Throwable? -> assertFuture3("async1", testData) }
                .whenCompleteAsync { testData: TestData, throwable: Throwable? -> assertFuture3("async2", testData) }
                .whenCompleteAsync { testData: TestData, throwable: Throwable? -> assertFuture3("async3", testData) }
                .thenCompose { testData: TestData ->
                    CompletableFuture.supplyAsync { testData.next() }
                }
                .get()
        MyLog.i(this, "$method async work completed")
        Assert.assertEquals("step 4 completed $finalData", 4, finalData.stepNumber)
        MyLog.i(this, "$method asyncStagesTest completed")
    }

    private fun assertFuture3(message: String, testData: TestData) {
        MyLog.i(this, "$message assertFuture3 started")
        Assert.assertEquals("future3 completed $testData", 3, testData.stepNumber)
        Assert.assertEquals("future3 messages $testData", 3, testData.messages.size.toLong())
        MatcherAssert.assertThat("future3 messages $testData", testData.messages[1], StringStartsWith.startsWith("Step 2"))
        MatcherAssert.assertThat("future3 messages $testData", testData.messages[2], StringStartsWith.startsWith("Step 3"))
        DbUtils.waitMs(message, 1000)
        MyLog.i(this, "$message assertFuture3 completed")
    }
}
