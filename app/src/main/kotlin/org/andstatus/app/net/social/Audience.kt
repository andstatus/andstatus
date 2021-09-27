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
package org.andstatus.app.net.social

import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.provider.BaseColumns
import io.vavr.control.Try
import org.andstatus.app.R
import org.andstatus.app.actor.Group
import org.andstatus.app.actor.GroupType
import org.andstatus.app.context.MyContext
import org.andstatus.app.data.ActorSql
import org.andstatus.app.data.DbUtils
import org.andstatus.app.data.MyProvider
import org.andstatus.app.data.MyQuery
import org.andstatus.app.data.SqlIds
import org.andstatus.app.database.table.ActorTable
import org.andstatus.app.database.table.AudienceTable
import org.andstatus.app.origin.Origin
import org.andstatus.app.util.CollectionsUtil
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.StringUtil
import org.andstatus.app.util.TryUtils
import java.util.*
import java.util.function.Consumer
import java.util.function.Function
import java.util.stream.Collectors

class Audience(val origin: Origin) {
    private val actors: MutableList<Actor> = ArrayList()

    var visibility: Visibility = Visibility.UNKNOWN
        get() = field
        set(visibility) {
            if (this === EMPTY || field == visibility) return
            if (origin.originType.isFollowersChangeAllowed) setFollowers(visibility.isFollowers())
            field = visibility
        }

    private var followers: Actor = Actor.EMPTY

    fun getFirstNonSpecial(): Actor {
        return actors.stream().findFirst().orElse(Actor.EMPTY)
    }

    fun toAudienceString(inReplyToActor: Actor): String {
        if (this === EMPTY) return "(empty)"
        val context = origin.myContext.context
        val builder = MyStringBuilder()
        val toBuilder = MyStringBuilder()
        if (visibility.isPublic()) {
            builder.withSpace(context?.getText(R.string.timeline_title_public) ?: "Public")
        } else if (visibility.isFollowers()) {
            toBuilder.withSpace(context?.getText(R.string.followers) ?: "Followers")
        } else if (visibility.isPrivate) {
            builder.withSpace(context?.getText(R.string.notification_events_private) ?: "Private")
            actors.stream()
                    .filter { actor: Actor -> !actor.isSame(inReplyToActor) }
                    .map { obj: Actor -> obj.getRecipientName() }
                    .sorted()
                    .forEach { text: String? -> toBuilder.withComma(text) }
        } else {
            builder.withSpace(context?.getText(R.string.privacy_unknown) ?: "Public?")
        }
        if (toBuilder.nonEmpty) {
            builder.withSpace(StringUtil.format(context, R.string.message_source_to, toBuilder.toString()))
        }
        if (inReplyToActor.nonEmpty) {
            builder.withSpace(StringUtil.format(context, R.string.message_source_in_reply_to,
                    inReplyToActor.getRecipientName()))
        }
        return builder.toString()
    }

    fun evaluateAndGetActorsToSave(actorOfAudience: Actor): List<Actor> {
        if (this === EMPTY) return emptyList()
        val toSave = actors.stream()
                .map { actor: Actor -> lookupInActorOfAudience(actorOfAudience, actor) }
                .collect(Collectors.toList())
        actors.clear()
        toSave.forEach { actor: Actor -> add(actor) }
        if (followers === Actor.FOLLOWERS) {
            followers = Group.getSingleActorsGroup(actorOfAudience, GroupType.FOLLOWERS, "")
        }
        if (!followers.isConstant()) {
            toSave.add(0, followers)
        }
        visibility = visibility.getKnown()
        return toSave
    }

    fun noRecipients(): Boolean {
        return getRecipients().isEmpty()
    }

    fun getRecipients(): MutableList<Actor> {
        val recipients: MutableList<Actor> = ArrayList(actors)
        if (isFollowers()) {
            recipients.add(0, followers)
        }
        if (visibility.isPublic()) {
            recipients.add(0, Actor.PUBLIC)
        }
        return recipients
    }

    fun hasNonSpecial(): Boolean {
        return actors.size > 0
    }

    fun getNonSpecialActors(): List<Actor> {
        return actors
    }

    fun copy(): Audience {
        val audience = Audience(origin).withVisibility(visibility)
        actors.forEach { actor: Actor -> audience.add(actor) }
        return audience
    }

    fun withVisibility(visibility: Visibility): Audience {
        this.visibility = visibility.getKnown()
        return this
    }

    fun addActorsFromContent(content: String, author: Actor, inReplyToActor: Actor) {
        author.extractActorsFromContent(content, inReplyToActor).forEach { actor: Actor -> add(actor) }
    }

    fun lookupUsers() {
        actors.forEach { obj: Actor -> obj.lookupActorId() }
        deduplicate()
    }

    private fun deduplicate() {
        val prevActors: MutableList<Actor> = ArrayList(actors)
        actors.clear()
        prevActors.forEach { actor: Actor -> add(actor) }
    }

