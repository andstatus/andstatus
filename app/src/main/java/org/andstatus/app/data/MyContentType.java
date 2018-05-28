/*
 * Copyright (C) 2014-2018 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.content.ContentResolver;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.webkit.MimeTypeMap;

import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtils;
import org.andstatus.app.util.UriUtils;

public enum MyContentType {
    IMAGE("image/*", 2, 1),
    TEXT("text/*", 3, 3),
    VIDEO("video/*", 4, 2),
    UNKNOWN("*/*", 0, 4);
    
    private static final String TAG = MyContentType.class.getSimpleName();
    
    private final long code;
    public final String generalMimeType;
    public final int attachmentsSortOrder;


    @NonNull
    public static MyContentType fromPathOfSavedFile(String mediaFilePath) {
        return StringUtils.isEmpty(mediaFilePath)
                ? UNKNOWN
                : fromUri(DownloadType.UNKNOWN, null, Uri.parse(mediaFilePath), UNKNOWN.generalMimeType);
    }

    public static MyContentType fromUri(DownloadType downloadType, ContentResolver contentResolver, Uri uri,
                                        String defaultMimeType) {
        if (downloadType == DownloadType.AVATAR) return MyContentType.IMAGE;

        String mimeType = uri2MimeType(contentResolver, uri, defaultMimeType);
        if (mimeType.startsWith("image")) {
            return IMAGE;
        } else if (mimeType.startsWith("video")) {
            return VIDEO;
        } else if (mimeType.startsWith("text")) {
            return TEXT;
        } else {
            return UNKNOWN;
        }
    }

    /** Returns the enum or UNKNOWN */
    @NonNull
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

    MyContentType(String generalMimeType, long code, int attachmentsSortOrder) {
        this.generalMimeType = generalMimeType;
        this.code = code;
        this.attachmentsSortOrder = attachmentsSortOrder;
    }

    public String save() {
        return Long.toString(code);
    }

    @NonNull
    public static String uri2MimeType(ContentResolver contentResolver, Uri uri) {
        return uri2MimeType(contentResolver, uri, "");
    }

    @NonNull
    public static String uri2MimeType(ContentResolver contentResolver, Uri uri, String defaultValue) {
        if (contentResolver != null && !UriUtils.isEmpty(uri)) {
            String mimeType = contentResolver.getType(uri);
            if (!isEmptyMime(mimeType)) return mimeType;
        }
        return path2MimeType(
                uri == null ? null : uri.getPath(),
                StringUtils.isEmpty(defaultValue) ? UNKNOWN.generalMimeType : defaultValue
        );
    }

    public static boolean isEmptyMime(String mimeType) {
        return StringUtils.isEmpty(mimeType) || mimeType.startsWith("*");
    }

    /** @return empty string if no extension found */
    @NonNull
    public static String mimeToFileExtension(String mimeType) {
        if (isEmptyMime(mimeType)) return "";
        String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
        return StringUtils.isEmpty(extension) ? "" : extension;
    }

    @NonNull
    private static String path2MimeType(String path, @NonNull String defaultValue) {
        if (StringUtils.isEmpty(path)) return defaultValue;
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(path));
        return isEmptyMime(mimeType) ? defaultValue : mimeType;
    }
}
