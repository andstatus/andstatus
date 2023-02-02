/*
 * Copyright (C) 2014-2017 yvolk (Yuri Volkov), http://yurivolkov.com
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
import android.os.Parcel
import android.os.Parcelable
import org.andstatus.app.context.MyContextHolder.Companion.myContextHolder
import org.andstatus.app.data.DbUtils
import org.andstatus.app.database.table.CommandTable
import org.andstatus.app.notification.NotificationEventType
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.RelativeTime
import org.andstatus.app.util.RelativeTime.millisToDelaySeconds
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

/**
 * Result of the command execution
 * See also [android.content.SyncStats]
 * @author yvolk@yurivolkov.com
 */
class CommandResult() : Parcelable {
    var delayedTill: Long? = null
        set(value) {
            field = value?.let { if (it > 0) it else null }
        }
    var lastExecutedDate: Long = 0
    var executionCount = 0
        private set
    var retriesLeft = 0
    var executed = false
    var numAuthExceptions: Long = 0
        private set
    var numIoExceptions: Long = 0
        private set
    private var numParseExceptions: Long = 0
    var message: String = ""
    var progress: String = ""
    var itemId: Long = 0

    // 0 means these values were not set
    var hourlyLimit = 0
    var remainingHits = 0

    // Counters for user notifications
    var downloadedCount: Long = 0
    var newCount: Long = 0
    val notificationEventCounts: MutableMap<NotificationEventType, AtomicLong> = HashMap()

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeLong(delayedTill ?: 0)
        dest.writeLong(lastExecutedDate)
        dest.writeInt(executionCount)
        dest.writeInt(retriesLeft)
        dest.writeLong(numAuthExceptions)
        dest.writeLong(numIoExceptions)
        dest.writeLong(numParseExceptions)
        dest.writeString(message)
        dest.writeLong(itemId)
        dest.writeInt(hourlyLimit)
        dest.writeInt(remainingHits)
        dest.writeLong(downloadedCount)
        dest.writeString(progress)
    }

    constructor(parcel: Parcel) : this() {
        delayedTill = parcel.readLong()
        lastExecutedDate = parcel.readLong()
        executionCount = parcel.readInt()
        retriesLeft = parcel.readInt()
        numAuthExceptions = parcel.readLong()
        numIoExceptions = parcel.readLong()
        numParseExceptions = parcel.readLong()
        message = parcel.readString() ?: ""
        itemId = parcel.readLong()
        hourlyLimit = parcel.readInt()
        remainingHits = parcel.readInt()
        downloadedCount = parcel.readLong()
        progress = parcel.readString() ?: ""
    }

    fun toContentValues(values: ContentValues) {
        delayedTill // TODO
        values.put(CommandTable.LAST_EXECUTED_DATE, lastExecutedDate)
        values.put(CommandTable.EXECUTION_COUNT, executionCount)
        values.put(CommandTable.RETRIES_LEFT, retriesLeft)
        values.put(CommandTable.NUM_AUTH_EXCEPTIONS, numAuthExceptions)
        values.put(CommandTable.NUM_IO_EXCEPTIONS, numIoExceptions)
        values.put(CommandTable.NUM_PARSE_EXCEPTIONS, numParseExceptions)
        values.put(CommandTable.ERROR_MESSAGE, message)
        values.put(CommandTable.DOWNLOADED_COUNT, downloadedCount)
        values.put(CommandTable.PROGRESS_TEXT, progress)
    }

    val hasError: Boolean get() = hasSoftError || hasHardError

    val hasHardError: Boolean get() = numAuthExceptions > 0 || numParseExceptions > 0

    val hasSoftError: Boolean get() = numIoExceptions > 0

    override fun describeContents(): Int {
        return 0
    }

    fun setSoftErrorIfNotOk(ok: Boolean) {
        if (!ok) {
            incrementNumIoExceptions()
        }
    }

    override fun toString(): String {
        return MyStringBuilder.formatKeyValue(this, toSummaryBuilder())
    }

    fun toSummary(): String {
        return toSummaryBuilder().toString()
    }

    private fun toSummaryBuilder(): MyStringBuilder {
        val sb = MyStringBuilder()
        delayedTill.millisToDelaySeconds()?.let { sb.withComma("Delayed for $it sec") }
        if (executionCount > 0) {
            sb.withComma("executed", executionCount)
            sb.withComma(
                "last", RelativeTime.getDifference(myContextHolder.getNow().context, lastExecutedDate)
            )
            if (retriesLeft > 0) sb.withComma("retriesLeft", retriesLeft)
            if (!hasError) sb.withComma("error", "none")
        }
        if (hasError) sb.withComma("error", if (hasHardError) "Hard" else "Soft")
        if (downloadedCount > 0) sb.withComma("downloaded", downloadedCount)
        if (newCount > 0) sb.withComma("new", newCount)
        notificationEventCounts.forEach { event: NotificationEventType, count: AtomicLong ->
            if (count.get() > 0) sb.withComma(event.name, count.get())
        }
        if (message.isNotEmpty()) sb.append("\n$message")
        return sb
    }

    fun incrementNumAuthExceptions() {
        numAuthExceptions++
    }

    fun incrementNumIoExceptions() {
        numIoExceptions++
    }

    fun incrementParseExceptions() {
        numParseExceptions++
    }

    fun incrementNewCount() {
        newCount++
    }

    fun onNotificationEvent(event: NotificationEventType) {
        if (event == NotificationEventType.EMPTY) return
        val count = notificationEventCounts.get(event)
        count?.incrementAndGet()
        if (count == null) notificationEventCounts[event] = AtomicLong(1)
    }

    fun incrementDownloadedCount() {
        downloadedCount++
    }

    fun resetRetries(command: CommandEnum) {
        retriesLeft = command.numberOfRetries
        prepareForLaunch()
    }

    fun prepareForLaunch() {
        delayedTill = null
        executed = false
        numAuthExceptions = 0
        numIoExceptions = 0
        numParseExceptions = 0
        message = ""
        itemId = 0
        hourlyLimit = 0
        remainingHits = 0
        newCount = 0
        notificationEventCounts.values.forEach(Consumer { c: AtomicLong -> c.set(0) })
        downloadedCount = 0
        progress = ""
    }

    fun afterExecutionEnded() {
        if (delayedTill == null) {
            executed = true
            executionCount++
            if (retriesLeft > 0) {
                retriesLeft -= 1
            }
            lastExecutedDate = System.currentTimeMillis()
        }
    }

    val shouldWeRetry: Boolean get() = (!executed || hasError) && !hasHardError && retriesLeft > 0

    companion object {
        const val INITIAL_NUMBER_OF_RETRIES = 10

        @JvmField
        val CREATOR: Parcelable.Creator<CommandResult> = object : Parcelable.Creator<CommandResult> {
            override fun createFromParcel(inp: Parcel): CommandResult {
                return CommandResult(inp)
            }

            override fun newArray(size: Int): Array<CommandResult?> {
                return arrayOfNulls<CommandResult?>(size)
            }
        }

        fun fromCursor(cursor: Cursor): CommandResult {
            val result = CommandResult()
            result.delayedTill // TODO
            result.lastExecutedDate = DbUtils.getLong(cursor, CommandTable.LAST_EXECUTED_DATE)
            result.executionCount = DbUtils.getInt(cursor, CommandTable.EXECUTION_COUNT)
            result.retriesLeft = DbUtils.getInt(cursor, CommandTable.RETRIES_LEFT)
            result.numAuthExceptions = DbUtils.getLong(cursor, CommandTable.NUM_AUTH_EXCEPTIONS)
            result.numIoExceptions = DbUtils.getLong(cursor, CommandTable.NUM_IO_EXCEPTIONS)
            result.numParseExceptions = DbUtils.getLong(cursor, CommandTable.NUM_PARSE_EXCEPTIONS)
            result.message = DbUtils.getString(cursor, CommandTable.ERROR_MESSAGE)
            result.downloadedCount = DbUtils.getInt(cursor, CommandTable.DOWNLOADED_COUNT).toLong()
            result.progress = DbUtils.getString(cursor, CommandTable.PROGRESS_TEXT)
            return result
        }

        fun toString(commandResult: CommandResult?): String {
            return commandResult?.toString() ?: "(result is null)"
        }
    }
}
