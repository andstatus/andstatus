/*
 * Copyright (c) 2016-2018 yvolk (Yuri Volkov), http://yurivolkov.com
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

import androidx.annotation.NonNull;

import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.database.table.CommandTable;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.RelativeTime;
import org.andstatus.app.util.TryUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import io.vavr.control.Try;

/**
 * @author yvolk@yurivolkov.com
 */
public class CommandQueue {
    private static final int INITIAL_CAPACITY = 100;
    private static final long RETRY_QUEUE_PROCESSING_PERIOD_SECONDS = 900;
    private static final long MIN_RETRY_PERIOD_SECONDS = 900;
    private static final long MAX_DAYS_IN_ERROR_QUEUE = 10;
    private final static Queue<CommandData> preQueue = new PriorityBlockingQueue<>(INITIAL_CAPACITY);

    private volatile MyContext myContext = MyContextHolder.get();
    private final Context context;
    private final AtomicLong mRetryQueueProcessedAt = new AtomicLong();
    private final Map<QueueType, OneQueue> queues = new HashMap<>();
    private volatile boolean loaded = false;
    private volatile boolean saved = false;

    static void addToPreQueue(CommandData commandData) {
        switch (commandData.getCommand()) {
            case EMPTY:
            case UNKNOWN:
                return;
            default:
                break;
        }
        if (preQueue.contains(commandData)) {
            MyLog.v(CommandQueue.class, () -> "Didn't add to preQueue. Already found " + commandData);
            return;
        }
        MyLog.v(CommandQueue.class, () -> "Adding to preQueue " + commandData);
        if (preQueue.offer(commandData)) return;

        // TODO: Remove less prioritized item to free space for this one?!

        MyLog.e(CommandQueue.class, "Couldn't add to the preQueue, size=" + preQueue.size());
    }

    private static class OneQueue {
        Queue<CommandData> queue = new PriorityBlockingQueue<>(INITIAL_CAPACITY);
        final AtomicInteger savedCount = new AtomicInteger();
        volatile boolean savedForegroundTasks = false;

        public void clear() {
            queue.clear();
            savedCount.set(0);
            savedForegroundTasks = false;
        }

        public boolean isEmpty() {
            return queue.isEmpty() && savedCount.get() == 0;
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
            return queue.size() + savedCount.get();
        }
    }

    CommandQueue() {
        this(MyContextHolder.get().context());
    }

    CommandQueue(Context context) {
        this.context = context;
        for (QueueType queueType : QueueType.values()) {
            if (queueType.createQueue) queues.put(queueType, new OneQueue());
        }
    }

    public void setMyContext(MyContext myContext) {
        this.myContext = myContext;
    }

    public Queue<CommandData> get(QueueType queueType) {
        switch (queueType) {
            case PRE:
                return preQueue;
            case UNKNOWN:
                return null;
            default:
                return queues.get(queueType).queue;
        }
    }

    public synchronized CommandQueue load() {
        if (loaded) {
            MyLog.v(this, "Already loaded");
        } else {
            int count = load(QueueType.CURRENT) + load(QueueType.RETRY);
            int countError = load(QueueType.ERROR);
            MyLog.d(this, "State restored, " + (count > 0 ? Integer.toString(count) : "no ")
                    + " msg in the Queues"
                    + (countError > 0 ? ", plus " + Integer.toString(countError) + " in Error queue" : "")
            );
            loaded = true;
        }
        return this;
    }

