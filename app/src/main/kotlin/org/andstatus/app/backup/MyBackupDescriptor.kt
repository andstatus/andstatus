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
package org.andstatus.app.backup

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import androidx.documentfile.provider.DocumentFile
import org.andstatus.app.context.MyContextHolder.Companion.myContextHolder
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.database.DatabaseCreator
import org.andstatus.app.util.DocumentFileUtils
import org.andstatus.app.util.FileDescriptorUtils
import org.andstatus.app.util.JsonUtils
import org.andstatus.app.util.MyLog
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.FileDescriptor
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

class MyBackupDescriptor private constructor(private val progressLogger: ProgressLogger) {
    private var backupSchemaVersion = BACKUP_SCHEMA_VERSION_UNKNOWN
    private var appInstanceName: String = ""
    private var applicationVersionCode = 0
    private var applicationVersionName: String = ""
    private var createdDate: Long = 0
    private var saved = false
    private var fileDescriptor: FileDescriptor? = null
    private var docDescriptor: DocumentFile? = null
    private var accountsCount: Long = 0
    fun setJson(jso: JSONObject) {
        backupSchemaVersion = jso.optInt(
            KEY_BACKUP_SCHEMA_VERSION,
            backupSchemaVersion
        )
        createdDate = jso.optLong(KEY_CREATED_DATE, createdDate)
        saved = createdDate != 0L
        appInstanceName = JsonUtils.optString(jso, KEY_APP_INSTANCE_NAME, appInstanceName)
        applicationVersionCode = jso.optInt(KEY_APPLICATION_VERSION_CODE, applicationVersionCode)
        applicationVersionName = JsonUtils.optString(jso, KEY_APPLICATION_VERSION_NAME, applicationVersionName)
        accountsCount = jso.optLong(KEY_ACCOUNTS_COUNT, accountsCount)
        if (backupSchemaVersion != BACKUP_SCHEMA_VERSION) {
            try {
                MyLog.w(TAG, "Bad backup descriptor: " + jso.toString(2))
            } catch (e: JSONException) {
                MyLog.d(TAG, "Bad backup descriptor: " + jso.toString(), e)
            }
        }
    }

    private fun setEmptyFields(context: Context) {
        backupSchemaVersion = BACKUP_SCHEMA_VERSION
        val pm = context.getPackageManager()
        val pi: PackageInfo = try {
            pm.getPackageInfo(context.getPackageName(), 0)
        } catch (e: PackageManager.NameNotFoundException) {
            throw IOException(e)
        }
        appInstanceName = MyPreferences.getAppInstanceName()
        applicationVersionCode = pi.versionCode
        applicationVersionName = pi.versionName ?: pi.versionCode.toString()
    }

    fun getBackupSchemaVersion(): Int {
        return backupSchemaVersion
    }

    fun getCreatedDate(): Long {
        return createdDate
    }

    fun getApplicationVersionCode(): Int {
        return applicationVersionCode
    }

    fun getApplicationVersionName(): String {
        return applicationVersionName
    }

    fun isEmpty(): Boolean {
        return fileDescriptor == null && docDescriptor == null
    }

    fun save(context: Context) {
        if (isEmpty()) {
            throw FileNotFoundException("MyBackupDescriptor is empty")
        }
        try {
            docDescriptor?.let {
                context.getContentResolver().openOutputStream(it.getUri())?.use { outputStream ->
                    if (createdDate == 0L) createdDate = System.currentTimeMillis()
                    writeStringToStream(toJson().toString(2), outputStream)
                    saved = true
                }
            } ?: FileOutputStream(fileDescriptor)
        } catch (e: JSONException) {
            throw IOException(e)
        }
    }

    private fun toJson(): JSONObject {
        val jso = JSONObject()
        if (isEmpty()) return jso
        try {
            jso.put(KEY_BACKUP_SCHEMA_VERSION, backupSchemaVersion)
            jso.put(KEY_APP_INSTANCE_NAME, appInstanceName)
            jso.put(KEY_CREATED_DATE, createdDate)
            jso.put(KEY_CREATED_DEVICE, MyPreferences.getDeviceBrandModelString())
            jso.put(KEY_APPLICATION_VERSION_CODE, applicationVersionCode)
            jso.put(KEY_APPLICATION_VERSION_NAME, applicationVersionName)
            jso.put(KEY_ACCOUNTS_COUNT, accountsCount)
        } catch (e: JSONException) {
            MyLog.w(this, "toJson", e)
        }
        return jso
    }

