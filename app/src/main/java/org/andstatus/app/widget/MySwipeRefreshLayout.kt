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
package org.andstatus.app.widget

import android.content.Context
import android.util.AttributeSet
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder

/**
 * See http://stackoverflow.com/questions/24658428/swiperefreshlayout-webview-when-scroll-position-is-at-top
 */
class MySwipeRefreshLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
        SwipeRefreshLayout(context, attrs) {
    private var mCanSwipeRefreshScrollUpCallback: CanSwipeRefreshScrollUpCallback? = null

    interface CanSwipeRefreshScrollUpCallback {
        fun canSwipeRefreshChildScrollUp(): Boolean
    }

    override fun canChildScrollUp(): Boolean {
        return mCanSwipeRefreshScrollUpCallback?.canSwipeRefreshChildScrollUp() ?: super.canChildScrollUp()
    }

    init {
        if (context is CanSwipeRefreshScrollUpCallback) {
            mCanSwipeRefreshScrollUpCallback = context
            MyLog.v(this) { "Created for " + MyStringBuilder.objToTag(mCanSwipeRefreshScrollUpCallback) }
        }
    }
}