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

import androidx.annotation.NonNull;

import org.andstatus.app.actor.Group;
import org.andstatus.app.actor.GroupType;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.ActorSql;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.SqlIds;
import org.andstatus.app.database.table.ActorTable;
import org.andstatus.app.database.table.AudienceTable;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.util.CollectionsUtil;
import org.andstatus.app.util.IsEmpty;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.TryUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.vavr.control.Try;

public class Audience implements IsEmpty {
    private static final String TAG = Audience.class.getSimpleName();
    public final static Audience EMPTY = new Audience(Origin.EMPTY);
    private static final String LOAD_SQL = "SELECT " + ActorSql.select()
            + " FROM (" + ActorSql.tables()
            + ") INNER JOIN " + AudienceTable.TABLE_NAME + " ON "
            + AudienceTable.TABLE_NAME + "." + AudienceTable.ACTOR_ID + "="
            + ActorTable.TABLE_NAME + "." + ActorTable._ID
            + " AND " + AudienceTable.NOTE_ID + "=";
    public final Origin origin;
    private final Set<Actor> actors = new HashSet<>();
    private TriState isPublic = TriState.UNKNOWN;
    private boolean isFollowers = false;

    public Audience(Origin origin) {
        this.origin = origin;
    }

    public static Audience fromNoteId(@NonNull Origin origin, long noteId) {
        return fromNoteId(origin, noteId, MyQuery.noteIdToTriState(NoteTable.PUBLIC, noteId));
    }

    public static Audience fromNoteId(@NonNull Origin origin, long noteId, TriState isPublic) {
        if (noteId == 0) return Audience.EMPTY;

        String where = AudienceTable.NOTE_ID + "=" + noteId;
        String sql = "SELECT " + ActorTable.GROUP_TYPE + "," +
                AudienceTable.ACTOR_ID + "," + ActorTable.ACTOR_OID +
                " FROM " + AudienceTable.TABLE_NAME +
                " INNER JOIN " + ActorTable.TABLE_NAME + " ON " +
                AudienceTable.TABLE_NAME + "." + AudienceTable.ACTOR_ID + "=" +
                ActorTable.TABLE_NAME + "." + ActorTable._ID +
                " WHERE " + where;
        Audience audience = new Audience(origin);
        final Function<Cursor, Actor> function = cursor -> Actor.fromTwoIds(origin,
                GroupType.fromId(DbUtils.getLong(cursor, ActorTable.GROUP_TYPE)),
                DbUtils.getLong(cursor, AudienceTable.ACTOR_ID),
                DbUtils.getString(cursor, ActorTable.ACTOR_OID));
        audience.actors.addAll(MyQuery.get(MyContextHolder.get(), sql, function));
        audience.setPublic(isPublic);
        return audience;
    }

    public static Audience loadIds(@NonNull Origin origin, long noteId, Optional<TriState> isPublic) {
        Audience audience = new Audience(origin);
        final String sql = "SELECT " + AudienceTable.ACTOR_ID +
                " FROM " + AudienceTable.TABLE_NAME +
                " WHERE " + AudienceTable.NOTE_ID + "=" + noteId;
        final Function<Cursor, Actor> function = cursor -> Actor.fromId(origin, cursor.getLong(0));
        audience.actors.addAll(MyQuery.get(origin.myContext, sql, function));
        audience.setPublic(isPublic.orElseGet(() -> MyQuery.noteIdToTriState(NoteTable.PUBLIC, noteId)));
        return audience;
    }

    public static Audience load(@NonNull Origin origin, long noteId, Optional<TriState> isPublic) {
        Audience audience = new Audience(origin);
        final String sql = LOAD_SQL + noteId;
        final Function<Cursor, Actor> function = cursor -> Actor.fromCursor(origin.myContext, cursor, true);
        audience.actors.addAll(MyQuery.get(origin.myContext, sql, function));
        audience.setPublic(isPublic.orElseGet(() -> MyQuery.noteIdToTriState(NoteTable.PUBLIC, noteId)));
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
        audience.setPublic(getPublic());
        audience.setFollowers(isFollowers());
        return audience;
    }

    public void addActorsFromContent(@NonNull String content, @NonNull Actor author, @NonNull Actor inReplyToActor) {
        author.extractActorsFromContent(content, inReplyToActor).forEach(this::add);
    }

