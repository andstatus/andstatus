package org.andstatus.app.backup;

import android.os.ParcelFileDescriptor;
import android.test.InstrumentationTestCase;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.util.FileUtils;
import org.andstatus.app.util.MyLog;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class MyBackupAgentTest extends InstrumentationTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestSuite.initializeWithData(this);
    }

    public void testBackupRestore() throws IOException, JSONException {
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

        testBackup(backupAgent, descriptorFile, dataFolder);
        testRestore(backupAgent, descriptorFile, dataFolder);
        
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

    private void testRestore(MyBackupAgent backupAgent, File descriptorFile, File dataFolder)
            throws IOException {
        final int appVersion = 100;
        ParcelFileDescriptor state4 = ParcelFileDescriptor.open(descriptorFile,
                ParcelFileDescriptor.MODE_READ_WRITE);
        MyBackupDataInput dataInput = new MyBackupDataInput(dataFolder);
        assertEquals("Keys in the backup: " + Arrays.toString(dataInput.listKeys().toArray()) , 4, dataInput.listKeys().size());
        
        backupAgent.onRestore(dataInput, appVersion, state4);
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
