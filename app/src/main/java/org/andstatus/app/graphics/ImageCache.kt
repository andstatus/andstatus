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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.util.DisplayMetrics
import android.util.LruCache
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.data.MediaFile
import org.andstatus.app.data.MyContentType
import org.andstatus.app.util.MyLog
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.atomic.AtomicLong

/**
 * @author yvolk@yurivolkov.com
 * On LruCache usage read http://developer.android.com/reference/android/util/LruCache.html
 */
class ImageCache(context: Context?, name: CacheName?, maxBitmapHeightWidthIn: Int, requestedCacheSizeIn: Int) : LruCache<String?, CachedImage?>(requestedCacheSizeIn) {
    val name: CacheName?

    @Volatile
    private val requestedCacheSize: Int

    @Volatile
    private val currentCacheSize: Int

    @Volatile
    var maxBitmapHeight = 0

    @Volatile
    var maxBitmapWidth = 0
    val hits: AtomicLong? = AtomicLong()
    val misses: AtomicLong? = AtomicLong()
    val brokenBitmaps: MutableSet<String?>? = ConcurrentSkipListSet()
    val recycledBitmaps: Queue<Bitmap?>?
    val displayMetrics: DisplayMetrics?

    @Volatile
    var rounded = false
    val showImageInimations: Boolean
    override fun resize(maxSize: Int) {
        throw IllegalStateException("Cache cannot be resized")
    }

    private fun newBlankBitmap(): Bitmap? {
        return Bitmap.createBitmap(displayMetrics, maxBitmapWidth,
                maxBitmapHeight, CachedImage.Companion.BITMAP_CONFIG)
    }

    fun getCachedImage(mediaFile: MediaFile?): CachedImage? {
        return getImage(mediaFile, true)
    }

    fun loadAndGetImage(mediaFile: MediaFile?): CachedImage? {
        return getImage(mediaFile, false)
    }

    override fun entryRemoved(evicted: Boolean, key: String?, oldValue: CachedImage?, newValue: CachedImage?) {
        if (oldValue.isBitmapRecyclable()) {
            oldValue.makeExpired()
            recycledBitmaps.add(oldValue.getBitmap())
        }
    }

    private fun getImage(mediaFile: MediaFile?, fromCacheOnly: Boolean): CachedImage? {
        if (mediaFile.getPath().isNullOrEmpty()) {
            return null
        }
        var image = get(mediaFile.getPath())
        if (image != null) {
            hits.incrementAndGet()
        } else if (brokenBitmaps.contains(mediaFile.getPath())) {
            hits.incrementAndGet()
            return CachedImage.Companion.BROKEN
        } else {
            misses.incrementAndGet()
            if (!fromCacheOnly && File(mediaFile.getPath()).exists()) {
                image = loadImage(mediaFile)
                if (image != null) {
                    if (currentCacheSize > 0) {
                        put(mediaFile.getPath(), image)
                    }
                } else {
                    brokenBitmaps.add(mediaFile.getPath())
                }
            }
        }
        return image
    }

    private fun loadImage(mediaFile: MediaFile?): CachedImage? {
        return when (MyContentType.Companion.fromPathOfSavedFile(mediaFile.getPath())) {
            MyContentType.IMAGE, MyContentType.ANIMATED_IMAGE -> {
                if (showImageInimations && Build.VERSION.SDK_INT >= 28) {
                    ImageCacheApi28Helper.animatedFileToCachedImage(this, mediaFile)
                } else imageFileToCachedImage(mediaFile)
            }
            MyContentType.VIDEO -> bitmapToCachedImage(mediaFile, videoFileToBitmap(mediaFile))
            else -> null
        }
    }

    fun imageFileToCachedImage(mediaFile: MediaFile?): CachedImage? {
        return bitmapToCachedImage(mediaFile, imageFileToBitmap(mediaFile))
    }

    fun bitmapToCachedImage(mediaFile: MediaFile?, bitmap: Bitmap?): CachedImage? {
        if (bitmap == null) {
            return null
        }
        val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
        var background = getSuitableRecycledBitmap(srcRect)
        if (background == null) {
            MyLog.w(mediaFile, "No suitable bitmap found to cache "
                    + srcRect.width() + "x" + srcRect.height() + " '" + mediaFile.getPath() + "'")
            return null
        }
        val canvas = Canvas(background)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        try {
            // On Android 8+ this may cause
            //   java.lang.IllegalArgumentException: Software rendering doesn't support hardware bitmaps
            // See https://stackoverflow.com/questions/58314397/java-lang-illegalstateexception-software-rendering-doesnt-support-hardware-bit
            if (rounded) {
                drawRoundedBitmap(canvas, bitmap)
            } else {
                canvas.drawBitmap(bitmap, 0f, 0f, null)
            }
            bitmap.recycle()
        } catch (e: Exception) {
            // TODO: better approach needed... maybe fail?!
            MyLog.w(TAG, "Drawing bitmap of $mediaFile", e)
            recycledBitmaps.add(background)
            background = bitmap
        }
        return CachedImage(mediaFile.getId(), background, srcRect)
    }

    /**
     * The solution is from http://evel.io/2013/07/21/rounded-avatars-in-android/
     */
    private fun drawRoundedBitmap(canvas: Canvas?, bitmap: Bitmap?) {
        val rectF = RectF(0, 0, bitmap.getWidth(), bitmap.getHeight())
        val paint = Paint()
        paint.isAntiAlias = true
        paint.isDither = true
        val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        paint.shader = shader
        canvas.drawOval(rectF, paint)
    }

