/*
 * Copyright (c) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.timeline.meta

import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.BaseColumns
import org.andstatus.app.IntentExtra
import org.andstatus.app.account.AccessStatus
import org.andstatus.app.account.MyAccount
import org.andstatus.app.actor.ActorsScreenType
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyContextEmpty
import org.andstatus.app.data.ContentValuesUtils
import org.andstatus.app.data.DbUtils
import org.andstatus.app.data.MatchedUri
import org.andstatus.app.data.MyQuery
import org.andstatus.app.data.ParsedUri
import org.andstatus.app.data.SqlWhere
import org.andstatus.app.database.table.TimelineTable
import org.andstatus.app.net.social.Actor
import org.andstatus.app.origin.Origin
import org.andstatus.app.os.AsyncUtil
import org.andstatus.app.service.CommandResult
import org.andstatus.app.timeline.ListScope
import org.andstatus.app.util.BundleUtils
import org.andstatus.app.util.CollectionsUtil
import org.andstatus.app.util.IsEmpty
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.RelativeTime
import org.andstatus.app.util.StringUtil
import org.andstatus.app.util.TriState
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * @author yvolk@yurivolkov.com
 */
class Timeline : Comparable<Timeline?>, IsEmpty {
    val myContext: MyContext

    @Volatile
    var id: Long = 0
        private set
    val timelineType: TimelineType

    /** "Authenticated User" used to retrieve/post to... this Timeline  */
    val myAccountToSync: MyAccount

    /** An Actor as a parameter of this timeline.
     * This may be the same as the Authenticated User ([.myAccountToSync])
     * or some other User e.g. to get a list of messages by some other person/user of the Social Network
     */
    val actor: Actor

    /** The Social Network of this timeline. Some timelines don't depend on
     * an Authenticated User ([.myAccountToSync]), e.g. [TimelineType.PUBLIC] - this
     * timeline may be fetched by any authenticated user of this Social Network  */
    val origin: Origin

    /** This may be used e.g. to search [TimelineType.PUBLIC] timeline  */
    val searchQuery: String

    /** The timeline combines messages from all accounts
     * or from Social Networks, e.g. Search in all Social Networks  */
    val isCombined: Boolean

    /** If this timeline can be synced  */
    val isSyncable: Boolean

    /** If this timeline can be synced automatically  */
    val isSyncableAutomatically: Boolean

    /** Is it possible to sync this timeline via usage of one or more (child, not combined...)
     * timelines for individual accounts  */
    val isSyncableForAccounts: Boolean

    /** Is it possible to sync this timeline via usage of one or more (child, not combined...)
     * timelines for individual Social networks  */
    val isSyncableForOrigins: Boolean

    /** If the timeline is synced automatically  */
    @Volatile
    var isSyncedAutomatically = false
        set(value) {
            if (field != value && isSyncableAutomatically) {
                field = value
                setChanged()
            }
        }

    /** If the timeline should be shown in a Timeline selector  */
    @Volatile
    var isDisplayedInSelector: DisplayedInSelector = DisplayedInSelector.NEVER
        set(value) {
            if (field != value) {
                field = value
                setChanged()
            }
        }

    /** Used for sorting timelines in a selector  */
    @Volatile
    var selectorOrder: Long = 0
        set(value) {
            if (field != value) {
                field = value
                setChanged()
            }
        }

    /** When this timeline was last time successfully synced  */
    private val syncSucceededDate: AtomicLong = AtomicLong()

    /** When last sync error occurred  */
    private val syncFailedDate: AtomicLong = AtomicLong()

    /** Error message at [.syncFailedDate]  */
    @Volatile
    private var errorMessage: String? = ""

    /** Number of successful sync operations: "Synced [.syncedTimesCount] times"  */
    private val syncedTimesCount: AtomicLong = AtomicLong()

    /** Number of failed sync operations  */
    private val syncFailedTimesCount: AtomicLong = AtomicLong()
    private val downloadedItemsCount: AtomicLong = AtomicLong()
    private val newItemsCount: AtomicLong = AtomicLong()
    private val countSince: AtomicLong = AtomicLong(System.currentTimeMillis())

    /** Accumulated numbers for statistics. They are reset by a user's request  */
    private val syncedTimesCountTotal: AtomicLong = AtomicLong()
    private val syncFailedTimesCountTotal: AtomicLong = AtomicLong()
    private val downloadedItemsCountTotal: AtomicLong = AtomicLong()
    private val newItemsCountTotal: AtomicLong = AtomicLong()

    /** Timeline position of the youngest ever downloaded message  */
    @Volatile
    private var youngestPosition = ""

    /** Date of the item corresponding to the [.youngestPosition]  */
    @Volatile
    private var youngestItemDate: Long = 0

    /** Last date when youngest items of this timeline were successfully synced
     * (even if there were no new item at that time).
     * It may be used to calculate when it will be time for the next automatic update
     */
    @Volatile
    private var youngestSyncedDate: Long = 0

