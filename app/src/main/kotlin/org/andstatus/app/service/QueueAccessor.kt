package org.andstatus.app.service

import kotlinx.coroutines.sync.withLock
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.RelativeTime
import java.util.concurrent.TimeUnit

class QueueAccessor(private val cq: CommandQueue, val accessorType: CommandQueue.AccessorType) {

    fun isAnythingToExecuteNow(): Boolean = !cq.loaded ||
        isAnythingToExecuteNowIn(mainQueueType) ||
        isAnythingToExecuteNowIn(QueueType.SKIPPED) ||
        isTimeToProcessRetryQueue && isAnythingToExecuteNowIn(QueueType.RETRY)

    val countToExecuteNow: Long
        get() = (countToExecuteNowIn(mainQueueType) +
            countToExecuteNowIn(QueueType.PRE) +
            countToExecuteNowIn(QueueType.SKIPPED) +
            if (isTimeToProcessRetryQueue) countToExecuteNowIn(QueueType.RETRY) else 0)

    private fun isForAccessor(cd: CommandData): Boolean {
        val commandForDownloads = (cd.command == CommandEnum.GET_ATTACHMENT
                || cd.command == CommandEnum.GET_AVATAR)
        return (accessorType == CommandQueue.AccessorType.GENERAL) xor commandForDownloads
    }

    private val mainQueueType: QueueType
        get() = if (accessorType == CommandQueue.AccessorType.GENERAL) QueueType.CURRENT else QueueType.DOWNLOADS

    private val isTimeToProcessRetryQueue: Boolean
        get() = RelativeTime.moreSecondsAgoThan(
            cq.mRetryQueueProcessedAt.get(),
            RETRY_QUEUE_PROCESSING_PERIOD_SECONDS
        )

    private fun countToExecuteNowIn(queueType: QueueType): Long {
        var counter: Long = 0
        if (!cq.loaded) {
            return 0
        }
        val queue = cq.get(queueType).queue
        for (cd in queue) {
            if (isForAccessor(cd) && !skip(cd)) counter++
        }
        return counter
    }

    private fun isAnythingToExecuteNowIn(queueType: QueueType): Boolean {
        if (!cq.loaded) {
            return true
        }
        val queue = cq.get(queueType).queue
        for (cd in queue) {
            if (isForAccessor(cd) && !skip(cd)) return true
        }
        return false
    }

    suspend fun nextToExecute(queueExecutor: QueueExecutor): CommandData {
        moveCommandsFromPreToMainQueue()
        CommandQueue.mutex.withLock {
            var commandData: CommandData
            do {
                commandData = cq.get(mainQueueType).queue.poll() ?: CommandData.EMPTY
                if (commandData.isEmpty && isTimeToProcessRetryQueue && isAnythingToExecuteNowIn(QueueType.RETRY)) {
                    moveCommandsFromRetryToMainQueue()
                    commandData = cq.get(mainQueueType).queue.poll() ?: CommandData.EMPTY
                }
                if (commandData.isEmpty) {
                    break
                }
                commandData = findInRetryQueue(commandData)
                if (commandData.nonEmpty) {
                    commandData = findInErrorQueue(commandData)
                }
                if (commandData.hasDuplicateIn(QueueType.EXECUTING)) {
                    commandData = CommandData.EMPTY
                }
                if (skip(commandData)) {
                    if (!commandData.hasDuplicateIn(QueueType.SKIPPED)) {
                        cq.addToQueue(QueueType.SKIPPED, commandData)
                    }
                    commandData = CommandData.EMPTY
                }
            } while (commandData.isEmpty)

            if (commandData.nonEmpty) {
                MyLog.v(
                    queueExecutor, "ToExecute $accessorType in " +
                        (if (cq.myContext.isInForeground)
                            "foreground " + (if (MyPreferences.isSyncWhileUsingApplicationEnabled) "enabled" else "disabled")
                        else "background") +
                        ": " + commandData.toString()
                )
                cq.changed = true
                commandData.setManuallyLaunched(false)
                cq[QueueType.EXECUTING].addToQueue(commandData)
            }
            return commandData
        }
    }

    private fun skip(commandData: CommandData): Boolean {
        if (commandData.isEmpty) return false

        if (!commandData.isInForeground() && cq.myContext.isInForeground &&
            !MyPreferences.isSyncWhileUsingApplicationEnabled
        ) {
            return true
        }
        return !commandData.command.getConnectionRequired().isConnectionStateOk(cq.myContext.connectionState)
    }

