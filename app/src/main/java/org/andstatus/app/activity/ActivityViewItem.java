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
import android.support.annotation.NonNull;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.note.NoteViewItem;
import org.andstatus.app.net.social.ActivityType;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.timeline.DuplicationLink;
import org.andstatus.app.timeline.TimelineFilter;
import org.andstatus.app.timeline.ViewItem;
import org.andstatus.app.actor.ActorViewItem;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.RelativeTime;

/** View on ActivityStream
 * @author yvolk@yurivolkov.com
 */
public class ActivityViewItem extends ViewItem<ActivityViewItem> implements Comparable<ActivityViewItem> {
    public static final ActivityViewItem EMPTY = new ActivityViewItem(true);
    private long id = 0;
    public final Origin origin;
    private long updatedDate = 0;
    public final ActivityType activityType;

    private long noteId;
    public final long objActorId;

    ActorViewItem actor = ActorViewItem.EMPTY;
    public final NoteViewItem noteViewItem;
    private ActorViewItem objActorItem = ActorViewItem.EMPTY;

    protected ActivityViewItem(boolean isEmpty) {
        super(isEmpty);
        origin = Origin.EMPTY;
        activityType  = ActivityType.EMPTY;
        objActorId = 0;
        noteViewItem = NoteViewItem.EMPTY;
    }

    protected ActivityViewItem(Cursor cursor) {
        super(false);
        long startTime = System.currentTimeMillis();
        id = DbUtils.getLong(cursor, ActivityTable.ACTIVITY_ID);
        origin = MyContextHolder.get().persistentOrigins().fromId(DbUtils.getLong(cursor, ActivityTable.ORIGIN_ID));
        activityType = ActivityType.fromId(DbUtils.getLong(cursor, ActivityTable.ACTIVITY_TYPE));
        updatedDate = DbUtils.getLong(cursor, ActivityTable.UPDATED_DATE);
        actor = ActorViewItem.fromActor(Actor.fromOriginAndActorId(origin,
                DbUtils.getLong(cursor, ActivityTable.ACTOR_ID)));
        noteId = DbUtils.getLong(cursor, ActivityTable.MSG_ID);
        objActorId = DbUtils.getLong(cursor, ActivityTable.USER_ID);
        if (objActorId != 0) {
            objActorItem = ActorViewItem.fromActorId(origin, objActorId);
        }
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(this, ": " + (System.currentTimeMillis() - startTime) + "ms");
        }
        if (noteId == 0) {
            noteViewItem = NoteViewItem.EMPTY;
        } else {
            noteViewItem = NoteViewItem.EMPTY.getNew().fromCursorRow(MyContextHolder.get(), cursor);
            noteViewItem.setParent(this);
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
    public ActivityViewItem fromCursor(Cursor cursor) {
        return new ActivityViewItem(cursor);
    }

    @NonNull
    @Override
    public ActivityViewItem getNew() {
        return new ActivityViewItem(false);
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
    public DuplicationLink duplicates(@NonNull ActivityViewItem other) {
        if (isEmpty() || other.isEmpty() || duplicatesByChildren(other) == DuplicationLink.NONE)
            return DuplicationLink.NONE;
        if (activityType != other.activityType && other.activityType == ActivityType.UPDATE)
            return DuplicationLink.IS_DUPLICATED;
        return updatedDate >= other.updatedDate ? DuplicationLink.IS_DUPLICATED : DuplicationLink.DUPLICATES;
    }

    @NonNull
    protected DuplicationLink duplicatesByChildren(@NonNull ActivityViewItem other) {
        if (noteId !=0) {
            return noteViewItem.duplicates(other.noteViewItem);
        } else if (objActorId != 0) {
            return objActorItem.duplicates(other.objActorItem);
        }
        return super.duplicates(other);
    }

    String getDetails(Context context) {
        StringBuilder builder = new StringBuilder(RelativeTime.getDifference(context, updatedDate));
        if (isCollapsed()) {
            I18n.appendWithSpace(builder, "(+" + getChildrenCount() + ")");
        }
        if (MyPreferences.isShowDebuggingInfoInUi()) {
            I18n.appendWithSpace(builder, "(actId=" + id + ")");
        }
        return builder.toString();
    }

    @Override
    public String toString() {
        if (this == EMPTY) {
            return "EMPTY";
        }
        return actor.getWebFingerIdOrActorName() + " " + activityType + " " + (noteId == 0
                ? objActorItem
                : noteViewItem
        );
    }

    public ActorViewItem getObjActorItem() {
        return objActorItem;
    }

    public void setObjActorItem(ActorViewItem objActorItem) {
        this.objActorItem = objActorItem;
    }
}
