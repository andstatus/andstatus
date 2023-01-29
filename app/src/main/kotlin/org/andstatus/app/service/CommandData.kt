/*
 * Copyright (C) 2010-2016 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.os.Bundle
import android.provider.BaseColumns
import org.andstatus.app.IntentExtra
import org.andstatus.app.R
import org.andstatus.app.SearchObjects
import org.andstatus.app.account.MyAccount
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyContextHolder.Companion.myContextHolder
import org.andstatus.app.data.ContentValuesUtils
import org.andstatus.app.data.DbUtils
import org.andstatus.app.data.MyQuery
import org.andstatus.app.database.table.CommandTable
import org.andstatus.app.database.table.NoteTable
import org.andstatus.app.net.social.Actor
import org.andstatus.app.net.social.Attachments
import org.andstatus.app.origin.Origin
import org.andstatus.app.service.CommandQueue.OneQueue
import org.andstatus.app.timeline.ListScope
import org.andstatus.app.timeline.WhichPage
import org.andstatus.app.timeline.meta.Timeline
import org.andstatus.app.timeline.meta.TimelineTitle
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.BundleUtils
import org.andstatus.app.util.I18n
import org.andstatus.app.util.IsEmpty
import org.andstatus.app.util.MyHtml
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.RelativeTime
import org.andstatus.app.util.Taggable
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Command data store
 *
 * @author yvolk@yurivolkov.com
 */
