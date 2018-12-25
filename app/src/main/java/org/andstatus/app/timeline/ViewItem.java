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

import android.content.Context;
import android.database.Cursor;
import androidx.annotation.NonNull;

import org.andstatus.app.R;
import org.andstatus.app.actor.ActorListLoader;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.IsEmpty;
import org.andstatus.app.util.MyStringBuilder;
import org.andstatus.app.util.RelativeTime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.andstatus.app.util.RelativeTime.SOME_TIME_AGO;

public class ViewItem<T extends ViewItem<T>> implements IsEmpty {
    private final List<T> children = new ArrayList<>();
    private final boolean isEmpty;
    private ViewItem parent = EmptyViewItem.EMPTY;
    protected long insertedDate = 0;
    public final long updatedDate;

    protected ViewItem(boolean isEmpty, long updatedDate) {
        this.isEmpty = isEmpty;
        this.updatedDate = updatedDate;
    }

    @NonNull
    public static <T extends ViewItem<T>> T getEmpty(@NonNull TimelineType timelineType) {
        return (T) ViewItemType.fromTimelineType(timelineType).emptyViewItem;
    }

    public long getId() {
        return 0;
    }

    public long getDate() {
        return 0;
    }

    @NonNull
    public final Collection<T> getChildren() {
        return children;
    }

    @NonNull
    public DuplicationLink duplicates(Timeline timeline, @NonNull T other) {
        return DuplicationLink.NONE;
    }

    public boolean isCollapsed() {
        return getChildrenCount() > 0;
    }

    void collapse(T child) {
        this.getChildren().addAll(child.getChildren());
        child.getChildren().clear();
        this.getChildren().add(child);
    }

    @NonNull
    public T fromCursor(MyContext myContext, Cursor cursor) {
        return getEmpty(TimelineType.UNKNOWN);
    }

    public boolean matches(TimelineFilter filter) {
        return true;
    }

    @Override
    public boolean isEmpty() {
        return isEmpty;
    }

    protected int getChildrenCount() {
        return isEmpty() ? 0 : Integer.max(getParent().getChildrenCount(), getChildren().size());
    }

    public void setParent(ViewItem parent) {
        this.parent = parent;
    }
    @NonNull
    public ViewItem getParent() {
        return parent == null ? EmptyViewItem.EMPTY : parent;
    }

    public long getTopmostId() {
        return getParent().isEmpty() ? getId() : getParent().getId();
    }

    @NonNull
    protected MyStringBuilder getMyStringBuilderWithTime(Context context, boolean showReceivedTime) {
        final String difference = RelativeTime.getDifference(context, updatedDate);
        MyStringBuilder builder = MyStringBuilder.of(difference);
        if (showReceivedTime && updatedDate > SOME_TIME_AGO && insertedDate > updatedDate) {
            final String receivedDifference = RelativeTime.getDifference(context, insertedDate);
            if (!receivedDifference.equals(difference)) {
                builder.withSpace("(" + String.format(context.getText(R.string.received_sometime_ago).toString(),
                        receivedDifference) + ")");
            }
        }
        return builder;
    }

    public void addActorsToLoad(ActorListLoader loader) {
        // Empty
    }

    public void setLoadedActors(ActorListLoader loader) {
        // Empty
    }

    @Override
    public int hashCode() {
        int result = Long.hashCode(getId());
        return 31 * result + Long.hashCode(getDate());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        T that = (T) o;
        return getId() == that.getId() && getDate() == that.getDate();
    }
}
