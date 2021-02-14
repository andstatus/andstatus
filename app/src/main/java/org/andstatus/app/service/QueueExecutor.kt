package org.andstatus.app.service

import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.os.MyAsyncTask
import org.andstatus.app.service.CommandEnum
import org.andstatus.app.service.CommandQueue.AccessorType
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.RelativeTime
import org.andstatus.app.util.SharedPreferencesUtil
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicLong

internal class QueueExecutor(myService: MyService?, accessorType: AccessorType?) : MyAsyncTask<Void?, Void?, Boolean?>(TAG + "-" + accessorType, PoolEnum.SYNC), CommandExecutorParent {
    private val myServiceRef: WeakReference<MyService?>?
    private val accessorType: AccessorType?
    private val executedCounter: AtomicLong? = AtomicLong()
    override fun instanceTag(): String? {
        return super.instanceTag() + "-" + accessorType
    }

    override fun doInBackground2(aVoid: Void?): Boolean? {
        val myService = myServiceRef.get()
        if (myService == null) {
            MyLog.v(this) { "Didn't start, no reference to MyService" }
            return true
        }
        val accessor = myService.myContext.queues().getAccessor(accessorType)
        accessor.moveCommandsFromSkippedToMainQueue()
        MyLog.v(this) { "Started, " + accessor.countToExecuteNow() + " commands to process" }
        val breakReason: String
        do {
            if (isStopping) {
                breakReason = "isStopping"
                break
            }
            if (isCancelled) {
                breakReason = "Cancelled"
                break
            }
            if (RelativeTime.secondsAgo(backgroundStartedAt) > MAX_EXECUTION_TIME_SECONDS) {
                breakReason = "Executed too long"
                break
            }
            if (myService.executors.getRef(accessorType).get() !== this) {
                breakReason = "Other executor"
                break
            }
            val commandData = accessor.pollQueue()
            if (commandData == null) {
                breakReason = "No more commands"
                break
            }
            executedCounter.incrementAndGet()
            currentlyExecutingSince = System.currentTimeMillis()
            currentlyExecutingDescription = commandData.toString()
            myService.broadcastBeforeExecutingCommand(commandData)
            CommandExecutorStrategy.Companion.executeCommand(commandData, this)
            if (commandData.result.shouldWeRetry()) {
                myService.myContext.queues().addToQueue(QueueType.RETRY, commandData)
            } else if (commandData.result.hasError()) {
                myService.myContext.queues().addToQueue(QueueType.ERROR, commandData)
            } else {
                addSyncAfterNoteWasSent(myService, commandData)
            }
            myService.broadcastAfterExecutingCommand(commandData)
        } while (true)
        MyLog.v(this) { "Ended, " + executedCounter.get() + " commands executed, " + accessor.countToExecuteNow() + " left" }
        myService.myContext.queues().save()
        currentlyExecutingSince = 0
        currentlyExecutingDescription = breakReason
        return true
    }

    private fun addSyncAfterNoteWasSent(myService: MyService?, commandDataExecuted: CommandData?) {
        if (commandDataExecuted.getResult().hasError()
                || commandDataExecuted.getCommand() != CommandEnum.UPDATE_NOTE || !SharedPreferencesUtil.getBoolean(
                        MyPreferences.KEY_SYNC_AFTER_NOTE_WAS_SENT, false)) {
            return
        }
        myService.myContext.queues().addToQueue(QueueType.CURRENT, CommandData.Companion.newTimelineCommand(CommandEnum.GET_TIMELINE,
                commandDataExecuted.getTimeline().myAccountToSync, TimelineType.SENT)
                .setInForeground(commandDataExecuted.isInForeground()))
    }

    override fun onFinish(aBoolean: Boolean?, success: Boolean) {
        val myService = myServiceRef.get()
        if (myService != null) {
            myService.latestActivityTime = System.currentTimeMillis()
        }
        MyLog.v(this, if (success) "onExecutorSuccess" else "onExecutorFailure")
        currentlyExecutingSince = 0
        if (myService != null) {
            myService.reviveHeartBeat()
            myService.startStopExecution()
        }
    }

    override fun isStopping(): Boolean {
        val myService = myServiceRef.get()
        return myService == null || myService.isStopping
    }

    override fun classTag(): String? {
        return TAG
    }

    override fun toString(): String {
        val sb = MyStringBuilder()
        if (currentlyExecutingSince == 0L) {
            sb.withComma("notExecuting", currentlyExecutingDescription)
        } else {
            sb.withComma("executing", currentlyExecutingDescription)
            sb.withComma("since", RelativeTime.getDifference(MyContextHolder.Companion.myContextHolder.getNow().context(), currentlyExecutingSince))
        }
        if (isStopping) {
            sb.withComma("stopping")
        }
        sb.withComma(super.toString())
        return sb.toKeyValue(this)
    }

    companion object {
        const val MAX_EXECUTION_TIME_SECONDS: Long = 60
        private val TAG: String? = "QueueExecutor"
    }

    init {
        myServiceRef = WeakReference(myService)
        this.accessorType = accessorType
    }
}