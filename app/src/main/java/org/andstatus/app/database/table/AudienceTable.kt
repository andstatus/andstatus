package org.andstatus.app.database.table;

import android.database.sqlite.SQLiteDatabase;

import org.andstatus.app.data.DbUtils;

/**
 * Recipients and Mentioned actors
 * See https://www.w3.org/TR/activitystreams-vocabulary/#audienceTargeting
 * We don't distinguish between Primary and Secondary audiences yet.
 * Targeting to Public is flagged by {@link NoteTable#VISIBILITY}
 */
public class AudienceTable {
    public static final String TABLE_NAME = "audience";

    private AudienceTable() {
    }

    public static final String ACTOR_ID = ActorTable.ACTOR_ID;
    public static final String NOTE_ID =  NoteTable.NOTE_ID;

    public static void create(SQLiteDatabase db) {
        DbUtils.execSQL(db, "CREATE TABLE " + TABLE_NAME + " ("
                + ACTOR_ID + " INTEGER NOT NULL,"
                + NOTE_ID + " INTEGER NOT NULL,"
                + " CONSTRAINT pk_audience PRIMARY KEY (" + NOTE_ID + " ASC, " + ACTOR_ID + " ASC)"
                + ")");

        DbUtils.execSQL(db, "CREATE INDEX idx_audience_actor ON " + TABLE_NAME + " (" + ACTOR_ID + ")");
        DbUtils.execSQL(db, "CREATE INDEX idx_audience_note ON " + TABLE_NAME + " (" + NOTE_ID + ")");
    }

}
