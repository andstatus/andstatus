package org.andstatus.app.service;

import androidx.annotation.NonNull;

public enum QueueType {
    CURRENT("current", "C", true, true, false),
    DOWNLOADS("downloads", "D", true, true, false),
    RETRY("retry", "R", true, true, true),
    ERROR("error", "E", false, true, true),
    PRE("pre", "P", true, false, false),
    SKIPPED("skipped", "S", true, true, true),
    UNKNOWN("unknown", "U", false, false, false);

    final private String code;
    final private String acronym;
    final private boolean executable;
    public final boolean createQueue;
    public final boolean onAddRemoveExisting;

    QueueType(String code, String acronym, boolean executable, boolean createQueue, boolean onAddRemoveExisting) {
        this.code = code;
        this.acronym = acronym;
        this.executable = executable;
        this.createQueue = createQueue;
        this.onAddRemoveExisting = onAddRemoveExisting;
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
