/*
 * Copyright (C) 2014-2017 yvolk (Yuri Volkov), http://yurivolkov.com
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
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.database.table.CommandTable;
import org.andstatus.app.notification.NotificationEventType;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.RelativeTime;
import org.andstatus.app.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Result of the command execution
 * See also {@link android.content.SyncStats}
 * @author yvolk@yurivolkov.com
 */
public final class CommandResult implements Parcelable {
    static final int INITIAL_NUMBER_OF_RETRIES = 10;
    
    private long lastExecutedDate = 0;
    private int executionCount = 0;
    private int retriesLeft = 0;
    
    private boolean executed = false;
    private long numAuthExceptions = 0;
    private long numIoExceptions = 0;
    private long numParseExceptions = 0;
    private String mMessage = "";
    private String progress = "";

    private long itemId = 0;
    
    // 0 means these values were not set
    private int hourlyLimit = 0;
    private int remainingHits = 0;
    
    // Counters to use for user notifications
    private long downloadedCount = 0;
    private long newCount = 0;
    public final Map<NotificationEventType, AtomicLong> notificationEventCounts = new HashMap<>();

    public CommandResult() {
    }

    public static final Creator<CommandResult> CREATOR = new Creator<CommandResult>() {
        @Override
        public CommandResult createFromParcel(Parcel in) {
            return new CommandResult(in);
        }

        @Override
        public CommandResult[] newArray(int size) {
            return new CommandResult[size];
        }
    };
    
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(lastExecutedDate);
        dest.writeInt(executionCount);
        dest.writeInt(retriesLeft);
        dest.writeLong(numAuthExceptions);
        dest.writeLong(numIoExceptions);
        dest.writeLong(numParseExceptions);
        dest.writeString(mMessage);
        dest.writeLong(itemId);
        dest.writeInt(hourlyLimit);
        dest.writeInt(remainingHits);
        dest.writeLong(downloadedCount);
        dest.writeString(progress);
    }
    
    public CommandResult(Parcel parcel) {
        lastExecutedDate = parcel.readLong();
        executionCount = parcel.readInt();
        retriesLeft = parcel.readInt();
        numAuthExceptions = parcel.readLong();
        numIoExceptions = parcel.readLong();
        numParseExceptions = parcel.readLong();
        mMessage = parcel.readString();
        itemId = parcel.readLong();
        hourlyLimit = parcel.readInt();
        remainingHits = parcel.readInt();
        downloadedCount = parcel.readLong();
        progress = parcel.readString();
    }

    public void toContentValues(ContentValues values) {
        values.put(CommandTable.LAST_EXECUTED_DATE, lastExecutedDate);
        values.put(CommandTable.EXECUTION_COUNT, executionCount);
        values.put(CommandTable.RETRIES_LEFT, retriesLeft);
        values.put(CommandTable.NUM_AUTH_EXCEPTIONS, numAuthExceptions);
        values.put(CommandTable.NUM_IO_EXCEPTIONS, numIoExceptions);
        values.put(CommandTable.NUM_PARSE_EXCEPTIONS, numParseExceptions);
        values.put(CommandTable.ERROR_MESSAGE, mMessage);
        values.put(CommandTable.DOWNLOADED_COUNT, downloadedCount);
        values.put(CommandTable.PROGRESS_TEXT, progress);
    }

    public static CommandResult fromCursor(Cursor cursor) {
        CommandResult result = new CommandResult();
        result.lastExecutedDate = DbUtils.getLong(cursor, CommandTable.LAST_EXECUTED_DATE);
        result.executionCount = DbUtils.getInt(cursor, CommandTable.EXECUTION_COUNT);
        result.retriesLeft = DbUtils.getInt(cursor, CommandTable.RETRIES_LEFT);
        result.numAuthExceptions = DbUtils.getLong(cursor, CommandTable.NUM_AUTH_EXCEPTIONS);
        result.numIoExceptions = DbUtils.getLong(cursor, CommandTable.NUM_IO_EXCEPTIONS);
        result.numParseExceptions = DbUtils.getLong(cursor, CommandTable.NUM_PARSE_EXCEPTIONS);
        result.mMessage = DbUtils.getString(cursor, CommandTable.ERROR_MESSAGE);
        result.downloadedCount = DbUtils.getInt(cursor, CommandTable.DOWNLOADED_COUNT);
        result.progress = DbUtils.getString(cursor, CommandTable.PROGRESS_TEXT);
        return result;
    }

    public int getExecutionCount() {
        return executionCount;
    }

    public boolean hasError() {
        return hasSoftError() || hasHardError();
    }
    
    public boolean hasHardError() {
        return numAuthExceptions > 0 || numParseExceptions > 0;
    }

    public boolean hasSoftError() {
        return numIoExceptions > 0;
    }
    
    @Override
    public int describeContents() {
        return 0;
    }
    
    public void setSoftErrorIfNotOk(boolean ok) {
        if (!ok) {
            incrementNumIoExceptions();
        }
    }
    
    public static String toString(CommandResult commandResult) {
        return commandResult == null ? "(result is null)" : commandResult.toString();
    }
    
    @Override
    public String toString() {
        return MyLog.formatKeyValue("CommandResult", toSummaryBuilder());
    }

    public String toSummary() {
        return toSummaryBuilder().toString();
    }
    
    private StringBuilder toSummaryBuilder() {
        StringBuilder message = new StringBuilder();
        if (executionCount > 0) {
            message.append("executed:" + executionCount + ", ");
            message.append("last:" + RelativeTime.getDifference(MyContextHolder.get().context(), lastExecutedDate) + ", ");
            if (retriesLeft > 0) {
                message.append("retriesLeft:" + retriesLeft + ", ");
            }
            if (!hasError()) {
                message.append("error:None, ");
            }
        }
        if (hasError()) {
            message.append("error:" + (hasHardError() ? "Hard" : "Soft") + ", ");
        }
        if (downloadedCount > 0) {
            message.append("downloaded:" + downloadedCount + ", ");
        }
        if (newCount > 0) {
            message.append("new:" + newCount + ", ");
        }
        notificationEventCounts.forEach( (event, count) -> {
            if (count.get() > 0) message.append(event.name() + ":" + count.get() + ", ");
        });
        if (StringUtils.nonEmpty(mMessage)) {
            message.append(" \n" + mMessage);
        }
        return message;
    }
    
    public long getNumAuthExceptions() {
        return numAuthExceptions;
    }

    protected void incrementNumAuthExceptions() {
        numAuthExceptions++;
    }

    public long getNumIoExceptions() {
        return numIoExceptions;
    }

    public void incrementNumIoExceptions() {
        numIoExceptions++;
    }

    public long getNumParseExceptions() {
        return numParseExceptions;
    }

    void incrementParseExceptions() {
        numParseExceptions++;
    }

    public int getHourlyLimit() {
        return hourlyLimit;
    }

    protected void setHourlyLimit(int hourlyLimit) {
        this.hourlyLimit = hourlyLimit;
    }

    public int getRemainingHits() {
        return remainingHits;
    }

    protected void setRemainingHits(int remainingHits) {
        this.remainingHits = remainingHits;
    }

    public void incrementNewCount() {
        newCount++;
    }

    public void onNotificationEvent(@NonNull NotificationEventType event) {
        if (event == NotificationEventType.EMPTY) return;

        AtomicLong count = notificationEventCounts.get(event);
        if (count == null) {
            notificationEventCounts.put(event, new AtomicLong(1));
        } else {
            count.incrementAndGet();
        }
    }

    public void incrementDownloadedCount() {
        downloadedCount++;
    }

    public long getDownloadedCount() {
        return downloadedCount;
    }
    
    public long getNewCount() {
        return newCount;
    }

    protected int getRetriesLeft() {
        return retriesLeft;
    }
    
    void resetRetries(CommandEnum command) {
        retriesLeft = INITIAL_NUMBER_OF_RETRIES;
        switch (command) {
            case GET_TIMELINE:
            case GET_OLDER_TIMELINE:
            case RATE_LIMIT_STATUS:
                retriesLeft = 0;
                break;
            default:
                break;
        }
        prepareForLaunch();
    }

    void prepareForLaunch() {
        executed = false;
        
        numAuthExceptions = 0;
        numIoExceptions = 0;
        numParseExceptions = 0;
        mMessage = "";
        
        itemId = 0;
        
        hourlyLimit = 0;
        remainingHits = 0;

        newCount = 0;
        notificationEventCounts.values().forEach(c -> c.set(0));
        downloadedCount = 0;

        progress = "";
    }
    
    void afterExecutionEnded() {
        executed = true;
        executionCount++;
        if (retriesLeft > 0) {
            retriesLeft -= 1;
        }
        lastExecutedDate = System.currentTimeMillis();
    }
    
    boolean shouldWeRetry() {
        return (!executed || hasError()) && !hasHardError() && (retriesLeft > 0);
    }

    long getItemId() {
        return itemId;
    }

    void setItemId(long itemId) {
        this.itemId = itemId;
    }

    public long getLastExecutedDate() {
        return lastExecutedDate;
    }

    public String getMessage() {
        return mMessage;
    }

    public void setMessage(String message) {
        this.mMessage = message;
    }

    public String getProgress() {
        return progress;
    }

    public void setProgress(String progress) {
        this.progress = progress;
    }
}
