/*
 * Copyright (C) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.database.Cursor
import android.provider.BaseColumns
import org.andstatus.app.account.MyAccount
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.data.DbUtils
import org.andstatus.app.data.DownloadStatus
import org.andstatus.app.data.MyQuery
import org.andstatus.app.data.TextMediaType
import org.andstatus.app.database.table.NoteTable
import org.andstatus.app.origin.Origin
import org.andstatus.app.origin.OriginType
import org.andstatus.app.service.CommandData
import org.andstatus.app.service.CommandEnum
import org.andstatus.app.service.MyServiceManager
import org.andstatus.app.util.LazyVal
import org.andstatus.app.util.MyHtml
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.StringUtil
import org.andstatus.app.util.TriState
import org.andstatus.app.util.UriUtils
import java.util.*

/**
 * Note ("Tweet", "toot" etc.) of a Social Network
 * @author yvolk@yurivolkov.com
 */
class Note : AObject {
    private var status: DownloadStatus = DownloadStatus.UNKNOWN
    val oid: String
    var updatedDate: Long = 0

    @Volatile
    private var audience: Audience
    private var name: String = ""
    var summary: String = ""
        private set
    private var isSensitive = false
    var content: String = ""
        private set
    private val contentToSearch: LazyVal<String> = LazyVal.of { evalContentToSearch() }
    private var inReplyTo: AActivity = AActivity.EMPTY
    val replies: MutableList<AActivity>
    var conversationOid: String = ""
    var via: String = ""
    var url: String = ""
    private var likesCount: Long = 0
    private var reblogsCount: Long = 0
    private var repliesCount: Long = 0
    val attachments: Attachments

    /** Some additional attributes may appear from "My account's" (authenticated Account's) point of view  */ // In our system
    val origin: Origin
    var noteId = 0L
    private var conversationId = 0L

    private fun loadAudience(): Note {
        audience = Audience.load(origin, noteId, Optional.empty())
        return this
    }

    private constructor(origin: Origin, oid: String) {
        this.origin = origin
        this.oid = oid
        audience = if (origin.isEmpty) Audience.EMPTY else Audience(origin)
        replies = if (origin.isEmpty) mutableListOf() else ArrayList()
        attachments = if (origin.isEmpty) Attachments.EMPTY else Attachments()
    }

    fun update(accountActor: Actor): AActivity {
        return act(accountActor, Actor.EMPTY, ActivityType.UPDATE)
    }

    fun act(accountActor: Actor, actor: Actor, activityType: ActivityType): AActivity {
        val mbActivity: AActivity = AActivity.from(accountActor, activityType)
        mbActivity.setActor(actor)
        mbActivity.setNote(this)
        return mbActivity
    }

    fun getName(): String {
        return name
    }

    fun getContentToPost(): String {
        return MyHtml.fromContentStored(content, origin.originType.textMediaTypeToPost)
    }

    fun getContentToSearch(): String {
        return contentToSearch.get()
    }

    private fun isHtmlContentAllowed(): Boolean {
        return origin.isHtmlContentAllowed()
    }

    private fun evalContentToSearch(): String {
        return MyHtml.getContentToSearch(
                (if (name.isNotEmpty()) "$name " else "") +
                (if (summary.isNotEmpty()) "$summary " else "") +
                content
        )
    }

    fun setName(name: String?): Note {
        this.name = MyHtml.htmlToCompactPlainText(name)
        contentToSearch.reset()
        return this
    }

    fun setSummary(summary: String?) {
        this.summary = MyHtml.htmlToCompactPlainText(summary)
        contentToSearch.reset()
    }

    fun setContentStored(content: String) {
        this.content = content
        contentToSearch.reset()
    }

    fun setContentPosted(content: String?): Note {
        setContent(content, origin.originType.textMediaTypePosted)
        return this
    }

    fun setContent(content: String?, mediaType: TextMediaType?) {
        this.content = MyHtml.toContentStored(content, mediaType, isHtmlContentAllowed())
        contentToSearch.reset()
    }

    fun setConversationOid(conversationOid: String?): Note {
        if (conversationOid.isNullOrEmpty()) {
            this.conversationOid = ""
        } else {
            this.conversationOid = conversationOid
        }
        return this
    }

    fun lookupConversationId(): Long {
        if (conversationId == 0L && conversationOid.isNotEmpty()) {
            conversationId = MyQuery.conversationOidToId(origin.id, conversationOid)
        }
        if (conversationId == 0L && noteId != 0L) {
            conversationId = MyQuery.noteIdToLongColumnValue(NoteTable.CONVERSATION_ID, noteId)
        }
        if (conversationId == 0L && getInReplyTo().nonEmpty) {
            if (getInReplyTo().getNote().noteId != 0L) {
                conversationId = MyQuery.noteIdToLongColumnValue(NoteTable.CONVERSATION_ID,
                        getInReplyTo().getNote().noteId)
            }
        }
        return setConversationIdFromMsgId()
    }

