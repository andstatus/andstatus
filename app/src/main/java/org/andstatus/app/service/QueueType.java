package org.andstatus.app.service;

import androidx.annotation.NonNull;

public enum QueueType {
    CURRENT("current", "C", true, true),
    RETRY("retry", "R", true, true),
    ERROR("error", "E", false, true),
    PRE("pre", "P", true, false),
    SKIPPED("skipped", "S", true, true),
    UNKNOWN("unknown", "U", false, false);

    final private String code;
    final private String acronym;
    final private boolean executable;
    public final boolean createQueue;

    QueueType(String code, String acronym, boolean executable, boolean createQueue) {
        this.code = code;
        this.acronym = acronym;
        this.executable = executable;
        this.createQueue = createQueue;
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
