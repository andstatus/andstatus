package org.andstatus.app.os

import io.vavr.control.Try
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.StopWatch
import org.junit.Assert
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class MyAsyncTaskTest {

    class TestTask : AsyncTask<String, String, String>(PoolEnum.DEFAULT_POOL) {
        val onPreExecuteVal = AtomicBoolean()
        val inBackgroundVal = AtomicBoolean()
        val onCancelVal = AtomicBoolean()
        val onPostExecuteVal = AtomicReference<String?>()
        var exceptionDuringBackground: Exception? = null

        override suspend fun onPreExecute() {
            onPreExecuteVal.set(true)
            delay(200)
        }

        override suspend fun doInBackground(params: String): Try<String> {
            inBackgroundVal.set(true)
            delay(250)
            exceptionDuringBackground?.let { throw it }
            delay(250)
            return Try.success("done")
        }

        override suspend fun onCancel() {
            onCancelVal.set(true)
        }

        override suspend fun onPostExecute(result: Try<String>) {
            onPostExecuteVal.set("got: $result")
            delay(200)
        }
    }

    @Test
    fun cancelBeforeBackground() = runBlocking {
        val task = TestTask().apply { executeInContext(Dispatchers.Default, "") }
        MyLog.i(this, "Executing $task")
        StopWatch.tillPassedSeconds(5) {
            task.onPreExecuteVal.get()
        }
        cancelAndWaitTillFinished(task)

        Assert.assertTrue("onPreExecute $task", task.onPreExecuteVal.get())
        Assert.assertFalse("inBackground $task", task.inBackgroundVal.get())
        Assert.assertTrue("onCancel $task", task.onCancelVal.get())
    }

    @Test
    fun cancelDuringBackground() = runBlocking {
        val task = TestTask().apply { executeInContext(Dispatchers.Default, "") }
        MyLog.i(this, "Executing $task")
        StopWatch.tillPassedSeconds(5) {
            task.inBackgroundVal.get()
        }
        cancelAndWaitTillFinished(task)

        Assert.assertTrue("onPreExecute $task", task.onPreExecuteVal.get())
        Assert.assertTrue("inBackground $task", task.inBackgroundVal.get())
        Assert.assertTrue("onCancel $task", task.onCancelVal.get())
    }

    @Test
    fun externallyCancelledDuringBackground() = runBlocking {
        val task = TestTask().apply { executeInContext(Dispatchers.Default, "") }
        MyLog.i(this, "Executing $task")
        StopWatch.tillPassedSeconds(5) {
            task.inBackgroundVal.get()
        }
        cancelAndWaitTillFinished(task, true)

        Assert.assertTrue("onPreExecute $task", task.onPreExecuteVal.get())
        Assert.assertTrue("inBackground $task", task.inBackgroundVal.get())
        Assert.assertTrue("onCancel $task", task.onCancelVal.get())
    }

    @Test
    fun cancelDuringPostExecute() = runBlocking {
        val task = TestTask().apply { executeInContext(Dispatchers.Default, "") }
        MyLog.i(this, "Executing $task")
        StopWatch.tillPassedSeconds(5) {
            task.onPostExecuteVal.get() != null
        }
        cancelAndWaitTillFinished(task)

        Assert.assertTrue("onPreExecute $task", task.onPreExecuteVal.get())
        Assert.assertTrue("inBackground $task", task.inBackgroundVal.get())
        Assert.assertFalse("onCancel $task", task.onCancelVal.get())
    }

    @Test
    fun normalExecution() = runBlocking {
        val task = TestTask().apply { executeInContext(Dispatchers.Default, "") }
        MyLog.i(this, "Executing $task")
        waitTillFinished(task)

        Assert.assertTrue("onPreExecute $task", task.onPreExecuteVal.get())
        Assert.assertTrue("inBackground $task", task.inBackgroundVal.get())
        Assert.assertFalse("onCancel $task", task.onCancelVal.get())
    }

    @Test
    fun exceptionDuringBackground() = runBlocking {
        val task = TestTask().apply {
            exceptionDuringBackground = Exception("Something went wrong")
            executeInContext(Dispatchers.Default, "")
        }
        MyLog.i(this, "Executing $task")
        waitTillFinished(task)

        Assert.assertTrue("onPreExecute $task", task.onPreExecuteVal.get())
        Assert.assertTrue("inBackground $task", task.inBackgroundVal.get())
        Assert.assertFalse("onCancel $task", task.onCancelVal.get())
    }

    private suspend fun cancelAndWaitTillFinished(task: TestTask, cancelJob: Boolean = false) {
        MyLog.i(this, "Before cancel of $task")
        if (cancelJob) task.job?.cancel()
        else task.cancel()
        waitTillFinished(task)
    }

    private suspend fun waitTillFinished(task: TestTask) {
        MyLog.i(this, "Waiting till finished $task")
        StopWatch.tillPassedSeconds(5) {
            task.isFinished
        }
        MyLog.i(this, "After waiting for isFinished $task")
        Assert.assertNotNull("onPostExecute $task", task.onPostExecuteVal.get())
        task.job?.join()
        MyLog.i(this, "After join() $task")
    }

}
