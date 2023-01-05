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
package org.andstatus.app.actor

import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyContextHolder.Companion.myContextHolder
import org.andstatus.app.data.MyQuery
import org.andstatus.app.database.table.ActivityTable
import org.andstatus.app.database.table.NoteTable
import org.andstatus.app.net.social.Actor
import org.andstatus.app.net.social.Audience.Companion.fromNoteId
import org.andstatus.app.origin.Origin

/**
 * @author yvolk@yurivolkov.com
 */
class ActorsOfNoteLoader(
    myContext: MyContext, actorsScreenType: ActorsScreenType, origin: Origin, private val selectedNoteId: Long,
    searchQuery: String
) : ActorsLoader(myContext, actorsScreenType, origin, 0, searchQuery) {
    private val originOfSelectedNote: Origin
    val noteContent: String?
    override fun loadInternal() {
        addFromNoteRow()
        if (!items.isEmpty()) super.loadInternal()
    }

    private fun addFromNoteRow() {
        addActorIdToList(originOfSelectedNote, MyQuery.noteIdToLongColumnValue(NoteTable.AUTHOR_ID, selectedNoteId))
        addActorIdToList(originOfSelectedNote, MyQuery.noteIdToLongColumnValue(ActivityTable.ACTOR_ID, selectedNoteId))
        fromNoteId(originOfSelectedNote, selectedNoteId).getNonSpecialActors()
            .forEach { actor: Actor -> addActorToList(actor) }
        MyQuery.getRebloggers(myContextHolder.getNow().database, origin, selectedNoteId)
            .forEach { actor: Actor -> addActorToList(actor) }
    }

    override fun getSubtitle(): String? {
        return noteContent
    }

    init {
        noteContent = MyQuery.noteIdToStringColumnValue(NoteTable.CONTENT, selectedNoteId)
        originOfSelectedNote = myContextHolder.getNow().origins.fromId(
            MyQuery.noteIdToOriginId(selectedNoteId)
        )
    }
}
