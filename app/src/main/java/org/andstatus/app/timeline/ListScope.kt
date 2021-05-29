/*
 * Copyright (C) 2018 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.timeline

import org.andstatus.app.R
import org.andstatus.app.context.MyContext

enum class ListScope(private val timelinePrepositionResId: Int) {
    ORIGIN(R.string.combined_timeline_off_origin), USER(R.string.combined_timeline_off_account), ACTOR_AT_ORIGIN(0);

    fun timelinePreposition(myContext: MyContext?): CharSequence? {
        return if (myContext == null || myContext.context() == null || timelinePrepositionResId == 0) "" else myContext.context().getText(timelinePrepositionResId)
    }
}
