package org.andstatus.app.net.http;

import org.andstatus.app.R;
import org.andstatus.app.context.MyPreferences;

public enum SslModeEnum {
    SECURE("secure", R.string.preference_ssl_mode_secure, R.string.preference_ssl_mode_secure_summary),
    INSECURE("insecure", R.string.preference_ssl_mode_insecure, R.string.preference_ssl_mode_insecure_summary),
    MISCONFIGURED("misconfigured", R.string.preference_ssl_mode_misconfigured, R.string.preference_ssl_mode_misconfigured_summary);
    
    private final String code;
    private final int entryResourceId;
    private final int summaryResourceId;

    SslModeEnum(String code, int entryResourceId, int summaryResourceId) {
        this.code = code;
        this.summaryResourceId = summaryResourceId;
        this.entryResourceId = entryResourceId;
    }
    
    public static SslModeEnum load(String strCode) {
        for (SslModeEnum tt : SslModeEnum.values()) {
            if (tt.code.equals(strCode)) {
                return tt;
            }
        }
        return SECURE;
    }

    /**
     * String to be used for persistence
     */
    public String save() {
        return code;
    }
    
    @Override
    public String toString() {
        return "SSL mode:" + code;
    }
    
    public static SslModeEnum getPreference() {
        return load(MyPreferences.getString(MyPreferences.KEY_SSL_MODE, SECURE.code));
    }

    public int getEntryResourceId() {
        return entryResourceId;
    }

    public int getSummaryResourceId() {
        return summaryResourceId;
    }

}
