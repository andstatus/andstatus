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
package org.andstatus.app.net.social

import android.content.ContentValues
import android.database.Cursor
import android.provider.BaseColumns
import io.vavr.control.Try
import org.andstatus.app.context.MyContext
import org.andstatus.app.data.DbUtils
import org.andstatus.app.data.DownloadStatus
import org.andstatus.app.data.MyProvider
import org.andstatus.app.data.MyQuery
import org.andstatus.app.data.OidEnum
import org.andstatus.app.database.table.ActivityTable
import org.andstatus.app.notification.NotificationEventType
import org.andstatus.app.origin.OriginConfig
import org.andstatus.app.os.MyAsyncTask
import org.andstatus.app.util.I18n
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.RelativeTime
import org.andstatus.app.util.StringUtil
import org.andstatus.app.util.TriState
import java.util.*

/** Activity in a sense of Activity Streams https://www.w3.org/TR/activitystreams-core/  */
class AActivity private constructor(accountActor: Actor?, type: ActivityType?) : AObject() {
    private var prevTimelinePosition: TimelinePosition? = TimelinePosition.Companion.EMPTY
    private var nextTimelinePosition: TimelinePosition? = TimelinePosition.Companion.EMPTY
    private var oid: String? = ""
    private var storedUpdatedDate = RelativeTime.DATETIME_MILLIS_NEVER
    private var updatedDate = RelativeTime.DATETIME_MILLIS_NEVER
    private var id: Long = 0
    private var insDate = RelativeTime.DATETIME_MILLIS_NEVER
    val accountActor: Actor
    val type: ActivityType
    private var actor: Actor? = Actor.Companion.EMPTY

    // Objects of the Activity may be of several types...
    @Volatile
    private var note: Note = Note.Companion.EMPTY
    private var objActor: Actor = Actor.Companion.EMPTY
    private var aActivity = EMPTY

    /** Some additional attributes may appear from "My account's" (authenticated User's) point of view  */
    private var subscribedByMe: TriState? = TriState.UNKNOWN
    private var interacted: TriState? = TriState.UNKNOWN
    private var interactionEventType: NotificationEventType? = NotificationEventType.EMPTY
    private var notified: TriState? = TriState.UNKNOWN
    private var notifiedActor: Actor? = Actor.Companion.EMPTY
    private var newNotificationEventType: NotificationEventType? = NotificationEventType.EMPTY
    fun initializePublicAndFollowers() {
        val visibility = getNote().inReplyTo.note.audience().visibility
        getNote().audience().visibility = if (visibility.isKnown) visibility else Visibility.Companion.fromCheckboxes(true, accountActor.origin.originType.isFollowersChangeAllowed)
    }

    fun getActor(): Actor {
        return if (actor.nonEmpty()) {
            actor
        } else when (getObjectType()) {
            AObjectType.ACTOR -> objActor
            AObjectType.NOTE -> getAuthor()
            else -> Actor.Companion.EMPTY
        }
    }

    fun setActor(actor: Actor?) {
        check(!(this === EMPTY && Actor.Companion.EMPTY !== actor)) { "Cannot set Actor of EMPTY Activity" }
        this.actor = actor ?: Actor.Companion.EMPTY
    }

    fun isAuthorActor(): Boolean {
        return getActor().isSame(getAuthor())
    }

    fun followedByActor(): TriState? {
        return if (type == ActivityType.FOLLOW) TriState.TRUE else if (type == ActivityType.UNDO_FOLLOW) TriState.FALSE else TriState.UNKNOWN
    }

    fun getAuthor(): Actor {
        if (isEmpty) {
            return Actor.Companion.EMPTY
        }
        return if (getObjectType() == AObjectType.NOTE) {
            when (type) {
                ActivityType.CREATE, ActivityType.UPDATE, ActivityType.DELETE -> actor
                else -> Actor.Companion.EMPTY
            }
        } else getActivity().getAuthor()
    }

    fun setAuthor(author: Actor?) {
        if (getActivity() !== EMPTY) getActivity().setActor(author)
    }

    fun isMyActorOrAuthor(myContext: MyContext): Boolean {
        return myContext.users().isMe(getActor()) || myContext.users().isMe(getAuthor())
    }

