/*
 * Copyright (c) 2016-2018 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.andstatus.app.service

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import io.vavr.control.Try
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.data.DbUtils
import org.andstatus.app.data.DbUtils.closeSilently
import org.andstatus.app.database.table.CommandTable
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.RelativeTime
import org.andstatus.app.util.StopWatch
import org.andstatus.app.util.TryUtils
import java.util.*
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

/**
 * @author yvolk@yurivolkov.com
 */
class CommandQueue(private val myContext: MyContext) {
    private val mRetryQueueProcessedAt: AtomicLong = AtomicLong()
    private val queues: MutableMap<QueueType, OneQueue> = HashMap()
    private val generalAccessor: Accessor
    private val accessors: MutableMap<AccessorType, Accessor> = HashMap()

    @Volatile
    private var loaded = false

    @Volatile
    private var changed = false

    class OneQueue(val queueType: QueueType) {
        var queue: Queue<CommandData> = PriorityBlockingQueue(INITIAL_CAPACITY)
        fun clear() {
            queue.clear()
        }

        fun isEmpty(): Boolean {
            return queue.isEmpty()
        }

        private fun hasForegroundTasks(): Boolean {
            for (commandData in queue) {
                if (commandData.isInForeground()) {
                    return true
                }
            }
            return false
        }

        fun addToQueue(commandData: CommandData): Boolean {
            when (commandData.command) {
                CommandEnum.EMPTY, CommandEnum.UNKNOWN -> {
                    MyLog.v(TAG) { "Didn't add unknown command to $queueType queue: $commandData" }
                    return true
                }
                else -> {
                }
            }
            if (queueType.onAddRemoveExisting) {
                if (queue.remove(commandData)) {
                    MyLog.v(TAG) { "Removed equal command from $queueType queue" }
                }
            } else {
                if (queue.contains(commandData)) {
                    MyLog.v(TAG) { "Didn't add to $queueType queue. Already found $commandData" }
                    return true
                }
            }
            if (queueType == QueueType.CURRENT && queueType == QueueType.DOWNLOADS) {
                commandData.getResult().prepareForLaunch()
            }
            MyLog.v(TAG) { "Adding to $queueType queue $commandData" }
            if (queue.offer(commandData)) {
                return true
            }
            MyLog.e(TAG, "Couldn't add to the " + queueType + " queue, size=" + queue.size)
            return false
        }

        fun size(): Int {
            return queue.size
        }
    }

    fun getAccessor(accessorType: AccessorType): Accessor {
        val accessor = accessors.get(accessorType)
        return accessor ?: generalAccessor
    }

    operator fun get(queueType: QueueType): OneQueue {
        return queues.get(queueType) ?: throw IllegalArgumentException("Unknown queueType $queueType")
    }

    @Synchronized
    fun load(): CommandQueue {
        if (loaded) {
            MyLog.v(TAG, "Already loaded")
        } else {
            val stopWatch: StopWatch = StopWatch.createStarted()
            val count = (load(QueueType.CURRENT) + load(QueueType.DOWNLOADS)
                    + load(QueueType.SKIPPED) + load(QueueType.RETRY))
            val countError = load(QueueType.ERROR)
            MyLog.i(TAG, "commandQueueInitializedMs:" + stopWatch.time + ";"
                    + (if (count > 0) Integer.toString(count) else " no") + " msg in queues"
                    + if (countError > 0) ", plus $countError in Error queue" else ""
            )
            loaded = true
        }
        return this
    }

    /** @return Number of items loaded
     */
    private fun load(queueType: QueueType): Int {
        val method = "loadQueue-" + queueType.save()
        val oneQueue = get(queueType)
        val queue = oneQueue.queue
        var count = 0
        val db = myContext.getDatabase()
        if (db == null) {
            MyLog.d(TAG, "$method; Database is unavailable")
            return 0
        }
        val sql = ("SELECT * FROM " + CommandTable.TABLE_NAME + " WHERE " + CommandTable.QUEUE_TYPE + "='"
                + queueType.save() + "'")
        var c: Cursor? = null
        try {
            c = db.rawQuery(sql, null)
            while (c.moveToNext()) {
                val cd: CommandData = CommandData.fromCursor(myContext, c)
                if (CommandEnum.EMPTY == cd.command) {
                    MyLog.w(TAG, "$method; empty skipped $cd")
                } else if (queue.contains(cd)) {
                    MyLog.w(TAG, "$method; duplicate skipped $cd")
                } else {
                    if (queue.offer(cd)) {
                        count++
                        if (MyLog.isVerboseEnabled() && (count < 6 || cd.command == CommandEnum.UPDATE_NOTE)) {
                            MyLog.v(TAG, "$method; $count: $cd")
                        }
                    } else {
                        MyLog.e(TAG, "$method; Couldn't edd to queue $cd")
                    }
                }
            }
        } finally {
            closeSilently(c)
        }
        MyLog.d(TAG, "$method; loaded $count commands from '$queueType'")
        return count
    }

