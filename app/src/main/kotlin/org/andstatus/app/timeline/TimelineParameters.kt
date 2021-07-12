/*
 * Copyright (C) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.timeline

import android.app.LoaderManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import org.andstatus.app.IntentExtra
import org.andstatus.app.account.MyAccount
import org.andstatus.app.context.MyContext
import org.andstatus.app.data.TimelineSql
import org.andstatus.app.database.table.ActivityTable
import org.andstatus.app.timeline.meta.Timeline
import org.andstatus.app.timeline.meta.TimelineTitle
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.SelectionAndArgs
import org.andstatus.app.util.Taggable

class TimelineParameters(private val myContext: MyContext, val timeline: Timeline, val whichPage: WhichPage) : Taggable {
    var mLoaderCallbacks: LoaderManager.LoaderCallbacks<Cursor>? = null
    private var mProjection: MutableSet<String> = mutableSetOf()
    var maxDate: Long = 0

    // These params are updated just before page loading
    @Volatile
    var minDate: Long = 0

    @Volatile
    var selectionAndArgs: SelectionAndArgs = SelectionAndArgs()

    @Volatile
    var sortOrderAndLimit: String = ""

    // Execution state / loaded data:
    @Volatile
    var isLoaded = false

    @Volatile
    var rowsLoaded = 0

    @Volatile
    var minDateLoaded: Long = 0

    @Volatile
    var maxDateLoaded: Long = 0

    fun mayHaveYoungerPage(): Boolean {
        return (maxDate > 0
                || minDate > 0 && rowsLoaded > 0 && minDate < maxDateLoaded)
    }

    fun mayHaveOlderPage(): Boolean {
        return whichPage == WhichPage.CURRENT || minDate > 0 || maxDate > 0 && rowsLoaded > 0 && maxDate > minDateLoaded
    }

    fun isSortOrderAscending(): Boolean {
        return maxDate == 0L && minDate > 0
    }

    fun isEmpty(): Boolean {
        return timeline.isEmpty || whichPage == WhichPage.EMPTY
    }

    fun isAtHome(): Boolean {
        return timeline == myContext.timelines.getDefault()
    }

    override fun toString(): String {
        return MyStringBuilder.formatKeyValue(this,
                toSummary()
                        + ", account=" + timeline.myAccountToSync.getAccountName()
                        + (if (timeline.getActorId() == 0L) "" else ", selectedActorId=" + timeline.getActorId()) //    + ", projection=" + Arrays.toString(mProjection)
                        + (if (minDate > 0) ", minDate=" + MyLog.formatDateTime(minDate) else "")
                        + (if (maxDate > 0) ", maxDate=" + MyLog.formatDateTime(maxDate) else "")
                        + (if (selectionAndArgs.isEmpty()) "" else ", sa=$selectionAndArgs")
                        + (if (sortOrderAndLimit.isEmpty()) "" else ", sortOrder=$sortOrderAndLimit")
                        + (if (isLoaded) ", loaded" else "")
                        + if (mLoaderCallbacks == null) "" else ", loaderCallbacks=$mLoaderCallbacks"
        )
    }

    fun getTimelineType(): TimelineType {
        return timeline.timelineType
    }

    fun getSelectedActorId(): Long {
        return timeline.getActorId()
    }

    fun isTimelineCombined(): Boolean {
        return timeline.isCombined
    }

    fun saveState(outState: Bundle) {
        outState.putString(IntentExtra.MATCHED_URI.key, timeline.getUri().toString())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as TimelineParameters
        if (timeline != that.timeline) return false
        if (whichPage != WhichPage.CURRENT && that.whichPage != WhichPage.CURRENT) {
            if (minDate != that.minDate) return false
        }
        return maxDate == that.maxDate
    }

    override fun hashCode(): Int {
        var result = timeline.hashCode()
        result = if (whichPage == WhichPage.CURRENT) {
            31 * result + (-1 xor (-1 ushr 32))
        } else {
            31 * result + (minDate xor (minDate ushr 32)).toInt()
        }
        result = 31 * result + (maxDate xor (maxDate ushr 32)).toInt()
        return result
    }

    fun toSummary(): String {
        return whichPage.getTitle(myContext.context).toString() + " " + TimelineTitle.from(myContext, timeline)
    }

    fun getMyAccount(): MyAccount {
        return timeline.myAccountToSync
    }

    fun rememberItemDateLoaded(date: Long) {
        if (minDateLoaded == 0L || minDateLoaded > date) {
            minDateLoaded = date
        }
        if (maxDateLoaded == 0L || maxDateLoaded < date) {
            maxDateLoaded = date
        }
    }

    private fun prepareQueryParameters() {
        when (whichPage) {
            WhichPage.CURRENT -> minDate = TimelineViewPositionStorage.loadListPosition(this).minSentDate
            else -> {
            }
        }
        sortOrderAndLimit = buildSortOrderAndLimit()
        selectionAndArgs = buildSelectionAndArgs()
    }

    private fun buildSortOrderAndLimit(): String {
        return (ActivityTable.getTimelineSortOrder(getTimelineType(), isSortOrderAscending())
                + if (minDate > 0 && maxDate > 0) "" else " LIMIT " + PAGE_SIZE)
    }

    private fun buildSelectionAndArgs(): SelectionAndArgs {
        val sa = SelectionAndArgs()
        val minDateActual = if (minDate > 0) minDate else 1
        sa.addSelection(ActivityTable.getTimeSortField(getTimelineType()) + " >= ?", minDateActual.toString())
        if (maxDate > 0) {
            sa.addSelection(ActivityTable.getTimeSortField(getTimelineType()) + " <= ?",
                    if (maxDate >= minDateActual) maxDate.toString() else minDateActual.toString())
        }
        return sa
    }

    fun queryDatabase(): Cursor? {
        prepareQueryParameters()
        return myContext.context.contentResolver.query(getContentUri(), mProjection.toTypedArray<String>(),
                selectionAndArgs.selection, selectionAndArgs.selectionArgs, sortOrderAndLimit)
    }

    fun getContentUri(): Uri {
        return timeline.getUri()
    }

    fun getMyContext(): MyContext {
        return myContext
    }

    override val classTag: String get() = TAG

    companion object {
        private val TAG: String = TimelineParameters::class.java.simpleName

        /**
         * Msg are being loaded into the list starting from one page. More Msg
         * are being loaded in a case User scrolls down to the end of list.
         */
        const val PAGE_SIZE = 200
        fun clone(prev: TimelineParameters, whichPage: WhichPage): TimelineParameters {
            val params = TimelineParameters(prev.myContext,
                    if (whichPage == WhichPage.EMPTY) Timeline.EMPTY else prev.timeline,
                    if (whichPage == WhichPage.ANY) prev.whichPage else whichPage)
            if (whichPage != WhichPage.EMPTY) {
                enrichNonEmptyParameters(params, prev)
            }
            return params
        }

        private fun enrichNonEmptyParameters(params: TimelineParameters, prev: TimelineParameters) {
            params.mLoaderCallbacks = prev.mLoaderCallbacks
            when (params.whichPage) {
                WhichPage.OLDER -> if (prev.mayHaveOlderPage()) {
                    params.maxDate = prev.minDateLoaded
                } else {
                    params.maxDate = prev.maxDate
                }
                WhichPage.YOUNGER -> if (prev.mayHaveYoungerPage()) {
                    params.minDate = prev.maxDateLoaded
                } else {
                    params.minDate = prev.minDate
                }
                else -> {
                }
            }
            MyLog.v(TimelineParameters::class.java) { "Constructing " + params.toSummary() }
            params.mProjection = if (ViewItemType.fromTimelineType(params.timeline.timelineType)
                    == ViewItemType.ACTIVITY) TimelineSql.getActivityProjection() else TimelineSql.getTimelineProjection()
        }
    }
}
