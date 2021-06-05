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
package org.andstatus.app.backup

import android.app.Activity
import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.content.Context
import android.os.ParcelFileDescriptor
import android.text.format.Formatter
import org.andstatus.app.FirstActivity
import org.andstatus.app.R
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.MyContextState
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.context.MyStorage
import org.andstatus.app.data.DataPruner
import org.andstatus.app.data.DbUtils
import org.andstatus.app.database.DatabaseHolder
import org.andstatus.app.service.MyServiceManager
import org.andstatus.app.service.MyServiceState
import org.andstatus.app.util.FileUtils
import org.andstatus.app.util.FileUtils.newFileOutputStreamWithRetry
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.SharedPreferencesUtil
import org.andstatus.app.util.ZipUtils
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

class MyBackupAgent : BackupAgent() {
    private var activity: Activity? = null
    private var backupDescriptor: MyBackupDescriptor? = null
    private var previousKey: String? = ""
    private var accountsBackedUp: Long = 0
    var accountsRestored: Long = 0
    private var databasesBackedUp: Long = 0
    var databasesRestored: Long = 0
    private var sharedPreferencesBackedUp: Long = 0
    var sharedPreferencesRestored: Long = 0
    private var foldersBackedUp: Long = 0
    var foldersRestored: Long = 0

    fun setActivity(activity: Activity?) {
        this.activity = activity
         MyContextHolder.myContextHolder.initialize(activity)
        attachBaseContext( MyContextHolder.myContextHolder.getNow().baseContext)
    }

    override fun onCreate() {
         MyContextHolder.myContextHolder.initialize(this)
    }

    override fun onBackup(oldState: ParcelFileDescriptor?, data: BackupDataOutput?,
                          newState: ParcelFileDescriptor) {
        if ( MyContextHolder.myContextHolder.getNow().isTestRun) {
            val logmsg = "onBackup; skipped due to test run"
            MyLog.i(this, logmsg)
            throw IOException(logmsg)
        }
        if (!SharedPreferencesUtil.getBoolean(MyPreferences.KEY_ENABLE_ANDROID_BACKUP, false)) {
            val logmsg = "onBackup; skipped: disabled in Settings"
            MyLog.i(this, logmsg)
            throw IOException(logmsg)
        }
        onBackup(
                MyBackupDescriptor.fromOldParcelFileDescriptor(oldState, ProgressLogger.getEmpty("")),
                MyBackupDataOutput(getContext(), data),
                MyBackupDescriptor.fromEmptyParcelFileDescriptor(newState, ProgressLogger.getEmpty("")))
    }

    fun onBackup(oldDescriptor: MyBackupDescriptor?, data: MyBackupDataOutput?,
                 newDescriptor: MyBackupDescriptor?) {
        val method = "onBackup"
        // Ignore oldDescriptor for now...
        MyLog.i(this, method + " started" + (if (data != null) ", folder='" + data.getDataFolderName() + "'" else "") +
                ", " + if (oldDescriptor?.saved() == true) "oldState:" + oldDescriptor.toString() else "no old state")
         MyContextHolder.myContextHolder.initialize(this).getBlocking()
        backupDescriptor = newDescriptor
        try {
            if (data == null) {
                throw FileNotFoundException("No BackupDataOutput")
            } else if (! MyContextHolder.myContextHolder.getNow().isReady) {
                throw FileNotFoundException("Application context is not initialized")
            } else if ( MyContextHolder.myContextHolder.getNow().accounts.isEmpty) {
                throw FileNotFoundException("Nothing to backup - No accounts yet")
            } else {
                val isServiceAvailableStored = checkAndSetServiceUnavailable()
                doBackup(data)
                backupDescriptor?.save(getContext())
                MyLog.v(this) { method + "; newState: " + backupDescriptor.toString() }
                if (isServiceAvailableStored) {
                    MyServiceManager.setServiceAvailable()
                }
            }
        } finally {
            MyLog.i(this, method + " ended, " + if (backupDescriptor?.saved() == true) "success" else "failure")
        }
    }

    fun checkAndSetServiceUnavailable(): Boolean {
        val isServiceAvailableStored: Boolean = MyServiceManager.isServiceAvailable()
        MyServiceManager.setServiceUnavailable()
        MyServiceManager.stopService()
        var ind = 0
        while (true) {
            val serviceState = MyServiceManager.getServiceState()
            if (serviceState == MyServiceState.STOPPED) {
                break
            } else {
                MyLog.d(this,"Waiting for MyService stopped $ind currently $serviceState")
            }
            if (ind > 5) {
                throw IOException(getString(R.string.system_is_busy_try_later))
            }
            DbUtils.waitMs("checkAndSetServiceUnavailable", 5000)
            ind++
        }
        return isServiceAvailableStored
    }

