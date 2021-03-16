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
package org.andstatus.app.origin

import androidx.annotation.StringRes
import org.andstatus.app.R
import org.andstatus.app.context.MyContext

class OriginMastodon internal constructor(myContext: MyContext, originType: OriginType) : Origin(myContext, originType) {
    override fun alternativeTermForResourceId(@StringRes resId: Int): Int {
        return when (resId) {
            R.string.label_host -> R.string.label_host_mastodon
            R.string.host_hint -> R.string.host_hint_mastodon
            else -> resId
        }
    }
}