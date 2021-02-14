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
package org.andstatus.app.timeline

import org.andstatus.app.actor.ActorViewItem
import org.andstatus.app.actor.ActorsLoader

/**
 * @author yvolk@yurivolkov.com
 */
class TimelinePage<T : ViewItem<T?>?>(val params: TimelineParameters, items: MutableList<T?>?) {
    var actorViewItem: ActorViewItem = ActorViewItem.Companion.EMPTY
    private val emptyItem: T?
    val items: MutableList<T?>
    fun getEmptyItem(): T {
        return emptyItem
    }

    fun setLoadedActor(loader: ActorsLoader?) {
        if (params.timeline.timelineType.hasActorProfile()) {
            val index = loader.getList().indexOf(ActorViewItem.Companion.fromActor(params.timeline.actor))
            if (index >= 0) actorViewItem = loader.getList()[index]
        }
    }

    init {
        emptyItem = ViewItem.Companion.getEmpty(params.timelineType)
        this.items = items ?: emptyList()
    }
}