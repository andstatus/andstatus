/*
 * Copyright (c) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.widget;

import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.v4.util.Pair;

import org.andstatus.app.ViewItem;
import org.andstatus.app.activity.ActivityViewItem;
import org.andstatus.app.msg.KeywordsFilter;
import org.andstatus.app.msg.MessageViewItem;
import org.andstatus.app.timeline.TimelineType;

import java.util.Collection;
import java.util.Collections;

public interface TimelineViewItem extends ViewItem {

    static TimelineViewItem getEmpty(@NonNull TimelineType timelineType) {
        switch (timelineType) {
            case NOTIFICATIONS:
                return ActivityViewItem.EMPTY;
            case UNKNOWN:
                return EmptyViewItem.EMPTY;
            default:
                return MessageViewItem.EMPTY;
        }
    }

    default Collection<TimelineViewItem> getChildren() {
        return Collections.emptyList();
    }

    default DuplicationLink duplicates(TimelineViewItem other) {
        return DuplicationLink.NONE;
    }

    default boolean isCollapsed() {
        return !getChildren().isEmpty();
    }

    default void collapse(TimelineViewItem child) {
        this.getChildren().addAll(child.getChildren());
        child.getChildren().clear();
        this.getChildren().add(child);
    }

    @NonNull
    default Pair<TimelineViewItem,Boolean> fromCursor(Cursor cursor, KeywordsFilter keywordsFilter,
                                                      KeywordsFilter searchQuery, boolean hideRepliesNotToMeOrFriends) {
        return new Pair<>(getEmpty(TimelineType.UNKNOWN), true);
    }
}
