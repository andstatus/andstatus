/*
 * Copyright (C) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.net.social;

import android.content.ContentValues;
import android.database.Cursor;
import android.support.annotation.NonNull;

import org.andstatus.app.context.MyContext;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.database.ActivityTable;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.util.MyLog;

/** Activity in a sense of Activity Streams https://www.w3.org/TR/activitystreams-core/ */
public class MbActivity {
    public static final MbActivity EMPTY = from(MbUser.EMPTY, MbActivityType.EMPTY);
    private TimelinePosition timelinePosition = TimelinePosition.EMPTY;
    private long timelineDate = 0;
    private long id = 0;
    private long insDate = 0;

    @NonNull
    public final MbUser accountUser;
    private MbUser actor = MbUser.EMPTY;
    @NonNull
    public final MbActivityType type;

    // Objects of the Activity may be of several types...
    @NonNull
    private MbMessage mbMessage = MbMessage.EMPTY;
    @NonNull
    private MbUser mbUser = MbUser.EMPTY;
    private MbActivity mbActivity = null;

    public static MbActivity from(MbUser accountUser, MbActivityType type) {
        return new MbActivity(accountUser, type);
    }

    private MbActivity(MbUser accountUser, MbActivityType type) {
        this.accountUser = accountUser == null ? MbUser.EMPTY : accountUser;
        this.type = type == null ? MbActivityType.EMPTY : type;
    }

    @NonNull
    public MbUser getActor() {
        if (!actor.isEmpty()) {
            return actor;
        }
        switch (getObjectType()) {
            case USER:
                return mbUser;
            case MESSAGE:
                return getMessage().getAuthor();
            default:
                return MbUser.EMPTY;
        }
    }

    public void setActor(MbUser actor) {
        this.actor = actor == null ? MbUser.EMPTY : actor;
    }

    public boolean isAuthorActor() {
        return getActor().oid.equals(getMessage().getAuthor().oid);
    }

    public boolean isActorMe() {
        return getActor().oid.equals(accountUser.oid);
    }

    public boolean isAuthorMe() {
        return getMessage().getAuthor().oid.equals(accountUser.oid);
    }

    @NonNull
    public MbObjectType getObjectType() {
        if (!mbMessage.isEmpty()) {
            return MbObjectType.MESSAGE;
        } else if (!mbUser.isEmpty()) {
            return MbObjectType.USER;
        } else if (mbActivity!= null && !mbActivity.isEmpty()) {
            return MbObjectType.ACTIVITY;
        } else {
            return MbObjectType.EMPTY;
        }
    }

    public boolean isEmpty() {
        return type == MbActivityType.EMPTY || getObjectType() == MbObjectType.EMPTY || accountUser.isEmpty();
    }

    public TimelinePosition getTimelinePosition() {
        return timelinePosition;
    }

    public void setTimelinePosition(String strPosition) {
        this.timelinePosition = new TimelinePosition(strPosition);
    }

    public long getTimelineDate() {
        return timelineDate;
    }

    public void setTimelineDate(long timelineDate) {
        this.timelineDate = timelineDate;
    }

    @NonNull
    public MbMessage getMessage() {
        return mbMessage;
    }

    public void setMessage(MbMessage mbMessage) {
        this.mbMessage = mbMessage == null ? MbMessage.EMPTY : mbMessage;
        if (timelinePosition.isEmpty() && !this.mbMessage.isEmpty()) {
            timelinePosition = new TimelinePosition(this.mbMessage.oid);
        }
    }

    @NonNull
    public MbUser getUser() {
        return mbUser;
    }

    public void setUser(MbUser mbUser) {
        this.mbUser = mbUser == null ? MbUser.EMPTY : mbUser;
        if (timelinePosition.isEmpty() && !this.mbUser.isEmpty()) {
            timelinePosition = new TimelinePosition(this.mbUser.oid);
        }
    }

    @NonNull
    public MbActivity getActivity() {
        return mbActivity == null ? EMPTY : mbActivity;
    }

