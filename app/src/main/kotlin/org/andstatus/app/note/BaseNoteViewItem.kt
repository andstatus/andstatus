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
package org.andstatus.app.note

import android.content.Context
import android.database.Cursor
import android.text.Html
import android.text.Spannable
import org.andstatus.app.R
import org.andstatus.app.account.MyAccount
import org.andstatus.app.actor.ActorViewItem
import org.andstatus.app.actor.ActorsLoader
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.data.AttachedImageFiles
import org.andstatus.app.data.DbUtils
import org.andstatus.app.data.DownloadStatus
import org.andstatus.app.data.MyQuery
import org.andstatus.app.data.TextMediaType
import org.andstatus.app.database.table.ActivityTable
import org.andstatus.app.database.table.NoteTable
import org.andstatus.app.net.social.Actor
import org.andstatus.app.net.social.Audience
import org.andstatus.app.net.social.SpanUtil
import org.andstatus.app.net.social.Visibility
import org.andstatus.app.origin.Origin
import org.andstatus.app.timeline.DuplicationLink
import org.andstatus.app.timeline.TimelineFilter
import org.andstatus.app.timeline.ViewItem
import org.andstatus.app.timeline.meta.Timeline
import org.andstatus.app.util.I18n
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.RelativeTime
import org.andstatus.app.util.SharedPreferencesUtil
import org.andstatus.app.util.StringUtil
import org.andstatus.app.util.TriState
import java.util.*
import java.util.concurrent.TimeUnit

abstract class BaseNoteViewItem<T : BaseNoteViewItem<T>> : ViewItem<T> {
    var myContext: MyContext =  MyContextHolder.myContextHolder.getNow()
    var activityUpdatedDate: Long = 0
    var noteStatus: DownloadStatus = DownloadStatus.UNKNOWN
    private var activityId: Long = 0
    private var noteId: Long = 0
    private var origin: Origin =  Origin.EMPTY
    var author: ActorViewItem = ActorViewItem.EMPTY
        protected set
    var visibility: Visibility = Visibility.UNKNOWN
    private var isSensitive = false
    var audience: Audience = Audience.EMPTY
    var inReplyToNoteId: Long = 0
    var inReplyToActor: ActorViewItem = ActorViewItem.EMPTY
    var noteSource: String = ""
    private var nameString: String = ""
    private var name: Spannable = SpanUtil.EMPTY
    private var summaryString: String = ""
    private var summary: Spannable = SpanUtil.EMPTY
    private var contentString: String = ""
    private var content: Spannable = SpanUtil.EMPTY
    var contentToSearch: String = ""
    var likesCount: Long = 0
    var reblogsCount: Long = 0
    var repliesCount: Long = 0
    var favorited = false
    var rebloggers: MutableMap<Long, String> = HashMap()
    var reblogged = false
    val attachmentsCount: Long
    val attachedImageFiles: AttachedImageFiles
    private var linkedMyAccount: MyAccount = MyAccount.EMPTY
    val detailsSuffix: StringBuilder = StringBuilder()

    protected constructor(isEmpty: Boolean, updatedDate: Long) : super(isEmpty, updatedDate) {
        attachmentsCount = 0
        attachedImageFiles = AttachedImageFiles.EMPTY
    }

    internal constructor(myContext: MyContext, cursor: Cursor?) : super(false, DbUtils.getLong(cursor, NoteTable.UPDATED_DATE)) {
        activityId = DbUtils.getLong(cursor, ActivityTable.ACTIVITY_ID)
        setNoteId(DbUtils.getLong(cursor, ActivityTable.NOTE_ID))
        setOrigin(myContext.origins().fromId(DbUtils.getLong(cursor, ActivityTable.ORIGIN_ID)))
        isSensitive = DbUtils.getBoolean(cursor, NoteTable.SENSITIVE)
        likesCount = DbUtils.getLong(cursor, NoteTable.LIKES_COUNT)
        reblogsCount = DbUtils.getLong(cursor, NoteTable.REBLOGS_COUNT)
        repliesCount = DbUtils.getLong(cursor, NoteTable.REPLIES_COUNT)
        this.myContext = myContext
        if (MyPreferences.getDownloadAndDisplayAttachedImages()) {
            attachmentsCount = DbUtils.getLong(cursor, NoteTable.ATTACHMENTS_COUNT)
            attachedImageFiles = if (attachmentsCount == 0L) AttachedImageFiles.EMPTY else AttachedImageFiles.load(myContext, noteId)
        } else {
            attachmentsCount = 0
            attachedImageFiles = AttachedImageFiles.EMPTY
        }
    }

