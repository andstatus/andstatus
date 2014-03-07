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

import android.os.Parcel;
import android.os.Parcelable;

import org.andstatus.app.data.TimelineTypeEnum;

/**
 * Result of the command execution
 * See also {@link android.content.SyncStats}
 * @author yvolk@yurivolkov.com
 */
public class CommandResult implements Parcelable {
    
    private long numAuthExceptions = 0;
    private long numIoExceptions = 0;
    private long numParseExceptions = 0;
    protected boolean willRetry = false;
    
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
        String message = hasError() ? (hasHardError() ? "Hard Error" : "Soft Error") : " No errors";
        if (downloadedCount > 0) {
            message += ", " + downloadedCount + " downloaded";
        }
        if (getMessagesAdded() > 0) {
            message += ", " + messagesAdded + " messages";
        }
        if (mentionsAdded > 0) {
            message += ", " + mentionsAdded + " mentions";
        }
        if (directedAdded > 0) {
            message += ", " + directedAdded + " directs";
        }
        
        return message;
    }

    public CommandResult(Parcel parcel) {
        numAuthExceptions = parcel.readLong();
        numIoExceptions = parcel.readLong();
        numParseExceptions = parcel.readLong();
        willRetry = (parcel.readInt() != 0);
        hourlyLimit = parcel.readInt();
        remainingHits = parcel.readInt();
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

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(numAuthExceptions);
        dest.writeLong(numIoExceptions);
        dest.writeLong(numParseExceptions);
        dest.writeInt(willRetry ? 1 : 0);
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

    public static String toString(CommandResult commandResult) {
        return commandResult == null ? "result is null" : commandResult.toString();
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

    public void incrementParseExceptions() {
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
}
