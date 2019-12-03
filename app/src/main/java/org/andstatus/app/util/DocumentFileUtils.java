/*
 * Copyright (C) 2019 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import org.andstatus.app.R;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

public class DocumentFileUtils {
    private static final String TAG = DocumentFileUtils.class.getSimpleName();

    private DocumentFileUtils() {
        // Empty
    }

    public static JSONObject getJSONObject(Context context, DocumentFile fileDescriptor) {
        JSONObject jso = null;
        String fileString = uri2String(context, fileDescriptor.getUri());
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

    private static String uri2String(Context context, Uri uri) {
        final int BUFFER_LENGTH = 10000;
        try (InputStream is = context.getContentResolver().openInputStream(uri);
             Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8);) {
            char[] buffer = new char[BUFFER_LENGTH];
            StringBuilder builder = new StringBuilder();
            int count;
            while ((count = reader.read(buffer)) != -1) {
                builder.append(buffer, 0, count);
            }
            return builder.toString();
        } catch (Exception e) {
            String msg = "Error while reading " + context.getText(R.string.app_name) +
                    " settings from " + uri + "\n" + e.getMessage();
            Log.w(TAG, msg, e);
        }
        return "";
    }

    public static JSONArray getJSONArray(Context context, DocumentFile fileDescriptor) {
        JSONArray jso = null;
        String fileString = uri2String(context, fileDescriptor.getUri());
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

    /** Reads up to 'size' bytes, starting from 'offset' */
    public static byte[] getBytes(Context context, DocumentFile file, int offset, int size) throws IOException {
        if (file == null) return new byte[0];

        try (InputStream is = context.getContentResolver().openInputStream(file.getUri())) {
            return FileUtils.getBytes(is, file.getUri().getPath(), offset, size);
        }
    }
}