    fun setOtherViewProperties(cursor: Cursor?) {
        setName(DbUtils.getString(cursor, NoteTable.NAME))
        setSummary(DbUtils.getString(cursor, NoteTable.SUMMARY))
        setContent(DbUtils.getString(cursor, NoteTable.CONTENT))
        inReplyToNoteId = DbUtils.getLong(cursor, NoteTable.IN_REPLY_TO_NOTE_ID)
        inReplyToActor = ActorViewItem.fromActorId(getOrigin(), DbUtils.getLong(cursor, NoteTable.IN_REPLY_TO_ACTOR_ID))
        visibility = Visibility.fromCursor(cursor)
        audience = Audience.fromNoteId(getOrigin(), getNoteId(), visibility)
        noteStatus = DownloadStatus.load(DbUtils.getLong(cursor, NoteTable.NOTE_STATUS))
        favorited = DbUtils.getTriState(cursor, NoteTable.FAVORITED) == TriState.TRUE
        reblogged = DbUtils.getTriState(cursor, NoteTable.REBLOGGED) == TriState.TRUE
        val via = DbUtils.getString(cursor, NoteTable.VIA)
        if (!via.isEmpty()) {
            noteSource = Html.fromHtml(via).toString().trim { it <= ' ' }
        }
        for (actor in MyQuery.getRebloggers( MyContextHolder.myContextHolder.getNow().getDatabase(), getOrigin(), getNoteId())) {
            rebloggers[actor.actorId] = actor.getWebFingerId()
        }
    }

    fun getActivityId(): Long {
        return activityId
    }

    fun getNoteId(): Long {
        return noteId
    }

    fun setNoteId(noteId: Long) {
        this.noteId = noteId
    }

    fun getOrigin(): Origin {
        return origin
    }

    fun setOrigin(origin: Origin) {
        this.origin = origin
    }

    fun setLinkedAccount(linkedActorId: Long) {
        linkedMyAccount = myContext.accounts().fromActorId(linkedActorId)
    }

    fun getLinkedMyAccount(): MyAccount {
        return linkedMyAccount
    }

    private fun setCollapsedStatus(noteDetails: MyStringBuilder) {
        if (isCollapsed()) {
            noteDetails.withSpace("(+${getChildrenCount()}")
        }
    }

    override fun duplicates(timeline: Timeline, preferredOrigin: Origin, other: T): DuplicationLink {
        if (isEmpty || other.isEmpty) return DuplicationLink.NONE
        return if (getNoteId() == other.getNoteId()) duplicatesByFavoritedAndReblogged(preferredOrigin, other) else duplicatesByOther(preferredOrigin, other)
    }

    private fun duplicatesByFavoritedAndReblogged(preferredOrigin: Origin, other: T): DuplicationLink {
        if (favorited != other.favorited) {
            return if (favorited) DuplicationLink.IS_DUPLICATED else DuplicationLink.DUPLICATES
        } else if (reblogged != other.reblogged) {
            return if (reblogged) DuplicationLink.IS_DUPLICATED else DuplicationLink.DUPLICATES
        }
        if (preferredOrigin.nonEmpty
                && author.actor.origin != other.author.actor.origin) {
            if (preferredOrigin == author.actor.origin) return DuplicationLink.IS_DUPLICATED
            if (preferredOrigin == other.author.actor.origin) return DuplicationLink.DUPLICATES
        }
        if (getLinkedMyAccount() != other.getLinkedMyAccount()) {
            return if (getLinkedMyAccount().compareTo(other.getLinkedMyAccount()) <= 0) DuplicationLink.IS_DUPLICATED else DuplicationLink.DUPLICATES
        }
        return if (rebloggers.size > other.rebloggers.size) DuplicationLink.IS_DUPLICATED else DuplicationLink.DUPLICATES
    }

    private fun duplicatesByOther(preferredOrigin: Origin, other: T): DuplicationLink {
        if (updatedDate > RelativeTime.SOME_TIME_AGO && other.updatedDate > RelativeTime.SOME_TIME_AGO && Math.abs(updatedDate - other.updatedDate) >= TimeUnit.HOURS.toMillis(24) || isTooShortToCompare()
                || other.isTooShortToCompare()) return DuplicationLink.NONE
        if (contentToSearch == other.contentToSearch) {
            return if (updatedDate == other.updatedDate) {
                duplicatesByFavoritedAndReblogged(preferredOrigin, other)
            } else if (updatedDate < other.updatedDate) {
                DuplicationLink.IS_DUPLICATED
            } else {
                DuplicationLink.DUPLICATES
            }
        } else if (contentToSearch.contains(other.contentToSearch)) {
            return DuplicationLink.DUPLICATES
        } else if (other.contentToSearch.contains(contentToSearch)) {
            return DuplicationLink.IS_DUPLICATED
        }
        return DuplicationLink.NONE
    }

