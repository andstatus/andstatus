package org.andstatus.app.os

import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** We need this as long as we use CompletableFuture (to be replaced with coroutines) */
class NonUiThreadExecutor : Executor {
    override fun execute(command: Runnable) {
        if (AsyncTask.isUiThread) {
            executor.execute(command)
        } else {
            command.run()
        }
    }

    companion object {
        val INSTANCE: NonUiThreadExecutor = NonUiThreadExecutor()
        private val executor = ThreadPoolExecutor(1, 3, 1, TimeUnit.SECONDS,
            LinkedBlockingQueue(512))
    }
}
