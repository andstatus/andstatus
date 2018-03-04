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

import org.andstatus.app.context.MyContext;
import org.andstatus.app.util.FileUtils;
import org.andstatus.app.util.MyLog;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

public class MyBackupDataInput {
    final static int FILE_CHUNK_SIZE = 250000;
    private static final String ENTITY_HEADER_NOT_READ = "Entity header not read";
    private MyContext myContext;
    private BackupDataInput backupDataInput;

    private File dataFolder = null;
    private Set<BackupHeader> headers = new TreeSet<BackupHeader>();
    private Iterator<BackupHeader> keysIterator;
    private boolean mHeaderReady = false;
    private int dataOffset = 0;
    private BackupHeader header = BackupHeader.getEmpty();
    
    static class BackupHeader implements Comparable<BackupHeader> {
        String key;
        long ordinalNumber;
        int dataSize;
        String fileExtension;

        BackupHeader(String key, long ordinalNumber, int dataSize, String fileExtension) {
            this.key = key;
            this.ordinalNumber = ordinalNumber;
            this.dataSize = dataSize;
            this.fileExtension = fileExtension;
        }

        static BackupHeader getEmpty() {
            return new BackupHeader("", 0, 0, "");
        }
        
        static BackupHeader fromJson(JSONObject jso) {
            return new BackupHeader(
            jso.optString(MyBackupDataOutput.KEY_KEYNAME, ""),
            jso.optLong(MyBackupDataOutput.KEY_ORDINAL_NUMBER, 0),
            jso.optInt(MyBackupDataOutput.KEY_DATA_SIZE, 0),
            jso.optString(MyBackupDataOutput.KEY_FILE_EXTENSION, MyBackupDataOutput.DATA_FILE_EXTENSION_DEFAULT));
        }

        @Override
        public int compareTo(BackupHeader another) {
            return ordinalNumber == another.ordinalNumber ? 0
                    : (ordinalNumber > another.ordinalNumber ? 1 : -1);
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + dataSize;
            result = prime * result + ((fileExtension == null) ? 0 : fileExtension.hashCode());
            result = prime * result + ((key == null) ? 0 : key.hashCode());
            result = prime * result + (int) (ordinalNumber ^ (ordinalNumber >>> 32));
            return result;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            BackupHeader other = (BackupHeader) o;
            if (dataSize != other.dataSize) {
                return false;
            }
            if (fileExtension == null) {
                if (other.fileExtension != null) {
                    return false;
                }
            } else if (!fileExtension.equals(other.fileExtension)) {
                return false;
            }
            if (key == null) {
                if (other.key != null) {
                    return false;
                }
            } else if (!key.equals(other.key)) {
                return false;
            }
            if (ordinalNumber != other.ordinalNumber) {
                return false;
            }
            return true;
        }

        @Override
        public String toString() {
            return "BackupHeader [key=" + key + ", ordinalNumber=" + ordinalNumber + ", dataSize="
                    + dataSize + "]";
        }
    }
    
    MyBackupDataInput(BackupDataInput backupDataInput) {
        this.backupDataInput = backupDataInput;
    }

    MyBackupDataInput(File dataFolder) throws IOException {
        this.dataFolder = dataFolder;
        for (String filename : this.dataFolder.list()) {
            if (filename.endsWith(MyBackupDataOutput.HEADER_FILE_SUFFIX)) {
                File headerFile = new File(dataFolder, filename);
                headers.add(BackupHeader.fromJson(FileUtils.getJSONObject(headerFile)));
            }
        }
        keysIterator = headers.iterator();
    }

    Set<BackupHeader> listKeys() {
        return headers;
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
        dataOffset = 0;
        if (keysIterator.hasNext()) {
            header = keysIterator.next();
            if (header.dataSize > 0) {
                mHeaderReady = true;
            } else {
                throw new FileNotFoundException("Header is invalid, " + header);
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
            return header.key;
        } else {
            throw new IllegalStateException(ENTITY_HEADER_NOT_READ);
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
            return header.dataSize;
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
        int bytesRead = 0;
        if (size > FILE_CHUNK_SIZE) {
            throw new FileNotFoundException("Size to read is too large: " + size);
        } else if (size < 1 || dataOffset >= header.dataSize) {
            // skip
        } else if (mHeaderReady) {
            File dataFile = new File(dataFolder, header.key + MyBackupDataOutput.DATA_FILE_SUFFIX + header.fileExtension);
            byte[] readData = FileUtils.getBytes(dataFile, dataOffset, size);
            bytesRead = readData.length;
            System.arraycopy(readData, 0, data, offset, bytesRead);
        } else {
            throw new IllegalStateException("Entity header not read");
        }
        MyLog.v(this, "key=" + header.key + ", offset=" + dataOffset + ", bytes read=" + bytesRead);
        dataOffset += bytesRead;
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
            mHeaderReady = false;
        } else {
            throw new IllegalStateException("Entity header not read");
        }
    }
    
    File getDataFolder() {
        return dataFolder;
    }
    
    void setMyContext(MyContext myContext) {
        this.myContext = myContext;
    }
    public MyContext getMyContext() {
        return myContext;
    }
}
