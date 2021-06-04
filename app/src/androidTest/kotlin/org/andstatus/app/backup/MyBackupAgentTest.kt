package org.andstatus.app.backup

import android.accounts.AccountManager
import android.accounts.AuthenticatorException
import android.accounts.OperationCanceledException
import android.content.Context
import androidx.documentfile.provider.DocumentFile
import io.vavr.control.Try
import org.andstatus.app.account.AccountUtils
import org.andstatus.app.account.MyAccounts
import org.andstatus.app.context.DemoData
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.MyStorage
import org.andstatus.app.context.TestSuite
import org.andstatus.app.service.MyServiceManager
import org.andstatus.app.util.DocumentFileUtils
import org.andstatus.app.util.FileUtils
import org.andstatus.app.util.IgnoredInTravis2
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.SharedPreferencesUtil
import org.andstatus.app.util.TriState
import org.json.JSONException
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit

class MyBackupAgentTest: IgnoredInTravis2() {

    @Before
    @Throws(Exception::class)
    fun setUp() {
        TestSuite.initializeWithData(this)
        ensureOneFileExistsInDownloads()
    }

    @Test
    @Throws(Throwable::class)
    fun testBackupRestore() {
        val accountsBefore: MyAccounts = MyAccounts.Companion.newEmpty( MyContextHolder.myContextHolder.getNow())
        accountsBefore.initialize()
        TestSuite.forget()
        TestSuite.initialize(this)
        DemoData.demoData.assertConversations()
        Assert.assertEquals("Compare Persistent accounts with copy",  MyContextHolder.myContextHolder.getNow().accounts, accountsBefore)
        compareOneAccount( MyContextHolder.myContextHolder.getNow().accounts, accountsBefore, DemoData.demoData.gnusocialTestAccountName)
        val outputFolder = DocumentFile.fromFile( MyContextHolder.myContextHolder.getNow().context.getCacheDir())
        val dataFolder = testBackup(outputFolder)
        deleteApplicationData()
        testRestore(dataFolder)
        TestSuite.forget()
        TestSuite.initialize(this)
        Assert.assertEquals("Number of persistent accounts", accountsBefore.size().toLong(),  MyContextHolder.myContextHolder.getNow().accounts.size().toLong())
        Assert.assertEquals("Persistent accounts", accountsBefore,  MyContextHolder.myContextHolder.getNow().accounts)
        compareOneAccount(accountsBefore,  MyContextHolder.myContextHolder.getNow().accounts, DemoData.demoData.gnusocialTestAccountName)
        DemoData.demoData.assertConversations()
        TestSuite.initializeWithData(this)
        deleteBackup(dataFolder)
    }

    @Throws(JSONException::class)
    private fun compareOneAccount(accountsExpected: MyAccounts, accountsActual: MyAccounts, accountName: String) {
        val oldAccount = accountsExpected.fromAccountName(accountName)
        val newAccount = accountsActual.fromAccountName(accountName)
        val message = "Compare accounts " +
                oldAccount.toJson().toString(2) + " and " + newAccount.toJson().toString(2)
        Assert.assertEquals(message, oldAccount, newAccount)
        Assert.assertEquals(message, oldAccount.toJson().toString(2), newAccount.toJson().toString(2))
    }

    @Throws(Throwable::class)
    private fun testBackup(backupFolder: DocumentFile): DocumentFile {
        MyLog.i(this,"testBackup started")
        val backupManager = MyBackupManager(null, null)
        backupManager.prepareForBackup(backupFolder)
        val dataFolder = backupManager.getDataFolder() ?: throw IllegalStateException("No dataFolder")
        Assert.assertTrue("Data folder created: '$dataFolder'", dataFolder.exists())
        val existingDescriptorFile: Try<DocumentFile> = MyBackupManager.Companion.getExistingDescriptorFile(dataFolder)
        Assert.assertTrue("Descriptor file created: " + existingDescriptorFile.map { obj: DocumentFile -> obj.getUri() },
                existingDescriptorFile.map { obj: DocumentFile -> obj.exists() }.getOrElse(false))
        backupManager.backup()
        Assert.assertEquals("Shared preferences backed up", 1L, backupManager.getBackupAgent()?.getSharedPreferencesBackedUp())
        Assert.assertEquals("Media files and logs backed up", 2L, backupManager.getBackupAgent()?.getFoldersBackedUp())
        Assert.assertEquals("Databases backed up", 1L, backupManager.getBackupAgent()?.getDatabasesBackedUp())
        Assert.assertEquals("Accounts backed up", MyContextHolder.myContextHolder.getNow().accounts.size().toLong(),
                backupManager.getBackupAgent()?.getAccountsBackedUp())
        val descriptorFile2: Try<DocumentFile> = MyBackupManager.Companion.getExistingDescriptorFile(dataFolder)
        var jso = DocumentFileUtils.getJSONObject( MyContextHolder.myContextHolder.getNow().context, descriptorFile2.get())
        Assert.assertEquals(MyBackupDescriptor.Companion.BACKUP_SCHEMA_VERSION.toLong(), jso.getInt(MyBackupDescriptor.Companion.KEY_BACKUP_SCHEMA_VERSION).toLong())
        Assert.assertTrue(jso.getLong(MyBackupDescriptor.Companion.KEY_CREATED_DATE) > System.currentTimeMillis() - 1000000)
        val backupDescriptor = backupManager.getBackupAgent()?.getBackupDescriptor() ?: throw IllegalStateException("No backup descriptor")
        Assert.assertEquals(MyBackupDescriptor.Companion.BACKUP_SCHEMA_VERSION.toLong(), backupDescriptor.getBackupSchemaVersion().toLong())
        val accountHeader = dataFolder.createFile("", "account_header.json") ?: throw IllegalStateException("No accountHeader file")
        Assert.assertTrue(accountHeader.exists())
        jso = DocumentFileUtils.getJSONObject( MyContextHolder.myContextHolder.getNow().context, accountHeader)
        Assert.assertTrue(jso.getInt(MyBackupDataOutput.Companion.KEY_DATA_SIZE) > 10)
        Assert.assertEquals(".json", jso.getString(MyBackupDataOutput.Companion.KEY_FILE_EXTENSION))
        val accountData = dataFolder.createFile("", "account_data.json") ?: throw IllegalStateException("No accountData")
        Assert.assertTrue(accountData.exists())
        val jsa = DocumentFileUtils.getJSONArray( MyContextHolder.myContextHolder.getNow().context, accountData)
        Assert.assertTrue(jsa.length() > 2)
        return dataFolder
    }