    /** Timeline position of the oldest ever downloaded message  */
    @Volatile
    private var oldestPosition = ""

    /** Date of the item corresponding to the [.oldestPosition]  */
    @Volatile
    private var oldestItemDate: Long = 0

    /** Last date when oldest items of this timeline were successfully synced
     * (even if there were no new item at that time).
     * It may be used to calculate when it will be time for the next automatic update
     */
    @Volatile
    private var oldestSyncedDate: Long = 0

    /** Position of the timeline, which a User viewed   */
    @Volatile
    private var visibleItemId: Long = 0

    @Volatile
    private var visibleY = 0

    @Volatile
    private var visibleOldestDate: Long = 0

    @Volatile
    private var changed = true

    @Volatile
    private var lastChangedDate: Long = 0

    private constructor() {
        myContext = MyContextEmpty.EMPTY
        timelineType = TimelineType.UNKNOWN
        myAccountToSync = MyAccount.EMPTY
        actor = Actor.EMPTY
        origin = Origin.EMPTY
        searchQuery = ""
        isCombined = calcIsCombined(timelineType, origin)
        isSyncable = false
        isSyncableAutomatically = false
        isSyncableForAccounts = false
        isSyncableForOrigins = false
    }

    internal constructor(
        myContext: MyContext, id: Long, timelineType: TimelineType,
        actor: Actor, origin: Origin, searchQuery: String?, selectorOrder: Long
    ) {
        Objects.requireNonNull(timelineType)
        Objects.requireNonNull(actor)
        Objects.requireNonNull(origin)
        this.myContext = myContext
        this.id = id
        this.actor = fixedActor(timelineType, actor)
        this.origin = fixedOrigin(timelineType, origin)
        myAccountToSync = calcAccountToSync(myContext, timelineType, this.origin, this.actor)
        this.searchQuery = StringUtil.optNotEmpty(searchQuery).orElse("")
        isCombined = calcIsCombined(timelineType, this.origin)
        this.timelineType = fixedTimelineType(timelineType)
        isSyncable = calcIsSyncable(myAccountToSync)
        isSyncableAutomatically = isSyncable && myAccountToSync.isSyncedAutomatically
        isSyncableForAccounts = calcIsSyncableForAccounts(myContext)
        isSyncableForOrigins = calcIsSyncableForOrigins(myContext)
        this.selectorOrder = selectorOrder
    }

    fun getDefaultSelectorOrder(): Long {
        return (timelineType.ordinal + 1L) * 2 + if (isCombined) 1 else 0
    }

    private fun calcIsSyncable(myAccountToSync: MyAccount): Boolean = !isCombined &&
        timelineType.isSyncable &&
        myAccountToSync.isValid &&
        myAccountToSync.accessStatus != AccessStatus.NEVER &&
        myAccountToSync.origin.originType.isTimelineTypeSyncable(timelineType) &&
        myAccountToSync.connection.hasApiEndpoint(timelineType.connectionApiRoutine)

    private fun calcIsSyncableForAccounts(myContext: MyContext): Boolean = isCombined &&
        timelineType.isSyncable && timelineType.canBeCombinedForMyAccounts() &&
        myContext.accounts.getFirstSucceeded().isValidAndSucceeded()

    private fun calcIsSyncableForOrigins(myContext: MyContext): Boolean = isCombined &&
        timelineType.isSyncable && timelineType.canBeCombinedForOrigins() &&
        myContext.accounts.getFirstSucceeded().isValidAndSucceeded()

    private fun calcIsCombined(timelineType: TimelineType, origin: Origin): Boolean =
        if (timelineType.isAtOrigin) origin.isEmpty else actor.isEmpty

    private fun calcAccountToSync(
        myContext: MyContext,
        timelineType: TimelineType,
        origin: Origin,
        actor: Actor
    ): MyAccount = if (timelineType.isAtOrigin && origin.nonEmpty)
        myContext.accounts.getFirstPreferablySucceededForOrigin(origin)
    else myContext.accounts.toSyncThatActor(actor)

    private fun fixedActor(timelineType: TimelineType, actor: Actor): Actor =
        if (timelineType.isForUser()) actor else Actor.EMPTY

    private fun fixedOrigin(timelineType: TimelineType, origin: Origin): Origin =
        if (timelineType.isAtOrigin) origin else actor.origin

    private fun fixedTimelineType(timelineType: TimelineType): TimelineType =
        if (isCombined || (if (timelineType.isAtOrigin) origin.isValid
            else actor.nonEmpty)
        ) timelineTypeFixEverything(timelineType) else TimelineType.UNKNOWN

    private fun timelineTypeFixEverything(timelineType: TimelineType): TimelineType = when (timelineType) {
        TimelineType.EVERYTHING,
        TimelineType.SEARCH -> if (hasSearchQuery) TimelineType.SEARCH else TimelineType.EVERYTHING
        else -> timelineType
    }