    @Synchronized
    fun save() {
        if (!changed && preQueue.isEmpty()) {
            MyLog.v(TAG) { "save; Nothing to save. changed:" + changed + "; preQueueIsEmpty:" + preQueue.isEmpty() }
            return
        }
        val db = myContext.getDatabase()
        if (db == null) {
            MyLog.d(TAG, "save; Database is unavailable")
            return
        }
        if (!myContext.isReady() && !myContext.isExpired) {
            MyLog.d(TAG, "save; Cannot save: context is " + myContext.state())
            return
        }
        accessors.values.forEach(Consumer { obj: Accessor -> obj.moveCommandsFromPreToMainQueue() })
        if (loaded) clearQueuesInDatabase(db)
        val countNotError = save(db, QueueType.CURRENT)
                .flatMap { i1: Int -> save(db, QueueType.DOWNLOADS).map { i2: Int -> i1 + i2 } }
                .flatMap { i1: Int -> save(db, QueueType.SKIPPED).map { i2: Int -> i1 + i2 } }
                .flatMap { i1: Int -> save(db, QueueType.RETRY).map { i2: Int -> i1 + i2 } }
        val countError = save(db, QueueType.ERROR)
        MyLog.d(TAG, (if (loaded) "Queues saved" else "Saved new queued commands only") + ", "
                + if (countNotError.isFailure || countError.isFailure()) " Error saving commands!" else (if (countNotError.get() > 0) Integer.toString(countNotError.get()) else "no") + " commands"
                + if (countError.get() > 0) ", plus " + countError.get() + " in Error queue" else ""
        )
        changed = false
    }

    /** @return Number of items persisted
     */
    private fun save(db: SQLiteDatabase, queueType: QueueType): Try<Int> {
        val method = "saveQueue-" + queueType.save()
        val oneQueue = get(queueType)
        val queue = oneQueue.queue
        var count = 0
        try {
            if (!queue.isEmpty()) {
                val commands: MutableList<CommandData?> = ArrayList()
                while (!queue.isEmpty() && count < 300) {
                    val cd = queue.poll()
                    if (cd != null) {
                        val values = ContentValues()
                        cd.toContentValues(values)
                        values.put(CommandTable.QUEUE_TYPE, queueType.save())
                        db.insert(CommandTable.TABLE_NAME, null, values)
                        count++
                        commands.add(cd)
                        if (MyLog.isVerboseEnabled() && (count < 6 || cd.command == CommandEnum.UPDATE_NOTE)) {
                            MyLog.v(TAG, method + "; " + count + ": " + cd.toString())
                        }
                        if (myContext.isTestRun() && queue.contains(cd)) {
                            MyLog.e(TAG, method + "; Duplicated command in a queue:" + count + " " + cd.toString())
                        }
                    }
                }
                val left = queue.size
                // And add all commands back to the queue, so we won't need to reload them from a database
                commands.forEach(Consumer { e: CommandData? -> queue.offer(e) })
                MyLog.d(TAG, method + "; " + count + " saved" +
                        if (left == 0) " all" else ", $left left")
            }
        } catch (e: Exception) {
            val msgLog = method + "; " + count + " saved, " + queue.size + " left."
            MyLog.e(TAG, msgLog, e)
            return TryUtils.failure(msgLog, e)
        }
        return Try.success(count)
    }

    @Synchronized
    private fun clearQueuesInDatabase(db: SQLiteDatabase): Try<Void> {
        val method = "clearQueuesInDatabase"
        try {
            val sql = "DELETE FROM " + CommandTable.TABLE_NAME
            DbUtils.execSQL(db, sql)
        } catch (e: Exception) {
            MyLog.e(TAG, method, e)
            return TryUtils.failure(method, e)
        }
        return TryUtils.SUCCESS
    }

