package org.andstatus.app.data;

import org.andstatus.app.util.MyLog;

public enum DownloadType {
    AVATAR(1),
    IMAGE(2),
    TEXT(3),
    UNKNOWN(0);

    private static final String TAG = DownloadType.class.getSimpleName();
    
    private long code;

    DownloadType(long code) {
        this.code = code;
    }

    public String save() {
        return Long.toString(code);
    }
    
    public static DownloadType load(String strCode) {
        try {
            return load(Long.parseLong(strCode));
        } catch (NumberFormatException e) {
            MyLog.v(TAG, "Error converting '" + strCode + "'", e);
        }
        return UNKNOWN;
    }
    
    public static DownloadType load( long code) {
        for(DownloadType val : values()) {
            if (val.code == code) {
                return val;
            }
        }
        return UNKNOWN;
    }
    
}
