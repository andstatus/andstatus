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
import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;

import org.andstatus.app.R;
import org.andstatus.app.actor.Group;
import org.andstatus.app.actor.GroupType;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.data.ActorSql;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.SqlIds;
import org.andstatus.app.database.table.ActorTable;
import org.andstatus.app.database.table.AudienceTable;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.util.CollectionsUtil;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyStringBuilder;
import org.andstatus.app.util.StringUtil;
import org.andstatus.app.util.TryUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.vavr.control.Try;

public class Audience {
    private static final String TAG = Audience.class.getSimpleName();
    public final static Audience EMPTY = new Audience(Origin.EMPTY);
    private static final String LOAD_SQL = "SELECT " + ActorSql.selectFullProjection()
            + " FROM (" + ActorSql.allTables()
            + ") INNER JOIN " + AudienceTable.TABLE_NAME + " ON "
            + AudienceTable.TABLE_NAME + "." + AudienceTable.ACTOR_ID + "="
            + ActorTable.TABLE_NAME + "." + ActorTable._ID
            + " AND " + AudienceTable.NOTE_ID + "=";
    public final Origin origin;

    private List<Actor> actors = new ArrayList<>();
    private Visibility visibility = Visibility.UNKNOWN;
    private Actor followers = Actor.EMPTY;

    public Audience(Origin origin) {
        this.origin = origin;
    }

    public static Audience fromNoteId(@NonNull Origin origin, long noteId) {
        return fromNoteId(origin, noteId, Visibility.fromNoteId(noteId));
    }

    public static Audience fromNoteId(@NonNull Origin origin, long noteId, Visibility visibility) {
        if (noteId == 0) return Audience.EMPTY;

        String where = AudienceTable.NOTE_ID + "=" + noteId;
        String sql = "SELECT " + ActorTable.GROUP_TYPE + "," +
                AudienceTable.ACTOR_ID + "," + ActorTable.ACTOR_OID +
                " FROM " + AudienceTable.TABLE_NAME +
                " INNER JOIN " + ActorTable.TABLE_NAME + " ON " +
                AudienceTable.TABLE_NAME + "." + AudienceTable.ACTOR_ID + "=" +
                ActorTable.TABLE_NAME + "." + ActorTable._ID +
                " WHERE " + where;
        Audience audience = new Audience(origin).withVisibility(visibility);
        final Function<Cursor, Actor> function = cursor -> Actor.fromTwoIds(origin,
                GroupType.fromId(DbUtils.getLong(cursor, ActorTable.GROUP_TYPE)),
                DbUtils.getLong(cursor, AudienceTable.ACTOR_ID),
                DbUtils.getString(cursor, ActorTable.ACTOR_OID));
        MyQuery.get(origin.myContext, sql, function).forEach(audience::add);
        return audience;
    }

    public static Audience load(@NonNull Origin origin, long noteId, Optional<Visibility> optVisibility) {
        Audience audience = new Audience(origin);
        audience.setVisibility(optVisibility.orElseGet(() -> Visibility.fromNoteId(noteId)));
        final String sql = LOAD_SQL + noteId;
        final Function<Cursor, Actor> function = cursor -> Actor.fromCursor(origin.myContext, cursor, true);
        MyQuery.get(origin.myContext, sql, function).forEach(audience::add);
        return audience;
    }

    public Actor getFirstNonSpecial() {
        return actors.stream().findFirst().orElse(Actor.EMPTY);
    }

    public String toAudienceString(Actor inReplyToActor) {
        if (this == EMPTY) return "(empty)";

        Context context = origin.myContext.context();
        MyStringBuilder builder = new MyStringBuilder();
        MyStringBuilder toBuilder = new MyStringBuilder();
        if (getVisibility().isPublic()) {
            builder.withSpace(context.getText(R.string.timeline_title_public));
        } else if (getVisibility().isFollowers()) {
            toBuilder.withSpace(context.getText(R.string.followers));
        } else if (getVisibility().isPrivate()) {
            builder.withSpace(context.getText(R.string.notification_events_private));
            actors.stream()
                .filter(actor -> !actor.isSame(inReplyToActor))
                .map(Actor::getRecipientName)
                .sorted()
                .forEach(toBuilder::withComma);
        } else {
            builder.withSpace(context.getText(R.string.privacy_unknown));
        }
        if (toBuilder.nonEmpty()) {
            builder.withSpace(StringUtil.format(context, R.string.message_source_to, toBuilder.toString()));
        }
        if (inReplyToActor.nonEmpty()) {
            builder.withSpace(StringUtil.format(context, R.string.message_source_in_reply_to,
                    inReplyToActor.getRecipientName()));
        }
        return builder.toString();
    }

