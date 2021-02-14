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

package org.andstatus.app.widget;

import android.content.Context;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import android.util.AttributeSet;

import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyStringBuilder;

/**
 * See http://stackoverflow.com/questions/24658428/swiperefreshlayout-webview-when-scroll-position-is-at-top
  */
public class MySwipeRefreshLayout extends SwipeRefreshLayout {
    private CanSwipeRefreshScrollUpCallback mCanSwipeRefreshScrollUpCallback;

    public MySwipeRefreshLayout(Context context) {
        this(context, null);
    }
    
    public MySwipeRefreshLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (CanSwipeRefreshScrollUpCallback.class.isAssignableFrom(context.getClass())) {
            mCanSwipeRefreshScrollUpCallback = (CanSwipeRefreshScrollUpCallback) context;
            MyLog.v(this, () -> "Created for " + MyStringBuilder.objToTag(mCanSwipeRefreshScrollUpCallback));
        }
    }

    public interface CanSwipeRefreshScrollUpCallback {
        boolean canSwipeRefreshChildScrollUp();
    }

    @Override
    public boolean canChildScrollUp() {
        if (mCanSwipeRefreshScrollUpCallback != null) {
            return mCanSwipeRefreshScrollUpCallback.canSwipeRefreshChildScrollUp();
        }
        return super.canChildScrollUp();
    }    

}
