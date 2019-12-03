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
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.TryUtils;
import org.andstatus.app.util.UriUtils;

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
    private static final String DESCRIPTOR_FILE_NAME = "_descriptor.json";
    private DocumentFile dataFolder = null;
    private MyBackupDescriptor newDescriptor = MyBackupDescriptor.getEmpty();

    private MyBackupAgent backupAgent;
    private final Activity activity;
    private final ProgressLogger progressLogger;

    MyBackupManager(Activity activity, ProgressLogger.ProgressCallback progressCallback) {
        this.activity = activity;
        this.progressLogger = new ProgressLogger(progressCallback);
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
        progressLogger.logProgress("Data folder will be created inside:'"
                + backupFolder.getUri() + "'");
        if (backupFolder.exists() && dataFolderToDescriptorFile(backupFolder).isSuccess()) {
            throw new FileNotFoundException("Wrong folder, descriptor file exists here:'"
                    + dataFolderToDescriptorFile(backupFolder).get().getUri() + "'");
        }
        final String backupFileNamePrefix = MyLog.currentDateTimeFormatted() + "-AndStatusBackup";
        DocumentFile dataFolderToBe = backupFolder.createDirectory(backupFileNamePrefix);
        if (dataFolderToBe == null) {
            throw new IOException("Couldn't create subfolder '" + backupFileNamePrefix + "'" +
                    " inside '" + backupFolder.getUri() + "'");
        }
        if (dataFolderToBe.listFiles().length > 0) {
            throw new IOException("Data folder already exists:'" + dataFolderToBe.getUri() + "'");
        }
        dataFolder = dataFolderToBe;

        if (getExistingDescriptorFile().isSuccess()) {
            throw new IOException("Descriptor file already exists:'" +
                    getExistingDescriptorFile().get().getUri() + "'");
        }
        DocumentFile descriptorDocfile = dataFolder.createFile("", DESCRIPTOR_FILE_NAME);
        if (descriptorDocfile == null) {
            throw new IOException("Couldn't create descriptor file '" + DESCRIPTOR_FILE_NAME + "'" +
                    " inside '" + dataFolder.getUri() + "'");
        }
    }

    DocumentFile getDataFolder() {
        return dataFolder;
    }

    Try<DocumentFile> getExistingDescriptorFile() {
        return dataFolderToDescriptorFile(dataFolder);
    }

    static boolean isBackupFolder(DocumentFile dataFolder) {
        if (dataFolder != null && dataFolder.exists() && dataFolder.isDirectory() ) {
            return dataFolderToDescriptorFile(dataFolder).isSuccess();
        }
        return false;
    }

    private static Try<DocumentFile> dataFolderToDescriptorFile(DocumentFile dataFolder) {
        return TryUtils.ofNullableCallable(() -> dataFolder.findFile(DESCRIPTOR_FILE_NAME));
    }

    void backup() throws Throwable {
        progressLogger.logProgress("Starting backup to data folder:'" + dataFolder.getUri() + "'");
        backupAgent = new MyBackupAgent();
        Context context = MyContextHolder.get(activity).context();
        backupAgent.setContext(context);
        backupAgent.setActivity(activity);

        MyBackupDataOutput dataOutput = new MyBackupDataOutput(context, dataFolder);
        Try<MyBackupDescriptor> descriptorFile = getExistingDescriptorFile()
            .map( df -> {
            newDescriptor = MyBackupDescriptor.fromEmptyDocFileDescriptor(context, df, progressLogger);
            backupAgent.onBackup(MyBackupDescriptor.getEmpty(), dataOutput, newDescriptor);
            progressLogger.logSuccess();
            return newDescriptor;
        });
        if (descriptorFile.isFailure()) {
            throw descriptorFile.getCause();
        }
    }

    static void restoreInteractively(DocumentFile backupFile, Activity activity, ProgressLogger.ProgressCallback progressCallback) {
        MyBackupManager backupManager = new MyBackupManager(activity, progressCallback);
        try {
            backupManager.prepareForRestore(backupFile);
            backupManager.restore();
        } catch (Throwable e) {
            MyLog.ignored(backupManager, e);
            backupManager.progressLogger.logProgress(e.getMessage());
            backupManager.progressLogger.logFailure();
        }
    }

    void prepareForRestore(DocumentFile dataFolderOrFile) throws Throwable {
        if (dataFolderOrFile == null) {
            throw new FileNotFoundException("Data folder or file is not selected");
        }
        if (!dataFolderOrFile.exists()) {
            throw new FileNotFoundException("Data file doesn't exist:'" + dataFolderOrFile.getUri() + "'");
        }
        if (dataFolderOrFile.isDirectory()) {
            this.dataFolder = dataFolderOrFile;
        } else {
            this.dataFolder = dataFolderOrFile.getParentFile();
        }
        Try<DocumentFile> descriptorFile = getExistingDescriptorFile();
        if (descriptorFile.isFailure()) {
            throw new FileNotFoundException("Descriptor file doesn't exist: '" + descriptorFile.toString() + "'");
        }

        newDescriptor = descriptorFile.map(df -> {
            MyBackupDescriptor descriptor = MyBackupDescriptor.fromOldDocFileDescriptor(
                    MyContextHolder.get(activity).context(), df, progressLogger);
            if (descriptor.getBackupSchemaVersion() != MyBackupDescriptor.BACKUP_SCHEMA_VERSION) {
                throw new FileNotFoundException(
                        "Unsupported backup schema version: " + descriptor.getBackupSchemaVersion() +
                                "; created with " + descriptor.appVersionNameAndCode() +
                                "\nData folder:'" + dataFolder.getUri().getPath() + "'." +
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
        DocumentFile parentFolder = DocumentFile.fromTreeUri(context,
                UriUtils.fromString("content://com.android.externalstorage.documents/tree/primary%3Abackups%2FAndStatus"));
        return parentFolder == null
                ? DocumentFile.fromFile(Environment.getExternalStoragePublicDirectory(""))
                : parentFolder;
    }

    MyBackupAgent getBackupAgent() {
        return backupAgent;
    }
}