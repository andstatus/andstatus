/*
 * Copyright (c) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.context

import android.os.Environment
import org.andstatus.app.context.MyContextHolder.Companion.myContextHolder
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.SharedPreferencesUtil
import org.andstatus.app.util.TriState
import java.io.File
import java.util.*
import java.util.stream.Stream

/**
 * Utility class grouping references to Storage
 * @author yvolk@yurivolkov.com
 */
object MyStorage {
    val TEMP_FILENAME_PREFIX: String = "temp_"
    const val FILE_CHUNK_SIZE = 250000
    private val TAG: String = MyStorage::class.simpleName!!

    /** Standard directory in which to place databases  */
    val DIRECTORY_DATABASES: String = "databases"
    val DIRECTORY_DOWNLOADS: String = "downloads"
    val DIRECTORY_LOGS: String = "logs"
    fun isApplicationDataCreated(): TriState {
        return SharedPreferencesUtil.areDefaultPreferenceValuesSet()
    }

    fun getDataFilesDir(type: String?): File? {
        return getDataFilesDir(type, TriState.UNKNOWN)
    }

    /**
     * This function works just like [ Context.getExternalFilesDir][android.content.Context.getExternalFilesDir],
     * but it takes [MyPreferences.KEY_USE_EXTERNAL_STORAGE] into account,
     * so it returns directory either on internal or external storage.
     *
     * @param type The type of files directory to return.  May be null for
     * the root of the files directory or one of
     * the following Environment constants for a subdirectory:
     * [Environment.DIRECTORY_...][android.os.Environment.DIRECTORY_PICTURES] (since API 8),
     * [MyStorage.DIRECTORY_DATABASES]
     * @param useExternalStorage if not UNKNOWN, use this value instead of stored in preferences
     * as [MyPreferences.KEY_USE_EXTERNAL_STORAGE]
     *
     * @return directory, already created for you OR null in a case of an error
     * @see [filesExternal](http://developer.android.com/guide/topics/data/data-storage.html.filesExternal)
     */
    fun getDataFilesDir(type: String?, useExternalStorage: TriState): File? {
        return getDataFilesDir(type, useExternalStorage, DIRECTORY_LOGS != type)
    }

    private fun getDataFilesDir(type: String?, useExternalStorage: TriState, logged: Boolean): File? {
        val method = "getDataFilesDir"
        var dir: File? = null
        val textToLog = StringBuilder()
        val myContext: MyContext = myContextHolder.getNow()
        if (myContext.isEmpty) {
            textToLog.append("No android.content.Context yet")
        } else {
            if (isStorageExternal(useExternalStorage)) {
                if (isWritableExternalStorageAvailable(textToLog)) {
                    try {
                        dir = myContext.baseContext.getExternalFilesDir(type)
                    } catch (e: NullPointerException) {
                        // I noticed this exception once, but that time it was related to SD card malfunction...
                        if (logged) {
                            MyLog.e(TAG, "$method getExternalFilesDir for $type", e)
                        }
                    }
                }
            } else {
                dir = myContext.baseContext.filesDir
                if (!type.isNullOrEmpty()) {
                    dir = File(dir, type)
                }
            }
            if (dir != null && !dir.exists()) {
                try {
                    dir.mkdirs()
                } catch (e: Exception) {
                    if (logged) {
                        MyLog.w(TAG, "$method; Error creating directory", e)
                    }
                } finally {
                    if (!dir.exists()) {
                        textToLog.append("Could not create '" + dir.path + "'")
                        dir = null
                    }
                }
            }
        }
        if (logged && textToLog.length > 0) {
            MyLog.i(TAG, "$method; $textToLog")
        }
        return dir
    }

    fun isWritableExternalStorageAvailable(textToLog: StringBuilder?): Boolean {
        val state = Environment.getExternalStorageState()
        var available = false
        if (Environment.MEDIA_MOUNTED == state) {
            available = true
        } else if (Environment.MEDIA_MOUNTED_READ_ONLY == state) {
            textToLog?.append("We can only read External storage")
        } else {
            textToLog?.append("Error accessing External storage, state='$state'")
        }
        return available
    }

    fun isStorageExternal(): Boolean {
        return isStorageExternal(TriState.UNKNOWN)
    }

    private fun isStorageExternal(useExternalStorage: TriState): Boolean {
        return useExternalStorage.toBoolean(
            SharedPreferencesUtil.getBoolean(MyPreferences.KEY_USE_EXTERNAL_STORAGE, false)
        )
    }

    fun getDatabasePath(name: String?): File? {
        return getDatabasePath(name, TriState.UNKNOWN)
    }

    /**
     * Extends [android.content.ContextWrapper.getDatabasePath]
     * @param name The name of the database for which you would like to get its path.
     * @param useExternalStorage if not UNKNOWN, use this value instead of stored in preferences
     * as [MyPreferences.KEY_USE_EXTERNAL_STORAGE]
     */
    fun getDatabasePath(name: String?, useExternalStorage: TriState): File? {
        val dbDir = getDataFilesDir(DIRECTORY_DATABASES, useExternalStorage)
        var dbAbsolutePath: File? = null
        if (dbDir != null) {
            dbAbsolutePath = File(dbDir.path + "/" + name)
        }
        return dbAbsolutePath
    }

    /**
     * Simple check that allows to prevent data access errors
     */
    fun isDataAvailable(): Boolean {
        return getDataFilesDir(null) != null
    }

    fun isTempFile(file: File): Boolean {
        return file.getName().startsWith(TEMP_FILENAME_PREFIX)
    }

    fun getMediaFiles(): Stream<File> = getDataFilesDir(DIRECTORY_DOWNLOADS)?.let {
        Arrays.stream(it.listFiles()).filter { obj: File -> obj.isFile() }
    } ?: Stream.empty()

    fun newTempFile(filename: String): File {
        return newMediaFile(TEMP_FILENAME_PREFIX + filename)
    }

    fun newMediaFile(filename: String): File {
        val folder = getDataFilesDir(DIRECTORY_DOWNLOADS)
        return folder?.let {
            if (!it.exists()) it.mkdir()
            return File(it, filename)
        } ?: throw IllegalStateException("Couldn't create media file")
    }

    fun getMediaFilesSize(): Long {
        return getMediaFiles().mapToLong { obj: File -> obj.length() }?.sum() ?: 0
    }

    fun getLogsDir(logged: Boolean): File? {
        return getDataFilesDir(DIRECTORY_LOGS, TriState.UNKNOWN, logged)
    }
}
