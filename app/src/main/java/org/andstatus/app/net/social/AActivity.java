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

import static org.andstatus.app.util.UriUtils.TEMP_OID_PREFIX;

/** Activity in a sense of Activity Streams https://www.w3.org/TR/activitystreams-core/ */
public class AActivity extends AObject {
    public static final AActivity EMPTY = from(Actor.EMPTY, ActivityType.EMPTY);
    private TimelinePosition timelinePosition = TimelinePosition.EMPTY;
    private long updatedDate = 0;
    private long id = 0;
    private long insDate = 0;

    @NonNull
    public final Actor accountActor;
    @NonNull
    public final ActivityType type;
    private Actor actor = Actor.EMPTY;

    // Objects of the Activity may be of several types...
    @NonNull
    private Note note = Note.EMPTY;
    @NonNull
    private Actor objActor = Actor.EMPTY;
    private AActivity aActivity = AActivity.EMPTY;

    /** Some additional attributes may appear from "My account's" (authenticated User's) point of view */
    private TriState subscribedByMe = TriState.UNKNOWN;
    private TriState interacted = TriState.UNKNOWN;
    private NotificationEventType interactionEventType = NotificationEventType.EMPTY;
    private TriState notified = TriState.UNKNOWN;
    private Actor notifiedActor = Actor.EMPTY;
    private NotificationEventType newNotificationEventType = NotificationEventType.EMPTY;

    @NonNull
    public static AActivity fromInner(@NonNull Actor actor, @NonNull ActivityType type,
                                      @NonNull AActivity innerActivity) {
        final AActivity activity = new AActivity(innerActivity.accountActor, type);
        activity.setActor(actor);
        activity.setActivity(innerActivity);
        activity.setUpdatedDate(innerActivity.getUpdatedDate() + 60);
        return activity;
    }

    @NonNull
    public static AActivity from(@NonNull Actor accountActor, @NonNull ActivityType type) {
        return new AActivity(accountActor, type);
    }

    @NonNull
    public static AActivity newPartialNote(@NonNull Actor accountActor, Actor actor, String noteOid) {
        return newPartialNote(accountActor, actor, noteOid, 0, DownloadStatus.UNKNOWN);
    }

    @NonNull
    public static AActivity newPartialNote(@NonNull Actor accountActor, Actor actor, String noteOid,
                                           long updatedDate, DownloadStatus status) {
        AActivity activity = from(accountActor, ActivityType.UPDATE);
        activity.setActor(actor);
        activity.setTimelinePosition(
                (UriUtils.isTempOid(noteOid) ? TEMP_OID_PREFIX : "")
                + (StringUtils.isEmpty(noteOid) ? "" : activity.getActorPrefix())
                + noteOid);
        final Note note = Note.fromOriginAndOid(activity.accountActor.origin, noteOid, status);
        activity.setNote(note);
        note.setUpdatedDate(updatedDate);
        activity.setUpdatedDate(updatedDate);
        return activity;
    }

    private AActivity(Actor accountActor, ActivityType type) {
        this.accountActor = accountActor == null ? Actor.EMPTY : accountActor;
        this.type = type == null ? ActivityType.EMPTY : type;
    }

    @NonNull
    public Actor getActor() {
        if (!actor.isEmpty()) {
            return actor;
        }
        switch (getObjectType()) {
            case ACTOR:
                return objActor;
            case NOTE:
                return getAuthor();
            default:
                return Actor.EMPTY;
        }
    }

    public void setActor(Actor actor) {
        if (this == EMPTY && Actor.EMPTY != actor) {
            throw new IllegalStateException("Cannot set Actor of EMPTY Activity");
        }
        this.actor = actor == null ? Actor.EMPTY : actor;
    }

    public boolean isAuthorActor() {
        return getActor().isSame(getAuthor());
    }

    @NonNull
    public Actor getAuthor() {
        if (isEmpty()) {
            return Actor.EMPTY;
        }
        if (getObjectType().equals(AObjectType.NOTE)) {
            switch (type) {
                case CREATE:
                case UPDATE:
                case DELETE:
                    return actor;
                default:
                    return Actor.EMPTY;
            }
        }
        return getActivity().getAuthor();
    }

    public void setAuthor(Actor author) {
        if (getActivity() != EMPTY) getActivity().setActor(author);
    }

    public boolean isMyActorOrAuthor(@NonNull MyContext myContext) {
        return myContext.users().containsMe(getActor()) || myContext.users().containsMe(getAuthor());
    }

