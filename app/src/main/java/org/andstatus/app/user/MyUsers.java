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
import org.andstatus.app.util.MyLog;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Collectors;

public class MyUsers {
    private final MyContext myContext;
    private final Map<Long, Long> myActors2Users = new ConcurrentHashMap<>();
    private final Set<Long> myFriends = new ConcurrentSkipListSet<>();

    public static MyUsers newEmpty(MyContext myContext) {
        return new MyUsers(myContext);
    }

    private MyUsers(MyContext myContext) {
        this.myContext = myContext;
    }

    public boolean isEmpty() {
        return myActors2Users.isEmpty();
    }

    public int size() {
        return myActors2Users.size();
    }

    public MyUsers initialize() {
        initializeMyActors();
        initializeMyFriends();
        MyLog.v(this, "Users list initialized, " + myActors2Users.size() + " actors, "
                + myFriends.size() + " friends");
        return this;
    }

    private void initializeMyActors() {
        myActors2Users.clear();
        if (myContext.getDatabase() == null) return;
        final String sql = "SELECT " + ActorTable.TABLE_NAME + "." + ActorTable._ID + ", "
                + ActorTable.TABLE_NAME + "." + ActorTable.USER_ID
                + " FROM " + ActorTable.TABLE_NAME
                + " INNER JOIN " + UserTable.TABLE_NAME + " ON " + ActorTable.TABLE_NAME + "." + ActorTable.USER_ID
                + "=" + UserTable.TABLE_NAME + "." + UserTable._ID
                + " AND " + UserTable.IS_MY + "=1";
        try (Cursor cursor = myContext.getDatabase().rawQuery(sql, null)) {
            while (cursor.moveToNext()) {
                myActors2Users.put(cursor.getLong(0), cursor.getLong(1));
            }
        } catch (Exception e) {
            MyLog.i(this, "SQL:'" + sql + "'", e);
        }
    }

    private void initializeMyFriends() {
        myFriends.clear();
        final String sql = "SELECT DISTINCT " + FriendshipTable.FRIEND_ID + " FROM " + FriendshipTable.TABLE_NAME
            + " WHERE " + FriendshipTable.FOLLOWED + "=1" + " AND " + FriendshipTable.ACTOR_ID
            + SqlActorIds.fromIds(myActors2Users.keySet()).getSql();
        myFriends.addAll(MyQuery.getLongs(myContext.getDatabase(), sql));
    }

    public boolean contains(@NonNull Collection<Actor> actors) {
        for (Actor actor : actors) {
            if (myActors2Users.containsKey(actor.actorId)) return true;
        }
        return false;
    }

    public boolean contains(@NonNull Actor actor) {
      return myActors2Users.containsKey(actor.actorId);
    }

    public boolean isMyActorId(long actorId) {
        return myActors2Users.containsKey(actorId);
    }

    @Override
    public String toString() {
        return "MyUsers{" + myActors2Users + '}';
    }

    public boolean isMeOrMyFriend(long actorId) {
        return isMyActorId(actorId) || myFriends.contains(actorId);
    }

    @NonNull
    public SqlActorIds myActorIds() {
        return SqlActorIds.fromIds(myActors2Users.keySet());
    }

    @NonNull
    public SqlActorIds myActorIdsFor(long actorId) {
        Long userId = myActors2Users.get(actorId);
        if (userId == null || userId == 0) return SqlActorIds.EMPTY;
        return SqlActorIds.fromIds(myActors2Users.entrySet().stream().filter(entry -> entry.getValue().equals(userId))
                .map(Map.Entry::getKey).collect(Collectors.toList()));
    }
}
