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
import org.andstatus.app.net.social.MbObjectType;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.origin.Origin;
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
    public static final ActivityViewItem EMPTY = new ActivityViewItem();
    private long id = 0;
    Origin origin = Origin.EMPTY;
    private long insDate = 0;
    private long updatedDate = 0;
    public MbActivityType activityType = MbActivityType.EMPTY;

    private long messageId;
    long userId;
    private long objActivityId;

    MbObjectType objectType = MbObjectType.EMPTY;
    UserViewItem actor = UserViewItem.EMPTY;
    public MessageViewItem message = MessageViewItem.EMPTY;
    public UserViewItem user = UserViewItem.EMPTY;

    @Override
    public long getId() {
        return id;
    }

    @Override
    public long getDate() {
        return insDate;
    }

    @Override
    public int compareTo(@NonNull ActivityViewItem o) {
        // TODO: replace with Long#compare
        return insDate < o.insDate ? -1 : (insDate == o.insDate ? 0 : 1);
    }

    @NonNull
    @Override
    public ActivityViewItem fromCursor(Cursor cursor) {
        return new ActivityViewItem().loadCursor(cursor);
    }

    private ActivityViewItem loadCursor(Cursor cursor) {
        long startTime = System.currentTimeMillis();
        id = DbUtils.getLong(cursor, ActivityTable.ACTIVITY_ID);
        activityType = MbActivityType.fromId(DbUtils.getLong(cursor, ActivityTable.ACTIVITY_TYPE));
        insDate = DbUtils.getLong(cursor, ActivityTable.INS_DATE);
        updatedDate = DbUtils.getLong(cursor, ActivityTable.UPDATED_DATE);
        origin = MyContextHolder.get().persistentOrigins().fromId(DbUtils.getLong(cursor, ActivityTable.ORIGIN_ID));
        actor = UserViewItem.fromMbUser(MbUser.fromOriginAndUserId(origin.getId(),
                DbUtils.getLong(cursor, ActivityTable.ACTOR_ID)));
        messageId = DbUtils.getLong(cursor, ActivityTable.MSG_ID);
        userId = DbUtils.getLong(cursor, ActivityTable.USER_ID);
        objActivityId = DbUtils.getLong(cursor, ActivityTable.OBJ_ACTIVITY_ID);
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(this, ": " + (System.currentTimeMillis() - startTime) + "ms");
        }
        if (messageId != 0) {
            message = MessageViewItem.fromCursorRow(MyContextHolder.get(), cursor);
        }
        return this;
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

    String getDetails(Context context) {
        StringBuilder builder = new StringBuilder(RelativeTime.getDifference(context, updatedDate));
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
}
