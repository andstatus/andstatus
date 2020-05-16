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

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.ParcelFileDescriptor;

import androidx.documentfile.provider.DocumentFile;

import org.andstatus.app.database.DatabaseCreator;
import org.andstatus.app.util.DocumentFileUtils;
import org.andstatus.app.util.FileDescriptorUtils;
import org.andstatus.app.util.JsonUtils;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtil;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import static org.andstatus.app.context.MyContextHolder.myContextHolder;

public class MyBackupDescriptor {
    private static final Object TAG = MyBackupDescriptor.class;

    static final int BACKUP_SCHEMA_VERSION_UNKNOWN = -1;
    /** Depends, in particular, on @{@link DatabaseCreator#DATABASE_VERSION}
     * v.7 2017-11-04 app.v.36 Moving to ActivityStreams data model
     * v.6 2016-11-27 app.v.31 database schema changed
     * v.5 2016-05-22 app.v.27 database schema changed
     * v.4 2016-02-28 app.v.23 database schema changed
     */
    static final int BACKUP_SCHEMA_VERSION = 7;
    static final String KEY_ACCOUNTS_COUNT = "accounts_count";
    static final String KEY_CREATED_DATE = "created_date";
    static final String KEY_BACKUP_SCHEMA_VERSION = "backup_schema_version";
    static final String KEY_APPLICATION_VERSION_CODE = "app_version_code";
    static final String KEY_APPLICATION_VERSION_NAME = "app_version_name";

    private int backupSchemaVersion = BACKUP_SCHEMA_VERSION_UNKNOWN;
    private int applicationVersionCode = 0;
    private String applicationVersionName = "";

    private long createdDate = 0;
    private boolean saved = false;
    private FileDescriptor fileDescriptor = null;
    private DocumentFile docDescriptor = null;

    private long accountsCount = 0;

    private final ProgressLogger progressLogger;

    private MyBackupDescriptor(ProgressLogger progressLogger) {
        this.progressLogger = progressLogger;
    }

    static MyBackupDescriptor getEmpty() {
        return new MyBackupDescriptor(ProgressLogger.getEmpty(""));
    }

    static MyBackupDescriptor fromOldParcelFileDescriptor(ParcelFileDescriptor parcelFileDescriptor,
                                                          ProgressLogger progressLogger) {
        MyBackupDescriptor myBackupDescriptor = new MyBackupDescriptor(progressLogger);
        if (parcelFileDescriptor != null) {
            myBackupDescriptor.fileDescriptor = parcelFileDescriptor.getFileDescriptor();
            JSONObject jso = FileDescriptorUtils.getJSONObject(parcelFileDescriptor.getFileDescriptor());
            myBackupDescriptor.setJson(jso);
        }
        return myBackupDescriptor;
    }

    static MyBackupDescriptor fromOldDocFileDescriptor(Context context, DocumentFile parcelFileDescriptor,
                                                          ProgressLogger progressLogger) {
        MyBackupDescriptor myBackupDescriptor = new MyBackupDescriptor(progressLogger);
        if (parcelFileDescriptor != null) {
            myBackupDescriptor.docDescriptor = parcelFileDescriptor;
            myBackupDescriptor.setJson(DocumentFileUtils.getJSONObject(context, parcelFileDescriptor));
        }
        return myBackupDescriptor;
    }

    void setJson(JSONObject jso) {
        backupSchemaVersion = jso.optInt(KEY_BACKUP_SCHEMA_VERSION,
        backupSchemaVersion);
        createdDate = jso.optLong(KEY_CREATED_DATE, createdDate);
        saved = createdDate != 0;
        applicationVersionCode = jso.optInt(KEY_APPLICATION_VERSION_CODE, applicationVersionCode);
        applicationVersionName = JsonUtils.optString(jso, KEY_APPLICATION_VERSION_NAME, applicationVersionName);
        accountsCount = jso.optLong(KEY_ACCOUNTS_COUNT, accountsCount);
        if (backupSchemaVersion != BACKUP_SCHEMA_VERSION) {
            try {
                MyLog.w(TAG, "Bad backup descriptor: " + jso.toString(2) );
            } catch (JSONException e) {
                MyLog.d(TAG, "Bad backup descriptor: " + jso.toString(), e);
            }
        }
    }

