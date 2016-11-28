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
import android.content.Context;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.support.annotation.NonNull;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.util.MyLog;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;

/**
 * Creates backups in the local file system:
 * 1. Using stock Android {@link BackupAgent} interface 
 * 2. Or using custom interface, launched from your application
 * 
 * One backup consists of:
 * 1. Backup descriptor file
 * 2. Folder with: 
 *      For each backup "key": header file and data file  
 * @author yvolk (Yuri Volkov), http://yurivolkov.com
 */
class MyBackupManager {
    static final String DESCRIPTOR_FILE_NAME = "_descriptor.json";
    private File dataFolder = null;
    private MyBackupDescriptor newDescriptor = MyBackupDescriptor.getEmpty();    

    private MyBackupAgent backupAgent;
    private final ProgressLogger progressLogger;

    MyBackupManager(ProgressLogger.ProgressCallback progressCallback) {
        this.progressLogger = new ProgressLogger(progressCallback);
    }

    static void backupInteractively(File backupFolder, ProgressLogger.ProgressCallback progressCallback) {
        MyBackupManager backupManager = new MyBackupManager(progressCallback);
        try {
            backupManager.prepareForBackup(backupFolder);
            backupManager.backup();
        } catch (IOException e) {
            MyLog.ignored(backupManager, e);
            backupManager.progressLogger.logProgress(e.getMessage());
            backupManager.progressLogger.logFailure();
        }
    }
    
    void prepareForBackup(File backupFolder) throws IOException {
        progressLogger.logProgress("Data folder will be created inside:'"
                + backupFolder.getAbsolutePath() + "'");
        if (backupFolder.exists() && dataFolderToDescriptorFile(backupFolder).exists()) {
            throw new FileNotFoundException("Wrong folder, descriptor file already exists:'"
                    + dataFolderToDescriptorFile(backupFolder).getAbsolutePath() + "'");
        }
        final String backupFileNamePrefix = MyLog.currentDateTimeFormatted() + "-AndStatusBackup";
        File dataFolderToBe = new File(backupFolder, backupFileNamePrefix);
        if (dataFolderToBe.exists()) {
            throw new FileNotFoundException("Data folder already exists:'"
                    + dataFolderToBe.getAbsolutePath() + "'");
        }
        dataFolder = dataFolderToBe;
        if (!dataFolder.mkdir() || !dataFolder.exists()) {
            throw new FileNotFoundException("Couldn't create the data folder:'"
                    + dataFolder.getAbsolutePath() + "'");
        }
        if (!getDescriptorFile().createNewFile()) {
            throw new FileNotFoundException("Descriptor file already exists:'"
                    + getDescriptorFile().getAbsolutePath() + "'");
        }
    }

    File getDataFolder() {
        return dataFolder;
    }

    File getDescriptorFile() {
        return dataFolderToDescriptorFile(dataFolder);
    }

    public static boolean isBackupFolder(File dataFolder) {
        if (dataFolder != null && dataFolder.exists() && dataFolder.isDirectory() ) {
            return dataFolderToDescriptorFile(dataFolder).exists();
        }
        return false;
    }

    @NonNull
    static File dataFolderToDescriptorFile(File dataFolder) {
        return new File(dataFolder, DESCRIPTOR_FILE_NAME);
    }
    
    void backup() throws IOException {
        progressLogger.logProgress("Starting backup to data folder:'" + dataFolder.getAbsolutePath() + "'");
        backupAgent = new MyBackupAgent();
        backupAgent.setContext(MyContextHolder.get().context());
        
        MyBackupDataOutput dataOutput = new MyBackupDataOutput(dataFolder);
        ParcelFileDescriptor newState = ParcelFileDescriptor.open(getDescriptorFile(),
                ParcelFileDescriptor.MODE_READ_WRITE);
        try {
            newDescriptor = MyBackupDescriptor.fromEmptyParcelFileDescriptor(newState, progressLogger);
            backupAgent.onBackup(MyBackupDescriptor.getEmpty(), dataOutput, newDescriptor);
            progressLogger.logSuccess();
        } finally {
            newState.close();
        }
    }

    static void restoreInteractively(File backupFile, ProgressLogger.ProgressCallback progressCallback) {
        MyBackupManager backupManager = new MyBackupManager(progressCallback);
        try {
            backupManager.prepareForRestore(backupFile);
            backupManager.restore();
        } catch (IOException e) {
            MyLog.ignored(backupManager, e);
            backupManager.progressLogger.logProgress(e.getMessage());
            backupManager.progressLogger.logFailure();
        }
    }
    
    void prepareForRestore(File dataFolderOrFile) throws IOException {
        if (dataFolderOrFile == null) {
            throw new FileNotFoundException("Data folder or file is not selected");
        }
        if (!dataFolderOrFile.exists()) {
            throw new FileNotFoundException("Data file doesn't exist:'" + dataFolderOrFile.getAbsolutePath() + "'");
        }
        if (dataFolderOrFile.isDirectory()) {
            this.dataFolder = dataFolderOrFile;
        } else {
            this.dataFolder = dataFolderOrFile.getParentFile();
        }
        if (!getDescriptorFile().exists()) {
            throw new FileNotFoundException("Descriptor file doesn't exist:'" + getDescriptorFile().getAbsolutePath() + "'");
        }

        ParcelFileDescriptor newState = ParcelFileDescriptor.open(getDescriptorFile(),
                ParcelFileDescriptor.MODE_READ_ONLY);
        try {
            newDescriptor = MyBackupDescriptor.fromOldParcelFileDescriptor(newState, progressLogger);
            if (newDescriptor.getBackupSchemaVersion() != MyBackupDescriptor.BACKUP_SCHEMA_VERSION) {
                throw new FileNotFoundException("Unsupported backup schema version: " + newDescriptor.getBackupSchemaVersion()
                        + "; created with app version code:" + newDescriptor.getApplicationVersionCode()
                        + "; data folder:'" + dataFolder.getAbsolutePath() + "'");
            }
        } finally {
            newState.close();
        }
    }

    void restore() throws IOException {
        MyBackupDataInput dataInput = new MyBackupDataInput(dataFolder);
        if (dataInput.listKeys().size() < 3) {
            throw new FileNotFoundException("Not enough keys in the backup: " + Arrays.toString(dataInput.listKeys().toArray()));
        }
        
        progressLogger.logProgress("Starting restore from data folder:'" + dataFolder.getAbsolutePath() 
                + "', created with app version code:" + newDescriptor.getApplicationVersionCode());
        backupAgent = new MyBackupAgent();
        backupAgent.setContext(MyContextHolder.get().context());
        backupAgent.onRestore(dataInput, newDescriptor.getApplicationVersionCode(), newDescriptor);
        progressLogger.logSuccess();
    }

    MyBackupDescriptor getNewDescriptor() {
        return newDescriptor;
    }
    
    static String newBackupFilenamePrefix() {
        return MyLog.currentDateTimeFormatted() + "-AndStatusBackup";
    }

    @NonNull
    static File getDefaultBackupDirectory(Context context) {
        File directory = Environment.getExternalStorageDirectory();
        if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            directory = context.getFilesDir();
        }
        return new File(directory, "backups/AndStatus");
    }
    
    MyBackupAgent getBackupAgent() {
        return backupAgent;
    }
}
