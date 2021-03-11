package org.andstatus.app.user

import android.database.Cursor
import android.provider.BaseColumns
import org.andstatus.app.actor.GroupType
import org.andstatus.app.context.MyContext
import org.andstatus.app.data.ActorSql
import org.andstatus.app.data.DbUtils
import org.andstatus.app.data.GroupMembership
import org.andstatus.app.data.MyQuery
import org.andstatus.app.database.table.ActorTable
import org.andstatus.app.database.table.GroupMembersTable
import org.andstatus.app.database.table.TimelineTable
import org.andstatus.app.database.table.UserTable
import org.andstatus.app.net.social.Actor
import org.andstatus.app.origin.Origin
import org.andstatus.app.util.CollectionsUtil
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.RelativeTime
import org.andstatus.app.util.StopWatch
import org.andstatus.app.util.TriState
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier

class CachedUsersAndActors private constructor(private val myContext: MyContext) {
    val users: MutableMap<Long, User> = ConcurrentHashMap()
    val actors: MutableMap<Long, Actor> = ConcurrentHashMap()
    val actorGroupTypes: MutableMap<Long, GroupType> = ConcurrentHashMap()
    val originIdAndUsernameToActorId: MutableMap<String, Long> = ConcurrentHashMap()
    val myUsers: MutableMap<Long, User> = ConcurrentHashMap()
    val myActors: MutableMap<Long, Actor> = ConcurrentHashMap()

    /** key - friendId, set of values - IDs of my actors   */
    val friendsOfMyActors: MutableMap<Long, MutableSet<Long>> = ConcurrentHashMap()
    val followersOfMyActors: MutableMap<Long, MutableSet<Long>> = ConcurrentHashMap()
    fun size(): Int {
        return myUsers.size
    }

    fun initialize(): CachedUsersAndActors {
        val stopWatch: StopWatch = StopWatch.createStarted()
        initializeMyUsers()
        initializeMyFriendsOrFollowers(GroupType.FRIENDS, friendsOfMyActors)
        initializeMyFriendsOrFollowers(GroupType.FOLLOWERS, followersOfMyActors)
        loadTimelineActors()
        MyLog.i(this, "usersInitializedMs:" + stopWatch.time + "; "
                + myUsers.size + " users, "
                + myActors.size + " my actors, "
                + friendsOfMyActors.size + " friends, "
                + followersOfMyActors.size + " followers")
        return this
    }

    private fun initializeMyUsers() {
        users.clear()
        actors.clear()
        myUsers.clear()
        myActors.clear()
        val sql = ("SELECT " + ActorSql.selectFullProjection()
                + " FROM " + ActorSql.allTables()
                + " WHERE " + UserTable.IS_MY + "=" + TriState.TRUE.id)
        val function = Function<Cursor, Actor> { cursor: Cursor -> Actor.fromCursor(myContext, cursor, true) }
        MyQuery.get(myContext, sql, function).forEach(Consumer { actor: Actor -> updateCache(actor) })
    }

    private fun initializeMyFriendsOrFollowers(groupType: GroupType, groupMembers: MutableMap<Long, MutableSet<Long>>) {
        val MY_ACTOR_ID = "myActorId"
        groupMembers.clear()
        val sql = ("SELECT DISTINCT " + ActorSql.selectFullProjection()
                + ", friends." + ActorTable.PARENT_ACTOR_ID + " AS " + MY_ACTOR_ID
                + " FROM (" + ActorSql.allTables() + ")"
                + " INNER JOIN (" + GroupMembership.selectMemberIds(myActors.keys, groupType, true)
                + " ) as friends ON friends." + GroupMembersTable.MEMBER_ID +
                "=" + ActorTable.TABLE_NAME + "." + BaseColumns._ID)
        val function = Function<Cursor, Void> { cursor: Cursor ->
            val other: Actor = Actor.fromCursor(myContext, cursor, true)
            val me: Actor = Actor.load(myContext, DbUtils.getLong(cursor, MY_ACTOR_ID))
            groupMembers.compute(other.actorId, CollectionsUtil.addValue(me.actorId))
            null
        }
        MyQuery.get(myContext, sql, function)
    }

    fun reload(actor: Actor): Actor {
        return load(actor.actorId, true)
    }

    @JvmOverloads
    fun load(actorId: Long, reloadFirst: Boolean = false): Actor {
        val actor: Actor = Actor.load(myContext, actorId, reloadFirst) { Actor.getEmpty() }
        if (reloadFirst && isMe(actor)) {
            reloadFriendsOrFollowersOfMy(GroupType.FRIENDS, friendsOfMyActors, actor)
            reloadFriendsOrFollowersOfMy(GroupType.FOLLOWERS, followersOfMyActors, actor)
        }
        return actor
    }

    private fun reloadFriendsOrFollowersOfMy(groupType: GroupType, groupMembers: MutableMap<Long, MutableSet<Long>>, actor: Actor) {
        groupMembers.entries.stream().filter { entry: MutableMap.MutableEntry<Long, MutableSet<Long>> -> entry.value.contains(actor.actorId) }
                .forEach { entry: MutableMap.MutableEntry<Long, MutableSet<Long>> -> groupMembers.compute(entry.key, CollectionsUtil.removeValue(actor.actorId)) }
        GroupMembership.getGroupMemberIds(myContext, actor.actorId, groupType)
                .forEach(Consumer { friendId: Long -> groupMembers.compute(friendId, CollectionsUtil.addValue(actor.actorId)) }
                )
    }