    /** @return Number of items loaded */
    private int load(@NonNull QueueType queueType) {
        final String method = "loadQueue-" + queueType.save();
        OneQueue oneQueue = queues.get(queueType);
        Queue<CommandData> queue = oneQueue.queue;
        int count = 0;
        SQLiteDatabase db = myContext.getDatabase();
        if (db == null) {
            MyLog.d(context, method + "; Database is unavailable");
            return 0;
        }
        String sql = "SELECT * FROM " + CommandTable.TABLE_NAME + " WHERE " + CommandTable.QUEUE_TYPE + "='"
                + queueType.save() + "'";
        Cursor c = null;
        try {
            c = db.rawQuery(sql, null);
            while (c.moveToNext()) {
                CommandData cd = CommandData.fromCursor(myContext, c);
                if (CommandEnum.EMPTY.equals(cd.getCommand())) {
                    MyLog.e(context, method + "; empty skipped " + cd);
                } else if (queue.contains(cd)) {
                    MyLog.e(context, method + "; duplicate skipped " + cd);
                } else {
                    if (queue.offer(cd)) {
                        count++;
                        if (MyLog.isVerboseEnabled() && (count < 6 || cd.getCommand() == CommandEnum.UPDATE_NOTE)) {
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
        oneQueue.savedCount.set(0);
        oneQueue.savedForegroundTasks = false;
        return count;
    }

    synchronized void save() {
        SQLiteDatabase db = myContext.getDatabase();
        if (db == null) {
            MyLog.d(context, "save; Database is unavailable");
            return;
        }
        if (loaded) clearQueuesInDatabase(db);
        moveCommandsFromPreToMainQueue();
        Try<Integer> countCurrentRetry = save(db, QueueType.CURRENT)
                .flatMap(i1 -> save(db, QueueType.RETRY).map(i2 -> i1 + i2));
        Try<Integer> countError = save(db, QueueType.ERROR);
        MyLog.d(this, (loaded ? "Queues saved" : "Saved new queued commands only") + ", "
            + ( countCurrentRetry.isFailure() || countError.isFailure()
                ? " Error saving commands!"
                : ((countCurrentRetry.get() > 0 ? Integer.toString(countCurrentRetry.get()) : "no") + " commands"
                    + (countError.get() > 0 ? ", plus " + countError.get() + " in Error queue" : ""))
            )
        );
        saved |= loaded;
        loaded = false;
    }

    /** @return Number of items persisted */
    private Try<Integer> save(@NonNull SQLiteDatabase db, @NonNull QueueType queueType) {
        final String method = "saveQueue-" + queueType.save();
        OneQueue oneQueue = queues.get(queueType);
        Queue<CommandData> queue = oneQueue.queue;
        int count = 0;
        try {
            if (loaded) {
                oneQueue.savedCount.set(0);
                oneQueue.savedForegroundTasks = false;
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
                    if (MyLog.isVerboseEnabled() && (count < 6 || cd.getCommand() == CommandEnum.UPDATE_NOTE)) {
                        MyLog.v(context, method + "; " + count + ": " + cd.toString());
                    }
                    if (myContext.isTestRun() && queue.contains(cd)) {
                        MyLog.e(context, method + "; Duplicated command in a queue:" + count + " " + cd.toString());
                    }
                }
                MyLog.e(context, method + "; " + count + " saved" +
                        (queue.isEmpty() ? "" : ", " + queue.size() + " left"));
            }
        } catch (Exception e) {
            String msgLog = method + "; " + count + " saved, " + queue.size() + " left.";
            MyLog.e(context, msgLog, e);
            return TryUtils.failure(msgLog, e);
        }
        oneQueue.savedCount.addAndGet(count);
        return Try.success(count);
    }

    private synchronized Try<Boolean> clearQueuesInDatabase(@NonNull SQLiteDatabase db) {
        final String method = "clearQueuesInDatabase";
        try {
            String sql = "DELETE FROM " + CommandTable.TABLE_NAME;
            DbUtils.execSQL(db, sql);
        } catch (Exception e) {
            MyLog.e(context, method, e);
            return TryUtils.failure(method, e);
        }
        return TryUtils.TRUE;
    }

    void clear() {
        loaded = true;
        // MyLog.v(this, MyLog.getStackTrace(new IllegalStateException("CommandQueue#clear called")));
        for ( Map.Entry<QueueType, OneQueue> entry : queues.entrySet()) {
            entry.getValue().clear();
        }
        preQueue.clear();
        save();
        MyLog.v(this, "Queues cleared");
    }

    void deleteCommand(CommandData commandData) {
        moveCommandsFromPreToMainQueue();
        for (OneQueue oneQueue : queues.values()) {
            commandData.deleteCommandFromQueue(oneQueue.queue);
        }
        if (commandData.getResult().getDownloadedCount() == 0) {
            commandData.getResult().incrementParseExceptions();
            commandData.getResult().setMessage("Didn't delete command #" + commandData.itemId);
        }
        commandData.getResult().afterExecutionEnded();
    }

    int totalSizeToExecute() {
        int size = 0;
        for ( Map.Entry<QueueType, OneQueue> entry : queues.entrySet()) {
            if (entry.getKey().isExecutable()) {
                size += entry.getValue().size();
            }
        }
        return size + preQueue.size();
    }

    void addToQueue(QueueType queueType, CommandData commandData) {
        get(queueType).remove(commandData);
        if (!get(queueType).offer(commandData)) {
            MyLog.e(this, queueType.name() + " is full?");
        }
    }

    boolean isAnythingToExecuteNow() {
        return !loaded && !saved || !preQueue.isEmpty() || isAnythingToExecuteNowIn(QueueType.CURRENT)
                || isAnythingToRetryNow();
    }

    private boolean isAnythingToRetryNow() {
        return RelativeTime.moreSecondsAgoThan(mRetryQueueProcessedAt.get(),
                RETRY_QUEUE_PROCESSING_PERIOD_SECONDS) && isAnythingToExecuteNowIn(QueueType.RETRY);
    }

    private boolean isAnythingToExecuteNowIn(@NonNull QueueType queueType) {
        if (!loaded && !saved) {
            return true;
        }
        if ( queues.get(queueType).isEmpty()) {
            return false;
        }
        if (!MyPreferences.isSyncWhileUsingApplicationEnabled() && myContext.isInForeground()) {
            return queues.get(queueType).hasForegroundTasks();
        }
        return true;
    }

    CommandData pollQueue() {
        moveCommandsFromPreToMainQueue();
        CommandData commandData;
        do {
            commandData = get(QueueType.CURRENT).poll();
            if (commandData == null && isAnythingToRetryNow()) {
                moveCommandsFromRetryToMainQueue();
                commandData = get(QueueType.CURRENT).poll();
            }
            if (commandData == null) {
                break;
            }
            commandData = findInRetryQueue(commandData);
            if (commandData != null) {
                commandData = findInErrorQueue(commandData);
            }
            if (commandData != null && !commandData.isInForeground() && myContext.isInForeground()
                    && !MyPreferences.isSyncWhileUsingApplicationEnabled()) {
                addToPreQueue(commandData);
                commandData = null;
            }
        } while (commandData == null);
        MyLog.v(this, "Polled in "
                + (myContext.isInForeground() ? "foreground "
                    + (MyPreferences.isSyncWhileUsingApplicationEnabled() ? "enabled" : "disabled")
                  : "background")
                + (commandData == null ? "" : " " + commandData));
        if (commandData != null) {
            commandData.setManuallyLaunched(false);
        }
        return commandData;
    }

    private void moveCommandsFromPreToMainQueue() {
        for (CommandData cd : preQueue) {
            if (addToMainQueue(cd)) preQueue.remove(cd);
        }
    }

    /** @return true if success */
    private boolean addToMainQueue(CommandData commandData) {
        if (get(QueueType.CURRENT).contains(commandData)) {
            MyLog.v(this, () -> "Didn't add to Main queue. Already found " + commandData);
            return true;
        }
        commandData.getResult().prepareForLaunch();
        MyLog.v(this, () -> "Adding to Main queue " + commandData);
        if (get(QueueType.CURRENT).offer(commandData)) return true;

        MyLog.e(this, "Couldn't add to the main queue, size=" + queues.get(QueueType.CURRENT).size());
        return false;
    }

    private void moveCommandsFromRetryToMainQueue() {
        for (CommandData cd : get(QueueType.RETRY)) {
            if (cd.executedMoreSecondsAgoThan(MIN_RETRY_PERIOD_SECONDS) && addToMainQueue(cd)) {
                get(QueueType.RETRY).remove(cd);
                MyLog.v(this, () -> "Moved from Retry to Main queue: " + cd);
            }
        }
        mRetryQueueProcessedAt.set(System.currentTimeMillis());
    }

    private CommandData findInRetryQueue(CommandData cdIn) {
        CommandData cdOut = cdIn;
        if (get(QueueType.RETRY).contains(cdIn)) {
            for (CommandData cd : get(QueueType.RETRY)) {
                if (cd.equals(cdIn)) {
                    cd.resetRetries();
                    if (cdIn.isManuallyLaunched() || cd.executedMoreSecondsAgoThan(MIN_RETRY_PERIOD_SECONDS)) {
                        cdOut = cd;
                        get(QueueType.RETRY).remove(cd);
                        MyLog.v(this, () -> "Returned from Retry queue: " + cd);
                    } else {
                        cdOut = null;
                        MyLog.v(this, () -> "Found in Retry queue: " + cd);
                    }
                    break;
                }
            }
        }
        return cdOut;
    }

    private CommandData findInErrorQueue(CommandData cdIn) {
        CommandData cdOut = cdIn;
        if (get(QueueType.ERROR).contains(cdIn)) {
            for (CommandData cd : get(QueueType.ERROR)) {
                if (cd.equals(cdIn)) {
                    if (cdIn.isManuallyLaunched() || cd.executedMoreSecondsAgoThan(MIN_RETRY_PERIOD_SECONDS)) {
                        cdOut = cd;
                        get(QueueType.ERROR).remove(cd);
                        MyLog.v(this, () -> "Returned from Error queue: " + cd);
                        cd.resetRetries();
                    } else {
                        cdOut = null;
                        MyLog.v(this, () -> "Found in Error queue: " + cd);
                    }
                } else {
                    if (cd.executedMoreSecondsAgoThan(TimeUnit.DAYS.toSeconds(MAX_DAYS_IN_ERROR_QUEUE))) {
                        get(QueueType.ERROR).remove(cd);
                        MyLog.i(this, "Removed old from Error queue: " + cd);
                    }
                }
            }
        }
        return cdOut;
    }
}