    override operator fun compareTo(other: Timeline?): Int {
        if (other == null) return 1

        val result = CollectionsUtil.compareCheckbox(checkBoxDisplayedInSelector, other.checkBoxDisplayedInSelector)
        return if (result != 0) {
            result
        } else selectorOrder.compareTo(other.selectorOrder)
    }

    fun toContentValues(values: ContentValues) {
        ContentValuesUtils.putNotZero(values, BaseColumns._ID, id)
        values.put(TimelineTable.TIMELINE_TYPE, timelineType.save())
        values.put(TimelineTable.ACTOR_ID, actor.actorId)
        values.put(TimelineTable.ORIGIN_ID, origin.id)
        values.put(TimelineTable.SEARCH_QUERY, searchQuery)
        values.put(TimelineTable.IS_SYNCED_AUTOMATICALLY, isSyncedAutomatically)
        values.put(TimelineTable.DISPLAYED_IN_SELECTOR, isDisplayedInSelector.save())
        values.put(TimelineTable.SELECTOR_ORDER, selectorOrder)
        values.put(TimelineTable.SYNC_SUCCEEDED_DATE, syncSucceededDate.get())
        values.put(TimelineTable.SYNC_FAILED_DATE, syncFailedDate.get())
        values.put(TimelineTable.ERROR_MESSAGE, errorMessage)
        values.put(TimelineTable.SYNCED_TIMES_COUNT, syncedTimesCount.get())
        values.put(TimelineTable.SYNC_FAILED_TIMES_COUNT, syncFailedTimesCount.get())
        values.put(TimelineTable.DOWNLOADED_ITEMS_COUNT, downloadedItemsCount.get())
        values.put(TimelineTable.NEW_ITEMS_COUNT, newItemsCount.get())
        values.put(TimelineTable.COUNT_SINCE, countSince.get())
        values.put(TimelineTable.SYNCED_TIMES_COUNT_TOTAL, syncedTimesCountTotal.get())
        values.put(TimelineTable.SYNC_FAILED_TIMES_COUNT_TOTAL, syncFailedTimesCountTotal.get())
        values.put(TimelineTable.DOWNLOADED_ITEMS_COUNT_TOTAL, downloadedItemsCountTotal.get())
        values.put(TimelineTable.NEW_ITEMS_COUNT_TOTAL, newItemsCountTotal.get())
        values.put(TimelineTable.YOUNGEST_POSITION, youngestPosition)
        values.put(TimelineTable.YOUNGEST_ITEM_DATE, youngestItemDate)
        values.put(TimelineTable.YOUNGEST_SYNCED_DATE, youngestSyncedDate)
        values.put(TimelineTable.OLDEST_POSITION, oldestPosition)
        values.put(TimelineTable.OLDEST_ITEM_DATE, oldestItemDate)
        values.put(TimelineTable.OLDEST_SYNCED_DATE, oldestSyncedDate)
        values.put(TimelineTable.VISIBLE_ITEM_ID, visibleItemId)
        values.put(TimelineTable.VISIBLE_Y, visibleY)
        values.put(TimelineTable.VISIBLE_OLDEST_DATE, visibleOldestDate)
        if (lastChangedDate > 0) values.put(TimelineTable.LAST_CHANGED_DATE, lastChangedDate)
    }

    fun fromSearch(myContext: MyContext, globalSearch: Boolean): Timeline =
        if (globalSearch) myContext.timelines.get(TimelineType.SEARCH, actor, origin, searchQuery) else this

    fun fromIsCombined(myContext: MyContext, isCombinedNew: Boolean): Timeline =
        if (isCombined == isCombinedNew || !isCombined && timelineType.isForUser() && !timelineType.isAtOrigin
            && actor.user.isMyUser != TriState.TRUE
        ) this
        else myContext.timelines.get(
            timelineType,
            if (isCombinedNew) Actor.EMPTY else myContext.accounts.currentAccount.actor,
            if (isCombinedNew) Origin.EMPTY else myContext.accounts.currentAccount.origin,
            searchQuery
        )

    fun fromMyAccount(myContext: MyContext, myAccountNew: MyAccount): Timeline =
        if (isCombined || myAccountToSync == myAccountNew || timelineType.isForUser() &&
            !timelineType.isAtOrigin && actor.user.isMyUser != TriState.TRUE
        ) this
        else myContext.timelines.get(
            timelineType,
            myAccountNew.actor,
            myAccountNew.origin, searchQuery
        )

    override val isEmpty: Boolean get() = timelineType == TimelineType.UNKNOWN

    val isValid: Boolean get() = timelineType != TimelineType.UNKNOWN

    val homeOrigin: Origin get() = if (timelineType.isAtOrigin) origin else actor.toHomeOrigin().origin

    val checkBoxDisplayedInSelector: Boolean get() = isDisplayedInSelector != DisplayedInSelector.NEVER

