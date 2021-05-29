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
        val widthStored = MeasureSpec.getSize(widthMeasureSpecStored)
        val heightStored = MeasureSpec.getSize(heightMeasureSpecStored)
        if (heightLocked && widthStored > 0 && heightStored > 0) {
            saveMeasureSpec("1", widthMeasureSpecStored, heightMeasureSpecStored)
            setMeasuredDimension(widthStored, heightStored)
            return
        }
        if (referencedView == null) {
            referencedView = (parent.parent as View).findViewById(R.id.note_body)
        }
        if (referencedView == null) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            saveMeasureSpec("2",
                    MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.AT_MOST)
            )
            return
        }
        val maxWidth = getDisplayWidth()
        val maxHeight = Math.floor (MAX_ATTACHED_IMAGE_PART * getDisplayHeight()).toInt()
        var width = referencedView?.measuredWidth ?: 0
        var height = if (width == 0) 0 else Math.floor((width * getDrawableHeightToWidthRatio()).toDouble()).toInt()
        logMeasures(method, width, widthMeasureSpec, height)
        var heightMode = MeasureSpec.EXACTLY
        if (width <= 0 || width > maxWidth) {
            width = maxWidth
        }
        if (height <= 0 || height > maxHeight) {
            height = maxHeight
            heightMode = MeasureSpec.AT_MOST
        }
        layoutParams.height = height
        val widthSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
        val heightSpec = MeasureSpec.makeMeasureSpec(height, heightMode)
        saveMeasureSpec("3", widthSpec, heightSpec)
        setMeasuredDimension(width, height)
    }

    private fun logMeasures(method: String, refWidth: Int?, widthMeasureSpec: Int, height: Int) {
        if (isInEditMode || !MyLog.isVerboseEnabled()) {
            return
        }
        // We need to catch an error here in order to work in Android Editor preview
        try {
            MyLog.v(this
            ) {
                (method + ";"
                        + ", width=" + MeasureSpec.toString(widthMeasureSpec)
                        + (if (refWidth == null) "" else ", refWidth=$refWidth")
                        + ", height=$height "
                        + (if (heightLocked) "locked" else "")
                        )
            }
        } catch (e: Exception) {
            Log.i(AttachedImageView::class.java.simpleName, "$method; MyLog class was not found", e)
        }
    }

    private fun saveMeasureSpec(breadCrumb: String, widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val method = "$breadCrumb-saveMeasure"
        val height = MeasureSpec.getSize(heightMeasureSpec)
        logMeasures(method, null, widthMeasureSpec, height)
        if (!heightLocked) {
            widthMeasureSpecStored = MeasureSpec.makeMeasureSpec(
                    MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.AT_MOST)
            heightMeasureSpecStored = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        }
    }

    fun getDisplayWidth(): Int {
        return ImageCaches.getDisplaySize(context).x
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
