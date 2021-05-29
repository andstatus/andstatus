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

class InputActorPage(jsonCollection: AJsonCollection, actors: List<Actor>) : InputPage<Actor>(jsonCollection, actors) {
    override fun empty(): Actor {
        return Actor.EMPTY
    }

    companion object {
        val EMPTY: InputActorPage = of(mutableListOf())
        val TRY_EMPTY = Try.success(EMPTY)
        fun of(actors: List<Actor>): InputActorPage {
            return InputActorPage(AJsonCollection.EMPTY, actors)
        }

        fun of(jsonCollection: AJsonCollection, actors: MutableList<Actor>): InputActorPage {
            return InputActorPage(jsonCollection, actors)
        }
    }
}
