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

package org.andstatus.app.actor;

import org.andstatus.app.context.MyContext;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.net.social.Audience;
import org.andstatus.app.origin.Origin;

import static org.andstatus.app.context.MyContextHolder.myContextHolder;

/**
 * @author yvolk@yurivolkov.com
 */
public class ActorsOfNoteLoader extends ActorsLoader {
    private final long selectedNoteId;
    private final Origin originOfSelectedNote;
    final String noteContent;

    public ActorsOfNoteLoader(MyContext myContext, ActorsScreenType actorsScreenType, Origin origin, long noteId,
                              String searchQuery) {
        super(myContext, actorsScreenType, origin, 0, searchQuery);
        selectedNoteId = noteId;
        noteContent = MyQuery.noteIdToStringColumnValue(NoteTable.CONTENT, selectedNoteId);
        originOfSelectedNote = myContextHolder.getNow().origins().fromId(
                MyQuery.noteIdToOriginId(selectedNoteId));
    }

    @Override
    protected void loadInternal() {
        addFromNoteRow();
        if (!items.isEmpty()) super.loadInternal();
    }

    private void addFromNoteRow() {
        addActorIdToList(originOfSelectedNote, MyQuery.noteIdToLongColumnValue(NoteTable.AUTHOR_ID, selectedNoteId));
        addActorIdToList(originOfSelectedNote, MyQuery.noteIdToLongColumnValue(ActivityTable.ACTOR_ID, selectedNoteId));
        Audience.fromNoteId(originOfSelectedNote, selectedNoteId).getNonSpecialActors().forEach(this::addActorToList);
        MyQuery.getRebloggers(myContextHolder.getNow().getDatabase(), origin, selectedNoteId).forEach(this::addActorToList);
    }

    @Override
    protected String getSubtitle() {
        return noteContent;
    }
}