    fun getNotifiedActor(): Actor? {
        return notifiedActor
    }

    fun getObjectType(): AObjectType {
        return if (note != null && note.nonEmpty()) {
            AObjectType.NOTE
        } else if (objActor.nonEmpty()) {
            AObjectType.ACTOR
        } else if (getActivity().nonEmpty()) {
            AObjectType.ACTIVITY
        } else {
            AObjectType.EMPTY
        }
    }

    override fun isEmpty(): Boolean {
        return this === EMPTY || type == ActivityType.EMPTY || getObjectType() == AObjectType.EMPTY || accountActor.isEmpty
    }

    fun getOid(): String? {
        return oid
    }

    fun setOid(oidIn: String?): AActivity? {
        oid = if (StringUtil.isEmpty(oidIn)) "" else oidIn
        return this
    }

    fun getPrevTimelinePosition(): TimelinePosition? {
        return if (prevTimelinePosition.isEmpty()) if (nextTimelinePosition.isEmpty()) TimelinePosition.Companion.of(oid) else nextTimelinePosition else prevTimelinePosition
    }

    fun getNextTimelinePosition(): TimelinePosition? {
        return if (nextTimelinePosition.isEmpty()) if (prevTimelinePosition.isEmpty()) TimelinePosition.Companion.of(oid) else prevTimelinePosition else nextTimelinePosition
    }

    fun setTimelinePositions(prevPosition: String?, nextPosition: String?): AActivity? {
        prevTimelinePosition = TimelinePosition.Companion.of(if (StringUtil.isEmpty(prevPosition)) "" else prevPosition)
        nextTimelinePosition = TimelinePosition.Companion.of(if (StringUtil.isEmpty(nextPosition)) "" else nextPosition)
        return this
    }

    private fun buildTempOid(): String {
        return StringUtil.toTempOid(
                getActorPrefix() +
                        type.name.toLowerCase() + "-" +
                        (if (StringUtil.nonEmpty(getNote().oid)) getNote().oid + "-" else "") +
                        MyLog.uniqueDateTimeFormatted())
    }

    private fun getActorPrefix(): String {
        return if (StringUtil.nonEmpty(actor.oid)) actor.oid + "-" else if (StringUtil.nonEmpty(accountActor.oid)) accountActor.oid + "-" else ""
    }

    fun getUpdatedDate(): Long {
        return updatedDate
    }

    fun setUpdatedDate(updatedDate: Long) {
        this.updatedDate = updatedDate
    }

    fun setUpdatedNow(level: Int) {
        if (isEmpty || level > 10) return
        setUpdatedDate(MyLog.uniqueCurrentTimeMS())
        getNote().setUpdatedNow(level + 1)
        getActivity().setUpdatedNow(level + 1)
    }

    fun getNote(): Note {
        return Optional.ofNullable(note).filter { msg: Note? -> msg !== Note.Companion.EMPTY }.orElseGet { getNestedNote() }
    }

    private fun getNestedNote(): Note {
        /* Referring to the nested note allows to implement an activity, which has both Actor and Author.
            Actor of the nested note is an Author.
            In a database we will have 2 activities: one for each actor! */
        return when (type) {
            ActivityType.ANNOUNCE, ActivityType.CREATE, ActivityType.DELETE, ActivityType.LIKE, ActivityType.UPDATE, ActivityType.UNDO_ANNOUNCE, ActivityType.UNDO_LIKE ->                 // Check for null even though it looks like result couldn't be null.
                // May be needed for AActivity.EMPTY activity...
                Optional.ofNullable(getActivity().getNote()).orElse(Note.Companion.EMPTY)
            else -> Note.Companion.EMPTY
        }
    }

    @JvmOverloads
    fun addAttachment(attachment: Attachment?, maxAttachments: Int = OriginConfig.Companion.MAX_ATTACHMENTS_DEFAULT) {
        val attachments = getNote().attachments.add(attachment)
        if (attachments.size() > maxAttachments) {
            attachments.list.removeAt(0)
        }
        setNote(getNote().withAttachments(attachments))
    }

