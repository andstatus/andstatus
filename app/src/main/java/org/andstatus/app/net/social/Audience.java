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
import android.support.annotation.NonNull;

import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.ActorSql;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MyProvider;
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
    private final Set<Actor> actors = new HashSet<>();
    private TriState isPublic = TriState.UNKNOWN;

    public Audience(Origin origin) {
        this.origin = origin;
    }

    public static Audience fromNoteId(@NonNull Origin origin, long noteId) {
        return fromNoteId(origin, noteId, MyQuery.noteIdToTriState(NoteTable.PUBLIC, noteId));
    }

    public static Audience fromNoteId(@NonNull Origin origin, long noteId, TriState isPublic) {
        if (noteId == 0) return Audience.EMPTY;

        String where = AudienceTable.NOTE_ID + "=" + noteId;
        String sql = "SELECT " + AudienceTable.ACTOR_ID + "," + ActorTable.ACTOR_OID
                + " FROM " + AudienceTable.TABLE_NAME
                + " INNER JOIN " + ActorTable.TABLE_NAME + " ON "
                + AudienceTable.TABLE_NAME + "." + AudienceTable.ACTOR_ID + "="
                + ActorTable.TABLE_NAME + "." + ActorTable._ID
                + " WHERE " + where;
        Audience audience = new Audience(origin);
        final Function<Cursor, Actor> function = cursor -> Actor.fromOriginAndActorId(origin,
                DbUtils.getLong(cursor, AudienceTable.ACTOR_ID),
                DbUtils.getString(cursor, ActorTable.ACTOR_OID));
        MyQuery.get(MyContextHolder.get(), sql, function).forEach(audience::add);
        audience.setPublic(isPublic);
        return audience;
    }

    public static Audience load(@NonNull MyContext myContext, @NonNull Origin origin, long noteId) {
        Audience audience = new Audience(origin);
        final String sql = "SELECT " + ActorSql.select()
                + " FROM (" + ActorSql.tables()
                + ") INNER JOIN " + AudienceTable.TABLE_NAME + " ON "
                + AudienceTable.TABLE_NAME + "." + AudienceTable.ACTOR_ID + "="
                + ActorTable.TABLE_NAME + "." + ActorTable._ID
                + " AND " + AudienceTable.NOTE_ID + "=" + noteId;
        final Function<Cursor, Actor> function = cursor -> Actor.fromCursor(myContext, cursor);
        MyQuery.get(myContext, sql, function).forEach(audience::add);
        audience.setPublic(MyQuery.noteIdToTriState(NoteTable.PUBLIC, noteId));
        return audience;
    }

    public Actor getFirstNonPublic() {
        return actors.stream().filter(Actor::nonPublic).findFirst().orElse(Actor.EMPTY);
    }

    public String getUsernames() {
        return actors.stream().map(Actor::getTimelineUsername).reduce((a, b) -> a + ", " + b).orElse("");
    }

    public Set<Actor> getActors() {
        return actors;
    }

    @Override
    public boolean isEmpty() {
        return this.equals(EMPTY) || actors.isEmpty();
    }

    public boolean hasNonPublic() {
        return actors.stream().anyMatch(Actor::nonPublic);
    }

    public Audience copy() {
        Audience audience = new Audience(origin);
        actors.forEach(audience::add);
        return audience;
    }

    public void extractActorsFromContent(@NonNull String content, @NonNull Actor author, @NonNull Actor inReplyToActor) {
        author.extractActorsFromContent(content, inReplyToActor).forEach(this::add);
    }

    public void add(@NonNull Actor actor) {
        if (actor.isPublic()) {
            isPublic = TriState.TRUE;
        }
        if (!actor.isPartiallyDefined()) {
            actors.remove(actor);
        }
        actors.add(actor);
    }

    public boolean containsMe(MyContext myContext) {
        return myContext.users().containsMe(actors);
    }

    public boolean contains(Actor actor) {
        return actors.contains(actor);
    }

    public boolean contains(long actorId) {
        return actors.stream().anyMatch(actor -> actor.actorId == actorId);
    }

    /** @return true if data changed */
    public boolean save(@NonNull MyContext myContext, @NonNull Origin origin, long noteId, TriState isPublic, boolean countOnly) {
        if (!origin.isValid() || noteId == 0) {
            return false;
        }
        Audience prevAudience = Audience.fromNoteId(origin, noteId, isPublic);
        Set<Actor> toDelete = new HashSet<>();
        Set<Actor> toAdd = new HashSet<>();
        for (Actor actor : prevAudience.getActors()) {
            if (actor.isPublic()) continue;
            if (!getActors().contains(actor)) {
                toDelete.add(actor);
            }
        }
        for (Actor actor : getActors()) {
            if (actor.isPublic()) continue;
            if (!prevAudience.getActors().contains(actor)) {
                if (actor.actorId == 0) {
                    MyLog.w(this, "No actorId for " + actor);
                } else {
                    toAdd.add(actor);
                }
            }
        }
        if (!countOnly) try {
            if (!toDelete.isEmpty()) {
                MyProvider.delete(myContext, AudienceTable.TABLE_NAME, AudienceTable.NOTE_ID + "=" + noteId
                        + " AND " + AudienceTable.ACTOR_ID + SqlActorIds.fromActors(toDelete).getSql(), null);
            }
            toAdd.forEach(actor -> MyProvider.insert(myContext, AudienceTable.TABLE_NAME, toContentValues(noteId, actor)));
        } catch (Exception e) {
            MyLog.e(this, "save, noteId:" + noteId + "; " + actors, e);
        }
        return  !toDelete.isEmpty() || !toAdd.isEmpty();
    }

    @NonNull
    private ContentValues toContentValues(long noteId, Actor actor) {
        ContentValues values = new ContentValues();
        values.put(AudienceTable.NOTE_ID, noteId);
        values.put(AudienceTable.ACTOR_ID, actor.actorId);
        return values;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Audience audience = (Audience) o;

        return actors.equals(audience.actors);
    }

    @Override
    public int hashCode() {
        return actors.hashCode();
    }

    @Override
    public String toString() {
        return actors.toString();
    }

    public void setPublic(TriState isPublic) {
        this.isPublic = isPublic;
        switch (isPublic) {
            case TRUE:
                actors.add(Actor.PUBLIC);
                break;
            default:
                actors.remove(Actor.PUBLIC);
                break;
        }
    }

    public TriState getPublic() {
        return isPublic;
    }
}
