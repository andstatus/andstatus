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
package org.andstatus.app.backup

import android.app.Activity
import android.app.backup.BackupAgent
import android.content.Context
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import io.vavr.control.Try
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.TryUtils
import java.io.FileNotFoundException
import java.io.IOException
import java.util.*
import java.util.function.UnaryOperator

/**
 * Creates backups in the local file system:
 * 1. Using stock Android [BackupAgent] interface
 * 2. Or using custom interface, launched from your application
 *
 * One backup consists of:
 * 1. Backup descriptor file
 * 2. Folder with:
 * For each backup "key": header file and data file
 * @author yvolk (Yuri Volkov), http://yurivolkov.com
 */
internal class MyBackupManager(private val activity: Activity?, progressListener: ProgressLogger.ProgressListener?) {
    private var dataFolder: DocumentFile? = null
    private var newDescriptor: MyBackupDescriptor = MyBackupDescriptor.getEmpty()
    private var backupAgent: MyBackupAgent? = null
    private val progressLogger: ProgressLogger = ProgressLogger(progressListener)

    @Throws(IOException::class)
    fun prepareForBackup(backupFolder: DocumentFile) {
        progressLogger.logProgress("Data folder will be created inside: '"
                + backupFolder.getUri() + "'")
        if (backupFolder.exists() && getExistingDescriptorFile(backupFolder).isSuccess()) {
            throw FileNotFoundException("Wrong folder, backup descriptor file '" + DESCRIPTOR_FILE_NAME + "'" +
                    " already exists here: '" + backupFolder.getUri().path + "'")
        }
        val appInstanceName = MyPreferences.getAppInstanceName()
        val dataFolderName = MyLog.currentDateTimeFormatted() + "-AndStatusBackup-" +
                (if (appInstanceName.isEmpty()) "" else "$appInstanceName-") +
                MyPreferences.getDeviceBrandModelString()
        val dataFolderToBe = backupFolder.createDirectory(dataFolderName)
                ?: throw IOException("Couldn't create subfolder '" + dataFolderName + "'" +
                        " inside '" + backupFolder.getUri() + "'")
        if (dataFolderToBe.listFiles().size > 0) {
            throw IOException("Data folder is not empty: '" + dataFolderToBe.uri + "'")
        }
        val descriptorFile = dataFolderToBe.createFile("", DESCRIPTOR_FILE_NAME)
                ?: throw IOException("Couldn't create descriptor file '" + DESCRIPTOR_FILE_NAME + "'" +
                        " inside '" + dataFolderToBe.uri + "'")
        dataFolder = dataFolderToBe
    }

    fun getDataFolder(): DocumentFile? {
        return dataFolder
    }

    fun backup() {
        progressLogger.logProgress("Starting backup to data folder:'" + dataFolder?.getUri() + "'")
        backupAgent = MyBackupAgent().also { agent ->
            agent.setActivity(activity)
            dataFolder?.let { folder ->
                val dataOutput = MyBackupDataOutput(agent, folder)
                getExistingDescriptorFile(folder)
                        .map { df: DocumentFile? ->
                            newDescriptor = MyBackupDescriptor.fromEmptyDocumentFile(agent, df, progressLogger)
                            agent.onBackup(MyBackupDescriptor.getEmpty(), dataOutput, newDescriptor)
                            progressLogger.logSuccess()
                            true
                        }
                        .get() // Return Try instead of throwing
            }
        }
    }

