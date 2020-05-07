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

package org.andstatus.app.activity;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;

import org.andstatus.app.actor.ActorListLoader;
import org.andstatus.app.actor.ActorViewItem;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.net.social.ActivityType;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.note.NoteViewItem;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.timeline.DuplicationLink;
import org.andstatus.app.timeline.TimelineFilter;
import org.andstatus.app.timeline.ViewItem;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.util.MyStringBuilder;

import static org.andstatus.app.context.MyContextHolder.myContextHolder;
import static org.andstatus.app.util.RelativeTime.DATETIME_MILLIS_NEVER;

/** View on ActivityStream
 * @author yvolk@yurivolkov.com
 */
public class ActivityViewItem extends ViewItem<ActivityViewItem> implements Comparable<ActivityViewItem> {
    public static final ActivityViewItem EMPTY = new ActivityViewItem(true);
    private long id = 0;
    public final Origin origin;
    public final ActivityType activityType;

    private long noteId;
    public final long objActorId;

    ActorViewItem actor = ActorViewItem.EMPTY;
    public final NoteViewItem noteViewItem;
    private ActorViewItem objActorItem = ActorViewItem.EMPTY;

    protected ActivityViewItem(boolean isEmpty) {
        super(isEmpty, DATETIME_MILLIS_NEVER);
        origin = Origin.EMPTY;
        activityType  = ActivityType.EMPTY;
        objActorId = 0;
        noteViewItem = NoteViewItem.EMPTY;
    }

    protected ActivityViewItem(Cursor cursor) {
        super(false, DbUtils.getLong(cursor, ActivityTable.UPDATED_DATE));
        id = DbUtils.getLong(cursor, ActivityTable.ACTIVITY_ID);
        origin = myContextHolder.getNow().origins().fromId(DbUtils.getLong(cursor, ActivityTable.ORIGIN_ID));
        activityType = ActivityType.fromId(DbUtils.getLong(cursor, ActivityTable.ACTIVITY_TYPE));
        insertedDate = DbUtils.getLong(cursor, ActivityTable.INS_DATE);
        actor = ActorViewItem.fromActor(Actor.fromId(origin, DbUtils.getLong(cursor, ActivityTable.ACTOR_ID)));
        noteId = DbUtils.getLong(cursor, ActivityTable.NOTE_ID);
        objActorId = DbUtils.getLong(cursor, ActivityTable.OBJ_ACTOR_ID);
        if (objActorId != 0) {
            objActorItem = ActorViewItem.fromActorId(origin, objActorId);
        }
        if (noteId == 0) {
            noteViewItem = NoteViewItem.EMPTY;
        } else {
            noteViewItem = NoteViewItem.EMPTY.fromCursor(myContextHolder.getNow(), cursor);
            noteViewItem.setParent(this);
            if (MyPreferences.isShowDebuggingInfoInUi()) {
                MyStringBuilder.appendWithSpace(noteViewItem.detailsSuffix, "(actId=" + id + ")");
            }
        }
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public long getDate() {
        return updatedDate;
    }

    @Override
    public int compareTo(@NonNull ActivityViewItem o) {
        return Long.compare(updatedDate, o.updatedDate);
    }

    @NonNull
    @Override
    public ActivityViewItem fromCursor(MyContext myContext, Cursor cursor) {
        return new ActivityViewItem(cursor);
    }

    @Override
    public boolean matches(TimelineFilter filter) {
        if (noteId !=0) {
            return noteViewItem.matches(filter);
        } else if (objActorId != 0) {
            return objActorItem.matches(filter);
        }
        return true;
    }

    @NonNull
    @Override
    public DuplicationLink duplicates(Timeline timeline, Origin preferredOrigin, @NonNull ActivityViewItem other) {
        if (isEmpty() || other.isEmpty()) return DuplicationLink.NONE;

        DuplicationLink link = duplicatesByChildren(timeline, preferredOrigin, other);
        if (link == DuplicationLink.NONE) return link;

        if (activityType != other.activityType && other.activityType == ActivityType.UPDATE) {
            return DuplicationLink.IS_DUPLICATED;
        }
        return updatedDate > other.updatedDate
                ? DuplicationLink.IS_DUPLICATED
                : updatedDate < other.updatedDate
                    ? DuplicationLink.DUPLICATES
                    : link;
    }

    @NonNull
    protected DuplicationLink duplicatesByChildren(Timeline timeline, Origin preferredOrigin, @NonNull ActivityViewItem other) {
        if (noteId !=0) {
            return noteViewItem.duplicates(timeline, preferredOrigin, other.noteViewItem);
        } else if (objActorId != 0) {
            return objActorItem.duplicates(timeline, preferredOrigin, other.objActorItem);
        }
        return super.duplicates(timeline, preferredOrigin, other);
    }

    protected String getDetails(Context context, boolean showReceivedTime) {
        MyStringBuilder builder = getMyStringBuilderWithTime(context, showReceivedTime);
        if (isCollapsed()) {
            builder.withSpace("(+" + getChildrenCount() + ")");
        }
        if (MyPreferences.isShowDebuggingInfoInUi()) {
            builder.withSpace("(actId=" + id + ")");
        }
        return builder.toString();
    }

    @Override
    public String toString() {
        if (this == EMPTY) {
            return "EMPTY";
        }
        return actor.getActor().getUniqueNameWithOrigin() + " " + activityType + " " + (noteId == 0
                ? objActorItem
                : noteViewItem
        );
    }

    public ActorViewItem getActor() {
        return actor;
    }

    public ActorViewItem getObjActorItem() {
        return objActorItem;
    }

    public void setObjActorItem(ActorViewItem objActorItem) {
        this.objActorItem = objActorItem;
    }

    @Override
    public void addActorsToLoad(ActorListLoader loader) {
        noteViewItem.addActorsToLoad(loader);
        if (activityType != ActivityType.CREATE && activityType != ActivityType.UPDATE) {
            loader.addActorToList(actor.getActor());
        }
        loader.addActorIdToList(origin, objActorId);
    }

    @Override
    public void setLoadedActors(ActorListLoader loader) {
        noteViewItem.setLoadedActors(loader);
        if (activityType != ActivityType.CREATE && activityType != ActivityType.UPDATE) {
            int index = loader.getList().indexOf(actor);
            if (index >= 0) {
                actor = loader.getList().get(index);
            }
        }
        if (objActorId != 0) {
            int index = loader.getList().indexOf(getObjActorItem());
            if (index >= 0) {
                setObjActorItem(loader.getList().get(index));
            }
            getObjActorItem().setParent(this);
        }
    }
}