    public List<Actor> evaluateAndGetActorsToSave(Actor actorOfAudience) {
        if (this == EMPTY) return Collections.emptyList();

        List<Actor> toSave = actors.stream()
                .map(actor -> lookupInActorOfAudience(actorOfAudience, actor))
                .collect(Collectors.toList());
        actors.clear();
        toSave.forEach(this::add);

        if (followers == Actor.FOLLOWERS) {
            followers = Group.getActorsGroup(actorOfAudience, GroupType.FOLLOWERS, "");
        }
        if (!followers.isConstant()) {
            toSave.add(0, followers);
        }
        setVisibility(getVisibility().getKnown());
        return toSave;
    }

    private static Actor lookupInActorOfAudience(Actor actorOfAudience, Actor actor) {
        if (actor.isEmpty()) return  Actor.EMPTY;
        if (actorOfAudience.isSame(actor)) return actorOfAudience;

        Optional<Actor> optFollowers = actorOfAudience.getEndpoint(ActorEndpointType.API_FOLLOWERS)
            .flatMap(uri -> actor.oid.equals(uri.toString())
                ? Optional.of(Group.getActorsGroup(actorOfAudience, GroupType.FOLLOWERS, actor.oid))
                : Optional.empty()
            );
        if (optFollowers.isPresent()) return optFollowers.get();

        Optional<Actor> optFriends = actorOfAudience.getEndpoint(ActorEndpointType.API_FOLLOWING)
                .flatMap(uri -> actor.oid.equals(uri.toString())
                        ? Optional.of(Group.getActorsGroup(actorOfAudience, GroupType.FRIENDS, actor.oid))
                        : Optional.empty()
                );
        if (optFriends.isPresent()) return optFriends.get();

        return actor;
    }

    public boolean noRecipients() {
        return getRecipients().isEmpty();
    }

    public List<Actor> getRecipients() {
        List<Actor> recipients = new ArrayList<>(actors);
        if (isFollowers()) {
            recipients.add(0, followers);
        }
        if (visibility.isPublic()) {
            recipients.add(0, Actor.PUBLIC);
        }
        return recipients;
    }

    public boolean hasNonSpecial() {
        return actors.size() > 0;
    }

    public List<Actor> getNonSpecialActors() {
        return actors;
    }

    public Audience copy() {
        Audience audience = new Audience(origin).withVisibility(this.getVisibility());
        actors.forEach(audience::add);
        return audience;
    }

