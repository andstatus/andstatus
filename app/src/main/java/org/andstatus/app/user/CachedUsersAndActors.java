package org.andstatus.app.user;

import android.database.Cursor;
import android.support.annotation.NonNull;

import org.andstatus.app.context.MyContext;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.SqlActorIds;
import org.andstatus.app.database.table.ActorTable;
import org.andstatus.app.database.table.FriendshipTable;
import org.andstatus.app.database.table.TimelineTable;
import org.andstatus.app.database.table.UserTable;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.TriState;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.andstatus.app.util.RelativeTime.SOME_TIME_AGO;

public class CachedUsersAndActors {
    private final MyContext myContext;
    public final Map<Long, User> users = new ConcurrentHashMap<>();
    public final Map<Long, Actor> actors = new ConcurrentHashMap<>();
    public final Map<Long, User> myUsers = new ConcurrentHashMap<>();
    public final Map<Long, Actor> myActors = new ConcurrentHashMap<>();
    public final Map<Long, Long> friendsOfMyActors = new ConcurrentHashMap<>();

    public static CachedUsersAndActors newEmpty(MyContext myContext) {
        return new CachedUsersAndActors(myContext);
    }

    private CachedUsersAndActors(MyContext myContext) {
        this.myContext = myContext;
    }

    public int size() {
        return myUsers.size();
    }

    public CachedUsersAndActors initialize() {
        initializeMyUsers();
        initializeFriendsOfMyActors();
        loadTimelineActors();
        MyLog.v(this, () -> "Users list initialized, "
                + myUsers.size() + " users, "
                + myActors.size() + " my actors, "
                + friendsOfMyActors.size() + " friends");
        return this;
    }

    private void initializeMyUsers() {
        users.clear();
        actors.clear();
        myUsers.clear();
        myActors.clear();
        final String sql = "SELECT " + Actor.getActorAndUserSqlColumns(true)
                + " FROM " + Actor.getActorAndUserSqlTables(false, true)
                + " WHERE " + UserTable.IS_MY + "=" + TriState.TRUE.id;
        final Function<Cursor, Actor> function = cursor -> Actor.fromCursor(myContext, cursor);
        MyQuery.get(myContext, sql, function).forEach(this::updateCache);
    }

    private void initializeFriendsOfMyActors() {
        friendsOfMyActors.clear();
        final String sql = "SELECT DISTINCT " + Actor.getActorAndUserSqlColumns(false)
                + ", " + FriendshipTable.ACTOR_ID
                + " FROM (" + Actor.getActorAndUserSqlTables() + ")"
                + " INNER JOIN " + FriendshipTable.TABLE_NAME
                + " ON " + FriendshipTable.FRIEND_ID + "=" + ActorTable.TABLE_NAME + "." + ActorTable._ID
                + " AND " + FriendshipTable.FOLLOWED + "=1"
                + " AND " + FriendshipTable.TABLE_NAME + "." + FriendshipTable.ACTOR_ID
                + SqlActorIds.fromIds(myActors.keySet()).getSql();

        final Function<Cursor, Void> function = cursor -> {
            long actorId = cursor.getLong(7);
            Actor friend = Actor.fromCursor(myContext, cursor);
            updateCache(friend);
            friendsOfMyActors.put(friend.actorId, actorId);
            return null;
        };
        MyQuery.get(myContext, sql, function);
    }

    private void loadTimelineActors() {
        final String sql = "SELECT " + Actor.getActorAndUserSqlColumns(false)
                + " FROM " + Actor.getActorAndUserSqlTables()
                + " WHERE " + ActorTable.TABLE_NAME + "." + ActorTable._ID + " IN ("
                + " SELECT DISTINCT " + TimelineTable.ACTOR_ID
                + " FROM " + TimelineTable.TABLE_NAME
                + ")";
        final Function<Cursor, Actor> function = cursor -> Actor.fromCursor(myContext, cursor);
        MyQuery.get(myContext, sql, function);
    }

