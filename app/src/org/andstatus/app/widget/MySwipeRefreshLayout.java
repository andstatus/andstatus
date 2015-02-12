/**
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

package org.andstatus.app.widget;

import android.app.ListActivity;
import android.content.Context;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.AttributeSet;
import android.view.View;

import org.andstatus.app.util.MyLog;

/**
 * See http://stackoverflow.com/questions/24658428/swiperefreshlayout-webview-when-scroll-position-is-at-top
  */
public class MySwipeRefreshLayout extends SwipeRefreshLayout {
    private CanChildScrollUpCallback mCanChildScrollUpCallback;
    private ListActivity mListActivity;
    
    public MySwipeRefreshLayout(Context context) {
        this(context, null);
    }
    
    public MySwipeRefreshLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (ListActivity.class.isInstance(context)) {
            mListActivity = (ListActivity) context;
            MyLog.v(this, "Created for " + MyLog.objTagToString(mListActivity));
        }
    }

    public void setCanChildScrollUpCallback(CanChildScrollUpCallback canChildScrollUpCallback) {
        mCanChildScrollUpCallback = canChildScrollUpCallback;
    }

    public static interface CanChildScrollUpCallback {
        public boolean canSwipeRefreshChildScrollUp();
    }

    @Override
    public boolean canChildScrollUp() {
        if (mCanChildScrollUpCallback != null) {
            return mCanChildScrollUpCallback.canSwipeRefreshChildScrollUp();
        }
        if (mListActivity != null) {
            return canListActivityListScrollUp();
        }
        return super.canChildScrollUp();
    }    
    
    private boolean canListActivityListScrollUp() {
        // See http://stackoverflow.com/questions/3014089/maintain-save-restore-scroll-position-when-returning-to-a-listview/3035521#3035521
        int index = mListActivity.getListView().getFirstVisiblePosition();
        if (index == 0) {
            View v = mListActivity.getListView().getChildAt(0);
            int top = (v == null) ? 0 : (v.getTop() - mListActivity.getListView().getPaddingTop());
            return top < 0;
        }
        return true;
    }
    
}
