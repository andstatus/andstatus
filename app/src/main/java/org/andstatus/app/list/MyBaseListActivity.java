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

package org.andstatus.app.list;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.View;
import android.widget.ListAdapter;
import android.widget.ListView;

import org.andstatus.app.MyActivity;
import org.andstatus.app.R;
import org.andstatus.app.timeline.EmptyBaseTimelineAdapter;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.widget.MySwipeRefreshLayout;

import java.util.Objects;

public abstract class MyBaseListActivity extends MyActivity implements
        MySwipeRefreshLayout.CanSwipeRefreshScrollUpCallback,
        MySwipeRefreshLayout.OnRefreshListener{

    protected MySwipeRefreshLayout mSwipeLayout = null;
    private int mPositionOfContextMenu = -1;
    private ListAdapter mAdapter = EmptyBaseTimelineAdapter.EMPTY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSwipeLayout = findSwipeLayout();
        if (mSwipeLayout != null) {
            mSwipeLayout.setOnRefreshListener(this);
        }
    }

    protected MySwipeRefreshLayout findSwipeLayout() {
        View view = findViewById(R.id.swipeRefreshLayout);
        if (view != null && MySwipeRefreshLayout.class.isAssignableFrom(view.getClass())) {
            return (MySwipeRefreshLayout) view ;
        }
        return null;
    }

    public int getPositionOfContextMenu() {
        return mPositionOfContextMenu;
    }

    public void setPositionOfContextMenu(int position) {
        this.mPositionOfContextMenu = position;
    }

    protected void setListAdapter(@NonNull ListAdapter adapter) {
        Objects.requireNonNull(adapter);
        mAdapter = adapter;
        getListView().setAdapter(mAdapter);
    }

    @NonNull
    public ListAdapter getListAdapter() {
        return mAdapter;
    }

    public ListView getListView() {
        return (ListView) findViewById(android.R.id.list);
    }

    @Override
    public boolean canSwipeRefreshChildScrollUp() {
        boolean can = true;
        try {
            // See http://stackoverflow.com/questions/3014089/maintain-save-restore-scroll-position-when-returning-to-a-listview/3035521#3035521
            int index = getListView().getFirstVisiblePosition();
            if (index == 0) {
                View v = getListView().getChildAt(0);
                int top = (v == null) ? 0 : (v.getTop() - getListView().getPaddingTop());
                can = top < 0;
            }
        } catch (java.lang.IllegalStateException e) {
            MyLog.v(this, e);
            can = false;
        }
        return can;
    }

    protected void hideSyncing(String source) {
        setCircularSyncIndicator(source, false);
    }

    protected void setCircularSyncIndicator(String source, boolean isSyncing) {
        if (mSwipeLayout != null
                && mSwipeLayout.isRefreshing() != isSyncing
                && !isFinishing()) {
            MyLog.v(this, source + " set Circular Syncing to " + isSyncing);
            mSwipeLayout.setRefreshing(isSyncing);
        }
    }

    /** Stub, which immediately hides the sync indicator */
    @Override
    public void onRefresh() {
        hideSyncing("Syncing not implemented in " + this.getClass().getName());
    }
}
