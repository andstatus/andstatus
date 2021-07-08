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

    class TestTask : MyAsyncTask<String, String, String>(PoolEnum.DEFAULT_POOL) {
        val onPreExecuteVal = AtomicBoolean()
        val inBackgroundVal = AtomicBoolean()
        val onCancelVal = AtomicBoolean()
        val onPostExecuteVal = AtomicReference<String?>()
        val onFinishVal = AtomicReference<String?>()
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

        override suspend fun onPostExecute(result: String) {
            onPostExecuteVal.set("got: $result")
            delay(200)
        }

        override suspend fun onFinish(result: Try<String>) {
            onFinishVal.set("got: $result")
            delay(200)
        }
    }

    @Test
    fun cancelBeforeBackground() = runBlocking {
        val stopWatch = StopWatch.createStarted()
        val task = TestTask().apply { executeInContext(Dispatchers.Default, "") }
        MyLog.i(this, "Executing $task")
        do {
            if (task.onPreExecuteVal.get()) {
                MyLog.i(this, "Pre-executing $task")
                task.cancel()
                MyLog.i(this, "After cancel() $task")
                break
            }
        } while (stopWatch.notPassedSeconds(5))
        MyLog.i(this, "Before join() $task")
        task.job?.join()
        MyLog.i(this, "After join() $task")

        Assert.assertTrue("onPreExecute $task", task.onPreExecuteVal.get())
        Assert.assertFalse("inBackground $task", task.inBackgroundVal.get())
        Assert.assertTrue("onCancel $task", task.onCancelVal.get())
        Assert.assertNull("onPostExecute $task", task.onPostExecuteVal.get())
        Assert.assertNotNull("onFinish $task", task.onFinishVal.get())
    }

    @Test
    fun cancelDuringBackground() = runBlocking {
        val stopWatch = StopWatch.createStarted()
        val task = TestTask().apply { executeInContext(Dispatchers.Default, "") }
        MyLog.i(this, "Executing $task")
        do {
            if (task.inBackgroundVal.get()) {
                MyLog.i(this, "In background $task")
                task.cancel()
                MyLog.i(this, "After cancel() $task")
                break
            }
        } while (stopWatch.notPassedSeconds(5))
        MyLog.i(this, "Before join() $task")
        task.job?.join()
        MyLog.i(this, "After join() $task")

        Assert.assertTrue("onPreExecute $task", task.onPreExecuteVal.get())
        Assert.assertTrue("inBackground $task", task.inBackgroundVal.get())
        Assert.assertTrue("onCancel $task", task.onCancelVal.get())
        Assert.assertNull("onPostExecute $task", task.onPostExecuteVal.get())
        Assert.assertNotNull("onFinish $task", task.onFinishVal.get())
    }

    @Test
    fun externallyCancelledDuringBackground() = runBlocking {
        val stopWatch = StopWatch.createStarted()
        val task = TestTask().apply { executeInContext(Dispatchers.Default, "") }
        MyLog.i(this, "Executing $task")
        do {
            if (task.inBackgroundVal.get()) {
                MyLog.i(this, "In background $task")
                task.job?.cancel()
                MyLog.i(this, "After cancel() $task")
                break
            }
        } while (stopWatch.notPassedSeconds(5))
        MyLog.i(this, "Before join() $task")
        task.job?.join()
        delay(500)
        MyLog.i(this, "After join() $task")

        Assert.assertTrue("onPreExecute $task", task.onPreExecuteVal.get())
        Assert.assertTrue("inBackground $task", task.inBackgroundVal.get())
        Assert.assertTrue("onCancel $task", task.onCancelVal.get())
        Assert.assertNull("onPostExecute $task", task.onPostExecuteVal.get())
        Assert.assertNotNull("onFinish $task", task.onFinishVal.get())
    }

    @Test
    fun cancelDuringPostExecute() = runBlocking {
        val stopWatch = StopWatch.createStarted()
        val task = TestTask().apply { executeInContext(Dispatchers.Default, "") }
        MyLog.i(this, "Executing $task")
        do {
            if (task.onPostExecuteVal.get() != null) {
                MyLog.i(this, "In post executing $task")
                task.cancel()
                MyLog.i(this, "After cancel() $task")
                break
            }
        } while (stopWatch.notPassedSeconds(5))
        MyLog.i(this, "Before join() $task")
        task.job?.join()
        MyLog.i(this, "After join() $task")

        Assert.assertTrue("onPreExecute $task", task.onPreExecuteVal.get())
        Assert.assertTrue("inBackground $task", task.inBackgroundVal.get())
        Assert.assertTrue("onCancel $task", task.onCancelVal.get())
        Assert.assertNotNull("onPostExecute $task", task.onPostExecuteVal.get())
        Assert.assertNotNull("onFinish $task", task.onFinishVal.get())
    }

    @Test
    fun normalExecution() = runBlocking {
        val task = TestTask().apply { executeInContext(Dispatchers.Default, "") }
        MyLog.i(this, "Executing $task")
        task.job?.join()
        MyLog.i(this, "After join() $task")

        Assert.assertTrue("onPreExecute $task", task.onPreExecuteVal.get())
        Assert.assertTrue("inBackground $task", task.inBackgroundVal.get())
        Assert.assertFalse("onCancel $task", task.onCancelVal.get())
        Assert.assertNotNull("onPostExecute $task", task.onPostExecuteVal.get())
        Assert.assertNotNull("onFinish $task", task.onFinishVal.get())
    }

    @Test
    fun exceptionDuringBackground() = runBlocking {
        val task = TestTask().apply {
            exceptionDuringBackground = Exception("Something went wrong")
            executeInContext(Dispatchers.Default, "")
        }
        MyLog.i(this, "Executing $task")
        task.job?.join()
        MyLog.i(this, "After join() $task")

        Assert.assertTrue("onPreExecute $task", task.onPreExecuteVal.get())
        Assert.assertTrue("inBackground $task", task.inBackgroundVal.get())
        Assert.assertFalse("onCancel $task", task.onCancelVal.get())
        Assert.assertNull("onPostExecute $task", task.onPostExecuteVal.get())
        Assert.assertNotNull("onFinish $task", task.onFinishVal.get())
    }

}