    public void lookupUsers() {
        actors.forEach(Actor::lookupActorId);
        deduplicate();
    }

    private void deduplicate() {
        List<Actor> prevActors = new ArrayList<>(actors);
        actors.clear();
        prevActors.forEach(this::add);
    }

    public void add(@NonNull Actor actor) {
        if (actor.isEmpty()) return;

        if (actor.isPublic()) {
            isPublic = TriState.TRUE;
        }
        List<Actor> same = actors.stream().filter(actor::isSame).collect(Collectors.toList());
        Actor toStore =  actor;
        for (Actor other: same) {
            if (other.isBetterToCacheThan(actor)) toStore = other;
            actors.remove(other);
        }
        actors.add(toStore);
    }

    public boolean containsMe(MyContext myContext) {
        return myContext.users().containsMe(actors);
    }

    public Try<Actor> findSame(Actor actor) {
        if (actor.groupType.isGroup.isTrue && actor.groupType.parentActorRequired()) {
            Actor sameActor = actors.stream().filter(a -> a.groupType == actor.groupType).findAny()
                .orElse(Actor.EMPTY);
            return sameActor.nonEmpty() ? Try.success(sameActor) : TryUtils.notFound();
        }
        return CollectionsUtil.findAny(getActors(), actor::isSame);
    }

    public boolean contains(long actorId) {
        return actors.stream().anyMatch(actor -> actor.actorId == actorId);
    }

    public boolean containsOid(String oid) {
        return actors.stream().anyMatch(actor -> actor.oid.equals(oid));
    }

    /** @return true if data changed */
    public boolean save(Actor activityActor, @NonNull MyContext myContext, long noteId, TriState isPublic, boolean countOnly) {
        if (!activityActor.origin.isValid() || noteId == 0 || activityActor.actorId == 0) {
            return false;
        }
        if (isFollowers && actors.stream().noneMatch(actor ->
                actor.groupType == GroupType.FOLLOWERS && actor.getParentActorId() == activityActor.actorId)) {
            actors.add(Group.getActorsGroup(activityActor, GroupType.FOLLOWERS, ""));
        }
        Audience prevAudience = Audience.loadIds(activityActor.origin, noteId, Optional.of(isPublic));
        Set<Actor> toDelete = new HashSet<>();
        Set<Actor> toAdd = new HashSet<>();
        for (Actor actor : prevAudience.getActors()) {
            if (actor.isPublic()) continue;
            findSame(actor).onFailure(e -> toDelete.add(actor));
        }
        for (Actor actor : getActors()) {
            if (actor.isPublic()) continue;
            if (actor.actorId == 0) {
                MyLog.w(this, "No actorId for " + actor);
                continue;
            }
            prevAudience.findSame(actor).onFailure( e -> toAdd.add(actor));
        }
        if (!toDelete.isEmpty() || !toAdd.isEmpty()) {
            MyLog.i(TAG, "Audience differs, noteId:" + noteId + "," +
                    "\nprev: " + prevAudience +
                    "\nnew: " + this +
                    (!toDelete.isEmpty() ? "\ntoDelete: " + toDelete : "") +
                    (!toAdd.isEmpty() ? "\ntoAdd: " + toAdd : "")
            );
        }
        if (!countOnly) try {
            if (!toDelete.isEmpty()) {
                MyProvider.delete(myContext, AudienceTable.TABLE_NAME, AudienceTable.NOTE_ID + "=" + noteId
                        + " AND " + AudienceTable.ACTOR_ID + SqlIds.actorIdsOf(toDelete).getSql());
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

    public void setFollowers(boolean isFollowers) {
        if (isFollowers == isFollowers()) return;

        this.isFollowers = isFollowers;  // We don't add the group immediately in order not to cause ANR
        if (!isFollowers) {
            List<Actor> toRemove = actors.stream().filter(actor -> actor.groupType == GroupType.FOLLOWERS)
                    .collect(Collectors.toList());
            actors.removeAll(toRemove);
        }
    }

    public boolean isFollowers() {
        return isFollowers || actors.stream().anyMatch(actor -> actor.groupType == GroupType.FOLLOWERS);
    }

    public void assertContext() {
        if (this == EMPTY) return;

        origin.assertContext();
        actors.forEach(Actor::assertContext);
    }
}
