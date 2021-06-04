/*
 * Copyright (c) 2020 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.actor

import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.data.MyQuery
import org.andstatus.app.net.social.Actor
import org.andstatus.app.net.social.Audience.Companion.fromNoteId
import org.andstatus.app.origin.Origin

/**
 * @author yvolk@yurivolkov.com
 */
class MentionedActorsLoader(myContext: MyContext, origin: Origin, private val selectedNoteId: Long) :
        ActorsLoader(myContext, ActorsScreenType.ACTORS_OF_NOTE, origin, 0, "") {
    private val originOfSelectedNote: Origin
    override fun loadInternal() {
        fromNoteId(originOfSelectedNote, selectedNoteId).getNonSpecialActors()
                .forEach { actor: Actor -> addActorToList(actor) }
        if (!items.isEmpty()) super.loadInternal()
    }

    init {
        originOfSelectedNote =  MyContextHolder.myContextHolder.getNow().origins.fromId(
                MyQuery.noteIdToOriginId(selectedNoteId))
    }
}
