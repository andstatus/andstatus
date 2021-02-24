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
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.data.DbUtils
import org.andstatus.app.database.table.CommandTable
import org.andstatus.app.notification.NotificationEventType
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.RelativeTime
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import java.util.function.BiConsumer
import java.util.function.Consumer

/**
 * Result of the command execution
 * See also [android.content.SyncStats]
 * @author yvolk@yurivolkov.com
 */
class CommandResult : Parcelable {
    private var lastExecutedDate: Long = 0
    private var executionCount = 0
    private var retriesLeft = 0
    private var executed = false
    private var numAuthExceptions: Long = 0
    private var numIoExceptions: Long = 0
    private var numParseExceptions: Long = 0
    private var mMessage: String? = ""
    private var progress: String? = ""
    private var itemId: Long = 0

    // 0 means these values were not set
    private var hourlyLimit = 0
    private var remainingHits = 0

    // Counters for user notifications
    private var downloadedCount: Long = 0
    private var newCount: Long = 0
    val notificationEventCounts: MutableMap<NotificationEventType?, AtomicLong?>? = HashMap()

    constructor() {}

    override fun writeToParcel(dest: Parcel?, flags: Int) {
        dest.writeLong(lastExecutedDate)
        dest.writeInt(executionCount)
        dest.writeInt(retriesLeft)
        dest.writeLong(numAuthExceptions)
        dest.writeLong(numIoExceptions)
        dest.writeLong(numParseExceptions)
        dest.writeString(mMessage)
        dest.writeLong(itemId)
        dest.writeInt(hourlyLimit)
        dest.writeInt(remainingHits)
        dest.writeLong(downloadedCount)
        dest.writeString(progress)
    }

    constructor(parcel: Parcel?) {
        lastExecutedDate = parcel.readLong()
        executionCount = parcel.readInt()
        retriesLeft = parcel.readInt()
        numAuthExceptions = parcel.readLong()
        numIoExceptions = parcel.readLong()
        numParseExceptions = parcel.readLong()
        mMessage = parcel.readString()
        itemId = parcel.readLong()
        hourlyLimit = parcel.readInt()
        remainingHits = parcel.readInt()
        downloadedCount = parcel.readLong()
        progress = parcel.readString()
    }

    fun toContentValues(values: ContentValues?) {
        values.put(CommandTable.LAST_EXECUTED_DATE, lastExecutedDate)
        values.put(CommandTable.EXECUTION_COUNT, executionCount)
        values.put(CommandTable.RETRIES_LEFT, retriesLeft)
        values.put(CommandTable.NUM_AUTH_EXCEPTIONS, numAuthExceptions)
        values.put(CommandTable.NUM_IO_EXCEPTIONS, numIoExceptions)
        values.put(CommandTable.NUM_PARSE_EXCEPTIONS, numParseExceptions)
        values.put(CommandTable.ERROR_MESSAGE, mMessage)
        values.put(CommandTable.DOWNLOADED_COUNT, downloadedCount)
        values.put(CommandTable.PROGRESS_TEXT, progress)
    }

    fun getExecutionCount(): Int {
        return executionCount
    }

    fun hasError(): Boolean {
        return hasSoftError() || hasHardError()
    }

    fun hasHardError(): Boolean {
        return numAuthExceptions > 0 || numParseExceptions > 0
    }

    fun hasSoftError(): Boolean {
        return numIoExceptions > 0
    }

    override fun describeContents(): Int {
        return 0
    }

    fun setSoftErrorIfNotOk(ok: Boolean) {
        if (!ok) {
            incrementNumIoExceptions()
        }
    }

    override fun toString(): String {
        return MyStringBuilder.Companion.formatKeyValue(this, toSummaryBuilder())
    }

    fun toSummary(): String? {
        return toSummaryBuilder().toString()
    }

    private fun toSummaryBuilder(): StringBuilder? {
        val message = StringBuilder()
        if (executionCount > 0) {
            message.append("executed:$executionCount, ")
            message.append("last:" + RelativeTime.getDifference( MyContextHolder.myContextHolder.getNow().context(), lastExecutedDate) + ", ")
            if (retriesLeft > 0) {
                message.append("retriesLeft:$retriesLeft, ")
            }
            if (!hasError()) {
                message.append("error:None, ")
            }
        }
        if (hasError()) {
            message.append("error:" + (if (hasHardError()) "Hard" else "Soft") + ", ")
        }
        if (downloadedCount > 0) {
            message.append("downloaded:$downloadedCount, ")
        }
        if (newCount > 0) {
            message.append("new:$newCount, ")
        }
        notificationEventCounts.forEach(BiConsumer { event: NotificationEventType?, count: AtomicLong? -> if (count.get() > 0) message.append(event.name + ":" + count.get() + ", ") })
        if (!mMessage.isNullOrEmpty()) {
            message.append(" \n$mMessage")
        }
        return message
    }