    @NonNull
    public AObjectType getObjectType() {
        if (note.nonEmpty()) {
            return AObjectType.NOTE;
        } else if (objActor.nonEmpty()) {
            return AObjectType.ACTOR;
        } else if (getActivity().nonEmpty()) {
            return AObjectType.ACTIVITY;
        } else {
            return AObjectType.EMPTY;
        }
    }

    public boolean isEmpty() {
        return this == AActivity.EMPTY ||  type == ActivityType.EMPTY || getObjectType() == AObjectType.EMPTY
                || accountActor.isEmpty();
    }

    public TimelinePosition getTimelinePosition() {
        return timelinePosition;
    }

    private void setTempTimelinePosition() {
        setTimelinePosition("");
    }

    public AActivity setTimelinePosition(String strPosition) {
        this.timelinePosition = new TimelinePosition(TextUtils.isEmpty(strPosition) ? getTempPositionString() : strPosition);
        return this;
    }

    @NonNull
    private String getTempPositionString() {
        return TEMP_OID_PREFIX
                + getActorPrefix()
                + type.name().toLowerCase() + "-"
                + (StringUtils.nonEmpty(getNote().oid) ? getNote().oid + "-" : "")
                + MyLog.uniqueDateTimeFormatted();
    }

    @NonNull
    private String getActorPrefix() {
        return StringUtils.nonEmpty(actor.oid) ? actor.oid + "-"
                : (StringUtils.nonEmpty(accountActor.oid) ? accountActor.oid + "-"
                    : "");
    }

    public long getUpdatedDate() {
        return updatedDate;
    }

    public void setUpdatedDate(long updatedDate) {
        this.updatedDate = updatedDate;
    }

    @NonNull
    public Note getNote() {
        return Optional.ofNullable(note).filter(msg -> msg != Note.EMPTY).orElseGet(this::getNestedNote);
    }

    @NonNull
    private Note getNestedNote() {
        /* Referring to the nested note allows to implement an activity, which has both Actor and Author.
            Actor of the nested note is an Author.
            In a database we will have 2 activities: one for each actor! */
        switch (type) {
            case ANNOUNCE:
            case CREATE:
            case DELETE:
            case LIKE:
            case UPDATE:
            case UNDO_ANNOUNCE:
            case UNDO_LIKE:
                // Check for null even though it looks like result couldn't be null.
                // May be needed for AActivity.EMPTY activity...
                return Optional.ofNullable(getActivity().getNote()).orElse(Note.EMPTY);
            default:
                return Note.EMPTY;
        }
    }

    public void setNote(Note note) {
        if (this == EMPTY && Note.EMPTY != note) {
            throw new IllegalStateException("Cannot set Note of EMPTY Activity");
        }
        this.note = note == null ? Note.EMPTY : note;
    }

    @NonNull
    public Actor getObjActor() {
        return objActor;
    }

    public void setObjActor(Actor actor) {
        if (this == EMPTY && Actor.EMPTY != actor) {
            throw new IllegalStateException("Cannot set objActor of EMPTY Activity");
        }
        this.objActor = actor == null ? Actor.EMPTY : actor;
    }

    public Audience recipients() {
        return getNote().audience();
    }

    @NonNull
    public AActivity getActivity() {
        return (aActivity == null) ? AActivity.EMPTY : aActivity;
    }

    public void setActivity(AActivity activity) {
        if (this == EMPTY && AActivity.EMPTY != activity) {
            throw new IllegalStateException("Cannot set Activity of EMPTY Activity");
        }
        if (activity != null) {
            this.aActivity = activity;
        }
    }