    private fun doBackup(data: MyBackupDataOutput) {
         MyContextHolder.myContextHolder.release { "doBackup" }
        sharedPreferencesBackedUp = backupFile(data,
                SHARED_PREFERENCES_KEY,
                SharedPreferencesUtil.defaultSharedPreferencesPath(getContext()))
        if (MyPreferences.isBackupDownloads()) {
            MyStorage.getDataFilesDir(MyStorage.DIRECTORY_DOWNLOADS)?.let { folder ->
                foldersBackedUp += backupFolder(data, DOWNLOADS_KEY, folder)
            }
        }
        MyStorage.getDatabasePath(DatabaseHolder.DATABASE_NAME)?.let { folder ->
            databasesBackedUp = backupFile(data,
                    DATABASE_KEY + "_" + DatabaseHolder.DATABASE_NAME, folder)
        }
        if (MyPreferences.isBackupLogFiles()) {
            MyStorage.getDataFilesDir(MyStorage.DIRECTORY_LOGS)?.let { folder ->
                foldersBackedUp += backupFolder(data, LOG_FILES_KEY, folder)
            }
        }
        accountsBackedUp =  MyContextHolder.myContextHolder.getNow().accounts.onBackup(data, backupDescriptor!!)
    }

    private fun backupFolder(data: MyBackupDataOutput, key: String, sourceFolder: File): Long {
        return ZipUtils.zipFiles(sourceFolder, MyStorage.newTempFile("$key.zip"))
                .map { zipFile: File ->
                    backupFile(data, key, zipFile)
                    zipFile.delete()
                    1L
                }
                .onFailure { e: Throwable ->
                    MyLog.w(this, "Failed to backup folder " +
                            sourceFolder.getAbsolutePath() + ", " + e.message)
                }
                .getOrElse(0)
    }

    private fun backupFile(data: MyBackupDataOutput, key: String, dataFile: File): Long {
        var backedUpFilesCount: Long = 0
        if (dataFile.exists()) {
            val fileLength = dataFile.length()
            if (fileLength > Int.MAX_VALUE) {
                throw FileNotFoundException("File '"
                        + dataFile.getName() + "' is too large for backup: " + formatBytes(fileLength))
            }
            val bytesToWrite = fileLength.toInt()
            data.writeEntityHeader(key, bytesToWrite, MyBackupDataOutput.getDataFileExtension(dataFile))
            var bytesWritten = 0
            while (bytesWritten < bytesToWrite) {
                val bytes = FileUtils.getBytes(dataFile, bytesWritten, MyStorage.FILE_CHUNK_SIZE)
                if (bytes.size <= 0) {
                    break
                }
                bytesWritten += bytes.size
                data.writeEntityData(bytes, bytes.size)
            }
            if (bytesWritten != bytesToWrite) {
                throw FileNotFoundException("Couldn't backup "
                        + filePartiallyWritten(key, dataFile, bytesToWrite, bytesWritten))
            }
            backedUpFilesCount++
            backupDescriptor?.getLogger()?.logProgress(
                    "Backed up " + fileWritten(key, dataFile, bytesWritten))
        } else {
            MyLog.v(this) { "File doesn't exist key='" + key + "', path='" + dataFile.getAbsolutePath() }
        }
        return backedUpFilesCount
    }

    private fun formatBytes(fileLength: Long): String? {
        return Formatter.formatFileSize(baseContext, fileLength)
    }

    private fun fileWritten(key: String, dataFile: File, bytesWritten: Int): String {
        return filePartiallyWritten(key, dataFile, bytesWritten, bytesWritten)
    }

    private fun filePartiallyWritten(key: String, dataFile: File, bytesToWrite: Int, bytesWritten: Int): String {
        return if (bytesWritten == bytesToWrite) {
            ("file:'" + dataFile.getName()
                    + "', key:'" + key + "', size:" + formatBytes(bytesWritten.toLong()))
        } else {
            ("file:'" + dataFile.getName()
                    + "', key:'" + key + "', wrote "
                    + formatBytes(bytesWritten.toLong()) + " of " + formatBytes(bytesToWrite.toLong()))
        }
    }

