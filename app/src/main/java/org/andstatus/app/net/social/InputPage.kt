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

import org.andstatus.app.net.social.TimelinePosition
import org.andstatus.app.util.HasEmpty

abstract class InputPage<T> protected constructor(jsonCollection: AJsonCollection,
                                                  val items: MutableList<T>) : HasEmpty<T> {
    val firstPosition: TimelinePosition = TimelinePosition.Companion.of(jsonCollection.firstPage.getId())
    val youngerPosition: TimelinePosition = TimelinePosition.Companion.of(jsonCollection.prevPage.getId())
    val thisPosition: TimelinePosition = TimelinePosition.Companion.of(jsonCollection.getId())
    val olderPosition: TimelinePosition = TimelinePosition.Companion.of(jsonCollection.nextPage.getId())

    override val isEmpty: Boolean = jsonCollection.isEmpty

    operator fun get(ind: Int): T? {
        return if (ind < 0 || ind > items.size) empty() else items.get(ind)
    }

    fun size(): Int {
        return items.size
    }

    override fun toString(): String {
        return items.toString()
    }
                                                  }