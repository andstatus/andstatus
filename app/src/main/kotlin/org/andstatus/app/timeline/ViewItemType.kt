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
package org.andstatus.app.timeline

import org.andstatus.app.activity.ActivityViewItem
import org.andstatus.app.actor.ActorViewItem
import org.andstatus.app.note.ConversationViewItem
import org.andstatus.app.note.NoteViewItem
import org.andstatus.app.service.QueueData
import org.andstatus.app.timeline.meta.TimelineType

enum class ViewItemType(val emptyViewItem: ViewItem<*>) {
    ACTIVITY(ActivityViewItem.EMPTY),
    NOTE(NoteViewItem.EMPTY),
    ACTOR(ActorViewItem.EMPTY),
    CONVERSATION(ConversationViewItem.EMPTY),
    COMMANDS_QUEUE(QueueData.EMPTY),
    UNKNOWN(EmptyViewItem.EMPTY);

    companion object {
        fun fromTimelineType(timelineType: TimelineType): ViewItemType {
            return if (timelineType.showsActivities()) {
                ACTIVITY
            } else when (timelineType) {
                TimelineType.ACTORS -> ACTOR
                TimelineType.CONVERSATION -> CONVERSATION
                TimelineType.COMMANDS_QUEUE -> COMMANDS_QUEUE
                TimelineType.UNKNOWN -> UNKNOWN
                else -> NOTE
            }
        }
    }
}
