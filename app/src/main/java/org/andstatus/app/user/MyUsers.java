package org.andstatus.app.user;

import android.database.Cursor;
import android.support.annotation.NonNull;

import org.andstatus.app.context.MyContext;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.SqlActorIds;
import org.andstatus.app.database.table.ActorTable;
import org.andstatus.app.database.table.FriendshipTable;
import org.andstatus.app.database.table.UserTable;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.Friendship;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.TriState;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.Function;
import java.util.stream.Collectors;

public class MyUsers {
    private final MyContext myContext;
    public final Map<Long, User> myUsers = new ConcurrentHashMap<>();
    public final Map<Long, Actor> myActors = new ConcurrentHashMap<>();
    public final Set<Friendship> friendsOfMyActors = new ConcurrentSkipListSet<>();

    public static MyUsers newEmpty(MyContext myContext) {
        return new MyUsers(myContext);
    }

    private MyUsers(MyContext myContext) {
        this.myContext = myContext;
    }

    public boolean isEmpty() {
        return myUsers.isEmpty();
    }

    public int size() {
        return myUsers.size();
    }

    public MyUsers initialize() {
        initializeMyUsers();
        initializeFriendsOfMyActors();
        MyLog.v(this, "Users list initialized, "
                + myUsers.size() + " users, "
                + myActors.size() + " actors, "
                + friendsOfMyActors.size() + " friends");
        return this;
    }

    private void initializeMyUsers() {
        myUsers.clear();
        myActors.clear();
        final String sql = "SELECT " + ActorTable.TABLE_NAME + "." + ActorTable._ID
                + ", " + ActorTable.TABLE_NAME + "." + ActorTable.ORIGIN_ID
                + ", " + ActorTable.TABLE_NAME + "." + ActorTable.USER_ID
                + ", " + UserTable.TABLE_NAME + "." + UserTable.KNOWN_AS
                + " FROM " + ActorTable.TABLE_NAME
                + " INNER JOIN " + UserTable.TABLE_NAME + " ON " + ActorTable.TABLE_NAME + "." + ActorTable.USER_ID
                + "=" + UserTable.TABLE_NAME + "." + UserTable._ID
                + " AND " + UserTable.IS_MY + "=" + TriState.TRUE.id;
        final Function<Cursor, Actor> function = cursor -> {
            Actor actor = Actor.fromOriginAndActorId(myContext.origins().fromId(cursor.getLong(1)),
                    cursor.getLong(0));
            final long userId = cursor.getLong(2);
            actor.user = myUsers.getOrDefault(userId, User.EMPTY);
            if (actor.user.isEmpty()) {
                actor.user = new User(userId, cursor.getString(3), TriState.TRUE, new HashSet<>());
                myUsers.put(userId, actor.user);
            }
            actor.user.actorIds.add(actor.actorId);
            return actor;
        };
        myActors.putAll(MyQuery.get(myContext, sql, function).stream()
                .collect(Collectors.toMap(actor -> actor.actorId, actor -> actor)));
    }

    private void initializeFriendsOfMyActors() {
        friendsOfMyActors.clear();
        final String sql = "SELECT DISTINCT " + FriendshipTable.FRIEND_ID
                + ", " + ActorTable.ORIGIN_ID
                + ", " + FriendshipTable.ACTOR_ID
                + " FROM " + FriendshipTable.TABLE_NAME
                + " INNER JOIN " + ActorTable.TABLE_NAME
                + " ON " + FriendshipTable.FRIEND_ID + "=" + ActorTable._ID
                + " WHERE " + FriendshipTable.FOLLOWED + "=1"
                + " AND " + FriendshipTable.ACTOR_ID + SqlActorIds.fromIds(myActors.keySet()).getSql();

        final Function<Cursor, Friendship> function = cursor -> {
            Actor friend = Actor.fromOriginAndActorId(myContext.origins().fromId(cursor.getLong(1)),
                    cursor.getLong(0));
            return new Friendship(myActors.getOrDefault(cursor.getLong(2), Actor.EMPTY), friend);
        };
        friendsOfMyActors.addAll(MyQuery.get(myContext, sql, function));
    }

    public boolean contains(@NonNull Collection<Actor> actors) {
        for (Actor actor : actors) {
            if (myActors.containsKey(actor.actorId)) return true;
        }
        return false;
    }

    public boolean contains(@NonNull Actor actor) {
      return myActors.containsKey(actor.actorId);
    }

    @Override
    public String toString() {
        return "MyUsers{\n" + myUsers + "\nMy actors: " + myActors + "\nMy friends: " + friendsOfMyActors + '}';
    }

    public boolean isMeOrMyFriend(long actorId) {
        return myActors.containsKey(actorId)
                || friendsOfMyActors.stream().filter(friendship -> friendship.friend.actorId == actorId).count() > 0;
    }

    @NonNull
    public User fromActorId(long actorId) {
        return myUsers.getOrDefault(myActors.getOrDefault(actorId, Actor.EMPTY).user, User.EMPTY);
    }
}