    fun save(myContext: MyContext): Timeline {
        if (AsyncUtil.isUiThread) return this
        if (timelineType.isPersistable() && (id == 0L || changed) && myContext.isReady) {
            val isNew = id == 0L
            if (isNew) {
                val duplicatedId = findDuplicateInDatabase(myContext)
                if (duplicatedId != 0L) {
                    MyLog.i(this, "Found duplicating timeline, id=$duplicatedId for $this")
                    return myContext.timelines.fromId(duplicatedId)
                }
                if (isAddedByDefault()) {
                    isDisplayedInSelector = DisplayedInSelector.IN_CONTEXT
                    isSyncedAutomatically = timelineType.isSyncedAutomaticallyByDefault
                }
            }
            if (selectorOrder == 0L) {
                selectorOrder = getDefaultSelectorOrder()
            }
            saveInternal(myContext)
            if (isNew && id != 0L) {
                return myContext.timelines.fromId(id)
            }
        }
        return this
    }

    private fun findDuplicateInDatabase(myContext: MyContext): Long {
        val where = SqlWhere()
        where.append(TimelineTable.TIMELINE_TYPE + "='" + timelineType.save() + "'")
        where.append(TimelineTable.ORIGIN_ID + "=" + origin.id)
        where.append(TimelineTable.ACTOR_ID + "=" + actor.actorId)
        where.append(TimelineTable.SEARCH_QUERY + "='" + searchQuery + "'")
        return MyQuery.conditionToLongColumnValue(
            myContext.database,
            "findDuplicateInDatabase",
            TimelineTable.TABLE_NAME,
            BaseColumns._ID,
            where.getCondition()
        )
    }

    private fun saveInternal(myContext: MyContext): Long {
        val contentValues = ContentValues()
        toContentValues(contentValues)
        if (myContext.isTestRun) {
            MyLog.v(this) { "Saving $this" }
        }
        if (id == 0L) {
            DbUtils.addRowWithRetry(myContext, TimelineTable.TABLE_NAME, contentValues, 3)
                .onSuccess { idAdded: Long ->
                    id = idAdded
                    changed = false
                }
        } else {
            DbUtils.updateRowWithRetry(myContext, TimelineTable.TABLE_NAME, id, contentValues, 3)
                .onSuccess { changed = false }
        }
        return id
    }

    fun delete(myContext: MyContext) {
        if (isRequired() && myContext.timelines.stream().noneMatch { that: Timeline -> duplicates(that) }) {
            MyLog.d(this, "Cannot delete required timeline: $this")
            return
        }
        val db = myContext.database
        if (db == null) {
            MyLog.d(this, "delete; Database is unavailable")
        } else {
            val sql = "DELETE FROM " + TimelineTable.TABLE_NAME + " WHERE _ID=" + id
            db.execSQL(sql)
            MyLog.v(this) { "Timeline deleted: $this" }
        }
    }

    fun isAddedByDefault(): Boolean {
        if (isRequired()) return true
        if (isCombined || !isValid || hasSearchQuery) return false
        return if (timelineType.isAtOrigin) {
            (TimelineType.getDefaultOriginTimelineTypes().contains(timelineType)
                && (origin.originType.isTimelineTypeSyncable(timelineType)
                || timelineType == TimelineType.EVERYTHING))
        } else {
            actor.user.isMyUser.isTrue && myAccountToSync.defaultTimelineTypes.contains(timelineType)
        }
    }

    /** Required timeline cannot be deleted  */
    fun isRequired(): Boolean {
        return isCombined && timelineType.isCombinedRequired && !hasSearchQuery
    }

    override fun toString(): String {
        val builder = MyStringBuilder()
        if (timelineType.isAtOrigin) {
            builder.withComma(if (origin.isValid) origin.name else "(all origins)")
        }
        if (timelineType.isForUser()) {
            if (actor.isEmpty) {
                builder.withComma("(all accounts)")
            } else if (actor.user.isMyUser.isTrue && myAccountToSync.isValid) {
                builder.withComma("account", myAccountToSync.accountName)
                if (myAccountToSync.origin != origin && origin.isValid) {
                    builder.withComma("origin", origin.name)
                }
            } else {
                builder.withComma(actor.user.toString())
            }
        }
        if (timelineType != TimelineType.UNKNOWN) {
            builder.withComma("type", timelineType.save())
        }
        if (actor.nonEmpty) {
            builder.withComma("actor$actor")
        }
        if (hasSearchQuery) {
            builder.withCommaQuoted("search", searchQuery, true)
        }
        if (id != 0L) {
            builder.withComma("id", id)
        }
        if (!isSyncable) {
            builder.withComma("not syncable")
        }
        return builder.toKeyValue("Timeline")
    }