    fun setNote(note: Note?) {
        check(!(this === EMPTY && Note.Companion.EMPTY !== note)) { "Cannot set Note of EMPTY Activity" }
        this.note = note ?: Note.Companion.EMPTY
    }

    fun getObjActor(): Actor {
        return objActor
    }

    fun setObjActor(actor: Actor?): AActivity? {
        check(!(this === EMPTY && Actor.Companion.EMPTY !== actor)) { "Cannot set objActor of EMPTY Activity" }
        objActor = actor ?: Actor.Companion.EMPTY
        return this
    }

    fun audience(): Audience? {
        return getNote().audience()
    }

    fun getActivity(): AActivity {
        return if (aActivity == null) EMPTY else aActivity
    }

    fun setActivity(activity: AActivity?) {
        check(!(this === EMPTY && EMPTY !== activity)) { "Cannot set Activity of EMPTY Activity" }
        if (activity != null) {
            aActivity = activity
        }
    }

    override fun toString(): String {
        return if (this === EMPTY) {
            "EMPTY"
        } else "AActivity{"
                + (if (isEmpty) "(empty), " else "")
                + type
                + ", id:" + id
                + ", oid:" + oid
                + ", updated:" + MyLog.debugFormatOfDate(updatedDate)
                + ", me:" + (if (accountActor.isEmpty) "EMPTY" else accountActor.oid)
                + (if (subscribedByMe.known) if (subscribedByMe == TriState.TRUE) ", subscribed" else ", NOT subscribed" else "")
                + (if (interacted.isTrue) ", interacted" else "")
                + (if (notified.isTrue) ", notified" + (if (notifiedActor.isEmpty()) " ???" else "Actor:$objActor") else "")
                + (if (newNotificationEventType.isEmpty()) "" else ", $newNotificationEventType")
                + (if (actor.isEmpty()) "" else ", \nactor:$actor")
                + (if (note == null || note.isEmpty) "" else ", \nnote:$note")
                + (if (getActivity().isEmpty) "" else """
     , 
     activity:${getActivity()}
     """.trimIndent())
                + (if (objActor.isEmpty) "" else ", objActor:$objActor")
                + '}'
    }

    fun getId(): Long {
        return id
    }

    fun isSubscribedByMe(): TriState? {
        return subscribedByMe
    }

    fun setSubscribedByMe(isSubscribed: TriState?) {
        if (isSubscribed != null) subscribedByMe = isSubscribed
    }

    fun isNotified(): TriState? {
        return notified
    }

    fun setNotified(notified: TriState?) {
        if (notified != null) this.notified = notified
    }

    fun save(myContext: MyContext?): Long {
        if (wontSave(myContext)) return id
        if (updatedDate > RelativeTime.SOME_TIME_AGO) calculateInteraction(myContext)
        if (getId() == 0L) {
            DbUtils.addRowWithRetry(myContext, ActivityTable.TABLE_NAME, toContentValues(), 3)
                    .onSuccess { idAdded: Long? ->
                        id = idAdded
                        MyLog.v(this) { "Added $this" }
                    }
                    .onFailure { e: Throwable? -> MyLog.w(this, "Failed to add $this", e) }
        } else {
            DbUtils.updateRowWithRetry(myContext, ActivityTable.TABLE_NAME, getId(), toContentValues(), 3)
                    .onSuccess { o: Void? -> MyLog.v(this) { "Updated $this" } }
                    .onFailure { e: Throwable? -> MyLog.w(this, "Failed to update $this", e) }
        }
        afterSave(myContext)
        return id
    }

