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
package org.andstatus.app.note

import org.andstatus.app.context.MyContext
import org.andstatus.app.data.MyQuery
import org.andstatus.app.data.ProjectionMap
import org.andstatus.app.data.SqlIds
import org.andstatus.app.database.table.ActivityTable
import org.andstatus.app.database.table.NoteTable
import org.andstatus.app.net.social.Actor
import org.andstatus.app.net.social.Audience
import org.andstatus.app.net.social.Visibility
import org.andstatus.app.origin.Origin
import org.andstatus.app.timeline.meta.TimelineType

/**
 * @author yvolk@yurivolkov.com
 */
class PrivateNotesConversationLoader(emptyItem: ConversationViewItem, myContext: MyContext, origin: Origin,
                                     selectedNoteId: Long, sync: Boolean) :
        ConversationLoader(emptyItem, myContext, origin, selectedNoteId, sync) {

    override fun load2(nonLoaded: ConversationViewItem) {
        val actorId = MyQuery.noteIdToLongColumnValue(ActivityTable.ACTOR_ID, nonLoaded.getNoteId())
        val audience: Audience = Audience.fromNoteId(ma.origin, nonLoaded.getNoteId())
        val selection = getSelectionForActorAndAudience("=$actorId",
                SqlIds.actorIdsOf(audience.getNonSpecialActors()).getSql())
        val uri = myContext.timelines[TimelineType.EVERYTHING, Actor.EMPTY, ma.origin].getUri()
        myContext.context.contentResolver
                .query(uri, nonLoaded.getProjection().toTypedArray(), selection, null, null).use { cursor ->
                    while (cursor != null && cursor.moveToNext()) {
                        addItemToList(nonLoaded.fromCursor(myContext, cursor))
                    }
                }
    }

    // TODO: Actually this is not exactly what we need, because we don't check recipients
    private fun getSelectionForActorAndAudience(actor: String?, audienceIds: String?): String {
        return ("(" + NoteTable.VISIBILITY + "=" + Visibility.PRIVATE.id
                + " AND (" + ProjectionMap.ACTIVITY_TABLE_ALIAS + "." + ActivityTable.ACTOR_ID + actor
                + " OR " + ProjectionMap.ACTIVITY_TABLE_ALIAS + "." + ActivityTable.ACTOR_ID + audienceIds + "))")
    }
}
