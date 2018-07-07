/*
 * Copyright (C) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.andstatus.app.net.social;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;

import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.SqlActorIds;
import org.andstatus.app.database.table.ActorTable;
import org.andstatus.app.database.table.AudienceTable;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.util.IsEmpty;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.TriState;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

public class Audience implements IsEmpty {
    public final static Audience EMPTY = new Audience(Origin.EMPTY);
    public final Origin origin;
    private final Set<Actor> recipients = new HashSet<>();
    private TriState isPublic = TriState.UNKNOWN;

    public Audience(Origin origin) {
        this.origin = origin;
    }

    public static Audience fromNoteId(@NonNull Origin origin, long noteId) {
        if (noteId == 0) return Audience.EMPTY;

        String where = AudienceTable.NOTE_ID + "=" + noteId;
        String sql = "SELECT " + AudienceTable.ACTOR_ID + "," + ActorTable.ACTOR_OID
                + " FROM " + AudienceTable.TABLE_NAME
                + " INNER JOIN " + ActorTable.TABLE_NAME + " ON " + AudienceTable.ACTOR_ID + "="
                + ActorTable.TABLE_NAME + "." + ActorTable._ID
                + " WHERE " + where;
        Audience audience = new Audience(origin);
        final Function<Cursor, Actor> function = cursor -> Actor.fromOriginAndActorId(origin,
                DbUtils.getLong(cursor, AudienceTable.ACTOR_ID),
                DbUtils.getString(cursor, ActorTable.ACTOR_OID));
        MyQuery.get(MyContextHolder.get(), sql, function).forEach(audience::add);
        audience.setPublic(MyQuery.noteIdToTriState(NoteTable.PUBLIC, noteId));
        return audience;
    }

    public static Audience load(@NonNull MyContext myContext, @NonNull Origin origin, long noteId) {
        Audience audience = new Audience(origin);
        final String sql = "SELECT " + Actor.getActorAndUserSqlColumns(false)
                + " FROM (" + Actor.getActorAndUserSqlTables()
                + ") INNER JOIN " + AudienceTable.TABLE_NAME + " ON " + AudienceTable.ACTOR_ID + "="
                + ActorTable.TABLE_NAME + "." + ActorTable._ID
                + " AND " + AudienceTable.NOTE_ID + "=" + noteId;
        final Function<Cursor, Actor> function = cursor -> Actor.fromCursor(myContext, cursor);
        MyQuery.get(myContext, sql, function).forEach(audience::add);
        audience.setPublic(MyQuery.noteIdToTriState(NoteTable.PUBLIC, noteId));
        return audience;
    }

    public Actor getFirstNonPublic() {
        return recipients.stream().filter(Actor::nonPublic).findFirst().orElse(Actor.EMPTY);
    }

    public String getUsernames() {
        return recipients.stream().map(Actor::getTimelineUsername).reduce((a, b) -> a + ", " + b).orElse("");
    }

    public Set<Actor> getRecipients() {
        return recipients;
    }

    @Override
    public boolean isEmpty() {
        return this.equals(EMPTY) || recipients.isEmpty();
    }

    public boolean hasNonPublic() {
        return recipients.stream().anyMatch(Actor::nonPublic);
    }

    public void copy(@NonNull Audience audience) {
        audience.recipients.forEach(this::add);
        isPublic = audience.isPublic;
    }

    public void add(@NonNull Actor actor) {
        if (actor.isPublic()) {
            isPublic = TriState.TRUE;
        }
        if (!recipients.contains(actor)) {
            recipients.add(actor);
            return;
        }
        if (actor.isPartiallyDefined()) {
            return;
        }
        recipients.remove(actor);
        recipients.add(actor);
    }

    public boolean containsMe(MyContext myContext) {
        return myContext.users().containsMe(recipients);
    }

    public boolean contains(Actor actor) {
        for (Actor recipient : recipients) {
            if (recipient.equals(actor)) {
                return true;
            }
        }
        return false;
    }

    public boolean contains(long actorId) {
        for (Actor recipient : recipients) {
            if (recipient.actorId == actorId) {
                return true;
            }
        }
        return false;
    }

    public void save(@NonNull MyContext myContext, @NonNull Origin origin, long noteId) {
        SQLiteDatabase db = myContext.getDatabase();
        if (db == null || !origin.isValid() || noteId == 0) {
            return;
        }
        Audience prevAudience = Audience.fromNoteId(origin, noteId);
        Set<Actor> toDelete = new HashSet<>();
        Set<Actor> toAdd = new HashSet<>();
        for (Actor actor : prevAudience.getRecipients()) {
            if (actor.isPublic()) continue;
            if (!getRecipients().contains(actor)) {
                toDelete.add(actor);
            }
        }
        for (Actor actor : getRecipients()) {
            if (actor.isPublic()) continue;
            if (!prevAudience.getRecipients().contains(actor)) {
                if (actor.actorId == 0) {
                    MyLog.w(this, "No actorId for " + actor);
                } else {
                    toAdd.add(actor);
                }
            }
        }
        try {
            if (!toDelete.isEmpty()) {
                db.delete(AudienceTable.TABLE_NAME, AudienceTable.NOTE_ID + "=" + noteId
                        + " AND " + AudienceTable.ACTOR_ID + SqlActorIds.fromActors(toDelete).getSql(), null);
            }
            for (Actor actor : toAdd) {
                ContentValues values = new ContentValues();
                values.put(AudienceTable.NOTE_ID, noteId);
                values.put(AudienceTable.ACTOR_ID, actor.actorId);
                long rowId = db.insert(AudienceTable.TABLE_NAME, null, values);
                if (rowId == -1) {
                    throw new SQLException("Failed to insert " + actor);
                }
            }
        } catch (Exception e) {
            MyLog.e(this, "save, noteId:" + noteId + "; " + recipients, e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Audience audience = (Audience) o;

        return recipients.equals(audience.recipients);
    }

    @Override
    public int hashCode() {
        return recipients.hashCode();
    }

    @Override
    public String toString() {
        return recipients.toString();
    }

    public void setPublic(TriState isPublic) {
        this.isPublic = isPublic;
        switch (isPublic) {
            case TRUE:
                recipients.add(Actor.PUBLIC);
                break;
            default:
                recipients.remove(Actor.PUBLIC);
                break;
        }
    }

    public TriState getPublic() {
        return isPublic;
    }

}
