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

package org.andstatus.app.util;

import android.text.TextUtils;

import org.andstatus.app.data.DbUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class FileDescriptorUtils {
    private static final String TAG = FileDescriptorUtils.class.getSimpleName();
    
    private FileDescriptorUtils() {
        // Empty
    }
    
    public static JSONObject getJSONObject(FileDescriptor fileDescriptor) {
        JSONObject jso = null;
        String fileString = getString(fileDescriptor);
        if (!TextUtils.isEmpty(fileString)) {
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

    public static String getString(FileDescriptor fileDescriptor) {
        // reads an UTF-8 string resource - API 9 required
        //return new String(getResource(id, context), Charset.forName("UTF-8"));
        return new String(getBytes(fileDescriptor));
    }

    public static byte[] getBytes(FileDescriptor fileDescriptor) {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        if (fileDescriptor != null) {
            InputStream is = new FileInputStream(fileDescriptor);
            byte[] readBuffer = new byte[4 * 1024];
            try {
                int read;
                do {
                    read = is.read(readBuffer, 0, readBuffer.length);
                    if(read == -1) {
                        break;
                    }
                    bout.write(readBuffer, 0, read);
                } while(true);
                return bout.toByteArray();
            } catch (IOException e) {
                MyLog.v(TAG, e);
            } finally {
                DbUtils.closeSilently(is);
            }
        }
        return new byte[0];
    }
}
