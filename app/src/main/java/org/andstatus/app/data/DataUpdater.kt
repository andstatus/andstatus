/* 
 * Copyright (c) 2012-2017 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.data

import android.content.ContentValues
import android.net.Uri
import io.vavr.control.Try
import org.andstatus.app.account.MyAccount
import org.andstatus.app.actor.GroupType
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.database.table.ActivityTable
import org.andstatus.app.database.table.ActorTable
import org.andstatus.app.database.table.NoteTable
import org.andstatus.app.net.social.AActivity
import org.andstatus.app.net.social.AObjectType
import org.andstatus.app.net.social.ActivityType
import org.andstatus.app.net.social.Actor
import org.andstatus.app.net.social.InputTimelinePage
import org.andstatus.app.net.social.Note
import org.andstatus.app.net.social.TimelinePosition
import org.andstatus.app.note.KeywordsFilter
import org.andstatus.app.service.CommandData
import org.andstatus.app.service.CommandEnum
import org.andstatus.app.service.CommandExecutionContext
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.RelativeTime
import org.andstatus.app.util.SharedPreferencesUtil
import org.andstatus.app.util.StringUtil
import org.andstatus.app.util.TriState
import org.andstatus.app.util.UriUtils
import java.util.*

/**
 * Stores (updates) notes and actors
 * from a Social network into a database.
 *
 * @author yvolk@yurivolkov.com
 */
class DataUpdater(private val execContext: CommandExecutionContext?) {
    private val lum: LatestActorActivities? = LatestActorActivities()
    private val keywordsFilter: KeywordsFilter? = KeywordsFilter(
            SharedPreferencesUtil.getString(MyPreferences.KEY_FILTER_HIDE_NOTES_BASED_ON_KEYWORDS, ""))

    constructor(ma: MyAccount?) : this(CommandExecutionContext(
            if (ma.getOrigin().myContext.isEmptyOrExpired)  MyContextHolder.myContextHolder.getNow() else ma.getOrigin().myContext,
            CommandData.Companion.newAccountCommand(CommandEnum.EMPTY, ma)
    )) {
    }

    fun onActivity(mbActivity: AActivity?): AActivity? {
        return onActivity(mbActivity, true)
    }

    fun onActivity(activity: AActivity?, saveLum: Boolean): AActivity? {
        return onActivityInternal(activity, saveLum, 0)
    }

    private fun onActivityInternal(activity: AActivity?, saveLum: Boolean, recursing: Int): AActivity? {
        if (activity == null || activity.isEmpty || recursing > MAX_RECURSING) {
            return activity
        }
        updateObjActor(activity.accountActor.update(activity.actor), recursing + 1)
        when (activity.objectType) {
            AObjectType.ACTIVITY -> onActivityInternal(activity.activity, false, recursing + 1)
            AObjectType.NOTE -> updateNote(activity, recursing + 1)
            AObjectType.ACTOR -> updateObjActor(activity, recursing + 1)
            else -> throw IllegalArgumentException("Unexpected activity: $activity")
        }
        updateActivity(activity)
        if (saveLum && recursing == 0) {
            saveLum()
        }
        return activity
    }

    private fun updateActivity(activity: AActivity?) {
        if (activity.isSubscribedByMe().notFalse
                && activity.getUpdatedDate() > 0 && (activity.isMyActorOrAuthor(execContext.myContext)
                        || activity.getNote().audience().containsMe(execContext.myContext))) {
            activity.setSubscribedByMe(TriState.TRUE)
        }
        if (activity.isNotified().unknown && execContext.myContext.users().isMe(activity.getActor()) &&
                activity.getNote().status.isPresentAtServer &&
                MyQuery.activityIdToTriState(ActivityTable.NOTIFIED, activity.getId()).isTrue) {
            activity.setNotified(TriState.FALSE)
        }
        activity.save(execContext.getMyContext())
        lum.onNewActorActivity(ActorActivity(activity.getActor().actorId, activity.getId(), activity.getUpdatedDate()))
        if (!activity.isAuthorActor()) {
            lum.onNewActorActivity(ActorActivity(activity.getAuthor().actorId, activity.getId(), activity.getUpdatedDate()))
        }
        execContext.getResult().onNotificationEvent(activity.getNewNotificationEventType())
    }

