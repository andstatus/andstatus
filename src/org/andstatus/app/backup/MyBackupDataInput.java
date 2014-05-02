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

import java.io.File;
import java.io.IOException;

public class MyBackupDataInput {
    private BackupDataInput backupDataInput;

    private File dataFolder = null;
    private boolean mHeaderReady = false;
    private String key = "";
    private int dataSize = 0;
    
    MyBackupDataInput(BackupDataInput backupDataInput) {
        this.backupDataInput = backupDataInput;
    }

    MyBackupDataInput(File dataFolder) {
        this.dataFolder = dataFolder;
    }

    /** {@link BackupDataInput#readNextHeader()}  */
    public boolean readNextHeader() throws IOException {
        if (backupDataInput != null) {
            return backupDataInput.readNextHeader();
        } else {
            return readNextHeader2();
        }
    }

    private boolean readNextHeader2() {
        mHeaderReady = false;
        // TODO:

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

    private int readEntityData2(byte[] data, int offset, int size) {
        int bytesRead = 0;
        if (mHeaderReady) {
            // TODO:
        } else {
            throw new IllegalStateException("Entity header not read");
        }
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
    
}
