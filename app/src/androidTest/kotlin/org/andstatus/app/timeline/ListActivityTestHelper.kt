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

import android.app.Activity
import android.app.Instrumentation.ActivityMonitor
import android.view.View
import android.widget.ListAdapter
import android.widget.ListView
import androidx.test.platform.app.InstrumentationRegistry
import org.andstatus.app.activity.ActivityViewItem
import org.andstatus.app.context.TestSuite
import org.andstatus.app.data.DbUtils
import org.andstatus.app.data.DownloadStatus
import org.andstatus.app.data.MyQuery
import org.andstatus.app.database.table.NoteTable
import org.andstatus.app.list.ContextMenuItem
import org.andstatus.app.list.MyBaseListActivity
import org.andstatus.app.note.BaseNoteViewItem
import org.andstatus.app.note.NoteViewItem
import org.andstatus.app.util.MyLog
import org.andstatus.app.view.SelectorDialog
import org.junit.Assert
import java.util.function.Predicate

class ListActivityTestHelper<T : MyBaseListActivity> {
    private val mActivity: T?
    private var mActivityMonitor: ActivityMonitor? = null
    private var dialogTagToMonitor: String? = null
    private val dialogToMonitor: SelectorDialog? = null

    constructor(activity: T?) : super() {
        mActivity = activity
    }

    constructor(activity: T, classOfActivityToMonitor: Class<out Activity>) : super() {
        addMonitor(classOfActivityToMonitor)
        mActivity = activity
    }

    /**
     * @return success
     */
    fun invokeContextMenuAction4ListItemId(methodExt: String, listItemId: Long, menuItem: ContextMenuItem,
                                           childViewId: Int): Boolean {
        val method = "invokeContextMenuAction4ListItemId"
        requireNotNull(mActivity)
        var success = false
        var msg = ""
        for (attempt in 1..3) {
            TestSuite.waitForIdleSync()
            val position = getPositionOfListItemId(listItemId)
            msg = "listItemId=$listItemId; menu Item=$menuItem; position=$position; attempt=$attempt"
            MyLog.v(this, msg)
            if (position >= 0 && getListItemIdAtPosition(position) == listItemId) {
                selectListPosition(methodExt, position)
                if (invokeContextMenuAction(methodExt, mActivity, position, menuItem.getId(), childViewId)) {
                    val id2 = getListItemIdAtPosition(position)
                    if (id2 == listItemId) {
                        success = true
                        break
                    } else {
                        MyLog.i(methodExt, "$method; Position changed, now pointing to listItemId=$id2; $msg")
                    }
                }
            }
        }
        MyLog.v(methodExt, "$method ended $success; $msg")
        TestSuite.waitForIdleSync()
        return success
    }

