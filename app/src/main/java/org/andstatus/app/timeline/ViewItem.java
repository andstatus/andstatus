package org.andstatus.app.timeline;
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

import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.v4.util.Pair;

import org.andstatus.app.activity.ActivityViewItem;
import org.andstatus.app.msg.KeywordsFilter;
import org.andstatus.app.msg.MessageViewItem;
import org.andstatus.app.timeline.meta.TimelineType;

import java.util.Collection;
import java.util.Collections;

public interface ViewItem {
    static ViewItem getEmpty(@NonNull TimelineType timelineType) {
        switch (timelineType) {
            case NOTIFICATIONS:
                return ActivityViewItem.EMPTY;
            case UNKNOWN:
                return EmptyViewItem.EMPTY;
            default:
                return MessageViewItem.EMPTY;
        }
    }

    long getId();
    long getDate();

    default Collection<ViewItem> getChildren() {
        return Collections.emptyList();
    }

    default DuplicationLink duplicates(ViewItem other) {
        return DuplicationLink.NONE;
    }

    default boolean isCollapsed() {
        return !getChildren().isEmpty();
    }

    default void collapse(ViewItem child) {
        this.getChildren().addAll(child.getChildren());
        child.getChildren().clear();
        this.getChildren().add(child);
    }

    @NonNull
    default Pair<ViewItem,Boolean> fromCursor(Cursor cursor, KeywordsFilter keywordsFilter,
                                                   KeywordsFilter searchQuery, boolean hideRepliesNotToMeOrFriends) {
        return new Pair<>(getEmpty(TimelineType.UNKNOWN), true);
    }
}
