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

package org.andstatus.app.service;

import androidx.annotation.NonNull;

import org.andstatus.app.timeline.ViewItem;

import static org.andstatus.app.context.MyContextHolder.myContextHolder;

/**
 * @author yvolk@yurivolkov.com
 */
public class QueueData extends ViewItem<QueueData> implements Comparable<QueueData> {
    public final static QueueData EMPTY = new QueueData(QueueType.UNKNOWN, CommandData.EMPTY);

    @NonNull
    final QueueType queueType;
    @NonNull
    final CommandData commandData;

    static QueueData getNew(@NonNull QueueType queueType, @NonNull CommandData commandData) {
        return new QueueData(queueType, commandData);
    }

    protected QueueData(@NonNull QueueType queueType, @NonNull CommandData commandData) {
        super(false, commandData.getCreatedDate());
        this.queueType = queueType;
        this.commandData = commandData;
    }

    @Override
    public long getId() {
        return commandData.hashCode();
    }

    @Override
    public long getDate() {
        return commandData.getCreatedDate();
    }

    public String toSharedSubject() {
        return queueType.getAcronym() + "; "
                + commandData.toCommandSummary(myContextHolder.getNow());
    }

    public String toSharedText() {
        return queueType.getAcronym() + "; "
                + commandData.share(myContextHolder.getNow());
    }

    @Override
    public int compareTo(@NonNull QueueData another) {
        return -longCompare(getDate(), another.getDate());
    }

    // TODO: Replace with Long.compare for API >= 19
    private static int longCompare(long lhs, long rhs) {
        return lhs < rhs ? -1 : (lhs == rhs ? 0 : 1);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        QueueData queueData = (QueueData) o;
        if (queueType != queueData.queueType) return false;
        return commandData.getCreatedDate() == queueData.commandData.getCreatedDate();
    }

    @Override
    public int hashCode() {
        int result = queueType.hashCode();
        result = 31 * result + (int) (commandData.getCreatedDate() ^ (commandData.getCreatedDate() >>> 32));
        return result;
    }
}
