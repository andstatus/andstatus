/*
 * Copyright (c) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.activity

import android.content.Context
import android.database.Cursor
import org.andstatus.app.actor.ActorViewItem
import org.andstatus.app.actor.ActorsLoader
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.data.DbUtils
import org.andstatus.app.database.table.ActivityTable
import org.andstatus.app.net.social.ActivityType
import org.andstatus.app.net.social.Actor
import org.andstatus.app.note.NoteViewItem
import org.andstatus.app.origin.Origin
import org.andstatus.app.timeline.DuplicationLink
import org.andstatus.app.timeline.TimelineFilter
import org.andstatus.app.timeline.ViewItem
import org.andstatus.app.timeline.meta.Timeline
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.RelativeTime

/** View on ActivityStream
 * @author yvolk@yurivolkov.com
 */
class ActivityViewItem : ViewItem<ActivityViewItem?>, Comparable<ActivityViewItem?> {
    private var id: Long = 0
    val origin: Origin?
    val activityType: ActivityType?
    private var noteId: Long = 0
    val objActorId: Long
    var actor: ActorViewItem? = ActorViewItem.Companion.EMPTY
    val noteViewItem: NoteViewItem?
    private var objActorItem: ActorViewItem? = ActorViewItem.Companion.EMPTY

    protected constructor(isEmpty: Boolean) : super(isEmpty, RelativeTime.DATETIME_MILLIS_NEVER) {
        origin =  Origin.EMPTY
        activityType = ActivityType.EMPTY
        objActorId = 0
        noteViewItem = NoteViewItem.Companion.EMPTY
    }

    protected constructor(myContext: MyContext?, cursor: Cursor?) : super(false, DbUtils.getLong(cursor, ActivityTable.UPDATED_DATE)) {
        id = DbUtils.getLong(cursor, ActivityTable.ACTIVITY_ID)
        origin = myContext.origins().fromId(DbUtils.getLong(cursor, ActivityTable.ORIGIN_ID))
        activityType = ActivityType.Companion.fromId(DbUtils.getLong(cursor, ActivityTable.ACTIVITY_TYPE))
        insertedDate = DbUtils.getLong(cursor, ActivityTable.INS_DATE)
        actor = ActorViewItem.Companion.fromActor(Actor.Companion.fromId(origin, DbUtils.getLong(cursor, ActivityTable.ACTOR_ID)))
        noteId = DbUtils.getLong(cursor, ActivityTable.NOTE_ID)
        objActorId = DbUtils.getLong(cursor, ActivityTable.OBJ_ACTOR_ID)
        if (objActorId != 0L) {
            objActorItem = ActorViewItem.Companion.fromActorId(origin, objActorId)
        }
        if (noteId == 0L) {
            noteViewItem = NoteViewItem.Companion.EMPTY
        } else {
            noteViewItem = NoteViewItem.Companion.EMPTY.fromCursor(myContext, cursor)
            noteViewItem.setParent(this)
            if (MyPreferences.isShowDebuggingInfoInUi()) {
                MyStringBuilder.Companion.appendWithSpace(noteViewItem.detailsSuffix, "(actId=$id)")
            }
        }
    }

    override fun getId(): Long {
        return id
    }

    override fun getDate(): Long {
        return updatedDate
    }

    override operator fun compareTo(o: ActivityViewItem): Int {
        return java.lang.Long.compare(updatedDate, o.updatedDate)
    }

    override fun fromCursor(myContext: MyContext?, cursor: Cursor?): ActivityViewItem {
        return ActivityViewItem(myContext, cursor)
    }

    override fun matches(filter: TimelineFilter?): Boolean {
        if (noteId != 0L) {
            return noteViewItem.matches(filter)
        } else if (objActorId != 0L) {
            return objActorItem.matches(filter)
        }
        return true
    }

    override fun duplicates(timeline: Timeline?, preferredOrigin: Origin?, other: ActivityViewItem): DuplicationLink {
        if (isEmpty || other.isEmpty) return DuplicationLink.NONE
        val link = duplicatesByChildren(timeline, preferredOrigin, other)
        if (link == DuplicationLink.NONE) return link
        if (activityType != other.activityType && other.activityType == ActivityType.UPDATE) {
            return DuplicationLink.IS_DUPLICATED
        }
        return if (updatedDate > other.updatedDate) DuplicationLink.IS_DUPLICATED else if (updatedDate < other.updatedDate) DuplicationLink.DUPLICATES else link
    }

    protected fun duplicatesByChildren(timeline: Timeline?, preferredOrigin: Origin?, other: ActivityViewItem): DuplicationLink {
        if (noteId != 0L) {
            return noteViewItem.duplicates(timeline, preferredOrigin, other.noteViewItem)
        } else if (objActorId != 0L) {
            return objActorItem.duplicates(timeline, preferredOrigin, other.objActorItem)
        }
        return super.duplicates(timeline, preferredOrigin, other)
    }

    fun getDetails(context: Context?, showReceivedTime: Boolean): String? {
        val builder = getMyStringBuilderWithTime(context, showReceivedTime)
        if (isCollapsed) {
            builder.withSpace("(+$childrenCount)")
        }
        if (MyPreferences.isShowDebuggingInfoInUi()) {
            builder.withSpace("(actId=$id)")
        }
        return builder.toString()
    }

    override fun toString(): String {
        return if (this === EMPTY) {
            "EMPTY"
        } else actor.getActor().uniqueNameWithOrigin + " " + activityType + " " + if (noteId == 0L) objActorItem else noteViewItem
    }

    fun getActor(): ActorViewItem? {
        return actor
    }

    fun getObjActorItem(): ActorViewItem? {
        return objActorItem
    }

    fun setObjActorItem(objActorItem: ActorViewItem?) {
        this.objActorItem = objActorItem
    }

    override fun addActorsToLoad(loader: ActorsLoader?) {
        noteViewItem.addActorsToLoad(loader)
        if (activityType != ActivityType.CREATE && activityType != ActivityType.UPDATE) {
            loader.addActorToList(actor.getActor())
        }
        loader.addActorIdToList(origin, objActorId)
    }

    override fun setLoadedActors(loader: ActorsLoader?) {
        noteViewItem.setLoadedActors(loader)
        if (activityType != ActivityType.CREATE && activityType != ActivityType.UPDATE) {
            val index = loader.getList().indexOf(actor)
            if (index >= 0) {
                actor = loader.getList()[index]
            }
        }
        if (objActorId != 0L) {
            val index = loader.getList().indexOf(getObjActorItem())
            if (index >= 0) {
                setObjActorItem(loader.getList()[index])
            }
            getObjActorItem().setParent(this)
        }
    }

    companion object {
        val EMPTY: ActivityViewItem = ActivityViewItem(true)
    }
}