    fun clear(): Try<Void> {
        loaded = true
        for ((_, value) in queues) {
            value.clear()
        }
        preQueue.clear()
        changed = true
        save()
        MyLog.v(TAG, "Queues cleared")
        return TryUtils.SUCCESS
    }

    fun deleteCommand(commandData: CommandData): Try<Void> {
        for (oneQueue in queues.values) {
            if (commandData.deleteFromQueue(oneQueue)) {
                changed = true
            }
        }
        if (commandData.getResult().getDownloadedCount() == 0L) {
            commandData.getResult().incrementParseExceptions()
            commandData.getResult().setMessage("Didn't delete command #" + commandData.itemId)
        }
        commandData.getResult().afterExecutionEnded()
        return TryUtils.SUCCESS
    }

    fun totalSizeToExecute(): Int {
        var size = 0
        for ((key, value) in queues) {
            if (key.isExecutable()) {
                size += value.size()
            }
        }
        return size + preQueue.size()
    }

    /** @return true if success
     */
    fun addToQueue(queueTypeIn: QueueType, commandData: CommandData): Boolean {
        if (get(queueTypeIn).addToQueue(commandData)) {
            changed = true
            return true
        }
        return false
    }

    fun isAnythingToExecuteNow(): Boolean {
        return accessors.values.stream().anyMatch { obj: Accessor -> obj.isAnythingToExecuteNow() }
    }

    enum class AccessorType {
        GENERAL, DOWNLOADS
    }

    class Accessor(private val cq: CommandQueue, val accessorType: AccessorType) {
        fun isAnythingToExecuteNow(): Boolean {
            moveCommandsFromPreToMainQueue()
            return (!cq.loaded
                    || isAnythingToExecuteNowIn(mainQueueType())
                    || isAnythingToExecuteNowIn(QueueType.SKIPPED)
                    || isTimeToProcessRetryQueue() && isAnythingToExecuteNowIn(QueueType.RETRY))
        }

        fun countToExecuteNow(): Long {
            return (countToExecuteNowIn(mainQueueType())
                    + countToExecuteNowIn(QueueType.PRE)
                    + countToExecuteNowIn(QueueType.SKIPPED)
                    + if (isTimeToProcessRetryQueue()) countToExecuteNowIn(QueueType.RETRY) else 0)
        }

        private fun isForAccessor(cd: CommandData): Boolean {
            val commandForDownloads = (cd.command == CommandEnum.GET_ATTACHMENT
                    || cd.command == CommandEnum.GET_AVATAR)
            return (accessorType == AccessorType.GENERAL) xor commandForDownloads
        }

        private fun mainQueueType(): QueueType {
            return if (accessorType == AccessorType.GENERAL) QueueType.CURRENT else QueueType.DOWNLOADS
        }

        private fun isTimeToProcessRetryQueue(): Boolean {
            return RelativeTime.moreSecondsAgoThan(cq.mRetryQueueProcessedAt.get(),
                    RETRY_QUEUE_PROCESSING_PERIOD_SECONDS)
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

        fun pollQueue(): CommandData? {
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
                if (skip(commandData) && commandData != CommandData.EMPTY) {
                    cq.addToQueue(QueueType.SKIPPED, commandData)
                    commandData = null
                }
            } while (commandData == null || commandData == CommandData.EMPTY)
            MyLog.v(TAG, "Polled $accessorType in "
                    + (if (cq.myContext.isInForeground()) "foreground "
                    + if (MyPreferences.isSyncWhileUsingApplicationEnabled()) "enabled" else "disabled" else "background")
                    + if (commandData == null) " (no command)" else " $commandData")
            if (commandData != null) {
                cq.changed = true
                commandData.setManuallyLaunched(false)
            }
            return commandData
        }

        private fun skip(commandData: CommandData?): Boolean {
            if (commandData == null || commandData == CommandData.EMPTY) return false
            if (!commandData.isInForeground() && cq.myContext.isInForeground()
                    && !MyPreferences.isSyncWhileUsingApplicationEnabled()) {
                return true
            }
            return if (!commandData.command.getConnectionRequired()
                            .isConnectionStateOk(cq.myContext.getConnectionState())) {
                true
            } else false
        }