    @Override
    public String toString() {
        return "MbActivity{" +
                type +
                (id == 0 ? "" : ", id=" + id) +
                ", oid=" + timelinePosition +
                (timelineDate == 0 ? "" : ", timelineDate=" + timelineDate) +
                (accountUser.isEmpty() ? "" : ", me=" + accountUser.getUserName()) +
                (actor.isEmpty() ? "" : ", actor=" + actor) +
                (mbMessage.isEmpty() ? "" : ", message=" + mbMessage) +
                (getActivity().isEmpty() ? "" : ", activity=" + getActivity()) +
                (mbUser.isEmpty() ? "" : ", user=" + mbUser) +
                '}';
    }

    public long getId() {
        return id;
    }

    public static MbActivity fromCursor(MyContext myContext, Cursor cursor) {
        MbActivity activity = from(myContext.persistentAccounts()
                        .fromUserId(DbUtils.getLong(cursor, ActivityTable.ACCOUNT_ID)).toPartialUser(),
                MbActivityType.fromId(DbUtils.getLong(cursor, ActivityTable.ACTIVITY_TYPE)));

        activity.id = DbUtils.getLong(cursor, ActivityTable._ID);
        activity.timelinePosition = new TimelinePosition(DbUtils.getString(cursor, ActivityTable.ACTIVITY_OID));
        activity.actor = MbUser.fromOriginAndUserId(activity.accountUser.originId,
                DbUtils.getLong(cursor, ActivityTable.ACTOR_ID));
        activity.mbMessage = MbMessage.fromOriginAndOid(activity.accountUser.originId, "", DownloadStatus.UNKNOWN);
        activity.mbUser = MbUser.fromOriginAndUserId(activity.accountUser.originId,
                DbUtils.getLong(cursor, ActivityTable.USER_ID));
        activity.mbActivity = MbActivity.from(activity.accountUser, MbActivityType.EMPTY);
        activity.mbActivity.id =  DbUtils.getLong(cursor, ActivityTable.OBJ_ACTIVITY_ID);
        activity.timelineDate = DbUtils.getLong(cursor, ActivityTable.UPDATED_DATE);
        activity.insDate = DbUtils.getLong(cursor, ActivityTable.INS_DATE);
        return activity;
    }
    
    public long save(MyContext myContext) {
        if (wontSave()) {
            return -1;
        }
        if (MyAsyncTask.isUiThread()) {
            throw new IllegalStateException("Saving activity on the Main thread " + toString());
        }
        ContentValues contentValues = new ContentValues();
        toContentValues(contentValues);
        if (getId() == 0) {
            id = DbUtils.addRowWithRetry(myContext, ActivityTable.TABLE_NAME, contentValues, 3);
            MyLog.v(this, "Added " + this);
        } else {
            DbUtils.updateRowWithRetry(myContext, ActivityTable.TABLE_NAME, getId(), contentValues, 3);
        }
        return id;
    }

    private boolean wontSave() {
        return isEmpty() || timelinePosition.isEmpty()
                || (type.equals(MbActivityType.UPDATE) && getObjectType().equals(MbObjectType.USER));
    }

    private void toContentValues(ContentValues values) {
        values.put(ActivityTable.ORIGIN_ID, accountUser.originId);
        values.put(ActivityTable.ACTIVITY_OID, timelinePosition.getPosition());
        values.put(ActivityTable.ACCOUNT_ID, accountUser.userId);
        values.put(ActivityTable.ACTIVITY_TYPE, type.id);
        values.put(ActivityTable.ACTOR_ID, getActor().userId);
        values.put(ActivityTable.MSG_ID, getMessage().msgId);
        values.put(ActivityTable.USER_ID, getUser().userId);
        values.put(ActivityTable.OBJ_ACTIVITY_ID, getActivity().id);
        values.put(ActivityTable.UPDATED_DATE, timelineDate);
        if (getId() == 0) {
            insDate = MyLog.uniqueCurrentTimeMS();
            values.put(ActivityTable.INS_DATE, insDate);
        }
    }
}