    fun saveLum() {
        lum.save()
    }

    private fun updateNote(activity: AActivity, recursing: Int) {
        if (recursing > MAX_RECURSING) return
        updateNote1(activity, recursing)
        onActivities(execContext, activity.note.replies)
    }

    private fun updateNote1(activity: AActivity, recursing: Int) {
        val method = "updateNote1"
        val note = activity.note
        try {
            val me = execContext.myContext.accounts().fromActorOfSameOrigin(activity.accountActor)
            if (me.nonValid) {
                MyLog.w(this, "$method; my account is invalid, skipping: $activity")
                return
            }
            updateObjActor(me.actor.update(me.actor, activity.actor), recursing + 1)
            if (activity.isAuthorActor) {
                activity.setAuthor(activity.actor)
            } else {
                updateObjActor(activity.actor.update(me.actor, activity.author), recursing + 1)
            }
            if (note.noteId == 0L) {
                note.noteId = MyQuery.oidToId(OidEnum.NOTE_OID, note.origin.id, note.oid)
            }
            val updatedDateStored: Long
            val statusStored: DownloadStatus
            if (note.noteId != 0L) {
                statusStored = DownloadStatus.Companion.load(
                        MyQuery.noteIdToLongColumnValue(NoteTable.NOTE_STATUS, note.noteId))
                updatedDateStored = MyQuery.noteIdToLongColumnValue(NoteTable.UPDATED_DATE, note.noteId)
            } else {
                updatedDateStored = 0
                statusStored = DownloadStatus.ABSENT
            }

            /*
             * Is the row first time retrieved from a Social Network?
             * Note can already exist in this these cases:
             * 1. There was only "a stub" stored (without a sent date and content)
             * 2. Note was "unsent" i.e. it had content, but didn't have oid
             */
            val isFirstTimeLoaded = (note.status == DownloadStatus.LOADED || note.noteId == 0L) &&
                    statusStored != DownloadStatus.LOADED
            val isFirstTimeSent = !isFirstTimeLoaded && note.noteId != 0L &&
                    StringUtil.nonEmptyNonTemp(note.oid) &&
                    statusStored.isUnsentDraft &&
                    StringUtil.isEmptyOrTemp(MyQuery.idToOid(execContext.myContext,
                            OidEnum.NOTE_OID, note.noteId, 0))
            if (note.status == DownloadStatus.UNKNOWN && isFirstTimeSent) {
                note.status = DownloadStatus.SENT
            }
            val isDraftUpdated = !isFirstTimeLoaded && !isFirstTimeSent && note.status.isUnsentDraft
            val isNewerThanInDatabase = note.updatedDate > updatedDateStored
            if (!isFirstTimeLoaded && !isFirstTimeSent && !isDraftUpdated && !isNewerThanInDatabase) {
                MyLog.v("Note") { "Skipped note as not younger $note" }
                return
            }

            // TODO: move as toContentValues() into Note
            val values = ContentValues()
            if (isFirstTimeLoaded || note.noteId == 0L) {
                values.put(NoteTable.INS_DATE, MyLog.uniqueCurrentTimeMS())
            }
            values.put(NoteTable.NOTE_STATUS, note.status.save())
            if (isNewerThanInDatabase) {
                values.put(NoteTable.UPDATED_DATE, note.updatedDate)
            }
            if (activity.author.actorId != 0L) {
                values.put(NoteTable.AUTHOR_ID, activity.author.actorId)
            }
            if (UriUtils.nonEmptyOid(note.oid)) {
                values.put(NoteTable.NOTE_OID, note.oid)
            }
            values.put(NoteTable.ORIGIN_ID, note.origin.id)
            if (UriUtils.nonEmptyOid(note.conversationOid)) {
                values.put(NoteTable.CONVERSATION_OID, note.conversationOid)
            }
            if (note.hasSomeContent()) {
                values.put(NoteTable.NAME, note.name)
                values.put(NoteTable.SUMMARY, note.summary)
                values.put(NoteTable.SENSITIVE, if (note.isSensitive) 1 else 0)
                values.put(NoteTable.CONTENT, note.content)
                values.put(NoteTable.CONTENT_TO_SEARCH, note.contentToSearch)
            }
            updateInReplyTo(activity, values)
            activity.note.audience().lookupUsers()
            for (actor in note.audience().evaluateAndGetActorsToSave(activity.author)) {
                updateObjActor(activity.actor.update(me.actor, actor), recursing + 1)
            }
            if (!note.via.isNullOrEmpty()) {
                values.put(NoteTable.VIA, note.via)
            }
            if (!note.url.isNullOrEmpty()) {
                values.put(NoteTable.URL, note.url)
            }
            if (note.audience().visibility.isKnown) {
                values.put(NoteTable.VISIBILITY, note.audience().visibility.id)
            }
            if (note.likesCount > 0) {
                values.put(NoteTable.LIKES_COUNT, note.likesCount)
            }
            if (note.reblogsCount > 0) {
                values.put(NoteTable.REBLOGS_COUNT, note.reblogsCount)
            }
            if (note.repliesCount > 0) {
                values.put(NoteTable.REPLIES_COUNT, note.repliesCount)
            }
            if (note.lookupConversationId() != 0L) {
                values.put(NoteTable.CONVERSATION_ID, note.conversationId)
            }
            if (note.status.mayUpdateContent() && shouldSaveAttachments(isFirstTimeLoaded, isDraftUpdated)) {
                values.put(NoteTable.ATTACHMENTS_COUNT, note.attachments.size())
            }
            if (MyLog.isVerboseEnabled()) {
                MyLog.v(this) {
                    ((if (note.noteId == 0L) "insertNote" else "updateNote " + note.noteId)
                            + ":" + note.status
                            + (if (isFirstTimeLoaded) " new;" else "")
                            + (if (isDraftUpdated) " draft updated;" else "")
                            + (if (isFirstTimeSent) " just sent;" else "")
                            + if (isNewerThanInDatabase) " newer, updated at " + Date(note.updatedDate) + ";" else "")
                }
            }
            if ( MyContextHolder.myContextHolder.getNow().isTestRun()) {
                 MyContextHolder.myContextHolder.getNow().putAssertionData(MSG_ASSERTION_KEY, values)
            }
            if (note.noteId == 0L) {
                val msgUri = execContext.getContext().contentResolver.insert(
                        MatchedUri.Companion.getMsgUri(me.actorId, 0), values)
                note.noteId = ParsedUri.Companion.fromUri(msgUri).getNoteId()
                if (note.conversationId == 0L) {
                    val values2 = ContentValues()
                    values2.put(NoteTable.CONVERSATION_ID, note.setConversationIdFromMsgId())
                    execContext.getContext().contentResolver.update(msgUri, values2, null, null)
                }
                MyLog.v("Note") { "Added $note" }
                if (!note.hasSomeContent() && note.status.canBeDownloaded) {
                    Note.Companion.requestDownload(me, note.noteId, false)
                }
            } else {
                val msgUri: Uri = MatchedUri.Companion.getMsgUri(me.actorId, note.noteId)
                execContext.getContext().contentResolver.update(msgUri, values, null, null)
                MyLog.v("Note") { "Updated $note" }
            }
            if (note.status.mayUpdateContent()) {
                note.audience().save(activity.author, note.noteId, note.audience().visibility, false)
                if (shouldSaveAttachments(isFirstTimeLoaded, isDraftUpdated)) {
                    note.attachments.save(execContext, note.noteId)
                }
                if (keywordsFilter.matchedAny(note.contentToSearch)) {
                    activity.isNotified = TriState.FALSE
                } else {
                    if (note.status == DownloadStatus.LOADED) {
                        execContext.getResult().incrementDownloadedCount()
                        execContext.getResult().incrementNewCount()
                    }
                }
            }
        } catch (e: Exception) {
            MyLog.e(this, method, e)
        }
    }