    fun add(actor: Actor) {
        if (actor.isEmpty) return
        if (actor.isPublic()) {
            addVisibility(Visibility.PUBLIC)
            return
        }
        if (actor.isFollowers()) {
            followers = actor
            addVisibility(Visibility.TO_FOLLOWERS)
            return
        }
        val same = actors.stream().filter { that: Actor -> actor.isSame(that) }.collect(Collectors.toList())
        var toStore: Actor = actor
        for (other in same) {
            if (other.isBetterToCacheThan(actor)) toStore = other
            actors.remove(other)
        }
        actors.add(toStore)
    }

    fun containsMe(myContext: MyContext): Boolean {
        return myContext.users.containsMe(actors)
    }

    fun findSame(other: Actor): Try<Actor> {
        if (other.isEmpty) return TryUtils.notFound()
        if (other.isSame(followers)) return Try.success(followers)
        if (other.isPublic()) return if (visibility.isPublic()) Try.success(Actor.PUBLIC) else TryUtils.notFound()
        val nonSpecialActors = getNonSpecialActors()
        return if (other.groupType.hasParentActor) {
            TryUtils.fromOptional(nonSpecialActors.stream()
                    .filter { a: Actor -> a.groupType == other.groupType }.findAny())
        } else CollectionsUtil.findAny(nonSpecialActors) { that: Actor -> other.isSame(that) }
    }

    fun containsOid(oid: String?): Boolean {
        return if (oid.isNullOrEmpty()) false else oid == followers.oid || actors.stream()
                .anyMatch { actor: Actor -> actor.oid == oid }
    }

    /** TODO: Audience should belong to an Activity, not to a Note.
     * As audience currently belongs to a Note, we actually use noteAuthor instead of activityActor here.
     * @return true if data changed
     */
    fun save(actorOfAudience: Actor, noteId: Long, visibility: Visibility, countOnly: Boolean): Boolean {
        if (this === EMPTY || !actorOfAudience.origin.isValid || noteId == 0L || actorOfAudience.actorId == 0L ||
                !origin.myContext.isReady
        ) {
            return false
        }
        val prevAudience = loadIds(actorOfAudience.origin, noteId, Optional.of(visibility))
        val actorsToSave = evaluateAndGetActorsToSave(actorOfAudience)
        val toDelete: MutableSet<Actor> = HashSet()
        val toAdd: MutableSet<Actor> = HashSet()
        for (actor in prevAudience.evaluateAndGetActorsToSave(actorOfAudience)) {
            findSame(actor).onFailure { e: Throwable? -> toDelete.add(actor) }
        }
        for (actor in actorsToSave) {
            if (actor.actorId == 0L) {
                MyLog.w(TAG, "No actorId for $actor")
                continue
            }
            prevAudience.findSame(actor).onFailure { e: Throwable? -> toAdd.add(actor) }
        }
        if (!toDelete.isEmpty() || !toAdd.isEmpty()) {
            MyLog.d(TAG, "Audience differs, noteId:$noteId," +
                    "\nprev: $prevAudience" +
                    "\nnew: $this" +
                    (if (!toDelete.isEmpty()) "\ntoDelete: $toDelete" else "") +
                    if (!toAdd.isEmpty()) "\ntoAdd: $toAdd" else ""
            )
        }
        if (!countOnly) try {
            if (!toDelete.isEmpty()) {
                MyProvider.delete(origin.myContext, AudienceTable.TABLE_NAME, AudienceTable.NOTE_ID + "=" + noteId
                        + " AND " + AudienceTable.ACTOR_ID + SqlIds.actorIdsOf(toDelete).getSql())
            }
            toAdd.forEach { actor: Actor ->
                MyProvider.insert(origin.myContext, AudienceTable.TABLE_NAME, toContentValues(noteId, actor))
            }
        } catch (e: Exception) {
            MyLog.w(this, "save, noteId:$noteId; $actors", e)
        }
        return !toDelete.isEmpty() || !toAdd.isEmpty()
    }

    private fun toContentValues(noteId: Long, actor: Actor): ContentValues {
        val values = ContentValues()
        values.put(AudienceTable.NOTE_ID, noteId)
        values.put(AudienceTable.ACTOR_ID, actor.actorId)
        return values
    }

    override fun toString(): String {
        return toAudienceString(Actor.EMPTY) + "; " + getRecipients()
    }

    fun addVisibility(visibility: Visibility) {
        this.visibility = this.visibility.add(visibility)
    }

    private fun setFollowers(isFollowers: Boolean) {
        if (this === EMPTY || isFollowers == isFollowers()) return
        followers = if (isFollowers) if (followers.isConstant()) Actor.FOLLOWERS else followers else Actor.EMPTY
    }

    fun isFollowers(): Boolean {
        return followers.nonEmpty
    }

    fun assertContext() {
        if (this === EMPTY) return
        origin.assertContext()
        actors.forEach(Consumer { obj: Actor -> obj.assertContext() })
    }

