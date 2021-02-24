package org.andstatus.app.os

import android.os.AsyncTask
import java.util.concurrent.Executor

class NonUiThreadExecutor : Executor {
    override fun execute(command: Runnable) {
        if (MyAsyncTask.isUiThread()) {
            AsyncTask.THREAD_POOL_EXECUTOR.execute(command)
        } else {
            command.run()
        }
    }

    companion object {
        val INSTANCE: NonUiThreadExecutor? = NonUiThreadExecutor()
    }
}