    @JvmOverloads
    fun selectListPosition(methodExt: String?, positionIn: Int,
                           listView: ListView? = mActivity?.listView,
                           listAdapter: ListAdapter? = getListAdapter()) {
        val method = "selectListPosition"
        if (listView == null || listAdapter == null) {
            MyLog.v(methodExt, "$method no listView or adapter")
            return
        }
        MyLog.v(methodExt, "$method started; position=$positionIn")
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            var position = positionIn
            if (listAdapter.getCount() <= position) {
                position = listAdapter.getCount() - 1
            }
            MyLog.v(methodExt, method + " on setSelection " + position
                    + " of " + (listAdapter.getCount() - 1))
            listView.setSelectionFromTop(position, 0)
        }
        TestSuite.waitForIdleSync()
        MyLog.v(methodExt, "$method ended")
    }

    /**
     * @return success
     *
     * Note: This method cannot be invoked on the main thread.
     * See https://github.com/google/google-authenticator-android/blob/master/tests/src/com/google/android/apps/authenticator/TestUtilities.java
     */
    private fun invokeContextMenuAction(methodExt: String, activity: MyBaseListActivity,
                                        position: Int, menuItemId: Int, childViewId: Int): Boolean {
        val method = "invokeContextMenuAction"
        MyLog.v(methodExt, "$method started on menuItemId=$menuItemId at position=$position")
        var success = false
        var position1 = position
        for (attempt in 1..3) {
            goToPosition(methodExt, position)
            if (!longClickListAtPosition(methodExt, position1, childViewId)) {
                break
            }
            if (mActivity?.getPositionOfContextMenu() == position) {
                success = true
                break
            }
            MyLog.i(methodExt, method + "; Context menu created for position " + mActivity?.getPositionOfContextMenu()
                    + " instead of " + position
                    + "; was set to " + position1 + "; attempt " + attempt)
            position1 = position + (position1 - (mActivity?.getPositionOfContextMenu() ?: 0))
        }
        if (success) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                MyLog.v(methodExt, "$method; before performContextMenuIdentifierAction")
                activity.getWindow().performContextMenuIdentifierAction(menuItemId, 0)
            }
            TestSuite.waitForIdleSync()
        }
        MyLog.v(methodExt, "$method ended $success")
        return success
    }

    private fun longClickListAtPosition(methodExt: String?, position: Int, childViewId: Int): Boolean {
        val parentView = getViewByPosition(position)
        if (parentView == null) {
            MyLog.i(methodExt, "Parent view at list position $position doesn't exist")
            return false
        }
        var childView: View? = null
        if (childViewId != 0) {
            childView = parentView.findViewById(childViewId)
            if (childView == null) {
                MyLog.i(methodExt, "Child view $childViewId at list position $position doesn't exist")
                return false
            }
        }
        val viewToClick = childView ?: parentView
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val msg = ("performLongClick on "
                    + (if (childViewId == 0) "" else "child $childViewId ")
                    + viewToClick + " at position " + position)
            MyLog.v(methodExt, msg)
            try {
                viewToClick.performLongClick()
            } catch (e: Exception) {
                MyLog.e(msg, e)
            }
        }
        TestSuite.waitForIdleSync()
        return true
    }

    fun goToPosition(methodExt: String?, position: Int) {
        mActivity?.listView?.let { listView ->
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val msg = "goToPosition $position"
                MyLog.v(methodExt, msg)
                try {
                    listView.setSelectionFromTop(position + listView.getHeaderViewsCount(), 0)
                } catch (e: Exception) {
                    MyLog.e(msg, e)
                }
            }
        }
        TestSuite.waitForIdleSync()
    }

    // See http://stackoverflow.com/questions/24811536/android-listview-get-item-view-by-position
    fun getViewByPosition(position: Int): View? {
        return getViewByPosition(position, mActivity?.listView, getListAdapter())
    }

    fun getViewByPosition(position: Int, listView: ListView?, listAdapter: ListAdapter?): View? {
        if (listView == null) return null

        val method = "getViewByPosition"
        val firstListItemPosition = listView.getFirstVisiblePosition() - listView.getHeaderViewsCount()
        val lastListItemPosition = firstListItemPosition + listView.getChildCount() - 1 - listView.getHeaderViewsCount()
        val view: View?
        view = if (position < firstListItemPosition || position > lastListItemPosition) {
            if (position < 0 || listAdapter == null || listAdapter.count < position + 1) {
                null
            } else {
                listAdapter.getView(position, null, listView)
            }
        } else {
            val childIndex = position - firstListItemPosition
            listView.getChildAt(childIndex)
        }
        MyLog.v(this, method + ": pos:" + position + ", first:" + firstListItemPosition
                + ", last:" + lastListItemPosition + ", view:" + view)
        return view
    }

    fun getListItemIdOfLoadedReply(): Long {
        return getListItemIdOfLoadedReply { any: BaseNoteViewItem<*>? -> true }
    }

    fun getListItemIdOfLoadedReply(predicate: Predicate<BaseNoteViewItem<*>>): Long {
        return findListItemId("Loaded reply") { item: BaseNoteViewItem<*> ->
            if (item.inReplyToNoteId != 0L && item.noteStatus == DownloadStatus.LOADED && predicate.test(item)) {
                val statusOfReplied: DownloadStatus = DownloadStatus.Companion.load(
                        MyQuery.noteIdToLongColumnValue(NoteTable.NOTE_STATUS, item.inReplyToNoteId))
                statusOfReplied == DownloadStatus.LOADED
            } else false
        }
    }

    fun findListItemId(description: String?, predicate: Predicate<BaseNoteViewItem<*>>): Long {
        return findListItem(description, predicate).getId()
    }

    fun findListItem(description: String?, predicate: Predicate<BaseNoteViewItem<*>>): ViewItem<*> {
        val method = "findListItemId"
        val listAdapter = getListAdapter()
        if (listAdapter == null) {
            MyLog.v(method, "$method no listAdapter")
        } else {
            for (ind in 0 until listAdapter.count) {
                val viewItem = listAdapter.getItem(ind) as ViewItem<*>
                val item: BaseNoteViewItem<*> = toBaseNoteViewItem(viewItem)
                if (!item.isEmpty) {
                    if (predicate.test(item)) {
                        MyLog.v(this, "$method $ind. found $description : $item")
                        return viewItem
                    } else {
                        MyLog.v(this, "$ind. skipped : $item")
                    }
                }
            }
        }
        Assert.fail(method + " didn't find '" + description + "' in " + listAdapter)
        return EmptyViewItem.EMPTY
    }

    fun getPositionOfListItemId(itemId: Long): Int {
        var position = -1
        val listAdapter = getListAdapter()
        if (listAdapter != null) for (ind in 0 until listAdapter.count) {
            if (itemId == listAdapter.getItemId(ind)) {
                position = ind
                break
            }
        }
        return position
    }

    fun getListItemIdAtPosition(position: Int): Long {
        var itemId: Long = 0
        val listAdapter = getListAdapter()
        if (listAdapter != null && position >= 0 && position < listAdapter.count) {
            itemId = listAdapter.getItemId(position)
        }
        return itemId
    }

    private fun getListAdapter(): ListAdapter? {
        return mActivity?.getListAdapter()
    }

    @JvmOverloads
    fun clickListAtPosition(methodExt: String?, position: Int,
                            listView: ListView? = mActivity?.listView,
                            listAdapter: ListAdapter? = getListAdapter()) {
        val method = "clickListAtPosition"
        if (listView == null || listAdapter == null) {
            MyLog.v(methodExt, "$method no listView or adapter")
            return
        }
        val viewToClick = getViewByPosition(position, listView, listAdapter)
        val listItemId = listAdapter.getItemId(position)
        val msgLog = "$method; id:$listItemId, position:$position, view:$viewToClick"
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            MyLog.v(methodExt, "onPerformClick $msgLog")

            // One of the two should work
            viewToClick?.performClick()
            listView.performItemClick(
                    viewToClick,
                    position + listView.getHeaderViewsCount(), listItemId)
            MyLog.v(methodExt, "afterClick $msgLog")
        }
        MyLog.v(methodExt, "$method ended, $msgLog")
        TestSuite.waitForIdleSync()
    }

    fun addMonitor(classOfActivity: Class<out Activity>) {
        mActivityMonitor = InstrumentationRegistry.getInstrumentation()
                .addMonitor(classOfActivity.getName(), null, false)
    }

    fun waitForNextActivity(methodExt: String?, timeOut: Long): Activity? {
        val nextActivity = InstrumentationRegistry.getInstrumentation().waitForMonitorWithTimeout(mActivityMonitor, timeOut)
        MyLog.v(methodExt, "After waitForMonitor: $nextActivity")
        Assert.assertNotNull("$methodExt; Next activity should be created", nextActivity)
        TestSuite.waitForListLoaded(nextActivity, 2)
        mActivityMonitor = null
        return nextActivity
    }

    fun waitForSelectorDialog(methodExt: String?, timeout: Int): SelectorDialog? {
        val method = "waitForSelectorDialog"
        var selectorDialog: SelectorDialog? = null
        var isVisible = false
        for (ind in 0..19) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            selectorDialog = mActivity?.getSupportFragmentManager()?.findFragmentByTag(dialogTagToMonitor) as SelectorDialog?
            if (selectorDialog != null && selectorDialog.isVisible) {
                isVisible = true
                break
            }
            if (DbUtils.waitMs(method, 1000)) {
                break
            }
        }
        Assert.assertTrue("$methodExt: Didn't find SelectorDialog with tag:'$dialogTagToMonitor'", selectorDialog != null)
        Assert.assertTrue(isVisible)
        val list = selectorDialog?.listView
        requireNotNull(list)
        var itemsCount = 0
        val minCount = 1
        for (ind in 0..59) {
            if (DbUtils.waitMs(method, 2000)) {
                break
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            val itemsCountNew = list.count
            MyLog.v(methodExt, "waitForSelectorDialog; countNew=$itemsCountNew, prev=$itemsCount, min=$minCount")
            if (itemsCountNew >= minCount && itemsCount == itemsCountNew) {
                break
            }
            itemsCount = itemsCountNew
        }
        Assert.assertTrue("There are $itemsCount items (min=$minCount) in the list of $dialogTagToMonitor",
                itemsCount >= minCount)
        return selectorDialog
    }

    fun selectIdFromSelectorDialog(method: String?, id: Long) {
        val selector = waitForSelectorDialog(method, 15000)
        val position = selector?.getListAdapter()?.getPositionById(id) ?: -1
        Assert.assertTrue("$method; Item id:$id found", position >= 0)
        requireNotNull(selector)
        selectListPosition(method, position, selector.listView, selector.getListAdapter())
        clickListAtPosition(method, position, selector.listView, selector.getListAdapter())
    }

    // TODO: Unify interface with my List Activity
    fun getSelectorViewByPosition(position: Int): View? {
        val selector = dialogToMonitor
        val method = "getSelectorViewByPosition"
        requireNotNull(selector)
        val listView = selector.listView
        val listAdapter = selector.getListAdapter()
        if (listView == null || listAdapter == null) {
            MyLog.v(method, "$method no listView or adapter")
            return null
        }
        val firstListItemPosition = listView.firstVisiblePosition
        val lastListItemPosition = firstListItemPosition + listView.childCount - 1
        val view: View?
        view = if (position < firstListItemPosition || position > lastListItemPosition) {
            if (position < 0 || listAdapter.count < position + 1) {
                null
            } else {
                listAdapter.getView(position, null, listView)
            }
        } else {
            val childIndex = position - firstListItemPosition
            listView.getChildAt(childIndex)
        }
        MyLog.v(this, method + ": pos:" + position + ", first:" + firstListItemPosition
                + ", last:" + lastListItemPosition + ", view:" + view)
        return view
    }

    fun clickView(methodExt: String, resourceId: Int) {
        mActivity?.findViewById<View>(resourceId)?.let {
            clickView(methodExt, it)
        }
    }

    fun clickView(methodExt: String, view: View) {
        val clicker = Runnable {
            MyLog.v(methodExt, "Before click view")
            view.performClick()
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync(clicker)
        MyLog.v(methodExt, "After click view")
        TestSuite.waitForIdleSync()
    }

    companion object {
        fun <T : MyBaseListActivity> newForSelectorDialog(activity: T?,
                                                           dialogTagToMonitor: String?): ListActivityTestHelper<T> {
            val helper = ListActivityTestHelper(activity)
            helper.dialogTagToMonitor = dialogTagToMonitor
            return helper
        }

        fun toBaseNoteViewItem(objItem: Any): BaseNoteViewItem<*> {
            if (ActivityViewItem::class.java.isAssignableFrom(objItem.javaClass)) {
                return (objItem as ActivityViewItem).noteViewItem
            } else if (BaseNoteViewItem::class.java.isAssignableFrom(objItem.javaClass)) {
                return objItem as BaseNoteViewItem<*>
            }
            return NoteViewItem.Companion.EMPTY
        }
    }
}
