/**
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

package org.andstatus.app.backup;

import android.app.backup.BackupDataOutput;

import org.andstatus.app.util.MyLog;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Allowing to instantiate and to mock BackupDataOutput class */
public class MyBackupDataOutput {
    static final String HEADER_FILE_SUFFIX = "_header.json";
    static final String DATA_FILE_SUFFIX = "_data";
    static final String DATA_FILE_EXTENSION_DEFAULT = ".dat";
    static final String KEY_KEYNAME = "key";
    static final String KEY_DATA_SIZE = "data_size";
    static final String KEY_ORDINAL_NUMBER = "ordinal_number";
    static final String KEY_FILE_EXTENSION = "file_extension";
    private File dataFolder;
    private BackupDataOutput backupDataOutput;
    private int sizeToWrite = 0;
    private int sizeWritten = 0;
    private File dataFile = null;
    private int headerOrdinalNumber = 0;

    public MyBackupDataOutput(BackupDataOutput backupDataOutput) {
        this.backupDataOutput = backupDataOutput;
    }
    
    public MyBackupDataOutput(File dataFolder) {
        this.dataFolder = dataFolder;
    }

    /** {@link BackupDataOutput#writeEntityHeader(String, int)} */
    public int writeEntityHeader(String key, int dataSize, String fileExtension) throws IOException {
        headerOrdinalNumber++;
        if (backupDataOutput != null) {
            return backupDataOutput.writeEntityHeader(key, dataSize);
        } else {
            return writeEntityHeader2(key, dataSize, fileExtension);
        }
    }

    private int writeEntityHeader2(String key, int dataSize, String fileExtension) throws IOException {
        MyLog.v(this, "Writing header for '" + key + "', size=" + dataSize);
        sizeToWrite = dataSize;
        sizeWritten = 0;
        writeHeaderFile(key, dataSize, fileExtension);
        createDataFile(key, dataSize, fileExtension);
        return key.length();
    }

    private void writeHeaderFile(String key, int dataSize, String fileExtension) throws IOException {
        File headerFile = new File(dataFolder, key + HEADER_FILE_SUFFIX);
        createFileIfNeeded(dataSize, headerFile);
        JSONObject jso = new JSONObject();
        try {
            jso.put(KEY_KEYNAME, key);
            jso.put(KEY_ORDINAL_NUMBER, headerOrdinalNumber);
            jso.put(KEY_DATA_SIZE, dataSize);
            jso.put(KEY_FILE_EXTENSION, fileExtension);
            byte[] bytes = jso.toString(2).getBytes(StandardCharsets.UTF_8);
            appendBytesToFile(headerFile, bytes, bytes.length);
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    private void createFileIfNeeded(int dataSize, File file) throws IOException {
        if (file.exists() && !file.delete()) {
            throw new FileNotFoundException("Couldn't delete " + file.getAbsolutePath());
        }
        if (dataSize >= 0 && !file.createNewFile()) {
            throw new FileNotFoundException("Couldn't create " + file.getAbsolutePath());
        }
    }
    
    private void createDataFile(String key, int dataSize, String fileExtension) throws IOException {
        dataFile = new File(dataFolder, key + DATA_FILE_SUFFIX + fileExtension);
        createFileIfNeeded(dataSize, dataFile);
    }

    /** {@link BackupDataOutput#writeEntityData(byte[], int)} */
    public int writeEntityData(byte[] data, int size) throws IOException {
        if (backupDataOutput != null) {
            return backupDataOutput.writeEntityData(data, size);
        } else {
            return writeEntityData2(data, size);
        }
    }

    private int writeEntityData2(byte[] data, int size) throws IOException {
        if (!dataFile.exists()) {
            throw new FileNotFoundException("Output file doesn't exist " + dataFile.getAbsolutePath());
        }
        if (size < 0) {
            throw new FileNotFoundException("Wrong number of bytes to write: " + size);
        }
        appendBytesToFile(dataFile, data, size);
        sizeWritten += size;
        if (sizeWritten >= sizeToWrite) {
            try {
                if (sizeWritten > sizeToWrite) {
                    throw new FileNotFoundException("Data is longer than expected: written=" + sizeWritten 
                            + ", expected=" + sizeToWrite );
                }
            } finally {
                dataFile = null;
                sizeWritten = 0;
            }
        }
        return size;
    }

    private void appendBytesToFile(File file, byte[] data, int size) throws IOException {
        MyLog.v(this, "Appending data to file='" + file.getName() + "', size=" + size);
        try (FileOutputStream fileOutputStream = new FileOutputStream(file, true);
            OutputStream out = new BufferedOutputStream(fileOutputStream)) {
            out.write(data, 0, size);
        }
    }

    File getDataFolder() {
        return dataFolder;
    }

    static String getDataFileExtension(File dataFile) {
        String name = dataFile.getName();
        int indDot = name.lastIndexOf(".");
        if (indDot >= 0) {
            return name.substring(indDot);
        }
        return DATA_FILE_EXTENSION_DEFAULT;
    }
}
