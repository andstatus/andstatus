package org.andstatus.app.backup;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.Context;

import androidx.documentfile.provider.DocumentFile;
import androidx.test.rule.GrantPermissionRule;

import org.andstatus.app.account.AccountUtils;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.account.MyAccounts;
import org.andstatus.app.context.MyStorage;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.DocumentFileUtils;
import org.andstatus.app.util.FileUtils;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.TriState;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.vavr.control.Try;

import static org.andstatus.app.context.DemoData.demoData;
import static org.andstatus.app.context.MyContextHolder.myContextHolder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MyBackupAgentTest {

    @Rule
    public GrantPermissionRule mRuntimePermissionRule = GrantPermissionRule.grant(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE);

    @Before
    public void setUp() throws Exception {
        TestSuite.initializeWithData(this);
        ensureOneFileExistsInDownloads();
    }

    private static void ensureOneFileExistsInDownloads() throws IOException {
        File downloads = MyStorage.getDataFilesDir(MyStorage.DIRECTORY_DOWNLOADS);
        if (Arrays.stream(downloads.listFiles()).noneMatch(File::isFile)) {
            File dummyFile = new File(downloads, "dummy.txt");
            dummyFile.createNewFile();
        }
    }

    @Test
    public void testBackupRestore() throws Throwable {
        MyAccounts accountsBefore = MyAccounts.newEmpty(myContextHolder.getNow());
        accountsBefore.initialize();

        TestSuite.forget();
        TestSuite.initialize(this);
        demoData.assertConversations();
        
        assertEquals("Compare Persistent accounts with copy", myContextHolder.getNow().accounts(), accountsBefore);
        compareOneAccount(myContextHolder.getNow().accounts(), accountsBefore, demoData.gnusocialTestAccountName);
        
        DocumentFile outputFolder = DocumentFile.fromFile(myContextHolder.getNow().context().getCacheDir());
        DocumentFile dataFolder = testBackup(outputFolder);
        deleteApplicationData();
        testRestore(dataFolder);

        TestSuite.forget();
        TestSuite.initialize(this);

        assertEquals("Number of persistent accounts", accountsBefore.size(), myContextHolder.getNow().accounts().size());
        
        assertEquals("Persistent accounts", accountsBefore, myContextHolder.getNow().accounts());
        compareOneAccount(accountsBefore, myContextHolder.getNow().accounts(), demoData.gnusocialTestAccountName);
        demoData.assertConversations();
        TestSuite.initializeWithData(this);

        deleteBackup(dataFolder);
    }

    private void compareOneAccount(MyAccounts accountsExpected, MyAccounts accountsActual, String accountName) throws JSONException {
        MyAccount oldAccount = accountsExpected.fromAccountName(accountName);
        MyAccount newAccount = accountsActual.fromAccountName(accountName);
        String message = "Compare accounts " +
                oldAccount.toJson().toString(2) + " and " + newAccount.toJson().toString(2);
        assertEquals(message, oldAccount, newAccount);
        assertEquals(message, oldAccount.toJson().toString(2), newAccount.toJson().toString(2));
    }

    private DocumentFile testBackup(DocumentFile backupFolder) throws Throwable {
        MyBackupManager backupManager = new MyBackupManager(null, null);
        backupManager.prepareForBackup(backupFolder);
        DocumentFile dataFolder = backupManager.getDataFolder();

        assertTrue("Data folder created: '" + dataFolder + "'", dataFolder.exists());
        Try<DocumentFile> existingDescriptorFile = MyBackupManager.getExistingDescriptorFile(dataFolder);
        assertTrue("Descriptor file created: " + existingDescriptorFile.map(DocumentFile::getUri),
                existingDescriptorFile.map(DocumentFile::exists).getOrElse(false));
        backupManager.backup();

        assertEquals("Shared preferences backed up", 1, backupManager.getBackupAgent().getSharedPreferencesBackedUp());
        assertEquals("Media files and logs backed up", 2, backupManager.getBackupAgent().getFoldersBackedUp());
        assertEquals("Databases backed up", 1, backupManager.getBackupAgent().getDatabasesBackedUp());
        assertEquals("Accounts backed up", backupManager.getBackupAgent().getAccountsBackedUp(), myContextHolder.getNow()
                .accounts().size());

        Try<DocumentFile> descriptorFile2 = MyBackupManager.getExistingDescriptorFile(dataFolder);
        JSONObject jso = DocumentFileUtils.getJSONObject(myContextHolder.getNow().context(), descriptorFile2.get());
        assertEquals(MyBackupDescriptor.BACKUP_SCHEMA_VERSION, jso.getInt(MyBackupDescriptor.KEY_BACKUP_SCHEMA_VERSION));
        assertTrue(jso.getLong(MyBackupDescriptor.KEY_CREATED_DATE) > System.currentTimeMillis() - 1000000);

        MyBackupDescriptor backupDescriptor = backupManager.getBackupAgent().getBackupDescriptor();
        assertEquals(MyBackupDescriptor.BACKUP_SCHEMA_VERSION, backupDescriptor.getBackupSchemaVersion());
        
        DocumentFile accountHeader = dataFolder.createFile("", "account_header.json");
        assertTrue(accountHeader.exists());
        jso = DocumentFileUtils.getJSONObject(myContextHolder.getNow().context(), accountHeader);
        assertTrue(jso.getInt(MyBackupDataOutput.KEY_DATA_SIZE) > 10);
        assertEquals(".json", jso.getString(MyBackupDataOutput.KEY_FILE_EXTENSION));

        DocumentFile accountData = dataFolder.createFile("", "account_data.json");
        assertTrue(accountData.exists());
        JSONArray jsa = DocumentFileUtils.getJSONArray(myContextHolder.getNow().context(), accountData);
        assertTrue(jsa.length() > 2);
        
        return dataFolder;
    }

    private void deleteApplicationData() throws IOException {
        MyServiceManager.setServiceUnavailable();
        deleteAccounts();
        Context context = myContextHolder.getNow().context();
        myContextHolder.release(() -> "deleteApplicationData");
        deleteFiles(context, false);
        deleteFiles(context, true);
        SharedPreferencesUtil.resetHasSetDefaultValues();
        assertEquals(TriState.FALSE, MyStorage.isApplicationDataCreated());
        TestSuite.onDataDeleted();
    }

    private void deleteAccounts() throws IOException {
        android.accounts.AccountManager am = AccountManager.get(myContextHolder.getNow().context());
        List<Account> aa = AccountUtils.getCurrentAccounts(myContextHolder.getNow().context());
        for (android.accounts.Account androidAccount : aa) {
            String logMsg = "Removing old account: " + androidAccount.name;
            MyLog.i(this, logMsg);
            AccountManagerFuture<Boolean> amf = am.removeAccount(androidAccount, null, null);
            try {
                amf.getResult(10, TimeUnit.SECONDS);
            } catch (OperationCanceledException | AuthenticatorException e) {
                MyLog.e(this, logMsg, e);
                throw new FileNotFoundException(logMsg + ", " + e.getMessage());
            }
        }
    }

    private void deleteFiles(Context context, boolean useExternalStorage) {
        FileUtils.deleteFilesRecursively(MyStorage.getDataFilesDir(MyStorage.DIRECTORY_DOWNLOADS, TriState.fromBoolean(useExternalStorage)));
        FileUtils.deleteFilesRecursively(MyStorage.getDataFilesDir(MyStorage.DIRECTORY_DATABASES, TriState.fromBoolean(useExternalStorage)));
        FileUtils.deleteFilesRecursively(SharedPreferencesUtil.prefsDirectory(context));
    }

    private void testRestore(DocumentFile dataFolder) throws Throwable {
        
        MyBackupManager backupManager = new MyBackupManager(null, null);
        backupManager.prepareForRestore(dataFolder);
        assertTrue("Data folder exists: '" + backupManager.getDataFolder().getUri() + "'", backupManager.getDataFolder().exists());

        backupManager.restore();
        assertEquals("Shared preferences restored", 1, backupManager.getBackupAgent().sharedPreferencesRestored);
        assertEquals("Downloads and logs restored", 2, backupManager.getBackupAgent().foldersRestored);
        assertEquals("Databases restored", 1, backupManager.getBackupAgent().databasesRestored);
    }

    private void deleteBackup(DocumentFile dataFolder) {
        for (DocumentFile dataFile : dataFolder.listFiles()) {
            if (!dataFile.delete()) {
                MyLog.e(this, "Couldn't delete file " + dataFile.getUri());
            }
        }
        if (!dataFolder.delete()) {
            MyLog.e(this, "Couldn't delete folder " + dataFolder.getUri());
        }
    }
}