    private fun writeStringToStream(string: String, outputStream: OutputStream) {
        val method = "writeStringToFileDescriptor"
        try {
            BufferedWriter(OutputStreamWriter(outputStream, StandardCharsets.UTF_8)).use { out -> out.write(string) }
        } catch (e: IOException) {
            MyLog.d(this, method, e)
            throw FileNotFoundException(method + "; " + e.localizedMessage)
        }
    }

    override fun toString(): String {
        return "MyBackupDescriptor " + toJson().toString()
    }

    fun saved(): Boolean {
        return saved
    }

    fun getAccountsCount(): Long {
        return accountsCount
    }

    fun setAccountsCount(accountsCount: Long) {
        this.accountsCount = accountsCount
        progressLogger.logProgress("Accounts backed up:$accountsCount")
    }

    fun getLogger(): ProgressLogger {
        return progressLogger
    }

    fun appVersionNameAndCode(): String {
        return "app version name:'" +
            (if (getApplicationVersionName().isEmpty()) "???" else getApplicationVersionName()) + "'" +
            ", version code:'" + getApplicationVersionCode() + "'"
    }

    companion object {
        private val TAG: Any = MyBackupDescriptor::class.java
        const val BACKUP_SCHEMA_VERSION_UNKNOWN = -1

        /** Depends, in particular, on @[DatabaseCreator.DATABASE_VERSION]
         * v.7 2017-11-04 app.v.36 Moving to ActivityStreams data model
         * v.6 2016-11-27 app.v.31 database schema changed
         * v.5 2016-05-22 app.v.27 database schema changed
         * v.4 2016-02-28 app.v.23 database schema changed
         */
        const val BACKUP_SCHEMA_VERSION = 7
        val KEY_BACKUP_SCHEMA_VERSION: String = "backup_schema_version"
        val KEY_APP_INSTANCE_NAME: String = MyPreferences.KEY_APP_INSTANCE_NAME
        val KEY_CREATED_DATE: String = "created_date"
        val KEY_CREATED_DEVICE: String = "created_device"
        val KEY_APPLICATION_VERSION_CODE: String = "app_version_code"
        val KEY_APPLICATION_VERSION_NAME: String = "app_version_name"
        val KEY_ACCOUNTS_COUNT: String = "accounts_count"
        fun getEmpty(): MyBackupDescriptor {
            return MyBackupDescriptor(ProgressLogger.getEmpty(""))
        }

        fun fromOldParcelFileDescriptor(
            parcelFileDescriptor: ParcelFileDescriptor?,
            progressLogger: ProgressLogger
        ): MyBackupDescriptor {
            val myBackupDescriptor = MyBackupDescriptor(progressLogger)
            if (parcelFileDescriptor != null) {
                myBackupDescriptor.fileDescriptor = parcelFileDescriptor.fileDescriptor
                val jso = FileDescriptorUtils.getJSONObject(parcelFileDescriptor.fileDescriptor)
                myBackupDescriptor.setJson(jso)
            }
            return myBackupDescriptor
        }

        fun fromOldDocFileDescriptor(
            context: Context, parcelFileDescriptor: DocumentFile?,
            progressLogger: ProgressLogger
        ): MyBackupDescriptor {
            val myBackupDescriptor = MyBackupDescriptor(progressLogger)
            if (parcelFileDescriptor != null) {
                myBackupDescriptor.docDescriptor = parcelFileDescriptor
                myBackupDescriptor.setJson(DocumentFileUtils.getJSONObject(context, parcelFileDescriptor))
            }
            return myBackupDescriptor
        }

        fun fromEmptyParcelFileDescriptor(
            parcelFileDescriptor: ParcelFileDescriptor,
            progressLoggerIn: ProgressLogger
        ): MyBackupDescriptor {
            val myBackupDescriptor = MyBackupDescriptor(progressLoggerIn)
            myBackupDescriptor.fileDescriptor = parcelFileDescriptor.getFileDescriptor()
            myBackupDescriptor.setEmptyFields(myContextHolder.getNow().baseContext)
            myBackupDescriptor.backupSchemaVersion = BACKUP_SCHEMA_VERSION
            return myBackupDescriptor
        }

        fun fromEmptyDocumentFile(
            context: Context, documentFile: DocumentFile?,
            progressLoggerIn: ProgressLogger
        ): MyBackupDescriptor {
            val myBackupDescriptor = MyBackupDescriptor(progressLoggerIn)
            myBackupDescriptor.docDescriptor = documentFile
            myBackupDescriptor.setEmptyFields(context)
            return myBackupDescriptor
        }
    }
}