    public Audience withVisibility(Visibility visibility) {
        setVisibility(visibility.getKnown());
        return this;
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
            addVisibility(Visibility.PUBLIC);
            return;
        }
        if (actor.isFollowers()) {
            followers = actor;
            addVisibility(Visibility.TO_FOLLOWERS);
            return;
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

    public Try<Actor> findSame(Actor other) {
        if (other.isEmpty()) return TryUtils.notFound();
        if (other.isSame(followers)) return Try.success(followers);
        if (other.isPublic()) return getVisibility().isPublic() ? Try.success(Actor.PUBLIC) : TryUtils.notFound();

        List<Actor> nonSpecialActors = getNonSpecialActors();
        if (other.groupType.parentActorRequired) {
            return TryUtils.fromOptional(nonSpecialActors.stream()
                    .filter(a -> a.groupType == other.groupType).findAny());
        }
        return CollectionsUtil.findAny(nonSpecialActors, other::isSame);
    }

    public boolean containsOid(String oid) {
        if (StringUtil.isEmpty(oid)) return false;
        return oid.equals(followers.oid) || actors.stream().anyMatch(actor -> actor.oid.equals(oid));
    }

    /** TODO: Audience should belong to an Activity, not to a Note.
     *        As audience currently belongs to a Note, we actually use noteAuthor instead of activityActor here.
     * @return true if data changed */
    public boolean save(Actor actorOfAudience, long noteId, Visibility visibility, boolean countOnly) {
        if (this == EMPTY || !actorOfAudience.origin.isValid() || noteId == 0 || actorOfAudience.actorId == 0 || !origin.myContext.isReady()) {
            return false;
        }
        Audience prevAudience = Audience.loadIds(actorOfAudience.origin, noteId, Optional.of(visibility));
        List<Actor> actorsToSave = evaluateAndGetActorsToSave(actorOfAudience);
        Set<Actor> toDelete = new HashSet<>();
        Set<Actor> toAdd = new HashSet<>();

        for (Actor actor : prevAudience.evaluateAndGetActorsToSave(actorOfAudience)) {
            findSame(actor).onFailure(e -> toDelete.add(actor));
        }
        for (Actor actor : actorsToSave) {
            if (actor.actorId == 0) {
                MyLog.w(TAG, "No actorId for " + actor);
                continue;
            }
            prevAudience.findSame(actor).onFailure( e -> toAdd.add(actor));
        }
        if (!toDelete.isEmpty() || !toAdd.isEmpty()) {
            MyLog.d(TAG, "Audience differs, noteId:" + noteId + "," +
                    "\nprev: " + prevAudience +
                    "\nnew: " + this +
                    (!toDelete.isEmpty() ? "\ntoDelete: " + toDelete : "") +
                    (!toAdd.isEmpty() ? "\ntoAdd: " + toAdd : "")
            );
        }
        if (!countOnly) try {
            if (!toDelete.isEmpty()) {
                MyProvider.delete(origin.myContext, AudienceTable.TABLE_NAME, AudienceTable.NOTE_ID + "=" + noteId
                        + " AND " + AudienceTable.ACTOR_ID + SqlIds.actorIdsOf(toDelete).getSql());
            }
            toAdd.forEach(actor -> MyProvider.insert(origin.myContext, AudienceTable.TABLE_NAME, toContentValues(noteId, actor)));
        } catch (Exception e) {
            MyLog.w(this, "save, noteId:" + noteId + "; " + actors, e);
        }
        return  !toDelete.isEmpty() || !toAdd.isEmpty();
    }

    private static Audience loadIds(@NonNull Origin origin, long noteId, Optional<Visibility> optVisibility) {
        Audience audience = new Audience(origin);
        final String sql = "SELECT " + AudienceTable.ACTOR_ID +
                " FROM " + AudienceTable.TABLE_NAME +
                " WHERE " + AudienceTable.NOTE_ID + "=" + noteId;
        final Function<Cursor, Actor> function = cursor -> Actor.fromId(origin, cursor.getLong(0));
        MyQuery.get(origin.myContext, sql, function).forEach(audience::add);
        audience.setVisibility(optVisibility.orElseGet(() -> Visibility.fromNoteId(noteId)));
        return audience;
    }

    @NonNull
    private ContentValues toContentValues(long noteId, Actor actor) {
        ContentValues values = new ContentValues();
        values.put(AudienceTable.NOTE_ID, noteId);
        values.put(AudienceTable.ACTOR_ID, actor.actorId);
        return values;
    }

    @Override
    public String toString() {
        return toAudienceString(Actor.EMPTY) + "; " + getRecipients();
    }

    public void addVisibility(Visibility visibility) {
        setVisibility(this.visibility.add(visibility));
    }

    public void setVisibility(Visibility visibility) {
        if (this == EMPTY || this.visibility == visibility) return;

        if (origin.getOriginType().isFollowersChangeAllowed) {
            setFollowers(visibility.isFollowers());
        }
        this.visibility = visibility;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    private void setFollowers(boolean isFollowers) {
        if (this == EMPTY || isFollowers == isFollowers()) return;

        followers = isFollowers
            ? followers.isConstant() ? Actor.FOLLOWERS : followers
            : Actor.EMPTY;
    }

    public boolean isFollowers() {
        return followers.nonEmpty();
    }

    public void assertContext() {
        if (this == EMPTY) return;

        origin.assertContext();
        actors.forEach(Actor::assertContext);
    }

    public void addActorsToLoad(Consumer<Actor> addActorToList) {
        if (this == EMPTY) return;

        actors.forEach(addActorToList);
        if (isFollowers() && !followers.isConstant()) {
            addActorToList.accept(followers);
        }
    }

    public void setLoadedActors(Function<Actor, Actor> getLoaded) {
        if (this == EMPTY) return;

        if (isFollowers()) {
            add(getLoaded.apply(followers));
        }
        new ArrayList<>(actors).forEach( actor -> add(getLoaded.apply(actor)));
    }

    boolean isMeInAudience() {
        return origin.nonEmpty() && origin.myContext.users().containsMe(getNonSpecialActors());
    }
}
