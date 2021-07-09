/*
 * Copyright (C) 2017-2019 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.data

import android.graphics.Point
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import io.vavr.control.CheckedFunction
import io.vavr.control.Try
import org.andstatus.app.MyActivity
import org.andstatus.app.graphics.AttachedImageView
import org.andstatus.app.graphics.CacheName
import org.andstatus.app.graphics.CachedImage
import org.andstatus.app.graphics.IdentifiableImageView
import org.andstatus.app.graphics.ImageCaches
import org.andstatus.app.graphics.MediaMetadata
import org.andstatus.app.graphics.MediaMetadata.Companion.EMPTY
import org.andstatus.app.graphics.MediaMetadata.Companion.fromFilePath
import org.andstatus.app.os.AsyncTaskLauncher
import org.andstatus.app.util.IdentifiableInstance
import org.andstatus.app.util.IsEmpty
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.TryUtils
import java.util.function.Consumer

abstract class MediaFile internal constructor(filename: String,
                                              @Volatile var contentType: MyContentType,
                                              mediaMetadata: MediaMetadata,
                                              val downloadId: Long,
                                              val downloadStatus: DownloadStatus,
                                              val downloadedDate: Long,
                                              override val isEmpty: Boolean = false) : IsEmpty, IdentifiableInstance {
    val downloadFile: DownloadFile = DownloadFile(filename)

    @Volatile
    var mediaMetadata: MediaMetadata = mediaMetadata
        private set

    fun isVideo(): Boolean {
        return contentType == MyContentType.VIDEO
    }

    fun showImage(myActivity: MyActivity, imageView: IdentifiableImageView?) {
        if (imageView == null) return
        imageView.setImageId(id)
        if (!imageMayBeShown()) {
            onNoImage(imageView)
            return
        }
        if (downloadStatus != DownloadStatus.LOADED) {
            showDefaultImage(imageView)
            requestDownload()
            return
        }
        if (imageView is AttachedImageView) {
            imageView.setMeasuresLocked(false)
        }
        val taskSuffix = "-sync-" + imageView.myViewId
        if (downloadFile.existed) {
            val cachedImage = getImageFromCache(imageView.getCacheName())
            if (cachedImage === CachedImage.BROKEN) {
                logResult("Broken cache", taskSuffix)
                onNoImage(imageView)
                return
            } else if (cachedImage != null && !cachedImage.isExpired()) {
                if (isInvestigated) {
                    logResult("Before Set cached", taskSuffix)
                }
                imageView.setLoaded()
                imageView.setImageDrawable(cachedImage.getDrawable())
                imageView.visibility = View.VISIBLE
                logResult("Set cached", taskSuffix)
                return
            }
            // TODO: Trying sync showing...
            val loader = ImageLoader(this, myActivity, imageView)
            loader.set(loader.load())
//            logResult("Show blank", taskSuffix)
//            showBlankImage(imageView)
//            AsyncTaskLauncher.execute(
//                    ImageLoader(this, myActivity, imageView),
//                    { obj: ImageLoader? -> obj?.load() ?: TryUtils.notFound() },
//                    { loader: ImageLoader? -> Consumer { tryImage: Try<CachedImage> -> loader?.set(tryImage) } })
        } else {
            logResult("No image file", taskSuffix)
            onNoImage(imageView)
            requestDownload()
        }
    }

    fun imageMayBeShown(): Boolean {
        return !isEmpty && downloadStatus != DownloadStatus.HARD_ERROR &&
                (downloadStatus != DownloadStatus.LOADED || mediaMetadata.nonEmpty)
    }

    private fun showBlankImage(imageView: ImageView) {
        imageView.setImageDrawable(null)
        imageView.visibility = View.VISIBLE
    }

    private fun showDefaultImage(imageView: IdentifiableImageView) {
        imageView.setLoaded()
        imageView.setImageDrawable(getDefaultImage().getDrawable())
        imageView.visibility = View.VISIBLE
    }

    private fun onNoImage(imageView: IdentifiableImageView) {
        if (isDefaultImageRequired()) {
            showDefaultImage(imageView)
        } else {
            imageView.visibility = View.GONE
        }
    }

    private fun getImageFromCache(cacheName: CacheName): CachedImage? {
        return ImageCaches.getCachedImage(cacheName, this)
    }

    fun preloadImageAsync(cacheName: CacheName) {
        val image = getImageFromCache(cacheName)
        if (image == null && downloadFile.existed) {
            AsyncTaskLauncher.execute(true) { preloadImage(this, cacheName) }
        }
    }

    fun loadAndGetImage(cacheName: CacheName): CachedImage? {
        if (downloadFile.existed) {
            return ImageCaches.loadAndGetImage(cacheName, this)
        }
        requestDownload()
        return null
    }

    fun getMsgLog(msgLog: String?, taskSuffix: String): String {
        return getTaskId(taskSuffix) + "; Id:${id} " + msgLog + " " + downloadFile.getFilePath()
    }

    fun loadDrawable(mapper: CheckedFunction<Drawable?, Drawable?>, uiConsumer: Consumer<Drawable?>) {
        val taskSuffix = "-syncd-" + uiConsumer.hashCode()
        if (downloadStatus != DownloadStatus.LOADED || !downloadFile.existed) {
            logResult("No image file", taskSuffix)
            requestDownload()
            uiConsumer.accept(null)
            return
        }
        val cacheName = if (this is AvatarFile) CacheName.AVATAR else CacheName.ATTACHED_IMAGE
        val cachedImage = getImageFromCache(cacheName)
        if (cachedImage === CachedImage.BROKEN) {
            logResult("Broken", taskSuffix)
            uiConsumer.accept(null)
            return
        } else if (cachedImage != null && !cachedImage.isExpired()) {
            logResult("Set", taskSuffix)
            uiConsumer.accept(cachedImage.getDrawable())
            return
        }
        logResult("Show default", taskSuffix)
        uiConsumer.accept(null)
        AsyncTaskLauncher.execute(DrawableLoader(this, cacheName),
            { loader: DrawableLoader? -> loader?.load()?.map(mapper) ?: TryUtils.notFound() },
            { { drawableTry: Try<Drawable?> -> drawableTry.onSuccess(uiConsumer) } })
    }

    fun logResult(msgLog: String?, taskSuffix: String) {
        MyLog.v(this) { getMsgLog(msgLog, taskSuffix) }
    }

    private fun getTaskId(taskSuffix: String?): String {
        return instanceTag() + "-load" + taskSuffix
    }

    fun getSize(): Point {
        if (mediaMetadata.isEmpty && downloadFile.existed) {
            mediaMetadata = fromFilePath(downloadFile.getFilePath()).getOrElse(EMPTY)
        }
        return mediaMetadata.size()
    }

    override fun toString(): String {
        return if (isEmpty) "EMPTY"
        else instanceTag() + ": id:${id} " + contentType + ", " + mediaMetadata + ", " + downloadFile
    }

    override val instanceId: Long get() = id
    open val id: Long get() = downloadId

    protected abstract fun getDefaultImage(): CachedImage
    protected abstract fun requestDownload()
    protected open fun isDefaultImageRequired(): Boolean {
        return false
    }

    fun getPath(): String {
        return downloadFile.getFilePath()
    }

    val isInvestigated: Boolean get() = MyLog.isDebugEnabled() && id == 315L

    companion object {
        fun preloadImage(mediaFile: MediaFile, cacheName: CacheName) {
            val taskSuffix = "-prel"
            val image = ImageCaches.loadAndGetImage(cacheName, mediaFile)
            when {
                image == null -> {
                    mediaFile.logResult("Failed to preload", taskSuffix)
                }
                image.id != mediaFile.id -> {
                    mediaFile.logResult("Loaded wrong image.id:" + image.id, taskSuffix)
                }
                else -> {
                    mediaFile.logResult("Preloaded", taskSuffix)
                }
            }
        }
    }
}