    static MyBackupDescriptor fromEmptyParcelFileDescriptor(ParcelFileDescriptor parcelFileDescriptor,
                                                            ProgressLogger progressLoggerIn) throws IOException {
        MyBackupDescriptor myBackupDescriptor = new MyBackupDescriptor(progressLoggerIn);
        myBackupDescriptor.fileDescriptor = parcelFileDescriptor.getFileDescriptor();
        myBackupDescriptor.setEmptyFields(myContextHolder.getNow().baseContext());
        myBackupDescriptor.backupSchemaVersion = BACKUP_SCHEMA_VERSION;
        return myBackupDescriptor;
    }

    static MyBackupDescriptor fromEmptyDocumentFile(Context context, DocumentFile documentFile,
                                                    ProgressLogger progressLoggerIn) throws IOException {
        MyBackupDescriptor myBackupDescriptor = new MyBackupDescriptor(progressLoggerIn);
        myBackupDescriptor.docDescriptor = documentFile;
        myBackupDescriptor.setEmptyFields(context);
        return myBackupDescriptor;
    }

    private void setEmptyFields(Context context) throws IOException {
        backupSchemaVersion = BACKUP_SCHEMA_VERSION;

        PackageManager pm = context.getPackageManager();
        PackageInfo pi;
        try {
            pi = pm.getPackageInfo(context.getPackageName(), 0);
        } catch (NameNotFoundException e) {
            throw new IOException(e);
        }
        applicationVersionCode = pi.versionCode;
        applicationVersionName = pi.versionName;
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

    public String getApplicationVersionName() {
        return applicationVersionName;
    }

    boolean isEmpty() {
        return fileDescriptor == null && docDescriptor == null;
    }

    void save(Context context) throws IOException {
        if (isEmpty()) {
            throw new FileNotFoundException("MyBackupDescriptor is empty");
        }
        try (OutputStream outputStream = docDescriptor == null
                ? new FileOutputStream(fileDescriptor)
                : context.getContentResolver().openOutputStream(docDescriptor.getUri())
        ) {
            if (createdDate == 0) createdDate = System.currentTimeMillis();
            writeStringToStream(toJson().toString(2), outputStream);
            saved = true;
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    private JSONObject toJson() {
        JSONObject jso = new JSONObject();
        if (isEmpty()) return jso;
        try {
            jso.put(KEY_BACKUP_SCHEMA_VERSION, backupSchemaVersion);
            jso.put(KEY_CREATED_DATE, createdDate);
            jso.put(KEY_APPLICATION_VERSION_CODE, applicationVersionCode);
            jso.put(KEY_APPLICATION_VERSION_NAME, applicationVersionName);
            jso.put(KEY_ACCOUNTS_COUNT, accountsCount);
        } catch (JSONException e) {
            MyLog.w(this, "toJson", e);
        }
        return jso;
    }

    private void writeStringToStream(String string, OutputStream outputStream) throws IOException {
        final String method = "writeStringToFileDescriptor";
        try (Writer out = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
            out.write(string);
        } catch (IOException e) {
            MyLog.d(this, method, e);
            throw new FileNotFoundException(method + "; " + e.getLocalizedMessage());
        }
    }

    @Override
    public String toString() {
        return "MyBackupDescriptor " + toJson().toString();
     }

    boolean saved() {
        return saved;
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

    public String appVersionNameAndCode() {
        return "app version name:'" +
            (StringUtil.isEmpty(getApplicationVersionName()) ? "???" : getApplicationVersionName()) + "'" +
            ", version code:'" + getApplicationVersionCode() + "'";

    }
}