    @Throws(IOException::class)
    private fun deleteApplicationData() {
        MyServiceManager.Companion.setServiceUnavailable()
        deleteAccounts()
        val context: Context =  MyContextHolder.myContextHolder.getNow().context
         MyContextHolder.myContextHolder.release { "deleteApplicationData" }
        deleteFiles(context, false)
        deleteFiles(context, true)
        SharedPreferencesUtil.resetHasSetDefaultValues()
        Assert.assertEquals(TriState.FALSE, MyStorage.isApplicationDataCreated())
        TestSuite.onDataDeleted()
    }

    @Throws(IOException::class)
    private fun deleteAccounts() {
        val am = AccountManager.get( MyContextHolder.myContextHolder.getNow().context)
        val aa = AccountUtils.getCurrentAccounts( MyContextHolder.myContextHolder.getNow().context)
        for (androidAccount in aa) {
            val logMsg = "Removing old account: " + androidAccount.name
            MyLog.i(this, logMsg)
            val amf = am.removeAccount(androidAccount, null, null)
            try {
                amf.getResult(10, TimeUnit.SECONDS)
            } catch (e: OperationCanceledException) {
                MyLog.e(this, logMsg, e)
                throw FileNotFoundException(logMsg + ", " + e.message)
            } catch (e: AuthenticatorException) {
                MyLog.e(this, logMsg, e)
                throw FileNotFoundException(logMsg + ", " + e.message)
            }
        }
    }

    private fun deleteFiles(context: Context, useExternalStorage: Boolean) {
        FileUtils.deleteFilesRecursively(MyStorage.getDataFilesDir(MyStorage.DIRECTORY_DOWNLOADS, TriState.Companion.fromBoolean(useExternalStorage)))
        FileUtils.deleteFilesRecursively(MyStorage.getDataFilesDir(MyStorage.DIRECTORY_DATABASES, TriState.Companion.fromBoolean(useExternalStorage)))
        FileUtils.deleteFilesRecursively(SharedPreferencesUtil.prefsDirectory(context))
    }

    @Throws(Throwable::class)
    private fun testRestore(dataFolder: DocumentFile?) {
        val backupManager = MyBackupManager(null, null)
        backupManager.prepareForRestore(dataFolder)
        Assert.assertTrue("Data folder exists: '" + backupManager.getDataFolder()?.uri + "'", backupManager.getDataFolder()?.exists() == true)
        backupManager.restore()
        Assert.assertEquals("Shared preferences restored", 1L, backupManager.getBackupAgent()?.sharedPreferencesRestored)
        Assert.assertEquals("Downloads and logs restored", 2L, backupManager.getBackupAgent()?.foldersRestored)
        Assert.assertEquals("Databases restored", 1L, backupManager.getBackupAgent()?.databasesRestored)
    }

    private fun deleteBackup(dataFolder: DocumentFile) {
        for (dataFile in dataFolder.listFiles()) {
            if (!dataFile.delete()) {
                MyLog.e(this, "Couldn't delete file " + dataFile.uri)
            }
        }
        if (!dataFolder.delete()) {
            MyLog.e(this, "Couldn't delete folder " + dataFolder.getUri())
        }
    }

    companion object {
        @Throws(IOException::class)
        private fun ensureOneFileExistsInDownloads() {
            val downloads = MyStorage.getDataFilesDir(MyStorage.DIRECTORY_DOWNLOADS) ?: throw IllegalStateException("No downloads")
            if (Arrays.stream(downloads.listFiles()).noneMatch { obj: File -> obj.isFile() }) {
                val dummyFile = File(downloads, "dummy.txt")
                dummyFile.createNewFile()
            }
        }
    }
}
