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

import android.app.ActivityManager
import android.content.Context
import android.graphics.Point
import android.view.WindowManager
import org.andstatus.app.context.MyContextHolder.Companion.myContextHolder
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.context.MyTheme
import org.andstatus.app.data.AvatarFile
import org.andstatus.app.data.MediaFile
import org.andstatus.app.util.I18n
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.SharedPreferencesUtil
import org.andstatus.app.util.StopWatch
import java.util.concurrent.ConcurrentHashMap

/**
 * @author yvolk@yurivolkov.com
 */
object ImageCaches {
    private const val ATTACHED_IMAGES_CACHE_PART_OF_TOTAL_APP_MEMORY = 0.20f
    const val ATTACHED_IMAGES_CACHE_SIZE_MIN = 10
    const val ATTACHED_IMAGES_CACHE_SIZE_MAX = 20
    private const val AVATARS_CACHE_PART_OF_TOTAL_APP_MEMORY = 0.05f
    const val AVATARS_CACHE_SIZE_MIN = 200
    const val AVATARS_CACHE_SIZE_MAX = 700

    @Volatile
    private var attachedImagesCache: ImageCache? = null

    @Volatile
    private var avatarsCache: ImageCache? = null

    @Synchronized
    fun initialize(context: Context) {
        val stopWatch: StopWatch = StopWatch.createStarted()
        styledImages.clear()
        initializeAttachedImagesCache(context)
        initializeAvatarsCache(context)
        MyLog.i(ImageCaches::class, "imageCachesInitializedMs:" + stopWatch.time + "; " + getCacheInfo())
    }

    private fun initializeAttachedImagesCache(context: Context) {
        // We assume that current display orientation is preferred, so we use "y" size only
        var imageSize = Math.round(
            AttachedImageView.MAX_ATTACHED_IMAGE_PART *
                getDisplaySize(context).y
        ).toInt()
        var cacheSize = 0
        for (i in 0..4) {
            cacheSize = calcCacheSize(
                context, imageSize,
                ATTACHED_IMAGES_CACHE_PART_OF_TOTAL_APP_MEMORY
            )
            if (cacheSize >= ATTACHED_IMAGES_CACHE_SIZE_MIN || imageSize < 2) {
                break
            }
            imageSize = imageSize * 2 / 3
        }
        if (cacheSize > ATTACHED_IMAGES_CACHE_SIZE_MAX) {
            cacheSize = ATTACHED_IMAGES_CACHE_SIZE_MAX
        }
        attachedImagesCache = ImageCache(
            context, CacheName.ATTACHED_IMAGE, imageSize,
            cacheSize
        )
    }

    private fun initializeAvatarsCache(context: Context) {
        val displayDensity = context.getResources().displayMetrics.density
        var imageSize: Int = Math.round(AvatarFile.AVATAR_SIZE_DIP * displayDensity)
        var cacheSize = 0
        for (i in 0..4) {
            cacheSize = calcCacheSize(context, imageSize, AVATARS_CACHE_PART_OF_TOTAL_APP_MEMORY)
            if (cacheSize >= AVATARS_CACHE_SIZE_MIN || imageSize < 48) {
                break
            }
            imageSize = imageSize * 2 / 3
        }
        if (cacheSize > AVATARS_CACHE_SIZE_MAX) {
            cacheSize = AVATARS_CACHE_SIZE_MAX
        }
        avatarsCache = ImageCache(context, CacheName.AVATAR, imageSize, cacheSize)
        setAvatarsRounded()
    }

    fun setAvatarsRounded() {
        avatarsCache?.evictAll()
        avatarsCache?.rounded = SharedPreferencesUtil.getBoolean(MyPreferences.KEY_ROUNDED_AVATARS, true)
    }

    private fun calcCacheSize(context: Context?, imageSize: Int, partOfAvailableMemory: Float): Int {
        return Math.round(partOfAvailableMemory * getTotalAppMemory(context) / imageSize / imageSize / ImageCache.BYTES_PER_PIXEL)
    }

    private fun getTotalAppMemory(context: Context?): Long {
        var memoryClass = 16
        if (context != null) {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            memoryClass = actManager.memoryClass
        }
        return memoryClass * 1024L * 1024L
    }

    private fun getMemoryInfo(context: Context?): ActivityManager.MemoryInfo {
        val memInfo = ActivityManager.MemoryInfo()
        if (context != null) {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            actManager.getMemoryInfo(memInfo)
        }
        return memInfo
    }

    fun loadAndGetImage(cacheName: CacheName, mediaFile: MediaFile): CachedImage? {
        return getCache(cacheName).loadAndGetImage(mediaFile)
    }

    fun getCachedImage(cacheName: CacheName, mediaFile: MediaFile): CachedImage? {
        return getCache(cacheName).getCachedImage(mediaFile)
    }

    fun getCache(cacheName: CacheName): ImageCache {
        return when (cacheName) {
            CacheName.ATTACHED_IMAGE -> attachedImagesCache ?: throw IllegalStateException("No attached images cache")
            else -> avatarsCache ?: throw IllegalStateException("No avatars cache")
        }
    }

    fun getCacheInfo(): String {
        val builder = StringBuilder("ImageCaches: ")
        if (avatarsCache == null || attachedImagesCache == null) {
            builder.append("not initialized")
        } else {
            builder.append(avatarsCache?.getInfo() + "; ")
            builder.append(attachedImagesCache?.getInfo() + "; ")
            builder.append("Styled images: " + styledImages.size + "; ")
        }
        val myContext = myContextHolder.getNow()
        if (!myContext.isEmpty) {
            val context: Context = myContext.context
            builder.append("Memory. App total: " + I18n.formatBytes(getTotalAppMemory(context)))
            val memInfo = getMemoryInfo(context)
            builder.append(
                "; Device: available " + I18n.formatBytes(memInfo.availMem) + " of "
                    + I18n.formatBytes(memInfo.totalMem)
            )
        }
        return builder.toString()
    }

    /**
     * See http://stackoverflow.com/questions/1016896/how-to-get-screen-dimensions
     */
    fun getDisplaySize(context: Context): Point {
        val size = Point()
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager?
        if (windowManager != null) {
            val display = windowManager.defaultDisplay
            display?.getSize(size)
        }
        if (size.x < 480 || size.y < 480) {
            // This is needed for preview in Layout editor
            size.x = 1280
            size.y = 768
        }
        return size
    }

    fun getImageCompat(context: Context, resourceId: Int): CachedImage {
        return CachedImage(resourceId.toLong(), context.getTheme().getDrawable(resourceId))
    }

    private val styledImages: MutableMap<Int, Array<CachedImage>> = ConcurrentHashMap()

    fun getStyledImage(resourceIdLight: Int, resourceId: Int): CachedImage {
        var styledImage = styledImages[resourceId]
        if (styledImage == null) {
            val myContext = myContextHolder.getNow()
            if (!myContext.isEmpty) {
                val context: Context = myContext.context
                val image = getImageCompat(context, resourceId)
                val imageLight = getImageCompat(context, resourceIdLight)
                styledImage = arrayOf(image, imageLight)
                styledImages[resourceId] = styledImage
            } else {
                return CachedImage.EMPTY
            }
        }
        return styledImage[if (MyTheme.isThemeLight()) 1 else 0]
    }
}
