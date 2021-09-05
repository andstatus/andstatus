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
package org.andstatus.app.list

import org.andstatus.app.timeline.LoadableListActivity.ProgressPublisher
import java.util.*

/**
 * @author yvolk@yurivolkov.com
 */
abstract class SyncLoader<T>(protected var items: MutableList<T> = ArrayList()) {

    open fun allowLoadingFromInternet() {
        // Empty
    }

    abstract fun load(publisher: ProgressPublisher? = null): SyncLoader<T>

    fun getList(): MutableList<T> {
        return items
    }

    fun getLoaded(beforeLoad: T): T {
        val index = getList().indexOf(beforeLoad)
        return if (index < 0) beforeLoad else getList()[index]
    }

    fun size(): Int {
        return items.size
    }
}
