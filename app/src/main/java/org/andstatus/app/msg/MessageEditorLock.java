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

package org.andstatus.app.msg;

import android.os.Build;

import org.andstatus.app.util.MyLog;

import java.util.Date;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

class MessageEditorLock {
    static final MessageEditorLock EMPTY = new MessageEditorLock(false, 0);
    static final AtomicReference<MessageEditorLock> lock = new AtomicReference<>(MessageEditorLock.EMPTY);

    final boolean isSave;
    final long msgId;
    long startedAt;

    MessageEditorLock(boolean isSave, long msgId) {
        this.isSave = isSave;
        this.msgId = msgId;
    }

    boolean isEmpty() {
        return this.equals(EMPTY);
    }

    boolean acquire(boolean doWait) {
        boolean acquired = true;
        for (int i = 0; i < 200; i++) {
            MessageEditorLock lockPrevious = lock.get();
            if (lockPrevious.expired()) {
                this.startedAt = MyLog.uniqueCurrentTimeMS();
                if (lock.compareAndSet(lockPrevious, this)) {
                    MyLog.v(this, "Received lock " + this + (lockPrevious.isEmpty() ? "" :
                            (". Replaced expired " + lockPrevious)));
                    break;
                }
            } else if (isSave && !lockPrevious.isSave) {
                this.startedAt = MyLog.uniqueCurrentTimeMS();
                if (lock.compareAndSet(lockPrevious, this)) {
                    MyLog.v(this, "Received lock " + this + ". Replaced load " + lockPrevious);
                    break;
                }
            } else {
                if (lockPrevious.isSave == isSave && lockPrevious.msgId == msgId) {
                    MyLog.v(this, "The same operation in progress: " + lockPrevious);
                    acquired = false;
                    break;
                }
            }
            if (!doWait) {
                acquired = false;
                break;
            }
            try {
                // http://stackoverflow.com/questions/363681/generating-random-integers-in-a-range-with-java
                if (Build.VERSION.SDK_INT >= 21) {
                    Thread.sleep(250 + ThreadLocalRandom.current().nextInt(0, 500));
                } else {
                    Thread.sleep(500);
                }
            } catch (InterruptedException e) {
                MyLog.v(this, "Wait interrupted", e);
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
        if (!isEmpty()) {
            if (lock.compareAndSet(this, EMPTY)) {
                MyLog.v(this, "Released lock " + this);
                return true;
            } else {
                MyLog.v(this, "Didn't release lock " + this + ". Was " + lock.get());
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
        if (msgId != 0) {
            builder.append("msgId:" + msgId + ",");
        }
        builder.append("started:" + new Date(startedAt));
        return MyLog.formatKeyValue(this, builder.toString());

    }

    boolean expired() {
        return isEmpty() || Math.abs(System.currentTimeMillis() - startedAt) > 60000;
    }
}