    private fun getSuitableRecycledBitmap(srcRect: Rect?): Bitmap? {
        return recycledBitmaps.poll()
    }

    private fun imageFileToBitmap(mediaFile: MediaFile?): Bitmap? {
        return try {
            val bitmap: Bitmap?
            val options = calculateScaling(mediaFile, mediaFile.getSize())
            bitmap = if (MyPreferences.isShowDebuggingInfoInUi()) {
                BitmapFactory.decodeFile(mediaFile.getPath(), options)
            } else {
                try {
                    BitmapFactory.decodeFile(mediaFile.getPath(), options)
                } catch (e: OutOfMemoryError) {
                    MyLog.w(mediaFile, getInfo(), e)
                    evictAll()
                    return null
                }
            }
            MyLog.v(mediaFile) {
                (if (bitmap == null) "Failed to load $name's bitmap" else "Loaded " + name + "'s bitmap " + bitmap.width
                        + "x" + bitmap.height) + " '" + mediaFile.getPath() + "' inSampleSize:" + options.inSampleSize
            }
            bitmap
        } catch (e: Exception) {
            MyLog.w(this, "Error loading '" + mediaFile.getPath() + "'", e)
            null
        }
    }

    private fun videoFileToBitmap(mediaFile: MediaFile?): Bitmap? {
        var retriever: MediaMetadataRetriever? = null
        return try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource( MyContextHolder.myContextHolder.getNow().context(), Uri.parse(mediaFile.getPath()))
            val source = retriever.frameAtTime ?: return null
            val options = calculateScaling(mediaFile, mediaFile.getSize())
            val bitmap = ThumbnailUtils.extractThumbnail(source, mediaFile.getSize().x / options.inSampleSize,
                    mediaFile.getSize().y / options.inSampleSize)
            source.recycle()
            MyLog.v(mediaFile) {
                (if (bitmap == null) "Failed to load $name's bitmap" else "Loaded " + name + "'s bitmap " + bitmap.width
                        + "x" + bitmap.height) + " '" + mediaFile.getPath() + "'"
            }
            bitmap
        } catch (e: Exception) {
            MyLog.w(this, "Error loading '" + mediaFile.getPath() + "'", e)
            null
        } finally {
            closeSilently(retriever)
        }
    }

    fun calculateScaling(objTag: Any?, imageSize: Point?): BitmapFactory.Options? {
        val options = BitmapFactory.Options()
        options.inSampleSize = 1
        var x = maxBitmapWidth
        var y = maxBitmapHeight
        while (imageSize.y > y || imageSize.x > x) {
            options.inSampleSize = if (options.inSampleSize < 2) 2 else options.inSampleSize * 2
            x *= 2
            y *= 2
        }
        if (options.inSampleSize > 1 && MyLog.isVerboseEnabled()) {
            MyLog.v(objTag, "Large bitmap " + imageSize.x + "x" + imageSize.y
                    + " scaling by " + options.inSampleSize + " times")
        }
        return options
    }

    fun getInfo(): String? {
        val builder = StringBuilder(name.title)
        builder.append(": " + maxBitmapWidth + "x" + maxBitmapHeight + ", "
                + size() + " of " + currentCacheSize)
        if (requestedCacheSize != currentCacheSize) {
            builder.append(" (initially capacity was $requestedCacheSize)")
        }
        builder.append(", free: " + recycledBitmaps.size)
        if (!brokenBitmaps.isEmpty()) {
            builder.append(", broken: " + brokenBitmaps.size)
        }
        val accesses = hits.get() + misses.get()
        builder.append(", hits:" + hits.get() + ", misses:" + misses.get()
                + if (accesses == 0L) "" else ", hitRate:" + hits.get() * 100 / accesses + "%")
        return builder.toString()
    }

    fun getMaxBitmapWidth(): Int {
        return maxBitmapWidth
    }

    private fun setMaxBounds(x: Int, y: Int) {
        if (x < 1 || y < 1) {
            MyLog.e(this, MyLog.getStackTrace(
                    IllegalArgumentException("setMaxBounds x=$x y=$y"))
            )
        } else {
            maxBitmapWidth = x
            maxBitmapHeight = y
        }
    }

    companion object {
        val TAG: String? = ImageCache::class.java.simpleName
        const val BYTES_PER_PIXEL = 4
    }

    init {
        showImageInimations = MyPreferences.isShowImageAnimations()
        this.name = name
        displayMetrics = context.getResources().displayMetrics
        setMaxBounds(maxBitmapHeightWidthIn, maxBitmapHeightWidthIn)
        requestedCacheSize = requestedCacheSizeIn
        currentCacheSize = requestedCacheSize
        recycledBitmaps = ConcurrentLinkedQueue()
        try {
            for (i in 0 until currentCacheSize + 2) {
                recycledBitmaps.add(newBlankBitmap())
            }
        } catch (e: OutOfMemoryError) {
            MyLog.w(this, getInfo(), e)
            currentCacheSize = recycledBitmaps.size - 2
            if (currentCacheSize < 0) {
                currentCacheSize = 0
            }
            super.resize(currentCacheSize)
        }
    }
}