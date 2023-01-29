package org.andstatus.app.account

import android.content.SharedPreferences
import org.json.JSONObject

/**
 *
 */
enum class AccessStatus(private val id: Int) {
    /**
     * NEVER - means that the Account was never successfully authenticated with current credentials.
     * This is why we reset the state to NEVER every time credentials have been changed.
     */
    NEVER(1),
    /** Access failed after being successful */
    FAILED(2),

    /**
     * The Account was successfully authenticated
     */
    SUCCEEDED(3);

    fun put(dw: AccountDataWriter) {
        dw.setDataInt(KEY, id)
    }

    fun put(jso: JSONObject) {
        jso.put(KEY, id)
    }

    companion object {
        val KEY: String = "credentials_verified"
        fun load(sp: SharedPreferences): AccessStatus {
            val id = sp.getInt(KEY, NEVER.id)
            return fromId(id.toLong())
        }

        fun fromId(id: Long): AccessStatus {
            var status: AccessStatus = NEVER
            for (status1 in values()) {
                if (status1.id.toLong() == id) {
                    status = status1
                    break
                }
            }
            return status
        }

        fun load(dr: AccountDataReader): AccessStatus {
            val id = dr.getDataInt(KEY, NEVER.id)
            return fromId(id.toLong())
        }

        fun load(jso: JSONObject): AccessStatus {
            val id = jso.optInt(KEY, NEVER.id)
            return fromId(id.toLong())
        }
    }
}
