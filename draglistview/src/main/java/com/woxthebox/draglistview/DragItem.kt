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

import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout

open class DragItem {
    var dragItemView: View
        private set
    var realDragView: View? = null
        private set
    private var mOffsetX = 0f
    private var mOffsetY = 0f
    private var mPosX = 0f
    private var mPosY = 0f
    private var mPosTouchDx = 0f
    private var mPosTouchDy = 0f
    private var mAnimationDx = 0f
    private var mAnimationDy = 0f
    private var mCanDragHorizontally = true
    var isSnapToTouch = true

    internal constructor(context: Context?) {
        dragItemView = View(context)
        hide()
    }

    constructor(context: Context?, layoutId: Int) {
        dragItemView = View.inflate(context, layoutId, null)
        hide()
    }

    open fun onBindDragView(clickedView: View, dragView: View) {
        val bitmap = Bitmap.createBitmap(clickedView.width, clickedView.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        clickedView.draw(canvas)
        dragView.background = BitmapDrawable(clickedView.resources, bitmap)
    }

    fun onMeasureDragView(clickedView: View, dragView: View) {
        dragView.layoutParams = FrameLayout.LayoutParams(clickedView.measuredWidth, clickedView.measuredHeight)
        val widthSpec = View.MeasureSpec.makeMeasureSpec(clickedView.measuredWidth, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(clickedView.measuredHeight, View.MeasureSpec.EXACTLY)
        dragView.measure(widthSpec, heightSpec)
    }

    fun onStartDragAnimation(dragView: View?) {}
    fun onEndDragAnimation(dragView: View?) {}
    fun canDragHorizontally(): Boolean {
        return mCanDragHorizontally
    }

    fun setCanDragHorizontally(canDragHorizontally: Boolean) {
        mCanDragHorizontally = canDragHorizontally
    }

    private fun show() {
        dragItemView.visibility = View.VISIBLE
    }

    fun hide() {
        dragItemView.visibility = View.GONE
        realDragView = null
    }

    val isDragging: Boolean
        get() = dragItemView.visibility === View.VISIBLE

    fun startDrag(startFromView: View, touchX: Float, touchY: Float) {
        show()
        realDragView = startFromView
        onBindDragView(startFromView, dragItemView)
        onMeasureDragView(startFromView, dragItemView)
        onStartDragAnimation(dragItemView)
        val startX = startFromView.x - (dragItemView.measuredWidth - startFromView.measuredWidth) / 2 + dragItemView
                .measuredWidth / 2
        val startY = startFromView.y - (dragItemView.measuredHeight - startFromView.measuredHeight) / 2 + dragItemView
                .measuredHeight / 2
        if (isSnapToTouch) {
            mPosTouchDx = 0f
            mPosTouchDy = 0f
            setPosition(touchX, touchY)
            setAnimationDx(startX - touchX)
            setAnimationDY(startY - touchY)
            val pvhX = PropertyValuesHolder.ofFloat("AnimationDx", mAnimationDx, 0f)
            val pvhY = PropertyValuesHolder.ofFloat("AnimationDY", mAnimationDy, 0f)
            val anim = ObjectAnimator.ofPropertyValuesHolder(this, pvhX, pvhY)
            anim.interpolator = DecelerateInterpolator()
            anim.duration = ANIMATION_DURATION.toLong()
            anim.start()
        } else {
            mPosTouchDx = startX - touchX
            mPosTouchDy = startY - touchY
            setPosition(touchX, touchY)
        }
    }

    fun endDrag(endToView: View?, listener: AnimatorListenerAdapter?) {
        onEndDragAnimation(dragItemView)
        val endX = endToView!!.x - (dragItemView.measuredWidth - endToView.measuredWidth) / 2 + dragItemView
                .measuredWidth / 2
        val endY = endToView.y - (dragItemView.measuredHeight - endToView.measuredHeight) / 2 + dragItemView
                .measuredHeight / 2
        val pvhX = PropertyValuesHolder.ofFloat("X", mPosX, endX)
        val pvhY = PropertyValuesHolder.ofFloat("Y", mPosY, endY)
        val anim = ObjectAnimator.ofPropertyValuesHolder(this, pvhX, pvhY)
        anim.interpolator = DecelerateInterpolator()
        anim.duration = ANIMATION_DURATION.toLong()
        anim.addListener(listener)
        anim.start()
    }

    fun setAnimationDx(x: Float) {
        mAnimationDx = x
        updatePosition()
    }

    fun setAnimationDY(y: Float) {
        mAnimationDy = y
        updatePosition()
    }

    var x: Float
        get() = mPosX
        set(x) {
            mPosX = x
            updatePosition()
        }
    var y: Float
        get() = mPosY
        set(y) {
            mPosY = y
            updatePosition()
        }

    fun setPosition(touchX: Float, touchY: Float) {
        mPosX = touchX + mPosTouchDx
        mPosY = touchY + mPosTouchDy
        updatePosition()
    }

    fun setOffset(offsetX: Float, offsetY: Float) {
        mOffsetX = offsetX
        mOffsetY = offsetY
        updatePosition()
    }

    private fun updatePosition() {
        if (mCanDragHorizontally) {
            dragItemView.x = mPosX + mOffsetX + mAnimationDx - dragItemView.measuredWidth / 2
        }
        dragItemView.y = mPosY + mOffsetY + mAnimationDy - dragItemView.measuredHeight / 2
        dragItemView.invalidate()
    }

    companion object {
        protected const val ANIMATION_DURATION = 250
    }
}