/*
 * Copyright (c) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.andstatus.app.R
import org.andstatus.app.actor.ActorAdapter
import org.andstatus.app.graphics.AvatarView
import org.andstatus.app.net.social.ActivityType
import org.andstatus.app.note.NoteAdapter
import org.andstatus.app.timeline.BaseTimelineAdapter
import org.andstatus.app.timeline.TimelineData
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.MyUrlSpan

/**
 * @author yvolk@yurivolkov.com
 */
class ActivityAdapter(private val contextMenu: ActivityContextMenu, listData: TimelineData<ActivityViewItem?>?) :
        BaseTimelineAdapter<ActivityViewItem?>(contextMenu.note.getMyContext(), listData) {
    private val actorAdapter: ActorAdapter?
    private val noteAdapter: NoteAdapter?
    private val objActorAdapter: ActorAdapter?
    private val showReceivedTime: Boolean

    internal enum class LayoutType {
        ACTOR, ACTOR_NOTE, ACTOR_OBJACTOR, ACTOR_ACTOR, NOTE;

        fun isActorShown(): Boolean {
            return when (this) {
                NOTE -> false
                else -> true
            }
        }

        fun isNoteShown(): Boolean {
            return when (this) {
                NOTE, ACTOR_NOTE -> true
                else -> false
            }
        }

        fun isObjActorViewShown(): Boolean {
            return when (this) {
                ACTOR_OBJACTOR, ACTOR_ACTOR -> true
                else -> false
            }
        }

        fun isObjActorShown(): Boolean {
            return when (this) {
                ACTOR_OBJACTOR -> true
                else -> false
            }
        }
    }

    override fun getView(position: Int, convertView: View?, viewGroup: ViewGroup?): View? {
        val view = getEmptyView(convertView)
        view.setOnClickListener(this)
        setPosition(view, position)
        val item = getItem(position)
        val layoutType = calcLayoutType(item)
        showActor(view, item, layoutType)
        val noteView = view.findViewById<ViewGroup?>(R.id.note_wrapper)
        if (layoutType.isNoteShown()) {
            noteAdapter.populateView(view, item.noteViewItem, showReceivedTime, position)
            noteView.setOnCreateContextMenuListener(contextMenu.note)
            noteView.setOnClickListener(noteAdapter)
            noteView.visibility = View.VISIBLE
        } else {
            noteAdapter.removeReplyToMeMarkerView(view)
            noteView.visibility = View.GONE
        }
        val objActorView = view.findViewById<ViewGroup?>(R.id.actor_wrapper)
        if (layoutType.isObjActorViewShown()) {
            if (layoutType.isObjActorShown()) {
                objActorAdapter.populator.populateView(objActorView, item.getObjActorItem(), position)
                objActorView.setOnCreateContextMenuListener(contextMenu.objActor)
                objActorView.setOnClickListener(objActorAdapter)
            } else {
                objActorAdapter.populator.populateView(objActorView, item.actor, position)
                objActorView.setOnCreateContextMenuListener(contextMenu.actor)
                objActorView.setOnClickListener(actorAdapter)
            }
            objActorView.visibility = View.VISIBLE
        } else {
            objActorView.visibility = View.GONE
        }
        return view
    }

    private fun calcLayoutType(item: ActivityViewItem?): LayoutType? {
        return if (item.noteViewItem.id == 0L) {
            if (item.getObjActorItem().id == 0L) {
                LayoutType.ACTOR
            } else if (myContext.users().isMe(item.getObjActorItem().actor)) {
                LayoutType.ACTOR_ACTOR
            } else {
                LayoutType.ACTOR_OBJACTOR
            }
        } else {
            if (item.activityType == ActivityType.CREATE || item.activityType == ActivityType.UPDATE) {
                LayoutType.NOTE
            } else {
                LayoutType.ACTOR_NOTE
            }
        }
    }

    private fun getEmptyView(convertView: View?): ViewGroup? {
        if (convertView == null) {
            val viewGroup = LayoutInflater.from(contextMenu.note.activity)
                    .inflate(R.layout.activity, null) as ViewGroup
            noteAdapter.setupButtons(viewGroup)
            return viewGroup
        }
        convertView.setBackgroundResource(0)
        val noteIndented = convertView.findViewById<View?>(R.id.note_indented)
        noteIndented.setBackgroundResource(0)
        if (showAvatars) {
            convertView.findViewById<View?>(R.id.actor_avatar_image).visibility = View.GONE
            convertView.findViewById<View?>(R.id.avatar_image).visibility = View.GONE
        }
        return convertView as ViewGroup?
    }

    private fun showActor(view: ViewGroup?, item: ActivityViewItem?, layoutType: LayoutType?) {
        val actorView = view.findViewById<ViewGroup?>(R.id.action_wrapper)
        if (layoutType.isActorShown()) {
            item.noteViewItem.hideTheReblogger(item.actor.actor)
            item.getObjActorItem().hideFollowedBy(item.actor.actor)
            if (showAvatars && layoutType != LayoutType.ACTOR_ACTOR) {
                val avatarView: AvatarView = view.findViewById(R.id.actor_avatar_image)
                item.actor.showAvatar(contextMenu.actor.activity, avatarView)
            }
            MyUrlSpan.Companion.showText(view, R.id.action_title,
                    (if (layoutType == LayoutType.ACTOR_ACTOR) item.actor.actor.actorNameInTimeline else item.actor.actor.actorNameInTimelineWithOrigin) +
                            " " + item.activityType.getActedTitle(contextMenu.actor.activity) +
                            if (layoutType == LayoutType.ACTOR_ACTOR) " " + item.getObjActorItem().actor.actorNameInTimelineWithOrigin else "",
                    false, false)
            MyUrlSpan.Companion.showText(view, R.id.action_details,
                    item.getDetails(contextMenu.actor.activity, showReceivedTime), false, false)
            actorView.setOnCreateContextMenuListener(contextMenu.actor)
            actorView.setOnClickListener(actorAdapter)
            actorView.visibility = View.VISIBLE
        } else {
            actorView.visibility = View.GONE
        }
    }

    init {
        actorAdapter = ActorAdapter(contextMenu.actor, TimelineDataActorWrapper(listData))
        noteAdapter = NoteAdapter(contextMenu.note, TimelineDataNoteWrapper(listData))
        objActorAdapter = ActorAdapter(contextMenu.objActor, TimelineDataObjActorWrapper(listData))
        showReceivedTime = listData.params.timelineType == TimelineType.UNREAD_NOTIFICATIONS
    }
}