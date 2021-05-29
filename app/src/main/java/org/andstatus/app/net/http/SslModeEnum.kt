package org.andstatus.app.net.http

import org.andstatus.app.R

enum class SslModeEnum(val id: Long, private val summaryResourceId: Int) {
    SECURE(1, R.string.preference_ssl_mode_secure_summary),
    INSECURE(2, R.string.preference_ssl_mode_insecure_summary),
    MISCONFIGURED(3, R.string.preference_ssl_mode_misconfigured_summary);

    override fun toString(): String {
        return "SSL mode:" + name
    }

    fun getSummaryResourceId(): Int {
        return summaryResourceId
    }

    fun getEntriesPosition(): Int {
        return ordinal
    }

    companion object {
        fun fromId(id: Long): SslModeEnum {
            for (tt in SslModeEnum.values()) {
                if (tt.id == id) {
                    return tt
                }
            }
            return SslModeEnum.SECURE
        }

        fun fromEntriesPosition(position: Int): SslModeEnum {
            var obj: SslModeEnum = SECURE
            for (value in values()) {
                if (value.ordinal == position) {
                    obj = value
                    break
                }
            }
            return obj
        }
    }
}
