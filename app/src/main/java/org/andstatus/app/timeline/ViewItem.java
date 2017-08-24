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

import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.v4.util.Pair;

import org.andstatus.app.msg.KeywordsFilter;
import org.andstatus.app.timeline.meta.TimelineType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ViewItem {
    private final List<ViewItem> children = new ArrayList<>();

    @NonNull
    public static ViewItem getEmpty(@NonNull TimelineType timelineType) {
        return ViewItemType.fromTimelineType(timelineType).emptyViewItem;
    }

    public long getId() {
        return 0;
    }

    public long getDate() {
        return 0;
    }

    @NonNull
    public final Collection<ViewItem> getChildren() {
        return children;
    }

    @NonNull
    public DuplicationLink duplicates(ViewItem other) {
        return DuplicationLink.NONE;
    }

    public boolean isCollapsed() {
        return !getChildren().isEmpty();
    }

    void collapse(ViewItem child) {
        this.getChildren().addAll(child.getChildren());
        child.getChildren().clear();
        this.getChildren().add(child);
    }

    /** @return 1. The item and 2. if it should be skipped (filtered out) */
    @NonNull
    public Pair<ViewItem,Boolean> fromCursor(Cursor cursor, KeywordsFilter keywordsFilter,
                                                   KeywordsFilter searchQuery, boolean hideRepliesNotToMeOrFriends) {
        return new Pair<>(getEmpty(TimelineType.UNKNOWN), true);
    }
}
