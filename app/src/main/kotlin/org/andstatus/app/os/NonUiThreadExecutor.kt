package org.andstatus.app.os

import java.util.concurrent.Executor

class NonUiThreadExecutor : Executor {
    override fun execute(command: Runnable) {
        if (AsyncTask.isUiThread) {
            AsyncTaskLauncher.execute(command)
        } else {
            command.run()
        }
    }

    companion object {
        val INSTANCE: NonUiThreadExecutor = NonUiThreadExecutor()
    }
}
