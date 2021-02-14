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
package org.andstatus.app.list

import android.os.Bundle
import android.view.View
import android.widget.ListAdapter
import android.widget.ListView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import org.andstatus.app.MyActivity
import org.andstatus.app.R
import org.andstatus.app.timeline.EmptyBaseTimelineAdapter
import org.andstatus.app.util.MyLog
import org.andstatus.app.widget.MySwipeRefreshLayout
import org.andstatus.app.widget.MySwipeRefreshLayout.CanSwipeRefreshScrollUpCallback
import java.util.*

abstract class MyBaseListActivity : MyActivity(), CanSwipeRefreshScrollUpCallback, OnRefreshListener {
    protected var mSwipeLayout: MySwipeRefreshLayout? = null
    private var mPositionOfContextMenu = -1
    private var mAdapter: ListAdapter? = EmptyBaseTimelineAdapter.Companion.EMPTY
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mSwipeLayout = findSwipeLayout()
        if (mSwipeLayout != null) {
            mSwipeLayout.setOnRefreshListener(this)
        }
    }

    protected fun findSwipeLayout(): MySwipeRefreshLayout? {
        val view = findViewById<View?>(R.id.swipeRefreshLayout)
        return if (view != null && MySwipeRefreshLayout::class.java.isAssignableFrom(view.javaClass)) {
            view as MySwipeRefreshLayout
        } else null
    }

    fun getPositionOfContextMenu(): Int {
        return mPositionOfContextMenu
    }

    fun setPositionOfContextMenu(position: Int) {
        mPositionOfContextMenu = position
    }

    protected open fun setListAdapter(adapter: ListAdapter) {
        Objects.requireNonNull(adapter)
        mAdapter = adapter
        getListView().setAdapter(mAdapter)
    }

    open fun getListAdapter(): ListAdapter {
        return mAdapter
    }

    open fun getListView(): ListView? {
        return findViewById<View?>(android.R.id.list) as ListView
    }

    override fun canSwipeRefreshChildScrollUp(): Boolean {
        var can = true
        try {
            // See http://stackoverflow.com/questions/3014089/maintain-save-restore-scroll-position-when-returning-to-a-listview/3035521#3035521
            val index = getListView().getFirstVisiblePosition()
            if (index == 0) {
                val v = getListView().getChildAt(0)
                val top = if (v == null) 0 else v.top - getListView().getPaddingTop()
                can = top < 0
            }
        } catch (e: IllegalStateException) {
            MyLog.v(this, e)
            can = false
        }
        return can
    }

    protected open fun hideSyncing(source: String?) {
        setCircularSyncIndicator(source, false)
    }

    protected fun setCircularSyncIndicator(source: String?, isSyncing: Boolean) {
        if (mSwipeLayout != null && mSwipeLayout.isRefreshing() != isSyncing && !isFinishing) {
            MyLog.v(this) { "$source set Circular Syncing to $isSyncing" }
            mSwipeLayout.setRefreshing(isSyncing)
        }
    }

    /** Stub, which immediately hides the sync indicator  */
    override fun onRefresh() {
        hideSyncing("Syncing not implemented in " + this.javaClass.name)
    }
}