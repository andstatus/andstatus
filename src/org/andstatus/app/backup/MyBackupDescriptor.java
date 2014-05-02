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

import android.os.ParcelFileDescriptor;

import org.andstatus.app.data.DbUtils;
import org.andstatus.app.util.FileDescriptorUtils;
import org.andstatus.app.util.MyLog;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Date;

public class MyBackupDescriptor {
    static final String CREATED_DATE = "created_date";
    static final String BACKUP_SCHEMA_VERSION = "backup_schema_version";
    private int backupSchemaVersion;
    private long createdDate = 0;
    private FileDescriptor fileDescriptor = null;

    static MyBackupDescriptor fromParcelFileDescriptor(int backupSchemaVersionIn, ParcelFileDescriptor parcelFileDescriptor) {
        MyBackupDescriptor myBackupDescriptor = new MyBackupDescriptor();
        myBackupDescriptor.backupSchemaVersion = backupSchemaVersionIn;
        if (parcelFileDescriptor != null) {
            myBackupDescriptor.fileDescriptor = parcelFileDescriptor.getFileDescriptor();
            JSONObject jso = FileDescriptorUtils.getJSONObject(parcelFileDescriptor.getFileDescriptor());
            myBackupDescriptor.backupSchemaVersion = jso.optInt(BACKUP_SCHEMA_VERSION, backupSchemaVersionIn);
            myBackupDescriptor.createdDate = jso.optLong(CREATED_DATE, myBackupDescriptor.createdDate);
        }
        return myBackupDescriptor;
    }

    int getBackupSchemaVersion() {
        return backupSchemaVersion;
    }

    long getCreatedDate() {
        return createdDate;
    }
    
    boolean isEmpty() {
        return fileDescriptor == null;
    }

    void save() throws IOException {
        if (isEmpty()) {
            throw new FileNotFoundException("MyBackupDescriptor is empty");
        }
        long createdDateNew = createdDate;
        if (createdDateNew == 0) {
            createdDateNew = System.currentTimeMillis();
        }
        JSONObject jso = new JSONObject();
        try {
            jso.put(BACKUP_SCHEMA_VERSION, backupSchemaVersion);
            jso.put(CREATED_DATE, createdDateNew);
            writeStringToFileDescriptor(jso.toString(), fileDescriptor, true);
            createdDate = createdDateNew;
        } catch (JSONException e) {
            throw new FileNotFoundException(e.getLocalizedMessage());
        }
    }
    
    private void writeStringToFileDescriptor(String string, FileDescriptor fd, boolean logged) throws IOException {
        final String method = "writeStringToFileDescriptor";
        Writer out = null;
        try {
            out = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(fd), "UTF-8"));
            out.write(string);
        } catch (IOException e) {
            if (logged) {
                MyLog.d(this, method, e);
            }
            throw new FileNotFoundException(method + "; " + e.getLocalizedMessage());
        } finally {
            DbUtils.closeSilently(out, method);
        }        
    }
    
    @Override
    public String toString() {
        return "MyBackupDescriptor {backupSchemaVersion:"
                + backupSchemaVersion
                + ", "
                + (createdDate == 0 ? "not created" : " created:"
                        + (new Date(createdDate)).toString())
                + (fileDescriptor == null ? ", fileDescriptor:null" : "")
                + "}";
     }

    boolean saved() {
        return (createdDate != 0);
    }
}
