/*
 * Copyright (c) 2016-2017 yvolk (Yuri Volkov), http://yurivolkov.com
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
import android.graphics.Point
import android.graphics.Rect
import android.graphics.drawable.Drawable

/**
 * @author yvolk@yurivolkov.com
 */
class CachedImage {
    val id: Long
    private val bitmap: Bitmap
    protected val source: Drawable

    @Volatile
    private var expired = false

    constructor(imageId: Long, bitmap: Bitmap, srcRect: Rect) {
        id = imageId
        this.bitmap = bitmap
        source = BitmapSubsetDrawable(bitmap, srcRect)
    }

    constructor(imageId: Long, drawable: Drawable) {
        id = imageId
        bitmap = EMPTY_BITMAP
        source = drawable
    }

    fun isBitmapRecyclable(): Boolean {
        return !expired && EMPTY_BITMAP != bitmap
    }

    fun getBitmap(): Bitmap {
        return bitmap
    }

    fun isExpired(): Boolean {
        return expired
    }

    fun makeExpired(): CachedImage {
        expired = true
        return this
    }

    fun getDrawable(): Drawable {
        return source
    }

    fun getImageSize(): Point {
        return Point(source.getIntrinsicWidth(), source.getIntrinsicHeight())
    }

    companion object {
        val BITMAP_CONFIG: Bitmap.Config = Bitmap.Config.ARGB_8888
        val EMPTY_RECT: Rect = Rect(0, 0, 0, 0)
        val EMPTY_BITMAP = newBitmap(1)
        val EMPTY = CachedImage(-1, EMPTY_BITMAP, EMPTY_RECT).makeExpired()
        val BROKEN = CachedImage(-2, EMPTY_BITMAP, EMPTY_RECT).makeExpired()

        private fun newBitmap(size: Int): Bitmap {
            return Bitmap.createBitmap(size, size, BITMAP_CONFIG)
        }
    }
}