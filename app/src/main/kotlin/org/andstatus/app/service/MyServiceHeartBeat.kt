package org.andstatus.app.service

import org.andstatus.app.data.DbUtils
import org.andstatus.app.os.AsyncTaskLauncher
import org.andstatus.app.os.MyAsyncTask
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.RelativeTime
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit

class MyServiceHeartBeat constructor(myService: MyService) : MyAsyncTask<Void?, Long?, Void?>(
    TAG,
    PoolEnum.SYNC
) {
    private val myServiceRef: WeakReference<MyService>

    @Volatile
    private var previousBeat = createdAt

    @Volatile
    private var mIteration: Long = 0
    override fun doInBackground(aVoid: Void?): Void? {
        MyLog.v(this) { "Started" }
        var breakReason = ""
        for (iteration in 1..9999) {
            val myService = myServiceRef.get()
            if (myService == null) {
                breakReason = "No reference to MyService"
                break
            }
            val heartBeat = myService.heartBeatRef.get()
            if (heartBeat != null && heartBeat !== this && heartBeat.isReallyWorking()) {
                breakReason = "Other instance found: $heartBeat"
                break
            }
            if (isCancelled) {
                breakReason = "Cancelled"
                break
            }
            if (DbUtils.waitMs(
                    "HeartBeatSleeping",
                    Math.toIntExact(TimeUnit.SECONDS.toMillis(HEARTBEAT_PERIOD_SECONDS))
                )
            ) {
                breakReason = "InterruptedException"
                break
            }
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
        return null
    }

    override fun onProgressUpdate(vararg values: Long?) {
        mIteration = values[0] ?: 0
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

    override fun isReallyWorking(): Boolean {
        return needsBackgroundWork() && !RelativeTime.wasButMoreSecondsAgoThan(
            previousBeat,
            HEARTBEAT_PERIOD_SECONDS * 2
        )
    }

    companion object {
        private val TAG: String = "HeartBeat"
        private const val HEARTBEAT_PERIOD_SECONDS: Long = 11
    }

    init {
        myServiceRef = WeakReference(myService)
    }
}