    private fun shouldSaveAttachments(isFirstTimeLoaded: Boolean, isDraftUpdated: Boolean): Boolean {
        return isFirstTimeLoaded || isDraftUpdated
    }

    private fun updateInReplyTo(activity: AActivity?, values: ContentValues?) {
        val inReply = activity.getNote().inReplyTo
        if (!inReply.note.oid.isNullOrEmpty()) {
            if (UriUtils.nonRealOid(inReply.note.conversationOid)) {
                inReply.note.setConversationOid(activity.getNote().conversationOid)
            }
            DataUpdater(execContext).onActivity(inReply)
            if (inReply.note.noteId != 0L) {
                activity.getNote().audience().add(inReply.author)
                values.put(NoteTable.IN_REPLY_TO_NOTE_ID, inReply.note.noteId)
                if (inReply.author.actorId != 0L) {
                    values.put(NoteTable.IN_REPLY_TO_ACTOR_ID, inReply.author.actorId)
                }
            }
        }
    }

    fun updateObjActor(activity: AActivity?, recursing: Int) {
        if (recursing > MAX_RECURSING) return
        val objActor = activity.getObjActor()
        val method = "updateObjActor"
        if (objActor.dontStore()) {
            MyLog.v(this) { method + "; don't store: " + objActor.uniqueName }
            return
        }
        val me = execContext.getMyContext().accounts().fromActorOfSameOrigin(activity.accountActor)
        if (me.nonValid()) {
            if (activity.accountActor == objActor) {
                MyLog.d(this, method + "; adding my account " + activity.accountActor)
            } else {
                MyLog.w(this, method + "; my account is invalid, skipping: " + activity.toString())
                return
            }
        }
        fixActorUpdatedDate(activity, objActor)
        objActor.lookupActorId()
        if (objActor.actorId != 0L && objActor.isNotFullyDefined && objActor.isMyFriend.unknown
                && activity.followedByActor().unknown && objActor.groupType == GroupType.UNKNOWN) {
            MyLog.v(this) { "$method; Skipping existing partially defined: $objActor" }
            return
        }
        objActor.lookupUser()
        if (shouldWeUpdateActor(method, objActor)) {
            updateObjActor2(activity, recursing, me)
        } else {
            updateFriendships(activity, me)
            execContext.myContext.users().reload(objActor)
        }
        MyLog.v(this) { "$method; $objActor" }
    }

