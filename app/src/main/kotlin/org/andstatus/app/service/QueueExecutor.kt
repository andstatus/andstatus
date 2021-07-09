package org.andstatus.app.service

import io.vavr.control.Try
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.os.AsyncTask
import org.andstatus.app.service.CommandQueue.AccessorType
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.RelativeTime
import org.andstatus.app.util.SharedPreferencesUtil
import org.andstatus.app.util.TryUtils
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicLong

class QueueExecutor(myService: MyService, private val accessorType: AccessorType) :
        AsyncTask<Unit, Void?, Boolean>("$TAG-$accessorType", PoolEnum.SYNC), CommandExecutorParent {
    private val myServiceRef: WeakReference<MyService> = WeakReference(myService)
    private val executedCounter: AtomicLong = AtomicLong()

    override fun instanceTag(): String {
        return super.instanceTag() + "-" + accessorType
    }

    override suspend fun doInBackground(params: Unit): Try<Boolean> {
        val myService = myServiceRef.get()
        if (myService == null) {
            MyLog.v(this) { "Didn't start, no reference to MyService" }
            return TryUtils.TRUE
        }
        val accessor = myService.myContext.queues.getAccessor(accessorType)
        accessor.moveCommandsFromSkippedToMainQueue()
        MyLog.v(this) { "Started, " + accessor.countToExecuteNow() + " commands to process" }
        val breakReason: String
        do {
            if (isStopping()) {
                breakReason = "isStopping"
                break
            }
            if (isCancelled) {
                breakReason = "Cancelled"
                break
            }
            if (RelativeTime.secondsAgo(backgroundStartedAt.get()) > MAX_EXECUTION_TIME_SECONDS) {
                breakReason = "Executed too long"
                break
            }
            if (myService.executors.getRef(accessorType).get() !== this) {
                breakReason = "Other executor"
                break
            }
            val commandData = accessor.pollQueue()
            if (commandData == null || commandData == CommandData.EMPTY) {
                breakReason = "No more commands"
                break
            }
            executedCounter.incrementAndGet()
            currentlyExecutingSince.set(System.currentTimeMillis())
            currentlyExecutingDescription = commandData.toString()
            myService.broadcastBeforeExecutingCommand(commandData)
            CommandExecutorStrategy.executeCommand(commandData, this)
            when {
                commandData.getResult().shouldWeRetry() -> {
                    myService.myContext.queues.addToQueue(QueueType.RETRY, commandData)
                }
                commandData.getResult().hasError() -> {
                    myService.myContext.queues.addToQueue(QueueType.ERROR, commandData)
                }
                else -> addSyncAfterNoteWasSent(myService, commandData)
            }
            myService.broadcastAfterExecutingCommand(commandData)
        } while (true)
        MyLog.v(this) { "Ended, cause:$breakReason, " + executedCounter.get() + " commands executed, " + accessor.countToExecuteNow() + " left" }
        myService.myContext.queues.save()
        currentlyExecutingSince.set(0)
        currentlyExecutingDescription = breakReason
        return TryUtils.TRUE
    }

    private fun addSyncAfterNoteWasSent(myService: MyService, commandDataExecuted: CommandData) {
        if (commandDataExecuted.getResult().hasError()
                || commandDataExecuted.command != CommandEnum.UPDATE_NOTE || !SharedPreferencesUtil.getBoolean(
                        MyPreferences.KEY_SYNC_AFTER_NOTE_WAS_SENT, false)) {
            return
        }
        myService.myContext.queues.addToQueue(QueueType.CURRENT, CommandData.newTimelineCommand(CommandEnum.GET_TIMELINE,
                commandDataExecuted.getTimeline().myAccountToSync, TimelineType.SENT)
                .setInForeground(commandDataExecuted.isInForeground()))
    }

    override suspend fun onPostExecute(result: Try<Boolean>) {
        val myService = myServiceRef.get()
        if (myService != null) {
            myService.latestActivityTime = System.currentTimeMillis()
        }
        MyLog.v(this, if (result.isSuccess) "onExecutorSuccess" else "onExecutorFailure")
        currentlyExecutingSince.set(0)
        if (myService != null) {
            myService.reviveHeartBeat()
            myService.startStopExecution()
        }
    }

    override fun isStopping(): Boolean {
        return myServiceRef.get()?.isStopping() ?: true
    }

    override fun classTag(): String {
        return TAG
    }

    override fun toString(): String {
        val sb = MyStringBuilder()
        if (isStopping()) {
            sb.withComma("stopping")
        }
        sb.withComma(super.toString())
        return sb.toKeyValue(this)
    }

    companion object {
        const val MAX_EXECUTION_TIME_SECONDS: Long = 60
        private const val TAG: String = "QueueExecutor"
    }
}
