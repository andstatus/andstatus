/*
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

package org.andstatus.app.util;

import org.andstatus.app.data.DbUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Arrays;

public class FileUtils {
    private static final String TAG = FileUtils.class.getSimpleName();
    private static final int BUFFER_LENGTH = 4 * 1024;

    private FileUtils() {
        // Empty
    }
    
    public static JSONArray getJSONArray(File file) throws IOException {
        JSONArray jso = null;
        String fileString = utf8File2String(file);
        if (!StringUtils.isEmpty(fileString)) {
            try {
                jso = new JSONArray(fileString);
            } catch (JSONException e) {
                MyLog.v(TAG, e);
                jso = null;
            }
        }
        if (jso == null) {
            jso = new JSONArray();
        }
        return jso;
    }
    
    public static JSONObject getJSONObject(File file) throws IOException {
        JSONObject jso = null;
        String fileString = utf8File2String(file);
        if (!StringUtils.isEmpty(fileString)) {
            try {
                jso = new JSONObject(fileString);
            } catch (JSONException e) {
                MyLog.v(TAG, e);
                jso = null;
            }
        }
        if (jso == null) {
            jso = new JSONObject();
        }
        return jso;
    }

    private static String utf8File2String(File file) throws IOException {
        return new String(getBytes(file), Charset.forName("UTF-8"));
    }

    /** Reads the whole file */
    public static byte[] getBytes(File file) throws IOException {
        if (file != null) {
            try (InputStream is = new FileInputStream(file)) {
                return getBytes(is);
            }
        }
        return new byte[0];
    }

    /** Read the stream into an array; the stream is not closed **/
    public static byte[] getBytes(InputStream is) throws IOException {
        if (is != null) {
            byte[] readBuffer = new byte[BUFFER_LENGTH];
            try (ByteArrayOutputStream bout = new ByteArrayOutputStream()) {
                int read;
                do {
                    read = is.read(readBuffer, 0, readBuffer.length);
                    if(read == -1) {
                        break;
                    }
                    bout.write(readBuffer, 0, read);
                } while(true);
                return bout.toByteArray();
            }
        }
        return new byte[0];
    }
    
    /** Reads up to 'size' bytes, starting from 'offset' */
    public static byte[] getBytes(File file, int offset, int size) throws IOException {
        if (file != null) {
            InputStream is = new FileInputStream(file);
            byte[] readBuffer = new byte[size];
            try {
                long bytesSkipped = is.skip(offset);
                if (bytesSkipped < offset) {
                    throw new FileNotFoundException("Skipped only " + bytesSkipped 
                            + " of " + offset + " bytes in file='" + file.getAbsolutePath() + "'");
                }
                int bytesRead = is.read(readBuffer, 0, size);
                if (bytesRead == readBuffer.length) {
                    return readBuffer;
                } else if (bytesRead > 0) {
                    return Arrays.copyOf(readBuffer, bytesRead);
                }
            } finally {
                DbUtils.closeSilently(is);
            }
        }
        return new byte[0];
    }

    public static void deleteFilesRecursively(File rootDirectory) {
        if (rootDirectory == null) {
            return;
        }
        MyLog.i(TAG, "On delete all files inside '" + rootDirectory.getAbsolutePath() +"'");
        MyLog.i(TAG, "Deleted files and dirs: " + deleteFilesRecursively(rootDirectory, 1));
    }

    private static long deleteFilesRecursively(File rootDirectory, long level) {
        if (rootDirectory == null) {
            return 0;
        }
        File[] files = rootDirectory.listFiles();
        if (files == null) {
            MyLog.v(TAG, () -> "No files inside " + rootDirectory.getAbsolutePath());
            return 0;
        }
        long nDeleted = 0;
        for (File file : files) {
            if (file.isDirectory()) {
                nDeleted += deleteFilesRecursively(file, level + 1);
                if (level > 1) {
                    nDeleted += deleteAndCountFile(file);
                }
            } else {
                nDeleted += deleteAndCountFile(file);
            }
        }
        return nDeleted;
    }

    private static long deleteAndCountFile(File file) {
        long nDeleted = 0;
        if (file.delete()) {
            nDeleted++;
        } else {
            MyLog.w(TAG, "Couldn't delete " + file.getAbsolutePath());
        }
        return nDeleted;
    }

    /**
     * Accepts null argument
     */
    public static boolean exists(File file) {
        if (file == null) {
            return false;
        }
        return file.exists();
    }

    /**
     * Based on <a href="http://www.screaming-penguin.com/node/7749">Backing
     * up your Android SQLite database to the SD card</a>
     *
     * @param src
     * @param dst
     * @return true if success
     * @throws IOException
     */
    public static boolean copyFile(Object objTag, File src, File dst) throws IOException {
        long sizeIn = -1;
        long sizeCopied = 0;
        boolean ok = false;
        if (src != null && src.exists()) {
            sizeIn = src.length();
            if (!dst.createNewFile()) {
                MyLog.e(objTag, "New file was not created: '" + dst.getCanonicalPath() + "'");
            } else if (src.getCanonicalPath().compareTo(dst.getCanonicalPath()) == 0) {
                MyLog.d(objTag, "Cannot copy to itself: '" + src.getCanonicalPath() + "'");
            } else {
                try (
                        FileInputStream fileInputStream = new FileInputStream(src);
                        java.nio.channels.FileChannel inChannel = fileInputStream.getChannel();
                        FileOutputStream fileOutputStream = new FileOutputStream(dst);
                        java.nio.channels.FileChannel outChannel = fileOutputStream.getChannel();
                ) {
                    sizeCopied = inChannel.transferTo(0, inChannel.size(), outChannel);
                    ok = (sizeIn == sizeCopied);
                }
                dst.setLastModified(src.lastModified());
            }
        }
        MyLog.d(objTag, "Copied " + sizeCopied + " bytes of " + sizeIn);
        return ok;
    }
}
