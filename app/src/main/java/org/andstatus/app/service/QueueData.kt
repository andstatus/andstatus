/*
 * Copyright (c) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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

import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.timeline.ViewItem

/**
 * @author yvolk@yurivolkov.com
 */
class QueueData protected constructor(val queueType: QueueType, val commandData: CommandData) : ViewItem<QueueData?>(false, commandData.createdDate), Comparable<QueueData?> {
    override fun getId(): Long {
        return commandData.hashCode()
    }

    override fun getDate(): Long {
        return commandData.createdDate
    }

    fun toSharedSubject(): String? {
        return (queueType.acronym + "; "
                + commandData.toCommandSummary( MyContextHolder.myContextHolder.getNow()))
    }

    fun toSharedText(): String? {
        return (queueType.acronym + "; "
                + commandData.share( MyContextHolder.myContextHolder.getNow()))
    }

    override operator fun compareTo(another: QueueData): Int {
        return -longCompare(date, another.date)
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val queueData = o as QueueData?
        return if (queueType != queueData.queueType) false else commandData.createdDate == queueData.commandData.createdDate
    }

    override fun hashCode(): Int {
        var result = queueType.hashCode()
        result = 31 * result + (commandData.createdDate xor (commandData.createdDate ushr 32)) as Int
        return result
    }

    companion object {
        val EMPTY: QueueData = QueueData(QueueType.UNKNOWN, CommandData.Companion.EMPTY)
        fun getNew(queueType: QueueType, commandData: CommandData): QueueData? {
            return QueueData(queueType, commandData)
        }

        // TODO: Replace with Long.compare for API >= 19
        private fun longCompare(lhs: Long, rhs: Long): Int {
            return if (lhs < rhs) -1 else if (lhs == rhs) 0 else 1
        }
    }
}