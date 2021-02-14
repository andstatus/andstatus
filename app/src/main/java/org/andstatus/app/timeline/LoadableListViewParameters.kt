/*
 * Copyright (C) 2019 yvolk (Yuri Volkov), http://yurivolkov.com
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

import org.andstatus.app.origin.Origin
import org.andstatus.app.util.TriState
import java.util.*

/** Parameters that don't require reloading of the list  */
class LoadableListViewParameters(val collapseDuplicates: TriState?, val collapsedItemId: Long, val preferredOrigin: Optional<Origin?>?) {
    fun isViewChanging(): Boolean {
        return collapseDuplicates.known || preferredOrigin.isPresent()
    }

    companion object {
        val EMPTY: LoadableListViewParameters? = LoadableListViewParameters(TriState.UNKNOWN, 0, Optional.empty())
        fun collapseDuplicates(collapseDuplicates: Boolean): LoadableListViewParameters? {
            return collapseOneDuplicate(collapseDuplicates, 0)
        }

        fun collapseOneDuplicate(collapseDuplicates: Boolean, collapsedItemId: Long): LoadableListViewParameters? {
            return LoadableListViewParameters(TriState.Companion.fromBoolean(collapseDuplicates), collapsedItemId, Optional.empty())
        }

        fun fromOrigin(preferredOrigin: Origin?): LoadableListViewParameters? {
            return LoadableListViewParameters(TriState.UNKNOWN, 0, Optional.ofNullable(preferredOrigin))
        }
    }
}