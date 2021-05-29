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

import android.graphics.drawable.Drawable
import io.vavr.control.Try
import org.andstatus.app.graphics.CacheName
import org.andstatus.app.graphics.CachedImage
import org.andstatus.app.graphics.ImageCaches
import org.andstatus.app.util.TryUtils

class DrawableLoader(mediaFile: MediaFile, private val cacheName: CacheName) :
        AbstractImageLoader(mediaFile, "-asynd") {
    fun load(): Try<Drawable> {
        return TryUtils.ofNullable(ImageCaches.loadAndGetImage(cacheName, mediaFile))
                .map { obj: CachedImage -> obj.getDrawable() }
    }
}
