/*
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
import android.content.Context;
import android.os.ParcelFileDescriptor;

import org.andstatus.app.R;
import org.andstatus.app.account.PersistentAccounts;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.context.MyPreferencesGroupsEnum;
import org.andstatus.app.context.MyStorage;
import org.andstatus.app.data.DataPruner;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.checker.DataChecker;
import org.andstatus.app.database.DatabaseHolder;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.service.MyServiceState;
import org.andstatus.app.util.FileUtils;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.TriState;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class MyBackupAgent extends BackupAgent {
    public static final String DATABASE_KEY = "database";
    public static final String SHARED_PREFERENCES_KEY = "shared_preferences";

    private MyBackupDescriptor backupDescriptor = null;

    private String previousKey = "";
    
    private long accountsBackedUp = 0;
    long accountsRestored = 0;
    private long databasesBackedUp = 0;
    long databasesRestored = 0;
    private long sharedPreferencesBackedUp = 0;
    long sharedPreferencesRestored = 0;

    public MyBackupAgent() {
    }
    
    void setContext(Context baseContext) {
        attachBaseContext(baseContext);
    }
    
    @Override
    public void onBackup(ParcelFileDescriptor oldState, BackupDataOutput data,
            ParcelFileDescriptor newState) throws IOException {
        if (MyContextHolder.get().isTestRun()) {
            String logmsg = "onBackup; skipped due to test run";
            MyLog.i(this, logmsg);
            throw new IOException(logmsg);
        }
        if (!SharedPreferencesUtil.getBoolean(MyPreferences.KEY_ENABLE_ANDROID_BACKUP, false)) {
            String logmsg = "onBackup; skipped: disabled in Settings";
            MyLog.i(this, logmsg);
            throw new IOException(logmsg);
        }
        onBackup(
                MyBackupDescriptor.fromOldParcelFileDescriptor(oldState, ProgressLogger.getEmpty()),
                new MyBackupDataOutput(data),
                MyBackupDescriptor.fromEmptyParcelFileDescriptor(newState,
                        ProgressLogger.getEmpty()));
    }

    public void onBackup(MyBackupDescriptor oldDescriptor, MyBackupDataOutput data,
            MyBackupDescriptor newDescriptor) throws IOException {
        final String method = "onBackup";
        // Ignore oldDescriptor for now...
        MyLog.i(this, method + " started"
                + (data != null && data.getDataFolder() != null ? ", folder='"
                        + data.getDataFolder().getAbsolutePath() + "'" : "")
                + ", " + (oldDescriptor.saved() ? "oldState:" + oldDescriptor.toString()
                        : "no old state"));
        MyContextHolder.initialize(this, this);
        backupDescriptor = newDescriptor;
        try {
            if (data == null) {
                throw new FileNotFoundException("No BackupDataOutput");
            } else if (!MyContextHolder.get().isReady()) {
                throw new FileNotFoundException("Application context is not initialized");
            } else if (MyContextHolder.get().accounts().isEmpty()) {
                throw new FileNotFoundException("Nothing to backup - No accounts yet");
            } else {
                boolean isServiceAvailableStored = checkAndSetServiceUnavailable();
                doBackup(data);
                backupDescriptor.save();
                MyLog.v(this, method + "; newState: " + backupDescriptor.toString());
                if (isServiceAvailableStored) {
                    MyServiceManager.setServiceAvailable();
                }
            }
        } finally {
            MyLog.i(this, method + " ended, " + (backupDescriptor.saved() ? "success" : "failure"));
        }
    }

    boolean checkAndSetServiceUnavailable() throws IOException {
        boolean isServiceAvailableStored = MyServiceManager.isServiceAvailable();
        MyServiceManager.setServiceUnavailable();
        MyServiceManager.stopService();
        for (int ind=0; ; ind++) {
            if (MyServiceManager.getServiceState() == MyServiceState.STOPPED) {
                break;
            }
            if (ind > 5) {
                throw new FileNotFoundException(getString(R.string.system_is_busy_try_later));
            }
            DbUtils.waitMs("checkAndSetServiceUnavailable", 5000);
        }
        return isServiceAvailableStored;
    }

    private void doBackup(MyBackupDataOutput data) throws IOException {
        sharedPreferencesBackedUp = backupFile(data,
                SHARED_PREFERENCES_KEY,
                SharedPreferencesUtil.defaultSharedPreferencesPath(MyContextHolder.get().context()));
        databasesBackedUp = backupFile(data,
                DATABASE_KEY + "_" + DatabaseHolder.DATABASE_NAME,
                MyStorage.getDatabasePath(DatabaseHolder.DATABASE_NAME));
        accountsBackedUp = MyContextHolder.get().accounts().onBackup(data, backupDescriptor);
    }
    
    private long backupFile(MyBackupDataOutput data, String key, File dataFile) throws IOException {
        long backedUpCount = 0;
        if (dataFile.exists()) {
            long fileLength = dataFile.length();
            if ( fileLength > Integer.MAX_VALUE) {
                throw new FileNotFoundException("File '" 
                        + dataFile.getName() + "' is too large for backup: " + fileLength + " bytes" );
            } 
            int bytesToWrite = (int) fileLength;
            data.writeEntityHeader(key, bytesToWrite, MyBackupDataOutput.getDataFileExtension(dataFile));
            int bytesWritten = 0;
            while (bytesWritten < bytesToWrite) {
                byte[] bytes = FileUtils.getBytes(dataFile, bytesWritten, MyBackupDataInput.fileChunkSize);
                if (bytes.length <= 0) {
                    break;
                }
                bytesWritten += bytes.length;
                data.writeEntityData(bytes, bytes.length);
            }
            if (bytesWritten != bytesToWrite) {
                throw new FileNotFoundException("Couldn't backup "
                        + filePartiallyWritten(key, dataFile, bytesToWrite, bytesWritten));
            }
            backedUpCount++;
            backupDescriptor.getLogger().logProgress(
                    "Backed up " + fileWritten(key, dataFile, bytesWritten));
        } else {
            MyLog.v(this, "File doesn't exist key='" + key + "', path='" + dataFile.getAbsolutePath());
        }
        return backedUpCount;
    }

    private String fileWritten(String key, File dataFile, int bytesWritten) {
        return filePartiallyWritten(key, dataFile, bytesWritten, bytesWritten);
    }
    
    private String filePartiallyWritten(String key, File dataFile, int bytesToWrite, int bytesWritten) {
        if ( bytesWritten == bytesToWrite) {
            return "file:'" + dataFile.getName()
                    + "', key:'" + key + "', length:"
                    + bytesWritten + " bytes";
        } else {
            return "file:'" + dataFile.getName()
                    + "', key:'" + key + "', wrote "
                    + bytesWritten + " of " + bytesToWrite + " bytes";
        }
    }
    
    @Override
    public void onRestore(BackupDataInput data, int appVersionCode, ParcelFileDescriptor newState)
            throws IOException {
        onRestore(new MyBackupDataInput(data), appVersionCode, MyBackupDescriptor.fromOldParcelFileDescriptor(newState, ProgressLogger.getEmpty()));
    }

    public void onRestore(MyBackupDataInput data, int appVersionCode, MyBackupDescriptor newDescriptor)
            throws IOException {
        final String method = "onRestore";
        backupDescriptor = newDescriptor;
        MyLog.i(this, method + " started" 
                + (data != null && data.getDataFolder() != null ? ", folder='"
                        + data.getDataFolder().getAbsolutePath() + "'" : "")
                + ", " + (newDescriptor.saved() ? " newState:" + newDescriptor.toString() 
                        : "no new state"));
        boolean success = false;
        try {
            switch (backupDescriptor.getBackupSchemaVersion()) {
                case MyBackupDescriptor.BACKUP_SCHEMA_VERSION_UNKNOWN:
                    throw new FileNotFoundException("No backup information in the backup descriptor");
                case MyBackupDescriptor.BACKUP_SCHEMA_VERSION:
                    if (data == null) {
                        throw new FileNotFoundException("No BackupDataInput");
                    } else if (!newDescriptor.saved()) {
                        throw new FileNotFoundException("No new state");
                    } else {
                        ensureNoDataIsPresent();
                        doRestore(data);
                        DataChecker.fixData(backupDescriptor.getLogger(),false);
                        success = true;
                    }
                    break;
                default:
                    throw new FileNotFoundException("Incompatible backup version "
                            + newDescriptor.getBackupSchemaVersion() + ", expected:" + MyBackupDescriptor.BACKUP_SCHEMA_VERSION);
            }
        } finally {
            MyLog.i(this, method + " ended, " + (success ? "success" : "failure"));
        }
    }

    private void ensureNoDataIsPresent() throws IOException {
        if (MyStorage.isApplicationDataCreated() == TriState.FALSE) {
            return;
        }
        MyContextHolder.initialize(this, this);
        if (!MyContextHolder.get().isReady()) {
            throw new FileNotFoundException("Application context is not initialized");
        } else if (!MyContextHolder.get().accounts().isEmpty()) {
            throw new FileNotFoundException("Cannot restore: AndStatus accounts are present. Please reinstall application before restore");
        }

        MyServiceManager.setServiceUnavailable();
        MyServiceManager.stopService();
        DatabaseHolder db = MyContextHolder.get().getMyDatabase();
        if (db != null) {
            db.close();
        }
    }
    
    private void doRestore(MyBackupDataInput data) throws IOException {
        restoreSharedPreferences(data);
        assertNextHeader(data, DATABASE_KEY + "_" + DatabaseHolder.DATABASE_NAME);
        databasesRestored += restoreFile(data, MyStorage.getDatabasePath(DatabaseHolder.DATABASE_NAME));
        MyContextHolder.release();
        MyContextHolder.setOnRestore(true);
        MyContextHolder.initialize(this, this);
        
        DataPruner.setDataPrunedNow();
        
        data.setMyContext(MyContextHolder.get());
        assertNextHeader(data, PersistentAccounts.KEY_ACCOUNT);
        accountsRestored += data.getMyContext().accounts().onRestore(data, backupDescriptor);

        MyContextHolder.release();
        MyContextHolder.setOnRestore(false);
        MyContextHolder.initialize(this, this);
    }

    private void restoreSharedPreferences(MyBackupDataInput data) throws IOException {
        MyLog.i(this, "On restoring Shared preferences");
        MyPreferencesGroupsEnum.setDefaultValues();
        assertNextHeader(data, SHARED_PREFERENCES_KEY);
        final String filename = "preferences";
        File tempFile = new File(SharedPreferencesUtil.prefsDirectory(MyContextHolder.get()
                .context()), filename + ".xml");
        sharedPreferencesRestored += restoreFile(data, tempFile);
        SharedPreferencesUtil.copyAll(SharedPreferencesUtil.getSharedPreferences(filename),
                SharedPreferencesUtil.getDefaultSharedPreferences());
        if (!tempFile.delete()) {
            MyLog.v(this, "Couldn't delete " + tempFile.getAbsolutePath());
        }
        fixExternalStorage();
        MyContextHolder.release();
        MyContextHolder.initialize(this, this);
    }
    
    private void fixExternalStorage() {
        if (!MyStorage.isStorageExternal() ||
                MyStorage.isWritableExternalStorageAvailable(null)) {
            return;
        }
        backupDescriptor.getLogger().logProgress("External storage is not available");
        SharedPreferencesUtil.putBoolean(MyPreferences.KEY_USE_EXTERNAL_STORAGE, false);
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
        if (dataFile.exists() && !dataFile.delete()) {
            throw new FileNotFoundException("Couldn't delete old file before restore '"
                    + dataFile.getName() + "'");
        }
        final String method = "restoreFile";
        MyLog.i(this, method + " started, " + fileWritten(data.getKey(), dataFile, data.getDataSize()));
        int bytesToWrite = data.getDataSize();
        int bytesWritten = 0;
        FileOutputStream output = new FileOutputStream(dataFile, false);
        try {
            while (bytesToWrite > bytesWritten) {
                byte[] bytes = new byte[MyBackupDataInput.fileChunkSize];
                int bytesRead = data.readEntityData(bytes, 0, bytes.length);
                if (bytesRead == 0) {
                    break;
                }
                output.write(bytes, 0, bytesRead);
                bytesWritten += bytesRead;
            }
            if (bytesWritten != bytesToWrite) {
                throw new FileNotFoundException("Couldn't restore " 
                        + filePartiallyWritten(data.getKey(), dataFile, bytesToWrite, bytesWritten));
            }
        } finally {
            output.close();
        }
        backupDescriptor.getLogger().logProgress("Restored "
                + filePartiallyWritten(data.getKey(), dataFile, bytesToWrite, bytesWritten));
        return 1;
    }

    MyBackupDescriptor getBackupDescriptor() {
        return backupDescriptor;
    }

    long getAccountsBackedUp() {
        return accountsBackedUp;
    }

    long getDatabasesBackedUp() {
        return databasesBackedUp;
    }

    long getSharedPreferencesBackedUp() {
        return sharedPreferencesBackedUp;
    }
}
