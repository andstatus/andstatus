/*
 * Copyright (c) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app

import android.widget.Spinner
import org.andstatus.app.actor.ActorsScreen
import org.andstatus.app.timeline.TimelineActivity

enum class SearchObjects(private val aClass: Class<*>?) {
    NOTES(TimelineActivity::class.java), ACTORS(ActorsScreen::class.java), GROUPS(ActorsScreen::class.java);

    fun getActivityClass(): Class<*>? {
        return aClass
    }

    companion object {
        fun fromSpinner(spinner: Spinner?): SearchObjects {
            var obj: SearchObjects = NOTES
            if (spinner != null) {
                for (`val` in values()) {
                    if (`val`.ordinal == spinner.selectedItemPosition) {
                        obj = `val`
                        break
                    }
                }
            }
            return obj
        }
    }
}