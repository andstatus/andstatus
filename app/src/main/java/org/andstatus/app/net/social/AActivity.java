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

import androidx.annotation.NonNull;
import androidx.core.util.Pair;

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
import org.andstatus.app.util.StringUtil;
import org.andstatus.app.util.TriState;

import java.util.Optional;

import io.vavr.control.Try;

import static org.andstatus.app.origin.OriginConfig.MAX_ATTACHMENTS_DEFAULT;
import static org.andstatus.app.util.RelativeTime.DATETIME_MILLIS_NEVER;
import static org.andstatus.app.util.RelativeTime.SOME_TIME_AGO;

/** Activity in a sense of Activity Streams https://www.w3.org/TR/activitystreams-core/ */
public class AActivity extends AObject {
    public static final AActivity EMPTY = from(Actor.EMPTY, ActivityType.EMPTY);
    public static final Try<AActivity> TRY_EMPTY = Try.success(EMPTY);

    private TimelinePosition prevTimelinePosition = TimelinePosition.EMPTY;
    private TimelinePosition nextTimelinePosition = TimelinePosition.EMPTY;
    private String oid = "";
    private long storedUpdatedDate = DATETIME_MILLIS_NEVER;
    private long updatedDate = DATETIME_MILLIS_NEVER;
    private long id = 0;
    private long insDate = DATETIME_MILLIS_NEVER;

    @NonNull
    public final Actor accountActor;
    @NonNull
    public final ActivityType type;
    private Actor actor = Actor.EMPTY;

