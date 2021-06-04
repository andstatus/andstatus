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

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.TextView
import org.andstatus.app.ActivityRequestCode
import org.andstatus.app.IntentExtra
import org.andstatus.app.R
import org.andstatus.app.account.MyAccount
import org.andstatus.app.list.SyncLoader
import org.andstatus.app.net.social.Actor
import org.andstatus.app.origin.Origin
import org.andstatus.app.timeline.BaseTimelineAdapter
import org.andstatus.app.timeline.LoadableListActivity
import org.andstatus.app.timeline.LoadableListPosition
import org.andstatus.app.timeline.WhichPage
import org.andstatus.app.util.I18n
import org.andstatus.app.util.MyCheckBox
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyUrlSpan
import org.andstatus.app.util.RelativeTime
import org.andstatus.app.util.StringUtil
import org.andstatus.app.view.EnumSelector
import java.util.stream.Collectors

/**
 * @author yvolk@yurivolkov.com
 */
class ManageTimelines : LoadableListActivity<ManageTimelinesViewItem>() {
    private var sortByField = R.id.synced
    private var sortDefault = true
    private var columnHeadersParent: ViewGroup? = null
    private var contextMenu: ManageTimelinesContextMenu? = null
    private var selectedItem: ManageTimelinesViewItem? = null
    private var isTotal = false

