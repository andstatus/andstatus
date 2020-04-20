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

package org.andstatus.app.note;

import android.database.Cursor;
import android.net.Uri;

import androidx.annotation.NonNull;

import org.andstatus.app.context.MyContext;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.ProjectionMap;
import org.andstatus.app.data.SqlIds;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.Audience;
import org.andstatus.app.net.social.Visibility;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.timeline.meta.TimelineType;

/**
 * @author yvolk@yurivolkov.com
 */
public class PrivateNotesConversationLoader extends ConversationLoader {
    public PrivateNotesConversationLoader(ConversationViewItem emptyItem, MyContext myContext, Origin origin,
                                          long selectedNoteId, boolean sync) {
        super(emptyItem, myContext, origin, selectedNoteId, sync);
    }

    @Override
    protected void load2(ConversationViewItem nonLoaded) {
        long actorId = MyQuery.noteIdToLongColumnValue(ActivityTable.ACTOR_ID, nonLoaded.getNoteId());
        Audience audience = Audience.fromNoteId(ma.getOrigin(), nonLoaded.getNoteId());
        String selection = getSelectionForActorAndAudience("=" + actorId,
                SqlIds.actorIdsOf(audience.getNonSpecialActors()).getSql());
        Uri uri = myContext.timelines().get(TimelineType.EVERYTHING, Actor.EMPTY, ma.getOrigin()).getUri();
        try (Cursor cursor = myContext.context().getContentResolver()
                .query(uri, nonLoaded.getProjection().toArray(new String[]{}), selection, null, null)) {
            while (cursor != null && cursor.moveToNext()) {
                addItemToList(nonLoaded.fromCursor(myContext, cursor));
            }
        }
    }

    @NonNull
    // TODO: Actually this is not exactly what we need, because we don't check recipients
    private String getSelectionForActorAndAudience(String actor, String audienceIds) {
        return "(" + NoteTable.VISIBILITY + "=" + Visibility.PRIVATE.id
                + " AND (" + ProjectionMap.ACTIVITY_TABLE_ALIAS + "." + ActivityTable.ACTOR_ID + actor
                + " OR " + ProjectionMap.ACTIVITY_TABLE_ALIAS + "." + ActivityTable.ACTOR_ID + audienceIds + "))";
    }

}