    // Objects of the Activity may be of several types...
    @NonNull
    private volatile Note note = Note.EMPTY;
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
        final Note note = Note.fromOriginAndOid(accountActor.origin, noteOid, status);
        AActivity activity = from(accountActor, ActivityType.UPDATE);
        activity.setActor(actor);
        activity.setOid(
                StringUtil.toTempOidIf(StringUtil.isEmptyOrTemp(note.oid),
                    activity.getActorPrefix() + StringUtil.stripTempPrefix(note.oid)));
        activity.setNote(note);
        note.setUpdatedDate(updatedDate);
        activity.setUpdatedDate(updatedDate);
        return activity;
    }

    private AActivity(Actor accountActor, ActivityType type) {
        this.accountActor = accountActor == null ? Actor.EMPTY : accountActor;
        this.type = type == null ? ActivityType.EMPTY : type;
    }

    public void initializePublicAndFollowers() {
        Visibility visibility = getNote().getInReplyTo().getNote().audience().getVisibility();
        getNote().audience().setVisibility(visibility.isKnown()
            ? visibility
            : Visibility.fromCheckboxes(true, accountActor.origin.getOriginType().isFollowersChangeAllowed));
    }

    @NonNull
    public Actor getActor() {
        if (actor.nonEmpty()) {
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

    public TriState followedByActor() {
        return type.equals(ActivityType.FOLLOW)
                ? TriState.TRUE
                : type.equals(ActivityType.UNDO_FOLLOW)
                    ? TriState.FALSE
                    : TriState.UNKNOWN;
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
        return myContext.users().isMe(getActor()) || myContext.users().isMe(getAuthor());
    }

    public Actor getNotifiedActor() {
        return notifiedActor;
    }

    @NonNull
    public AObjectType getObjectType() {
        if (note != null && note.nonEmpty()) {
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

    public String getOid() {
        return oid;
    }

    public AActivity setOid(String oidIn) {
        oid = StringUtil.isEmpty(oidIn) ? "" : oidIn;
        return this;
    }

    public TimelinePosition getPrevTimelinePosition() {
        return prevTimelinePosition.isEmpty()
                ? (nextTimelinePosition.isEmpty() ? TimelinePosition.of(oid) : nextTimelinePosition)
                : prevTimelinePosition;
    }

    public TimelinePosition getNextTimelinePosition() {
        return nextTimelinePosition.isEmpty()
                ? (prevTimelinePosition.isEmpty() ? TimelinePosition.of(oid) : prevTimelinePosition)
                : nextTimelinePosition;
    }

    public AActivity setTimelinePositions(String prevPosition, String nextPosition) {
        prevTimelinePosition = TimelinePosition.of(StringUtil.isEmpty(prevPosition) ? "" : prevPosition);
        nextTimelinePosition = TimelinePosition.of(StringUtil.isEmpty(nextPosition) ? "" : nextPosition);
        return this;
    }

    @NonNull
    private String buildTempOid() {
        return StringUtil.toTempOid(
                    getActorPrefix() +
                    type.name().toLowerCase() + "-" +
                    (StringUtil.nonEmpty(getNote().oid) ? getNote().oid + "-" : "") +
                    MyLog.uniqueDateTimeFormatted());
    }

    @NonNull
    private String getActorPrefix() {
        return StringUtil.nonEmpty(actor.oid)
                ? actor.oid + "-"
                : (StringUtil.nonEmpty(accountActor.oid)
                    ? accountActor.oid + "-"
                    : "");
    }

    public long getUpdatedDate() {
        return updatedDate;
    }

    public void setUpdatedDate(long updatedDate) {
        this.updatedDate = updatedDate;
    }

    public void setUpdatedNow(int level) {
        if (isEmpty() || level > 10) return;

        setUpdatedDate(MyLog.uniqueCurrentTimeMS());
        getNote().setUpdatedNow(level + 1);
        getActivity().setUpdatedNow(level + 1);
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

    public void addAttachment(Attachment attachment) {
        addAttachment(attachment, MAX_ATTACHMENTS_DEFAULT);
    }

    public void addAttachment(Attachment attachment, int maxAttachments) {
        Attachments attachments = getNote().attachments.add(attachment);
        if ( attachments.size() > maxAttachments) {
            attachments.list.remove(0);
        }
        setNote(getNote().copy(Optional.empty(), Optional.of(attachments)));
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

    public AActivity setObjActor(Actor actor) {
        if (this == EMPTY && Actor.EMPTY != actor) {
            throw new IllegalStateException("Cannot set objActor of EMPTY Activity");
        }
        this.objActor = actor == null ? Actor.EMPTY : actor;
        return this;
    }

    public Audience audience() {
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
                + ", oid:" + oid
                + ", updated:" + MyLog.debugFormatOfDate(updatedDate)
                + ", me:" + (accountActor.isEmpty() ? "EMPTY" : accountActor.oid)
                + (subscribedByMe.known ? (subscribedByMe == TriState.TRUE ? ", subscribed" : ", NOT subscribed") : "" )
                + (interacted.isTrue ? ", interacted" : "" )
                + (notified.isTrue
                    ? ", notified" + (notifiedActor.isEmpty() ? " ???" : "Actor:" + objActor)
                    : "" )
                + (newNotificationEventType.isEmpty() ? "" : ", " + newNotificationEventType)
                + (actor.isEmpty() ? "" : ", \nactor:" + actor)
                + (note == null || note.isEmpty() ? "" : ", \nnote:" + note)
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
        activity.setOid(DbUtils.getString(cursor, ActivityTable.ACTIVITY_OID));
        activity.actor = Actor.fromId(activity.accountActor.origin,
                DbUtils.getLong(cursor, ActivityTable.ACTOR_ID));
        activity.note = Note.fromOriginAndOid(activity.accountActor.origin, "", DownloadStatus.UNKNOWN);
        activity.objActor = Actor.fromId(activity.accountActor.origin,
                DbUtils.getLong(cursor, ActivityTable.OBJ_ACTOR_ID));
        activity.aActivity = AActivity.from(activity.accountActor, ActivityType.EMPTY);
        activity.aActivity.id =  DbUtils.getLong(cursor, ActivityTable.OBJ_ACTIVITY_ID);
        activity.subscribedByMe = DbUtils.getTriState(cursor, ActivityTable.SUBSCRIBED);
        activity.interacted = DbUtils.getTriState(cursor, ActivityTable.INTERACTED);
        activity.interactionEventType = NotificationEventType.fromId(
                DbUtils.getLong(cursor, ActivityTable.INTERACTION_EVENT));
        activity.notified = DbUtils.getTriState(cursor, ActivityTable.NOTIFIED);
        activity.notifiedActor = Actor.fromId(activity.accountActor.origin,
                DbUtils.getLong(cursor, ActivityTable.NOTIFIED_ACTOR_ID));
        activity.newNotificationEventType = NotificationEventType.fromId(
                DbUtils.getLong(cursor, ActivityTable.NEW_NOTIFICATION_EVENT));
        activity.updatedDate = DbUtils.getLong(cursor, ActivityTable.UPDATED_DATE);
        activity.storedUpdatedDate = activity.updatedDate;
        activity.insDate = DbUtils.getLong(cursor, ActivityTable.INS_DATE);
        return activity;
    }
    
    public long save(MyContext myContext) {
        if (wontSave(myContext)) return id;
        if (updatedDate > SOME_TIME_AGO) calculateInteraction(myContext);
        if (getId() == 0) {
            DbUtils.addRowWithRetry(myContext, ActivityTable.TABLE_NAME, toContentValues(), 3)
            .onSuccess(idAdded -> {
                id = idAdded;
                MyLog.v(this, () -> "Added " + this);
            })
            .onFailure(e -> MyLog.w(this, "Failed to add " + this, e));
        } else {
            DbUtils.updateRowWithRetry(myContext, ActivityTable.TABLE_NAME, getId(), toContentValues(), 3)
            .onSuccess(o -> MyLog.v(this, () -> "Updated " + this))
            .onFailure(e -> MyLog.w(this, "Failed to update " + this, e));
        }
        afterSave(myContext);
        return id;
    }

    private boolean wontSave(MyContext myContext) {
        if (isEmpty() || (type.equals(ActivityType.UPDATE) && getObjectType().equals(AObjectType.ACTOR))
                || (StringUtil.isEmpty(oid) && getId() != 0)) {
            MyLog.v(this, () -> "Won't save " + this);
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
        storedUpdatedDate = MyQuery.idToLongColumnValue(
                myContext.getDatabase(), ActivityTable.TABLE_NAME, ActivityTable.UPDATED_DATE, id);
        if (getId() != 0) {
            if (updatedDate <= storedUpdatedDate) {
                MyLog.v(this, () -> "Skipped as not younger " + this);
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
                        MyLog.v(this, () -> "Skipped as already " + type.name() + " " + this);
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
                        MyLog.v(this, () -> "Skipped as already " + type.name() + " " + this);
                        return true;
                    }
                    break;
                default:
                    break;
            }
            if (StringUtil.isTemp(oid)) {
                MyLog.v(this, () -> "Skipped as temp oid " + this);
                return true;
            }
        }
        return false;
    }

    private void findExisting(MyContext myContext) {
        if (StringUtil.nonEmpty(oid)) {
            id = MyQuery.oidToId(myContext, OidEnum.ACTIVITY_OID, accountActor.origin.getId(), oid);
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
        interacted = TriState.fromBoolean(newNotificationEventType.isInteracted());
        interactionEventType = newNotificationEventType;
        notifiedActor = calculateNotifiedActor(myContext, newNotificationEventType);
        if (isNotified().toBoolean(true)) {
            this.notified = TriState.fromBoolean(myContext.getNotifier().isEnabled(newNotificationEventType));
        }
        if (isNotified().isTrue) {
            MyLog.i("NewNotification",newNotificationEventType.name() +
                " " + accountActor.origin.getName() +
                " " + accountActor.getUniqueName() +
                " " + MyLog.formatDateTime(getUpdatedDate()) +
                " " + actor.getTimelineUsername() + " " + type +
                (getNote().nonEmpty()
                    ? " '" + getNote().oid + "' " + I18n.trimTextAt(getNote().getContent(), 300)
                    : "") +
                (getObjActor().nonEmpty() ? " " + getObjActor().getTimelineUsername() : "")
            );
        }
    }

    private NotificationEventType calculateNotificationEventType(MyContext myContext) {
        if (myContext.users().isMe(getActor())) return NotificationEventType.EMPTY;
        if (getNote().audience().getVisibility().isPrivate()) {
            return NotificationEventType.PRIVATE;
        } else if(myContext.users().containsMe(getNote().audience().getNonSpecialActors()) && !isMyActorOrAuthor(myContext)) {
            return NotificationEventType.MENTION;
        } else if (type == ActivityType.ANNOUNCE && myContext.users().isMe(getAuthor())) {
            return NotificationEventType.ANNOUNCE;
        } else if ((type == ActivityType.LIKE || type == ActivityType.UNDO_LIKE)
                && myContext.users().isMe(getAuthor())) {
            return NotificationEventType.LIKE;
        } else if ((type == ActivityType.FOLLOW || type == ActivityType.UNDO_FOLLOW)
                && myContext.users().isMe(getObjActor())) {
            return NotificationEventType.FOLLOW;
        } else if (isSubscribedByMe().isTrue) {
            return NotificationEventType.HOME;
        } else {
            return NotificationEventType.EMPTY;
        }
    }

    private Actor calculateNotifiedActor(MyContext myContext, NotificationEventType event) {
        switch (event) {
            case MENTION:
                return myContext.users().myActors.values().stream()
                    .filter(actor -> getNote().audience().findSame(actor).isSuccess()).findFirst()
                    .orElse(
                        myContext.users().myActors.values().stream().filter(a -> a.origin.equals(accountActor.origin))
                            .findFirst().orElse(Actor.EMPTY)
                    );
            case ANNOUNCE:
            case LIKE:
                return getAuthor();
            case FOLLOW:
                return getObjActor();
            case PRIVATE:
            case HOME:
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
        }
        if (newNotificationEventType.nonEmpty()) {
            values.put(ActivityTable.NEW_NOTIFICATION_EVENT, newNotificationEventType.id);
        }
        if (notifiedActor.nonEmpty()) {
            values.put(ActivityTable.NOTIFIED_ACTOR_ID, notifiedActor.actorId);
        }
        values.put(ActivityTable.UPDATED_DATE, updatedDate);
        if (id == 0) {
            values.put(ActivityTable.ACTIVITY_TYPE, type.id);
            if (StringUtil.isEmpty(oid)) {
                setOid(buildTempOid());
            }
        }
        if (id == 0 || (storedUpdatedDate <= SOME_TIME_AGO && updatedDate > SOME_TIME_AGO)) {
            insDate = MyLog.uniqueCurrentTimeMS();
            values.put(ActivityTable.INS_DATE, insDate);
        }
        if (StringUtil.nonEmpty(oid)) {
            values.put(ActivityTable.ACTIVITY_OID, oid);
        }
        return values;
    }

    private void afterSave(MyContext myContext) {
        switch (type) {
            case LIKE:
            case UNDO_LIKE:
                final MyAccount myActorAccount = myContext.accounts().fromActorOfAnyOrigin(actor);
                if (myActorAccount.isValid()) {
                    MyLog.v(this, () -> myActorAccount + " " + type
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

    public AActivity withVisibility(Visibility visibility) {
        getNote().audience().withVisibility(visibility);
        return this;
    }
}
