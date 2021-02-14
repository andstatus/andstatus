package org.andstatus.app.user;

import android.database.Cursor;

import androidx.annotation.NonNull;

import org.andstatus.app.actor.GroupType;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.data.ActorSql;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.GroupMembership;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.table.ActorTable;
import org.andstatus.app.database.table.GroupMembersTable;
import org.andstatus.app.database.table.TimelineTable;
import org.andstatus.app.database.table.UserTable;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.util.CollectionsUtil;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StopWatch;
import org.andstatus.app.util.TriState;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.andstatus.app.util.RelativeTime.SOME_TIME_AGO;

public class CachedUsersAndActors {
    private final MyContext myContext;
    public final Map<Long, User> users = new ConcurrentHashMap<>();
    public final Map<Long, Actor> actors = new ConcurrentHashMap<>();
    public final Map<Long, GroupType> actorGroupTypes = new ConcurrentHashMap<>();
    public final Map<String, Long> originIdAndUsernameToActorId = new ConcurrentHashMap<>();
    public final Map<Long, User> myUsers = new ConcurrentHashMap<>();
    public final Map<Long, Actor> myActors = new ConcurrentHashMap<>();
    /** key - friendId, set of values - IDs of my actors  */
    public final Map<Long, Set<Long>> friendsOfMyActors = new ConcurrentHashMap<>();
    public final Map<Long, Set<Long>> followersOfMyActors = new ConcurrentHashMap<>();

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
        StopWatch stopWatch = StopWatch.createStarted();
        initializeMyUsers();
        initializeMyFriendsOrFollowers(GroupType.FRIENDS, friendsOfMyActors);
        initializeMyFriendsOrFollowers(GroupType.FOLLOWERS, followersOfMyActors);
        loadTimelineActors();
        MyLog.i(this, "usersInitializedMs:" + stopWatch.getTime() + "; "
                + myUsers.size() + " users, "
                + myActors.size() + " my actors, "
                + friendsOfMyActors.size() + " friends, "
                + followersOfMyActors.size() + " followers");
        return this;
    }

    private void initializeMyUsers() {
        users.clear();
        actors.clear();
        myUsers.clear();
        myActors.clear();
        final String sql = "SELECT " + ActorSql.selectFullProjection()
                + " FROM " + ActorSql.allTables()
                + " WHERE " + UserTable.IS_MY + "=" + TriState.TRUE.id;
        final Function<Cursor, Actor> function = cursor -> Actor.fromCursor(myContext, cursor, true);
        MyQuery.get(myContext, sql, function).forEach(this::updateCache);
    }

    private void initializeMyFriendsOrFollowers(GroupType groupType, Map<Long, Set<Long>> groupMembers) {
        final String MY_ACTOR_ID = "myActorId";
        groupMembers.clear();
        final String sql = "SELECT DISTINCT " + ActorSql.selectFullProjection()
                + ", friends." + ActorTable.PARENT_ACTOR_ID + " AS " + MY_ACTOR_ID
                + " FROM (" + ActorSql.allTables() + ")"
                + " INNER JOIN (" + GroupMembership.selectMemberIds(myActors.keySet(), groupType, true)
                + " ) as friends ON friends." + GroupMembersTable.MEMBER_ID +
                "=" + ActorTable.TABLE_NAME + "." + ActorTable._ID;

        final Function<Cursor, Void> function = cursor -> {
            Actor other = Actor.fromCursor(myContext, cursor, true);
            Actor me = Actor.load(myContext, DbUtils.getLong(cursor, MY_ACTOR_ID));
            groupMembers.compute(other.actorId, CollectionsUtil.addValue(me.actorId));
            return null;
        };
        MyQuery.get(myContext, sql, function);
    }

    public Actor load(long actorId) {
        return load(actorId, false);
    }

    public Actor reload(Actor actor) {
        return load(actor.actorId, true);
    }

    public Actor load(long actorId, boolean reloadFirst) {
        Actor actor = Actor.load(myContext, actorId, reloadFirst, Actor::getEmpty);
        if (reloadFirst && isMe(actor)) {
            reloadFriendsOrFollowersOfMy(GroupType.FRIENDS, friendsOfMyActors, actor);
            reloadFriendsOrFollowersOfMy(GroupType.FOLLOWERS, followersOfMyActors, actor);
        }
        return actor;
    }

    private void reloadFriendsOrFollowersOfMy(GroupType groupType, Map<Long, Set<Long>> groupMembers, Actor actor) {
        groupMembers.entrySet().stream().filter(entry -> entry.getValue().contains(actor.actorId))
                .forEach(entry ->
                        groupMembers.compute(entry.getKey(), CollectionsUtil.removeValue(actor.actorId)));
        GroupMembership.getGroupMemberIds(myContext, actor.actorId, groupType)
                .forEach(friendId -> groupMembers.compute(friendId, CollectionsUtil.addValue(actor.actorId))
        );
    }

    private void loadTimelineActors() {
        final String sql = "SELECT " + ActorSql.selectFullProjection()
                + " FROM " + ActorSql.allTables()
                + " WHERE " + ActorTable.TABLE_NAME + "." + ActorTable._ID + " IN ("
                + " SELECT DISTINCT " + TimelineTable.ACTOR_ID
                + " FROM " + TimelineTable.TABLE_NAME
                + ")";
        final Function<Cursor, Actor> function = cursor -> Actor.fromCursor(myContext, cursor, true);
        MyQuery.get(myContext, sql, function);
    }

    public boolean containsMe(@NonNull Collection<Actor> actors) {
        return actors.stream().anyMatch(this::isMe);
    }

    @Override
    public String toString() {
        return "MyUsers{\n" + myUsers + "\nMy actors: " + myActors + "\nMy friends: " + friendsOfMyActors + '}';
    }

    public boolean isMeOrMyFriend(Actor actor) {
        return actor.nonEmpty() && (isMe(actor) || friendsOfMyActors.containsKey(actor.actorId));
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
        if (actor.user.nonEmpty()) {
            updateCache(actor);
            return actor;
        }

        User user2 = User.EMPTY;
        if (user2.isEmpty() && actor.actorId != 0) {
            user2 = User.load(myContext, actor.actorId);
        }
        if (user2.isEmpty() && actor.isWebFingerIdValid()) {
            user2 = User.load(myContext, MyQuery.webFingerIdToId(myContext, 0, actor.getWebFingerId(), false));
        }
        if (user2.isEmpty()) {
            user2 = User.getNew();
        }
        if (actor.user.isMyUser().known) {
            user2.setIsMyUser(actor.user.isMyUser());
        }
        actor.user = user2;

        if (actor.user.nonEmpty()) {
            updateCache(actor);
        }
        return actor;
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

    public GroupType idToGroupType(long actorId) {
        GroupType groupTypeCached = actorGroupTypes.get(actorId);
        if (groupTypeCached != null) return groupTypeCached;

        GroupType groupTypeStored = GroupType.fromId(MyQuery.idToLongColumnValue(
                myContext.getDatabase(), ActorTable.TABLE_NAME, ActorTable.GROUP_TYPE, actorId));
        actorGroupTypes.put(actorId, groupTypeStored);
        return groupTypeStored;
    }

    public void updateCache(@NonNull Actor actor) {
        if (actor.isEmpty()) return;

        final User user = actor.user;
        final long userId = user.userId;
        final long actorId = actor.actorId;
        if (actorId != 0) {
            if (userId != 0) {
                user.actorIds.add(actorId);
            }
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
        if (actor.isEmpty()) return;

        if (actor.getUpdatedDate() <= SOME_TIME_AGO) {
            actors.putIfAbsent(actor.actorId, actor);
            return;
        }
        if (actor.isBetterToCacheThan(actors.get(actor.actorId))) {
            actors.put(actor.actorId, actor);
            actorGroupTypes.put(actor.actorId, actor.groupType);
            if (actor.isOidReal()) {
                originIdAndUsernameToActorId.put(actor.origin.getId() + ";" + actor.getUsername(), actor.actorId);
            }
            myActors.computeIfPresent(actor.actorId, (id, actor1) -> actor);
        }
    }
}