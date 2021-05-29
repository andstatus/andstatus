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

import android.view.View
import io.vavr.control.Try
import org.andstatus.app.MyActivity
import org.andstatus.app.graphics.AttachedImageView
import org.andstatus.app.graphics.CachedImage
import org.andstatus.app.graphics.IdentifiableImageView
import org.andstatus.app.graphics.ImageCaches
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.TryUtils

class ImageLoader(mediaFile: MediaFile, private val myActivity: MyActivity,
                  private val imageView: IdentifiableImageView) :
        AbstractImageLoader(mediaFile, "-asyn-" + imageView.myViewId) {
    @Volatile
    private var logged = false

    fun load(): Try<CachedImage> {
        return TryUtils.ofNullable(
                if (skip()) null else ImageCaches.loadAndGetImage(imageView.getCacheName(), mediaFile))
    }

    fun set(tryImage: Try<CachedImage>) {
        if (skip()) return
        tryImage.onSuccess { image: CachedImage ->
            if (image.id != mediaFile.id) {
                logResult("Wrong image.id:${image.id} on Set")
                return@onSuccess
            }
            try {
                if (imageView is AttachedImageView) imageView.setMeasuresLocked(true)
                if (mediaFile.isInvestigated) {
                    mediaFile.logResult("Before Set loaded", taskSuffix)
                }
                imageView.setLoaded()
                imageView.setImageDrawable(image.getDrawable())
                imageView.visibility = View.VISIBLE
                logResult("Set loaded")
            } catch (e: Exception) {
                MyLog.d(mediaFile, mediaFile.getMsgLog("Error on setting loaded image", taskSuffix), e)
            }
        }.onFailure { e: Throwable -> logResult("No success on Set loaded") }
    }

    private fun skip(): Boolean {
        if (!myActivity.isMyResumed()) {
            logResult("Skipped not resumed activity")
            return true
        }
        if (imageView.isLoaded()) {
            logResult("Skipped already loaded")
            return true
        }
        if (imageView.getImageId() != mediaFile.id) {
            logResult("Skipped view.imageId:" + imageView.getImageId())
            return true
        }
        return false
    }

    private fun logResult(msgLog: String) {
        if (!logged) {
            logged = true
            mediaFile.logResult(msgLog, taskSuffix)
        }
    }
}
