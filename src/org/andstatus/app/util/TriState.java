package org.andstatus.app.util;

public enum TriState {
    TRUE,
    FALSE,
    UNKNOWN;

    public boolean toBoolean(boolean defaultValue) {
        switch (this){
            case FALSE:
                return false;
            case TRUE:
                return true;
            default:
                return defaultValue;
        }
    }

    public static TriState fromBoolean(boolean booleanToConvert) {
        if (booleanToConvert) {
            return TRUE;
        } else {
            return FALSE;
        }
    }
}
