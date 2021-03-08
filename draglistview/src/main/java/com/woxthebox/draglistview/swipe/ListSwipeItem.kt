/*
 * Copyright 2017 Magnus Woxblom
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
package com.woxthebox.draglistview.swipe

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.RelativeLayout
import androidx.recyclerview.widget.RecyclerView
import com.woxthebox.draglistview.R
import com.woxthebox.draglistview.swipe.ListSwipeHelper.OnSwipeListener

class ListSwipeItem : RelativeLayout {
    private enum class SwipeState {
        IDLE,  // Item is not moving
        SWIPING,  // Item is moving because the user is swiping with the finger
        ANIMATING // Item is animating
    }

    enum class SwipeDirection {
        LEFT, RIGHT, LEFT_AND_RIGHT, NONE
    }

    enum class SwipeInStyle {
        APPEAR, SLIDE
    }

    private var mLeftView: View? = null
    private var mRightView: View? = null
    private var mSwipeView: View? = null
    private var mViewHolder: RecyclerView.ViewHolder? = null
    private var mSwipeState = SwipeState.IDLE
    private var mSwipeTranslationX = 0f
    private var mStartSwipeTranslationX = 0f
    private var mFlingSpeed = 0f
    var isSwipeStarted = false
        private set
    private var mSwipeViewId = 0
    private var mLeftViewId = 0
    private var mRightViewId = 0
    var supportedSwipeDirection = SwipeDirection.LEFT_AND_RIGHT
    private var mSwipeInStyle = SwipeInStyle.APPEAR

    // Used to report swiped distance to listener. This is will be set at the start of the swipe and reset at the end.
    private var mSwipeListener: OnSwipeListener? = null

    constructor(context: Context?) : super(context) {}
    constructor(context: Context?, attrs: AttributeSet) : super(context, attrs) {
        init(attrs)
    }

    constructor(context: Context?, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(attrs)
    }

    private fun init(attrs: AttributeSet) {
        val a = context.obtainStyledAttributes(attrs, R.styleable.ListSwipeItem)
        mSwipeViewId = a.getResourceId(R.styleable.ListSwipeItem_swipeViewId, -1)
        mLeftViewId = a.getResourceId(R.styleable.ListSwipeItem_leftViewId, -1)
        mRightViewId = a.getResourceId(R.styleable.ListSwipeItem_rightViewId, -1)
        a.recycle()
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        mSwipeView = findViewById(mSwipeViewId)
        mLeftView = findViewById(mLeftViewId)
        mRightView = findViewById(mRightViewId)
        if (mLeftView != null) {
            mLeftView!!.visibility = INVISIBLE
        }
        if (mRightView != null) {
            mRightView!!.visibility = INVISIBLE
        }
    }

    override fun setTag(tag: Any) {
        super.setTag(tag)
        // If view holder is recyclable then reset as this view might be used to another card
        if (mViewHolder != null && mViewHolder!!.isRecyclable) {
            resetSwipe(false)
        }
    }

    fun setSwipeInStyle(style: SwipeInStyle) {
        mSwipeInStyle = style
    }

    fun setSwipeListener(listener: OnSwipeListener?) {
        mSwipeListener = listener
    }

    val swipedDirection: SwipeDirection
        get() {
            if (mSwipeState != SwipeState.IDLE) {
                return SwipeDirection.NONE
            }
            if (mSwipeView!!.translationX == -measuredWidth.toFloat()) {
                return SwipeDirection.LEFT
            } else if (mSwipeView!!.translationX == measuredWidth.toFloat()) {
                return SwipeDirection.RIGHT
            }
            return SwipeDirection.NONE
        }
    val isAnimating: Boolean
        get() = mSwipeState == SwipeState.ANIMATING

    fun setFlingSpeed(speed: Float) {
        mFlingSpeed = speed
    }

    fun swipeTranslationByX(dx: Float) {
        setSwipeTranslationX(mSwipeTranslationX + dx)
    }

    fun setSwipeTranslationX(x: Float) {
        // Based on supported swipe direction reset the x position
        var x = x
        if (supportedSwipeDirection == SwipeDirection.LEFT && x > 0 || supportedSwipeDirection == SwipeDirection.RIGHT && x < 0 || supportedSwipeDirection == SwipeDirection.NONE) {
            x = 0f
        }
        mSwipeTranslationX = x
        mSwipeTranslationX = Math.min(mSwipeTranslationX, measuredWidth.toFloat())
        mSwipeTranslationX = Math.max(mSwipeTranslationX, -measuredWidth.toFloat())
        mSwipeView!!.translationX = mSwipeTranslationX
        if (mSwipeListener != null) {
            mSwipeListener!!.onItemSwiping(this, mSwipeTranslationX)
        }
        if (mSwipeTranslationX < 0) {
            if (mSwipeInStyle == SwipeInStyle.SLIDE) {
                mRightView!!.translationX = measuredWidth + mSwipeTranslationX
            }
            mRightView!!.visibility = VISIBLE
            mLeftView!!.visibility = INVISIBLE
        } else if (mSwipeTranslationX > 0) {
            if (mSwipeInStyle == SwipeInStyle.SLIDE) {
                mLeftView!!.translationX = -measuredWidth + mSwipeTranslationX
            }
            mLeftView!!.visibility = VISIBLE
            mRightView!!.visibility = INVISIBLE
        } else {
            mRightView!!.visibility = INVISIBLE
            mLeftView!!.visibility = INVISIBLE
        }
    }

    fun animateToSwipeTranslationX(x: Float, vararg listeners: Animator.AnimatorListener?) {
        if (x == mSwipeTranslationX) {
            return
        }
        mSwipeState = SwipeState.ANIMATING
        val animator = ObjectAnimator.ofFloat(this, "SwipeTranslationX", mSwipeTranslationX, x)
        animator.duration = 250
        animator.interpolator = DecelerateInterpolator()
        for (listener in listeners) {
            if (listener != null) {
                animator.addListener(listener)
            }
        }
        animator.start()
    }

    fun resetSwipe(animate: Boolean) {
        if (isAnimating || !isSwipeStarted) {
            return
        }
        if (mSwipeTranslationX != 0f) {
            if (animate) {
                animateToSwipeTranslationX(0f, object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        mSwipeState = SwipeState.IDLE
                        mSwipeListener = null
                    }
                })
            } else {
                setSwipeTranslationX(0f)
                mSwipeState = SwipeState.IDLE
                mSwipeListener = null
            }
        } else {
            mSwipeListener = null
        }
        if (mViewHolder != null && !mViewHolder!!.isRecyclable) {
            mViewHolder!!.setIsRecyclable(true)
        }
        mViewHolder = null
        mFlingSpeed = 0f
        mStartSwipeTranslationX = 0f
        isSwipeStarted = false
    }

    fun handleSwipeUp(listener: Animator.AnimatorListener?) {
        if (isAnimating || !isSwipeStarted) {
            return
        }
        val idleListener: AnimatorListenerAdapter = object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                mSwipeState = SwipeState.IDLE
                if (mSwipeTranslationX == 0f) {
                    resetSwipe(false)
                }
                if (mViewHolder != null) {
                    mViewHolder!!.setIsRecyclable(true)
                }
            }
        }
        if (mFlingSpeed == 0f && Math.abs(mStartSwipeTranslationX - mSwipeTranslationX) < measuredWidth / 3) {
            // Bounce back
            animateToSwipeTranslationX(mStartSwipeTranslationX, idleListener, listener)
        } else {
            // Animate to end
            val newX = getTranslateToXPosition(mStartSwipeTranslationX, mSwipeTranslationX, mFlingSpeed)
            animateToSwipeTranslationX(newX, idleListener, listener)
        }
        mStartSwipeTranslationX = 0f
        mFlingSpeed = 0f
    }

    private fun getTranslateToXPosition(startTranslationX: Float, currentTranslationX: Float, flingSpeed: Float): Float {
        return if (flingSpeed == 0f && Math.abs(startTranslationX - currentTranslationX) < measuredWidth / 3) {
            // Bounce back
            startTranslationX
        } else if (currentTranslationX < 0) {
            // Swiping done side
            if (flingSpeed > 0) {
                0
            } else {
                (-measuredWidth).toFloat()
            }
        } else if (startTranslationX == 0f) {
            // Swiping action side from start position
            if (flingSpeed < 0) {
                0
            } else {
                measuredWidth.toFloat()
            }
        } else {
            // Swiping action side from action position
            if (flingSpeed > 0) {
                measuredWidth.toFloat()
            } else {
                0
            }
        }
    }

    fun handleSwipeMoveStarted(listener: OnSwipeListener?) {
        mStartSwipeTranslationX = mSwipeTranslationX
        mSwipeListener = listener
    }

    fun handleSwipeMove(dx: Float, viewHolder: RecyclerView.ViewHolder?) {
        if (isAnimating) {
            return
        }
        mSwipeState = SwipeState.SWIPING
        if (!isSwipeStarted) {
            isSwipeStarted = true
            mViewHolder = viewHolder
            mViewHolder!!.setIsRecyclable(false)
        }
        swipeTranslationByX(dx)
    }
}