    private fun loadTimelineActors() {
        val sql = ("SELECT " + ActorSql.selectFullProjection()
                + " FROM " + ActorSql.allTables()
                + " WHERE " + ActorTable.TABLE_NAME + "." + BaseColumns._ID + " IN ("
                + " SELECT DISTINCT " + TimelineTable.ACTOR_ID
                + " FROM " + TimelineTable.TABLE_NAME
                + ")")
        val function = Function<Cursor, Actor> { cursor: Cursor -> Actor.fromCursor(myContext, cursor, true) }
        MyQuery.get(myContext, sql, function)
    }

    fun containsMe(actors: Collection<Actor>): Boolean {
        return actors.stream().anyMatch { actor: Actor -> this.isMe(actor) }
    }

    override fun toString(): String {
        return "MyUsers{\n$myUsers\nMy actors: $myActors\nMy friends: $friendsOfMyActors}"
    }

    fun isMeOrMyFriend(actor: Actor): Boolean {
        return actor.nonEmpty && (isMe(actor) || friendsOfMyActors.containsKey(actor.actorId))
    }

    fun isMe(actor: Actor): Boolean {
        return isMe(actor.actorId)
    }

    fun isMe(actorId: Long): Boolean {
        return actorId != 0L && (myActors.containsKey(actorId) || myUsers.values.stream().anyMatch { user: User -> user.actorIds.contains(actorId) })
    }

    fun lookupUser(actor: Actor): Actor {
        if (actor.user.nonEmpty) {
            updateCache(actor)
            return actor
        }
        var user2: User = User.EMPTY
        if (user2.isEmpty && actor.actorId != 0L) {
            user2 = User.load(myContext, actor.actorId)
        }
        if (user2.isEmpty && actor.isWebFingerIdValid()) {
            user2 = User.load(myContext, MyQuery.webFingerIdToId(myContext, 0, actor.getWebFingerId(), false))
        }
        if (user2.isEmpty) {
            user2 = User.getNew()
        }
        if (actor.user.isMyUser().known) {
            user2.setIsMyUser(actor.user.isMyUser())
        }
        actor.user = user2
        if (actor.user.nonEmpty) {
            updateCache(actor)
        }
        return actor
    }

    /** Tries to find this actor in this origin
     * Returns the same Actor, if not found  */
    fun toOrigin(actor: Actor, origin: Origin): Actor {
        return if (actor.origin == origin) actor else actor.user.actorIds.stream()
                .map { id: Long -> actors.getOrDefault(id, Actor.EMPTY) }
                .filter { a: Actor -> a !== Actor.EMPTY && a.origin == origin }
                .findAny().orElse(actor)
    }

    fun userFromActorId(actorId: Long, userSupplier: Supplier<User>): User {
        if (actorId == 0L) return User.EMPTY
        val user1 = actors.getOrDefault(actorId, Actor.EMPTY).user
        return if (user1.nonEmpty) user1 else users.values.stream()
                .filter { user: User -> user.actorIds.contains(actorId) }
                .findFirst()
                .orElseGet(userSupplier)
    }

    fun idToGroupType(actorId: Long): GroupType {
        val groupTypeCached = actorGroupTypes.get(actorId)
        if (groupTypeCached != null) return groupTypeCached
        val groupTypeStored: GroupType = GroupType.fromId(MyQuery.idToLongColumnValue(
                myContext.getDatabase(), ActorTable.TABLE_NAME, ActorTable.GROUP_TYPE, actorId))
        actorGroupTypes[actorId] = groupTypeStored
        return groupTypeStored
    }

    fun updateCache(actor: Actor) {
        if (actor.isEmpty) return
        val user = actor.user
        val userId = user.userId
        val actorId = actor.actorId
        if (actorId != 0L) {
            if (userId != 0L) {
                user.actorIds.add(actorId)
            }
            updateCachedActor(actors, actor)
            if (user.isMyUser().isTrue) updateCachedActor(myActors, actor)
        }
        if (userId == 0L) return
        val cached = users.getOrDefault(userId, User.EMPTY)
        if (cached.isEmpty) {
            users.putIfAbsent(userId, user)
            if (user.isMyUser().isTrue) myUsers.putIfAbsent(userId, user)
        } else if (user.isMyUser().isTrue && cached.isMyUser().untrue) {
            user.actorIds.addAll(cached.actorIds)
            users[userId] = user
            myUsers[userId] = user
        } else if (actorId != 0L) {
            cached.actorIds.add(actorId)
        }
    }

    private fun updateCachedActor(actors: MutableMap<Long, Actor>, actor: Actor) {
        if (actor.isEmpty) return
        if (actor.getUpdatedDate() <= RelativeTime.SOME_TIME_AGO) {
            actors.putIfAbsent(actor.actorId, actor)
            return
        }
        if (actor.isBetterToCacheThan(actors.get(actor.actorId))) {
            actors[actor.actorId] = actor
            actorGroupTypes[actor.actorId] = actor.groupType
            if (actor.isOidReal()) {
                originIdAndUsernameToActorId[actor.origin.id.toString() + ";" + actor.getUsername()] = actor.actorId
            }
            myActors.computeIfPresent(actor.actorId, { id: Long, actor1: Actor -> actor })
        }
    }

    companion object {
        fun newEmpty(myContext: MyContext): CachedUsersAndActors {
            return CachedUsersAndActors(myContext)
        }
    }
}