    fun getNumAuthExceptions(): Long {
        return numAuthExceptions
    }

    protected fun incrementNumAuthExceptions() {
        numAuthExceptions++
    }

    fun getNumIoExceptions(): Long {
        return numIoExceptions
    }

    fun incrementNumIoExceptions() {
        numIoExceptions++
    }

    fun getNumParseExceptions(): Long {
        return numParseExceptions
    }

    fun incrementParseExceptions() {
        numParseExceptions++
    }

    fun getHourlyLimit(): Int {
        return hourlyLimit
    }

    fun setHourlyLimit(hourlyLimit: Int) {
        this.hourlyLimit = hourlyLimit
    }

    fun getRemainingHits(): Int {
        return remainingHits
    }

    fun setRemainingHits(remainingHits: Int) {
        this.remainingHits = remainingHits
    }

    fun incrementNewCount() {
        newCount++
    }

    fun onNotificationEvent(event: NotificationEventType) {
        if (event == NotificationEventType.EMPTY) return
        val count = notificationEventCounts.get(event)
        count?.incrementAndGet() ?: (notificationEventCounts[event] = AtomicLong(1))
    }

    fun incrementDownloadedCount() {
        downloadedCount++
    }

    fun getDownloadedCount(): Long {
        return downloadedCount
    }

    fun getNewCount(): Long {
        return newCount
    }

    fun getRetriesLeft(): Int {
        return retriesLeft
    }

    fun resetRetries(command: CommandEnum?) {
        retriesLeft = INITIAL_NUMBER_OF_RETRIES
        when (command) {
            CommandEnum.GET_TIMELINE, CommandEnum.GET_OLDER_TIMELINE, CommandEnum.RATE_LIMIT_STATUS -> retriesLeft = 0
            else -> {
            }
        }
        prepareForLaunch()
    }

    fun prepareForLaunch() {
        executed = false
        numAuthExceptions = 0
        numIoExceptions = 0
        numParseExceptions = 0
        mMessage = ""
        itemId = 0
        hourlyLimit = 0
        remainingHits = 0
        newCount = 0
        notificationEventCounts.values.forEach(Consumer { c: AtomicLong? -> c.set(0) })
        downloadedCount = 0
        progress = ""
    }

    fun afterExecutionEnded() {
        executed = true
        executionCount++
        if (retriesLeft > 0) {
            retriesLeft -= 1
        }
        lastExecutedDate = System.currentTimeMillis()
    }

    fun shouldWeRetry(): Boolean {
        return (!executed || hasError()) && !hasHardError() && retriesLeft > 0
    }

    fun getItemId(): Long {
        return itemId
    }

    fun setItemId(itemId: Long) {
        this.itemId = itemId
    }

    fun getLastExecutedDate(): Long {
        return lastExecutedDate
    }

    fun getMessage(): String? {
        return mMessage
    }

    fun setMessage(message: String?) {
        mMessage = message
    }

    fun getProgress(): String? {
        return progress
    }

    fun setProgress(progress: String?) {
        this.progress = progress
    }

    companion object {
        const val INITIAL_NUMBER_OF_RETRIES = 10
        val CREATOR: Parcelable.Creator<CommandResult?>? = object : Parcelable.Creator<CommandResult?> {
            override fun createFromParcel(`in`: Parcel?): CommandResult? {
                return CommandResult(`in`)
            }

            override fun newArray(size: Int): Array<CommandResult?>? {
                return arrayOfNulls<CommandResult?>(size)
            }
        }

        fun fromCursor(cursor: Cursor?): CommandResult? {
            val result = CommandResult()
            result.lastExecutedDate = DbUtils.getLong(cursor, CommandTable.LAST_EXECUTED_DATE)
            result.executionCount = DbUtils.getInt(cursor, CommandTable.EXECUTION_COUNT)
            result.retriesLeft = DbUtils.getInt(cursor, CommandTable.RETRIES_LEFT)
            result.numAuthExceptions = DbUtils.getLong(cursor, CommandTable.NUM_AUTH_EXCEPTIONS)
            result.numIoExceptions = DbUtils.getLong(cursor, CommandTable.NUM_IO_EXCEPTIONS)
            result.numParseExceptions = DbUtils.getLong(cursor, CommandTable.NUM_PARSE_EXCEPTIONS)
            result.mMessage = DbUtils.getString(cursor, CommandTable.ERROR_MESSAGE)
            result.downloadedCount = DbUtils.getInt(cursor, CommandTable.DOWNLOADED_COUNT).toLong()
            result.progress = DbUtils.getString(cursor, CommandTable.PROGRESS_TEXT)
            return result
        }

        fun toString(commandResult: CommandResult?): String? {
            return commandResult?.toString() ?: "(result is null)"
        }
    }
}