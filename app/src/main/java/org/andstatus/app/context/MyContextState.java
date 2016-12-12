package org.andstatus.app.context;

public enum MyContextState {
    EMPTY,
    DATABASE_READY,
    RESTORING,
    READY,
    EXPIRED,
    UPGRADING,
    DATABASE_UNAVAILABLE,
    ERROR,
    NO_PERMISSIONS
}