    suspend fun moveCommandsFromPreToMainQueue() {
        CommandQueue.mutex.withLock {
            for (cd in CommandQueue.preQueue.queue) {
                if (cd.hasDuplicateInMainOrExecuting || addToMainOrSkipQueue(cd)) CommandQueue.preQueue.queue.remove(cd)
            }
        }
    }

    private val CommandData.hasDuplicateInMainOrExecuting: Boolean
        get() = this.hasDuplicateIn(QueueType.CURRENT) ||
            this.hasDuplicateIn(QueueType.DOWNLOADS) || this.hasDuplicateIn(QueueType.EXECUTING)

    private fun CommandData.hasDuplicateIn(queueType: QueueType): Boolean =
        this.nonEmpty && cq[queueType].queue.contains(this).also {
            if (it) MyLog.v("") { "Duplicate found in $queueType: $this" }
        }

    suspend fun moveCommandsFromSkippedToMainQueue() {
        CommandQueue.mutex.withLock {
            val queue = cq.get(QueueType.SKIPPED).queue
            for (cd in queue) {
                if (cd.hasDuplicateInMainOrExecuting) {
                    queue.remove(cd)
                } else if (!skip(cd)) {
                    queue.remove(cd)
                    if (!addToMainOrSkipQueue(cd)) {
                        queue.add(cd)
                    }
                }
            }
        }
    }

    /** @return true if command was added
     */
    private fun addToMainOrSkipQueue(commandData: CommandData): Boolean {
        if (!isForAccessor(commandData)) return false
        return if (skip(commandData)) {
            cq.addToQueue(QueueType.SKIPPED, commandData)
        } else {
            cq.addToQueue(mainQueueType, commandData)
        }
    }

    private fun moveCommandsFromRetryToMainQueue() {
        val queue = cq.get(QueueType.RETRY).queue
        for (cd in queue) {
            if (cd.isTimeToRetry && addToMainOrSkipQueue(cd)) {
                queue.remove(cd)
                cq.changed = true
                MyLog.v(CommandQueue.TAG) { "Moved from Retry to Main queue: $cd" }
            }
        }
        cq.mRetryQueueProcessedAt.set(System.currentTimeMillis())
    }

    private fun findInRetryQueue(cdIn: CommandData): CommandData {
        val queue = cq.get(QueueType.RETRY).queue
        var cdOut = cdIn
        if (queue.contains(cdIn)) {
            for (cd in queue) {
                if (cd == cdIn) {
                    cd.resetRetries()
                    if (cdIn.isManuallyLaunched() || cd.isTimeToRetry) {
                        cdOut = cd
                        queue.remove(cd)
                        cq.changed = true
                        MyLog.v(CommandQueue.TAG) { "Returned from Retry queue: $cd" }
                    } else {
                        cdOut = CommandData.EMPTY
                        MyLog.v(CommandQueue.TAG) { "Found in Retry queue, but left there: $cd" }
                    }
                    break
                }
            }
        }
        return cdOut
    }

    private fun findInErrorQueue(cdIn: CommandData): CommandData {
        val queue = cq.get(QueueType.ERROR).queue
        var cdOut = cdIn
        if (queue.contains(cdIn)) {
            for (cd in queue) {
                if (cd == cdIn) {
                    if (cdIn.isManuallyLaunched() || cd.isTimeToRetry) {
                        cdOut = cd
                        queue.remove(cd)
                        cq.changed = true
                        MyLog.v(CommandQueue.TAG) { "Returned from Error queue: $cd" }
                        cd.resetRetries()
                    } else {
                        cdOut = CommandData.EMPTY
                        MyLog.v(CommandQueue.TAG) { "Found in Error queue, but left there: $cd" }
                    }
                } else {
                    if (cd.executedMoreSecondsAgoThan(MAX_SECONDS_IN_ERROR_QUEUE)) {
                        queue.remove(cd)
                        cq.changed = true
                        MyLog.i(CommandQueue.TAG, "Removed old from Error queue: $cd")
                    }
                }
            }
        }
        return cdOut
    }

    fun onPostExecute(commandData: CommandData) {
        cq[QueueType.EXECUTING].queue.remove(commandData)
    }

    companion object {
        private const val RETRY_QUEUE_PROCESSING_PERIOD_SECONDS: Long = 900
        const val MIN_RETRY_PERIOD_SECONDS: Long = 900
        private val MAX_SECONDS_IN_ERROR_QUEUE: Long = TimeUnit.DAYS.toSeconds(10)
    }

}
