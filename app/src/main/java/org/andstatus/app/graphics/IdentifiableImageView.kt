/*
 * Copyright (C) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import org.andstatus.app.util.InstanceId

/**
 * @author yvolk@yurivolkov.com
 */
open class IdentifiableImageView : AppCompatImageView {
    val myViewId = InstanceId.next()

    @Volatile
    private var imageId: Long = 0

    @Volatile
    private var loaded = false

    constructor(context: Context) : super(context) {}
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {}
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {}

    fun getImageId(): Long {
        return imageId
    }

    fun setImageId(imageId: Long) {
        this.imageId = imageId
        loaded = false
    }

    fun isLoaded(): Boolean {
        return loaded
    }

    fun setLoaded() {
        loaded = true
    }

    open fun getCacheName(): CacheName {
        return CacheName.ATTACHED_IMAGE
    }
}