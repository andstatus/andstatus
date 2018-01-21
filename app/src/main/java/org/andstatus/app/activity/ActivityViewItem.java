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
import org.andstatus.app.msg.MessageViewItem;
import org.andstatus.app.net.social.MbActivityType;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.timeline.DuplicationLink;
import org.andstatus.app.timeline.TimelineFilter;
import org.andstatus.app.timeline.ViewItem;
import org.andstatus.app.user.UserViewItem;
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
    public final MbActivityType activityType;

    private long messageId;
    public final long userId;

    UserViewItem actor = UserViewItem.EMPTY;
    public final MessageViewItem message;
    private UserViewItem user = UserViewItem.EMPTY;

    protected ActivityViewItem(boolean isEmpty) {
        super(isEmpty);
        origin = Origin.EMPTY;
        activityType  = MbActivityType.EMPTY;
        userId = 0;
        message = MessageViewItem.EMPTY;
    }

    protected ActivityViewItem(Cursor cursor) {
        super(false);
        long startTime = System.currentTimeMillis();
        id = DbUtils.getLong(cursor, ActivityTable.ACTIVITY_ID);
        origin = MyContextHolder.get().persistentOrigins().fromId(DbUtils.getLong(cursor, ActivityTable.ORIGIN_ID));
        activityType = MbActivityType.fromId(DbUtils.getLong(cursor, ActivityTable.ACTIVITY_TYPE));
        updatedDate = DbUtils.getLong(cursor, ActivityTable.UPDATED_DATE);
        actor = UserViewItem.fromMbUser(MbUser.fromOriginAndUserId(origin.getId(),
                DbUtils.getLong(cursor, ActivityTable.ACTOR_ID)));
        messageId = DbUtils.getLong(cursor, ActivityTable.MSG_ID);
        userId = DbUtils.getLong(cursor, ActivityTable.USER_ID);
        if (userId != 0) {
            user = UserViewItem.fromUserId(origin, userId);
        }
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(this, ": " + (System.currentTimeMillis() - startTime) + "ms");
        }
        if (messageId == 0) {
            message = MessageViewItem.EMPTY;
        } else {
            message = MessageViewItem.EMPTY.getNew().fromCursorRow(MyContextHolder.get(), cursor);
            message.setParent(this);
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
        if (messageId !=0) {
            return message.matches(filter);
        } else if (userId != 0) {
            return user.matches(filter);
        }
        return true;
    }

    @NonNull
    @Override
    public DuplicationLink duplicates(@NonNull ActivityViewItem other) {
        if (isEmpty() || other.isEmpty() || duplicatesByChildren(other) == DuplicationLink.NONE)
            return DuplicationLink.NONE;
        if (activityType != other.activityType && other.activityType == MbActivityType.UPDATE)
            return DuplicationLink.IS_DUPLICATED;
        return updatedDate >= other.updatedDate ? DuplicationLink.IS_DUPLICATED : DuplicationLink.DUPLICATES;
    }

    @NonNull
    protected DuplicationLink duplicatesByChildren(@NonNull ActivityViewItem other) {
        if (messageId !=0) {
            return message.duplicates(other.message);
        } else if (userId != 0) {
            return user.duplicates(other.user);
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
        return actor.getWebFingerIdOrUserName() + " " + activityType + " " + (messageId == 0
                ? user
                : message
        );
    }

    public UserViewItem getUser() {
        return user;
    }

    public void setUser(UserViewItem user) {
        this.user = user;
    }
}
