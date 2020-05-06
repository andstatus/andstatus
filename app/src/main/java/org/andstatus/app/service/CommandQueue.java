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
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;

import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.database.table.CommandTable;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.RelativeTime;
import org.andstatus.app.util.TryUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import io.vavr.control.Try;

/**
 * @author yvolk@yurivolkov.com
 */
public class CommandQueue {
    private static final String TAG = CommandQueue.class.getSimpleName();

    private static final int INITIAL_CAPACITY = 100;
    private static final long RETRY_QUEUE_PROCESSING_PERIOD_SECONDS = 900;
    private static final long MIN_RETRY_PERIOD_SECONDS = 900;
    private static final long MAX_DAYS_IN_ERROR_QUEUE = 10;
    private final static Queue<CommandData> preQueue = new PriorityBlockingQueue<>(INITIAL_CAPACITY);

    private final MyContext myContext;
    private final AtomicLong mRetryQueueProcessedAt = new AtomicLong();
    private final Map<QueueType, OneQueue> queues = new HashMap<>();
    private volatile boolean loaded = false;
    private volatile boolean changed = false;

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

        public void clear() {
            queue.clear();
        }

        public boolean isEmpty() {
            return queue.isEmpty();
        }

        private boolean hasForegroundTasks() {
            for (CommandData commandData : queue) {
                if (commandData.isInForeground()) {
                    return true;
                }
            }
            return false;
        }