    fun isTooShortToCompare(): Boolean {
        return contentToSearch.length < MIN_LENGTH_TO_COMPARE
    }

    fun isReblogged(): Boolean {
        return !rebloggers.isEmpty()
    }

    open fun getDetails(context: Context, showReceivedTime: Boolean): MyStringBuilder {
        val builder = getMyStringBuilderWithTime(context, showReceivedTime)
        if (isSensitive() && MyPreferences.isShowSensitiveContent()) {
            builder.prependWithSeparator(myContext.context().getText(R.string.sensitive), " ")
        }
        setAudience(builder)
        setNoteSource(context, builder)
        setAccountDownloaded(builder)
        setNoteStatus(context, builder)
        setCollapsedStatus(builder)
        if (detailsSuffix.length > 0) builder.withSpace(detailsSuffix.toString())
        return builder
    }

    private fun setAudience(builder: MyStringBuilder) {
        builder.withSpace(audience.toAudienceString(inReplyToActor.actor))
    }

    private fun setNoteSource(context: Context, noteDetails: MyStringBuilder) {
        if (!SharedPreferencesUtil.isEmpty(noteSource) && "ostatus" != noteSource
                && "unknown" != noteSource) {
            noteDetails.withSpace(StringUtil.format(context, R.string.message_source_from, noteSource))
        }
    }

    private fun setAccountDownloaded(noteDetails: MyStringBuilder) {
        if (MyPreferences.isShowMyAccountWhichDownloadedActivity() && linkedMyAccount.isValid) {
            noteDetails.withSpace("a:" + linkedMyAccount.getShortestUniqueAccountName())
        }
    }

    private fun setNoteStatus(context: Context, noteDetails: MyStringBuilder) {
        if (noteStatus != DownloadStatus.LOADED) {
            noteDetails.withSpace("(").append(noteStatus.getTitle(context)).append(")")
        }
    }

    fun setName(name: String): BaseNoteViewItem<*> {
        nameString = name
        return this
    }

    fun getName(): Spannable {
        return name
    }

    fun setSummary(summary: String): BaseNoteViewItem<*> {
        summaryString = summary
        return this
    }

    fun getSummary(): Spannable {
        return summary
    }

    fun isSensitive(): Boolean {
        return isSensitive
    }

    fun setContent(content: String): BaseNoteViewItem<*> {
        contentString = content
        return this
    }

    fun getContent(): Spannable {
        return content
    }

    override fun getId(): Long {
        return getNoteId()
    }

    override fun getDate(): Long {
        return activityUpdatedDate
    }

    override fun matches(filter: TimelineFilter): Boolean {
        if (filter.keywordsFilter.nonEmpty || filter.searchQuery.nonEmpty) {
            if (filter.keywordsFilter.matchedAny(contentToSearch)) return false
            if (filter.searchQuery.nonEmpty && !filter.searchQuery.matchedAll(contentToSearch)) return false
        }
        return (!filter.hideRepliesNotToMeOrFriends
                || inReplyToActor.isEmpty
                ||  MyContextHolder.myContextHolder.getNow().users().isMeOrMyFriend(inReplyToActor.actor))
    }

    override fun toString(): String {
        return MyStringBuilder.formatKeyValue(this, I18n.trimTextAt(getContent().toString(), 40).toString() + ", "
                + getDetails(myContext.context(), false)
                + "', actorId:" + author.getActorId() + ", " + noteStatus
        )
    }

    fun hideTheReblogger(actor: Actor) {
        rebloggers.remove(actor.actorId)
    }

    override fun addActorsToLoad(loader: ActorsLoader) {
        loader.addActorToList(author.actor)
        loader.addActorToList(inReplyToActor.actor)
        audience.addActorsToLoad { actor: Actor -> loader.addActorToList(actor) }
    }

    override fun setLoadedActors(loader: ActorsLoader) {
        if (author.actor.nonEmpty) author = loader.getLoaded(author)
        if (inReplyToActor.actor.nonEmpty) inReplyToActor = loader.getLoaded(inReplyToActor)
        audience.setLoadedActors { actor: Actor -> loader.getLoaded(ActorViewItem.fromActor(actor)).actor }
        name = SpanUtil.textToSpannable(nameString, TextMediaType.PLAIN, audience)
        summary = SpanUtil.textToSpannable(summaryString, TextMediaType.PLAIN, audience)
        content = SpanUtil.textToSpannable(contentString, TextMediaType.HTML, audience)
    }

    companion object {
        private const val MIN_LENGTH_TO_COMPARE = 5
    }
}