    private fun wontSave(myContext: MyContext?): Boolean {
        if (isEmpty || type == ActivityType.UPDATE && getObjectType() == AObjectType.ACTOR
                || StringUtil.isEmpty(oid) && getId() != 0L) {
            MyLog.v(this) { "Won't save $this" }
            return true
        }
        check(!MyAsyncTask.Companion.isUiThread()) { "Saving activity on the Main thread " + toString() }
        check(accountActor.actorId != 0L) { "Account is unknown " + toString() }
        if (getId() == 0L) {
            findExisting(myContext)
        }
        storedUpdatedDate = MyQuery.idToLongColumnValue(
                myContext.getDatabase(), ActivityTable.TABLE_NAME, ActivityTable.UPDATED_DATE, id)
        if (getId() != 0L) {
            if (updatedDate <= storedUpdatedDate) {
                MyLog.v(this) { "Skipped as not younger $this" }
                return true
            }
            when (type) {
                ActivityType.LIKE, ActivityType.UNDO_LIKE -> {
                    val favAndType = MyQuery.noteIdToLastFavoriting(myContext.getDatabase(),
                            getNote().noteId, accountActor.actorId)
                    if (favAndType.second == ActivityType.LIKE && type == ActivityType.LIKE
                            || favAndType.second == ActivityType.UNDO_LIKE && type == ActivityType.UNDO_LIKE) {
                        MyLog.v(this) { "Skipped as already " + type.name + " " + this }
                        return true
                    }
                }
                ActivityType.ANNOUNCE, ActivityType.UNDO_ANNOUNCE -> {
                    val reblAndType = MyQuery.noteIdToLastReblogging(myContext.getDatabase(),
                            getNote().noteId, accountActor.actorId)
                    if (reblAndType.second == ActivityType.ANNOUNCE && type == ActivityType.ANNOUNCE
                            || reblAndType.second == ActivityType.UNDO_ANNOUNCE && type == ActivityType.UNDO_ANNOUNCE) {
                        MyLog.v(this) { "Skipped as already " + type.name + " " + this }
                        return true
                    }
                }
                else -> {
                }
            }
            if (StringUtil.isTemp(oid)) {
                MyLog.v(this) { "Skipped as temp oid $this" }
                return true
            }
        }
        return false
    }

    private fun findExisting(myContext: MyContext?) {
        if (StringUtil.nonEmpty(oid)) {
            id = MyQuery.oidToId(myContext, OidEnum.ACTIVITY_OID, accountActor.origin.id, oid)
        }
        if (id != 0L) {
            return
        }
        if (getNote().noteId != 0L && (type == ActivityType.UPDATE || type == ActivityType.CREATE)) {
            id = MyQuery.conditionToLongColumnValue(myContext.getDatabase(), "", ActivityTable.TABLE_NAME,
                    BaseColumns._ID, ActivityTable.NOTE_ID + "=" + getNote().noteId + " AND "
                    + ActivityTable.ACTIVITY_TYPE + "=" + type.id)
        }
    }

    private fun calculateInteraction(myContext: MyContext?) {
        newNotificationEventType = calculateNotificationEventType(myContext)
        interacted = TriState.Companion.fromBoolean(newNotificationEventType.isInteracted())
        interactionEventType = newNotificationEventType
        notifiedActor = calculateNotifiedActor(myContext, newNotificationEventType)
        if (isNotified().toBoolean(true)) {
            notified = TriState.Companion.fromBoolean(myContext.getNotifier().isEnabled(newNotificationEventType))
        }
        if (isNotified().isTrue) {
            MyLog.i("NewNotification", newNotificationEventType.name +
                    " " + accountActor.origin.name +
                    " " + accountActor.uniqueName +
                    " " + MyLog.formatDateTime(getUpdatedDate()) +
                    " " + actor.getActorNameInTimeline() + " " + type +
                    (if (getNote().nonEmpty()) " '" + getNote().oid + "' " + I18n.trimTextAt(getNote().content, 300) else "") +
                    if (getObjActor().nonEmpty()) " " + getObjActor().actorNameInTimeline else ""
            )
        }
    }

    private fun calculateNotificationEventType(myContext: MyContext?): NotificationEventType? {
        if (myContext.users().isMe(getActor())) return NotificationEventType.EMPTY
        if (getNote().audience().isMeInAudience && !isMyActorOrAuthor(myContext)) {
            return if (getNote().audience().visibility.isPrivate) NotificationEventType.PRIVATE else NotificationEventType.MENTION
        }
        return if (type == ActivityType.ANNOUNCE && myContext.users().isMe(getAuthor())) {
            NotificationEventType.ANNOUNCE
        } else if ((type == ActivityType.LIKE || type == ActivityType.UNDO_LIKE)
                && myContext.users().isMe(getAuthor())) {
            NotificationEventType.LIKE
        } else if ((type == ActivityType.FOLLOW || type == ActivityType.UNDO_FOLLOW)
                && myContext.users().isMe(getObjActor())) {
            NotificationEventType.FOLLOW
        } else if (isSubscribedByMe().isTrue) {
            NotificationEventType.HOME
        } else {
            NotificationEventType.EMPTY
        }
    }