    override fun onRestore(data: BackupDataInput?, appVersionCode: Int, newState: ParcelFileDescriptor?) {
        onRestore(MyBackupDataInput(getContext(), data), appVersionCode,
                MyBackupDescriptor.fromOldParcelFileDescriptor(newState, ProgressLogger.getEmpty("")))
    }

    fun onRestore(data: MyBackupDataInput?, appVersionCode: Int, newDescriptor: MyBackupDescriptor) {
        val method = "onRestore"
        backupDescriptor = newDescriptor
        MyLog.i(this, method + " started" +
                ", from app version code '" + appVersionCode + "'" +
                (if (data == null) "" else ", folder:'" + data.getDataFolderName() + "'") +
                ", " + if (newDescriptor.saved()) " newState:" + newDescriptor.toString() else "no new state")
        var success = false
        try {
            when (backupDescriptor?.getBackupSchemaVersion()) {
                MyBackupDescriptor.BACKUP_SCHEMA_VERSION_UNKNOWN -> throw FileNotFoundException("No backup information in the backup descriptor")
                MyBackupDescriptor.BACKUP_SCHEMA_VERSION -> if (data == null) {
                    throw FileNotFoundException("No BackupDataInput")
                } else if (!newDescriptor.saved()) {
                    throw FileNotFoundException("No new state")
                } else {
                    ensureNoDataIsPresent()
                    doRestore(data)
                    success = true
                }
                else -> throw FileNotFoundException("Incompatible backup version "
                        + newDescriptor.getBackupSchemaVersion() + ", expected:" + MyBackupDescriptor.BACKUP_SCHEMA_VERSION)
            }
        } finally {
            MyLog.i(this, method + " ended, " + if (success) "success" else "failure")
        }
    }

    private fun ensureNoDataIsPresent() {
        if (MyStorage.isApplicationDataCreated().isFalse) {
            return
        }
         MyContextHolder.myContextHolder.initialize(this).getBlocking()
        if (! MyContextHolder.myContextHolder.getNow().isReady) {
            throw FileNotFoundException("Application context is not initialized")
        } else if ( MyContextHolder.myContextHolder.getNow().accounts.nonEmpty) {
            throw FileNotFoundException("Cannot restore: AndStatus accounts are present. Please reinstall application before restore")
        }
        MyServiceManager.setServiceUnavailable()
        MyServiceManager.stopService()
         MyContextHolder.myContextHolder.release { "ensureNoDataIsPresent" }
    }

    private fun doRestore(data: MyBackupDataInput) {
        restoreSharedPreferences(data)
        if (optionalNextHeader(data, DOWNLOADS_KEY)) {
            MyStorage.getDataFilesDir(MyStorage.DIRECTORY_DOWNLOADS)?.let { folder ->
                foldersRestored += restoreFolder(data, folder)
            }
        }
        assertNextHeader(data, DATABASE_KEY + "_" + DatabaseHolder.DATABASE_NAME)
        MyStorage.getDatabasePath(DatabaseHolder.DATABASE_NAME)?.let { file ->
            databasesRestored += restoreFile(data, file)
        }
        MyContextHolder.myContextHolder.release { "doRestore, database restored" }
        MyContextHolder.myContextHolder
                .setOnRestore(true)
                .initialize(this).getBlocking()
        if ( MyContextHolder.myContextHolder.getNow().state == MyContextState.UPGRADING && activity != null) {
             MyContextHolder.myContextHolder.upgradeIfNeeded(activity)
        }
        if (optionalNextHeader(data, LOG_FILES_KEY)) {
            MyStorage.getDataFilesDir(MyStorage.DIRECTORY_LOGS)?.let { folder ->
                foldersRestored += restoreFolder(data, folder)
            }
        }
        DataPruner.setDataPrunedNow()
        data.setMyContext( MyContextHolder.myContextHolder.getNow())
        assertNextHeader(data, KEY_ACCOUNT)
        accountsRestored += data.getMyContext().accounts.onRestore(data, backupDescriptor!!)
         MyContextHolder.myContextHolder.release { "doRestore, accounts restored" }
        MyContextHolder.myContextHolder.setOnRestore(false)
         MyContextHolder.myContextHolder.initialize(this).getBlocking()
    }

