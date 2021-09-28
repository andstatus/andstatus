/*
 * Copyright (C) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.note

import org.andstatus.app.data.DbUtils
import org.andstatus.app.util.Identifiable
import org.andstatus.app.util.InstanceId
import org.andstatus.app.util.IsEmpty
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder
import java.util.*
import java.util.concurrent.atomic.AtomicReference

internal class NoteEditorLock(val isSave: Boolean, val noteId: Long) : IsEmpty, Identifiable {
    override val instanceId = InstanceId.next()
    var startedAt: Long = 0

    override val isEmpty: Boolean
        get() {
            return this == EMPTY
        }

    fun acquire(doWait: Boolean): Boolean {
        var acquired = true
        for (i in 0..199) {
            val lockPrevious = lock.get()
            if (lockPrevious.expired()) {
                startedAt = MyLog.uniqueCurrentTimeMS
                if (lock.compareAndSet(lockPrevious, this)) {
                    MyLog.v(this) { "Received lock " + this + if (lockPrevious.isEmpty) "" else ". Replaced expired $lockPrevious" }
                    break
                }
            } else if (isSave && !lockPrevious.isSave) {
                startedAt = MyLog.uniqueCurrentTimeMS
                if (lock.compareAndSet(lockPrevious, this)) {
                    MyLog.v(this) { "Received lock $this. Replaced load $lockPrevious" }
                    break
                }
            } else {
                if (lockPrevious.isSave == isSave && lockPrevious.noteId == noteId) {
                    MyLog.v(this) { "The same operation in progress: $lockPrevious" }
                    acquired = false
                    break
                }
            }
            if (!doWait || DbUtils.waitBetweenRetries("acquire")) {
                acquired = false
                break
            }
        }
        return acquired
    }

    fun acquired(): Boolean {
        return !expired() && lock.get() === this
    }

    fun release() {
        if (nonEmpty) {
            if (lock.compareAndSet(this, EMPTY)) {
                MyLog.v(this) { "Released lock $this" }
            } else {
                MyLog.v(this) { "Didn't release lock " + this + ". Was " + lock.get() }
            }
        }
    }

    override fun toString(): String {
        val builder = MyStringBuilder()
        if (isSave) {
            builder.withComma("save")
        }
        if (noteId != 0L) {
            builder.withComma("noteId", noteId)
        }
        builder.withComma("started", Date(startedAt))
        return MyStringBuilder.formatKeyValue(this, builder.toString())
    }

    fun expired(): Boolean {
        return isEmpty || Math.abs(System.currentTimeMillis() - startedAt) > 60000
    }

    override val classTag: String get() = TAG

    companion object {
        private val TAG: String = NoteEditorLock::class.simpleName!!
        val EMPTY: NoteEditorLock = NoteEditorLock(false, 0)
        private val lock: AtomicReference<NoteEditorLock> = AtomicReference(EMPTY)
        val isLockReleased: Boolean get() = lock.get().isEmpty
    }
}
