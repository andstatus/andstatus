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
import android.support.v4.util.Pair;
import android.text.TextUtils;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.OidEnum;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.notification.NotificationEventType;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtils;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.UriUtils;

import java.util.Optional;

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

    /** Some additional attributes may appear from "My account's" (authenticated User's) point of view */
    private TriState subscribedByMe = TriState.UNKNOWN;
    private TriState notified = TriState.UNKNOWN;
    private NotificationEventType notificationEventType = NotificationEventType.EMPTY;

    @NonNull
    public static MbActivity fromInner(@NonNull MbUser actor, @NonNull MbActivityType type,
                                       @NonNull MbActivity innerActivity) {
        final MbActivity activity = new MbActivity(innerActivity.accountUser, type);
        activity.setActor(actor);
        activity.setActivity(innerActivity);
        activity.setUpdatedDate(innerActivity.getUpdatedDate() + 60);
        return activity;
    }

    @NonNull
    public static MbActivity from(@NonNull MbUser accountUser, @NonNull MbActivityType type) {
        return new MbActivity(accountUser, type);
    }

    @NonNull
    public static MbActivity newPartialMessage(@NonNull MbUser accountUser, String msgOid) {
        return newPartialMessage(accountUser, msgOid, 0, DownloadStatus.UNKNOWN);
    }

    @NonNull
    public static MbActivity newPartialMessage(@NonNull MbUser accountUser, String msgOid,
                                               long updatedDate, DownloadStatus status) {
        MbActivity activity = from(accountUser, MbActivityType.UPDATE);
        activity.setTimelinePosition(msgOid);
        final MbMessage message = MbMessage.fromOriginAndOid(activity.accountUser.origin, msgOid, status);
        activity.setMessage(message);
        message.setUpdatedDate(updatedDate);
        activity.setUpdatedDate(updatedDate);
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
        if (this == EMPTY && MbUser.EMPTY != actor) {
            throw new IllegalStateException("Cannot set Actor of EMPTY Activity");
        }
        this.actor = actor == null ? MbUser.EMPTY : actor;
    }

    public boolean isAuthorActor() {
        return getActor().isSameUser(getAuthor());
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
        if (getActivity() != EMPTY) getActivity().setActor(author);
    }

    public boolean isMyActorOrAuthor(@NonNull MyContext myContext) {
        return myContext.persistentAccounts().isMe(getActor()) || myContext.persistentAccounts().isMe(getAuthor());
    }

    @NonNull
    public MbObjectType getObjectType() {
        if (mbMessage.nonEmpty()) {
            return MbObjectType.MESSAGE;
        } else if (mbUser.nonEmpty()) {
            return MbObjectType.USER;
        } else if (getActivity().nonEmpty()) {
            return MbObjectType.ACTIVITY;
        } else {
            return MbObjectType.EMPTY;
        }
    }

    public boolean isEmpty() {
        return this == MbActivity.EMPTY ||  type == MbActivityType.EMPTY || getObjectType() == MbObjectType.EMPTY
                || accountUser.isEmpty();
    }

    public TimelinePosition getTimelinePosition() {
        return timelinePosition;
    }

    private void setTempTimelinePosition() {
        setTimelinePosition("");
    }

    public void setTimelinePosition(String strPosition) {
        this.timelinePosition = new TimelinePosition(TextUtils.isEmpty(strPosition) ? getTempPositionString() : strPosition);
    }

    @NonNull
    private String getTempPositionString() {
        return UriUtils.TEMP_OID_PREFIX
                + (StringUtils.nonEmpty(actor.oid) ?  actor.oid + "-" : "")
                + type.name().toLowerCase() + "-"
                + (StringUtils.nonEmpty(getMessage().oid) ? getMessage().oid + "-" : "")
                + MyLog.uniqueDateTimeFormatted();
    }

    public long getUpdatedDate() {
        return updatedDate;
    }

    public void setUpdatedDate(long updatedDate) {
        this.updatedDate = updatedDate;
    }

    @NonNull
    public MbMessage getMessage() {
        return Optional.ofNullable(mbMessage).filter(msg -> msg != MbMessage.EMPTY).orElse(getNestedMessage());
    }

    @NonNull
    private MbMessage getNestedMessage() {
        /* Referring to the nested message allows to implement an activity, which has both Actor and Author.
            Actor of the nested message is an Author.
            In a database we will have 2 activities: one for each actor! */
        switch (type) {
            case ANNOUNCE:
            case CREATE:
            case DELETE:
            case LIKE:
            case UPDATE:
            case UNDO_ANNOUNCE:
            case UNDO_LIKE:
                return Optional.ofNullable(getActivity().getMessage()).orElse(MbMessage.EMPTY);
            default:
                return MbMessage.EMPTY;
        }
    }

    public void setMessage(MbMessage mbMessage) {
        if (this == EMPTY && MbMessage.EMPTY != mbMessage) {
            throw new IllegalStateException("Cannot set Message of EMPTY Activity");
        }
        this.mbMessage = mbMessage == null ? MbMessage.EMPTY : mbMessage;
    }

    @NonNull
    public MbUser getUser() {
        return mbUser;
    }

    public void setUser(MbUser mbUser) {
        if (this == EMPTY && MbUser.EMPTY != mbUser) {
            throw new IllegalStateException("Cannot set User of EMPTY Activity");
        }
        this.mbUser = mbUser == null ? MbUser.EMPTY : mbUser;
    }

    public Audience recipients() {
        return getMessage().audience();
    }

    @NonNull
    public MbActivity getActivity() {
        return (mbActivity == null) ? MbActivity.EMPTY : mbActivity;
    }

    public void setActivity(MbActivity activity) {
        if (this == EMPTY && MbActivity.EMPTY != activity) {
            throw new IllegalStateException("Cannot set Activity of EMPTY Activity");
        }
        if (activity != null) {
            this.mbActivity = activity;
        }
    }

    @Override
    public String toString() {
        if (this == EMPTY) {
            return "EMPTY";
        }
        return "MbActivity{"
                + (isEmpty() ? "(empty), " : "")
                + type
                + ", id:" + id
                + ", oid:" + timelinePosition
                + ", updated:" + MyLog.debugFormatOfDate(updatedDate)
                + ", me:" + (accountUser.isEmpty() ? "EMPTY" : accountUser.oid)
                + (subscribedByMe.known() ? (subscribedByMe == TriState.TRUE ? ", subscribed" : ", NOT subscribed") : "" )
                + (notified.known() ? (notified == TriState.TRUE ? ", notified" : ", NOT notified") : "" )
                + (notificationEventType.isEmpty() ? "" : ", " + notificationEventType)
                + (actor.isEmpty() ? "" : ", \nactor:" + actor)
                + (mbMessage.isEmpty() ? "" : ", \nmessage:" + mbMessage)
                + (getActivity().isEmpty() ? "" : ", \nactivity:" + getActivity())
                + (mbUser.isEmpty() ? "" : ", user:" + mbUser)
                + '}';
    }

    public long getId() {
        return id;
    }

    public TriState isSubscribedByMe() {
        return subscribedByMe;
    }

    public void setSubscribedByMe(TriState isSubscribed) {
        if (isSubscribed != null) this.subscribedByMe = isSubscribed;
    }

    public TriState isNotified() {
        return notified;
    }

    public void setNotified(TriState notified) {
        if (notified != null) this.notified = notified;
    }

    public static MbActivity fromCursor(MyContext myContext, Cursor cursor) {
        MbActivity activity = from(myContext.persistentAccounts()
                        .fromUserId(DbUtils.getLong(cursor, ActivityTable.ACCOUNT_ID)).toPartialUser(),
                MbActivityType.fromId(DbUtils.getLong(cursor, ActivityTable.ACTIVITY_TYPE)));

        activity.id = DbUtils.getLong(cursor, ActivityTable._ID);
        activity.timelinePosition = new TimelinePosition(DbUtils.getString(cursor, ActivityTable.ACTIVITY_OID));
        activity.actor = MbUser.fromOriginAndUserId(activity.accountUser.origin,
                DbUtils.getLong(cursor, ActivityTable.ACTOR_ID));
        activity.mbMessage = MbMessage.fromOriginAndOid(activity.accountUser.origin, "", DownloadStatus.UNKNOWN);
        activity.mbUser = MbUser.fromOriginAndUserId(activity.accountUser.origin,
                DbUtils.getLong(cursor, ActivityTable.USER_ID));
        activity.mbActivity = MbActivity.from(activity.accountUser, MbActivityType.EMPTY);
        activity.mbActivity.id =  DbUtils.getLong(cursor, ActivityTable.OBJ_ACTIVITY_ID);
        activity.subscribedByMe = DbUtils.getTriState(cursor, ActivityTable.SUBSCRIBED);
        activity.notified = DbUtils.getTriState(cursor, ActivityTable.NOTIFIED);
        activity.setNotificationEventType(NotificationEventType.fromId(
                DbUtils.getLong(cursor, ActivityTable.NEW_NOTIFICATION_EVENT)));
        activity.updatedDate = DbUtils.getLong(cursor, ActivityTable.UPDATED_DATE);
        activity.insDate = DbUtils.getLong(cursor, ActivityTable.INS_DATE);
        return activity;
    }
    
    public long save(MyContext myContext) {
        if (wontSave(myContext)) return id;
        calculateNotification(myContext);
        if (getId() == 0) {
            id = DbUtils.addRowWithRetry(myContext, ActivityTable.TABLE_NAME, toContentValues(), 3);
            MyLog.v(this, "Added " + this);
        } else {
            DbUtils.updateRowWithRetry(myContext, ActivityTable.TABLE_NAME, getId(), toContentValues(), 3);
            MyLog.v(this, "Updated " + this);
        }
        afterSave(myContext);
        return id;
    }

    private boolean wontSave(MyContext myContext) {
        if (isEmpty() || (type.equals(MbActivityType.UPDATE) && getObjectType().equals(MbObjectType.USER))
                || (timelinePosition.isEmpty() && getId() != 0)) {
            MyLog.v(this, "Won't save " + this);
            return true;
        }

        if (MyAsyncTask.isUiThread()) {
            throw new IllegalStateException("Saving activity on the Main thread " + toString());
        }
        if (accountUser.userId == 0) {
            throw new IllegalStateException("Account is unknown " + toString());
        }
        if (getId() == 0) {
            findExisting(myContext);
        }
        if (getId() != 0) {
            long storedUpdatedDate = MyQuery.idToLongColumnValue(
                    myContext.getDatabase(), ActivityTable.TABLE_NAME, ActivityTable.UPDATED_DATE, id);
            if (updatedDate <= storedUpdatedDate) {
                MyLog.v(this, "Skipped as not younger " + this);
                return true;
            }
            switch (type) {
                case LIKE:
                case UNDO_LIKE:
                    final Pair<Long, MbActivityType> favAndType = MyQuery.msgIdToLastFavoriting(myContext.getDatabase(),
                            getMessage().msgId, accountUser.userId);
                    if ((favAndType.second.equals(MbActivityType.LIKE) && type == MbActivityType.LIKE)
                            || (favAndType.second.equals(MbActivityType.UNDO_LIKE) && type == MbActivityType.UNDO_LIKE)
                            ) {
                        MyLog.v(this, "Skipped as already " + type.name() + " " + this);
                        return true;
                    }
                    break;
                case ANNOUNCE:
                case UNDO_ANNOUNCE:
                    final Pair<Long, MbActivityType> reblAndType = MyQuery.msgIdToLastReblogging(myContext.getDatabase(),
                            getMessage().msgId, accountUser.userId);
                    if ((reblAndType.second.equals(MbActivityType.ANNOUNCE) && type == MbActivityType.ANNOUNCE)
                            || (reblAndType.second.equals(MbActivityType.UNDO_ANNOUNCE) && type == MbActivityType.UNDO_ANNOUNCE)
                            ) {
                        MyLog.v(this, "Skipped as already " + type.name() + " " + this);
                        return true;
                    }
                    break;
                default:
                    break;
            }
            if (timelinePosition.isTemp()) {
                MyLog.v(this, "Skipped as temp oid " + this);
                return true;
            }
        }
        return false;
    }

    private void findExisting(MyContext myContext) {
        if (!timelinePosition.isEmpty()) {
            id = MyQuery.oidToId(myContext.getDatabase(), OidEnum.ACTIVITY_OID, accountUser.origin.getId(),
                    timelinePosition.getPosition());
        }
        if (id != 0) {
            return;
        }
        if (getMessage().msgId != 0 && (type == MbActivityType.UPDATE || type == MbActivityType.CREATE)) {
            id = MyQuery.conditionToLongColumnValue(myContext.getDatabase(),"", ActivityTable.TABLE_NAME,
                    ActivityTable._ID, ActivityTable.MSG_ID + "=" + getMessage().msgId + " AND "
            + ActivityTable.ACTIVITY_TYPE + "=" + type.id);
        }
    }

    private void calculateNotification(MyContext myContext) {
        if (getUpdatedDate() < 1
                || isNotified().equals(TriState.FALSE)
                || myContext.persistentAccounts().isMe(getActor())) return;
        final NotificationEventType event;
        if(myContext.getNotifier().isEnabled(NotificationEventType.MENTION)
                && getMessage().audience().hasMyAccount(myContext)
                && !isMyActorOrAuthor(myContext)) {
            event = NotificationEventType.MENTION;
        } else if (myContext.getNotifier().isEnabled(NotificationEventType.ANNOUNCE)
                && type == MbActivityType.ANNOUNCE
                && myContext.persistentAccounts().isMe(getAuthor())) {
            event = NotificationEventType.ANNOUNCE;
        } else if (myContext.getNotifier().isEnabled(NotificationEventType.LIKE)
                && (type == MbActivityType.LIKE || type == MbActivityType.UNDO_LIKE)
                && myContext.persistentAccounts().isMe(getAuthor())) {
            event = NotificationEventType.LIKE;
        } else if (myContext.getNotifier().isEnabled(NotificationEventType.FOLLOW)
                && (type == MbActivityType.FOLLOW || type == MbActivityType.UNDO_FOLLOW)
                && myContext.persistentAccounts().isMe(getUser())) {
            event = NotificationEventType.FOLLOW;
        } else if (myContext.getNotifier().isEnabled(NotificationEventType.PRIVATE)
                && getMessage().isPrivate()) {
            event = NotificationEventType.PRIVATE;
        } else {
            return;
        }
        setNotificationEventType(event);
    }

    private ContentValues toContentValues() {
        ContentValues values = new ContentValues();
        values.put(ActivityTable.ORIGIN_ID, accountUser.origin.getId());
        values.put(ActivityTable.ACCOUNT_ID, accountUser.userId);
        values.put(ActivityTable.ACTOR_ID, getActor().userId);
        values.put(ActivityTable.MSG_ID, getMessage().msgId);
        values.put(ActivityTable.USER_ID, getUser().userId);
        values.put(ActivityTable.OBJ_ACTIVITY_ID, getActivity().id);
        if (subscribedByMe.known()) {
            values.put(ActivityTable.SUBSCRIBED, subscribedByMe.id);
        }
        if (notified.known()) {
            values.put(ActivityTable.NOTIFIED, notified.id);
        }
        values.put(ActivityTable.NEW_NOTIFICATION_EVENT, notificationEventType.id);
        values.put(ActivityTable.UPDATED_DATE, updatedDate);
        if (getId() == 0) {
            values.put(ActivityTable.ACTIVITY_TYPE, type.id);
            if (timelinePosition.isEmpty()) {
                setTempTimelinePosition();
            }
            insDate = MyLog.uniqueCurrentTimeMS();
            values.put(ActivityTable.INS_DATE, insDate);
        }
        if (!timelinePosition.isEmpty()) {
            values.put(ActivityTable.ACTIVITY_OID, timelinePosition.getPosition());
        }
        return values;
    }

    private void afterSave(MyContext myContext) {
        switch (type) {
            case LIKE:
            case UNDO_LIKE:
                final MyAccount myActorAccount = myContext.persistentAccounts().getUserFor(actor);
                if (myActorAccount.isValid()) {
                    MyLog.v(this, myActorAccount + " " + type
                            + " '" + getMessage().oid + "' " + I18n.trimTextAt(getMessage().getBody(), 80));
                    MyProvider.updateMessageFavorited(myContext, accountUser.origin, getMessage().msgId);
                }
                break;
            default:
                break;
        }
    }

    public void setNotificationEventType(@NonNull NotificationEventType notificationEventType) {
        this.notificationEventType = notificationEventType;
        if (notificationEventType != NotificationEventType.EMPTY) setNotified(TriState.TRUE);
    }

    public NotificationEventType getNotificationEventType() {
        return notificationEventType;
    }
}
