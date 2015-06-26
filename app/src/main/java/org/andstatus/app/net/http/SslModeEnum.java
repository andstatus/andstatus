package org.andstatus.app.net.http;

import org.andstatus.app.R;

public enum SslModeEnum {
    SECURE(1, R.string.preference_ssl_mode_secure_summary),
    INSECURE(2, R.string.preference_ssl_mode_insecure_summary),
    MISCONFIGURED(3, R.string.preference_ssl_mode_misconfigured_summary);
    
    private final long id;
    private final int summaryResourceId;

    SslModeEnum(long id, int summaryResourceId) {
        this.id = id;
        this.summaryResourceId = summaryResourceId;
    }
    
    public static SslModeEnum fromId(long id) {
        for (SslModeEnum tt : SslModeEnum.values()) {
            if (tt.id == id) {
                return tt;
            }
        }
        return SECURE;
    }

    public Long getId() {
        return id;
    }
    
    @Override
    public String toString() {
        return "SSL mode:" + this.name();
    }

    public int getSummaryResourceId() {
        return summaryResourceId;
    }

    public int getEntriesPosition() {
        return ordinal();
    }

    public static SslModeEnum fromEntriesPosition(int position) {
        SslModeEnum obj = SECURE;
        for(SslModeEnum val : values()) {
            if (val.ordinal() == position) {
                obj = val;
                break;
            }
        }
        return obj;
    }

}