    fun addActorsToLoad(addActorToList: Consumer<Actor>) {
        if (this === EMPTY) return
        actors.forEach(addActorToList)
        if (isFollowers() && !followers.isConstant()) {
            addActorToList.accept(followers)
        }
    }

    fun setLoadedActors(getLoaded: Function<Actor, Actor>) {
        if (this === EMPTY) return
        if (isFollowers()) {
            add(getLoaded.apply(followers))
        }
        ArrayList(actors).forEach(Consumer { actor: Actor -> add(getLoaded.apply(actor)) })
    }

    fun isMeInAudience(): Boolean {
        return origin.nonEmpty && origin.myContext.users.containsMe(getNonSpecialActors())
    }

    companion object {
        private val TAG: String = Audience::class.java.simpleName
        val EMPTY: Audience = Audience( Origin.EMPTY)
        private val LOAD_SQL: String = ("SELECT " + ActorSql.selectFullProjection()
                + " FROM (" + ActorSql.allTables()
                + ") INNER JOIN " + AudienceTable.TABLE_NAME + " ON "
                + AudienceTable.TABLE_NAME + "." + AudienceTable.ACTOR_ID + "="
                + ActorTable.TABLE_NAME + "." + BaseColumns._ID
                + " AND " + AudienceTable.NOTE_ID + "=")

        @JvmOverloads
        fun fromNoteId(origin: Origin, noteId: Long, visibility: Visibility = Visibility.fromNoteId(noteId)): Audience {
            if (noteId == 0L) return EMPTY
            val where = AudienceTable.NOTE_ID + "=" + noteId
            val sql = "SELECT " + ActorTable.GROUP_TYPE + "," +
                    AudienceTable.ACTOR_ID + "," + ActorTable.ACTOR_OID +
                    " FROM " + AudienceTable.TABLE_NAME +
                    " INNER JOIN " + ActorTable.TABLE_NAME + " ON " +
                    AudienceTable.TABLE_NAME + "." + AudienceTable.ACTOR_ID + "=" +
                    ActorTable.TABLE_NAME + "." + BaseColumns._ID +
                    " WHERE " + where
            val audience = Audience(origin).withVisibility(visibility)
            val function = Function<Cursor, Actor> { cursor: Cursor ->
                Actor.fromTwoIds(origin,
                        GroupType.fromId(DbUtils.getLong(cursor, ActorTable.GROUP_TYPE)),
                        DbUtils.getLong(cursor, AudienceTable.ACTOR_ID),
                        DbUtils.getString(cursor, ActorTable.ACTOR_OID))
            }
            MyQuery.get(origin.myContext, sql, function).forEach(Consumer { actor: Actor -> audience.add(actor) })
            return audience
        }

        fun load(origin: Origin, noteId: Long, optVisibility: Optional<Visibility>): Audience {
            val audience = Audience(origin)
            audience.visibility = optVisibility.orElseGet { Visibility.fromNoteId(noteId) }
            val sql = LOAD_SQL + noteId
            val function = Function<Cursor, Actor> { cursor: Cursor -> Actor.fromCursor(origin.myContext, cursor, true) }
            MyQuery.get(origin.myContext, sql, function).forEach(Consumer { actor: Actor -> audience.add(actor) })
            return audience
        }

        private fun lookupInActorOfAudience(actorOfAudience: Actor, actor: Actor): Actor {
            if (actor.isEmpty) return Actor.EMPTY
            if (actorOfAudience.isSame(actor)) return actorOfAudience
            val optFollowers = actorOfAudience.getEndpoint(ActorEndpointType.API_FOLLOWERS)
                    .flatMap { uri: Uri? -> if (actor.oid == uri.toString()) Optional.of(Group.getSingleActorsGroup(actorOfAudience, GroupType.FOLLOWERS, actor.oid)) else Optional.empty() }
            if (optFollowers.isPresent) return optFollowers.get()
            val optFriends = actorOfAudience.getEndpoint(ActorEndpointType.API_FOLLOWING)
                    .flatMap { uri: Uri? -> if (actor.oid == uri.toString()) Optional.of(Group.getSingleActorsGroup(actorOfAudience, GroupType.FRIENDS, actor.oid)) else Optional.empty() }
            return if (optFriends.isPresent) optFriends.get() else actor
        }

        private fun loadIds(origin: Origin, noteId: Long, optVisibility: Optional<Visibility>): Audience {
            val audience = Audience(origin)
            val sql = "SELECT " + AudienceTable.ACTOR_ID +
                    " FROM " + AudienceTable.TABLE_NAME +
                    " WHERE " + AudienceTable.NOTE_ID + "=" + noteId
            val function = Function<Cursor, Actor> { cursor: Cursor -> Actor.fromId(origin, cursor.getLong(0)) }
            MyQuery.get(origin.myContext, sql, function).forEach(Consumer { actor: Actor -> audience.add(actor) })
            audience.visibility = optVisibility.orElseGet { Visibility.fromNoteId(noteId) }
            return audience
        }
    }
}
