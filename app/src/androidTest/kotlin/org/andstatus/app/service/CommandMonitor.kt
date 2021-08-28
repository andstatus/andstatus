/*
 * Copyright (C) 2021 yvolk (Yuri Volkov), http://yurivolkov.com
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

import java.util.concurrent.atomic.AtomicReference

class CommandMonitor {
    private val emptyCounter = CommandCounter(CommandData.EMPTY, 0, 0)

    private val counter = AtomicReference(emptyCounter)
    val command: CommandData get() = counter.get().command
    val startCount: Long get() = counter.get().startCount
    val endCount get() = counter.get().endCount

    fun setCommand(command: CommandData): CommandCounter =
        (if (command == emptyCounter.command) emptyCounter else counter.get().copy(command = command))
            .also { counter.set(it) }

    fun onStart(command: CommandData) {
        do {
            val oldCounter = counter.get()
            if (oldCounter == emptyCounter || command != oldCounter.command) break
            val newCounter = oldCounter.copy(startCount = oldCounter.startCount + 1)
        } while (!counter.compareAndSet(oldCounter, newCounter))
    }

    fun onEnd(command: CommandData) {
        do {
            val oldCounter = counter.get()
            if (oldCounter == emptyCounter || command != oldCounter.command) break
            val newCounter = oldCounter.copy(endCount = oldCounter.endCount + 1)
        } while (!counter.compareAndSet(oldCounter, newCounter))
    }
}

