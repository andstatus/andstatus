/* 
 * Copyright (c) 2011-2016 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.service

import io.vavr.control.Try
import org.andstatus.app.context.DemoData
import org.andstatus.app.data.DataUpdater
import org.andstatus.app.data.DownloadData
import org.andstatus.app.data.DownloadStatus
import org.andstatus.app.data.MyProvider
import org.andstatus.app.data.MyQuery
import org.andstatus.app.data.OidEnum
import org.andstatus.app.data.checker.CheckConversations
import org.andstatus.app.database.table.ActivityTable
import org.andstatus.app.database.table.NoteTable
import org.andstatus.app.net.http.ConnectionException
import org.andstatus.app.net.http.ConnectionException.StatusCode
import org.andstatus.app.net.social.AActivity
import org.andstatus.app.net.social.ActivityType
import org.andstatus.app.net.social.Actor
import org.andstatus.app.net.social.Attachments
import org.andstatus.app.net.social.Note
import org.andstatus.app.net.social.RateLimitStatus
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.RelativeTime
import org.andstatus.app.util.StringUtil
import org.andstatus.app.util.TriState
import org.andstatus.app.util.TryUtils
import org.andstatus.app.util.UriUtils
import java.util.stream.Collectors

internal class CommandExecutorOther(execContext: CommandExecutionContext) : CommandExecutorStrategy(execContext) {

    override suspend fun execute(): Try<Boolean> {
        return when (execContext.commandData.command) {
            CommandEnum.LIKE, CommandEnum.UNDO_LIKE -> createOrDestroyFavorite(execContext.commandData.itemId,
                    execContext.commandData.command == CommandEnum.LIKE)
            CommandEnum.FOLLOW, CommandEnum.UNDO_FOLLOW -> followOrStopFollowingActor(getActor(),
                    execContext.commandData.command == CommandEnum.FOLLOW)
            CommandEnum.UPDATE_NOTE -> updateNote(execContext.commandData.itemId)
            CommandEnum.DELETE_NOTE -> deleteNote(execContext.commandData.itemId)
            CommandEnum.UNDO_ANNOUNCE -> undoAnnounce(execContext.commandData.itemId)
            CommandEnum.GET_CONVERSATION -> getConversation(execContext.commandData.itemId)
            CommandEnum.GET_NOTE -> getNote(execContext.commandData.itemId)
            CommandEnum.GET_ACTOR -> getActorCommand(getActor(), execContext.commandData.getUsername())
            CommandEnum.SEARCH_ACTORS -> searchActors(execContext.commandData.getUsername())
            CommandEnum.ANNOUNCE -> reblog(execContext.commandData.itemId)
            CommandEnum.RATE_LIMIT_STATUS -> rateLimitStatus()
            CommandEnum.GET_ATTACHMENT -> FileDownloader.newForDownloadData(execContext.myContext, DownloadData.fromId(execContext.commandData.itemId))
                    .setConnectionRequired(ConnectionRequired.DOWNLOAD_ATTACHMENT)
                    .load(execContext.commandData)
            CommandEnum.GET_AVATAR -> AvatarDownloader(getActor()).load(execContext.commandData)
            else -> TryUtils.failure("Unexpected command here " + execContext.commandData)
        }
    }

    private fun searchActors(searchQuery: String?): Try<Boolean> {
        val method = "searchActors"
        val msgLog = "$method; query='$searchQuery'"
        return if (searchQuery.isNullOrEmpty()) {
            logExecutionError(true, "$msgLog, empty query")
        } else getConnection()
                .searchActors(ACTORS_LIMIT, searchQuery)
                .map { actors: List<Actor> ->
                    val dataUpdater = DataUpdater(execContext)
                    val myAccountActor = execContext.getMyAccount().actor
                    for (actor in actors) {
                        dataUpdater.onActivity(myAccountActor.update(actor))
                    }
                    true
                }
                .mapFailure { e: Throwable? -> ConnectionException.of(e).append(msgLog) }
    }

    private fun getConversation(noteId: Long): Try<Boolean> {
        val method = "getConversation"
        val conversationOid = MyQuery.noteIdToConversationOid(execContext.myContext, noteId)
        return if (conversationOid.isEmpty()) {
            logExecutionError(true, method + " empty conversationId " +
                    MyQuery.noteInfoForLog(execContext.myContext, noteId))
        } else getConnection()
                .getConversation(conversationOid)
                .onSuccess { activities: List<AActivity> ->
                    DataUpdater.onActivities(execContext, activities)
                    MyLog.d(this, method + if (noErrors()) " succeeded" else " failed")
                }
                .onSuccess { activities: List<AActivity> ->
                    val noteIds = activities.stream()
                            .map { activity: AActivity -> activity.getNote().noteId }
                            .collect(Collectors.toSet())
                    if (noteIds.size > 1) {
                        if (CheckConversations().setNoteIdsOfOneConversation(noteIds)
                                        .setMyContext(execContext.myContext).fix() > 0) {
                            execContext.commandData.getResult().incrementNewCount()
                        }
                    }
                }
                .map { activities -> true }
                .mapFailure { e: Throwable -> ConnectionException.of(e).append(MyQuery.noteInfoForLog(execContext.myContext, noteId)) }
    }

    private fun getActorCommand(actorIn: Actor, username: String?): Try<Boolean> {
        val method = "getActor"
        var msgLog = "$method;"
        val actorIn2 = if (UriUtils.nonRealOid(actorIn.oid) && actorIn.origin.isUsernameValid(username)
                && !actorIn.isUsernameValid()) Actor.fromId(actorIn.origin, actorIn.actorId).setUsername(username)
                .setWebFingerId(actorIn.getWebFingerId()) else actorIn
        if (!actorIn2.canGetActor()) {
            msgLog += ", cannot get Actor"
            return logExecutionError(true, msgLog + actorInfoLogged(actorIn2))
        }
        val msgLog2 = msgLog + "; username='" + actorIn2.getUsername() + "'"
        return getConnection()
                .getActor(actorIn2)
                .flatMap { actor: Actor -> failIfActorIsEmpty(msgLog2, actor) }
                .map { actor: Actor ->
                    val activity = execContext.getMyAccount().actor.update(actor)
                    DataUpdater(execContext).onActivity(activity)
                    true
                }
                .onFailure { e: Throwable? -> actorIn2.requestAvatarDownload() }
    }

    /**
     * @param create true - create, false - destroy
     */
    private fun createOrDestroyFavorite(noteId: Long, create: Boolean): Try<Boolean> {
        val method = (if (create) "create" else "destroy") + "Favorite"
        return getNoteOid(method, noteId, true)
                .flatMap { oid: String -> if (create) getConnection().like(oid) else getConnection().undoLike(oid) }
                .flatMap { activity: AActivity ->
                    failIfEmptyNote(method, noteId, activity.getNote())
                            .map { b: Boolean -> activity }
                }
                .flatMap { activity: AActivity ->
                    if (activity.type != if (create) ActivityType.LIKE else ActivityType.UNDO_LIKE) {
                        /*
                 * yvolk: 2011-09-27 Twitter docs state that
                 * this may happen due to asynchronous nature of
                 * the process, see
                 * https://dev.twitter.com/docs/
                 * api/1/post/favorites/create/%3Aid
                 */
                        if (create) {
                            // For the case we created favorite, let's
                            // change the flag manually.
                            val activity2 = activity.getNote().act(activity.accountActor, activity.getActor(), ActivityType.LIKE)
                            MyLog.d(this, "$method; Favorited flag didn't change yet.")
                            // Let's try to assume that everything was OK
                            return@flatMap Try.success(activity2)
                        } else {
                            // yvolk: 2011-09-27 Sometimes this
                            // twitter.com 'async' process doesn't work
                            // so let's try another time...
                            // This is safe, because "delete favorite"
                            // works even for the "Unfavorited" tweet :-)
                            return@flatMap logExecutionError<AActivity>(false, method + "; Favorited flag didn't change yet. " +
                                    MyQuery.noteInfoForLog(execContext.myContext, noteId))
                        }
                    }
                    Try.success(activity)
                }
                .map { activity: AActivity ->
                    // Please note that the Favorited note may be NOT in the Account's Home timeline!
                    DataUpdater(execContext).onActivity(activity)
                    true
                }
    }

    private fun getNoteOid(method: String?, noteId: Long, required: Boolean): Try<String> {
        val oid = MyQuery.idToOid(execContext.myContext, OidEnum.NOTE_OID, noteId, 0)
        return if (required && oid.isEmpty()) {
            logExecutionError(true, method + "; no note ID in the Social Network "
                    + MyQuery.noteInfoForLog(execContext.myContext, noteId))
        } else Try.success(oid)
    }

    /**
     * @param follow true - Follow, false - Stop following
     */
    private fun followOrStopFollowingActor(actor: Actor, follow: Boolean): Try<Boolean> {
        val method = (if (follow) "follow" else "stopFollowing") + "Actor"
        return getConnection()
                .follow(actor.oid, follow)
                .flatMap { activity: AActivity ->
                    val friend = activity.getObjActor()
                    friend.isMyFriend = TriState.UNKNOWN // That "hack" attribute may only confuse us here as it can show outdated info
                    failIfActorIsEmpty(method, friend)
                            .map { a: Actor ->
                                DataUpdater(execContext).onActivity(activity)
                                true
                            }
                }
    }

    private fun failIfActorIsEmpty(method: String, actor: Actor): Try<Actor> {
        return if (actor.isEmpty) {
            logExecutionError(false, "Actor is empty, $method")
        } else Try.success(actor)
    }

    private fun actorInfoLogged(actor: Actor): String {
        return actor.toString()
    }

    private fun deleteNote(noteId: Long): Try<Boolean> {
        val method = "deleteNote"
        if (noteId == 0L) {
            MyLog.d(this, "$method skipped as noteId == 0")
            return Try.success(true)
        }
        val author: Actor = Actor.load(execContext.myContext, MyQuery.noteIdToActorId(NoteTable.AUTHOR_ID, noteId))
        return (if (execContext.getMyAccount().actor.isSame(author)) deleteNoteAtServer(noteId, method) else Try.success(true))
                .onSuccess { b: Boolean? -> MyProvider.deleteNoteAndItsActivities(execContext.myContext, noteId) }
    }

    private fun deleteNoteAtServer(noteId: Long, method: String): Try<Boolean> {
        val tryOid = getNoteOid(method, noteId, false)
        val statusStored: DownloadStatus = DownloadStatus.load(MyQuery.noteIdToLongColumnValue(NoteTable.NOTE_STATUS, noteId))
        if (tryOid.filter { obj: String? -> StringUtil.nonEmptyNonTemp(obj) }.isFailure || statusStored != DownloadStatus.LOADED) {
            MyLog.i(this, "$method; OID='$tryOid', status='$statusStored' for noteId=$noteId")
            return Try.success(true)
        }
        return tryOid
                .flatMap { oid: String -> getConnection().deleteNote(oid) }
                .recoverWith(ConnectionException::class.java
                ) { e: ConnectionException ->  // "Not found" means that there is no such "Status", so we may
                    // assume that it's Ok!
                    if (e.getStatusCode() == StatusCode.NOT_FOUND) Try.success(true) else logException<Any?>(e, method + "; noteOid:" + tryOid + ", " +
                            MyQuery.noteInfoForLog(execContext.myContext, noteId)).map { any: Any? -> true }
                }
    }

    private fun undoAnnounce(noteId: Long): Try<Boolean> {
        val method = "destroyReblog"
        val actorId = execContext.getMyAccount().actorId
        val reblogAndType = MyQuery.noteIdToLastReblogging(
                execContext.myContext.database, noteId, actorId)
        if (reblogAndType.second != ActivityType.ANNOUNCE) {
            return logExecutionError(true, "No local Reblog of "
                    + MyQuery.noteInfoForLog(execContext.myContext, noteId) +
                    " by " + execContext.getMyAccount())
        }
        val reblogOid = MyQuery.idToOid(execContext.myContext, OidEnum.REBLOG_OID, noteId, actorId)
        return getConnection()
                .undoAnnounce(reblogOid)
                .recoverWith(ConnectionException::class.java
                ) { e: ConnectionException ->  // "Not found" means that there is no such "Status", so we may
                    // assume that it's Ok!
                    if (e.getStatusCode() == StatusCode.NOT_FOUND) Try.success(true) else logException<Any?>(e, method + "; reblogOid:" + reblogOid + ", " +
                            MyQuery.noteInfoForLog(execContext.myContext, noteId)).map { any: Any? -> true }
                }
                .onSuccess { b: Boolean ->  // And delete the reblog from local storage
                    MyProvider.deleteActivity(execContext.myContext, reblogAndType.first ?: 0, noteId, false)
                }
    }

    private fun getNote(noteId: Long): Try<Boolean> {
        val method = "getNote"
        return getNoteOid(method, noteId, true)
                .flatMap { oid: String -> getConnection().getNote(oid) }
                .flatMap { activity: AActivity ->
                    if (activity.isEmpty) {
                        return@flatMap logExecutionError<Boolean>(false, "Received Note is empty, "
                                + MyQuery.noteInfoForLog(execContext.myContext, noteId))
                    } else {
                        try {
                            DataUpdater(execContext).onActivity(activity)
                            return@flatMap Try.success(true)
                        } catch (e: Exception) {
                            return@flatMap logExecutionError<Boolean>(false, "Error while saving to the local cache,"
                                    + MyQuery.noteInfoForLog(execContext.myContext, noteId) + ", " + e.message)
                        }
                    }
                }
                .onFailure { e: Throwable ->
                    if (ConnectionException.of(e).getStatusCode() == StatusCode.NOT_FOUND) {
                        execContext.getResult().incrementParseExceptions()
                        // This means that there is no such "Status"
                        // TODO: so we don't need to retry this command
                    }
                }
    }

    private fun updateNote(activityId: Long): Try<Boolean> {
        val method = "updateNote"
        val noteId = MyQuery.activityIdToLongColumnValue(ActivityTable.NOTE_ID, activityId)
        val note: Note = Note.loadContentById(execContext.myContext, noteId)
                .withAttachments(Attachments.load(execContext.myContext, noteId))
        getNoteOid(method, MyQuery.noteIdToLongColumnValue(NoteTable.IN_REPLY_TO_NOTE_ID, noteId), false)
                .filter { obj: String? -> StringUtil.nonEmptyNonTemp(obj) }
                .map { inReplyToNoteOid: String? ->
                    AActivity.newPartialNote(execContext.getMyAccount().actor,
                            Actor.EMPTY, inReplyToNoteOid, RelativeTime.DATETIME_MILLIS_NEVER, DownloadStatus.UNKNOWN)
                            .setOid(inReplyToNoteOid)
                }
                .onSuccess { activity: AActivity? -> note.setInReplyTo(activity) }
        DemoData.crashTest { note.content.startsWith("Crash me on sending 2015-04-10") }
        if (MyLog.isVerboseEnabled()) {
            val msgLog = ((if (!note.getName().isEmpty()) "name:'" + note.getName() + "'; " else "")
                    + (if (!note.summary.isEmpty()) "summary:'" + note.summary + "'; " else "")
                    + (if (!note.content.isEmpty()) "content:'" + MyLog.trimmedString(note.getContentToPost(), 80) + "'" else "")
                    + if (note.attachments.isEmpty) "" else "; " + note.attachments)
            MyLog.v(this) { "$method;$msgLog" }
        }
        return if (!note.getStatus().mayBeSent()) {
            Try.failure(ConnectionException.hardConnectionException("Wrong note status: " + note.getStatus(), null))
        } else getConnection().updateNote(note)
                .flatMap { activity: AActivity ->
                    failIfEmptyNote(method, noteId, activity.getNote()).map { b: Boolean? ->
                        // The note was sent successfully, so now update unsent message
                        // New Actor's note should be put into the Account's Home timeline.
                        activity.setId(activityId)
                        activity.getNote().noteId = noteId
                        DataUpdater(execContext).onActivity(activity)
                        execContext.getResult().setItemId(noteId)
                        true
                    }
                            .onFailure { e: Throwable? -> execContext.myContext.notifier.onUnsentActivity(activityId) }
                }
    }

    private fun failIfEmptyNote(method: String?, noteId: Long, note: Note?): Try<Boolean> {
        return if (note == null || note.isEmpty) {
            logExecutionError(false, method + "; Received note is empty, "
                    + MyQuery.noteInfoForLog(execContext.myContext, noteId))
        } else Try.success(true)
    }

    private fun reblog(rebloggedNoteId: Long): Try<Boolean> {
        val method = "Reblog"
        return getNoteOid(method, rebloggedNoteId, true)
                .flatMap { oid: String -> getConnection().announce(oid) }
                .map { activity: AActivity ->
                    failIfEmptyNote(method, rebloggedNoteId, activity.getNote())
                    // The tweet was sent successfully
                    // Reblog should be put into the Account's Home timeline!
                    DataUpdater(execContext).onActivity(activity)
                    MyProvider.updateNoteReblogged(execContext.myContext, activity.accountActor.origin, rebloggedNoteId)
                    true
                }
    }

    private fun rateLimitStatus(): Try<Boolean> {
        return getConnection().rateLimitStatus()
                .map { rateLimitStatus: RateLimitStatus ->
                    if (rateLimitStatus.nonEmpty) {
                        execContext.getResult().setRemainingHits(rateLimitStatus.remaining)
                        execContext.getResult().setHourlyLimit(rateLimitStatus.limit)
                    }
                    rateLimitStatus.nonEmpty
                }
    }

    companion object {
        private const val ACTORS_LIMIT = 400
    }
}