    @Override
    public String toString() {
        if (this == EMPTY) {
            return "EMPTY";
        }
        return "AActivity{"
                + (isEmpty() ? "(empty), " : "")
                + type
                + ", id:" + id
                + ", oid:" + timelinePosition
                + ", updated:" + MyLog.debugFormatOfDate(updatedDate)
                + ", me:" + (accountActor.isEmpty() ? "EMPTY" : accountActor.oid)
                + (subscribedByMe.known ? (subscribedByMe == TriState.TRUE ? ", subscribed" : ", NOT subscribed") : "" )
                + (interacted.isTrue ? ", interacted" : "" )
                + (notifiedActor.isEmpty() ? "" : " actor:" + objActor)
                + (notified.isTrue ? ", notified" : "" )
                + (newNotificationEventType.isEmpty() ? "" : ", " + newNotificationEventType)
                + (actor.isEmpty() ? "" : ", \nactor:" + actor)
                + (note.isEmpty() ? "" : ", \nnote:" + note)
                + (getActivity().isEmpty() ? "" : ", \nactivity:" + getActivity())
                + (objActor.isEmpty() ? "" : ", objActor:" + objActor)
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

    public static AActivity fromCursor(MyContext myContext, Cursor cursor) {
        AActivity activity = from(
                myContext.accounts().fromActorId(DbUtils.getLong(cursor, ActivityTable.ACCOUNT_ID)).getActor(),
                ActivityType.fromId(DbUtils.getLong(cursor, ActivityTable.ACTIVITY_TYPE)));

        activity.id = DbUtils.getLong(cursor, ActivityTable._ID);
        activity.timelinePosition = new TimelinePosition(DbUtils.getString(cursor, ActivityTable.ACTIVITY_OID));
        activity.actor = Actor.fromOriginAndActorId(activity.accountActor.origin,
                DbUtils.getLong(cursor, ActivityTable.ACTOR_ID));
        activity.note = Note.fromOriginAndOid(activity.accountActor.origin, "", DownloadStatus.UNKNOWN);
        activity.objActor = Actor.fromOriginAndActorId(activity.accountActor.origin,
                DbUtils.getLong(cursor, ActivityTable.OBJ_ACTOR_ID));
        activity.aActivity = AActivity.from(activity.accountActor, ActivityType.EMPTY);
        activity.aActivity.id =  DbUtils.getLong(cursor, ActivityTable.OBJ_ACTIVITY_ID);
        activity.subscribedByMe = DbUtils.getTriState(cursor, ActivityTable.SUBSCRIBED);
        activity.interacted = DbUtils.getTriState(cursor, ActivityTable.INTERACTED);
        activity.interactionEventType = NotificationEventType.fromId(
                DbUtils.getLong(cursor, ActivityTable.INTERACTION_EVENT));
        activity.notified = DbUtils.getTriState(cursor, ActivityTable.NOTIFIED);
        activity.notifiedActor = Actor.fromOriginAndActorId(activity.accountActor.origin,
                DbUtils.getLong(cursor, ActivityTable.NOTIFIED_ACTOR_ID));
        activity.newNotificationEventType = NotificationEventType.fromId(
                DbUtils.getLong(cursor, ActivityTable.NEW_NOTIFICATION_EVENT));
        activity.updatedDate = DbUtils.getLong(cursor, ActivityTable.UPDATED_DATE);
        activity.insDate = DbUtils.getLong(cursor, ActivityTable.INS_DATE);
        return activity;
    }
    
    public long save(MyContext myContext) {
        if (wontSave(myContext)) return id;
        if (updatedDate > 0) calculateInteraction(myContext);
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
        if (isEmpty() || (type.equals(ActivityType.UPDATE) && getObjectType().equals(AObjectType.ACTOR))
                || (timelinePosition.isEmpty() && getId() != 0)) {
            MyLog.v(this, "Won't save " + this);
            return true;
        }

        if (MyAsyncTask.isUiThread()) {
            throw new IllegalStateException("Saving activity on the Main thread " + toString());
        }
        if (accountActor.actorId == 0) {
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
                    final Pair<Long, ActivityType> favAndType = MyQuery.noteIdToLastFavoriting(myContext.getDatabase(),
                            getNote().noteId, accountActor.actorId);
                    if ((favAndType.second.equals(ActivityType.LIKE) && type == ActivityType.LIKE)
                            || (favAndType.second.equals(ActivityType.UNDO_LIKE) && type == ActivityType.UNDO_LIKE)
                            ) {
                        MyLog.v(this, "Skipped as already " + type.name() + " " + this);
                        return true;
                    }
                    break;
                case ANNOUNCE:
                case UNDO_ANNOUNCE:
                    final Pair<Long, ActivityType> reblAndType = MyQuery.noteIdToLastReblogging(myContext.getDatabase(),
                            getNote().noteId, accountActor.actorId);
                    if ((reblAndType.second.equals(ActivityType.ANNOUNCE) && type == ActivityType.ANNOUNCE)
                            || (reblAndType.second.equals(ActivityType.UNDO_ANNOUNCE) && type == ActivityType.UNDO_ANNOUNCE)
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
            id = MyQuery.oidToId(myContext, OidEnum.ACTIVITY_OID, accountActor.origin.getId(),
                    timelinePosition.getPosition());
        }
        if (id != 0) {
            return;
        }
        if (getNote().noteId != 0 && (type == ActivityType.UPDATE || type == ActivityType.CREATE)) {
            id = MyQuery.conditionToLongColumnValue(myContext.getDatabase(),"", ActivityTable.TABLE_NAME,
                    ActivityTable._ID, ActivityTable.NOTE_ID + "=" + getNote().noteId + " AND "
            + ActivityTable.ACTIVITY_TYPE + "=" + type.id);
        }
    }

    private void calculateInteraction(MyContext myContext) {
        newNotificationEventType = calculateNotificationEventType(myContext);
        interacted = TriState.fromBoolean(newNotificationEventType != NotificationEventType.EMPTY);
        interactionEventType = newNotificationEventType;
        notifiedActor = calculateNotifiedActor(myContext, newNotificationEventType);
        if (isNotified().toBoolean(true)) {
            this.notified = TriState.fromBoolean(myContext.getNotifier().isEnabled(newNotificationEventType));
        }
    }

    private NotificationEventType calculateNotificationEventType(MyContext myContext) {
        if (myContext.users().containsMe(getActor())) return NotificationEventType.EMPTY;
        if (getNote().getPublic().isFalse) {
            return NotificationEventType.PRIVATE;
        } else if(myContext.users().containsMe(getNote().audience().getRecipients()) && !isMyActorOrAuthor(myContext)) {
            return NotificationEventType.MENTION;
        } else if (type == ActivityType.ANNOUNCE && myContext.users().containsMe(getAuthor())) {
            return NotificationEventType.ANNOUNCE;
        } else if ((type == ActivityType.LIKE || type == ActivityType.UNDO_LIKE)
                && myContext.users().containsMe(getAuthor())) {
            return NotificationEventType.LIKE;
        } else if ((type == ActivityType.FOLLOW || type == ActivityType.UNDO_FOLLOW)
                && myContext.users().containsMe(getObjActor())) {
            return NotificationEventType.FOLLOW;
        } else {
            return NotificationEventType.EMPTY;
        }
    }

    private Actor calculateNotifiedActor(MyContext myContext, NotificationEventType event) {
        switch (event) {
            case MENTION:
                return myContext.users().myActors.values().stream()
                        .filter(actor -> getNote().audience().contains(actor)).findFirst().orElse(Actor.EMPTY);
            case ANNOUNCE:
            case LIKE:
                return getAuthor();
            case FOLLOW:
                return getObjActor();
            case PRIVATE:
                return accountActor;
            default:
                return Actor.EMPTY;
        }
    }

    private ContentValues toContentValues() {
        ContentValues values = new ContentValues();
        values.put(ActivityTable.ORIGIN_ID, accountActor.origin.getId());
        values.put(ActivityTable.ACCOUNT_ID, accountActor.actorId);
        values.put(ActivityTable.ACTOR_ID, getActor().actorId);
        values.put(ActivityTable.NOTE_ID, getNote().noteId);
        values.put(ActivityTable.OBJ_ACTOR_ID, getObjActor().actorId);
        values.put(ActivityTable.OBJ_ACTIVITY_ID, getActivity().id);
        if (subscribedByMe.known) {
            values.put(ActivityTable.SUBSCRIBED, subscribedByMe.id);
        }
        if (interacted.known) {
            values.put(ActivityTable.INTERACTED, interacted.id);
            values.put(ActivityTable.INTERACTION_EVENT, interactionEventType.id);
        }
        if (notified.known) {
            values.put(ActivityTable.NOTIFIED, notified.id);
            values.put(ActivityTable.NEW_NOTIFICATION_EVENT, newNotificationEventType.id);
        }
        if (notifiedActor.nonEmpty()) {
            values.put(ActivityTable.NOTIFIED_ACTOR_ID, notifiedActor.actorId);
        }
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
                final MyAccount myActorAccount = myContext.accounts().fromActorOfAnyOrigin(actor);
                if (myActorAccount.isValid()) {
                    MyLog.v(this, myActorAccount + " " + type
                            + " '" + getNote().oid + "' " + I18n.trimTextAt(getNote().getContent(), 80));
                    MyProvider.updateNoteFavorited(myContext, actor.origin, getNote().noteId);
                }
                break;
            default:
                break;
        }
    }

    public NotificationEventType getNewNotificationEventType() {
        return newNotificationEventType;
    }

    public void setId(long id) {
        this.id = id;
    }
}
