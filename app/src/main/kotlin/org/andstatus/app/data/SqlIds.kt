/*
 * Copyright (C) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.data

import org.andstatus.app.context.MyContextHolder.Companion.myContextHolder
import org.andstatus.app.net.social.Actor
import org.andstatus.app.timeline.meta.Timeline
import org.andstatus.app.util.IsEmpty
import java.util.*
import java.util.stream.Collectors

/**
 * Helper class to construct sql WHERE clause selecting by Ids
 * @author yvolk@yurivolkov.com
 */
class SqlIds : IsEmpty {
    private val ids: MutableSet<Long>

    private constructor(ids: Collection<Long>) {
        this.ids = HashSet(ids)
    }

    private constructor(vararg id: Long?) {
        ids = HashSet(Arrays.asList(*id))
    }

    fun size(): Int {
        return ids.size
    }

    fun getList(): String {
        val sb = StringBuilder()
        for (id in ids) {
            if (sb.length > 0) {
                sb.append(", ")
            }
            sb.append(java.lang.Long.toString(id))
        }
        return sb.toString()
    }

    fun getSql(): String {
        return if (size() == 0) {
            getInexistentId()
        } else if (size() == 1) {
            "=" + ids.iterator().next()
        } else {
            " IN (" + getList() + ")"
        }
    }

    fun getNotSql(): String {
        return if (size() == 0) {
            ""
        } else if (size() == 1) {
            "!=" + ids.iterator().next()
        } else {
            " NOT IN (" + getList() + ")"
        }
    }

    override val isEmpty: Boolean
        get() {
            return ids.isEmpty()
        }

    companion object {
        val EMPTY: SqlIds = SqlIds()

        /** We may not have our actor in some origin... ?!  */
        fun notifiedActorIdsOfTimeline(timeline: Timeline): SqlIds {
            return if (timeline.isCombined) {
                EMPTY
            } else {
                actorIdsOfTimelineActor(timeline)
            }
        }

        fun actorIdsOfTimelineActor(timeline: Timeline): SqlIds {
            return if (timeline.isCombined) {
                myActorsIds()
            } else if (timeline.timelineType.isAtOrigin()) {
                EMPTY
            } else {
                fromIds(timeline.actor.user.actorIds)
            }
        }

        fun myActorsIds(): SqlIds {
            return fromIds(myContextHolder.getNow().users.myActors.keys)
        }

        fun actorIdsOfTimelineAccount(timeline: Timeline): SqlIds {
            return if (timeline.isCombined || timeline.timelineType.isAtOrigin()) {
                EMPTY
            } else fromIds(timeline.actor.user.actorIds)
        }

        fun actorIdsOf(actors: Collection<Actor>): SqlIds {
            return SqlIds(actors.stream().map { actor: Actor -> actor.actorId }.collect(Collectors.toList()))
        }

        fun fromId(id: Long): SqlIds {
            return SqlIds(setOf<Long>(id))
        }

        fun fromIds(ids: Collection<Long>): SqlIds {
            return SqlIds(ids)
        }

        fun fromIds(vararg ids: Long?): SqlIds {
            return SqlIds(*ids)
        }

        fun getInexistentId(): String {
            return "=" + Long.MIN_VALUE
        }
    }
}
