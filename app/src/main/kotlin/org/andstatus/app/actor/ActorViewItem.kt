/*
 * Copyright (C) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.andstatus.app.actor

import android.database.Cursor
import org.andstatus.app.MyActivity
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.graphics.IdentifiableImageView
import org.andstatus.app.net.social.Actor
import org.andstatus.app.origin.Origin
import org.andstatus.app.timeline.DuplicationLink
import org.andstatus.app.timeline.TimelineFilter
import org.andstatus.app.timeline.ViewItem
import org.andstatus.app.timeline.meta.Timeline
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.NullUtil
import org.andstatus.app.util.RelativeTime
import java.util.stream.Stream

class ActorViewItem private constructor(val actor: Actor, isEmpty: Boolean) : ViewItem<ActorViewItem>(isEmpty, RelativeTime.DATETIME_MILLIS_NEVER), Comparable<ActorViewItem?> {
    var populated = false
    private var myActorFollowingToHide: Actor = Actor.EMPTY
    private var myActorFollowedToHide: Actor = Actor.EMPTY

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as ActorViewItem
        return actor == that.actor
    }

    override fun hashCode(): Int {
        return actor.hashCode()
    }

    fun getActorId(): Long {
        return actor.actorId
    }

    fun getDescription(): String {
        val builder = StringBuilder(actor.getSummary())
        if (MyPreferences.isShowDebuggingInfoInUi()) {
            MyStringBuilder.appendWithSpace(builder, "(id=" + actor.actorId + ")")
        }
        return builder.toString()
    }

    override fun toString(): String {
        return "ActorViewItem{" +
                actor +
                '}'
    }

    override val isEmpty: Boolean
        get() {
            return actor.isEmpty
        }

    override fun getId(): Long {
        return actor.actorId
    }

    override fun getDate(): Long {
        return actor.getUpdatedDate()
    }

    override operator fun compareTo(other: ActorViewItem?): Int {
        return actor.uniqueName.compareTo(other?.actor?.uniqueName ?: "")
    }

    fun showAvatar(myActivity: MyActivity, imageView: IdentifiableImageView) {
        actor.avatarFile.showImage(myActivity, imageView)
    }

    override fun fromCursor(myContext: MyContext, cursor: Cursor): ActorViewItem {
        val actor: Actor = Actor.fromCursor(myContext, cursor, true)
        val item = ActorViewItem(actor, false)
        item.populated = true
        return item
    }

    override fun matches(filter: TimelineFilter): Boolean {
        // TODO: implement filtering
        return super.matches(filter)
    }

    override fun duplicates(timeline: Timeline, preferredOrigin: Origin, other: ActorViewItem): DuplicationLink {
        if (isEmpty || other.isEmpty) return DuplicationLink.NONE
        if (preferredOrigin.nonEmpty && actor.origin != other.actor.origin) {
            if (preferredOrigin == actor.origin) return DuplicationLink.IS_DUPLICATED
            if (preferredOrigin == other.actor.origin) return DuplicationLink.DUPLICATES
        }
        return super.duplicates(timeline, preferredOrigin, other)
    }

    fun hideFollowedBy(myActor: Actor) {
        myActorFollowingToHide = myActor
    }

    fun getMyActorsFollowingTheActor(myContext: MyContext): Stream<Actor> {
        return NullUtil.getOrDefault<Long, MutableSet<Long>>(myContext.users.friendsOfMyActors, actor.actorId,
                mutableSetOf<Long>()).stream()
                .filter { id: Long -> id != myActorFollowingToHide.actorId }
                .map { id: Long -> NullUtil.getOrDefault(myContext.users.actors, id, Actor.EMPTY) }
                .filter { obj: Actor -> obj.nonEmpty }
    }

    fun hideFollowing(myActor: Actor) {
        myActorFollowedToHide = myActor
    }

    fun getMyActorsFollowedByTheActor(myContext: MyContext): Stream<Actor> {
        return NullUtil.getOrDefault<Long, MutableSet<Long>>(myContext.users.followersOfMyActors, actor.actorId, mutableSetOf<Long>()).stream()
                .filter { id: Long -> id != myActorFollowedToHide.actorId }
                .map { id: Long -> NullUtil.getOrDefault(myContext.users.actors, id, Actor.EMPTY) }
                .filter { obj: Actor -> obj.nonEmpty }
    }

    companion object {
        val EMPTY: ActorViewItem = ActorViewItem(Actor.EMPTY, true)
        fun newEmpty(description: String?): ActorViewItem {
            val actor: Actor = if (description.isNullOrEmpty()) Actor.EMPTY else Actor.newUnknown( Origin.EMPTY, GroupType.UNKNOWN).setSummary(description)
            return fromActor(actor)
        }

        fun fromActorId(origin: Origin, actorId: Long): ActorViewItem {
            return if (actorId == 0L) EMPTY else fromActor(Actor.fromId(origin, actorId))
        }

        fun fromActor(actor: Actor): ActorViewItem {
            return if (actor.isEmpty) EMPTY else ActorViewItem(actor, false)
        }
    }
}
