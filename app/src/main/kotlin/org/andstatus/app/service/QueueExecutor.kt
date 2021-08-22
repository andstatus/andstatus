package org.andstatus.app.service

import io.vavr.control.Try
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.os.AsyncEnum
import org.andstatus.app.os.AsyncResult
import org.andstatus.app.service.CommandQueue.AccessorType
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.RelativeTime
import org.andstatus.app.util.SharedPreferencesUtil
import org.andstatus.app.util.TryUtils
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicLong

class QueueExecutor(myService: MyService, val accessorType: AccessorType) :
        AsyncResult<Unit, Boolean>(QueueExecutor::class, AsyncEnum.SYNC), CommandExecutorParent {
    private val myServiceRef: WeakReference<MyService> = WeakReference(myService)
    val executedCounter: AtomicLong = AtomicLong()

    override suspend fun doInBackground(params: Unit): Try<Boolean> {
        val myService = myServiceRef.get()
        if (myService == null) {
            MyLog.v(this) { "Didn't start, no reference to MyService" }
            return TryUtils.TRUE
        }
        val accessor = myService.myContext.queues.getAccessor(accessorType)
        accessor.moveCommandsFromSkippedToMainQueue()
        MyLog.v(this) { "Started, to process in ${accessorType.title}:" + accessor.countToExecuteNow }
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
            if (!myService.executors.contains(this)) {
                breakReason = "Removed"
                break
            }
            val commandData = accessor.nextToExecute(this)
            if (commandData.isEmpty) {
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
            accessor.onPostExecute(commandData)
            myService.broadcastAfterExecutingCommand(commandData)
        } while (true)
        MyLog.v(this) { "Ended:$breakReason, left:" + accessor.countToExecuteNow + ", $this" }
        myService.myContext.queues.save()
        currentlyExecutingDescription = breakReason
        return TryUtils.TRUE
    }

    private fun addSyncAfterNoteWasSent(myService: MyService, commandDataExecuted: CommandData) {
        if (commandDataExecuted.getResult().hasError() ||
            commandDataExecuted.command != CommandEnum.UPDATE_NOTE ||
            !SharedPreferencesUtil.getBoolean(MyPreferences.KEY_SYNC_AFTER_NOTE_WAS_SENT, false)) {
            return
        }

        myService.myContext.queues.addToQueue(QueueType.CURRENT, CommandData.newTimelineCommand(CommandEnum.GET_TIMELINE,
                commandDataExecuted.getTimeline().myAccountToSync, TimelineType.SENT)
                .setInForeground(commandDataExecuted.isInForeground()))
    }

    override suspend fun onPostExecute(result: Try<Boolean>) {
        myServiceRef.get()?.onExecutorFinished()
    }

    override fun isStopping(): Boolean {
        return myServiceRef.get()?.isStopping() ?: true
    }

    override fun toString(): String {
        val sb = MyStringBuilder()
        if (isStopping()) {
            sb.withComma("stopping")
        }
        sb.withComma(accessorType.title)
        sb.withComma("commands executed", executedCounter.get())
        sb.withComma(super.toString())
        return sb.toString()
    }

    companion object {
        const val MAX_EXECUTION_TIME_SECONDS: Long = 60
    }
}