class CommandData private constructor(
    commandId: Long,
    val command: CommandEnum,
    /** [MyAccount] for this command. Invalid account if command is not Account specific ( do we have such? )
     * It holds actorId for command, which needs such a parameter (not only for a timeline)
     */
    val myAccount: MyAccount,
    val commandTimeline: CommandTimeline,
    createdDate: Long
) : Comparable<CommandData>, Taggable, IsEmpty {

    val commandId: Long = if (commandId == 0L) MyLog.uniqueCurrentTimeMS else commandId
    val createdDate: Long = if (createdDate > 0) createdDate else this.commandId
    private var description: String = ""

    val myContext: MyContext
        get() = commandTimeline.myContext.takeIf { it.nonEmpty }
            ?: myAccount.myContext

    @Volatile
    private var mInForeground = false

    @Volatile
    private var mManuallyLaunched = false

    /** This is: 1. Generally: Note ID ([NoteTable.NOTE_ID] of the [NoteTable])...
     */
    var itemId: Long = 0

    /** Sometimes we don't know [Timeline.actor] yet...
     * Used for Actor search also  */
    var username: String = ""
        private set(value) {
            if (field.isEmpty() && value.isNotEmpty()) {
                field = value
            }
        }

    var result: CommandResult = CommandResult()
        private set

    init {
        resetRetries()
    }

    private fun setTrimmedNoteContentAsDescription(noteId: Long) {
        if (noteId != 0L) {
            description = trimConditionally(
                MyQuery.noteIdToStringColumnValue(NoteTable.CONTENT, noteId), true
            )
                .toString()
        }
    }

    /**
     * @return Intent to be sent to MyService
     */
    fun toIntent(intent: Intent): Intent {
        Objects.requireNonNull(intent)
        intent.putExtras(toBundle())
        return intent
    }

    private fun toBundle(): Bundle {
        val bundle = Bundle()
        BundleUtils.putNotEmpty(bundle, IntentExtra.COMMAND, command.save())
        if (command == CommandEnum.EMPTY) return bundle
        BundleUtils.putNotZero(bundle, IntentExtra.COMMAND_ID, commandId)
        if (myAccount.isValid) {
            bundle.putString(IntentExtra.ACCOUNT_NAME.key, myAccount.getAccountName())
        }
        timeline.toBundle(bundle)
        BundleUtils.putNotZero(bundle, IntentExtra.ITEM_ID, itemId)
        BundleUtils.putNotEmpty(bundle, IntentExtra.USERNAME, username)
        BundleUtils.putNotEmpty(bundle, IntentExtra.COMMAND_DESCRIPTION, description)
        bundle.putBoolean(IntentExtra.IN_FOREGROUND.key, mInForeground)
        bundle.putBoolean(IntentExtra.MANUALLY_LAUNCHED.key, mManuallyLaunched)
        bundle.putParcelable(IntentExtra.COMMAND_RESULT.key, result)
        return bundle
    }

    fun toContentValues(values: ContentValues) {
        ContentValuesUtils.putNotZero(values, BaseColumns._ID, commandId)
        ContentValuesUtils.putNotZero(values, CommandTable.CREATED_DATE, createdDate)
        values.put(CommandTable.COMMAND_CODE, command.save())
        values.put(CommandTable.ACCOUNT_ID, myAccount.actorId)
        ContentValuesUtils.putNotEmpty(values, CommandTable.DESCRIPTION, description)
        values.put(CommandTable.IN_FOREGROUND, mInForeground)
        values.put(CommandTable.MANUALLY_LAUNCHED, mManuallyLaunched)
        commandTimeline.toContentValues(values)
        ContentValuesUtils.putNotZero(values, CommandTable.ITEM_ID, itemId)
        values.put(CommandTable.USERNAME, username)
        result.toContentValues(values)
    }

    val searchQuery: String? get() = commandTimeline.searchQuery

    override fun toString(): String {
        if (this.isEmpty) return "(no command)"
        val builder = MyStringBuilder()
        builder.withComma("command", command.save())
        builder.withComma("id", commandId)
        if (mManuallyLaunched) {
            builder.withComma("manual")
        }
        if (mInForeground) {
            builder.withComma("foreground")
        }
        builder.withComma("account", myAccount.getAccountName()) { myAccount.nonEmpty }
        builder.withComma("username", username)
        if (description.isNotEmpty() && description != username) {
            builder.withSpaceQuoted(description)
        }
        if (commandTimeline.isValid()) {
            builder.withComma(commandTimeline.toString())
        }
        builder.withComma("itemId", itemId) { itemId != 0L }
        builder.withComma("created:" + RelativeTime.getDifference(myContext.context, createdDate))
        builder.withComma(CommandResult.toString(result))
        return MyStringBuilder.formatKeyValue(this, builder)
    }

    /** We need to distinguish duplicated commands but to ignore differences in results!  */
    override fun hashCode(): Int {
        val prime = 31
        var result = 1 + command.save().hashCode()
        result = prime * result + myAccount.hashCode()
        result += prime * commandTimeline.hashCode()
        result += (prime * itemId).toInt()
        return result
    }

    override val isEmpty: Boolean get() = this == EMPTY

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CommandData) return false
        if (command != other.command) return false
        if (myAccount != other.myAccount) return false
        return if (commandTimeline != other.commandTimeline) false else itemId == other.itemId
    }

    override operator fun compareTo(other: CommandData): Int {
        val greater: Int
        if (isInForeground() != other.isInForeground()) {
            return if (isInForeground()) -1 else 1
        }
        greater = if (commandId == other.commandId) {
            return 0
        } else if (command.getPriority() == other.command.getPriority()) {
            if (commandId > other.commandId) 1 else -1
        } else {
            if (command.getPriority() > other.command.getPriority()) 1 else -1
        }
        return greater
    }

    fun getTimelineType(): TimelineType {
        return commandTimeline.timelineType
    }

    fun isManuallyLaunched(): Boolean {
        return mManuallyLaunched
    }

    fun setManuallyLaunched(manuallyLaunched: Boolean): CommandData {
        mManuallyLaunched = manuallyLaunched
        return this
    }

    fun share(myContext: MyContext): String {
        return toUserFriendlyForm(myContext, false)
    }

    fun toCommandSummary(myContext: MyContext): String {
        return toUserFriendlyForm(myContext, true)
    }

    private fun toUserFriendlyForm(myContext: MyContext, summaryOnly: Boolean): String {
        val builder: MyStringBuilder = MyStringBuilder.of(
            if (command == CommandEnum.GET_TIMELINE || command == CommandEnum.GET_OLDER_TIMELINE) "" else toShortCommandName(
                myContext
            )
        )
        if (!summaryOnly) {
            if (mInForeground) builder.withSpace(", foreground")
            if (mManuallyLaunched) builder.withSpace(", manual")
        }
        when (command) {
            CommandEnum.GET_AVATAR -> {
                builder.withSpace(ListScope.USER.timelinePreposition(myContext))
                builder.withSpace(timeline.actor.webFingerId)
                if (myContext.accounts.getDistinctOriginsCount() > 1) {
                    builder.withSpace(ListScope.ORIGIN.timelinePreposition(myContext))
                    builder.withSpace(commandTimeline.origin.name)
                }
            }
            CommandEnum.GET_ATTACHMENT,
            CommandEnum.UPDATE_NOTE,
            CommandEnum.UPDATE_MEDIA -> builder.withSpaceQuoted(trimConditionally(description, summaryOnly))
            CommandEnum.GET_TIMELINE -> builder.append(TimelineTitle.from(myContext, timeline).toString())
            CommandEnum.GET_OLDER_TIMELINE -> {
                builder.append(WhichPage.OLDER.getTitle(myContext.context))
                builder.withSpace(TimelineTitle.from(myContext, timeline).toString())
            }
            CommandEnum.FOLLOW, CommandEnum.UNDO_FOLLOW, CommandEnum.GET_FOLLOWERS, CommandEnum.GET_FRIENDS -> builder.withSpace(
                timeline.actor.actorNameInTimeline
            )
            CommandEnum.GET_ACTOR, CommandEnum.SEARCH_ACTORS -> if (username.isNotEmpty()) builder.withSpaceQuoted(
                username
            )
            else -> appendScopeName(myContext, builder)
        }
        if (!summaryOnly) {
            builder.atNewLine(createdDateWithLabel(myContext.context))
            builder.atNewLine(result.toSummary())
        }
        return builder.toString()
    }

    private fun appendScopeName(myContext: MyContext, builder: MyStringBuilder) {
        if (timeline.myAccountToSync.isValid) {
            builder.withSpace(getTimelineType().scope.timelinePreposition(myContext))
            if (getTimelineType().isAtOrigin()) {
                builder.withSpace(commandTimeline.origin.name)
            } else {
                builder.withSpace(timeline.myAccountToSync.getAccountName())
            }
        }
    }

    fun createdDateWithLabel(context: Context): String {
        return context.getText(R.string.created_label).toString() + " " + RelativeTime.getDifference(
            context,
            createdDate
        )
    }

    fun toCommandProgress(myContext: MyContext): String {
        return toShortCommandName(myContext) + "; " + result.progress
    }

    private fun toShortCommandName(myContext: MyContext): String {
        val builder = StringBuilder()
        when (command) {
            CommandEnum.GET_TIMELINE -> builder.append(getTimelineType().title(myContext.context))
            CommandEnum.GET_OLDER_TIMELINE -> {
                builder.append(WhichPage.OLDER.getTitle(myContext.context))
                builder.append(" ")
                builder.append(getTimelineType().title(myContext.context))
            }
            else -> builder.append(command.getTitle(myContext, timeline.myAccountToSync.getAccountName()))
        }
        return builder.toString()
    }

    /** @return true if the command was deleted
     */
    fun deleteFromQueue(queue: OneQueue): Boolean {
        val deleted = AtomicBoolean(false)
        val method = "deleteFromQueue " + queue.queueType
        queue.forEachEx { cd ->
            if (cd.commandId == commandId) {
                if (remove(cd)) {
                    deleted.set(true)
                }
                result.incrementDownloadedCount()
                MyLog.v(this) { "$method deleted: $cd" }
            }
        }
        MyLog.v(this) {
            (method + " id=" + commandId + (if (deleted.get()) " deleted" else " not found")
                + ", processed queue: " + queue.size)
        }
        return deleted.get()
    }

    fun isInForeground(): Boolean {
        return mInForeground
    }

    fun setInForeground(inForeground: Boolean): CommandData {
        mInForeground = inForeground
        return this
    }

    val isTimeToRetry: Boolean
        get() = result.delayedTill?.let { it < System.currentTimeMillis() }
            ?: executedMoreSecondsAgoThan(QueueAccessor.MIN_RETRY_PERIOD_SECONDS)

    fun executedMoreSecondsAgoThan(predefinedPeriodSeconds: Long): Boolean {
        return RelativeTime.moreSecondsAgoThan(
            result.lastExecutedDate,
            predefinedPeriodSeconds
        )
    }

    fun resetRetries() {
        result.resetRetries(command)
    }

    val timeline: Timeline get() = commandTimeline.timeline.get()

    override val classTag: String get() = TAG

    companion object {
        private val TAG: String = CommandData::class.simpleName!!
        val EMPTY = newCommand(CommandEnum.EMPTY)

        fun newSearch(
            searchObjects: SearchObjects?,
            myContext: MyContext, origin: Origin, queryString: String?
        ): CommandData {
            return if (searchObjects == SearchObjects.NOTES) {
                val timeline = myContext.timelines.get(TimelineType.SEARCH, Actor.EMPTY, origin, queryString)
                CommandData(
                    0, CommandEnum.GET_TIMELINE, timeline.myAccountToSync,
                    CommandTimeline.of(timeline), 0
                )
            } else {
                newActorCommand(CommandEnum.SEARCH_ACTORS, Actor.EMPTY, queryString)
            }
        }

        fun newUpdateStatus(myAccount: MyAccount, unsentActivityId: Long, noteId: Long): CommandData {
            val hasAttachmentsToUpload: Boolean = Attachments.newLoaded(myAccount.myContext, noteId).toUploadCount > 0
            val commandData = newAccountCommand(
                if (hasAttachmentsToUpload) CommandEnum.UPDATE_MEDIA else CommandEnum.UPDATE_NOTE,
                myAccount
            )
            commandData.itemId = unsentActivityId
            commandData.setTrimmedNoteContentAsDescription(noteId)
            return commandData
        }

        fun newFetchAttachment(noteId: Long, downloadDataRowId: Long): CommandData {
            val commandData = newOriginCommand(CommandEnum.GET_ATTACHMENT, Origin.EMPTY)
            commandData.itemId = downloadDataRowId
            commandData.setTrimmedNoteContentAsDescription(noteId)
            return commandData
        }

        fun newActorCommand(command: CommandEnum, actor: Actor, username: String?): CommandData {
            return newActorCommandAtOrigin(command, actor, username, Origin.EMPTY)
        }

        fun newActorCommandAtOrigin(
            command: CommandEnum,
            actor: Actor,
            username: String?,
            origin: Origin
        ): CommandData {
            val commandData = newTimelineCommand(
                command,
                myContextHolder.getNow().timelines.get(
                    if (origin.isEmpty) TimelineType.SENT else TimelineType.SENT_AT_ORIGIN, actor, origin
                )
            )
            commandData.username = username ?: ""
            commandData.description = commandData.username
            return commandData
        }

        fun newCommand(command: CommandEnum): CommandData {
            return newOriginCommand(command, Origin.EMPTY)
        }

        fun newItemCommand(command: CommandEnum, myAccount: MyAccount, itemId: Long): CommandData {
            val commandData = newAccountCommand(command, myAccount)
            commandData.itemId = itemId
            return commandData
        }

        fun actOnActorCommand(
            command: CommandEnum,
            myAccount: MyAccount,
            actor: Actor,
            username: String?
        ): CommandData {
            if (myAccount.nonValid || actor.isEmpty && username.isNullOrEmpty()) return EMPTY
            val timeline: Timeline = myContextHolder.getNow().timelines.get(TimelineType.SENT, actor, Origin.EMPTY)
            val commandData = if (actor.isEmpty) newAccountCommand(command, myAccount)
            else CommandData(0, command, myAccount, CommandTimeline.of(timeline), 0)
            commandData.username = username ?: ""
            commandData.description = commandData.username
            return commandData
        }

        fun newAccountCommand(command: CommandEnum, myAccount: MyAccount): CommandData {
            return CommandData(0, command, myAccount, CommandTimeline.of(Timeline.EMPTY), 0)
        }

        fun newOriginCommand(command: CommandEnum, origin: Origin): CommandData {
            return newTimelineCommand(
                command, if (origin.isEmpty) Timeline.EMPTY
                else myContextHolder.getNow().timelines.get(TimelineType.EVERYTHING, Actor.EMPTY, origin)
            )
        }

        fun newTimelineCommand(
            command: CommandEnum, myAccount: MyAccount,
            timelineType: TimelineType
        ): CommandData {
            return newTimelineCommand(
                command, myContextHolder.getNow().timelines
                    .get(timelineType, myAccount.actor, Origin.EMPTY)
            )
        }

        fun newTimelineCommand(command: CommandEnum, timeline: Timeline): CommandData {
            return CommandData(0, command, timeline.myAccountToSync, CommandTimeline.of(timeline), 0)
        }

        /**
         * Used to decode command from the Intent upon receiving it
         */
        fun fromIntent(myContext: MyContext, intent: Intent?): CommandData {
            return intent?.extras?.let { fromBundle(myContext, it) } ?: EMPTY
        }

        private fun fromBundle(myContext: MyContext, bundle: Bundle): CommandData =
            when (val command: CommandEnum = CommandEnum.fromBundle(bundle)) {
                CommandEnum.UNKNOWN, CommandEnum.EMPTY -> EMPTY
                else -> CommandData(
                    bundle.getLong(IntentExtra.COMMAND_ID.key, 0),
                    command,
                    MyAccount.fromBundle(myContext, bundle),
                    CommandTimeline.of(Timeline.fromBundle(myContext, bundle)),
                    bundle.getLong(IntentExtra.CREATED_DATE.key)
                ).apply {
                    itemId = bundle.getLong(IntentExtra.ITEM_ID.key)
                    username = BundleUtils.getString(bundle, IntentExtra.USERNAME)
                    description = BundleUtils.getString(bundle, IntentExtra.COMMAND_DESCRIPTION)
                    mInForeground = bundle.getBoolean(IntentExtra.IN_FOREGROUND.key)
                    mManuallyLaunched = bundle.getBoolean(IntentExtra.MANUALLY_LAUNCHED.key)
                    result = bundle.getParcelable(IntentExtra.COMMAND_RESULT.key) ?: CommandResult()
                }
            }

        fun fromCursor(myContext: MyContext, cursor: Cursor): CommandData {
            val command: CommandEnum = CommandEnum.load(DbUtils.getString(cursor, CommandTable.COMMAND_CODE))
            if (CommandEnum.UNKNOWN == command) return EMPTY
            val commandData = CommandData(
                DbUtils.getLong(cursor, BaseColumns._ID),
                command,
                myContext.accounts.fromActorId(DbUtils.getLong(cursor, CommandTable.ACCOUNT_ID)),
                CommandTimeline.fromCursor(myContext, cursor),
                DbUtils.getLong(cursor, CommandTable.CREATED_DATE)
            )
            commandData.description = DbUtils.getString(cursor, CommandTable.DESCRIPTION)
            commandData.mInForeground = DbUtils.getBoolean(cursor, CommandTable.IN_FOREGROUND)
            commandData.mManuallyLaunched = DbUtils.getBoolean(cursor, CommandTable.MANUALLY_LAUNCHED)
            commandData.itemId = DbUtils.getLong(cursor, CommandTable.ITEM_ID)
            commandData.username = DbUtils.getString(cursor, CommandTable.USERNAME)
            commandData.result = CommandResult.fromCursor(cursor)
            return commandData
        }

        private fun trimConditionally(text: String?, trim: Boolean): CharSequence {
            return if (text.isNullOrEmpty()) {
                ""
            } else if (trim) {
                I18n.trimTextAt(MyHtml.htmlToCompactPlainText(text), 40)
            } else {
                text
            }
        }
    }
}
