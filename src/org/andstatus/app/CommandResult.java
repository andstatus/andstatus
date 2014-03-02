/**
 * Copyright (C) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Result of the command execution
 * See also {@link android.content.SyncStats}
 * @author yvolk@yurivolkov.com
 */
public class CommandResult implements Parcelable {
    
    public long numAuthExceptions = 0;
    public long numIoExceptions = 0;
    public long numParseExceptions = 0;
    public boolean willRetry = false;
    
    // 0 means these values were not set
    public int hourlyLimit = 0;
    public int remainingHits = 0;

    public CommandResult() {
    }
    
    @Override
    public String toString() {
        return hasError() ? (hasHardError() ? "Hard Error" : "Soft Error") : " No errors";
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
}
