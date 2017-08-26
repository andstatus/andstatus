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
import android.text.TextUtils;

import org.andstatus.app.context.MyContext;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.OidEnum;
import org.andstatus.app.database.ActivityTable;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.util.MyLog;

/** Activity in a sense of Activity Streams https://www.w3.org/TR/activitystreams-core/ */
public class MbActivity extends AObject {
    public static final MbActivity EMPTY = from(MbUser.EMPTY, MbActivityType.EMPTY);
    private TimelinePosition timelinePosition = TimelinePosition.EMPTY;
    private long updatedDate = 0;
    private long id = 0;
    private long insDate = 0;

    @NonNull
    public final MbUser accountUser;
    @NonNull
    public final MbActivityType type;
    private MbUser actor = MbUser.EMPTY;

    // Objects of the Activity may be of several types...
    @NonNull
    private MbMessage mbMessage = MbMessage.EMPTY;
    @NonNull
    private MbUser mbUser = MbUser.EMPTY;
    private MbActivity mbActivity = MbActivity.EMPTY;

    @NonNull
    public static MbActivity fromInner(@NonNull MbUser actor, @NonNull MbActivityType type,
                                       @NonNull MbActivity innerActivity) {
        final MbActivity activity = new MbActivity(innerActivity.accountUser, type);
        activity.setActor(actor);
        activity.setActivity(innerActivity);
        activity.setUpdatedDate(innerActivity.getUpdatedDate() + 60);
        activity.setTempTimelinePosition();
        return activity;
    }

    @NonNull
    public static MbActivity from(@NonNull MbUser accountUser, @NonNull MbActivityType type) {
        return new MbActivity(accountUser, type);
    }

    @NonNull
    public static MbActivity newPartialMessage(@NonNull MbUser accountUser, String msgOid) {
        return newPartialMessage(accountUser, msgOid, System.currentTimeMillis(), DownloadStatus.UNKNOWN);
    }

    @NonNull
    public static MbActivity newPartialMessage(@NonNull MbUser accountUser, String msgOid, long updatedDate, DownloadStatus status) {
        MbActivity activity = from(accountUser, MbActivityType.UPDATE);
        final MbMessage message = MbMessage.fromOriginAndOid(activity.accountUser.originId, msgOid, status);
        activity.setMessage(message);
        message.setUpdatedDate(updatedDate);
        activity.setUpdatedDate(updatedDate);
        activity.setTimelinePosition(msgOid);
        return activity;
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
                return getAuthor();
            default:
                return MbUser.EMPTY;
        }
    }

    public void setActor(MbUser actor) {
        this.actor = actor == null ? MbUser.EMPTY : actor;
    }

    public boolean isAuthorActor() {
        return getActor().oid.equals(getAuthor().oid);
    }

    @NonNull
    public MbUser getAuthor() {
        if (isEmpty()) {
            return MbUser.EMPTY;
        }
        if (getObjectType().equals(MbObjectType.MESSAGE)) {
            switch (type) {
                case CREATE:
                case UPDATE:
                case DELETE:
                    return actor;
                default:
                    return MbUser.EMPTY;
            }
        }
        return getActivity().getAuthor();
    }

    public void setAuthor(MbUser author) {
        getActivity().setActor(author);
    }

    public boolean isActorMe() {
        return getActor().oid.equals(accountUser.oid);
    }

    public boolean isAuthorMe() {
        return getAuthor().oid.equals(accountUser.oid);
    }

    @NonNull
    public MbObjectType getObjectType() {
        if (mbMessage.nonEmpty()) {
            return MbObjectType.MESSAGE;
        } else if (mbUser.nonEmpty()) {
            return MbObjectType.USER;
        } else if (mbActivity.nonEmpty()) {
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

    public void setTempTimelinePosition() {
        setTimelinePosition("");
    }

    public void setTimelinePosition(String strPosition) {
        this.timelinePosition = new TimelinePosition(TextUtils.isEmpty(strPosition) ? getTempPositionString() : strPosition);
    }

    @NonNull
    private String getTempPositionString() {
        return MbUser.TEMP_OID_PREFIX + actor.oid + "-" + type.name() + "-"
                + (TextUtils.isEmpty(getMessage().oid) ? MyLog.uniqueCurrentTimeMS() : getMessage().oid)
                + "_" + updatedDate;
    }

    public long getUpdatedDate() {
        return updatedDate;
    }

    public void setUpdatedDate(long updatedDate) {
        this.updatedDate = updatedDate;
    }

    @NonNull
    public MbMessage getMessage() {
        if (mbMessage != MbMessage.EMPTY) {
            return mbMessage;
        }
        /* Referring to the nested message allows to implement an activity, which has both Actor and Author.
            Author is Actor of the nested message.
            In a database we will have 2 activities: one for each actor! */
        switch (type) {
            case ANNOUNCE:
            case CREATE:
            case DELETE:
            case LIKE:
            case UPDATE:
            case UNDO_ANNOUNCE:
            case UNDO_LIKE:
                return getActivity().getMessage();
            default:
                return MbMessage.EMPTY;
        }
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

    public Audience recipients() {
        return getMessage().audience();
    }

    @NonNull
    public MbActivity getActivity() {
        return mbActivity;
    }

    public void setActivity(MbActivity activity) {
        this.mbActivity = activity;
    }

    @Override
    public String toString() {
        return "MbActivity{"
                + (isEmpty() ? "(empty), " : "")
                + type
                + ", id:" + id
                + ", oid:" + timelinePosition
                + ", updated:" + MyLog.debugFormatOfDate(updatedDate)
                + ", me:" + (accountUser.isEmpty() ? "EMPTY" : accountUser.oid)
                + (actor.isEmpty() ? "" : ", actor:" + actor)
                + (mbMessage.isEmpty() ? "" : ", message:" + mbMessage)
                + (getActivity().isEmpty() ? "" : ", activity:" + getActivity())
                + (mbUser.isEmpty() ? "" : ", user:" + mbUser)
                + '}';
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
        activity.updatedDate = DbUtils.getLong(cursor, ActivityTable.UPDATED_DATE);
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
        if (getId() == 0) {
            id = MyQuery.oidToId(myContext.getDatabase(), OidEnum.ACTIVITY_OID, accountUser.originId,
                    timelinePosition.getPosition());
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
        values.put(ActivityTable.UPDATED_DATE, updatedDate);
        if (getId() == 0) {
            insDate = MyLog.uniqueCurrentTimeMS();
            values.put(ActivityTable.INS_DATE, insDate);
        }
    }
}
