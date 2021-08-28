/*
 * Copyright (c) 2016-2021 yvolk (Yuri Volkov), http://yurivolkov.com
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
import org.andstatus.app.util.IsEmpty

/**
 * @author yvolk@yurivolkov.com
 */
class CachedImage : IsEmpty {
    val id: Long
    val foreignBitmap: Boolean
    @Volatile
    private var bitmap: Bitmap?
    @Volatile
    private var source: Drawable?
    @Volatile
    private var expired = false

    constructor(imageId: Long, bitmap: Bitmap, srcRect: Rect, foreignBitmap: Boolean) {
        id = imageId
        this.foreignBitmap = foreignBitmap
        this.bitmap = bitmap
        source = BitmapSubsetDrawable(bitmap, srcRect, imageId)
    }

    constructor(imageId: Long, drawable: Drawable) {
        id = imageId
        foreignBitmap = false
        bitmap = EMPTY_BITMAP
        source = drawable
    }

    override val isEmpty: Boolean
        get() = (bitmap == null || bitmap === EMPTY_BITMAP) && source == null

    fun isExpired(): Boolean {
        return expired
    }

    fun makeExpired(): Bitmap? {
        val recycledBitmap = if (nonEmpty) bitmap else null
        if (nonEmpty) {
            expired = true
            bitmap = null
            source = null
        }
        return recycledBitmap
    }

    fun getDrawable(): Drawable {
        return source ?: EMPTY.getDrawable()
    }

    fun getImageSize(): Point {
        return source?.let {
            Point(it.getIntrinsicWidth(), it.getIntrinsicHeight())
        }  ?: Point (0, 0)
    }

    companion object {
        val BITMAP_CONFIG: Bitmap.Config = Bitmap.Config.ARGB_8888
        val EMPTY_RECT: Rect = Rect(0, 0, 0, 0)
        val EMPTY_BITMAP = Bitmap.createBitmap(1, 1, BITMAP_CONFIG)
        val EMPTY = CachedImage(-1, EMPTY_BITMAP, EMPTY_RECT, true)
        val BROKEN = CachedImage(-2, EMPTY_BITMAP, EMPTY_RECT, true)
    }
}
