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

import org.andstatus.app.context.MyContextHolder.Companion.myContextHolder
import org.andstatus.app.timeline.ViewItem

/**
 * @author yvolk@yurivolkov.com
 */
class QueueData constructor(val queueType: QueueType, val commandData: CommandData) :
    ViewItem<QueueData>(false, commandData.createdDate), Comparable<QueueData> {

    override fun getId(): Long {
        return commandData.hashCode().toLong()
    }

    override fun getDate(): Long {
        return commandData.createdDate
    }

    fun toSharedSubject(): String {
        return (queueType.acronym + "; "
            + commandData.toCommandSummary(myContextHolder.getNow()))
    }

    fun toSharedText(): String {
        return queueType.acronym + "; " + commandData.share(myContextHolder.getNow())
    }

    override operator fun compareTo(other: QueueData): Int {
        return -getDate().compareTo(other.getDate())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val queueData = other as QueueData
        return if (queueType != queueData.queueType) false
        else commandData.createdDate == queueData.commandData.createdDate
    }

    override fun hashCode(): Int {
        var result = queueType.hashCode()
        result = 31 * result + (commandData.createdDate xor (commandData.createdDate ushr 32)).toInt()
        return result
    }

    companion object {
        val EMPTY: QueueData = QueueData(QueueType.UNKNOWN, CommandData.EMPTY)
        fun getNew(queueType: QueueType, commandData: CommandData): QueueData {
            return QueueData(queueType, commandData)
        }

    }
}