    private fun calculateNotifiedActor(myContext: MyContext?, event: NotificationEventType?): Actor? {
        return when (event) {
            NotificationEventType.MENTION, NotificationEventType.PRIVATE -> myContext.users().myActors.values.stream()
                    .filter { actor: Actor? -> getNote().audience().findSame(actor).isSuccess }
                    .findFirst()
                    .orElse(
                            myContext.users().myActors.values.stream()
                                    .filter { a: Actor? -> a.origin == accountActor.origin }
                                    .findFirst()
                                    .orElse(Actor.Companion.EMPTY)
                    )
            NotificationEventType.ANNOUNCE, NotificationEventType.LIKE -> getAuthor()
            NotificationEventType.FOLLOW -> getObjActor()
            NotificationEventType.HOME -> accountActor
            else -> Actor.Companion.EMPTY
        }
    }

    private fun toContentValues(): ContentValues? {
        val values = ContentValues()
        values.put(ActivityTable.ORIGIN_ID, accountActor.origin.id)
        values.put(ActivityTable.ACCOUNT_ID, accountActor.actorId)
        values.put(ActivityTable.ACTOR_ID, getActor().actorId)
        values.put(ActivityTable.NOTE_ID, getNote().noteId)
        values.put(ActivityTable.OBJ_ACTOR_ID, getObjActor().actorId)
        values.put(ActivityTable.OBJ_ACTIVITY_ID, getActivity().id)
        if (subscribedByMe.known) {
            values.put(ActivityTable.SUBSCRIBED, subscribedByMe.id)
        }
        if (interacted.known) {
            values.put(ActivityTable.INTERACTED, interacted.id)
            values.put(ActivityTable.INTERACTION_EVENT, interactionEventType.id)
        }
        if (notified.known) {
            values.put(ActivityTable.NOTIFIED, notified.id)
        }
        if (newNotificationEventType.nonEmpty()) {
            values.put(ActivityTable.NEW_NOTIFICATION_EVENT, newNotificationEventType.id)
        }
        if (notifiedActor.nonEmpty()) {
            values.put(ActivityTable.NOTIFIED_ACTOR_ID, notifiedActor.actorId)
        }
        values.put(ActivityTable.UPDATED_DATE, updatedDate)
        if (id == 0L) {
            values.put(ActivityTable.ACTIVITY_TYPE, type.id)
            if (StringUtil.isEmpty(oid)) {
                setOid(buildTempOid())
            }
        }
        if (id == 0L || storedUpdatedDate <= RelativeTime.SOME_TIME_AGO && updatedDate > RelativeTime.SOME_TIME_AGO) {
            insDate = MyLog.uniqueCurrentTimeMS()
            values.put(ActivityTable.INS_DATE, insDate)
        }
        if (StringUtil.nonEmpty(oid)) {
            values.put(ActivityTable.ACTIVITY_OID, oid)
        }
        return values
    }

    private fun afterSave(myContext: MyContext?) {
        when (type) {
            ActivityType.LIKE, ActivityType.UNDO_LIKE -> {
                val myActorAccount = myContext.accounts().fromActorOfAnyOrigin(actor)
                if (myActorAccount.isValid) {
                    MyLog.v(this) {
                        (myActorAccount.toString() + " " + type
                                + " '" + getNote().oid + "' " + I18n.trimTextAt(getNote().content, 80))
                    }
                    MyProvider.Companion.updateNoteFavorited(myContext, actor.origin, getNote().noteId)
                }
            }
            else -> {
            }
        }
    }

    fun getNewNotificationEventType(): NotificationEventType? {
        return newNotificationEventType
    }

