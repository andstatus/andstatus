package org.andstatus.app.account;

import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 */
public enum CredentialsVerificationStatus {
    /**
     * NEVER - means that the Account was never successfully authenticated with current credentials.
     * This is why we reset the state to NEVER every time credentials have been changed.
     */
    NEVER(1),
    FAILED(2),
    /**
     * The Account was successfully authenticated
     */
    SUCCEEDED(3);

    private int id;

    public static final String KEY = "credentials_verified";

    private CredentialsVerificationStatus(int id) {
        this.id = id;
    }

    public void put(AccountDataWriter dw) {
        dw.setDataInt(KEY, id);
    }

    public void put(JSONObject jso) throws JSONException {
        jso.put(KEY, id);
    }

    public static CredentialsVerificationStatus load(SharedPreferences sp) {
        int id = sp.getInt(KEY, NEVER.id);
        return fromId(id);
    }

    public static CredentialsVerificationStatus fromId(long id) {
        CredentialsVerificationStatus status = NEVER;
        for (CredentialsVerificationStatus status1 : values()) {
            if (status1.id == id) {
                status = status1;
                break;
            }
        }
        return status;
    }

    public static CredentialsVerificationStatus load(AccountDataReader dr) {
        int id = dr.getDataInt(KEY, NEVER.id);
        return fromId(id);
    }

    public static CredentialsVerificationStatus load(JSONObject jso) {
        int id = jso.optInt(KEY, NEVER.id);
        return fromId(id);
    }
}
