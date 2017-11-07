package org.andstatus.app.database.table;

import android.database.sqlite.SQLiteDatabase;

import org.andstatus.app.data.DbUtils;

/**
 * Recipients and Mentioned users
 * See https://www.w3.org/TR/activitystreams-vocabulary/#audienceTargeting
 * We don't distinguish between Primary and Secondary audiences yet.
 * Targeting to Public is flagged by {@link MsgTable#PRIVATE}
 */
public class AudienceTable {
    public static final String TABLE_NAME = "audience";

    private AudienceTable() {
    }

    public static final String USER_ID = UserTable.USER_ID;
    public static final String MSG_ID =  MsgTable.MSG_ID;

    public static void create(SQLiteDatabase db) {
        DbUtils.execSQL(db, "CREATE TABLE " + TABLE_NAME + " ("
                + USER_ID + " INTEGER NOT NULL,"
                + MSG_ID + " INTEGER NOT NULL,"
                + " CONSTRAINT pk_audience PRIMARY KEY (" + MSG_ID + " ASC, " + USER_ID + " ASC)"
                + ")");

        DbUtils.execSQL(db, "CREATE INDEX idx_audience_user ON " + TABLE_NAME + " (" + USER_ID + ")");
        DbUtils.execSQL(db, "CREATE INDEX idx_audience_msg ON " + TABLE_NAME + " (" + MSG_ID + ")");
    }

}