    fun positionsToString(): String {
        val builder = StringBuilder()
        builder.append("TimelinePositions{")
        if (youngestSyncedDate > RelativeTime.SOME_TIME_AGO) {
            builder.append("synced at " + Date(youngestSyncedDate).toString())
            builder.append(", pos:" + getYoungestPosition())
        }
        if (oldestSyncedDate > RelativeTime.SOME_TIME_AGO) {
            builder.append("older synced at " + Date(oldestSyncedDate).toString())
            builder.append(", pos:" + getOldestPosition())
        }
        builder.append('}')
        return builder.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || other !is Timeline) return false
        if (timelineType != other.timelineType) return false
        if (id != 0L || other.id != 0L) {
            return id == other.id
        }
        if (origin != other.origin) return false
        return if (actor != other.actor) false else StringUtil.equalsNotEmpty(searchQuery, other.searchQuery)
    }

    fun duplicates(that: Timeline): Boolean {
        if (equals(that)) return false
        if (id > 0 && id < that.id) return false
        if (timelineType != that.timelineType) return false
        if (origin != that.origin) return false
        return if (!actor.isSame(that.actor)) false else StringUtil.equalsNotEmpty(searchQuery, that.searchQuery)
    }

    override fun hashCode(): Int {
        var result = timelineType.hashCode()
        if (id != 0L) result = 31 * result + java.lang.Long.hashCode(id)
        result = 31 * result + origin.hashCode()
        result = 31 * result + actor.hashCode()
        if (searchQuery.isNotEmpty()) result = 31 * result + searchQuery.hashCode()
        return result
    }

    val actorId: Long get() = actor.actorId

    val hasSearchQuery: Boolean get() = searchQuery.isNotEmpty()

    fun toBundle(bundle: Bundle) {
        BundleUtils.putNotZero(bundle, IntentExtra.TIMELINE_ID, id)
        if (timelineType != TimelineType.UNKNOWN) {
            bundle.putString(IntentExtra.TIMELINE_TYPE.key, timelineType.save())
        }
        BundleUtils.putNotZero(bundle, IntentExtra.ORIGIN_ID, origin.id)
        BundleUtils.putNotZero(bundle, IntentExtra.ACTOR_ID, actor.actorId)
        BundleUtils.putNotEmpty(bundle, IntentExtra.SEARCH_QUERY, searchQuery)
    }

    /**
     * @return true if it's time to auto update this timeline
     */
    val isTimeToAutoSync: Boolean
        get() {
            if (System.currentTimeMillis() - getLastSyncedDate() < MIN_RETRY_PERIOD_MS) {
                return false
            }
            val syncFrequencyMs = myAccountToSync.effectiveSyncFrequencyMillis
            // This correction needs to take into account
            // that we stored time when sync ended, and not when Android initiated the sync.
            val correctionForExecutionTime = syncFrequencyMs / 10
            val passedMs = System.currentTimeMillis() - getLastSyncedDate()
            val blnOut = passedMs > syncFrequencyMs - correctionForExecutionTime
            MyLog.v(this) {
                "It's time to auto update " + this +
                    ". " +
                    TimeUnit.MILLISECONDS.toMinutes(passedMs) +
                    " minutes passed."
            }
            return blnOut
        }

    fun forgetPositionsAndDates() {
        if (youngestPosition.isNotEmpty()) {
            youngestPosition = ""
            setChanged()
        }
        if (youngestItemDate > 0) {
            youngestItemDate = 0
            setChanged()
        }
        if (youngestSyncedDate > 0) {
            youngestSyncedDate = 0
            setChanged()
        }
        if (oldestPosition.isNotEmpty()) {
            oldestPosition = ""
            setChanged()
        }
        if (oldestItemDate > 0) {
            oldestItemDate = 0
            setChanged()
        }
        if (oldestSyncedDate > 0) {
            oldestSyncedDate = 0
            setChanged()
        }
        setSyncSucceededDate(0)
        if (syncFailedDate.get() > 0) {
            syncFailedDate.set(0)
            setChanged()
        }
    }

    fun onNewMsg(newDate: Long, youngerPosition: String?, olderPosition: String?) {
        if (newDate <= RelativeTime.SOME_TIME_AGO) return
        if (!youngerPosition.isNullOrEmpty() && (youngestItemDate < newDate ||
                youngestItemDate == newDate && StringUtil.isNewFilledValue(youngestPosition, youngerPosition))
        ) {
            youngestItemDate = newDate
            youngestPosition = youngerPosition
            setChanged()
        }
        if (!olderPosition.isNullOrEmpty() && (oldestItemDate <= RelativeTime.SOME_TIME_AGO || oldestItemDate > newDate ||
                oldestItemDate == newDate && StringUtil.isNewFilledValue(oldestPosition, olderPosition))
        ) {
            oldestItemDate = newDate
            oldestPosition = olderPosition
            setChanged()
        }
    }

    fun getYoungestPosition(): String {
        return youngestPosition
    }

    fun getYoungestItemDate(): Long {
        return youngestItemDate
    }

    fun getYoungestSyncedDate(): Long {
        return youngestSyncedDate
    }

    fun setYoungestSyncedDate(newDate: Long) {
        if (newDate > RelativeTime.SOME_TIME_AGO && youngestSyncedDate < newDate) {
            youngestSyncedDate = newDate
            setChanged()
        }
    }

    fun getOldestItemDate(): Long {
        return oldestItemDate
    }

    fun getOldestPosition(): String {
        return oldestPosition
    }

    fun getOldestSyncedDate(): Long {
        return oldestSyncedDate
    }

    fun setOldestSyncedDate(newDate: Long) {
        if (newDate > RelativeTime.SOME_TIME_AGO && (oldestSyncedDate <= RelativeTime.SOME_TIME_AGO || oldestSyncedDate > newDate)) {
            oldestSyncedDate = newDate
            setChanged()
        }
    }

    fun getVisibleItemId(): Long {
        return visibleItemId
    }

    fun setVisibleItemId(visibleItemId: Long) {
        if (this.visibleItemId != visibleItemId) {
            setChanged()
            this.visibleItemId = visibleItemId
        }
    }

    fun getVisibleY(): Int {
        return visibleY
    }

    fun setVisibleY(visibleY: Int) {
        if (this.visibleY != visibleY) {
            setChanged()
            this.visibleY = visibleY
        }
    }

    fun getVisibleOldestDate(): Long {
        return visibleOldestDate
    }

    fun setVisibleOldestDate(visibleOldestDate: Long) {
        if (this.visibleOldestDate != visibleOldestDate) {
            setChanged()
            this.visibleOldestDate = visibleOldestDate
        }
    }

    fun cloneForAccount(myContext: MyContext, ma: MyAccount): Timeline {
        return myContext.timelines.get(0, timelineType, ma.actor, Origin.EMPTY, searchQuery)
    }

    fun cloneForOrigin(myContext: MyContext, origin: Origin): Timeline {
        return myContext.timelines.get(0, timelineType, Actor.EMPTY, origin, searchQuery)
    }

    fun onSyncEnded(myContext: MyContext, result: CommandResult) {
        onSyncEnded(result).save(myContext)
        myContext.timelines.stream()
            .filter { obj: Timeline -> obj.isSyncable }
            .filter { timeline: Timeline -> isSyncedSimultaneously(timeline) }
            .forEach { timeline: Timeline -> timeline.onSyncedSimultaneously(this).save(myContext) }
    }

    private fun onSyncedSimultaneously(other: Timeline): Timeline {
        if (setIfLess(syncFailedDate, other.syncFailedDate)) {
            errorMessage = other.errorMessage
        }
        setIfLess(syncFailedTimesCount, other.syncFailedTimesCount)
        setIfLess(syncFailedTimesCountTotal, other.syncFailedTimesCountTotal)
        setIfLess(syncSucceededDate, other.syncSucceededDate)
        setIfLess(syncedTimesCount, other.syncedTimesCount)
        setIfLess(syncedTimesCountTotal, other.syncedTimesCountTotal)
        setIfLess(newItemsCount, other.newItemsCount)
        setIfLess(newItemsCountTotal, other.newItemsCountTotal)
        setIfLess(downloadedItemsCount, other.downloadedItemsCount)
        setIfLess(downloadedItemsCountTotal, other.downloadedItemsCountTotal)
        onNewMsg(other.youngestItemDate, other.youngestPosition, "")
        onNewMsg(other.oldestItemDate, "", other.oldestPosition)
        setYoungestSyncedDate(other.youngestSyncedDate)
        setOldestSyncedDate(other.oldestSyncedDate)
        return this
    }

    private fun isSyncedSimultaneously(timeline: Timeline): Boolean {
        return (this != timeline &&
            !timeline.isCombined &&
            timelineType.connectionApiRoutine == timeline.timelineType.connectionApiRoutine &&
            searchQuery == timeline.searchQuery &&
            myAccountToSync == timeline.myAccountToSync &&
            actor == timeline.actor &&
            origin == timeline.origin)
    }

    private fun onSyncEnded(result: CommandResult): Timeline {
        if (result.hasError) {
            syncFailedDate.set(System.currentTimeMillis())
            if (result.message.isNotEmpty()) {
                errorMessage = result.message
            }
            syncFailedTimesCount.incrementAndGet()
            syncFailedTimesCountTotal.incrementAndGet()
        } else {
            syncSucceededDate.set(System.currentTimeMillis())
            syncedTimesCount.incrementAndGet()
            syncedTimesCountTotal.incrementAndGet()
        }
        if (result.newCount > 0) {
            newItemsCount.addAndGet(result.newCount)
            newItemsCountTotal.addAndGet(result.newCount)
        }
        if (result.downloadedCount > 0) {
            downloadedItemsCount.addAndGet(result.downloadedCount)
            downloadedItemsCountTotal.addAndGet(result.downloadedCount)
        }
        setChanged()
        return this
    }

    fun getSyncSucceededDate(): Long {
        return syncSucceededDate.get()
    }

    fun setSyncSucceededDate(syncSucceededDate: Long) {
        if (this.syncSucceededDate.get() != syncSucceededDate) {
            this.syncSucceededDate.set(syncSucceededDate)
            setChanged()
        }
    }

    val isSyncableSomehow: Boolean get() = isSyncable || isSyncableForOrigins || isSyncableForAccounts

    val isForMyAccount: Boolean get() = actor.nonEmpty && actor.isSame(myAccountToSync.actor)

    val isSyncedByOtherUser: Boolean get() = actor.isEmpty || myAccountToSync.actor.notSameUser(actor)

    fun getDownloadedItemsCount(isTotal: Boolean): Long {
        return if (isTotal) downloadedItemsCountTotal.get() else downloadedItemsCount.get()
    }

    fun getNewItemsCount(isTotal: Boolean): Long {
        return if (isTotal) newItemsCountTotal.get() else newItemsCount.get()
    }

    fun getSyncedTimesCount(isTotal: Boolean): Long {
        return if (isTotal) syncedTimesCountTotal.get() else syncedTimesCount.get()
    }

    fun getSyncFailedDate(): Long {
        return syncFailedDate.get()
    }

    fun getSyncFailedTimesCount(isTotal: Boolean): Long {
        return if (isTotal) syncFailedTimesCountTotal.get() else syncFailedTimesCount.get()
    }

    fun getErrorMessage(): String? {
        return errorMessage
    }

    fun getLastSyncedDate(): Long {
        return java.lang.Long.max(getSyncSucceededDate(), getSyncFailedDate())
    }

    fun resetCounters(all: Boolean) {
        if (all) {
            syncFailedTimesCountTotal.set(0)
            syncedTimesCountTotal.set(0)
            downloadedItemsCountTotal.set(0)
            newItemsCountTotal.set(0)
        }
        errorMessage = ""
        syncFailedTimesCount.set(0)
        syncedTimesCount.set(0)
        downloadedItemsCount.set(0)
        newItemsCount.set(0)
        countSince.set(System.currentTimeMillis())
        changed = true
    }

    fun getCountSince(): Long {
        return countSince.get()
    }

    fun getUri(): Uri {
        return MatchedUri.getTimelineUri(this)
    }

    fun getClickUri(): Uri {
        return Uri.parse("content://" + TIMELINE_CLICK_HOST + getUri().encodedPath)
    }

    private fun setChanged() {
        changed = true
        lastChangedDate = System.currentTimeMillis()
    }

    fun getLastChangedDate(): Long {
        return lastChangedDate
    }

    fun match(
        isForSelector: Boolean, isTimelineCombined: TriState, timelineType: TimelineType,
        actor: Actor, origin: Origin
    ): Boolean {
        if (isForSelector && isDisplayedInSelector == DisplayedInSelector.ALWAYS) {
            return true
        } else if (isForSelector && isDisplayedInSelector == DisplayedInSelector.NEVER) {
            return false
        } else if (timelineType != TimelineType.UNKNOWN && timelineType != this.timelineType) {
            return false
        } else if (isTimelineCombined == TriState.TRUE) {
            return isCombined
        } else if (isTimelineCombined == TriState.FALSE && isCombined) {
            return false
        } else if (timelineType == TimelineType.UNKNOWN || timelineType.scope == ListScope.ACTOR_AT_ORIGIN) {
            return ((actor.actorId == 0L || actor.actorId == actorId)
                && (origin.isEmpty || origin == this.origin))
        } else if (timelineType.isAtOrigin) {
            return origin.isEmpty || origin == this.origin
        }
        return actor.actorId == 0L || actor.actorId == actorId
    }

    fun hasActorProfile(): Boolean {
        return !isCombined && actor.nonEmpty && timelineType.hasActorProfile()
    }

    fun orElse(aDefault: Timeline): Timeline {
        return if (isEmpty) aDefault else this
    }

    companion object {
        val EMPTY: Timeline = Timeline()
        private val MIN_RETRY_PERIOD_MS = TimeUnit.SECONDS.toMillis(30)
        const val TIMELINE_CLICK_HOST: String = "timeline.app.andstatus.org"

        fun fromCursor(myContext: MyContext, cursor: Cursor): Timeline {
            val timeline = Timeline(
                myContext,
                DbUtils.getLong(cursor, BaseColumns._ID),
                TimelineType.load(DbUtils.getString(cursor, TimelineTable.TIMELINE_TYPE)),
                Actor.load(myContext, DbUtils.getLong(cursor, TimelineTable.ACTOR_ID)),
                myContext.origins.fromId(DbUtils.getLong(cursor, TimelineTable.ORIGIN_ID)),
                DbUtils.getString(cursor, TimelineTable.SEARCH_QUERY),
                DbUtils.getLong(cursor, TimelineTable.SELECTOR_ORDER)
            )
            timeline.changed = false
            timeline.isSyncedAutomatically = DbUtils.getBoolean(cursor, TimelineTable.IS_SYNCED_AUTOMATICALLY)
            timeline.isDisplayedInSelector =
                DisplayedInSelector.load(DbUtils.getString(cursor, TimelineTable.DISPLAYED_IN_SELECTOR))
            timeline.syncSucceededDate.set(DbUtils.getLong(cursor, TimelineTable.SYNC_SUCCEEDED_DATE))
            timeline.syncFailedDate.set(DbUtils.getLong(cursor, TimelineTable.SYNC_FAILED_DATE))
            timeline.errorMessage = DbUtils.getString(cursor, TimelineTable.ERROR_MESSAGE)
            timeline.syncedTimesCount.set(DbUtils.getLong(cursor, TimelineTable.SYNCED_TIMES_COUNT))
            timeline.syncFailedTimesCount.set(DbUtils.getLong(cursor, TimelineTable.SYNC_FAILED_TIMES_COUNT))
            timeline.downloadedItemsCount.set(DbUtils.getLong(cursor, TimelineTable.DOWNLOADED_ITEMS_COUNT))
            timeline.newItemsCount.set(DbUtils.getLong(cursor, TimelineTable.NEW_ITEMS_COUNT))
            timeline.countSince.set(DbUtils.getLong(cursor, TimelineTable.COUNT_SINCE))
            timeline.syncedTimesCountTotal.set(DbUtils.getLong(cursor, TimelineTable.SYNCED_TIMES_COUNT_TOTAL))
            timeline.syncFailedTimesCountTotal.set(DbUtils.getLong(cursor, TimelineTable.SYNC_FAILED_TIMES_COUNT_TOTAL))
            timeline.downloadedItemsCountTotal.set(DbUtils.getLong(cursor, TimelineTable.DOWNLOADED_ITEMS_COUNT_TOTAL))
            timeline.newItemsCountTotal.set(DbUtils.getLong(cursor, TimelineTable.NEW_ITEMS_COUNT_TOTAL))
            timeline.youngestPosition = DbUtils.getString(cursor, TimelineTable.YOUNGEST_POSITION)
            timeline.youngestItemDate = DbUtils.getLong(cursor, TimelineTable.YOUNGEST_ITEM_DATE)
            timeline.youngestSyncedDate = DbUtils.getLong(cursor, TimelineTable.YOUNGEST_SYNCED_DATE)
            timeline.oldestPosition = DbUtils.getString(cursor, TimelineTable.OLDEST_POSITION)
            timeline.oldestItemDate = DbUtils.getLong(cursor, TimelineTable.OLDEST_ITEM_DATE)
            timeline.oldestSyncedDate = DbUtils.getLong(cursor, TimelineTable.OLDEST_SYNCED_DATE)
            timeline.visibleItemId = DbUtils.getLong(cursor, TimelineTable.VISIBLE_ITEM_ID)
            timeline.visibleY = DbUtils.getInt(cursor, TimelineTable.VISIBLE_Y)
            timeline.visibleOldestDate = DbUtils.getLong(cursor, TimelineTable.VISIBLE_OLDEST_DATE)
            timeline.lastChangedDate = DbUtils.getLong(cursor, TimelineTable.LAST_CHANGED_DATE)
            return timeline
        }

        fun fromBundle(myContext: MyContext, bundle: Bundle?): Timeline {
            if (bundle == null) return EMPTY
            val timeline = myContext.timelines.fromId(bundle.getLong(IntentExtra.TIMELINE_ID.key))
            return if (timeline.nonEmpty) timeline
            else myContext.timelines[TimelineType.load(bundle.getString(IntentExtra.TIMELINE_TYPE.key)),
                Actor.load(myContext, bundle.getLong(IntentExtra.ACTOR_ID.key)),
                myContext.origins.fromId(BundleUtils.fromBundle(bundle, IntentExtra.ORIGIN_ID)),
                BundleUtils.getString(bundle, IntentExtra.SEARCH_QUERY)]
        }

        fun fromParsedUri(myContext: MyContext, parsedUri: ParsedUri, searchQueryIn: String?): Timeline {
            val timeline = myContext.timelines[parsedUri.getTimelineType(), Actor.load(
                myContext,
                parsedUri.getActorId()
            ), parsedUri.getOrigin(myContext),
                if (searchQueryIn.isNullOrEmpty()) parsedUri.searchQuery else searchQueryIn]
            if (timeline.timelineType == TimelineType.UNKNOWN && parsedUri.getActorsScreenType() == ActorsScreenType.UNKNOWN) {
                MyLog.w(Timeline::class.java, "fromParsedUri; uri:" + parsedUri.getUri() + "; " + timeline)
            }
            return timeline
        }

        fun fromId(myContext: MyContext, id: Long): Timeline {
            return if (id == 0L) EMPTY else MyQuery.getSet(
                myContext,
                "SELECT * FROM " + TimelineTable.TABLE_NAME + " WHERE " + BaseColumns._ID + "=" + id
            ) { cursor: Cursor -> fromCursor(myContext, cursor) }.stream().findFirst().orElse(EMPTY)
        }

        private fun setIfLess(value: AtomicLong, other: AtomicLong): Boolean {
            if (value.get() < other.get()) {
                value.set(other.get())
                return true
            }
            return false
        }
    }
}
