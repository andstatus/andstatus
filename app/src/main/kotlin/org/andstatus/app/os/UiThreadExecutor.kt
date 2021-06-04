package org.andstatus.app.os

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executor

/** See https://stackoverflow.com/questions/12850143/android-basics-running-code-in-the-ui-thread  */
class UiThreadExecutor : Executor {
    override fun execute(command: Runnable) {
        Handler(Looper.getMainLooper()).post(command)
    }

    companion object {
        val INSTANCE: UiThreadExecutor = UiThreadExecutor()
    }
}
