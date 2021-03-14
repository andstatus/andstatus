/*
 * Copyright (C) 2020 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.net.social

import io.vavr.control.Try

class InputTimelinePage(jsonCollection: AJsonCollection, activities: MutableList<AActivity>) : InputPage<AActivity>(jsonCollection, activities) {
    override fun empty(): AActivity {
        return AActivity.EMPTY
    }

    companion object {
        val EMPTY: InputTimelinePage = of(mutableListOf())
        val TRY_EMPTY: Try<InputTimelinePage> = Try.success(EMPTY)

        fun of(activities: MutableList<AActivity>): InputTimelinePage {
            return InputTimelinePage(AJsonCollection.EMPTY, activities)
        }

        fun of(jsonCollection: AJsonCollection, activities: MutableList<AActivity>): InputTimelinePage {
            return InputTimelinePage(jsonCollection, activities)
        }
    }
}