    private fun shouldWeUpdateActor(method: String?, objActor: Actor?): Boolean {
        val updatedDateStored = MyQuery.actorIdToLongColumnValue(ActorTable.UPDATED_DATE, objActor.actorId)
        if (updatedDateStored > RelativeTime.SOME_TIME_AGO && updatedDateStored >= objActor.getUpdatedDate()) {
            MyLog.v(this) { "$method; Skipped actor update as not younger $objActor" }
            return false
        }
        return true
    }

    private fun updateObjActor2(activity: AActivity?, recursing: Int, me: MyAccount?) {
        val method = "updateObjActor2"
        try {
            val actor = activity.getObjActor()
            val actorOid = if (actor.actorId == 0L && !actor.isOidReal) actor.toTempOid() else actor.oid
            val values = ContentValues()
            if (actor.actorId == 0L || actor.isFullyDefined) {
                if (actor.actorId == 0L || actor.isOidReal) {
                    values.put(ActorTable.ACTOR_OID, actorOid)
                }

                // Substitute required empty values with some temporary for a new entry only!
                var username = actor.username
                if (SharedPreferencesUtil.isEmpty(username)) {
                    username = StringUtil.toTempOid(actorOid)
                }
                values.put(ActorTable.USERNAME, username)
                values.put(ActorTable.WEBFINGER_ID, actor.webFingerId)
                var realName = actor.realName
                if (SharedPreferencesUtil.isEmpty(realName)) {
                    realName = username
                }
                values.put(ActorTable.REAL_NAME, realName)
                // End of required attributes
            }
            if (actor.groupType != GroupType.UNKNOWN) {
                values.put(ActorTable.GROUP_TYPE, actor.groupType.id)
            }
            if (actor.parentActorId != 0L) {
                values.put(ActorTable.PARENT_ACTOR_ID, actor.parentActorId)
            }
            if (actor.hasAvatar()) {
                values.put(ActorTable.AVATAR_URL, actor.avatarUrl)
            }
            if (!SharedPreferencesUtil.isEmpty(actor.summary)) {
                values.put(ActorTable.SUMMARY, actor.summary)
            }
            if (!SharedPreferencesUtil.isEmpty(actor.homepage)) {
                values.put(ActorTable.HOMEPAGE, actor.homepage)
            }
            if (!SharedPreferencesUtil.isEmpty(actor.profileUrl)) {
                values.put(ActorTable.PROFILE_PAGE, actor.profileUrl)
            }
            if (!SharedPreferencesUtil.isEmpty(actor.location)) {
                values.put(ActorTable.LOCATION, actor.location)
            }
            if (actor.notesCount > 0) {
                values.put(ActorTable.NOTES_COUNT, actor.notesCount)
            }
            if (actor.favoritesCount > 0) {
                values.put(ActorTable.FAVORITES_COUNT, actor.favoritesCount)
            }
            if (actor.followingCount > 0) {
                values.put(ActorTable.FOLLOWING_COUNT, actor.followingCount)
            }
            if (actor.followersCount > 0) {
                values.put(ActorTable.FOLLOWERS_COUNT, actor.followersCount)
            }
            if (actor.createdDate > 0) {
                values.put(ActorTable.CREATED_DATE, actor.createdDate)
            }
            if (actor.updatedDate > 0) {
                values.put(ActorTable.UPDATED_DATE, actor.updatedDate)
            }
            actor.saveUser()
            val actorUri: Uri = MatchedUri.Companion.getActorUri(me.actorId, actor.actorId)
            if (actor.actorId == 0L) {
                values.put(ActorTable.ORIGIN_ID, actor.origin.id)
                values.put(ActorTable.USER_ID, actor.user.userId)
                actor.actorId = ParsedUri.Companion.fromUri(
                        execContext.getContext().contentResolver.insert(actorUri, values))
                        .getActorId()
            } else if (values.size() > 0) {
                execContext.getContext().contentResolver.update(actorUri, values, null, null)
            }
            actor.endpoints.save(actor.actorId)
            updateFriendships(activity, me)
            actor.avatarFile.resetAvatarErrors(execContext.myContext)
            execContext.myContext.users().reload(actor)
            if (actor.isNotFullyDefined && actor.canGetActor()) {
                actor.requestDownload(false)
            }
            actor.requestAvatarDownload()
            if (actor.hasLatestNote()) {
                updateNote(actor.latestActivity, recursing + 1)
            }
        } catch (e: Exception) {
            MyLog.e(this, "$method; $activity", e)
        }
    }

