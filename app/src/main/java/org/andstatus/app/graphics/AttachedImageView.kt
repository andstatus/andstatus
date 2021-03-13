/*
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.graphics

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.View
import org.andstatus.app.R
import org.andstatus.app.util.MyLog

/**
 * The ImageView auto resizes to the width of the referenced view
 * @author yvolk@yurivolkov.com
 */
class AttachedImageView : IdentifiableImageView {
    private var referencedView: View? = null
    private var heightLocked = false
    private var widthMeasureSpecStored = 0
    private var heightMeasureSpecStored = 0

    constructor(context: Context) : super(context) {}
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {}
    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle) {}

    fun setReferencedView(referencedViewIn: View?) {
        referencedView = referencedViewIn
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val method = "onMeasure"
        if (heightLocked && heightMeasureSpecStored != 0) {
            saveMeasureSpec(widthMeasureSpecStored, heightMeasureSpecStored)
            setMeasuredDimension(widthMeasureSpecStored, heightMeasureSpecStored)
            return
        }
        if (referencedView == null) {
            referencedView = (parent.parent as View).findViewById(R.id.note_body)
        }
        if (referencedView == null) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            saveMeasureSpec(measuredWidthAndState, measuredHeightAndState)
            return
        }
        val refWidthPixels = referencedView?.getMeasuredWidth() ?: 1
        var height = Math.floor((refWidthPixels * getDrawableHeightToWidthRatio()).toDouble()).toInt()
        logIt(method, refWidthPixels, widthMeasureSpec, height.toFloat())
        var mode = MeasureSpec.EXACTLY
        if (height == 0) {
            height = MAX_HEIGHT
            mode = MeasureSpec.AT_MOST
        }
        if (height > MAX_ATTACHED_IMAGE_PART * getDisplayHeight()) {
            height = Math.floor(MAX_ATTACHED_IMAGE_PART
                    * getDisplayHeight()).toInt()
        }
        layoutParams.height = height
        val widthSpec = MeasureSpec.makeMeasureSpec(refWidthPixels, MeasureSpec.EXACTLY)
        val heightSpec = MeasureSpec.makeMeasureSpec(height, mode)
        saveMeasureSpec(widthSpec, heightSpec)
        setMeasuredDimension(widthSpec, heightSpec)
    }

    private fun logIt(method: String, refWidthPixels: Int?, widthMeasureSpec: Int, height: Float) {
        if (isInEditMode || !MyLog.isVerboseEnabled()) {
            return
        }
        // We need to catch an error here in order to work in Android Editor preview
        try {
            MyLog.v(this
            ) {
                (method + ";"
                        + (if (heightLocked) "locked" else "      ")
                        + " height=" + height
                        + ", widthSpec=" + MeasureSpec.toString(widthMeasureSpec)
                        + if (refWidthPixels == null) "" else " refWidth=$refWidthPixels,")
            }
        } catch (e: Exception) {
            Log.i(AttachedImageView::class.java.simpleName, "$method; MyLog class was not found", e)
        }
    }

    private fun saveMeasureSpec(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val method = "onMeasure"
        logIt(method, null, widthMeasureSpec, heightMeasureSpec.toFloat())
        if (!heightLocked) {
            widthMeasureSpecStored = MeasureSpec.makeMeasureSpec(
                    MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.AT_MOST)
            heightMeasureSpecStored = MeasureSpec.makeMeasureSpec(
                    MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.EXACTLY)
        }
    }

    fun getDisplayHeight(): Int {
        return ImageCaches.getDisplaySize(context).y
    }

    private fun getDrawableHeightToWidthRatio(): Float {
        var ratio = 9f / 19f
        if (drawable != null) {
            val width = drawable.intrinsicWidth
            val height = drawable.intrinsicHeight
            if (width > 0 && height > 0) {
                ratio = 1f * height / width
            }
        }
        return ratio
    }

    fun setMeasuresLocked(locked: Boolean) {
        heightLocked = locked
    }

    companion object {
        const val MAX_ATTACHED_IMAGE_PART = 0.75
        private const val MAX_HEIGHT = 2500
    }
}