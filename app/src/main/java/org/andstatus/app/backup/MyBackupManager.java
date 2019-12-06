/*
 * Copyright (C) 2019 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.app.Activity;
import android.app.backup.BackupAgent;
import android.content.Context;
import android.os.Environment;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.TryUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;

import io.vavr.control.Try;

import static java.util.function.UnaryOperator.identity;

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
    public static final String DESCRIPTOR_FILE_NAME = "_descriptor.json";
    private DocumentFile dataFolder = null;
    private MyBackupDescriptor newDescriptor = MyBackupDescriptor.getEmpty();

    private MyBackupAgent backupAgent;
    private final Activity activity;
    private final ProgressLogger progressLogger;

    MyBackupManager(Activity activity, ProgressLogger.ProgressCallback progressCallback) {
        this.activity = activity;
        this.progressLogger = new ProgressLogger(progressCallback, "MyBackupManager");
    }

    static void backupInteractively(DocumentFile backupFolder, Activity activity, ProgressLogger.ProgressCallback progressCallback) {
        MyBackupManager backupManager = new MyBackupManager(activity, progressCallback);
        try {
            backupManager.prepareForBackup(backupFolder);
            backupManager.backup();
        } catch (Throwable e) {
            backupManager.progressLogger.logProgress(e.getMessage());
            backupManager.progressLogger.logFailure();
            MyLog.w(backupManager, "Backup failed", e);
        }
    }
    
    void prepareForBackup(DocumentFile backupFolder) throws IOException {
        progressLogger.logProgress("Data folder will be created inside: '"
                + backupFolder.getUri() + "'");
        if (backupFolder.exists() && getExistingDescriptorFile(backupFolder).isSuccess()) {
            throw new FileNotFoundException("Wrong folder, backup descriptor file '" + DESCRIPTOR_FILE_NAME + "'" +
                    " already exists here: '" + backupFolder.getUri().getPath() + "'");
        }
        final String dataFolderName = MyLog.currentDateTimeFormatted() + "-AndStatusBackup-" +
                MyPreferences.getAppInstanceName();

        DocumentFile dataFolderToBe = backupFolder.createDirectory(dataFolderName);
        if (dataFolderToBe == null) {
            throw new IOException("Couldn't create subfolder '" + dataFolderName + "'" +
                    " inside '" + backupFolder.getUri() + "'");
        }
        if (dataFolderToBe.listFiles().length > 0) {
            throw new IOException("Data folder is not empty: '" + dataFolderToBe.getUri() + "'");
        }
        DocumentFile descriptorFile = dataFolderToBe.createFile("", DESCRIPTOR_FILE_NAME);
        if (descriptorFile == null) {
            throw new IOException("Couldn't create descriptor file '" + DESCRIPTOR_FILE_NAME + "'" +
                    " inside '" + dataFolderToBe.getUri() + "'");
        }
        dataFolder = dataFolderToBe;
    }

    DocumentFile getDataFolder() {
        return dataFolder;
    }

    static boolean isDataFolder(DocumentFile dataFolder) {
        if (dataFolder != null && dataFolder.exists() && dataFolder.isDirectory() ) {
            return getExistingDescriptorFile(dataFolder).isSuccess();
        }
        return false;
    }

    static Try<DocumentFile> getExistingDescriptorFile(DocumentFile dataFolder) {
        return TryUtils.ofNullableCallable(() -> dataFolder.findFile(DESCRIPTOR_FILE_NAME));
    }

    void backup() throws Throwable {
        progressLogger.logProgress("Starting backup to data folder:'" + dataFolder.getUri() + "'");
        backupAgent = new MyBackupAgent();
        Context context = MyContextHolder.get(activity).context();
        backupAgent.setContext(context);
        backupAgent.setActivity(activity);

        MyBackupDataOutput dataOutput = new MyBackupDataOutput(context, dataFolder);

        getExistingDescriptorFile(dataFolder)
        .map( df -> {
                newDescriptor = MyBackupDescriptor.fromEmptyDocumentFile(context, df, progressLogger);
                backupAgent.onBackup(MyBackupDescriptor.getEmpty(), dataOutput, newDescriptor);
                progressLogger.logSuccess();
                return true;
            })
        .get(); // Return Try instead of throwing
    }

    static void restoreInteractively(DocumentFile dataFolder, Activity activity, ProgressLogger.ProgressCallback progressCallback) {
        MyBackupManager backupManager = new MyBackupManager(activity, progressCallback);
        try {
            backupManager.prepareForRestore(dataFolder);
            backupManager.restore();

            DocumentFile backupFolder = dataFolder.getParentFile();
            if (backupFolder != null) {
                MyPreferences.setLastBackupUri(backupFolder.getUri());
            }
        } catch (Throwable e) {
            MyLog.ignored(backupManager, e);
            backupManager.progressLogger.logProgress(e.getMessage());
            backupManager.progressLogger.logFailure();
        }
    }

    void prepareForRestore(DocumentFile dataFolder) throws Throwable {
        if (dataFolder == null) {
            throw new FileNotFoundException("Data folder is not selected");
        }
        if (!dataFolder.exists()) {
            throw new FileNotFoundException("Data folder doesn't exist:'" + dataFolder.getUri() + "'");
        }
        Try<DocumentFile> descriptorFile = getExistingDescriptorFile(dataFolder);
        if (descriptorFile.isFailure()) {
            throw new FileNotFoundException("Descriptor file " + DESCRIPTOR_FILE_NAME +
                    " doesn't exist: '" + descriptorFile.getCause().getMessage() + "'");
        }

        this.dataFolder = dataFolder;
        newDescriptor = descriptorFile.map(df -> {
            MyBackupDescriptor descriptor = MyBackupDescriptor.fromOldDocFileDescriptor(
                    MyContextHolder.get(activity).context(), df, progressLogger);
            if (descriptor.getBackupSchemaVersion() != MyBackupDescriptor.BACKUP_SCHEMA_VERSION) {
                throw new FileNotFoundException(
                        "Unsupported backup schema version: " + descriptor.getBackupSchemaVersion() +
                                "; created with " + descriptor.appVersionNameAndCode() +
                                "\nData folder:'" + this.dataFolder.getUri().getPath() + "'." +
                                "\nPlease use older AndStatus version to restore this backup."
                );
            }
            return descriptor;
        }).getOrElseThrow(identity());
    }

    void restore() throws IOException {
        Context context = MyContextHolder.get(activity).context();
        MyBackupDataInput dataInput = new MyBackupDataInput(context, dataFolder);
        if (dataInput.listKeys().size() < 3) {
            throw new FileNotFoundException("Not enough keys in the backup: " + Arrays.toString(dataInput.listKeys().toArray()));
        }

        progressLogger.logProgress("Starting restoring from data folder:'" + dataFolder.getUri().getPath()
                + "', created with " + newDescriptor.appVersionNameAndCode());
        backupAgent = new MyBackupAgent();
        backupAgent.setContext(context);
        backupAgent.setActivity(activity);
        backupAgent.onRestore(dataInput, newDescriptor.getApplicationVersionCode(), newDescriptor);
        progressLogger.logSuccess();
    }

    @NonNull
    static DocumentFile getDefaultBackupFolder(Context context) {
        DocumentFile backupFolder = DocumentFile.fromTreeUri(context, MyPreferences.getLastBackupUri());
        return backupFolder == null || !backupFolder.exists()
                ? DocumentFile.fromFile(Environment.getExternalStoragePublicDirectory(""))
                : backupFolder;
    }

    MyBackupAgent getBackupAgent() {
        return backupAgent;
    }
}