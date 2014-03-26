/**
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.content.SharedPreferences;
import android.os.Parcel;
import android.os.Parcelable;

import org.andstatus.app.IntentExtra;
import org.andstatus.app.data.TimelineTypeEnum;
import org.andstatus.app.util.MyLog;

/**
 * Result of the command execution
 * See also {@link android.content.SyncStats}
 * @author yvolk@yurivolkov.com
 */
public final class CommandResult implements Parcelable {
    static final int MAX_RETRIES = 10;
    
    private int executionCount = 0;
    private int retriesLeft = 0;
    private long numAuthExceptions = 0;
    private long numIoExceptions = 0;
    private long numParseExceptions = 0;

    private long itemId = 0;
    
    // 0 means these values were not set
    private int hourlyLimit = 0;
    private int remainingHits = 0;
    
    // Counters to use for user notifications
    private int messagesAdded = 0;
    private int mentionsAdded = 0;
    private int directedAdded = 0;
    private int downloadedCount = 0;

    public CommandResult() {
    }
    
    @Override
    public String toString() {
        StringBuilder message = new StringBuilder();
        if (executionCount > 0) {
            message.append("executed:" + executionCount + ",");
            if (retriesLeft > 0) {
                message.append("retriesLeft:" + retriesLeft + ",");
            }
            if (!hasError()) {
                message.append("error:None,");
            }
        }
        if (hasError()) {
            message.append("error:" + (hasHardError() ? "Hard" : "Soft") + ",");
        }
        if (downloadedCount > 0) {
            message.append("downloaded:" + downloadedCount + ",");
        }
        if (messagesAdded > 0) {
            message.append("messagesAdded:" + messagesAdded + ",");
        }
        if (mentionsAdded > 0) {
            message.append("mentionsAdded:" + mentionsAdded + ",");
        }
        if (directedAdded > 0) {
            message.append("directedAdded:" + directedAdded + ",");
        }
        
        return MyLog.formatKeyValue("CommandResult", message);
    }

    public CommandResult(Parcel parcel) {
        executionCount = parcel.readInt();
        retriesLeft = parcel.readInt();
        numAuthExceptions = parcel.readLong();
        numIoExceptions = parcel.readLong();
        numParseExceptions = parcel.readLong();
        hourlyLimit = parcel.readInt();
        remainingHits = parcel.readInt();
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

    void saveToSharedPreferences(android.content.SharedPreferences.Editor ed, int index) {
        String si = Integer.toString(index);
        if (executionCount > 0) {
            ed.putInt(IntentExtra.EXTRA_EXECUTION_COUNT.key + si, executionCount);
        }
        ed.putInt(IntentExtra.EXTRA_RETRIES_LEFT.key + si, retriesLeft);
    }

    void loadFromSharedPreferences(SharedPreferences sp, int index) {
        String si = Integer.toString(index);
        executionCount = sp.getInt(IntentExtra.EXTRA_EXECUTION_COUNT.key + si, executionCount);
        retriesLeft = sp.getInt(IntentExtra.EXTRA_RETRIES_LEFT.key + si, retriesLeft);
    }
    
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(executionCount);
        dest.writeInt(retriesLeft);
        dest.writeLong(numAuthExceptions);
        dest.writeLong(numIoExceptions);
        dest.writeLong(numParseExceptions);
        dest.writeInt(hourlyLimit);
        dest.writeInt(remainingHits);
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

    public void setSoftErrorIfNotOk(boolean ok) {
        if (!ok) {
            incrementNumIoExceptions();
        }
    }
    
    public static String toString(CommandResult commandResult) {
        return commandResult == null ? "(result is null)" : commandResult.toString();
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

    public void incrementMessagesCount(TimelineTypeEnum timelineType) {
        switch (timelineType) {
            case HOME:
                messagesAdded++;
                break;
            case DIRECT:
                directedAdded++;
                break;
            default:
                break;
        }
    }

    public void incrementMentionsCount() {
        mentionsAdded++;
    }

    public void incrementDownloadedCount() {
        downloadedCount++;
    }
    
    protected int getMessagesAdded() {
        return messagesAdded;
    }

    protected int getMentionsAdded() {
        return mentionsAdded;
    }

    protected int getDirectedAdded() {
        return directedAdded;
    }
    
    protected int getRetriesLeft() {
        return retriesLeft;
    }
    
    void resetRetries(CommandEnum command) {
        retriesLeft = MAX_RETRIES;
        switch (command) {
            case AUTOMATIC_UPDATE:
            case FETCH_TIMELINE:
            case RATE_LIMIT_STATUS:
            case SEARCH_MESSAGE:
                retriesLeft = 0;
                break;
            default:
                break;
        }
    }

    /**
     * Before execution started
     */
    void onLaunched() {
        numAuthExceptions = 0;
        numIoExceptions = 0;
        numParseExceptions = 0;
        
        hourlyLimit = 0;
        remainingHits = 0;

        messagesAdded = 0;
        mentionsAdded = 0;
        directedAdded = 0;
    }
    
    /**
     * After execution ended
     */
    void onExecuted() {
        executionCount++;
        if (retriesLeft > 0) {
            retriesLeft -= 1;
        }
    }
    
    boolean shouldWeRetry() {
        boolean retry = false;
        if (hasError() && !hasHardError()) {
            if (retriesLeft > 0) {
                retry = true;
            }
        }
        return retry;
    }

    long getItemId() {
        return itemId;
    }

    void setItemId(long itemId) {
        this.itemId = itemId;
    }
}
