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

package org.andstatus.app.timeline;

import android.support.annotation.NonNull;

import org.andstatus.app.activity.ActivityViewItem;
import org.andstatus.app.note.ConversationViewItem;
import org.andstatus.app.note.NoteViewItem;
import org.andstatus.app.service.QueueData;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.actor.ActorViewItem;

public enum ViewItemType {
    ACTIVITY(ActivityViewItem.EMPTY),
    NOTE(NoteViewItem.EMPTY),
    ACTOR(ActorViewItem.EMPTY),
    CONVERSATION(ConversationViewItem.EMPTY),
    COMMANDS_QUEUE(QueueData.EMPTY),
    UNKNOWN(EmptyViewItem.EMPTY);

    final ViewItem emptyViewItem;

    ViewItemType(ViewItem emptyViewItem) {
        this.emptyViewItem = emptyViewItem;
    }

    static ViewItemType fromTimelineType(@NonNull TimelineType timelineType) {
        if (timelineType.showsActivities()) {
            return ACTIVITY;
        }
        switch (timelineType) {
            case ACTORS:
                return ACTOR;
            case CONVERSATION:
                return CONVERSATION;
            case COMMANDS_QUEUE:
                return COMMANDS_QUEUE;
            case UNKNOWN:
                return UNKNOWN;
            default:
                return NOTE;
        }
    }
}
