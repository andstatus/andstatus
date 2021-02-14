/*
 * Copyright (c) 2020 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.annotation.TargetApi
import android.graphics.ImageDecoder
import android.graphics.ImageDecoder.ImageInfo
import android.graphics.drawable.Animatable
import android.graphics.drawable.BitmapDrawable
import android.util.Size
import org.andstatus.app.data.MediaFile
import org.andstatus.app.util.MyLog

/** @author yvolk@yurivolkov.com
 * Methods extracted in a separate class to avoid initialization attempts of absent classes
 */
@TargetApi(28)
object ImageCacheApi28Helper {
    fun animatedFileToCachedImage(imageCache: ImageCache?, mediaFile: MediaFile?): CachedImage? {
        return try {
            val source = ImageDecoder.createSource(mediaFile.downloadFile.file)
            val drawable = ImageDecoder.decodeDrawable(source) { decoder: ImageDecoder?, info: ImageInfo?, source1: ImageDecoder.Source? ->
                // To allow drawing bitmaps on Software canvases
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
                setTargetSize(imageCache, mediaFile, decoder, info.getSize())
            }
            if (drawable is BitmapDrawable) {
                return imageCache.bitmapToCachedImage(mediaFile, (drawable as BitmapDrawable).bitmap)
            }
            if (drawable is Animatable) {
                (drawable as Animatable).start()
            }
            CachedImage(mediaFile.downloadId, drawable)
        } catch (e: Exception) {
            MyLog.i(ImageCache.Companion.TAG, "Failed to decode $mediaFile", e)
            imageCache.imageFileToCachedImage(mediaFile)
        }
    }

    private fun setTargetSize(imageCache: ImageCache?, objTag: Any?, decoder: ImageDecoder?, imageSize: Size?) {
        var width = imageSize.getWidth()
        var height = imageSize.getHeight()
        while (height > imageCache.maxBitmapHeight || width > imageCache.maxBitmapWidth) {
            height = height * 3 / 4
            width = width * 3 / 4
        }
        if (width != imageSize.getWidth()) {
            MyLog.v(objTag, "Large bitmap " + imageSize + " scaled to " + width + "x" + height)
            decoder.setTargetSize(width, height)
        }
    }
}