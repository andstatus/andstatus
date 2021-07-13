package org.andstatus.app.service

import kotlinx.coroutines.sync.withLock
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.RelativeTime
import java.util.concurrent.TimeUnit

class QueueAccessor(private val cq: CommandQueue, val accessorType: CommandQueue.AccessorType) {
    fun isAnythingToExecuteNow(): Boolean {
        moveCommandsFromPreToMainQueue()
        return (!cq.loaded
            || isAnythingToExecuteNowIn(mainQueueType())
            || isAnythingToExecuteNowIn(QueueType.SKIPPED)
            || isTimeToProcessRetryQueue() && isAnythingToExecuteNowIn(QueueType.RETRY))
    }

    fun countToExecuteNow(): Long {
        return (countToExecuteNowIn(mainQueueType()) +
            countToExecuteNowIn(QueueType.PRE) +
            countToExecuteNowIn(QueueType.SKIPPED) +
            if (isTimeToProcessRetryQueue()) countToExecuteNowIn(QueueType.RETRY) else 0)
    }

    private fun isForAccessor(cd: CommandData): Boolean {
        val commandForDownloads = (cd.command == CommandEnum.GET_ATTACHMENT
                || cd.command == CommandEnum.GET_AVATAR)
        return (accessorType == CommandQueue.AccessorType.GENERAL) xor commandForDownloads
    }

    private fun mainQueueType(): QueueType {
        return if (accessorType == CommandQueue.AccessorType.GENERAL) QueueType.CURRENT else QueueType.DOWNLOADS
    }

    private fun isTimeToProcessRetryQueue(): Boolean {
        return RelativeTime.moreSecondsAgoThan(
            cq.mRetryQueueProcessedAt.get(),
            RETRY_QUEUE_PROCESSING_PERIOD_SECONDS
        )
    }

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

    suspend fun nextToExecute(queueExecutor: QueueExecutor): CommandData? {
        moveCommandsFromPreToMainQueue()
        var commandData: CommandData?
        do {
            commandData = cq.get(mainQueueType()).queue.poll()
            if (commandData == null && isTimeToProcessRetryQueue() && isAnythingToExecuteNowIn(QueueType.RETRY)) {
                moveCommandsFromRetryToMainQueue()
                commandData = cq.get(mainQueueType()).queue.poll()
            }
            if (commandData == null) {
                break
            }
            commandData = findInRetryQueue(commandData)
            if (commandData != CommandData.EMPTY) {
                commandData = findInErrorQueue(commandData)
            }
            if (skip(commandData)) {
                cq.addToQueue(QueueType.SKIPPED, commandData)
                commandData = null
            }
        } while (commandData == null || commandData == CommandData.EMPTY)
        if (commandData == CommandData.EMPTY) commandData = null

        MyLog.v(
            queueExecutor, "ToExecute $accessorType in " +
                (if (cq.myContext.isInForeground) "foreground " +
                    (if (MyPreferences.isSyncWhileUsingApplicationEnabled()) "enabled" else "disabled")
                else "background") +
                ": " + (commandData?.toString() ?: "(no command)")
        )
        if (commandData != null) {
            cq.changed = true
            commandData.setManuallyLaunched(false)

            cq[QueueType.EXECUTING].addToQueue(commandData)
        }
        return commandData
    }

    private fun skip(commandData: CommandData?): Boolean {
        if (commandData == null || commandData == CommandData.EMPTY) return false

        if (!commandData.isInForeground() && cq.myContext.isInForeground
            && !MyPreferences.isSyncWhileUsingApplicationEnabled()
        ) {
            return true
        }
        return if (!commandData.command.getConnectionRequired()
                .isConnectionStateOk(cq.myContext.connectionState)
        ) {
            true
        } else false
    }

    fun moveCommandsFromPreToMainQueue() {
        for (cd in CommandQueue.preQueue.queue) {
            if (cq[mainQueueType()].queue.contains(cd) || cq[QueueType.EXECUTING].queue.contains(cd)) {
                MyLog.v("") {
                    "FromPreToMain; Didn't add duplicate, removing: $cd"
                }
                CommandQueue.preQueue.queue.remove(cd)
            } else if (addToMainOrSkipQueue(cd)) {
                CommandQueue.preQueue.queue.remove(cd)
            }
        }
    }

    suspend fun moveCommandsFromSkippedToMainQueue() {
        CommandQueue.mutex.withLock {
            val queue = cq.get(QueueType.SKIPPED).queue
            for (cd in queue) {
                if (!skip(cd)) {
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
            cq.addToQueue(mainQueueType(), commandData)
        }
    }

    private suspend fun moveCommandsFromRetryToMainQueue() {
        CommandQueue.mutex.withLock {
            val queue = cq.get(QueueType.RETRY).queue
            for (cd in queue) {
                if (cd.executedMoreSecondsAgoThan(MIN_RETRY_PERIOD_SECONDS) && addToMainOrSkipQueue(cd)) {
                    queue.remove(cd)
                    cq.changed = true
                    MyLog.v(CommandQueue.TAG) { "Moved from Retry to Main queue: $cd" }
                }
            }
            cq.mRetryQueueProcessedAt.set(System.currentTimeMillis())
        }
    }

    private fun findInRetryQueue(cdIn: CommandData): CommandData {
        val queue = cq.get(QueueType.RETRY).queue
        var cdOut = cdIn
        if (queue.contains(cdIn)) {
            for (cd in queue) {
                if (cd == cdIn) {
                    cd.resetRetries()
                    if (cdIn.isManuallyLaunched() || cd.executedMoreSecondsAgoThan(MIN_RETRY_PERIOD_SECONDS)) {
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
                    if (cdIn.isManuallyLaunched() || cd.executedMoreSecondsAgoThan(MIN_RETRY_PERIOD_SECONDS)) {
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
                    if (cd.executedMoreSecondsAgoThan(TimeUnit.DAYS.toSeconds(MAX_DAYS_IN_ERROR_QUEUE))) {
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
        private const val MIN_RETRY_PERIOD_SECONDS: Long = 900
        private const val MAX_DAYS_IN_ERROR_QUEUE: Long = 10
    }

}
