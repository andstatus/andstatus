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
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;

import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtil;
import org.andstatus.app.util.UriUtils;

public enum MyContentType {
    IMAGE("image/*", 2, 1),
    ANIMATED_IMAGE("image/*", 6, 2),
    VIDEO("video/*", 4, 3),
    TEXT("text/*", 3, 4),
    APPLICATION("application/*", 5, 5),
    UNKNOWN("*/*", 0, 6);

    private static final String TAG = MyContentType.class.getSimpleName();
    public static final String APPLICATION_JSON = "application/json";

    private final long code;
    // See https://developer.mozilla.org/en-US/docs/Web/HTTP/Basics_of_HTTP/MIME_types
    public final String generalMimeType;
    public final int attachmentsSortOrder;

    @NonNull
    public static MyContentType fromPathOfSavedFile(String mediaFilePath) {
        return StringUtil.isEmpty(mediaFilePath)
                ? UNKNOWN
                : fromUri(DownloadType.UNKNOWN, null, Uri.parse(mediaFilePath), UNKNOWN.generalMimeType);
    }

    public static MyContentType fromUri(DownloadType downloadType, ContentResolver contentResolver, Uri uri,
                                        String defaultMimeType) {
        MyContentType myContentType = fromMimeType(uri2MimeType(contentResolver, uri, defaultMimeType));
        return myContentType == ANIMATED_IMAGE || downloadType != DownloadType.AVATAR
            ? myContentType
            : IMAGE;
    }

    private static MyContentType fromMimeType(String mimeType) {
        if (mimeType.startsWith("image")) {
            if (mimeType.endsWith("/gif") || mimeType.endsWith("/apng")) {
                return ANIMATED_IMAGE;
            }
            return IMAGE;
        } else if (mimeType.startsWith("video")) {
            return VIDEO;
        } else if (mimeType.startsWith("text")) {
            return TEXT;
        } else if (mimeType.startsWith("application")) {
            return APPLICATION;
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

    public boolean isImage() {
        return this == IMAGE || this == ANIMATED_IMAGE;
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
        if (UriUtils.isEmpty(uri)) return getDefaultValue(defaultValue);

        if (contentResolver != null) {
            String mimeType = contentResolver.getType(uri);
            if (!isEmptyMime(mimeType)) return mimeType;
        }
        return path2MimeType(uri.getPath(), getDefaultValue(defaultValue));
    }

    private static String getDefaultValue(String defaultValue) {
        return StringUtil.isEmpty(defaultValue) ? UNKNOWN.generalMimeType : defaultValue;
    }

    public static boolean isEmptyMime(String mimeType) {
        return StringUtil.isEmpty(mimeType) || mimeType.startsWith("*");
    }

    /** @return empty string if no extension found */
    @NonNull
    public static String mimeToFileExtension(String mimeType) {
        if (isEmptyMime(mimeType)) return "";
        String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
        return StringUtil.isEmpty(extension) ? "" : extension;
    }

    @NonNull
    public static String path2MimeType(String path, @NonNull String defaultValue) {
        if (StringUtil.isEmpty(path)) return defaultValue;
        String fileExtension = MimeTypeMap.getFileExtensionFromUrl(path);
        boolean clarifyType = false;
        if (StringUtil.isEmpty(fileExtension) && defaultValue.endsWith("/*") && StringUtil.nonEmpty(path) ) {
            // Hack allowing to set actual extension after underscore
            fileExtension = MimeTypeMap.getFileExtensionFromUrl(path.replaceAll("_", "."));
            clarifyType = true;
        }
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension);
        if (clarifyType && !isEmptyMime(mimeType) && !isEmptyMime(defaultValue)) {
            int indSlash = defaultValue.indexOf("/");
            if (indSlash >= 0 && !mimeType.startsWith(defaultValue.substring(0, indSlash + 1))) {
                mimeType = "";
            }
        }
        return isEmptyMime(mimeType) ? defaultValue : mimeType;
    }

    public boolean getDownloadMediaOfThisType() {
        switch (this) {
            case IMAGE:
            case ANIMATED_IMAGE:
                return MyPreferences.getDownloadAndDisplayAttachedImages();
            case VIDEO:
                return MyPreferences.getDownloadAttachedVideo();
            default:
                return false;
        }
    }
}
