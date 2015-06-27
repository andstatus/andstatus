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

package org.andstatus.app.data;

import android.net.Uri;
import android.text.TextUtils;

import org.andstatus.app.util.MyLog;

import java.net.URL;

public enum MyContentType {
    IMAGE(2),
    TEXT(3),
    UNKNOWN(0);
    
    public static final String DEFAULT_MIME_TYPE = "image/jpeg";

    private static final String TAG = MyContentType.class.getSimpleName();
    
    private final long code;
    
    MyContentType(long code) {
        this.code = code;
    }
    
    public String save() {
        return Long.toString(code);
    }
    
    /**
     * see http://stackoverflow.com/questions/3571223/how-do-i-get-the-file-extension-of-a-file-in-java
     * @return empty string if not found
     */
    public static String getExtension(String filename) {
        String extension = "";
        int i = filename.lastIndexOf('.');
        int p = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        if (i > p) {
            extension = filename.substring(i+1);
        }
        return extension;
    }

    /**
     * Returns the enum or UNKNOWN
     */
    public static MyContentType load(String strCode) {
        try {
            return load(Long.parseLong(strCode));
        } catch (NumberFormatException e) {
            MyLog.v(TAG, "Error converting '" + strCode + "'", e);
        }
        return UNKNOWN;
    }
    
    public static MyContentType load( long code) {
        for(MyContentType val : values()) {
            if (val.code == code) {
                return val;
            }
        }
        return UNKNOWN;
    }
    
    public static MyContentType fromUrl(URL url, MyContentType defaultValue) {
        MyContentType val = defaultValue == null ? UNKNOWN : defaultValue;
        if (url != null) {
            String extension = MyContentType.getExtension(url.getPath());
            if( TextUtils.isEmpty(extension)) {
                return val;
            } 
            if ("jpg.jpeg.png.gif".contains(extension)) {
                val = IMAGE;
            } else if ("txt.html.htm".contains(extension)) {
                val = TEXT;
            }
        } 
        return val;
    }

    public static MyContentType fromUrl(URL url, String defaultValue) {
        if (TextUtils.isEmpty(defaultValue)) {
            // Nothing to do
        } else if (defaultValue.startsWith("image")) {
            return IMAGE;
        } else if (defaultValue.startsWith("text")) {
            return TEXT;
        } 
        return fromUrl(url, UNKNOWN);
    }

    public static String uri2MimeType(Uri uri, String defaultValue) {
        String filename = uri == null ? null : uri.getPath();
        return filename2MimeType(filename, defaultValue);
    }
    
    public static String filename2MimeType(String filename, String defaultValue) {
        String mimeType = defaultValue == null ? DEFAULT_MIME_TYPE : defaultValue;
        if (filename != null) {
            String extension = MyContentType.getExtension(filename);
            if( TextUtils.isEmpty(extension)) {
                return mimeType;
            } 
            if ("jpg.jpeg.png.gif".contains(extension)) {
                mimeType = "image/" + extension;
            } else if ("txt.html.htm".contains(extension)) {
                mimeType = "text/" + extension;
            }
        } 
        return mimeType;
    }
    
}
