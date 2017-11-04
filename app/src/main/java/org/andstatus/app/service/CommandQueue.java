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
import android.database.sqlite.SQLiteDiskIOException;
import android.support.annotation.NonNull;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.database.table.CommandTable;
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
    private static class OneQueue {
        Queue<CommandData> queue = new PriorityBlockingQueue<>(100);
        volatile int savedCount = 0;
        volatile boolean savedForegroundTasks = false;

        public void clear() {
            queue.clear();
            savedCount = 0;
            savedForegroundTasks = false;
        }

        public boolean isEmpty() {
            return queue.isEmpty() && savedCount == 0;
        }

        private boolean hasForegroundTasks() {
            if (savedForegroundTasks) {
                return true;
            }
            boolean has = false;
            for (CommandData commandData : queue) {
                if (commandData.isInForeground()) {
                    has = true;
                    break;
                }
            }
            return has;
        }

        public int size() {
            return queue.size() + savedCount;
        }
    }
    private final Map<QueueType, OneQueue> queues = new HashMap<>();
    private volatile boolean loaded = false;
    private volatile boolean saved = false;

    public CommandQueue() {
        this(MyContextHolder.get().context());
    }

    public CommandQueue(Context context) {
        this.context = context;
        for (QueueType queueType : QueueType.values()) {
            queues.put(queueType, new OneQueue());
        }
    }

    public Queue<CommandData> get(QueueType queueType) {
        return queues.get(queueType).queue;
    }

    public synchronized CommandQueue load() {
        if (loaded) {
            MyLog.d(this, "Already loaded");
        } else {
            int count = load(QueueType.CURRENT) + load(QueueType.RETRY);
            int countError = load(QueueType.ERROR);
            MyLog.d(this, "State restored, " + (count > 0 ? Integer.toString(count) : "no ")
                    + " msg in the Queues, "
                    + (countError > 0 ? Integer.toString(countError) + " in Error queue" : "")
            );
            loaded = true;
        }
        return this;
    }

    /** @return Number of items loaded */
    protected int load(@NonNull QueueType queueType) {
        final String method = "loadQueue-" + queueType.save();
        OneQueue oneQueue = queues.get(queueType);
        Queue<CommandData> queue = oneQueue.queue;
        int count = 0;
        SQLiteDatabase db = MyContextHolder.get().getDatabase();
        if (db == null) {
            MyLog.d(context, method + "; Database is unavailable");
            return 0;
        }
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
                        count++;
                        if (MyLog.isVerboseEnabled() && (count < 6 || cd.getCommand() == CommandEnum.UPDATE_STATUS )) {
                            MyLog.v(context, method + "; " + count + ": " + cd.toString());
                        }
                    } else {
                        MyLog.e(context, method + "; " + cd);
                    }
                }
            }
        } finally {
            DbUtils.closeSilently(c);
        }
        MyLog.d(context, method + "; loaded " + count + " commands from '" + queueType + "'");
        oneQueue.savedCount = 0;
        oneQueue.savedForegroundTasks = false;
        return count;
    }

    public synchronized void save() {
        if (loaded) {
            clearQueuesInDatabase();
        }
        int count = save(QueueType.CURRENT) + save(QueueType.RETRY);
        int countError = save(QueueType.ERROR);
        MyLog.d(this, (loaded ? "Queues saved" : "Saved new queued commands only") + ", "
                + (count > 0 ? Integer.toString(count) : "no") + " commands"
                + (countError > 0 ? ", including " + Integer.toString(countError) + " in Error queue" : "")
        );
        saved |= loaded;
        loaded = false;
    }

    /** @return Number of items persisted */
    public int save(@NonNull QueueType queueType) {
        final String method = "saveQueue-" + queueType.save();
        OneQueue oneQueue = queues.get(queueType);
        Queue<CommandData> queue = oneQueue.queue;
        int count = 0;
        try {
            SQLiteDatabase db = MyContextHolder.get().getDatabase();
            if (db == null) {
                MyLog.d(context, method + "; Database is unavailable");
                return 0;
            }
            if (loaded) {
                oneQueue.savedCount = 0;
                oneQueue.savedForegroundTasks = false;
                String sql = "DELETE FROM " + CommandTable.TABLE_NAME + " WHERE " + CommandTable.QUEUE_TYPE + "='" + queueType.save() + "'";
                DbUtils.execSQL(db, sql);
            }

            if (!queue.isEmpty()) {
                while (!queue.isEmpty() && count < 300) {
                    CommandData cd = queue.poll();
                    oneQueue.savedForegroundTasks |= cd.isInForeground();
                    ContentValues values = new ContentValues();
                    cd.toContentValues(values);
                    values.put(CommandTable.QUEUE_TYPE, queueType.save());
                    db.insert(CommandTable.TABLE_NAME, null, values);
                    count++;
                    if (MyLog.isVerboseEnabled() && (count < 6 || cd.getCommand() == CommandEnum.UPDATE_STATUS )) {
                        MyLog.v(context, method + "; " + count + ": " + cd.toString());
                    }
                    if (MyContextHolder.get().isTestRun() && queue.contains(cd)) {
                        MyLog.e(context, method + "; Duplicated command in a queue:" + count + " " + cd.toString());
                    }
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
            if (SQLiteDiskIOException.class.isAssignableFrom(e.getClass())) {
                throw e;
            } else {
                throw new IllegalStateException(msgLog, e);
            }
        }
        oneQueue.savedCount += count;
        return count;
    }

    public synchronized void clearQueuesInDatabase() {
        final String method = "clearQueuesInDatabase";
        try {
            SQLiteDatabase db = MyContextHolder.get().getDatabase();
            if (db == null) {
                MyLog.d(context, method + "; Database is unavailable");
            }
            String sql = "DELETE FROM " + CommandTable.TABLE_NAME;
            DbUtils.execSQL(db, sql);
        } catch (Exception e) {
            String msgLog = method + MyContextHolder.getSystemInfo(context, true);
            MyLog.e(context, msgLog, e);
            if (SQLiteDiskIOException.class.isAssignableFrom(e.getClass())) {
                throw e;
            } else {
                throw new IllegalStateException(msgLog, e);
            }
        }
    }

    public void clear() {
        loaded = true;
        // MyLog.v(this, MyLog.getStackTrace(new IllegalStateException("CommandQueue#clear called")));
        for ( Map.Entry<QueueType, OneQueue> entry : queues.entrySet()) {
            entry.getValue().clear();
        }
        clearQueuesInDatabase();
        MyLog.v(this, "Queues cleared");
    }

    public void deleteCommand(CommandData commandData) {
        if (!loaded) load();
        for (OneQueue oneQueue : queues.values()) {
            commandData.deleteCommandInTheQueue(oneQueue.queue);
        }
    }

    public boolean isAnythingToExecuteNowIn(@NonNull QueueType queueType) {
        if (!loaded && !saved) {
            return true;
        }
        if ( queues.get(queueType).isEmpty()) {
            return false;
        }
        if (!MyPreferences.isSyncWhileUsingApplicationEnabled()
                && MyContextHolder.get().isInForeground()) {
            return queues.get(queueType).hasForegroundTasks();
        }
        return true;
    }

    public int totalSizeToExecute() {
        int size = 0;
        for ( Map.Entry<QueueType, OneQueue> entry : queues.entrySet()) {
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