    private fun restoreSharedPreferences(data: MyBackupDataInput) {
        MyLog.i(this, "On restoring Shared preferences")
        FirstActivity.setDefaultValues(getContext())
        assertNextHeader(data, SHARED_PREFERENCES_KEY)
        val filename = MyStorage.TEMP_FILENAME_PREFIX + "preferences"
        val tempFile = File(SharedPreferencesUtil.prefsDirectory(getContext()), "$filename.xml")
        sharedPreferencesRestored += restoreFile(data, tempFile)
        SharedPreferencesUtil.copyAll(SharedPreferencesUtil.getSharedPreferences(filename),
                SharedPreferencesUtil.getDefaultSharedPreferences())
        if (!tempFile.delete()) {
            MyLog.v(this) { "Couldn't delete " + tempFile.absolutePath }
        }
        fixExternalStorage()
        MyContextHolder.myContextHolder.release { "restoreSharedPreferences" }
        MyContextHolder.myContextHolder.initialize(this).getBlocking()
    }

    private fun getContext(): Context {
        return activity ?: this
    }

    private fun fixExternalStorage() {
        if (!MyStorage.isStorageExternal() ||
                MyStorage.isWritableExternalStorageAvailable(null)) {
            return
        }
        backupDescriptor?.getLogger()?.logProgress("External storage is not available")
        SharedPreferencesUtil.putBoolean(MyPreferences.KEY_USE_EXTERNAL_STORAGE, false)
    }

    private fun assertNextHeader(data: MyBackupDataInput, key: String) {
        if (key != previousKey && !data.readNextHeader()) {
            throw FileNotFoundException("Unexpected end of backup on key='$key'")
        }
        previousKey = data.getKey()
        if (key != data.getKey()) {
            throw FileNotFoundException("Expected key='" + key + "' but was found key='" + data.getKey() + "'")
        }
    }

    private fun optionalNextHeader(data: MyBackupDataInput, key: String): Boolean {
        if (data.readNextHeader()) {
            previousKey = data.getKey()
            return key == data.getKey()
        }
        return false
    }

    private fun restoreFolder(data: MyBackupDataInput, targetFolder: File): Long {
        val tempFile = MyStorage.newTempFile(data.getKey() + ".zip")
        restoreFile(data, tempFile)
        val result = ZipUtils.unzipFiles(tempFile, targetFolder)
        tempFile.delete()
        return result
                .onSuccess { s: String -> backupDescriptor!!.getLogger().logProgress(s) }
                .onFailure { e: Throwable -> backupDescriptor!!.getLogger().logProgress(e.message ?: "(some error)") }
                .map { s: String? -> 1L }.getOrElse(0L)
    }

    /** @return count of restores files
     */
    fun restoreFile(data: MyBackupDataInput, dataFile: File): Long {
        if (dataFile.exists() && !dataFile.delete()) {
            throw FileNotFoundException("Couldn't delete old file before restore '"
                    + dataFile.getName() + "'")
        }
        val method = "restoreFile"
        MyLog.i(this, method + " started, " + fileWritten(data.getKey(), dataFile, data.getDataSize()))
        val bytesToWrite = data.getDataSize()
        var bytesWritten = 0
        newFileOutputStreamWithRetry(dataFile)?.use { output ->
            while (bytesToWrite > bytesWritten) {
                val bytes = ByteArray(MyStorage.FILE_CHUNK_SIZE)
                val bytesRead = data.readEntityData(bytes, 0, bytes.size)
                if (bytesRead == 0) {
                    break
                }
                output.write(bytes, 0, bytesRead)
                bytesWritten += bytesRead
            }
            if (bytesWritten != bytesToWrite) {
                throw FileNotFoundException("Couldn't restore "
                        + filePartiallyWritten(data.getKey(), dataFile, bytesToWrite, bytesWritten))
            }
        }
        backupDescriptor!!.getLogger().logProgress("Restored "
                + filePartiallyWritten(data.getKey(), dataFile, bytesToWrite, bytesWritten))
        return 1
    }

    fun getBackupDescriptor(): MyBackupDescriptor? {
        return backupDescriptor
    }

    fun getAccountsBackedUp(): Long {
        return accountsBackedUp
    }

    fun getDatabasesBackedUp(): Long {
        return databasesBackedUp
    }

    fun getSharedPreferencesBackedUp(): Long {
        return sharedPreferencesBackedUp
    }

    fun getFoldersBackedUp(): Long {
        return foldersBackedUp
    }

    companion object {
        val SHARED_PREFERENCES_KEY: String = "shared_preferences"
        val DOWNLOADS_KEY: String = "downloads"
        val DATABASE_KEY: String = "database"
        val LOG_FILES_KEY: String = "logs"
        val KEY_ACCOUNT: String = "account"
    }
}
