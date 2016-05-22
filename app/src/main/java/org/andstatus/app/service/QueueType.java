package org.andstatus.app.service;

import android.support.annotation.NonNull;

public enum QueueType {
    CURRENT("current", "C", true),
    RETRY("retry", "R", true),
    ERROR("error", "E", false),
    TEST("test", "T", false),
    UNKNOWN("unknown", "U", false);

    private String code;
    private String acronym;
    private boolean executable;

    QueueType(String code, String acronym, boolean executable) {
        this.code = code;
        this.acronym = acronym;
        this.executable = executable;
    }

    /** Returns the enum or UNKNOWN */
    @NonNull
    public static QueueType load(String strCode) {
        for (QueueType tt : QueueType.values()) {
            if (tt.code.equals(strCode)) {
                return tt;
            }
        }
        return UNKNOWN;
    }

    /** String to be used for persistence */
    public String save() {
        return code;
    }

    public String getAcronym() {
        return acronym;
    }

    public boolean isExecutable() {
        return executable;
    }
}