    public boolean containsMe(@NonNull Collection<Actor> actors) {
        return actors.stream().anyMatch(this::isMe);
    }

    @Override
    public String toString() {
        return "MyUsers{\n" + myUsers + "\nMy actors: " + myActors + "\nMy friends: " + friendsOfMyActors + '}';
    }

    public boolean isMeOrMyFriend(long actorId) {
        return actorId != 0 && (isMe(actorId) || friendsOfMyActors.containsKey(actorId));
    }

    public boolean isMe(@NonNull Actor actor) {
        return isMe(actor.actorId);
    }

    public boolean isMe(long actorId) {
        return actorId != 0 && (
            myActors.containsKey(actorId) || myUsers.values().stream().anyMatch(user -> user.actorIds.contains(actorId))
        );
    }

    public Actor lookupUser(Actor actor) {
        if (actor.user == User.EMPTY && actor.actorId != 0) {
            actor.user = User.load(myContext, actor.actorId);
        }
        if (actor.user == User.EMPTY && actor.isWebFingerIdValid()) {
            actor.user = User.load(myContext, MyQuery.webFingerIdToId(0, actor.getWebFingerId()));
        }
        if (actor.user == User.EMPTY) {
            actor.user = User.getNew();
        } else {
            updateCache(actor);
        }
        return actor;
    }

    /** Tries to find this actor in the home origin (the same host...).
     * Returns the same Actor, if not found */
    public Actor toHomeOrigin(Actor actor) {
        return actor.origin.getHost().equals(actor.getHost())
                ? actor
                : actor.user.actorIds.stream().map(id -> actors.getOrDefault(id, Actor.EMPTY))
                    .filter(a -> a != Actor.EMPTY && a.origin.getHost().equals(actor.getHost()))
                    .findAny().orElse(actor);
    }

    /** Tries to find this actor in this origin
     * Returns the same Actor, if not found */
    public Actor toOrigin(Actor actor, Origin origin) {
        return actor.origin.equals(origin)
                ? actor
                : actor.user.actorIds.stream().map(id -> actors.getOrDefault(id, Actor.EMPTY))
                .filter(a -> a != Actor.EMPTY && a.origin.equals(origin))
                .findAny().orElse(actor);
    }

    @NonNull
    public User userFromActorId(long actorId, Supplier<User> userSupplier) {
        if (actorId == 0) return User.EMPTY;
        final User user1 = actors.getOrDefault(actorId, Actor.EMPTY).user;
        return user1.nonEmpty()
                ? user1
                : users.values().stream().filter(user -> user.actorIds.contains(actorId)).findFirst().orElseGet(userSupplier);
    }

    public void updateCache(@NonNull Actor actor) {
        if (actor.isEmpty()) return;

        final User user = actor.user;
        final long userId = user.userId;
        final long actorId = actor.actorId;
        if (actorId != 0) {
            user.actorIds.add(actorId);
            updateCachedActor(actors, actor);
            if (user.isMyUser().isTrue) updateCachedActor(myActors, actor);
        }
        if (userId == 0) return;

        User cached = users.getOrDefault(userId, User.EMPTY);
        if (cached.isEmpty()) {
            users.putIfAbsent(userId, user);
            if (user.isMyUser().isTrue) myUsers.putIfAbsent(userId, user);
        } else if (user.isMyUser().isTrue && cached.isMyUser().untrue) {
            user.actorIds.addAll(cached.actorIds);
            users.put(userId, user);
            myUsers.put(userId, user);
        } else if (actorId != 0) {
            cached.actorIds.add(actorId);
        }
    }

    private void updateCachedActor(Map<Long, Actor> actors, Actor actor) {
        if (actor.getUpdatedDate() <= SOME_TIME_AGO) {
            actors.putIfAbsent(actor.actorId, actor);
            return;
        }
        Actor existing = actors.get(actor.actorId);
        if (existing == null || existing.getUpdatedDate() < actor.getUpdatedDate()) {
            actors.put(actor.actorId, actor);
        }
    }
}