        public int size() {
            return queue.size();
        }
    }

    public CommandQueue(MyContext myContext) {
        this.myContext = myContext;
        for (QueueType queueType : QueueType.values()) {
            if (queueType.createQueue) queues.put(queueType, new OneQueue());
        }
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
            MyLog.v(TAG, "Already loaded");
        } else {
            int count = load(QueueType.CURRENT) + load(QueueType.RETRY);
            int countError = load(QueueType.ERROR);
            MyLog.d(TAG, "State restored, " + (count > 0 ? Integer.toString(count) : "no ")
                    + " msg in the Queues"
                    + (countError > 0 ? ", plus " + countError + " in Error queue" : "")
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
            MyLog.d(TAG, method + "; Database is unavailable");
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
                    MyLog.e(TAG, method + "; empty skipped " + cd);
                } else if (queue.contains(cd)) {
                    MyLog.e(TAG, method + "; duplicate skipped " + cd);
                } else {
                    if (queue.offer(cd)) {
                        count++;
                        if (MyLog.isVerboseEnabled() && (count < 6 || cd.getCommand() == CommandEnum.UPDATE_NOTE)) {
                            MyLog.v(TAG, method + "; " + count + ": " + cd.toString());
                        }
                    } else {
                        MyLog.e(TAG, method + "; " + cd);
                    }
                }
            }
        } finally {
            DbUtils.closeSilently(c);
        }
        MyLog.d(TAG, method + "; loaded " + count + " commands from '" + queueType + "'");
        return count;
    }

    public synchronized void save() {
        if (!changed && preQueue.isEmpty()) {
            MyLog.v(TAG, () -> "save; Nothing to save. changed:" + changed + "; preQueueIsEmpty:" + preQueue.isEmpty());
            return;
        }

        SQLiteDatabase db = myContext.getDatabase();
        if (db == null) {
            MyLog.d(TAG, "save; Database is unavailable");
            return;
        }
        if (!myContext.isReady() && !myContext.isExpired()) {
            MyLog.d(TAG, "save; Cannot save: context is " + myContext.state());
            return;
        }
        if (loaded) clearQueuesInDatabase(db);
        moveCommandsFromPreToMainQueue();
        Try<Integer> countCurrentRetry = save(db, QueueType.CURRENT)
                .flatMap(i1 -> save(db, QueueType.RETRY).map(i2 -> i1 + i2));
        Try<Integer> countError = save(db, QueueType.ERROR);
        MyLog.d(TAG, (loaded ? "Queues saved" : "Saved new queued commands only") + ", "
            + ( countCurrentRetry.isFailure() || countError.isFailure()
                ? " Error saving commands!"
                : ((countCurrentRetry.get() > 0 ? Integer.toString(countCurrentRetry.get()) : "no") + " commands"
                    + (countError.get() > 0 ? ", plus " + countError.get() + " in Error queue" : ""))
            )
        );
        changed = false;
    }

    /** @return Number of items persisted */
    private Try<Integer> save(@NonNull SQLiteDatabase db, @NonNull QueueType queueType) {
        final String method = "saveQueue-" + queueType.save();
        OneQueue oneQueue = queues.get(queueType);
        Queue<CommandData> queue = oneQueue.queue;
        int count = 0;
        try {
            if (!queue.isEmpty()) {
                List<CommandData> commands = new ArrayList<>();
                while (!queue.isEmpty() && count < 300) {
                    CommandData cd = queue.poll();
                    ContentValues values = new ContentValues();
                    cd.toContentValues(values);
                    values.put(CommandTable.QUEUE_TYPE, queueType.save());
                    db.insert(CommandTable.TABLE_NAME, null, values);
                    count++;
                    commands.add(cd);
                    if (MyLog.isVerboseEnabled() && (count < 6 || cd.getCommand() == CommandEnum.UPDATE_NOTE)) {
                        MyLog.v(TAG, method + "; " + count + ": " + cd.toString());
                    }
                    if (myContext.isTestRun() && queue.contains(cd)) {
                        MyLog.e(TAG, method + "; Duplicated command in a queue:" + count + " " + cd.toString());
                    }
                }
                // And add all commands back to the queue, so we won't need to reload them from a database
                commands.forEach(queue::offer);
                MyLog.d(TAG, method + "; " + count + " saved" +
                        (queue.isEmpty() ? "" : ", " + queue.size() + " left"));
            }
        } catch (Exception e) {
            String msgLog = method + "; " + count + " saved, " + queue.size() + " left.";
            MyLog.e(TAG, msgLog, e);
            return TryUtils.failure(msgLog, e);
        }
        return Try.success(count);
    }

    private synchronized Try<Boolean> clearQueuesInDatabase(@NonNull SQLiteDatabase db) {
        final String method = "clearQueuesInDatabase";
        try {
            String sql = "DELETE FROM " + CommandTable.TABLE_NAME;
            DbUtils.execSQL(db, sql);
        } catch (Exception e) {
            MyLog.e(TAG, method, e);
            return TryUtils.failure(method, e);
        }
        return TryUtils.TRUE;
    }

    void clear() {
        loaded = true;
        for ( Map.Entry<QueueType, OneQueue> entry : queues.entrySet()) {
            entry.getValue().clear();
        }
        preQueue.clear();
        changed = true;
        save();
        MyLog.v(TAG, "Queues cleared");
    }

    void deleteCommand(CommandData commandData) {
        moveCommandsFromPreToMainQueue();
        for (OneQueue oneQueue : queues.values()) {
            if (commandData.deleteCommandFromQueue(oneQueue.queue)) {
                changed = true;
            }
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
            MyLog.e(TAG, queueType.name() + " is full?");
        }
        changed = true;
    }

    boolean isAnythingToExecuteNow() {
        return !loaded || !preQueue.isEmpty() || isAnythingToExecuteNowIn(QueueType.CURRENT)
                || isAnythingToRetryNow();
    }

    private boolean isAnythingToRetryNow() {
        return RelativeTime.moreSecondsAgoThan(mRetryQueueProcessedAt.get(),
                RETRY_QUEUE_PROCESSING_PERIOD_SECONDS) && isAnythingToExecuteNowIn(QueueType.RETRY);
    }

    private boolean isAnythingToExecuteNowIn(@NonNull QueueType queueType) {
        if (!loaded) {
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
        MyLog.v(TAG, "Polled in "
                + (myContext.isInForeground() ? "foreground "
                    + (MyPreferences.isSyncWhileUsingApplicationEnabled() ? "enabled" : "disabled")
                  : "background")
                + (commandData == null ? "" : " " + commandData));
        if (commandData != null) {
            changed = true;
            commandData.setManuallyLaunched(false);
        }
        return commandData;
    }

    private void moveCommandsFromPreToMainQueue() {
        for (CommandData cd : preQueue) {
            if (addToMainQueue(cd)) {
                preQueue.remove(cd);
            }
        }
    }

    /** @return true if success */
    private boolean addToMainQueue(CommandData commandData) {
        if (get(QueueType.CURRENT).contains(commandData)) {
            MyLog.v(TAG, () -> "Didn't add to Main queue. Already found " + commandData);
            return true;
        }
        commandData.getResult().prepareForLaunch();
        MyLog.v(TAG, () -> "Adding to Main queue " + commandData);
        changed = true;
        if (get(QueueType.CURRENT).offer(commandData)) return true;

        MyLog.e(TAG, "Couldn't add to the main queue, size=" + queues.get(QueueType.CURRENT).size());
        return false;
    }

    private void moveCommandsFromRetryToMainQueue() {
        for (CommandData cd : get(QueueType.RETRY)) {
            if (cd.executedMoreSecondsAgoThan(MIN_RETRY_PERIOD_SECONDS) && addToMainQueue(cd)) {
                get(QueueType.RETRY).remove(cd);
                changed = true;
                MyLog.v(TAG, () -> "Moved from Retry to Main queue: " + cd);
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
                        changed = true;
                        MyLog.v(TAG, () -> "Returned from Retry queue: " + cd);
                    } else {
                        cdOut = null;
                        MyLog.v(TAG, () -> "Found in Retry queue, but left there: " + cd);
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
                        changed = true;
                        MyLog.v(TAG, () -> "Returned from Error queue: " + cd);
                        cd.resetRetries();
                    } else {
                        cdOut = null;
                        MyLog.v(TAG, () -> "Found in Error queue, but left there: " + cd);
                    }
                } else {
                    if (cd.executedMoreSecondsAgoThan(TimeUnit.DAYS.toSeconds(MAX_DAYS_IN_ERROR_QUEUE))) {
                        get(QueueType.ERROR).remove(cd);
                        changed = true;
                        MyLog.i(TAG, "Removed old from Error queue: " + cd);
                    }
                }
            }
        }
        return cdOut;
    }
}
