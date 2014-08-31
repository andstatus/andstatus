package org.andstatus.app.data;

import android.text.TextUtils;

import org.andstatus.app.util.MyLog;

import java.net.URL;

public enum ContentType {
    IMAGE(2),
    TEXT(3),
    UNKNOWN(0);
    
    private static final String TAG = ContentType.class.getSimpleName();
    
    private final long code;
    
    ContentType(long code) {
        this.code = code;
    }
    
    public String save() {
        return Long.toString(code);
    }
    
    /**
     * @see http://stackoverflow.com/questions/3571223/how-do-i-get-the-file-extension-of-a-file-in-java
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
    public static ContentType load(String strCode) {
        try {
            return load(Long.parseLong(strCode));
        } catch (NumberFormatException e) {
            MyLog.v(TAG, "Error converting '" + strCode + "'", e);
        }
        return UNKNOWN;
    }
    
    public static ContentType load( long code) {
        for(ContentType val : values()) {
            if (val.code == code) {
                return val;
            }
        }
        return UNKNOWN;
    }
    
    public static ContentType fromUrl(URL url, ContentType defaultValue) {
        ContentType val = defaultValue == null ? UNKNOWN : defaultValue;
        if (url != null) {
            String extension = ContentType.getExtension(url.getFile());
            if( TextUtils.isEmpty(extension)) {
                return val;
            } 
            if ("jpg.jpeg.png.gif".contains(extension)) {
                val = IMAGE;
            } else if ("txt.html".contains(extension)) {
                val = TEXT;
            }
        } 
        return val;
    }

    public static ContentType fromUrl(URL url, String defaultValue) {
        if (TextUtils.isEmpty(defaultValue) || url == null) {
            return UNKNOWN;
        } else if (defaultValue.startsWith("image")) {
            return IMAGE;
        } else if (defaultValue.startsWith("text")) {
            return TEXT;
        }
        return fromUrl(url, UNKNOWN);
    }
    
}
