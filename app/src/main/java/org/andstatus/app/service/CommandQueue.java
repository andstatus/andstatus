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

import android.content.Context;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.util.MyLog;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * @author yvolk@yurivolkov.com
 */
public class CommandQueue {
    private final Context context;
    private final Map<QueueType, Queue<CommandData>> queues = new HashMap<>();

    public CommandQueue() {
        this(MyContextHolder.get().context());
    }

    public CommandQueue(Context context) {
        this.context = context;
        for (QueueType queueType : QueueType.values()) {
            queues.put(queueType, new PriorityBlockingQueue<CommandData>(100));
        }
    }

    public Queue<CommandData> get(QueueType queueType) {
        return queues.get(queueType);
    }

    public CommandQueue load() {
        int count = load(QueueType.CURRENT) + load(QueueType.RETRY);
        int countError = load(QueueType.ERROR);
        MyLog.d(this, "State restored, " + (count > 0 ? Integer.toString(count) : "no ")
                + " msg in the Queues, "
                + (countError > 0 ? Integer.toString(countError) + " in Error queue" : "")
        );
        return this;
    }

    public int load(QueueType queueType) {
        return CommandData.loadQueue(context, get(queueType), queueType);
    }

    public void save() {
        int count = save(QueueType.CURRENT) + save(QueueType.RETRY);
        int countError = save(QueueType.ERROR);
        MyLog.d(this, "State saved, " + (count > 0 ? Integer.toString(count) : "no ")
                + " msg in the Queues, "
                + (countError > 0 ? Integer.toString(countError) + " in Error queue" : "")
        );
    }

    public int save(QueueType queueType) {
        return CommandData.saveQueue(context, get(queueType), queueType);
    }

    public void clear() {
        for (Queue<CommandData> queue : queues.values()) {
            queue.clear();
        }
        MyLog.v(this, "Queues cleared");
    }

    public void deleteCommand(CommandData commandData) {
        for (Queue<CommandData> queue : queues.values()) {
            commandData.deleteCommandInTheQueue(queue);
        }
    }

    public boolean hasForegroundTasks(QueueType queueType) {
        boolean has = false;
        for (CommandData commandData : get(queueType)) {
            if (commandData.isInForeground()) {
                has = true;
                break;
            }
        }
        return has;
    }

    public boolean isAnythingToExecuteNowIn(QueueType queueType) {
        if (queues.get(queueType).isEmpty()) {
            return false;
        }
        if (!MyPreferences.isSyncWhileUsingApplicationEnabled()
                && MyContextHolder.get().isInForeground()) {
            return hasForegroundTasks(queueType);
        }
        return true;
    }

    public int totalSizeToExecute() {
        int size = 0;
        for ( Map.Entry<QueueType,Queue<CommandData>> entry : queues.entrySet()) {
            if (entry.getKey().isExecutable()) {
                size += entry.getValue().size();
            }
        }
        return size;
    }

    public void addToQueue(QueueType queueType, CommandData commandData) {
        if (!get(queueType).contains(commandData)
                && !get(queueType).offer(commandData)) {
            MyLog.e(this, queueType.name() + " is full?");
        }
    }

}
