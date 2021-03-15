/**
 * Copyright (C) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.andstatus.app.note

import android.content.Context
import android.view.View
import android.widget.RelativeLayout
import androidx.appcompat.widget.AppCompatImageView
import org.andstatus.app.graphics.ImageCaches
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.ViewUtils

/**
 * This custom ImageView allows dynamically crop its image according to the height of the other view
 * @author yvolk@yurivolkov.com
 */
class ConversationIndentImageView(contextIn: Context, private val referencedView: View,
                                  private val widthPixels: Int, imageResourceIdLight: Int,
                                  imageResourceId: Int) : AppCompatImageView(contextIn) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val method = "onMeasure"
        val refHeight = ViewUtils.getHeightWithMargins(referencedView)
        MyLog.v(this) {
            method + "; indent=" + widthPixels + ", refHeight=" + refHeight + ", spec=" +
                    MeasureSpec.toString(heightMeasureSpec)
        }
        var mode = MeasureSpec.EXACTLY
        val height: Int
        if (refHeight == 0) {
            height = MAX_HEIGHT
            mode = MeasureSpec.AT_MOST
        } else {
            height = refHeight
            layoutParams.height = height
        }
        val measuredWidth = MeasureSpec.makeMeasureSpec(widthPixels, MeasureSpec.EXACTLY)
        val measuredHeight = MeasureSpec.makeMeasureSpec(height, mode)
        setMeasuredDimension(measuredWidth, measuredHeight)
    }

    companion object {
        private const val MIN_HEIGHT = 80

        /** It's a height of the underlying bitmap (not cropped)  */
        private const val MAX_HEIGHT = 2500
    }

    init {
        val layoutParams = RelativeLayout.LayoutParams(widthPixels, MIN_HEIGHT)
        scaleType = ScaleType.MATRIX
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE)
        setLayoutParams(layoutParams)
        setImageDrawable(ImageCaches.getStyledImage(imageResourceIdLight, imageResourceId).getDrawable())
    }
}