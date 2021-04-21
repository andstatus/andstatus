/**
 * Copyright 2014 Magnus Woxblom
 *
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.woxthebox.draglistview

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.woxthebox.draglistview.AutoScroller.AutoScrollListener
import com.woxthebox.draglistview.AutoScroller.ScrollDirection

class DragItemRecyclerView : RecyclerView, AutoScrollListener {
    interface DragItemListener {
        fun onDragStarted(itemPosition: Int, x: Float, y: Float)
        fun onDragging(itemPosition: Int, x: Float, y: Float)
        fun onDragEnded(newItemPosition: Int)
    }

    interface DragItemCallback {
        fun canDragItemAtPosition(dragPosition: Int): Boolean
        fun canDropItemAtPosition(dropPosition: Int): Boolean
    }

    private enum class DragState {
        DRAG_STARTED, DRAGGING, DRAG_ENDED
    }

    private var mAutoScroller: AutoScroller? = null
    private var mListener: DragItemListener? = null
    private var mDragCallback: DragItemCallback? = null
    private var mDragState = DragState.DRAG_ENDED
    private var mAdapter: DragItemAdapter<Any, DragItemAdapter.ViewHolder>? = null
    private var mDragItem: DragItem? = null
    private var mDropTargetBackgroundDrawable: Drawable? = null
    private var mDropTargetForegroundDrawable: Drawable? = null
    var dragItemId = NO_ID
        private set
    private var mHoldChangePosition = false
    private var mDragItemPosition = 0
    private var mTouchSlop = 0
    private var mStartY = 0f
    private var mClipToPadding = false
    private var mCanNotDragAboveTop = false
    private var mCanNotDragBelowBottom = false
    private var mScrollingEnabled = true
    private var mDisableReorderWhenDragging = false
    var isDragEnabled = true

    constructor(context: Context?) : super(context!!) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context!!, attrs, 0) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(context!!, attrs, defStyle) {
        init()
    }

    private fun init() {
        mAutoScroller = AutoScroller(context, this)
        mTouchSlop = ViewConfiguration.get(context).scaledTouchSlop
        addItemDecoration(object : ItemDecoration() {
            override fun onDraw(c: Canvas, parent: RecyclerView, state: State) {
                super.onDraw(c, parent, state)
                drawDecoration(c, parent, mDropTargetBackgroundDrawable)
            }

            override fun onDrawOver(c: Canvas, parent: RecyclerView, state: State) {
                super.onDrawOver(c, parent, state)
                drawDecoration(c, parent, mDropTargetForegroundDrawable)
            }

            private fun drawDecoration(c: Canvas, parent: RecyclerView, drawable: Drawable?) {
                if (mAdapter == null || mAdapter?.dropTargetId == NO_ID || drawable == null) {
                    return
                }
                for (i in 0 until parent.childCount) {
                    val item = parent.getChildAt(i)
                    val pos = getChildAdapterPosition(item)
                    if (pos != NO_POSITION && mAdapter!!.getItemId(pos) == mAdapter?.dropTargetId) {
                        drawable.setBounds(item.left, item.top, item.right, item.bottom)
                        drawable.draw(c)
                    }
                }
            }
        })
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (!mScrollingEnabled) {
            return false
        }
        when (event.action) {
            MotionEvent.ACTION_DOWN -> mStartY = event.y
            MotionEvent.ACTION_MOVE -> {
                val diffY = Math.abs(event.y - mStartY)
                if (diffY > mTouchSlop * 0.5) {
                    // Steal event from parent as we now only want to scroll in the list
                    parent.requestDisallowInterceptTouchEvent(true)
                }
            }
        }
        return super.onInterceptTouchEvent(event)
    }

    fun setCanNotDragAboveTopItem(canNotDragAboveTop: Boolean) {
        mCanNotDragAboveTop = canNotDragAboveTop
    }

    fun setCanNotDragBelowBottomItem(canNotDragBelowBottom: Boolean) {
        mCanNotDragBelowBottom = canNotDragBelowBottom
    }

    fun setScrollingEnabled(scrollingEnabled: Boolean) {
        mScrollingEnabled = scrollingEnabled
    }

    fun setDisableReorderWhenDragging(disableReorder: Boolean) {
        mDisableReorderWhenDragging = disableReorder
    }

    fun setDropTargetDrawables(backgroundDrawable: Drawable?, foregroundDrawable: Drawable?) {
        mDropTargetBackgroundDrawable = backgroundDrawable
        mDropTargetForegroundDrawable = foregroundDrawable
    }

    fun setDragItemListener(listener: DragItemListener?) {
        mListener = listener
    }

    fun setDragItemCallback(callback: DragItemCallback?) {
        mDragCallback = callback
    }

    fun setDragItem(dragItem: DragItem?) {
        mDragItem = dragItem
    }

    val isDragging: Boolean
        get() = mDragState != DragState.DRAG_ENDED

    override fun setClipToPadding(clipToPadding: Boolean) {
        super.setClipToPadding(clipToPadding)
        mClipToPadding = clipToPadding
    }

    override fun setAdapter(adapter: Adapter<*>?) {
        if (!isInEditMode) {
            if (adapter !is DragItemAdapter<*, *>) {
                throw RuntimeException("Adapter must extend DragItemAdapter")
            }
            if (!adapter.hasStableIds()) {
                throw RuntimeException("Adapter must have stable ids")
            }
        }
        super.setAdapter(adapter)
        mAdapter = adapter as DragItemAdapter<Any, DragItemAdapter.ViewHolder>
    }

    override fun swapAdapter(adapter: Adapter<*>?, r: Boolean) {
        if (!isInEditMode) {
            if (adapter !is DragItemAdapter<*, *>) {
                throw RuntimeException("Adapter must extend DragItemAdapter")
            }
            if (!adapter.hasStableIds()) {
                throw RuntimeException("Adapter must have stable ids")
            }
        }
        super.swapAdapter(adapter, r)
        mAdapter = adapter as DragItemAdapter<Any, DragItemAdapter.ViewHolder>
    }

    override fun setLayoutManager(layout: LayoutManager?) {
        super.setLayoutManager(layout)
        if (layout !is LinearLayoutManager) {
            throw RuntimeException("Layout must be an instance of LinearLayoutManager")
        }
    }

    override fun onAutoScrollPositionBy(dx: Int, dy: Int) {
        if (isDragging) {
            scrollBy(dx, dy)
            updateDragPositionAndScroll()
        } else {
            mAutoScroller!!.stopAutoScroll()
        }
    }

    override fun onAutoScrollColumnBy(columns: Int) {}

    /**
     * Returns the child view under the specific x,y coordinate.
     * This method will take margins of the child into account when finding it.
     */
    fun findChildView(x: Float, y: Float): View? {
        val count = childCount
        if (y <= 0 && count > 0) {
            return getChildAt(0)
        }
        for (i in count - 1 downTo 0) {
            val child = getChildAt(i)
            val params = child.layoutParams as MarginLayoutParams
            if (x >= child.left - params.leftMargin && x <= child.right + params.rightMargin && y >= child.top - params.topMargin && y <= child.bottom + params.bottomMargin) {
                return child
            }
        }
        return null
    }

    private fun shouldChangeItemPosition(newPos: Int): Boolean {
        // Check if drag position is changed and valid and that we are not in a hold position state
        if (mHoldChangePosition || mDragItemPosition == NO_POSITION || mDragItemPosition == newPos) {
            return false
        }
        // If we are not allowed to drag above top or bottom and new pos is 0 or item count then return false
        if (mCanNotDragAboveTop && newPos == 0 || mCanNotDragBelowBottom && newPos == mAdapter!!.itemCount - 1) {
            return false
        }
        // Check with callback if we are allowed to drop at this position
        return if (mDragCallback != null && !mDragCallback!!.canDropItemAtPosition(newPos)) {
            false
        } else true
    }

    private fun updateDragPositionAndScroll() {
        val view = findChildView(mDragItem?.x ?: 0f, mDragItem?.y ?: 0f) ?: return
        var newPos = getChildLayoutPosition(view)
        if (newPos == NO_POSITION) return

        // If using a LinearLayoutManager and the new view has a bigger height we need to check if passing centerY as well.
        // If not doing this extra check the bigger item will move back again when dragging slowly over it.
        val linearLayoutManager = layoutManager is LinearLayoutManager && layoutManager !is GridLayoutManager
        if (linearLayoutManager) {
            val params = view.layoutParams as MarginLayoutParams
            val viewHeight = view.measuredHeight + params.topMargin + params.bottomMargin
            val viewCenterY = view.top - params.topMargin + viewHeight / 2
            val dragDown = mDragItemPosition < getChildLayoutPosition(view)
            val movedPassedCenterY = if (dragDown) mDragItem?.y ?: 0f > viewCenterY else mDragItem?.y ?: 0f < viewCenterY

            // If new height is bigger then current and not passed centerY then reset back to current position
            if (viewHeight > mDragItem?.dragItemView?.measuredHeight ?: 0 && !movedPassedCenterY) {
                newPos = mDragItemPosition
            }
        }
        val layoutManager = layoutManager as LinearLayoutManager?
        if (shouldChangeItemPosition(newPos)) {
            if (mDisableReorderWhenDragging) {
                mAdapter?.dropTargetId = mAdapter!!.getItemId(newPos)
                mAdapter!!.notifyDataSetChanged()
            } else {
                val pos = layoutManager!!.findFirstVisibleItemPosition()
                val posView = layoutManager.findViewByPosition(pos)
                mAdapter!!.changeItemPosition(mDragItemPosition, newPos)
                mDragItemPosition = newPos

                // Since notifyItemMoved scrolls the list we need to scroll back to where we were after the position change.
                if (layoutManager.orientation == LinearLayoutManager.VERTICAL) {
                    val topMargin = (posView!!.layoutParams as MarginLayoutParams).topMargin
                    layoutManager.scrollToPositionWithOffset(pos, posView.top - topMargin)
                } else {
                    val leftMargin = (posView!!.layoutParams as MarginLayoutParams).leftMargin
                    layoutManager.scrollToPositionWithOffset(pos, posView.left - leftMargin)
                }
            }
        }
        var lastItemReached = false
        var firstItemReached = false
        val top = if (mClipToPadding) paddingTop else 0
        val bottom = if (mClipToPadding) height - paddingBottom else height
        val left = if (mClipToPadding) paddingLeft else 0
        val right = if (mClipToPadding) width - paddingRight else width
        val lastChild = findViewHolderForLayoutPosition(mAdapter!!.itemCount - 1)
        val firstChild = findViewHolderForLayoutPosition(0)

        // Check if first or last item has been reached
        if (layoutManager!!.orientation == LinearLayoutManager.VERTICAL) {
            if (lastChild != null && lastChild.itemView.bottom <= bottom) {
                lastItemReached = true
            }
            if (firstChild != null && firstChild.itemView.top >= top) {
                firstItemReached = true
            }
        } else {
            if (lastChild != null && lastChild.itemView.right <= right) {
                lastItemReached = true
            }
            if (firstChild != null && firstChild.itemView.left >= left) {
                firstItemReached = true
            }
        }

        // Start auto scroll if at the edge
        if (layoutManager.orientation == LinearLayoutManager.VERTICAL) {
            if (mDragItem?.y ?: 0f > height - view.height / 2 && !lastItemReached) {
                mAutoScroller!!.startAutoScroll(ScrollDirection.UP)
            } else if (mDragItem?.y ?: 0f < view.height / 2 && !firstItemReached) {
                mAutoScroller!!.startAutoScroll(ScrollDirection.DOWN)
            } else {
                mAutoScroller!!.stopAutoScroll()
            }
        } else {
            if (mDragItem?.x ?: 0f > width - view.width / 2 && !lastItemReached) {
                mAutoScroller!!.startAutoScroll(ScrollDirection.LEFT)
            } else if (mDragItem?.x ?: 0f < view.width / 2 && !firstItemReached) {
                mAutoScroller!!.startAutoScroll(ScrollDirection.RIGHT)
            } else {
                mAutoScroller!!.stopAutoScroll()
            }
        }
    }

    fun startDrag(itemView: View, itemId: Long, x: Float, y: Float): Boolean {
        val dragItemPosition = mAdapter!!.getPositionForItemId(itemId)
        if (!isDragEnabled || mCanNotDragAboveTop && dragItemPosition == 0
                || mCanNotDragBelowBottom && dragItemPosition == mAdapter!!.itemCount - 1) {
            return false
        }
        if (mDragCallback != null && !mDragCallback!!.canDragItemAtPosition(dragItemPosition)) {
            return false
        }

        // If a drag is starting the parent must always be allowed to intercept
        parent.requestDisallowInterceptTouchEvent(false)
        mDragState = DragState.DRAG_STARTED
        dragItemId = itemId
        mDragItem!!.startDrag(itemView, x, y)
        mDragItemPosition = dragItemPosition
        updateDragPositionAndScroll()
        mAdapter!!.setDragItemId(dragItemId)
        mAdapter!!.notifyDataSetChanged()
        if (mListener != null) {
            mListener!!.onDragStarted(mDragItemPosition, mDragItem?.x ?: 0f, mDragItem?.y ?: 0f)
        }
        invalidate()
        return true
    }

    fun onDragging(x: Float, y: Float) {
        if (mDragState == DragState.DRAG_ENDED) {
            return
        }
        mDragState = DragState.DRAGGING
        mDragItemPosition = mAdapter!!.getPositionForItemId(dragItemId)
        mDragItem!!.setPosition(x, y)
        if (!mAutoScroller!!.isAutoScrolling) {
            updateDragPositionAndScroll()
        }
        if (mListener != null) {
            mListener!!.onDragging(mDragItemPosition, x, y)
        }
        invalidate()
    }

    fun onDragEnded() {
        // Need check because sometimes the framework calls drag end twice in a row
        if (mDragState == DragState.DRAG_ENDED) {
            return
        }
        mAutoScroller!!.stopAutoScroll()
        isEnabled = false
        if (mDisableReorderWhenDragging) {
            val newPos = mAdapter!!.getPositionForItemId(mAdapter?.dropTargetId ?: 0)
            if (newPos != NO_POSITION) {
                mAdapter!!.swapItems(mDragItemPosition, newPos)
                mDragItemPosition = newPos
            }
            mAdapter!!.dropTargetId = NO_ID
        }

        // Post so layout is done before we start end animation
        post { // Sometimes the holder will be null if a holder has not yet been set for the position
            val holder = findViewHolderForAdapterPosition(mDragItemPosition)
            if (holder != null) {
                if (itemAnimator != null) {
                    itemAnimator!!.endAnimation(holder)
                }
                mDragItem!!.endDrag(holder.itemView, object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        holder.itemView.alpha = 1f
                        onDragItemAnimationEnd()
                    }
                })
            } else {
                onDragItemAnimationEnd()
            }
        }
    }

    private fun onDragItemAnimationEnd() {
        mAdapter!!.setDragItemId(NO_ID)
        mAdapter!!.dropTargetId = NO_ID
        mAdapter!!.notifyDataSetChanged()
        mDragState = DragState.DRAG_ENDED
        if (mListener != null) {
            mListener!!.onDragEnded(mDragItemPosition)
        }
        dragItemId = NO_ID
        mDragItem!!.hide()
        isEnabled = true
        invalidate()
    }

    fun getDragPositionForY(y: Float): Int {
        val child = findChildView(0f, y)
        var pos: Int
        pos = if (child == null && childCount > 0) {
            // If child is null and child count is not 0 it means that an item was
            // dragged below the last item in the list, then put it after that item
            getChildLayoutPosition(getChildAt(childCount - 1)) + 1
        } else {
            getChildLayoutPosition(child!!)
        }

        // If pos is NO_POSITION it means that the child has not been laid out yet,
        // this only happens for pos 0 as far as I know
        if (pos == NO_POSITION) {
            pos = 0
        }
        return pos
    }

    fun addDragItemAndStart(y: Float, item: Any?, itemId: Long) {
        val pos = getDragPositionForY(y)
        mDragState = DragState.DRAG_STARTED
        dragItemId = itemId
        mAdapter!!.setDragItemId(dragItemId)
        mAdapter!!.addItem(pos, item)
        mDragItemPosition = pos
        mHoldChangePosition = true
        postDelayed({ mHoldChangePosition = false }, itemAnimator!!.moveDuration)
        invalidate()
    }

    fun removeDragItemAndEnd(): Any? {
        if (mDragItemPosition == NO_POSITION) {
            return null
        }
        mAutoScroller!!.stopAutoScroll()
        val item = mAdapter!!.removeItem(mDragItemPosition)
        mAdapter!!.setDragItemId(NO_ID)
        mDragState = DragState.DRAG_ENDED
        dragItemId = NO_ID
        invalidate()
        return item
    }
}