    @Volatile
    private var countersSince: Long = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        mLayoutId = R.layout.timeline_list
        super.onCreate(savedInstanceState)
        contextMenu = ManageTimelinesContextMenu(this)
        val linearLayout = findViewById<LinearLayout?>(R.id.linear_list_wrapper)
        val inflater = layoutInflater
        val listHeader = inflater.inflate(R.layout.timeline_list_header, linearLayout, false)
        linearLayout.addView(listHeader, 0)
        columnHeadersParent = listHeader.findViewById<ViewGroup>(R.id.columnHeadersParent).also { group ->
            for (i in 0 until group.getChildCount()) {
                group.getChildAt(i).setOnClickListener { v: View -> sortBy(v.getId()) }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode != RESULT_OK || data == null) {
            return
        }
        when (ActivityRequestCode.fromId(requestCode)) {
            ActivityRequestCode.SELECT_DISPLAYED_IN_SELECTOR -> selectedItem?.let { item ->
                val displayedInSelector: DisplayedInSelector = DisplayedInSelector.load(
                        data.getStringExtra(IntentExtra.SELECTABLE_ENUM.key))
                item.timeline.setDisplayedInSelector(displayedInSelector)
                MyLog.v("isDisplayedInSelector") {
                    displayedInSelector.save() + " " +
                            item.timeline
                }
                if (displayedInSelector != DisplayedInSelector.IN_CONTEXT || sortByField == R.id.displayedInSelector) {
                    showList(WhichPage.CURRENT)
                }
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onLoadFinished(pos: LoadableListPosition<*>) {
        showSortColumn()
        super.onLoadFinished(pos)
    }

    private fun showSortColumn() {
        val parent = columnHeadersParent ?: return
        for (i in 0 until parent.getChildCount() ) {
            val view = parent.getChildAt(i)
            if (!TextView::class.java.isAssignableFrom(view.javaClass)) {
                continue
            }
            val textView = view as TextView
            var text = textView.text.toString()
            if (text.isNotEmpty() && "▲▼↑↓".indexOf(text[0]) >= 0) {
                text = text.substring(1)
                textView.text = text
            }
            if (textView.id == sortByField) {
                textView.text = (if (sortDefault) '▲' else '▼').toString() + text
            }
        }
    }

    private fun sortBy(fieldId: Int, isDefaultOrder: Boolean = sortByField != fieldId) {
        sortByField = fieldId
        sortDefault = isDefaultOrder
        showList(WhichPage.CURRENT)
    }

    override fun newSyncLoader(args: Bundle?): SyncLoader<ManageTimelinesViewItem> {
        return object : SyncLoader<ManageTimelinesViewItem>() {
            override fun load(publisher: ProgressPublisher?) {
                items = myContext.timelines
                        .stream()
                        .map { timeline: Timeline ->
                            ManageTimelinesViewItem(myContext, timeline,
                                    MyAccount.EMPTY, false)
                        }
                        .sorted(ManageTimelinesViewItemComparator(sortByField, sortDefault, isTotal))
                        .collect(Collectors.toList())
                countersSince = items.stream().map { item: ManageTimelinesViewItem -> item.countSince }
                        .filter { count: Long -> count > 0 }
                        .min { obj: Long, anotherLong: Long -> obj.compareTo(anotherLong) }
                        .orElse(0L)
            }
        }
    }

    override fun newListAdapter(): BaseTimelineAdapter<ManageTimelinesViewItem> {
        return object : BaseTimelineAdapter<ManageTimelinesViewItem>(myContext,
                myContext.timelines[TimelineType.MANAGE_TIMELINES, Actor.EMPTY,  Origin.EMPTY],
                getLoaded().getList() as MutableList<ManageTimelinesViewItem>) {
            var defaultTimeline: Timeline? = myContext.timelines.getDefault()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val view = convertView ?: newView()
                view.setOnCreateContextMenuListener(contextMenu)
                view.setOnClickListener(this)
                setPosition(view, position)
                val item = getItem(position)
                MyUrlSpan.showText(view, R.id.title, item.timelineTitle.toString(), false, true)
                MyUrlSpan.showText(view, R.id.account, item.timelineTitle.accountName, false, true)
                MyUrlSpan.showText(view, R.id.origin, item.timelineTitle.originName, false, true)
                showDisplayedInSelector(view, item)
                MyCheckBox.set(view, R.id.synced, item.timeline.isSyncedAutomatically(),
                        if (item.timeline.isSyncableAutomatically()) CompoundButton.OnCheckedChangeListener {
                            buttonView: CompoundButton?, isChecked: Boolean ->
                            item.timeline.setSyncedAutomatically(isChecked)
                            MyLog.v("isSyncedAutomatically") { (if (isChecked) "+ " else "- ") + item.timeline }
                        } else null)
                MyUrlSpan.showText(view, R.id.syncedTimesCount,
                        I18n.notZero(item.timeline.getSyncedTimesCount(isTotal)), false, true)
                MyUrlSpan.showText(view, R.id.downloadedItemsCount,
                        I18n.notZero(item.timeline.getDownloadedItemsCount(isTotal)), false, true)
                MyUrlSpan.showText(view, R.id.newItemsCount,
                        I18n.notZero(item.timeline.getNewItemsCount(isTotal)), false, true)
                MyUrlSpan.showText(view, R.id.syncSucceededDate,
                        RelativeTime.getDifference(this@ManageTimelines, item.timeline.getSyncSucceededDate()),
                        false, true)
                MyUrlSpan.showText(view, R.id.syncFailedTimesCount,
                        I18n.notZero(item.timeline.getSyncFailedTimesCount(isTotal)), false, true)
                MyUrlSpan.showText(view, R.id.syncFailedDate,
                        RelativeTime.getDifference(this@ManageTimelines, item.timeline.getSyncFailedDate()),
                        false, true)
                MyUrlSpan.showText(view, R.id.errorMessage, item.timeline.getErrorMessage(), false, true)
                MyUrlSpan.showText(view, R.id.lastChangedDate,
                        RelativeTime.getDifference(this@ManageTimelines, item.timeline.getLastChangedDate()),
                        false, true)
                return view
            }

            protected fun showDisplayedInSelector(parentView: View, item: ManageTimelinesViewItem) {
                val view = parentView.findViewById<CheckBox?>(R.id.displayedInSelector)
                MyCheckBox.set(parentView,
                        R.id.displayedInSelector,
                        item.timeline.isDisplayedInSelector() != DisplayedInSelector.NEVER
                ) { buttonView: CompoundButton, isChecked: Boolean ->
                    if (isChecked) {
                        selectedItem = item
                        EnumSelector.newInstance<DisplayedInSelector>(
                                ActivityRequestCode.SELECT_DISPLAYED_IN_SELECTOR,
                                DisplayedInSelector::class.java).show(this@ManageTimelines)
                    } else {
                        item.timeline.setDisplayedInSelector(DisplayedInSelector.NEVER)
                        buttonView.setText("")
                    }
                    MyLog.v("isDisplayedInSelector") { (if (isChecked) "+ " else "- ") + item.timeline }
                }
                view.text = if (item.timeline == defaultTimeline) "D" else if (item.timeline.isDisplayedInSelector() == DisplayedInSelector.ALWAYS) "*" else ""
            }

            private fun newView(): View {
                return LayoutInflater.from(this@ManageTimelines).inflate(R.layout.timeline_list_item, null)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.timelines, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun getCustomTitle(): CharSequence {
        val title = StringBuilder(title
                .toString() + (if (getListAdapter().getCount() == 0) "" else " " + getListAdapter().getCount())
                + " / ")
        if (isTotal) {
            title.append(getText(R.string.total_counters))
        } else if (countersSince > 0) {
            title.append(StringUtil.format(this, R.string.since,
                    RelativeTime.getDifference(this, countersSince)))
        }
        return title
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.getItemId()) {
            R.id.reset_counters_menu_item -> {
                myContext.timelines.resetCounters(isTotal)
                myContext.timelines.saveChanged().thenRun { showList(WhichPage.CURRENT) }
            }
            R.id.reset_timelines_order -> {
                myContext.timelines.resetDefaultSelectorOrder()
                myContext.timelines.saveChanged().thenRun { sortBy(R.id.displayedInSelector, true) }
            }
            R.id.total_counters -> {
                isTotal = !isTotal
                item.setChecked(isTotal)
                showList(WhichPage.CURRENT)
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return false
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        contextMenu?.onContextItemSelected(item)
        return super.onContextItemSelected(item)
    }
}
