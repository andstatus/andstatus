package org.andstatus.app.backup;

import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.test.InstrumentationTestCase;

import org.andstatus.app.account.AuthenticatorService;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.account.PersistentAccounts;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.net.ConnectionException;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.FileUtils;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class MyBackupAgentTest extends InstrumentationTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestSuite.initializeWithData(this);
    }

    public void testBackupRestore() throws IOException, JSONException, NameNotFoundException, ConnectionException, InterruptedException {
        TestSuite.forget();
        TestSuite.initialize(this);
        
        PersistentAccounts accountsBefore = PersistentAccounts.getEmpty();
        accountsBefore.initialize();
        assertEquals("Compare Persistent accounts with copy", MyContextHolder.get().persistentAccounts(), accountsBefore);
        compareOneAccount(MyContextHolder.get().persistentAccounts(), accountsBefore, TestSuite.GNUSOCIAL_TEST_ACCOUNT_NAME);
        
        File outputFolder = MyContextHolder.get().context().getCacheDir();
        File dataFolder = testBackup(outputFolder);
        deleteApplicationData();
        testRestore(dataFolder);

        TestSuite.forget();
        TestSuite.initialize(this);

        assertEquals("Number of persistent accounts", accountsBefore.size(), MyContextHolder.get().persistentAccounts().size());
        
        assertEquals("Persistent accounts", accountsBefore, MyContextHolder.get().persistentAccounts());
        compareOneAccount(accountsBefore, MyContextHolder.get().persistentAccounts(), TestSuite.GNUSOCIAL_TEST_ACCOUNT_NAME);

        deleteBackup(dataFolder);
    }

    private void compareOneAccount(PersistentAccounts accountsExpected, PersistentAccounts accountsActual, String accountName) throws JSONException {
        MyAccount oldAccount = accountsExpected.fromAccountName(accountName);
        MyAccount newAccount = accountsActual.fromAccountName(accountName);
        String message = "Compare account, hash codes: " + oldAccount.hashCode() + " and " + newAccount.hashCode() +
                oldAccount.toJson().toString(2) + " and " + newAccount.toJson().toString(2);
        assertEquals(message, oldAccount, newAccount);
    }

    private File testBackup(File backupFolder) throws IOException, JSONException {
        MyBackupManager backupManager = new MyBackupManager(null);
        backupManager.prepareForBackup(backupFolder);
        assertTrue("Data folder created: '" + backupManager.getDataFolder() + "'",
                backupManager.getDataFolder().exists());
        assertTrue("Descriptor file created: " + backupManager.getDescriptorFile().getAbsolutePath(), backupManager.getDescriptorFile().exists());
        backupManager.backup();

        assertEquals("Shared preferences backed up", 1, backupManager.getBackupAgent().sharedPreferencesBackedUp);
        assertEquals("Databases backed up", 1, backupManager.getBackupAgent().databasesBackedUp);
        assertEquals("Accounts backed up", backupManager.getBackupAgent().accountsBackedUp, MyContextHolder.get()
                .persistentAccounts().size());
        
        assertTrue("Descriptor file was filled: " + backupManager.getDescriptorFile().getAbsolutePath(), backupManager.getDescriptorFile().length() > 10);
        JSONObject jso = FileUtils.getJSONObject(backupManager.getDescriptorFile());
        assertEquals(MyBackupDescriptor.BACKUP_SCHEMA_VERSION, jso.getInt(MyBackupDescriptor.KEY_BACKUP_SCHEMA_VERSION));
        assertTrue(jso.getLong(MyBackupDescriptor.KEY_CREATED_DATE) > System.currentTimeMillis() - 1000000);

        MyBackupDescriptor backupDescriptor = backupManager.getBackupAgent().getBackupDescriptor();
        assertEquals(MyBackupDescriptor.BACKUP_SCHEMA_VERSION, backupDescriptor.getBackupSchemaVersion());
        
        File accountHeader = new File(backupManager.getDataFolder(), "account_header.json");
        assertTrue(accountHeader.exists());
        jso = FileUtils.getJSONObject(accountHeader);
        assertTrue(jso.getInt(MyBackupDataOutput.KEY_DATA_SIZE) > 10);
        assertEquals(jso.getString(MyBackupDataOutput.KEY_FILE_EXTENSION), ".json");

        File accountData = new File(backupManager.getDataFolder(), "account_data.json");
        assertTrue(accountData.exists());
        JSONArray jsa = FileUtils.getJSONArray(accountData);
        assertTrue(jsa.length() > 2);
        
        return backupManager.getDataFolder();
    }

    private void deleteApplicationData() throws IOException {
        MyServiceManager.setServiceUnavailable();
        deleteAccounts();
        Context context = MyContextHolder.get().context();
        MyContextHolder.get().getDatabase().close();
        MyContextHolder.release();
        deleteFiles(context, false);
        deleteFiles(context, true);
        TestSuite.onDataDeleted();
    }

    private void deleteAccounts() throws IOException {
        android.accounts.AccountManager am = AccountManager.get(MyContextHolder.get().context());
        android.accounts.Account[] aa = am.getAccountsByType( AuthenticatorService.ANDROID_ACCOUNT_TYPE );
        for (android.accounts.Account androidAccount : aa) {
            MyLog.i(this, "Removing old account: " + androidAccount.name);
            AccountManagerFuture<Boolean> amf = am.removeAccount(androidAccount, null, null);
            try {
                amf.getResult(10, TimeUnit.SECONDS);
            } catch (OperationCanceledException e) {
                throw new FileNotFoundException(e.getMessage());
            } catch (AuthenticatorException e) {
                throw new FileNotFoundException(e.getMessage());
            }
        }
    }

    private void deleteFiles(Context context, boolean useExternalStorage) {
        FileUtils.deleteFilesRecursively(MyPreferences.getDataFilesDir(MyPreferences.DIRECTORY_DOWNLOADS, useExternalStorage));
        FileUtils.deleteFilesRecursively(MyPreferences.getDataFilesDir(MyPreferences.DIRECTORY_DATABASES, useExternalStorage));
        FileUtils.deleteFilesRecursively(SharedPreferencesUtil.prefsDirectory(context));
    }

    private void testRestore(File dataFolder)
            throws IOException {
        
        MyBackupManager backupManager = new MyBackupManager(null);
        backupManager.prepareForRestore(dataFolder);
        assertTrue("Data folder exists: '" + backupManager.getDataFolder().getAbsolutePath() + "'", backupManager.getDataFolder().exists());

        backupManager.restore();
        assertEquals("Shared preferences restored", 1, backupManager.getBackupAgent().sharedPreferencesRestored);
        assertEquals("Databases restored", 1, backupManager.getBackupAgent().databasesRestored);
    }

    private void deleteBackup(File dataFolder) {
        for (File dataFile : dataFolder.listFiles()) {
            dataFile.delete();
        }
        dataFolder.delete();
    }
}