        fun moveCommandsFromPreToMainQueue() {
            for (cd in preQueue.queue) {
                if (addToMainOrSkipQueue(cd)) {
                    preQueue.queue.remove(cd)
                }
            }
        }

        fun moveCommandsFromSkippedToMainQueue() {
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

        /** @return true if success
         */
        private fun addToMainOrSkipQueue(commandData: CommandData): Boolean {
            if (!isForAccessor(commandData)) return false
            return if (skip(commandData)) {
                cq.addToQueue(QueueType.SKIPPED, commandData)
            } else {
                cq.addToQueue(mainQueueType(), commandData)
            }
        }

        private fun moveCommandsFromRetryToMainQueue() {
            val queue = cq.get(QueueType.RETRY).queue
            for (cd in queue) {
                if (cd.executedMoreSecondsAgoThan(MIN_RETRY_PERIOD_SECONDS) && addToMainOrSkipQueue(cd)) {
                    queue.remove(cd)
                    cq.changed = true
                    MyLog.v(TAG) { "Moved from Retry to Main queue: $cd" }
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
                        if (cdIn.isManuallyLaunched() || cd.executedMoreSecondsAgoThan(MIN_RETRY_PERIOD_SECONDS)) {
                            cdOut = cd
                            queue.remove(cd)
                            cq.changed = true
                            MyLog.v(TAG) { "Returned from Retry queue: $cd" }
                        } else {
                            cdOut = CommandData.EMPTY
                            MyLog.v(TAG) { "Found in Retry queue, but left there: $cd" }
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
                            MyLog.v(TAG) { "Returned from Error queue: $cd" }
                            cd.resetRetries()
                        } else {
                            cdOut = CommandData.EMPTY
                            MyLog.v(TAG) { "Found in Error queue, but left there: $cd" }
                        }
                    } else {
                        if (cd.executedMoreSecondsAgoThan(TimeUnit.DAYS.toSeconds(MAX_DAYS_IN_ERROR_QUEUE))) {
                            queue.remove(cd)
                            cq.changed = true
                            MyLog.i(TAG, "Removed old from Error queue: $cd")
                        }
                    }
                }
            }
            return cdOut
        }
    }

    fun getFromAnyQueue(dataIn: CommandData): CommandData {
        return inWhichQueue(dataIn)
                .map { oneQueue: OneQueue -> getFromQueue(oneQueue.queueType, dataIn) }
                .orElse(CommandData.EMPTY)
    }

    fun getFromQueue(queueType: QueueType, dataIn: CommandData): CommandData {
        for (data in get(queueType).queue) {
            if (dataIn == data) return data
        }
        return CommandData.EMPTY
    }

    fun inWhichQueue(commandData: CommandData?): Optional<OneQueue> {
        for (oneQueue in queues.values) {
            val queue = oneQueue.queue
            if (queue.contains(commandData)) {
                return Optional.of(oneQueue)
            }
        }
        return Optional.empty()
    }

    override fun toString(): String {
        val builder = MyStringBuilder()
        var count: Long = 0
        for (oneQueue in queues.values) {
            val queue = oneQueue.queue
            if (!queue.isEmpty()) {
                count += queue.size.toLong()
                builder.withComma(oneQueue.queueType.toString(), queue.toString())
            }
        }
        builder.withComma("sizeOfAllQueues", count)
        return builder.toKeyValue("CommandQueue")
    }

    companion object {
        private val TAG: String = CommandQueue::class.java.simpleName
        private const val INITIAL_CAPACITY = 100
        private const val RETRY_QUEUE_PROCESSING_PERIOD_SECONDS: Long = 900
        private const val MIN_RETRY_PERIOD_SECONDS: Long = 900
        private const val MAX_DAYS_IN_ERROR_QUEUE: Long = 10
        private val preQueue: OneQueue = OneQueue(QueueType.PRE)
        fun addToPreQueue(commandData: CommandData) {
            preQueue.addToQueue(commandData)
        }
    }

    init {
        for (queueType in QueueType.values()) {
            if (queueType.createQueue) queues[queueType] = OneQueue(queueType)
        }
        queues[QueueType.PRE] = preQueue
        generalAccessor = Accessor(this, AccessorType.GENERAL)
        accessors[AccessorType.GENERAL] = generalAccessor
        accessors[AccessorType.DOWNLOADS] = Accessor(this, AccessorType.DOWNLOADS)
    }
}
