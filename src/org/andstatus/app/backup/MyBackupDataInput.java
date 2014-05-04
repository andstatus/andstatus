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

import android.app.backup.BackupDataInput;

import org.andstatus.app.util.FileUtils;
import org.andstatus.app.util.MyLog;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class MyBackupDataInput {
    private BackupDataInput backupDataInput;

    private File dataFolder = null;
    private Set<String> keys = new HashSet<String>();
    private Iterator<String> keysIterator;
    private boolean mHeaderReady = false;
    private String key = "";
    private int dataSize = 0;
    
    MyBackupDataInput(BackupDataInput backupDataInput) {
        this.backupDataInput = backupDataInput;
    }

    MyBackupDataInput(File dataFolder) {
        this.dataFolder = dataFolder;
        for (String fileName : this.dataFolder.list()) {
            if (fileName.endsWith(MyBackupDataOutput.HEADER_FILE_SUFFIX)) {
                keys.add(fileName.substring(0, fileName.lastIndexOf(MyBackupDataOutput.HEADER_FILE_SUFFIX)));
            }
        }
        keysIterator = keys.iterator();
    }

    Set<String> listKeys() {
        return keys;
    }
    
    /** {@link BackupDataInput#readNextHeader()}  */
    public boolean readNextHeader() throws IOException {
        if (backupDataInput != null) {
            return backupDataInput.readNextHeader();
        } else {
            return readNextHeader2();
        }
    }

    private boolean readNextHeader2() throws IOException {
        mHeaderReady = false;
        if (keysIterator.hasNext()) {
            key = keysIterator.next();
            File headerFile = new File(dataFolder, key + MyBackupDataOutput.HEADER_FILE_SUFFIX);
            JSONObject jso = FileUtils.getJSONObject(headerFile);
            dataSize = jso.optInt(MyBackupDataOutput.KEY_DATA_SIZE, -1);
            if (dataSize > 0) {
                mHeaderReady = true;
            } else {
                throw new FileNotFoundException("Error reading the '" + headerFile.getName() + "' header file");
            }
        }
        return mHeaderReady;
    }

    /** {@link BackupDataInput#getKey()}  */
    public String getKey() {
        if (backupDataInput != null) {
            return backupDataInput.getKey();
        } else {
            return getKey2();
        }
    }

    private String getKey2() {
        if (mHeaderReady) {
            return key;
        } else {
            throw new IllegalStateException("Entity header not read");
        }
    }

    /** {@link BackupDataInput#getDataSize()}  */
    public int getDataSize() {
        if (backupDataInput != null) {
            return backupDataInput.getDataSize();
        } else {
            return getDataSize2();
        }
    }

    private int getDataSize2() {
        if (mHeaderReady) {
            return dataSize;
        } else {
            throw new IllegalStateException("Entity header not read");
        }
    }

    /** {@link BackupDataInput#readEntityData(byte[], int, int)}  */
    public int readEntityData(byte[] data, int offset, int size) throws IOException {
        if (backupDataInput != null) {
            return backupDataInput.readEntityData(data, offset, size);
        } else {
            return readEntityData2(data, offset, size);
        }
    }

    private int readEntityData2(byte[] data, int offset, int size) throws IOException {
        final long chunkSize = 50000;
        int bytesRead = 0;
        if (size > chunkSize) {
            throw new FileNotFoundException("Size to read is too large: " + size);
        } else if (size < 1 || offset >= dataSize) {
            // skip
        } else if (mHeaderReady) {
            File dataFile = new File(dataFolder, key + MyBackupDataOutput.DATA_FILE_SUFFIX);
            byte[] readData = FileUtils.getBytes(dataFile, offset, size);
            bytesRead = readData.length;
            System.arraycopy(readData, offset, data, 0, bytesRead);
        } else {
            throw new IllegalStateException("Entity header not read");
        }
        MyLog.v(this, "key=" + key + ", offset=" + offset + ", bytes read=" + bytesRead);
        return bytesRead;
    }

    /** {@link BackupDataInput#skipEntityData()}  */
    public void skipEntityData() throws IOException {
        if (backupDataInput != null) {
            backupDataInput.skipEntityData();
        } else {
            skipEntityData2();
        }
    }

    private void skipEntityData2() {
        if (mHeaderReady) {
            // TODO:
            mHeaderReady = false;
        } else {
            throw new IllegalStateException("Entity header not read");
        }
    }
    
    File getDataFolder() {
        return dataFolder;
    }
}
