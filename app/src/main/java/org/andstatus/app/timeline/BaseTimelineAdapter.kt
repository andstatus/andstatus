/*
 * Copyright (C) 2015-2017 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.view.View
import android.widget.BaseAdapter
import android.widget.TextView
import org.andstatus.app.R
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.timeline.meta.Timeline
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.SharedPreferencesUtil

abstract class BaseTimelineAdapter<T : ViewItem<T>>(
        protected val myContext: MyContext,
        private val listData: TimelineData<T>) : BaseAdapter(), View.OnClickListener {
    protected val showAvatars = MyPreferences.getShowAvatars()
    protected val showAttachedImages = MyPreferences.getDownloadAndDisplayAttachedImages()
    protected val markRepliesToMe = SharedPreferencesUtil.getBoolean(
            MyPreferences.KEY_MARK_REPLIES_TO_ME_IN_TIMELINE, true)
    private val displayDensity: Float

    @Volatile
    private var positionRestored = false

    /** Single page data  */
    constructor(myContext: MyContext, timeline: Timeline, items: MutableList<T>) : this(myContext,
            TimelineData<T>(
                    null,
                    TimelinePage<T>(TimelineParameters(myContext, timeline, WhichPage.EMPTY), items)
            )
    ) {
    }

    fun getListData(): TimelineData<T> {
        return listData
    }

    override fun getCount(): Int {
        return listData.size()
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).getId()
    }

    override fun getItem(position: Int): T {
        return listData.getItem(position)
    }

    fun getItem(view: View): T? {
        return getItem(getPosition(view))
    }

    /** @return -1 if not found
     */
    fun getPosition(view: View): Int {
        val positionView = getPositionView(view) ?: return -1
        return positionView.text.toString().toInt()
    }

    private fun getPositionView(view: View?): TextView? {
        var parentView: View = view ?: return null

        for (i in 0..9) {
            val positionView = parentView.findViewById<TextView?>(R.id.position)
            if (positionView != null) {
                return positionView
            }
            parentView = if (parentView.getParent() != null &&
                    View::class.java.isAssignableFrom(parentView.getParent().javaClass)) {
                parentView.getParent() as View
            } else {
                break
            }
        }
        return null
    }

    protected fun setPosition(view: View, position: Int) {
        val positionView = getPositionView(view)
        if (positionView != null) {
            positionView.text = Integer.toString(position)
        }
    }

    fun getPositionById(itemId: Long): Int {
        return listData.getPositionById(itemId)
    }

    fun setPositionRestored(positionRestored: Boolean) {
        this.positionRestored = positionRestored
    }

    fun isPositionRestored(): Boolean {
        return positionRestored
    }

    protected fun mayHaveYoungerPage(): Boolean {
        return listData.mayHaveYoungerPage()
    }

    protected fun isCombined(): Boolean {
        return listData.params.isTimelineCombined()
    }

    override fun onClick(v: View) {
        if (!MyPreferences.isLongPressToOpenContextMenu() && v.getParent() != null) {
            v.showContextMenu()
        }
    }

    // See  http://stackoverflow.com/questions/2238883/what-is-the-correct-way-to-specify-dimensions-in-dip-from-java-code
    fun dpToPixes(dp: Int): Int {
        return (dp * displayDensity).toInt()
    }

    override fun toString(): String {
        return MyStringBuilder.formatKeyValue(this, listData)
    }

    init {
        if (myContext.isEmpty) {
            displayDensity = 1f
        } else {
            displayDensity = myContext.context().resources.displayMetrics.density
            MyLog.v(this) { "density=$displayDensity" }
        }
    }
}