    fun setConversationIdFromMsgId(): Long {
        if (conversationId == 0L && noteId != 0L) {
            conversationId = noteId
        }
        return conversationId
    }

    fun getConversationId(): Long {
        return conversationId
    }

    fun getStatus(): DownloadStatus {
        return status
    }

    override val isEmpty: Boolean
        get() = !origin.isValid() || UriUtils.nonRealOid(oid) && status != DownloadStatus.DELETED &&
                (status != DownloadStatus.SENDING && status != DownloadStatus.DRAFT || !hasSomeContent())

    fun hasSomeContent(): Boolean = name.isNotEmpty() || summary.isNotEmpty() || content.isNotEmpty() ||
            attachments.nonEmpty

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is Note) {
            return false
        }
        return hashCode() == other.hashCode()
    }

    override fun hashCode(): Int {
        return toString().hashCode()
    }

    override fun toString(): String {
        if (this === EMPTY) {
            return MyStringBuilder.formatKeyValue(this, "EMPTY")
        }
        val builder = MyStringBuilder()
        builder.withComma("", "empty") { this.isEmpty }
        builder.withCommaNonEmpty("id", noteId)
        builder.withComma("conversation_id", conversationId) { conversationId != noteId }
        builder.withComma("status", status)
        builder.withCommaNonEmpty("name", name)
        builder.withCommaNonEmpty("summary", summary)
        builder.withCommaNonEmpty("content", content)
        builder.atNewLine("audience", audience.toAudienceString(Actor.EMPTY))
        builder.withComma("oid", oid) { UriUtils.isRealOid(oid) }
        builder.withComma("conversation_oid", conversationOid) { UriUtils.isRealOid(conversationOid) }
        builder.withCommaNonEmpty("url", url)
        builder.withCommaNonEmpty("via", via)
        builder.withComma("updated", MyLog.debugFormatOfDate(updatedDate))
        builder.withComma("origin", origin.name)
        if (attachments.nonEmpty) {
            builder.atNewLine(attachments.toString())
        }
        if (getInReplyTo().nonEmpty) {
            builder.atNewLine("inReplyTo", getInReplyTo().toString())
        }
        if (replies.size > 0) {
            builder.atNewLine("Replies", replies.toString())
        }
        return MyStringBuilder.formatKeyValue(this, builder.toString())
    }

    fun getInReplyTo(): AActivity {
        return inReplyTo
    }

    fun setInReplyTo(activity: AActivity?): Note {
        if (activity != null && activity.nonEmpty) {
            inReplyTo = activity
        }
        return this
    }

    fun isSensitive(): Boolean {
        return isSensitive
    }

    fun setSensitive(sensitive: Boolean): Note {
        isSensitive = sensitive
        return this
    }

    fun audience(): Audience {
        return audience
    }

    fun shallowCopy(): Note {
        val note = fromOriginAndOid(origin, oid, status)
        note.noteId = noteId
        note.updatedDate = updatedDate
        return note
    }

    fun withAttachments(attachments: Attachments): Note {
        return Note(this, Optional.empty(), Optional.of(attachments))
    }

    fun withNewOid(oid: String): Note {
        return Note(this, Optional.of(oid), Optional.empty())
    }

    private constructor(note: Note, oidNew: Optional<String>, attachments: Optional<Attachments>) {
        origin = note.origin
        oid = fixedOid(oidNew.orElse(note.oid))
        status = fixedStatus(oid, note.status)
        audience = note.audience.copy()
        noteId = note.noteId
        updatedDate = note.updatedDate
        name = note.name
        summary = note.summary
        isSensitive = note.isSensitive
        setContentStored(note.content)
        inReplyTo = note.inReplyTo
        replies = note.replies
        conversationOid = note.conversationOid
        via = note.via
        url = note.url
        likesCount = note.likesCount
        reblogsCount = note.reblogsCount
        repliesCount = note.repliesCount
        this.attachments = attachments.orElseGet { note.attachments.copy() }
        conversationId = note.conversationId
    }

    fun addFavoriteBy(accountActor: Actor, favoritedByMe: TriState) {
        if (favoritedByMe != TriState.TRUE) {
            return
        }
        val favorite: AActivity = AActivity.from(accountActor, ActivityType.LIKE)
        favorite.setActor(accountActor)
        favorite.setUpdatedDate(updatedDate)
        favorite.setNote(shallowCopy())
        replies.add(favorite)
    }

    fun getFavoritedBy(accountActor: Actor): TriState {
        return if (noteId == 0L) {
            for (reply in replies) {
                if (reply.type == ActivityType.LIKE && reply.getActor() == accountActor && reply.getNote().oid == oid) {
                    return TriState.TRUE
                }
            }
            TriState.UNKNOWN
        } else {
            val favAndType = MyQuery.noteIdToLastFavoriting( MyContextHolder.myContextHolder.getNow().getDatabase(),
                    noteId, accountActor.actorId)
            when (favAndType.second) {
                ActivityType.LIKE -> TriState.TRUE
                ActivityType.UNDO_LIKE -> TriState.FALSE
                else -> TriState.UNKNOWN
            }
        }
    }

    fun setDiscarded() {
        status = if (UriUtils.isRealOid(oid)) DownloadStatus.LOADED else DownloadStatus.DELETED
    }

    fun setStatus(status: DownloadStatus) {
        this.status = status
    }

    fun setAudience(audience: Audience) {
        this.audience = audience
    }

    fun setUpdatedNow(level: Int) {
        if (isEmpty || level > 10) return
        updatedDate = MyLog.uniqueCurrentTimeMS()
        inReplyTo.setUpdatedNow(level + 1)
    }

    fun setLikesCount(likesCount: Long) {
        this.likesCount = likesCount
    }

    fun getLikesCount(): Long {
        return likesCount
    }

    fun setReblogsCount(reblogsCount: Long) {
        this.reblogsCount = reblogsCount
    }

    fun getReblogsCount(): Long {
        return reblogsCount
    }

    fun setRepliesCount(repliesCount: Long) {
        this.repliesCount = repliesCount
    }

    fun getRepliesCount(): Long {
        return repliesCount
    }

    override fun classTag(): String {
        return TAG
    }

    companion object {
        private val TAG: String = Note::class.java.simpleName
        val EMPTY: Note = Note( Origin.EMPTY, getTempOid())

        fun fromOriginAndOid(origin: Origin, oid: String?, status: DownloadStatus): Note {
            val note = Note(origin, fixedOid(oid))
            note.status = fixedStatus(note.oid, status)
            return note
        }

        private fun fixedOid(oid: String?): String {
            return if (oid == null || UriUtils.isEmptyOid(oid)) getTempOid() else oid
        }

        private fun fixedStatus(oid: String?, status: DownloadStatus): DownloadStatus {
            return if (oid.isNullOrEmpty() && status == DownloadStatus.LOADED) {
                DownloadStatus.UNKNOWN
            } else status
        }

        fun getSqlToLoadContent(id: Long): String {
            val sql = ("SELECT " + BaseColumns._ID
                    + ", " + NoteTable.CONTENT
                    + ", " + NoteTable.CONTENT_TO_SEARCH
                    + ", " + NoteTable.NOTE_OID
                    + ", " + NoteTable.ORIGIN_ID
                    + ", " + NoteTable.NAME
                    + ", " + NoteTable.SUMMARY
                    + ", " + NoteTable.SENSITIVE
                    + ", " + NoteTable.NOTE_STATUS
                    + " FROM " + NoteTable.TABLE_NAME)
            return sql + if (id == 0L) "" else " WHERE " + BaseColumns._ID + "=" + id
        }

        fun contentFromCursor(myContext: MyContext, cursor: Cursor): Note {
            val note = fromOriginAndOid(myContext.origins().fromId(DbUtils.getLong(cursor, NoteTable.ORIGIN_ID)),
                    DbUtils.getString(cursor, NoteTable.NOTE_OID),
                    DownloadStatus.load(DbUtils.getLong(cursor, NoteTable.NOTE_STATUS)))
            note.noteId = DbUtils.getLong(cursor, BaseColumns._ID)
            note.setName(DbUtils.getString(cursor, NoteTable.NAME))
            note.setSummary(DbUtils.getString(cursor, NoteTable.SUMMARY))
            note.setSensitive(DbUtils.getBoolean(cursor, NoteTable.SENSITIVE))
            note.setContentStored(DbUtils.getString(cursor, NoteTable.CONTENT))
            return note
        }

        fun loadContentById(myContext: MyContext, noteId: Long): Note {
            return MyQuery[myContext, getSqlToLoadContent(noteId), { cursor: Cursor -> contentFromCursor(myContext, cursor) }]
                    .stream().findAny().map { obj: Note -> obj.loadAudience() }.orElse(EMPTY)
        }

        private fun getTempOid(): String {
            return StringUtil.toTempOid("note:" + MyLog.uniqueCurrentTimeMS())
        }

        fun mayBeEdited(originType: OriginType?, downloadStatus: DownloadStatus?): Boolean {
            return if (originType == null || downloadStatus == null) false
            else downloadStatus == DownloadStatus.DRAFT || downloadStatus.mayBeSent() ||
                    downloadStatus.isPresentAtServer() && originType.allowEditing()
        }

        fun requestDownload(ma: MyAccount, noteId: Long, isManuallyLaunched: Boolean) {
            MyLog.v(TAG) { "Note id:$noteId will be loaded from the Internet" }
            val command: CommandData = CommandData.newItemCommand(CommandEnum.GET_NOTE, ma, noteId)
                    .setManuallyLaunched(isManuallyLaunched)
                    .setInForeground(isManuallyLaunched)
            MyServiceManager.sendCommand(command)
        }
    }
}