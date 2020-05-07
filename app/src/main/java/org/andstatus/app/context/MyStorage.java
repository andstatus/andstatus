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

package org.andstatus.app.context;

import android.os.Environment;

import androidx.annotation.NonNull;

import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.StringUtil;
import org.andstatus.app.util.TriState;

import java.io.File;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.andstatus.app.context.MyContextHolder.myContextHolder;

/**
 * Utility class grouping references to Storage
 * @author yvolk@yurivolkov.com
 */
public class MyStorage {
    public static final String TEMP_FILENAME_PREFIX = "temp_";
    public static final int FILE_CHUNK_SIZE = 250000;
    private static final String TAG = MyStorage.class.getSimpleName();

    /** Standard directory in which to place databases */
    public static final String DIRECTORY_DATABASES = "databases";
    public static final String DIRECTORY_DOWNLOADS = "downloads";
    public static final String DIRECTORY_LOGS = "logs";

    private MyStorage() {
        // Non instantiable
    }

    public static TriState isApplicationDataCreated() {
        return SharedPreferencesUtil.areDefaultPreferenceValuesSet();
    }

    public static File getDataFilesDir(String type) {
        return getDataFilesDir(type, TriState.UNKNOWN);
    }

    /**
     * This function works just like {@link android.content.Context#getExternalFilesDir
     * Context.getExternalFilesDir},
     * but it takes {@link MyPreferences#KEY_USE_EXTERNAL_STORAGE} into account,
     * so it returns directory either on internal or external storage.
     *
     * @param type The type of files directory to return.  May be null for
     * the root of the files directory or one of
     * the following Environment constants for a subdirectory:
     * {@link android.os.Environment#DIRECTORY_PICTURES Environment.DIRECTORY_...} (since API 8),
     * {@link MyStorage#DIRECTORY_DATABASES}
     * @param useExternalStorage if not UNKNOWN, use this value instead of stored in preferences
     *                           as {@link MyPreferences#KEY_USE_EXTERNAL_STORAGE}
     *
     * @return directory, already created for you OR null in a case of an error
     * @see <a href="http://developer.android.com/guide/topics/data/data-storage.html#filesExternal">filesExternal</a>
     */
    public static File getDataFilesDir(String type, @NonNull TriState useExternalStorage) {
        return getDataFilesDir(type, useExternalStorage, !DIRECTORY_LOGS.equals(type));
    }

    public static File getDataFilesDir(String type, @NonNull TriState useExternalStorage, boolean logged) {
        final String method = "getDataFilesDir";
        File dir = null;
        StringBuilder textToLog = new StringBuilder();
        MyContext myContext = myContextHolder.getNow();
        if (myContext.context() == null) {
            textToLog.append("No android.content.Context yet");
        } else {
            if (isStorageExternal(useExternalStorage)) {
                if (isWritableExternalStorageAvailable(textToLog)) {
                    try {
                        dir = myContext.context().getExternalFilesDir(type);
                    } catch (NullPointerException e) {
                        // I noticed this exception once, but that time it was related to SD card malfunction...
                        if (logged) {
                            MyLog.e(TAG, method, e);
                        }
                    }
                }
            } else {
                dir = myContext.context().getFilesDir();
                if (!StringUtil.isEmpty(type)) {
                    dir = new File(dir, type);
                }
            }
            if (dir != null && !dir.exists()) {
                try {
                    //noinspection ResultOfMethodCallIgnored
                    dir.mkdirs();
                } catch (Exception e) {
                    if (logged) {
                        MyLog.e(TAG, method + "; Error creating directory", e);
                    }
                } finally {
                    if (!dir.exists()) {
                        textToLog.append("Could not create '" + dir.getPath() + "'");
                        dir = null;
                    }
                }
            }
        }
        if (logged && textToLog.length() > 0) {
            MyLog.i(TAG, method + "; " + textToLog);
        }
        return dir;
    }

    public static boolean isWritableExternalStorageAvailable(StringBuilder textToLog) {
        String state = Environment.getExternalStorageState();
        boolean available = false;
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            available = true;
        } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            if (textToLog != null) {
                textToLog.append("We can only read External storage");
            }
        } else {
            if (textToLog != null) {
                textToLog.append("Error accessing External storage, state='" + state + "'");
            }
        }
        return available;
    }

    public static boolean isStorageExternal() {
        return isStorageExternal(TriState.UNKNOWN);
    }

    public static boolean isStorageExternal(@NonNull TriState useExternalStorage) {
        return useExternalStorage.toBoolean(
                SharedPreferencesUtil.getBoolean(MyPreferences.KEY_USE_EXTERNAL_STORAGE, false));
    }

    public static File getDatabasePath(String name) {
        return getDatabasePath(name, TriState.UNKNOWN);
    }

    /**
     * Extends {@link android.content.ContextWrapper#getDatabasePath(String)}
     * @param name The name of the database for which you would like to get its path.
     * @param useExternalStorage if not UNKNOWN, use this value instead of stored in preferences
     *                           as {@link MyPreferences#KEY_USE_EXTERNAL_STORAGE}
     */
    public static File getDatabasePath(String name, @NonNull TriState useExternalStorage) {
        File dbDir = getDataFilesDir(DIRECTORY_DATABASES, useExternalStorage);
        File dbAbsolutePath = null;
        if (dbDir != null) {
            dbAbsolutePath = new File(dbDir.getPath() + "/" + name);
        }
        return dbAbsolutePath;
    }

    /**
     * Simple check that allows to prevent data access errors
     */
    public static boolean isDataAvailable() {
        return getDataFilesDir(null) != null;
    }

    public static boolean isTempFile(File file) {
        return file.getName().startsWith(TEMP_FILENAME_PREFIX);
    }

    public static Stream<File> getMediaFiles() {
        return Arrays.stream(getDataFilesDir(DIRECTORY_DOWNLOADS).listFiles()).filter(File::isFile);
    }

    public static File newTempFile(String filename) {
        return newMediaFile(TEMP_FILENAME_PREFIX + filename);
    }

    public static File newMediaFile(String filename) {
        File folder = getDataFilesDir(DIRECTORY_DOWNLOADS);
        if (!folder.exists()) folder.mkdir();
        return new File(folder, filename);
    }

    public static long getMediaFilesSize() {
        return getMediaFiles().mapToLong(File::length).sum();
    }

    public static File getLogsDir(boolean logged) {
        return getDataFilesDir(DIRECTORY_LOGS, TriState.UNKNOWN, logged);
    }
}
