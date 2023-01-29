package org.andstatus.app.service

import kotlinx.coroutines.sync.withLock
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.util.MyLog
import java.util.concurrent.TimeUnit

class QueueAccessor(private val cq: CommandQueue, val accessorType: CommandQueue.AccessorType) {

    fun isAnythingToExecuteNow(): Boolean = !cq.loaded ||
        isAnythingToExecuteNowIn(mainQueueType) ||
        isAnythingToExecuteNowIn(QueueType.SKIPPED) ||
        isAnythingToExecuteNowIn(QueueType.RETRY)

    val countToExecute: Int
        get() = countToExecuteIn(mainQueueType) +
            countToExecuteIn(QueueType.PRE) +
            countToExecuteIn(QueueType.SKIPPED) +
            countToExecuteIn(QueueType.RETRY)

    private fun isForAccessor(cd: CommandData): Boolean {
        val commandForDownloads = (cd.command == CommandEnum.GET_ATTACHMENT
                || cd.command == CommandEnum.GET_AVATAR)
        return (accessorType == CommandQueue.AccessorType.GENERAL) xor commandForDownloads
    }

    private val mainQueueType: QueueType
        get() = if (accessorType == CommandQueue.AccessorType.GENERAL) QueueType.CURRENT else QueueType.DOWNLOADS

    private fun countToExecuteIn(queueType: QueueType): Int =
        cq.get(queueType).count { it.mayBeExecuted(queueType) }

    private fun isAnythingToExecuteNowIn(queueType: QueueType): Boolean =
        cq.get(queueType).any { it.mayBeExecuted(queueType) }

    private fun CommandData.mayBeExecuted(queueType: QueueType): Boolean =
        isForAccessor(this) &&
            !skip(this) &&
            (queueType != QueueType.RETRY || isTimeToRetry)

    suspend fun nextToExecute(queueExecutor: QueueExecutor): CommandData {
        moveCommandsFromPreToMainQueue()
        CommandQueue.mutex.withLock {
            var commandData: CommandData
            do {
                commandData = cq.pollOrEmpty(mainQueueType)
                if (commandData.isEmpty) {
                    moveCommandsFromSkippedToMainQueue()
                    commandData = cq.pollOrEmpty(mainQueueType)
                }
                if (commandData.isEmpty) {
                    moveCommandsFromRetryToMainQueue()
                    commandData = cq.pollOrEmpty(mainQueueType)
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
                if (skip(commandData) && cq.addToQueue(QueueType.SKIPPED, commandData)) {
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
        return !commandData.command.connectionRequired.isConnectionStateOk(cq.myContext.connectionState)
    }

    suspend fun moveCommandsFromPreToMainQueue() {
        CommandQueue.mutex.withLock {
            cq[QueueType.PRE].forEachEx { cd ->
                if (cd.hasDuplicateInMainOrExecuting || addToMainOrSkipQueue(cd)) {
                    remove(cd)
                }
            }
        }
    }

    private val CommandData.hasDuplicateInMainOrExecuting: Boolean
        get() = this.hasDuplicateIn(QueueType.CURRENT) ||
            this.hasDuplicateIn(QueueType.DOWNLOADS) || this.hasDuplicateIn(QueueType.EXECUTING)

    private fun CommandData.hasDuplicateIn(queueType: QueueType): Boolean =
        this.nonEmpty && cq[queueType].contains(this).also {
            if (it) MyLog.v(this) {
                val existing = cq[queueType].find { it.equals(this) }
                "Duplicate found in $queueType for $this; existing: $existing"
            }
        }

    private fun moveCommandsFromSkippedToMainQueue() {
        cq[QueueType.SKIPPED].forEachEx { cd ->
            if (cd.hasDuplicateInMainOrExecuting) {
                remove(cd)
            } else if (!skip(cd)) {
                remove(cd)
                if (!addToMainOrSkipQueue(cd)) {
                    addToQueue(cd)
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
        cq[QueueType.RETRY].forEachEx { cd ->
            if (cd.mayBeExecuted(queueType) && addToMainOrSkipQueue(cd)) {
                remove(cd)
                MyLog.v(this) { "Moved from Retry to Main queue: $cd" }
            }
        }
    }

    private fun findInRetryQueue(cdIn: CommandData): CommandData {
        cq[QueueType.RETRY].forEachEx { cd ->
            if (cd == cdIn) {
                return if (cdIn.isManuallyLaunched() || cd.mayBeExecuted(QueueType.RETRY)) {
                    cd.resetRetries()
                    remove(cd)
                    MyLog.v(CommandQueue.TAG) { "Returned from Retry queue: $cd" }
                    cd
                } else {
                    MyLog.v(CommandQueue.TAG) { "Found in Retry queue, but left there: $cd" }
                    CommandData.EMPTY
                }
            }
        }
        return cdIn
    }

    private fun findInErrorQueue(cdIn: CommandData): CommandData {
        cq[QueueType.ERROR].forEachEx { cd ->
            if (cd == cdIn) {
                return if (cdIn.isManuallyLaunched()) {
                    remove(cd)
                    MyLog.v(CommandQueue.TAG) { "Returned from Error queue: $cd" }
                    cd.resetRetries()
                    cd
                } else {
                    MyLog.v(CommandQueue.TAG) { "Found in Error queue, but left there: $cd" }
                    CommandData.EMPTY
                }
            } else {
                if (cd.executedMoreSecondsAgoThan(MAX_SECONDS_IN_ERROR_QUEUE)) {
                    remove(cd)
                    MyLog.i(CommandQueue.TAG, "Removed old from Error queue: $cd")
                }
            }
        }
        return cdIn
    }

    fun onPostExecute(commandData: CommandData) {
        cq[QueueType.EXECUTING].remove(commandData)
    }

    companion object {
        const val MIN_RETRY_PERIOD_SECONDS: Long = 900
        private val MAX_SECONDS_IN_ERROR_QUEUE: Long = TimeUnit.DAYS.toSeconds(10)
    }

}
