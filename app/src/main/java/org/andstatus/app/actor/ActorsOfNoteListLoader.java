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

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.Audience;
import org.andstatus.app.origin.Origin;

/**
 * @author yvolk@yurivolkov.com
 */
public class ActorsOfNoteListLoader extends ActorListLoader {
    private final long selectedNoteId;
    private final Origin originOfSelectedNote;
    final String noteContent;
    private boolean mentionedOnly = false;

    public ActorsOfNoteListLoader(MyContext myContext, ActorListType actorListType, MyAccount ma, long centralItemId,
                                  String searchQuery) {
        super(myContext, actorListType, ma, ma.getOrigin(), centralItemId, searchQuery);

        selectedNoteId = centralItemId;
        noteContent = MyQuery.noteIdToStringColumnValue(NoteTable.CONTENT, selectedNoteId);
        originOfSelectedNote = MyContextHolder.get().origins().fromId(
                MyQuery.noteIdToOriginId(selectedNoteId));
    }

    public ActorsOfNoteListLoader setMentionedOnly(boolean mentionedOnly) {
        this.mentionedOnly = mentionedOnly;
        return this;
    }

    @Override
    protected void loadInternal() {
        addFromNoteRow();
        if (!mentionedOnly) {
            super.loadInternal();
        }
    }

    private void addFromNoteRow() {
        if (!mentionedOnly) {
            addActorIdToList(originOfSelectedNote,
                    MyQuery.noteIdToLongColumnValue(NoteTable.AUTHOR_ID, selectedNoteId));
            addActorIdToList(originOfSelectedNote,
                    MyQuery.noteIdToLongColumnValue(ActivityTable.ACTOR_ID, selectedNoteId));
        }
        Audience.fromNoteId(originOfSelectedNote, selectedNoteId).getActors().forEach(this::addActorToList);
        if (!mentionedOnly) {
            addRebloggers();
        }
    }

    private void addRebloggers() {
        for (Actor reblogger : MyQuery.getRebloggers(
                MyContextHolder.get().getDatabase(), origin, selectedNoteId)) {
            addActorToList(reblogger);
        }
    }

    @Override
    protected String getSubtitle() {
        return noteContent;
    }
}
