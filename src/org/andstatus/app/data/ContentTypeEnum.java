package org.andstatus.app.data;

import android.text.TextUtils;

import java.net.URL;

public enum ContentTypeEnum {
    IMAGE("image"),
    TEXT("text"),
    UNKNOWN("unknown");
    
    private final String code;
    ContentTypeEnum(String code) {
        this.code = code;
    }
    
    /**
     * String to be used for persistence
     */
    public String save() {
        return code;
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
    public static ContentTypeEnum load(String strCode) {
        for (ContentTypeEnum tt : ContentTypeEnum.values()) {
            if (tt.code.equals(strCode)) {
                return tt;
            }
        }
        return UNKNOWN;
    }
    
    public static ContentTypeEnum fromUrl(URL url, ContentTypeEnum defaultContentType) {
        ContentTypeEnum contentType = defaultContentType == null ? UNKNOWN : defaultContentType;
        if (url != null) {
            String extension = ContentTypeEnum.getExtension(url.getFile());
            if( TextUtils.isEmpty(extension)) {
                return contentType;
            } 
            if ("jpg.jpeg.png.gif".contains(extension)) {
                contentType = IMAGE;
            } else if ("txt.html".contains(extension)) {
                contentType = TEXT;
            }
        } 
        return contentType;
    }

    public static ContentTypeEnum fromUrl(URL url, String defaultContentType) {
        if (TextUtils.isEmpty(defaultContentType) || url == null) {
            return UNKNOWN;
        } else if (defaultContentType.startsWith("image")) {
            return IMAGE;
        } else if (defaultContentType.startsWith("text")) {
            return TEXT;
        }
        return fromUrl(url, UNKNOWN);
    }
    
}
