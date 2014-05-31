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
    static final String DESCRIPTOR_FILE_SUFFIX = "_descriptor.json";
    static final String DATA_FOLDER_SUFFIX = "_data";
    private File descriptorFile = null;
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
            backupManager.progressLogger.logProgress(e.getMessage());
            backupManager.progressLogger.logFailure();
        }
    }
    
    void prepareForBackup(File backupFolder) throws IOException {
        final String backupFileNamePrefix = MyLog.currentDateTimeFormatted() + "-AndStatusBackup";
        progressLogger.logProgress("Preparing for backup to folder:'" + backupFolder.getAbsolutePath() + "'; backup prefix:'" + backupFileNamePrefix + "'");
        
        File descriptorFileTobe = new File(backupFolder, backupFileNamePrefix + MyBackupManager.DESCRIPTOR_FILE_SUFFIX);
        if (descriptorFileTobe.exists()) {
            throw new FileNotFoundException("Descriptor file already exists:'" + descriptorFileTobe.getAbsolutePath() + "'");
        }
        descriptorFileTobe.createNewFile();
        if (!descriptorFileTobe.exists()) {
            throw new FileNotFoundException("Couldn't create the descriptor file:'" + descriptorFileTobe.getAbsolutePath() + "'");
        }
        this.descriptorFile = descriptorFileTobe;

        if (!getDataFolder().mkdir() || !getDataFolder().exists()) {
            throw new FileNotFoundException("Couldn't create the data folder:'" + getDataFolder().getAbsolutePath() + "'");
        }
    }

    File getDataFolder() {
        return descriptorFileToDataFolder(descriptorFile);
    }
    
    static File descriptorFileToDataFolder(File descriptorFile) {
        final String backupBaseFileName = descriptorFile.getName().substring(0, descriptorFile.getName().length() - DESCRIPTOR_FILE_SUFFIX.length());
        File dataFolder = new File(descriptorFile.getParent(), backupBaseFileName + DATA_FOLDER_SUFFIX);
        return dataFolder;
    }
    
    void backup() throws IOException {
        progressLogger.logProgress("Starting backup to:'" + descriptorFile.getAbsolutePath() + "'");
        backupAgent = new MyBackupAgent();
        MyBackupDataOutput dataOutput = new MyBackupDataOutput(getDataFolder());
        ParcelFileDescriptor newState = ParcelFileDescriptor.open(descriptorFile,
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
            backupManager.progressLogger.logProgress(e.getMessage());
            backupManager.progressLogger.logFailure();
        }
    }
    
    void prepareForRestore(File descriptorFileIn) throws IOException {
        if (descriptorFileIn == null) {
            throw new FileNotFoundException("Backup descriptor file is not selected");
        }
        if (!descriptorFileIn.exists()) {
            throw new FileNotFoundException("Descriptor file doesn't exist:'" + descriptorFileIn.getAbsolutePath() + "'");
        }
        this.descriptorFile = descriptorFileIn;

        progressLogger.logProgress("Preparing to restore from:'" + descriptorFile.getAbsolutePath() + "'");
        ParcelFileDescriptor newState = ParcelFileDescriptor.open(descriptorFile,
                ParcelFileDescriptor.MODE_READ_ONLY);
        try {
            newDescriptor = MyBackupDescriptor.fromOldParcelFileDescriptor(newState, progressLogger);
            if (newDescriptor.getBackupSchemaVersion() != MyBackupDescriptor.BACKUP_SCHEMA_VERSION) {
                throw new FileNotFoundException("Unsupported backup schema version: " + newDescriptor.getBackupSchemaVersion()
                        + "; created with app version code:" + newDescriptor.getApplicationVersionCode());
            }
        } finally {
            newState.close();
        }
        if ( !getDataFolder().exists()) {
            throw new FileNotFoundException("Data folder not found: '" + getDataFolder().getAbsolutePath() + "'");
        }
    }

    void restore() throws IOException {
        MyBackupDataInput dataInput = new MyBackupDataInput(getDataFolder());
        if (dataInput.listKeys().size() < 3) {
            throw new FileNotFoundException("Not enough keys in the backup: " + Arrays.toString(dataInput.listKeys().toArray()));
        }
        
        progressLogger.logProgress("Starting restore from:'" + descriptorFile.getAbsolutePath() 
                + "', created with app version code:" + newDescriptor.getApplicationVersionCode());
        backupAgent = new MyBackupAgent();
        backupAgent.onRestore(dataInput, newDescriptor.getApplicationVersionCode(), newDescriptor);
        progressLogger.logSuccess();
    }

    MyBackupDescriptor getNewDescriptor() {
        return newDescriptor;
    }
    
    static String newBackupFileNamePrefix() {
        return MyLog.currentDateTimeFormatted() + "-AndStatusBackup";
    }

    static File getDefaultBackupDirectory(Context context) {
        File directory = Environment.getExternalStorageDirectory();
        if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            directory = context.getFilesDir();
        }
        return new File(directory, "backups/AndStatus");
    }

    File getDescriptorFile() {
        return descriptorFile;
    }
    
    MyBackupAgent getBackupAgent() {
        return backupAgent;
    }
}