    @Throws(Throwable::class)
    fun prepareForRestore(dataFolder: DocumentFile?) {
        if (dataFolder == null) {
            throw FileNotFoundException("Data folder is not selected")
        }
        if (!dataFolder.exists()) {
            throw FileNotFoundException("Data folder doesn't exist:'" + dataFolder.uri + "'")
        }
        val descriptorFile = getExistingDescriptorFile(dataFolder)
        if (descriptorFile.isFailure()) {
            throw FileNotFoundException("Descriptor file " + DESCRIPTOR_FILE_NAME +
                    " doesn't exist: '" + descriptorFile.getCause().message + "'")
        }
        this.dataFolder = dataFolder
        newDescriptor = descriptorFile.map { df: DocumentFile? ->
            val descriptor: MyBackupDescriptor = MyBackupDescriptor.fromOldDocFileDescriptor(
                    MyContextHolder.myContextHolder.getNow().baseContext(), df, progressLogger)
            if (descriptor.getBackupSchemaVersion() != MyBackupDescriptor.BACKUP_SCHEMA_VERSION) {
                throw FileNotFoundException(
                        "Unsupported backup schema version: ${descriptor.getBackupSchemaVersion()}" +
                                "; created with ${descriptor.appVersionNameAndCode()}" +
                                "\nData folder:'${dataFolder.getUri().path}'." +
                                "\nPlease use older AndStatus version to restore this backup."
                )
            }
            descriptor
        }.getOrElseThrow(UnaryOperator.identity())
    }

    @Throws(IOException::class)
    fun restore() {
        backupAgent = MyBackupAgent().also { agent ->
            agent.setActivity(activity)
            dataFolder?.let { folder ->
                val dataInput = MyBackupDataInput(agent, folder)
                if (dataInput.listKeys().size < 3) {
                    throw FileNotFoundException("Not enough keys in the backup: " +
                            Arrays.toString(dataInput.listKeys().toTypedArray()))
                }
                progressLogger.logProgress("Starting restoring from data folder:'" + folder.getUri().path
                        + "', created with " + newDescriptor.appVersionNameAndCode())
                agent.onRestore(dataInput, newDescriptor.getApplicationVersionCode(), newDescriptor)
            }
        }
        progressLogger.logSuccess()
    }

    fun getBackupAgent(): MyBackupAgent? {
        return backupAgent
    }

    companion object {
        val DESCRIPTOR_FILE_NAME: String = "_descriptor.json"
        fun backupInteractively(backupFolder: DocumentFile, activity: Activity,
                                progressListener: ProgressLogger.ProgressListener?) {
            val backupManager = MyBackupManager(activity, progressListener)
            try {
                backupManager.prepareForBackup(backupFolder)
                backupManager.backup()
            } catch (e: Throwable) {
                backupManager.progressLogger.logProgress(e.message ?: "(some error)")
                backupManager.progressLogger.logFailure()
                MyLog.w(backupManager, "Backup failed", e)
            }
        }

        fun isDataFolder(dataFolder: DocumentFile?): Boolean {
            return if (dataFolder != null && dataFolder.exists() && dataFolder.isDirectory) {
                getExistingDescriptorFile(dataFolder).isSuccess()
            } else false
        }

        fun getExistingDescriptorFile(dataFolder: DocumentFile): Try<DocumentFile> {
            return TryUtils.ofNullableCallable { dataFolder.findFile(DESCRIPTOR_FILE_NAME) }
        }

        fun restoreInteractively(dataFolder: DocumentFile, activity: Activity, progressListener: ProgressLogger.ProgressListener?) {
            val backupManager = MyBackupManager(activity, progressListener)
            try {
                backupManager.prepareForRestore(dataFolder)
                backupManager.restore()
                val backupFolder = dataFolder.getParentFile()
                if (backupFolder != null) {
                    MyPreferences.setLastBackupUri(backupFolder.uri)
                }
            } catch (e: Throwable) {
                MyLog.ignored(backupManager, e)
                backupManager.progressLogger.logProgress(e.message ?: "(some error)")
                backupManager.progressLogger.logFailure()
            }
        }

        fun getDefaultBackupFolder(context: Context): DocumentFile {
            val backupFolder = DocumentFile.fromTreeUri(context, MyPreferences.getLastBackupUri())
            return if (backupFolder == null || !backupFolder.exists())
                DocumentFile.fromFile(Environment.getExternalStoragePublicDirectory("")) else backupFolder
        }
    }

}