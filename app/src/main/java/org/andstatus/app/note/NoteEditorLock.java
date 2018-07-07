/**
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

package org.andstatus.app.note;

import org.andstatus.app.data.DbUtils;
import org.andstatus.app.util.IsEmpty;
import org.andstatus.app.util.MyLog;

import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

class NoteEditorLock implements IsEmpty {
    static final NoteEditorLock EMPTY = new NoteEditorLock(false, 0);
    static final AtomicReference<NoteEditorLock> lock = new AtomicReference<>(NoteEditorLock.EMPTY);

    final boolean isSave;
    final long noteId;
    long startedAt;

    NoteEditorLock(boolean isSave, long noteId) {
        this.isSave = isSave;
        this.noteId = noteId;
    }

    @Override
    public boolean isEmpty() {
        return this.equals(EMPTY);
    }

    boolean acquire(boolean doWait) {
        boolean acquired = true;
        for (int i = 0; i < 200; i++) {
            NoteEditorLock lockPrevious = lock.get();
            if (lockPrevious.expired()) {
                this.startedAt = MyLog.uniqueCurrentTimeMS();
                if (lock.compareAndSet(lockPrevious, this)) {
                    MyLog.v(this, () -> "Received lock " + this + (lockPrevious.isEmpty() ? "" :
                            (". Replaced expired " + lockPrevious)));
                    break;
                }
            } else if (isSave && !lockPrevious.isSave) {
                this.startedAt = MyLog.uniqueCurrentTimeMS();
                if (lock.compareAndSet(lockPrevious, this)) {
                    MyLog.v(this, () -> "Received lock " + this + ". Replaced load " + lockPrevious);
                    break;
                }
            } else {
                if (lockPrevious.isSave == isSave && lockPrevious.noteId == noteId) {
                    MyLog.v(this, () -> "The same operation in progress: " + lockPrevious);
                    acquired = false;
                    break;
                }
            }
            if (!doWait || DbUtils.waitBetweenRetries("acquire")) {
                acquired = false;
                break;
            }
        }
        return acquired;
    }

    public boolean acquired() {
        return !expired() && lock.get() == this;
    }

    public boolean release() {
        if (nonEmpty()) {
            if (lock.compareAndSet(this, EMPTY)) {
                MyLog.v(this, () -> "Released lock " + this);
                return true;
            } else {
                MyLog.v(this, () -> "Didn't release lock " + this + ". Was " + lock.get());
            }
        }
        return false;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        if (isSave) {
            builder.append("save,");
        }
        if (noteId != 0) {
            builder.append("msgId:" + noteId + ",");
        }
        builder.append("started:" + new Date(startedAt));
        return MyLog.formatKeyValue(this, builder.toString());

    }

    boolean expired() {
        return isEmpty() || Math.abs(System.currentTimeMillis() - startedAt) > 60000;
    }
}
