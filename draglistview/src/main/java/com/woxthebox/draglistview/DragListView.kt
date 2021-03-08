/*
 * Copyright 2014 Magnus Woxblom
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.woxthebox.draglistview

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView
import com.woxthebox.draglistview.DragItemAdapter.DragStartCallback
import com.woxthebox.draglistview.DragItemRecyclerView.DragItemCallback
import com.woxthebox.draglistview.DragItemRecyclerView.DragItemListener
import com.woxthebox.draglistview.swipe.ListSwipeHelper
import com.woxthebox.draglistview.swipe.ListSwipeHelper.OnSwipeListener

class DragListView : FrameLayout {
    interface DragListListener {
        fun onItemDragStarted(position: Int)
        fun onItemDragging(itemPosition: Int, x: Float, y: Float)
        fun onItemDragEnded(fromPosition: Int, toPosition: Int)
    }

    abstract class DragListListenerAdapter : DragListListener {
        override fun onItemDragStarted(position: Int) {}
        override fun onItemDragging(itemPosition: Int, x: Float, y: Float) {}
        override fun onItemDragEnded(fromPosition: Int, toPosition: Int) {}
    }

    interface DragListCallback {
        fun canDragItemAtPosition(dragPosition: Int): Boolean
        fun canDropItemAtPosition(dropPosition: Int): Boolean
    }

    abstract class DragListCallbackAdapter : DragListCallback {
        override fun canDragItemAtPosition(dragPosition: Int): Boolean {
            return true
        }

        override fun canDropItemAtPosition(dropPosition: Int): Boolean {
            return true
        }
    }

    private var mRecyclerView: DragItemRecyclerView? = null
    private var mDragListListener: DragListListener? = null
    private var mDragListCallback: DragListCallback? = null
    private var mDragItem: DragItem? = null
    private var mSwipeHelper: ListSwipeHelper? = null
    private var mTouchX = 0f
    private var mTouchY = 0f

    constructor(context: Context?) : super(context!!) {}
    constructor(context: Context?, attrs: AttributeSet?) : super(context!!, attrs) {}
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context!!, attrs, defStyleAttr) {}

    override fun onFinishInflate() {
        super.onFinishInflate()
        mDragItem = DragItem(context)
        mRecyclerView = createRecyclerView()
        mRecyclerView!!.setDragItem(mDragItem)
        addView(mRecyclerView)
        addView(mDragItem.getDragItemView())
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        val retValue = handleTouchEvent(event)
        return retValue || super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val retValue = handleTouchEvent(event)
        return retValue || super.onTouchEvent(event)
    }

    private fun handleTouchEvent(event: MotionEvent): Boolean {
        mTouchX = event.x
        mTouchY = event.y
        if (isDragging) {
            when (event.action) {
                MotionEvent.ACTION_MOVE -> mRecyclerView!!.onDragging(event.x, event.y)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> mRecyclerView!!.onDragEnded()
            }
            return true
        }
        return false
    }

    private fun createRecyclerView(): DragItemRecyclerView {
        val recyclerView = LayoutInflater.from(context).inflate(R.layout.drag_item_recycler_view, this, false) as DragItemRecyclerView
        recyclerView.isMotionEventSplittingEnabled = false
        recyclerView.itemAnimator = DefaultItemAnimator()
        recyclerView.isVerticalScrollBarEnabled = false
        recyclerView.isHorizontalScrollBarEnabled = false
        recyclerView.setDragItemListener(object : DragItemListener {
            private var mDragStartPosition = 0
            override fun onDragStarted(itemPosition: Int, x: Float, y: Float) {
                parent.requestDisallowInterceptTouchEvent(true)
                mDragStartPosition = itemPosition
                if (mDragListListener != null) {
                    mDragListListener!!.onItemDragStarted(itemPosition)
                }
            }

            override fun onDragging(itemPosition: Int, x: Float, y: Float) {
                if (mDragListListener != null) {
                    mDragListListener!!.onItemDragging(itemPosition, x, y)
                }
            }

            override fun onDragEnded(newItemPosition: Int) {
                if (mDragListListener != null) {
                    mDragListListener!!.onItemDragEnded(mDragStartPosition, newItemPosition)
                }
            }
        })
        recyclerView.setDragItemCallback(object : DragItemCallback {
            override fun canDragItemAtPosition(dragPosition: Int): Boolean {
                return mDragListCallback == null || mDragListCallback!!.canDragItemAtPosition(dragPosition)
            }

            override fun canDropItemAtPosition(dropPosition: Int): Boolean {
                return mDragListCallback == null || mDragListCallback!!.canDropItemAtPosition(dropPosition)
            }
        })
        return recyclerView
    }

    fun setSwipeListener(swipeListener: OnSwipeListener?) {
        if (mSwipeHelper == null) {
            mSwipeHelper = ListSwipeHelper(context.applicationContext, swipeListener)
        } else {
            mSwipeHelper!!.setSwipeListener(swipeListener)
        }

        // Always detach first so we don't get double listeners
        mSwipeHelper!!.detachFromRecyclerView()
        if (swipeListener != null) {
            mSwipeHelper!!.attachToRecyclerView(mRecyclerView)
        }
    }

    /**
     * Resets the swipe state of all list item views except the one that is passed as an exception view.
     *
     * @param exceptionView This view will not be reset.
     */
    fun resetSwipedViews(exceptionView: View?) {
        if (mSwipeHelper != null) {
            mSwipeHelper!!.resetSwipedViews(exceptionView)
        }
    }

    val recyclerView: RecyclerView?
        get() = mRecyclerView
    val adapter: DragItemAdapter<*, *>?
        get() = if (mRecyclerView != null) {
            mRecyclerView!!.adapter as DragItemAdapter<*, *>?
        } else null

    fun setAdapter(adapter: DragItemAdapter<*, *>, hasFixedItemSize: Boolean) {
        mRecyclerView!!.setHasFixedSize(hasFixedItemSize)
        mRecyclerView!!.adapter = adapter
        adapter.setDragStartedListener(object : DragStartCallback {
            override fun startDrag(itemView: View, itemId: Long): Boolean {
                return mRecyclerView!!.startDrag(itemView, itemId, mTouchX, mTouchY)
            }

            override val isDragging: Boolean
                get() = mRecyclerView!!.isDragging
        })
    }

    fun swapAdapter(adapter: DragItemAdapter<*, *>, removeAndRecycleExisting: Boolean) {
        mRecyclerView!!.swapAdapter(adapter, removeAndRecycleExisting)
        adapter.setDragStartedListener(object : DragStartCallback {
            override fun startDrag(itemView: View, itemId: Long): Boolean {
                return mRecyclerView!!.startDrag(itemView, itemId, mTouchX, mTouchY)
            }

            override val isDragging: Boolean
                get() = mRecyclerView!!.isDragging
        })
    }

    fun setLayoutManager(layout: RecyclerView.LayoutManager?) {
        mRecyclerView!!.layoutManager = layout
    }

    fun setDragListListener(listener: DragListListener?) {
        mDragListListener = listener
    }

    fun setDragListCallback(callback: DragListCallback?) {
        mDragListCallback = callback
    }

    var isDragEnabled: Boolean
        get() = mRecyclerView!!.isDragEnabled
        set(enabled) {
            mRecyclerView.setDragEnabled(enabled)
        }

    fun setCustomDragItem(dragItem: DragItem?) {
        removeViewAt(1)
        val newDragItem: DragItem
        newDragItem = dragItem ?: DragItem(context)
        newDragItem.setCanDragHorizontally(mDragItem!!.canDragHorizontally())
        newDragItem.isSnapToTouch = mDragItem!!.isSnapToTouch
        mDragItem = newDragItem
        mRecyclerView!!.setDragItem(mDragItem)
        addView(mDragItem.getDragItemView())
    }

    val isDragging: Boolean
        get() = mRecyclerView!!.isDragging

    fun setCanDragHorizontally(canDragHorizontally: Boolean) {
        mDragItem!!.setCanDragHorizontally(canDragHorizontally)
    }

    fun setSnapDragItemToTouch(snapToTouch: Boolean) {
        mDragItem.setSnapToTouch(snapToTouch)
    }

    fun setCanNotDragAboveTopItem(canNotDragAboveTop: Boolean) {
        mRecyclerView!!.setCanNotDragAboveTopItem(canNotDragAboveTop)
    }

    fun setCanNotDragBelowBottomItem(canNotDragBelowBottom: Boolean) {
        mRecyclerView!!.setCanNotDragBelowBottomItem(canNotDragBelowBottom)
    }

    fun setScrollingEnabled(scrollingEnabled: Boolean) {
        mRecyclerView!!.setScrollingEnabled(scrollingEnabled)
    }

    /**
     * Set if items should not reorder automatically when dragging. If reorder is disabled, drop target
     * drawables can be set with [.setDropTargetDrawables] which will highlight the current item that
     * will be swapped when dropping. By default items will reorder automatically when dragging.
     *
     * @param disableReorder True if reorder of items should be disabled, false otherwise.
     */
    fun setDisableReorderWhenDragging(disableReorder: Boolean) {
        mRecyclerView!!.setDisableReorderWhenDragging(disableReorder)
    }

    /**
     * If [.setDisableReorderWhenDragging] has been set to True then a background and/or foreground drawable
     * can be provided to highlight the current item which will be swapped when dropping. These drawables
     * will be drawn as decorations in the RecyclerView and will not interfere with the items own background
     * and foreground drawables.
     *
     * @param backgroundDrawable The background drawable for the item that will be swapped.
     * @param foregroundDrawable The foreground drawable for the item that will be swapped.
     */
    fun setDropTargetDrawables(backgroundDrawable: Drawable?, foregroundDrawable: Drawable?) {
        mRecyclerView!!.setDropTargetDrawables(backgroundDrawable, foregroundDrawable)
    }
}