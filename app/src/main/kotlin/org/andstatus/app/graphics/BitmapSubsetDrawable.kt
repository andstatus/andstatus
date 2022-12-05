/*
 * Copyright (c) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import org.andstatus.app.util.MyLog

/**
 * @author yvolk@yurivolkov.com
 */
class BitmapSubsetDrawable(val bitmap: Bitmap, val srcRect: Rect, val imageId: Long) : Drawable() {

    override fun getIntrinsicWidth(): Int {
        return srcRect.width()
    }

    override fun getIntrinsicHeight(): Int {
        return srcRect.height()
    }

    override fun draw(canvas: Canvas) {
        try {
            canvas.drawBitmap(bitmap, srcRect, bounds, null)
        } catch (e: Exception) {
            MyLog.w(this, "Failed to draw imageId:$imageId, $this", e)
        }
    }

    override fun setAlpha(alpha: Int) {
        // Empty
    }

    override fun setColorFilter(cf: ColorFilter?) {
        // Empty
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int {
        return PixelFormat.OPAQUE
    }
}
