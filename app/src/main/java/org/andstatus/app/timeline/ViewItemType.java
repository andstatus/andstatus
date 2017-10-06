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
import org.andstatus.app.msg.ConversationViewItem;
import org.andstatus.app.msg.MessageViewItem;
import org.andstatus.app.service.QueueData;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.user.UserViewItem;

public enum ViewItemType {
    ACTIVITY(ActivityViewItem.EMPTY),
    MESSAGE(MessageViewItem.EMPTY),
    USER(UserViewItem.EMPTY),
    CONVERSATION(ConversationViewItem.EMPTY),
    COMMANDS_QUEUE(QueueData.EMPTY),
    UNKNOWN(EmptyViewItem.EMPTY);

    final ViewItem emptyViewItem;

    ViewItemType(ViewItem emptyViewItem) {
        this.emptyViewItem = emptyViewItem;
    }

    static ViewItemType fromTimelineType(@NonNull TimelineType timelineType) {
        switch (timelineType) {
            case NOTIFICATIONS:
            case USER:
            case SENT:
                return ACTIVITY;
            case USERS:
                return USER;
            case CONVERSATION:
                return CONVERSATION;
            case COMMANDS_QUEUE:
                return COMMANDS_QUEUE;
            case UNKNOWN:
                return UNKNOWN;
            default:
                return MESSAGE;
        }
    }
}
