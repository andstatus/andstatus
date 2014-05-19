package org.andstatus.app.backup;

import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.ParcelFileDescriptor;
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
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class MyBackupAgentTest extends InstrumentationTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestSuite.initializeWithData(this);
    }

    public void testBackupRestore() throws IOException, JSONException, NameNotFoundException, ConnectionException, InterruptedException {
        MyBackupAgent backupAgent = new MyBackupAgent();
        
        File outputFolder = MyContextHolder.get().context().getCacheDir();
        final String descriptorFilePrefix = "descriptor";
        File descriptorFile = File.createTempFile(descriptorFilePrefix, ".backup", outputFolder);
        assertTrue("Descriptor file created: " + descriptorFile.getAbsolutePath(), descriptorFile.exists());
        MyLog.i(this, "Creating backup at '" + descriptorFile.getAbsolutePath() + "'");
        String dataFolderName = "data" + descriptorFile.getName().substring(descriptorFilePrefix.length(), descriptorFile.getName().length());
        File dataFolder = new File(outputFolder, dataFolderName);
        assertTrue("Folder " + dataFolderName + " created in " + outputFolder.getAbsolutePath(),
                dataFolder.mkdir());

        PersistentAccounts accountsBefore = PersistentAccounts.getEmpty();
        accountsBefore.initialize();
        assertEquals("Compare Persistent accounts with copy", MyContextHolder.get().persistentAccounts(), accountsBefore);
        
        testBackup(backupAgent, descriptorFile, dataFolder);
        deleteApplicationData();
        testRestore(backupAgent, descriptorFile, dataFolder);
        TestSuite.initialize(this);

        assertEquals("Persistent accounts", accountsBefore, MyContextHolder.get().persistentAccounts());
        assertEquals(
                "One account",
                accountsBefore.fromAccountName(TestSuite.STATUSNET_TEST_ACCOUNT_NAME),
                MyContextHolder.get().persistentAccounts()
                        .fromAccountName(TestSuite.STATUSNET_TEST_ACCOUNT_NAME));
        
        deleteBackup(descriptorFile, dataFolder);
    }

    private void testBackup(MyBackupAgent backupAgent, File descriptorFile, File dataFolder)
            throws IOException, JSONException {
        ParcelFileDescriptor state2 = ParcelFileDescriptor.open(descriptorFile,
                ParcelFileDescriptor.MODE_READ_WRITE);
        MyBackupDataOutput dataOutput = new MyBackupDataOutput(dataFolder);
        backupAgent.onBackup(null, dataOutput, state2);
        state2.close();

        assertEquals("Shared preferences backed up", 1, backupAgent.sharedPreferencesBackedUp);
        assertEquals("Databases backed up", 1, backupAgent.databasesBackedUp);
        assertEquals("Accounts backed up", backupAgent.accountsBackedUp, MyContextHolder.get()
                .persistentAccounts().size());
        
        assertTrue("Descriptor file was filled: " + descriptorFile, descriptorFile.length() > 10);

        JSONObject jso = FileUtils.getJSONObject(descriptorFile);
        assertEquals(MyBackupAgent.BACKUP_VERSION, jso.getInt(MyBackupDescriptor.BACKUP_SCHEMA_VERSION));
        assertTrue(jso.getLong(MyBackupDescriptor.CREATED_DATE) > System.currentTimeMillis() - 1000000);

        File accountHeader = new File(dataFolder, "account_header");
        assertTrue(accountHeader.exists());
        jso = FileUtils.getJSONObject(accountHeader);
        assertTrue(jso.getInt(MyBackupDataOutput.KEY_DATA_SIZE) > 10);

        File accountData = new File(dataFolder, "account_data");
        assertTrue(accountData.exists());
        JSONArray jsa = FileUtils.getJSONArray(accountData);
        assertTrue(jsa.length() > 2);
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
        deleteFilesRecursively(MyPreferences.getDataFilesDir(MyPreferences.DIRECTORY_AVATARS, useExternalStorage));
        deleteFilesRecursively(MyPreferences.getDataFilesDir(MyPreferences.DIRECTORY_DATABASES, useExternalStorage));
        deleteFilesRecursively(SharedPreferencesUtil.prefsDirectory(context));
    }

    private void deleteFilesRecursively(File rootDirectory) {
        for (File file : rootDirectory.listFiles()) {
            if (file.isDirectory()) {
                deleteFilesRecursively(file);
            } else {
                file.delete();
            }
        }
    }
    
    private void testRestore(MyBackupAgent backupAgent, File descriptorFile, File dataFolder)
            throws IOException {
        final int appVersion = 100;
        ParcelFileDescriptor state4 = ParcelFileDescriptor.open(descriptorFile,
                ParcelFileDescriptor.MODE_READ_WRITE);
        MyBackupDataInput dataInput = new MyBackupDataInput(dataFolder);
        assertEquals("Keys in the backup: " + Arrays.toString(dataInput.listKeys().toArray()) , backupAgent.suggestionsBackedUp + 3, dataInput.listKeys().size());
        
        backupAgent.onRestore(dataInput, appVersion, state4);
        assertEquals("Shared preferences restored", 1, backupAgent.sharedPreferencesRestored);
        assertEquals("Databases restored", 1, backupAgent.databasesRestored);
        assertEquals("Accounts restored", backupAgent.accountsBackedUp, backupAgent.accountsRestored);
        state4.close();
    }

    private void deleteBackup(File descriptorFile, File dataFolder) {
        for (File dataFile : dataFolder.listFiles()) {
            dataFile.delete();
        }
        dataFolder.delete();
        descriptorFile.delete();
    }
}
