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
import android.preference.PreferenceManager;

import org.andstatus.app.R;
import org.andstatus.app.account.PersistentAccounts;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.TimelineSearchSuggestionsProvider;
import org.andstatus.app.service.MyService.ServiceState;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.FileUtils;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class MyBackupAgent extends BackupAgent {
    public static final int BACKUP_VERSION = 1;
    private static final int BACKUP_VERSION_UNKNOWN = -1;
    public static final String KEY_DATABASE = "database";
    public static final String KEY_SHARED_PREFERENCES = "shared_preferences";
    public static final String SHARED_PREFERENCES_FILENAME = "shared_preferences";

    private String previousKey = "";
    
    long accountsBackedUp = 0;
    long accountsRestored = 0;
    long databasesBackedUp = 0;
    long databasesRestored = 0;
    long suggestionsBackedUp = 0;
    long suggestionsRestored = 0;
    long sharedPreferencesBackedUp = 0;
    long sharedPreferencesRestored = 0;
    
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
        MyLog.i(this, method + " started"
                + (data != null && data.getDataFolder() != null ? ", folder='"
                        + data.getDataFolder().getAbsolutePath() + "'" : "")
                + ", " + (oldDescriptor.saved() ? "oldState:" + oldDescriptor.toString()
                        : "no old state"));
        MyContextHolder.initialize(this, this);
        MyBackupDescriptor newDescriptor = MyBackupDescriptor.fromParcelFileDescriptor(
                BACKUP_VERSION, newState);
        try {
            if (data == null) {
                throw new FileNotFoundException("No BackupDataOutput");
            } else if (!MyContextHolder.get().isReady()) {
                throw new FileNotFoundException("Application context is not initialized");
            } else if (MyContextHolder.get().persistentAccounts().size() == 0) {
                throw new FileNotFoundException("Nothing to backup - No accounts yet");
            } else {
                boolean isServiceAvailableStored = MyServiceManager.isServiceAvailable();
                MyServiceManager.setServiceUnavailable();
                if (MyServiceManager.getServiceState() != ServiceState.STOPPED) {
                    throw new FileNotFoundException(getString(R.string.system_is_busy_try_later));
                }
                doBackup(data, newDescriptor);
                newDescriptor.save();
                MyLog.v(this, method + "; newState: " + newDescriptor.toString());
                if (isServiceAvailableStored) {
                    MyServiceManager.setServiceAvailable();
                }
            }
        } finally {
            MyLog.i(this, method + " ended, " + (newDescriptor.saved() ? "success" : "failure"));
        }
    }

    private void doBackup(MyBackupDataOutput data, MyBackupDescriptor newDescriptor) throws IOException {
        sharedPreferencesBackedUp = backupFile(data,
                KEY_SHARED_PREFERENCES,
                SharedPreferencesUtil.sharedPreferencesPath(MyContextHolder.get().context()));
        databasesBackedUp = backupFile(data,
                KEY_DATABASE + "_" + MyDatabase.DATABASE_NAME,
                MyPreferences.getDatabasePath(MyDatabase.DATABASE_NAME, null));
        suggestionsBackedUp = backupFile(data,
                KEY_DATABASE + "_" + TimelineSearchSuggestionsProvider.DATABASE_NAME,
                MyPreferences.getDatabasePath(TimelineSearchSuggestionsProvider.DATABASE_NAME, null));
        accountsBackedUp = MyContextHolder.get().persistentAccounts().onBackup(data, newDescriptor);
    }
    
    private long backupFile(MyBackupDataOutput data, String key, File dataFile) throws IOException {
        final int chunkSize = 10000;
        long backedUpCount = 0;
        if (dataFile.exists()) {
            long fileLength = dataFile.length();
            if ( fileLength > Integer.MAX_VALUE) {
                throw new FileNotFoundException("File '" 
                        + dataFile.getName() + "' is too large for backup: " + fileLength );
            } else {
                int bytesToWrite = (int) fileLength;
                data.writeEntityHeader(key, bytesToWrite);
                int bytesWritten = 0;
                while (bytesWritten < bytesToWrite) {
                    byte[] bytes = FileUtils.getBytes(dataFile, bytesWritten, chunkSize);
                    if (bytes.length <= 0) {
                        break;
                    }
                    bytesWritten += bytes.length;
                    data.writeEntityData(bytes, bytes.length);
                }
                if (bytesWritten != bytesToWrite) {
                    throw new FileNotFoundException("Couldn't backup File '" 
                            + dataFile.getName() + "'. written " 
                            + bytesWritten + " of " + bytesToWrite + " bytes");
                }
                backedUpCount++;
            }
            MyLog.v(this, "Backed up file key='" + key + "', name='" + dataFile.getName()
                    + "', length=" + fileLength);
        } else {
            MyLog.v(this, "File doesn't exist key='" + key + "', path='" + dataFile.getAbsolutePath());
        }
        return backedUpCount;
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
        MyLog.i(this, method + " started" 
                + (data != null && data.getDataFolder() != null ? ", folder='"
                        + data.getDataFolder().getAbsolutePath() + "'" : "")
                + ", " + (newDescriptor.saved() ? " newState:" + newDescriptor.toString() 
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
                    } else {
                        ensureNoDataIsPresent();
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

    private void ensureNoDataIsPresent() throws IOException {
        if (MyPreferences.shouldSetDefaultValues()) {
            return;
        }
        MyContextHolder.initialize(this, this);
        if (!MyContextHolder.get().isReady()) {
            throw new FileNotFoundException("Application context is not initialized");
        } else if ( MyContextHolder.get().persistentAccounts().size() > 0) {
            throw new FileNotFoundException("Cannot restore: AndStatus accounts are present. Please reinstall application before restore");
        }

        MyServiceManager.setServiceUnavailable();
        MyDatabase db = MyContextHolder.get().getDatabase();
        if (db != null) {
            db.close();
            db = null;
        }
        MyContextHolder.release();
    }
    
    private void doRestore(MyBackupDataInput data, MyBackupDescriptor newDescriptor) throws IOException {
        PreferenceManager.setDefaultValues(MyContextHolder.get().context(), R.xml.preferences, false);
        assertNextHeader(data, KEY_SHARED_PREFERENCES);
        sharedPreferencesRestored += restoreFile(data, 
                SharedPreferencesUtil.sharedPreferencesPath(MyContextHolder.get().context()));
        assertNextHeader(data, KEY_DATABASE + "_" + MyDatabase.DATABASE_NAME);
        databasesRestored += restoreFile(data,
                    MyPreferences.getDatabasePath(MyDatabase.DATABASE_NAME, null));
        if (optionalNextHeader(data, KEY_DATABASE + "_" + TimelineSearchSuggestionsProvider.DATABASE_NAME)) {
            suggestionsRestored += restoreFile(data,
                    MyPreferences.getDatabasePath(TimelineSearchSuggestionsProvider.DATABASE_NAME, null));            
        }
        MyContextHolder.initialize(this, this);
        assertNextHeader(data, PersistentAccounts.KEY_ACCOUNT);
        accountsRestored += MyContextHolder.get().persistentAccounts().onRestore(data, newDescriptor);
        MyContextHolder.get().setExpired();
    }
    
    private void assertNextHeader(MyBackupDataInput data, String key) throws IOException {
        if (!key.equals(previousKey) && !data.readNextHeader()) {
            throw new FileNotFoundException("Unexpected end of backup on key='" + key + "'");
        }
        previousKey = data.getKey();
        if (!key.equals(data.getKey())) {
            throw new FileNotFoundException("Expected key='" + key + "' but was found key='" + data.getKey() + "'");
        }
    }

    private boolean optionalNextHeader(MyBackupDataInput data, String key) throws IOException {
        if (data.readNextHeader()) {
            previousKey = data.getKey();
            return key.equals(data.getKey());
        }
        return false;
    }
    
    /** Returns count of restores files */
    public long restoreFile(MyBackupDataInput data, File dataFile) throws IOException {
        if (dataFile.exists()) {
            if (!dataFile.delete()) {
                throw new FileNotFoundException("Couldn't delete old file before restore '" 
                        + dataFile.getName() + "'");
            }
        }
        final String method = "restoreFile";
        MyLog.i(this, method + " started, key='" + data.getKey() + "', size=" + data.getDataSize() + " bytes");
        final int chunkSize = 10000;
        int bytesToWrite = data.getDataSize();
        int bytesWritten = 0;
        FileOutputStream output = new FileOutputStream(dataFile, false);
        try {
            while (bytesToWrite > bytesWritten) {
                byte[] bytes = new byte[chunkSize];
                int bytesRead = data.readEntityData(bytes, 0, bytes.length);
                if (bytesRead == 0) {
                    break;
                }
                output.write(bytes, 0, bytesRead);
                bytesWritten += bytesRead;
            }
         } finally {
            output.close();
         }
        return 1;
    }
    
}