    private fun updateFriendships(activity: AActivity?, me: MyAccount?) {
        val actor = activity.getObjActor()
        GroupMembership.Companion.setAndReload(execContext.myContext, me.getActor(), actor.isMyFriend, actor)
        GroupMembership.Companion.setAndReload(execContext.myContext, activity.getActor(), activity.followedByActor(), actor)
    }

    private fun fixActorUpdatedDate(activity: AActivity?, actor: Actor?) {
        if (actor.getCreatedDate() <= RelativeTime.SOME_TIME_AGO && actor.getUpdatedDate() <= RelativeTime.SOME_TIME_AGO) return
        if (actor.getUpdatedDate() <= RelativeTime.SOME_TIME_AGO || activity.type == ActivityType.FOLLOW || activity.type == ActivityType.UNDO_FOLLOW) {
            actor.setUpdatedDate(Math.max(activity.getUpdatedDate(), actor.getCreatedDate()))
        }
    }

    fun downloadOneNoteBy(actor: Actor?): Try<Void> {
        return execContext.getConnection()
                .getTimeline(true, TimelineType.SENT.connectionApiRoutine, TimelinePosition.Companion.EMPTY,
                        TimelinePosition.Companion.EMPTY, 1, actor)
                .map { page: InputTimelinePage? ->
                    for (item in page.items) {
                        onActivity(item, false)
                    }
                    saveLum()
                    null
                }
    }

    companion object {
        const val MAX_RECURSING = 4
        val MSG_ASSERTION_KEY: String? = "updateNote"
        fun onActivities(execContext: CommandExecutionContext?, activities: MutableList<AActivity?>?) {
            val dataUpdater = DataUpdater(execContext)
            for (mbActivity in activities) {
                dataUpdater.onActivity(mbActivity)
            }
        }
    }
}