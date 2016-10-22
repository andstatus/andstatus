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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.database.CommandTable;
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

    /** @return Number of items loaded */
    public int load(QueueType queueType) {
        final String method = "loadQueue-" + queueType.save();
        Queue<CommandData> queue = get(queueType);
        int count = 0;
        SQLiteDatabase db = MyContextHolder.get().getDatabase();
        if (db == null) {
            MyLog.d(context, method + "; Database is unavailable");
            return 0;
        }
        queue.clear();
        String sql = "SELECT * FROM " + CommandTable.TABLE_NAME + " WHERE " + CommandTable.QUEUE_TYPE + "='" + queueType.save() + "'";
        Cursor c = null;
        try {
            c = db.rawQuery(sql, null);
            while (c.moveToNext()) {
                CommandData cd = CommandData.fromCursor(MyContextHolder.get(), c);
                if (CommandEnum.EMPTY.equals(cd.getCommand())) {
                    MyLog.e(context, method + "; empty skipped " + cd);
                } else if (queue.contains(cd)) {
                    MyLog.e(context, method + "; duplicate skipped " + cd);
                } else {
                    if (queue.offer(cd)) {
                        if (MyLog.isVerboseEnabled() && count < 5) {
                            MyLog.v(context, method + "; " + cd);
                        }
                        count++;
                    } else {
                        MyLog.e(context, method + "; " + cd);
                    }
                }
            }
        } finally {
            DbUtils.closeSilently(c);
        }
        MyLog.d(context, method + "; loaded " + count + " commands from '" + queueType + "'");
        return count;
    }

    public void save() {
        int count = save(QueueType.CURRENT) + save(QueueType.RETRY);
        int countError = save(QueueType.ERROR);
        MyLog.d(this, "State saved, " + (count > 0 ? Integer.toString(count) : "no ")
                + " msg in the Queues, "
                + (countError > 0 ? Integer.toString(countError) + " in Error queue" : "")
        );
    }

    /** @return Number of items persisted */
    public int save(QueueType queueType) {
        final String method = "saveQueue-" + queueType.save();
        Queue<CommandData> queue = get(queueType);
        int count = 0;
        try {
            SQLiteDatabase db = MyContextHolder.get().getDatabase();
            if (db == null) {
                MyLog.d(context, method + "; Database is unavailable");
                return 0;
            }
            String sql = "DELETE FROM " + CommandTable.TABLE_NAME + " WHERE " + CommandTable.QUEUE_TYPE + "='" + queueType.save() + "'";
            DbUtils.execSQL(db, sql);

            if (!queue.isEmpty()) {
                while (!queue.isEmpty() && count < 300) {
                    CommandData cd = queue.poll();
                    ContentValues values = new ContentValues();
                    cd.toContentValues(values);
                    values.put(CommandTable.QUEUE_TYPE, queueType.save());
                    db.insert(CommandTable.TABLE_NAME, null, values);
                    if (MyLog.isVerboseEnabled() && count < 5) {
                        MyLog.v(context, method + "; Command saved: " + cd.toString());
                    }
                    count += 1;
                }
                if (queue.isEmpty()) {
                    MyLog.d(context, method + "; " + count + " saved");
                } else {
                    MyLog.e(context, method + "; " + count + " saved" +
                            (queue.isEmpty() ? "" : ", " + queue.size() + " left"));
                }
            }
        } catch (Exception e) {
            String msgLog = method + "; " + count + " saved, " + queue.size() + " left.\n"
                    + MyContextHolder.getSystemInfo(context, true);
            MyLog.e(context, msgLog, e);
            throw new IllegalStateException(msgLog, e);
        }
        return count;
    }

    public void clear() {
        // MyLog.v(this, MyLog.getStackTrace(new IllegalStateException("CommandQueue#clear called")));
        for ( Map.Entry<QueueType,Queue<CommandData>> entry : queues.entrySet()) {
            entry.getValue().clear();
            save(entry.getKey());
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
