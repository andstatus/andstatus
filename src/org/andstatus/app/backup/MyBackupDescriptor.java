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

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.ParcelFileDescriptor;

import org.andstatus.app.context.MyContextHolder;
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
    private static final Object TAG = MyBackupDescriptor.class;
    
    static final int BACKUP_SCHEMA_VERSION_UNKNOWN = -1;
    static final int BACKUP_SCHEMA_VERSION = 2;
    static final String KEY_ACCOUNTS_COUNT = "accounts_count";
    static final String KEY_CREATED_DATE = "created_date";
    static final String KEY_BACKUP_SCHEMA_VERSION = "backup_schema_version";
    static final String KEY_APPLICATION_VERSION_CODE = "app_version_code";
    
    private int backupSchemaVersion = BACKUP_SCHEMA_VERSION_UNKNOWN;
    private int applicationVersionCode = 0;

    private long createdDate = 0;
    private FileDescriptor fileDescriptor = null;

    private long accountsCount = 0;

    private final ProgressLogger progressLogger;
    
    private MyBackupDescriptor(ProgressLogger progressLogger) {
        this.progressLogger = progressLogger;
    }

    static MyBackupDescriptor getEmpty() {
        return new MyBackupDescriptor(ProgressLogger.getEmpty());
    }
    
    static MyBackupDescriptor fromOldParcelFileDescriptor(ParcelFileDescriptor parcelFileDescriptor, ProgressLogger progressLogger) {
        MyBackupDescriptor myBackupDescriptor = new MyBackupDescriptor(progressLogger);
        if (parcelFileDescriptor != null) {
            myBackupDescriptor.fileDescriptor = parcelFileDescriptor.getFileDescriptor();
            JSONObject jso = FileDescriptorUtils.getJSONObject(parcelFileDescriptor.getFileDescriptor());
            myBackupDescriptor.backupSchemaVersion = jso.optInt(KEY_BACKUP_SCHEMA_VERSION, myBackupDescriptor.backupSchemaVersion);
            myBackupDescriptor.createdDate = jso.optLong(KEY_CREATED_DATE, myBackupDescriptor.createdDate);
            myBackupDescriptor.applicationVersionCode = jso.optInt(KEY_APPLICATION_VERSION_CODE, myBackupDescriptor.applicationVersionCode);
            myBackupDescriptor.accountsCount = jso.optLong(KEY_ACCOUNTS_COUNT, myBackupDescriptor.accountsCount);
            if (myBackupDescriptor.backupSchemaVersion != BACKUP_SCHEMA_VERSION) {
                try {
                    MyLog.w(TAG, "Bad backup descriptor: " + jso.toString(2) );
                } catch (JSONException e) {
                    MyLog.d(TAG, "Bad backup descriptor: " + jso.toString(), e);
                }
            }
        }
        return myBackupDescriptor;
    }

    static MyBackupDescriptor fromEmptyParcelFileDescriptor(ParcelFileDescriptor parcelFileDescriptor, ProgressLogger progressLoggerIn) throws IOException {
        MyBackupDescriptor myBackupDescriptor = new MyBackupDescriptor(progressLoggerIn);
        myBackupDescriptor.fileDescriptor = parcelFileDescriptor.getFileDescriptor();
        myBackupDescriptor.backupSchemaVersion = BACKUP_SCHEMA_VERSION;

        PackageManager pm = MyContextHolder.get().context().getPackageManager();
        PackageInfo pi;
        try {
            pi = pm.getPackageInfo(MyContextHolder.get().context().getPackageName(), 0);
        } catch (NameNotFoundException e) {
            throw new FileNotFoundException(e.getLocalizedMessage());
        }
        myBackupDescriptor.applicationVersionCode = pi.versionCode;
        return myBackupDescriptor;
    }
    
    int getBackupSchemaVersion() {
        return backupSchemaVersion;
    }

    long getCreatedDate() {
        return createdDate;
    }

    int getApplicationVersionCode() {
        return applicationVersionCode;
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
            jso.put(KEY_BACKUP_SCHEMA_VERSION, backupSchemaVersion);
            jso.put(KEY_CREATED_DATE, createdDateNew);
            jso.put(KEY_APPLICATION_VERSION_CODE, applicationVersionCode);
            jso.put(KEY_ACCOUNTS_COUNT, accountsCount);
            
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
        return "MyBackupDescriptor {backupSchemaVersion:" + backupSchemaVersion
                + ", " + (createdDate == 0 ? "not created" : " created:" + (new Date(createdDate)).toString())
                + (fileDescriptor == null ? ", fileDescriptor:null" : "")
                + ", versionCode:" + applicationVersionCode
                + ", accountsCount:" + accountsCount
                + "}";
     }

    boolean saved() {
        return createdDate != 0;
    }

    public long getAccountsCount() {
        return accountsCount;
    }

    public void setAccountsCount(long accountsCount) {
        this.accountsCount = accountsCount;
        progressLogger.logProgress("Accounts backed up:" + accountsCount);
    }

    public ProgressLogger getLogger() {
        return progressLogger;
    }
}
