package org.andstatus.app.service

enum class QueueType(val code: String, val acronym: String, val executable: Boolean, val createQueue: Boolean, val onAddRemoveExisting: Boolean) {
    CURRENT("current", "C", true, true, false),
    DOWNLOADS("downloads", "D", true, true, false),
    RETRY("retry", "R", true, true, true),
    ERROR("error", "E", false, true, true),
    PRE("pre", "P", true, false, false),
    SKIPPED("skipped", "S", true, true, true),
    UNKNOWN("unknown", "U", false, false, false);

    /** String to be used for persistence  */
    fun save(): String {
        return code
    }

    fun isExecutable(): Boolean {
        return executable
    }

    companion object {
        /** Returns the enum or UNKNOWN  */
        fun load(strCode: String?): QueueType {
            for (tt in values()) {
                if (tt.code == strCode) {
                    return tt
                }
            }
            return UNKNOWN
        }
    }
}