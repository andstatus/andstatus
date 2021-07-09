package org.andstatus.app.service

import io.vavr.control.Try
import kotlinx.coroutines.delay
import org.andstatus.app.os.AsyncTaskLauncher
import org.andstatus.app.os.MyAsyncTask
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.RelativeTime
import org.andstatus.app.util.TryUtils
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit

class MyServiceHeartBeat constructor(myService: MyService) : MyAsyncTask<Void?, Long, Void>(TAG, PoolEnum.SYNC) {
    private val myServiceRef: WeakReference<MyService> = WeakReference(myService)

    @Volatile
    private var previousBeat = createdAt

    @Volatile
    private var mIteration: Long = 0

    override suspend fun doInBackground(params: Void?): Try<Void> {
        MyLog.v(this) { "Started" }
        var breakReason = ""
        for (iteration in 1..9999) {
            val myService = myServiceRef.get()
            if (myService == null) {
                breakReason = "No reference to MyService"
                break
            }
            val heartBeat = myService.heartBeatRef.get()
            if (heartBeat != null && heartBeat !== this && heartBeat.isReallyWorking) {
                breakReason = "Other instance found: $heartBeat"
                break
            }
            if (isCancelled) {
                breakReason = "Cancelled"
                break
            }
            delay(TimeUnit.SECONDS.toMillis(HEARTBEAT_PERIOD_SECONDS))
            if (!myService.initialized.get()) {
                breakReason = "Not initialized"
                break
            }
            publishProgress(iteration.toLong())
        }
        val breakReasonVal = breakReason
        MyLog.v(this) { "Ended $breakReasonVal; $this" }
        val myService = myServiceRef.get()
        myService?.heartBeatRef?.compareAndSet(this, null)
        return TryUtils.SUCCESS
    }

    override suspend fun onProgressUpdate(values: Long) {
        mIteration = values
        previousBeat = MyLog.uniqueCurrentTimeMS()
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(this) { "onProgressUpdate; $this" }
        }
        if (MyLog.isDebugEnabled() && RelativeTime.moreSecondsAgoThan(
                createdAt,
                QueueExecutor.MAX_EXECUTION_TIME_SECONDS
            )
        ) {
            MyLog.d(this, AsyncTaskLauncher.threadPoolInfo())
        }
        val myService = myServiceRef.get()
        myService?.startStopExecution()
    }

    override fun toString(): String {
        return instanceTag() + "; " + super.toString()
    }

    override fun instanceTag(): String {
        return super.instanceTag() + "-it" + mIteration
    }

    override fun classTag(): String {
        return TAG
    }

    override val isReallyWorking: Boolean
        get() {
            return needsBackgroundWork && !RelativeTime.wasButMoreSecondsAgoThan(
                previousBeat,
                HEARTBEAT_PERIOD_SECONDS * 2
            )
        }

    companion object {
        private const val TAG: String = "HeartBeat"
        private const val HEARTBEAT_PERIOD_SECONDS: Long = 11
    }

}
