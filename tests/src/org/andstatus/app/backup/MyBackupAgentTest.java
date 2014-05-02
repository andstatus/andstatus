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

public class MyBackupAgentTest extends InstrumentationTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestSuite.initializeWithData(this);
    }

    public void testBackupRestore() throws IOException, JSONException {
        MyBackupAgent backupAgent = new MyBackupAgent();
        
        File outputDir = MyContextHolder.get().context().getCacheDir();
        File descriptorFile = File.createTempFile("descriptorFile", ".backup", outputDir);
        assertTrue("Descriptor file created: " + descriptorFile, descriptorFile.exists());
        MyLog.i(this, "Creating backup at '" + descriptorFile.getAbsolutePath() + "'");
        String backupFolder = "data_" + descriptorFile.getName();
        File dataFolder = new File(outputDir, backupFolder);
        assertTrue("Folder " + backupFolder + " created in " + dataFolder.getAbsolutePath(),
                dataFolder.mkdir());

        testBackup(backupAgent, descriptorFile, dataFolder);
        testRestore(backupAgent, descriptorFile, dataFolder);
        
        descriptorFile.delete();
        dataFolder.delete();
    }

    private void testBackup(MyBackupAgent backupAgent, File descriptorFile, File dataFolder)
            throws IOException, JSONException {
        ParcelFileDescriptor state2 = ParcelFileDescriptor.open(descriptorFile,
                ParcelFileDescriptor.MODE_READ_WRITE);
        MyBackupDataOutput dataOutput = new MyBackupDataOutput(dataFolder);
        backupAgent.onBackup(null, dataOutput, state2);
        state2.close();

        assertTrue("Descriptor file was filled: " + descriptorFile, descriptorFile.length() > 10);

        JSONObject jso = FileUtils.getJSONObject(descriptorFile);
        assertEquals(MyBackupAgent.BACKUP_VERSION, jso.getInt(MyBackupDescriptor.BACKUP_SCHEMA_VERSION));
        assertTrue(jso.getLong(MyBackupDescriptor.CREATED_DATE) > System.currentTimeMillis() - 1000000);

        File accountHeader = new File(dataFolder, "account_header");
        assertTrue(accountHeader.exists());
        jso = FileUtils.getJSONObject(accountHeader);
        assertTrue(jso.getInt(MyBackupDataOutput.KEY_DATA_SIZE) > 10);

        File accountData = new File(dataFolder, "account");
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

        backupAgent.onRestore(dataInput, appVersion, state4);
        state4.close();
    }
}