    fun setId(id: Long) {
        this.id = id
    }

    fun withVisibility(visibility: Visibility?): AActivity? {
        getNote().audience().withVisibility(visibility)
        return this
    }

    companion object {
        val EMPTY: AActivity? = from(Actor.Companion.EMPTY, ActivityType.EMPTY)
        val TRY_EMPTY = Try.success(EMPTY)
        fun fromInner(actor: Actor, type: ActivityType,
                      innerActivity: AActivity): AActivity {
            val activity = AActivity(innerActivity.accountActor, type)
            activity.setActor(actor)
            activity.setActivity(innerActivity)
            activity.setUpdatedDate(innerActivity.getUpdatedDate() + 60)
            return activity
        }

        fun from(accountActor: Actor, type: ActivityType): AActivity {
            return AActivity(accountActor, type)
        }

        @JvmOverloads
        fun newPartialNote(accountActor: Actor, actor: Actor?, noteOid: String?,
                           updatedDate: Long = RelativeTime.DATETIME_MILLIS_NEVER, status: DownloadStatus? = DownloadStatus.UNKNOWN): AActivity {
            val note: Note = Note.Companion.fromOriginAndOid(accountActor.origin, noteOid, status)
            val activity = from(accountActor, ActivityType.UPDATE)
            activity.setActor(actor)
            activity.setOid(
                    StringUtil.toTempOidIf(StringUtil.isEmptyOrTemp(note.oid),
                            activity.getActorPrefix() + StringUtil.stripTempPrefix(note.oid)))
            activity.setNote(note)
            note.updatedDate = updatedDate
            activity.setUpdatedDate(updatedDate)
            return activity
        }

        fun fromCursor(myContext: MyContext?, cursor: Cursor?): AActivity? {
            val activity = from(
                    myContext.accounts().fromActorId(DbUtils.getLong(cursor, ActivityTable.ACCOUNT_ID)).actor,
                    ActivityType.Companion.fromId(DbUtils.getLong(cursor, ActivityTable.ACTIVITY_TYPE)))
            activity.id = DbUtils.getLong(cursor, BaseColumns._ID)
            activity.setOid(DbUtils.getString(cursor, ActivityTable.ACTIVITY_OID))
            activity.actor = Actor.Companion.fromId(activity.accountActor.origin,
                    DbUtils.getLong(cursor, ActivityTable.ACTOR_ID))
            activity.note = Note.Companion.fromOriginAndOid(activity.accountActor.origin, "", DownloadStatus.UNKNOWN)
            activity.objActor = Actor.Companion.fromId(activity.accountActor.origin,
                    DbUtils.getLong(cursor, ActivityTable.OBJ_ACTOR_ID))
            activity.aActivity = from(activity.accountActor, ActivityType.EMPTY)
            activity.aActivity.id = DbUtils.getLong(cursor, ActivityTable.OBJ_ACTIVITY_ID)
            activity.subscribedByMe = DbUtils.getTriState(cursor, ActivityTable.SUBSCRIBED)
            activity.interacted = DbUtils.getTriState(cursor, ActivityTable.INTERACTED)
            activity.interactionEventType = NotificationEventType.Companion.fromId(
                    DbUtils.getLong(cursor, ActivityTable.INTERACTION_EVENT))
            activity.notified = DbUtils.getTriState(cursor, ActivityTable.NOTIFIED)
            activity.notifiedActor = Actor.Companion.fromId(activity.accountActor.origin,
                    DbUtils.getLong(cursor, ActivityTable.NOTIFIED_ACTOR_ID))
            activity.newNotificationEventType = NotificationEventType.Companion.fromId(
                    DbUtils.getLong(cursor, ActivityTable.NEW_NOTIFICATION_EVENT))
            activity.updatedDate = DbUtils.getLong(cursor, ActivityTable.UPDATED_DATE)
            activity.storedUpdatedDate = activity.updatedDate
            activity.insDate = DbUtils.getLong(cursor, ActivityTable.INS_DATE)
            return activity
        }
    }

    init {
        this.accountActor = accountActor ?: Actor.Companion.EMPTY
        this.type = type ?: ActivityType.EMPTY
    }
}