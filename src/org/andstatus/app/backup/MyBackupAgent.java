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

package org.andstatus.app.backup;

import android.app.backup.BackupAgent;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.os.ParcelFileDescriptor;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.util.MyLog;

import java.io.FileNotFoundException;
import java.io.IOException;

public class MyBackupAgent extends BackupAgent {
    public static final int BACKUP_VERSION = 1;
    private static final int BACKUP_VERSION_UNKNOWN = -1;

    @Override
    public void onBackup(ParcelFileDescriptor oldState, BackupDataOutput data,
            ParcelFileDescriptor newState) throws IOException {
        onBackup(oldState, new MyBackupDataOutput(data), newState);
    }

    public void onBackup(ParcelFileDescriptor oldState, MyBackupDataOutput data,
            ParcelFileDescriptor newState) throws IOException {
        final String method = "onBackup";
        MyBackupDescriptor oldDescriptor = MyBackupDescriptor.fromParcelFileDescriptor(
                MyBackupAgent.BACKUP_VERSION_UNKNOWN, oldState);
        // Ignore oldDescriptor for now...
        MyLog.i(this, method + " started, "
                        + (oldDescriptor.saved() ? " oldState:" + oldDescriptor.toString()
                                : "no old state"));
        MyBackupDescriptor newDescriptor = MyBackupDescriptor.fromParcelFileDescriptor(
                BACKUP_VERSION, newState);
        try {
            if (data == null) {
                throw new FileNotFoundException("No BackupDataOutput");
            } else if (!MyContextHolder.get().isReady()) {
                throw new FileNotFoundException("Appplcation context is not initialized");
            } else {
                doBackup(data, newDescriptor);
                newDescriptor.save();
                MyLog.v(this, method + "; newState: " + newDescriptor.toString());
            }
        } finally {
            MyLog.i(this, method + " ended, " + (newDescriptor.saved() ? "success" : "failure"));
        }
    }

    private void doBackup(MyBackupDataOutput data, MyBackupDescriptor newDescriptor) throws IOException {
        MyContextHolder.get().persistentAccounts().onBackup(data, newDescriptor);
    }

    @Override
    public void onRestore(BackupDataInput data, int appVersionCode, ParcelFileDescriptor newState)
            throws IOException {
        onRestore(new MyBackupDataInput(data), appVersionCode, newState);
    }

    public void onRestore(MyBackupDataInput data, int appVersionCode, ParcelFileDescriptor newState)
            throws IOException {
        final String method = "onRestore";
        MyBackupDescriptor newDescriptor = MyBackupDescriptor.fromParcelFileDescriptor(
                BACKUP_VERSION_UNKNOWN, newState);
        MyLog.i(this, method + " started, " 
                + (newDescriptor.saved() ? " newState:" + newDescriptor.toString() 
                        : "no new state"));
        boolean success = false;
        try {
            switch (newDescriptor.getBackupSchemaVersion()) {
                case BACKUP_VERSION_UNKNOWN:
                    throw new FileNotFoundException("No backup information in the backup descriptor");
                case BACKUP_VERSION:
                    if (data == null) {
                        throw new FileNotFoundException("No BackupDataInput");
                    } else if (!newDescriptor.saved()) {
                        throw new FileNotFoundException("No new state");
                    } else if (!MyContextHolder.get().isReady()) {
                        throw new FileNotFoundException("Appplcation context is not initialized");
                    } else {
                        doRestore(data, newDescriptor);
                        success = true;
                    }
                    break;
                default:
                    throw new FileNotFoundException("Incompatible backup version "
                            + newDescriptor.getBackupSchemaVersion() + ", expected:" + BACKUP_VERSION);
            }
        } finally {
            MyLog.i(this, method + " ended, " + (success ? "success" : "failure"));
        }
    }
    
    private void doRestore(MyBackupDataInput data, MyBackupDescriptor newDescriptor) throws IOException {
        while (data.readNextHeader()) {
            MyContextHolder.get().persistentAccounts().onRestore(data, newDescriptor);
        }
    }
}
