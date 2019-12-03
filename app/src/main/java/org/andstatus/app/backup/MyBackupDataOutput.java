/*
 * Copyright (C) 2014-2019 yvolk (Yuri Volkov), http://yurivolkov.com
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
import android.content.Context;

import androidx.documentfile.provider.DocumentFile;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.util.MyLog;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static org.andstatus.app.util.FileUtils.newFileOutputStreamWithRetry;

/** Allowing to instantiate and to mock BackupDataOutput class */
public class MyBackupDataOutput {
    static final String HEADER_FILE_SUFFIX = "_header.json";
    static final String DATA_FILE_SUFFIX = "_data";
    static final String DATA_FILE_EXTENSION_DEFAULT = ".dat";
    static final String KEY_KEYNAME = "key";
    static final String KEY_DATA_SIZE = "data_size";
    static final String KEY_ORDINAL_NUMBER = "ordinal_number";
    static final String KEY_FILE_EXTENSION = "file_extension";
    private final Context context;
    private File dataFolder = null; // TODO we don't need this!
    private DocumentFile docFolder = null;
    private BackupDataOutput backupDataOutput;
    private int sizeToWrite = 0;
    private int sizeWritten = 0;
    private File dataFile = null;
    private DocumentFile docFile = null;
    private int headerOrdinalNumber = 0;

    public MyBackupDataOutput(Context context, BackupDataOutput backupDataOutput) {
        this.context = context;
        this.backupDataOutput = backupDataOutput;
    }

    public MyBackupDataOutput(File dataFolder) {
        this.context = MyContextHolder.get().context();
        this.dataFolder = dataFolder;
    }

    public MyBackupDataOutput(Context context, DocumentFile docFolder) {
        this.context = context;
        this.docFolder = docFolder;
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
        JSONObject jso = new JSONObject();
        try {
            jso.put(KEY_KEYNAME, key);
            jso.put(KEY_ORDINAL_NUMBER, headerOrdinalNumber);
            jso.put(KEY_DATA_SIZE, dataSize);
            jso.put(KEY_FILE_EXTENSION, fileExtension);
            byte[] bytes = jso.toString(2).getBytes(StandardCharsets.UTF_8);
            appendBytesToChild(key + HEADER_FILE_SUFFIX, bytes, bytes.length);
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    private File createFileIfNeeded(int dataSize, String childName) throws IOException {
        File file = new File(dataFolder, childName);
        if (file.exists() && !file.delete()) {
            throw new FileNotFoundException("Couldn't delete " + file.getAbsolutePath());
        }
        if (dataSize >= 0 && !file.createNewFile()) {
            throw new FileNotFoundException("Couldn't create " + file.getAbsolutePath());
        }
        return file;
    }
    
    private void createDataFile(String key, int dataSize, String fileExtension) throws IOException {
        String childName = key + DATA_FILE_SUFFIX + fileExtension;
        if (docFolder == null) {
            dataFile = createFileIfNeeded(dataSize, childName);
        } else {
            docFile = createDocumentIfNeeded(dataSize, childName);
        }
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
        if (docFile == null) {
            if (!dataFile.exists()) {
                throw new FileNotFoundException("Output file doesn't exist " + dataFile.getAbsolutePath());
            }
        } else if (!docFile.exists()) {
            throw new FileNotFoundException("Output document doesn't exist " + docFile.getUri());
        }
        if (size < 0) {
            throw new FileNotFoundException("Wrong number of bytes to write: " + size);
        }
        appendBytesToFile(data, size);
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

    private void appendBytesToFile(byte[] data, int size) throws IOException {
        try (OutputStream fileOutputStream = getOutputStreamAppend(size);
             OutputStream out = new BufferedOutputStream(fileOutputStream)) {
            out.write(data, 0, size);
        }
    }

    private OutputStream getOutputStreamAppend(int size) throws IOException {
        if (docFolder == null) {
            MyLog.v(this, "Appending data to file='" + dataFile.getName() + "', size=" + size);
            return newFileOutputStreamWithRetry(dataFile, true);
        } else {
            MyLog.v(this, "Appending data to document='" + docFile.getName() + "', size=" + size);
            return context.getContentResolver().openOutputStream(docFile.getUri(), "wa");
        }
    }

    private void appendBytesToChild(String childName, byte[] data, int size) throws IOException {
        MyLog.v(this, "Appending data to file='" + childName + "', size=" + size);
        try (OutputStream outputStream = getOutputStreamAppend(childName, size);
             OutputStream out = new BufferedOutputStream(outputStream)) {
            out.write(data, 0, size);
        }
    }

    private OutputStream getOutputStreamAppend(String childName, int size) throws IOException {
        if (docFolder == null) {
            return newFileOutputStreamWithRetry(createFileIfNeeded(size, childName), true);
        } else {
            return context.getContentResolver().openOutputStream(createDocumentIfNeeded(size, childName).getUri(), "wa");
        }
    }

    private DocumentFile createDocumentIfNeeded(int dataSize, String childName) throws IOException {
        DocumentFile documentFile = docFolder.findFile(childName);
        if (documentFile == null) {
            documentFile = docFolder.createFile("", childName);
        }
        if (documentFile == null) {
            throw  new IOException("Couldn't create '" + childName + "' document inside '" + docFolder.getUri() + "'");
        }
        return documentFile;
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
