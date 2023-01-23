/*
 * Copyright (c) 2016-2021 yvolk (Yuri Volkov), http://yurivolkov.com
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.andstatus.app.context.MyContext
import org.andstatus.app.data.DbUtils
import org.andstatus.app.data.DbUtils.closeSilently
import org.andstatus.app.database.table.CommandTable
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.StopWatch
import org.andstatus.app.util.TryUtils
import java.util.*
import java.util.concurrent.PriorityBlockingQueue
import java.util.function.Consumer

/**
 * @author yvolk@yurivolkov.com
 */
class CommandQueue(val myContext: MyContext) {
    private val queues: MutableMap<QueueType, OneQueue> = EnumMap(QueueType::class.java)
    private val generalAccessor: QueueAccessor
    private val accessors: MutableMap<AccessorType, QueueAccessor> = EnumMap(AccessorType::class.java)

    @Volatile
    internal var loaded = false

    @Volatile
    internal var changed = false

    class OneQueue(
        val cq: CommandQueue?,
        val queueType: QueueType,
        private val queue: Queue<CommandData> = PriorityBlockingQueue(INITIAL_CAPACITY)
    ) : Queue<CommandData> by queue {

        override fun clear() {
            queue.clear()
            cq?.changed = true
        }

        inline fun forEachEx(action: OneQueue.(CommandData) -> Unit) {
            for (element in this) action(element)
        }

        override fun remove(commandData: CommandData): Boolean =
            queue.remove(commandData).also {
                if (it) cq?.changed = true
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
                if (remove(commandData)) {
                    MyLog.v(TAG) { "Removed equal command from $queueType queue" }
                }
            } else {
                if (contains(commandData)) {
                    MyLog.v(TAG) { "Didn't add to $queueType queue. Already found $commandData" }
                    return true
                }
            }
            if (queueType == QueueType.CURRENT || queueType == QueueType.DOWNLOADS) {
                commandData.result.prepareForLaunch()
            }
            MyLog.v(TAG) { "Adding to $queueType queue $commandData" }
            if (offer(commandData)) {
                cq?.changed = true
                return true
            }
            MyLog.e(TAG, "Couldn't add to the $queueType queue, size=$size")
            return false
        }
    }

    fun getAccessor(accessorType: AccessorType): QueueAccessor {
        val accessor = accessors.get(accessorType)
        return accessor ?: generalAccessor
    }

    operator fun get(queueType: QueueType): OneQueue {
        return queues.get(queueType) ?: throw IllegalArgumentException("Unknown queueType $queueType")
    }

    fun pollOrEmpty(queueType: QueueType): CommandData = get(queueType).poll() ?: CommandData.EMPTY

    suspend fun load(): CommandQueue {
        if (loaded) {
            MyLog.v(TAG, "Already loaded")
        } else {
            mutex.withLock {
                val stopWatch: StopWatch = StopWatch.createStarted()
                val count = (load(QueueType.CURRENT) + load(QueueType.DOWNLOADS)
                    + load(QueueType.SKIPPED) + load(QueueType.RETRY))
                val countError = load(QueueType.ERROR)
                MyLog.i(
                    TAG, "commandQueueInitializedMs:" + stopWatch.time + ";"
                        + (if (count > 0) Integer.toString(count) else " no") + " msg in queues"
                        + if (countError > 0) ", plus $countError in Error queue" else ""
                )
                loaded = true
            }
        }
        return this
    }

    /** @return Number of items loaded
     */
    private fun load(queueType: QueueType): Int {
        val method = "loadQueue-" + queueType.save()
        val queue = get(queueType)
        var count = 0
        val db = myContext.database
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
                        if (MyLog.isVerboseEnabled() && (count < 6 || cd.command == CommandEnum.UPDATE_NOTE ||
                                cd.command == CommandEnum.UPDATE_MEDIA)
                        ) {
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

    fun save() {
        if (!changed && preQueue.isEmpty()) {
            MyLog.v(TAG) { "save; Nothing to save. changed:" + changed + "; preQueueIsEmpty:" + preQueue.isEmpty() }
            return
        }
        val db = myContext.database
        if (db == null) {
            MyLog.d(TAG, "save; Database is unavailable")
            return
        }
        if (!myContext.isReady && !myContext.isExpired) {
            MyLog.d(TAG, "save; Cannot save: context is " + myContext.state)
            return
        }
        runBlocking {
            accessors.values.forEach { accessor -> accessor.moveCommandsFromPreToMainQueue() }
            mutex.withLock {
                if (loaded) clearQueuesInDatabase(db)
                val countNotError = save(db, QueueType.CURRENT)
                    .flatMap { acc: Int -> save(db, QueueType.DOWNLOADS).map { i2: Int -> acc + i2 } }
                    .flatMap { acc: Int -> save(db, QueueType.SKIPPED).map { i2: Int -> acc + i2 } }
                    .flatMap { acc: Int -> save(db, QueueType.RETRY).map { i2: Int -> acc + i2 } }
                val countError = save(db, QueueType.ERROR)
                MyLog.d(
                    TAG, (if (loaded) "Queues saved" else "Saved new queued commands only") + ", " +
                        if (countNotError.isFailure || countError.isFailure()) " Error saving commands!"
                        else (if (countNotError.get() > 0) Integer.toString(countNotError.get()) else "no") + " commands" +
                            if (countError.get() > 0) ", plus " + countError.get() + " in Error queue" else ""
                )
                changed = false
            }
        }
    }

    /** @return Number of items persisted
     */
    private fun save(db: SQLiteDatabase, queueType: QueueType): Try<Int> {
        val method = "save-" + queueType.save()
        val queue = get(queueType)
        var count = 0
        try {
            if (!queue.isEmpty()) {
                val commands: MutableList<CommandData> = ArrayList()
                while (!queue.isEmpty() && count < 300) {
                    val cd = queue.poll()
                    if (cd != null) {
                        val values = ContentValues()
                        cd.toContentValues(values)
                        values.put(CommandTable.QUEUE_TYPE, queueType.save())
                        db.insert(CommandTable.TABLE_NAME, null, values)
                        count++
                        commands.add(cd)
                        if (MyLog.isVerboseEnabled() && (count < 6 || cd.command == CommandEnum.UPDATE_NOTE ||
                                cd.command == CommandEnum.UPDATE_MEDIA)
                        ) {
                            MyLog.v(TAG, "$method; $count: $cd")
                        }
                        if (myContext.isTestRun && queue.contains(cd)) {
                            MyLog.e(TAG, "$method; Duplicate command in a queue:$count $cd")
                        }
                    }
                }
                val left = queue.size
                // And add all commands back to the queue, so we won't need to reload them from a database
                commands.forEach(Consumer { e: CommandData -> queue.offer(e) })
                MyLog.d(
                    TAG, method + "; " + count + " saved" +
                        if (left == 0) " all" else ", $left left"
                )
            }
        } catch (e: Exception) {
            val msgLog = method + "; " + count + " saved, " + queue.size + " left."
            MyLog.e(TAG, msgLog, e)
            return TryUtils.failure(msgLog, e)
        }
        return Try.success(count)
    }

    private fun clearQueuesInDatabase(db: SQLiteDatabase): Try<Unit> {
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

    fun clear(): Try<Unit> {
        loaded = true
        for ((_, value) in queues) {
            value.clear()
        }
        save()
        MyLog.v(TAG, "Queues cleared")
        return TryUtils.SUCCESS
    }

    fun deleteCommand(commandData: CommandData): Try<Unit> {
        for (queue in queues.values) {
            commandData.deleteFromQueue(queue)
        }
        if (commandData.result.downloadedCount == 0L) {
            commandData.result.incrementParseExceptions()
            commandData.result.message = "Didn't delete command #" + commandData.itemId
        }
        commandData.result.afterExecutionEnded()
        return TryUtils.SUCCESS
    }

    /** @return true if success */
    fun addToQueue(queueTypeIn: QueueType, commandData: CommandData): Boolean =
        get(queueTypeIn).addToQueue(commandData)

    suspend fun isAnythingToExecuteNow(): Boolean = accessors.values.let {
        it.forEach { accessor -> accessor.moveCommandsFromPreToMainQueue() }
        it.any { accessor -> accessor.isAnythingToExecuteNow() }
    }

    enum class AccessorType(val title: String, val numberOfExecutors: Int) {
        GENERAL("General", 2),
        DOWNLOADS("Downloads", 4)
    }

    fun getFromAnyQueue(dataIn: CommandData): CommandData {
        return findQueue(dataIn)
            .map { queue: OneQueue -> getFromQueue(queue.queueType, dataIn) }
            .orElse(CommandData.EMPTY)
    }

    fun getFromQueue(queueType: QueueType, dataIn: CommandData): CommandData {
        for (data in get(queueType)) {
            if (dataIn == data) return data
        }
        return CommandData.EMPTY
    }

    fun findQueue(commandData: CommandData?): Optional<OneQueue> {
        for (queue in queues.values) {
            if (queue.contains(commandData)) {
                return Optional.of(queue)
            }
        }
        return Optional.empty()
    }

    override fun toString(): String {
        val builder = MyStringBuilder()
        var count: Long = 0
        for (queue in queues.values) {
            if (!queue.isEmpty()) {
                count += queue.size.toLong()
                builder.withComma(queue.queueType.toString(), queue.toString())
            }
        }
        builder.withComma("sizeOfAllQueues", count)
        return builder.toKeyValue("CommandQueue")
    }

    companion object {
        internal val TAG: String = CommandQueue::class.simpleName!!
        private const val INITIAL_CAPACITY = 100
        internal val mutex = Mutex()
        val preQueue: OneQueue = OneQueue(null, QueueType.PRE)

        fun addToPreQueue(commandData: CommandData) {
            if (commandData.command != CommandEnum.UNKNOWN) {
                preQueue.addToQueue(commandData)
            }
        }
    }

    init {
        for (queueType in QueueType.values()) {
            if (queueType.createQueue) queues[queueType] = OneQueue(this, queueType)
        }
        queues[QueueType.PRE] = preQueue
        generalAccessor = QueueAccessor(this, AccessorType.GENERAL)
        accessors[AccessorType.GENERAL] = generalAccessor
        accessors[AccessorType.DOWNLOADS] = QueueAccessor(this, AccessorType.DOWNLOADS)
    }
}
