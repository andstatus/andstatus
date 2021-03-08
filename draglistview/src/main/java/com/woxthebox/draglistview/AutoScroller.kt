/*
 * Copyright 2014 Magnus Woxblom
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.woxthebox.draglistview

import android.content.Context
import android.os.Handler

internal class AutoScroller(context: Context, private val mListener: AutoScrollListener) {
    internal enum class AutoScrollMode {
        POSITION, COLUMN
    }

    internal enum class ScrollDirection {
        UP, DOWN, LEFT, RIGHT
    }

    internal interface AutoScrollListener {
        fun onAutoScrollPositionBy(dx: Int, dy: Int)
        fun onAutoScrollColumnBy(columns: Int)
    }

    private val mHandler = Handler()
    var isAutoScrolling = false
        private set
    private val mScrollSpeed: Int
    private var mLastScrollTime: Long = 0
    private var mAutoScrollMode = AutoScrollMode.POSITION
    fun setAutoScrollMode(autoScrollMode: AutoScrollMode) {
        mAutoScrollMode = autoScrollMode
    }

    fun stopAutoScroll() {
        isAutoScrolling = false
    }

    fun startAutoScroll(direction: ScrollDirection?) {
        when (direction) {
            ScrollDirection.UP -> startAutoScrollPositionBy(0, mScrollSpeed)
            ScrollDirection.DOWN -> startAutoScrollPositionBy(0, -mScrollSpeed)
            ScrollDirection.LEFT -> if (mAutoScrollMode == AutoScrollMode.POSITION) {
                startAutoScrollPositionBy(mScrollSpeed, 0)
            } else {
                startAutoScrollColumnBy(1)
            }
            ScrollDirection.RIGHT -> if (mAutoScrollMode == AutoScrollMode.POSITION) {
                startAutoScrollPositionBy(-mScrollSpeed, 0)
            } else {
                startAutoScrollColumnBy(-1)
            }
        }
    }

    private fun startAutoScrollPositionBy(dx: Int, dy: Int) {
        if (!isAutoScrolling) {
            isAutoScrolling = true
            autoScrollPositionBy(dx, dy)
        }
    }

    private fun autoScrollPositionBy(dx: Int, dy: Int) {
        if (isAutoScrolling) {
            mListener.onAutoScrollPositionBy(dx, dy)
            mHandler.postDelayed({ autoScrollPositionBy(dx, dy) }, AUTO_SCROLL_UPDATE_DELAY.toLong())
        }
    }

    private fun startAutoScrollColumnBy(columns: Int) {
        if (!isAutoScrolling) {
            isAutoScrolling = true
            autoScrollColumnBy(columns)
        }
    }

    private fun autoScrollColumnBy(columns: Int) {
        if (isAutoScrolling) {
            if (System.currentTimeMillis() - mLastScrollTime > COLUMN_SCROLL_UPDATE_DELAY) {
                mListener.onAutoScrollColumnBy(columns)
                mLastScrollTime = System.currentTimeMillis()
            } else {
                mListener.onAutoScrollColumnBy(0)
            }
            mHandler.postDelayed({ autoScrollColumnBy(columns) }, AUTO_SCROLL_UPDATE_DELAY.toLong())
        }
    }

    companion object {
        private const val SCROLL_SPEED_DP = 8
        private const val AUTO_SCROLL_UPDATE_DELAY = 12
        private const val COLUMN_SCROLL_UPDATE_DELAY = 1000
    }

    init {
        mScrollSpeed = (context.resources.displayMetrics.density * SCROLL_SPEED_DP).toInt()
    }
}