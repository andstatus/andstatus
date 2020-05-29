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

package org.andstatus.app.actor;

import org.andstatus.app.context.MyContext;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.net.social.Audience;
import org.andstatus.app.origin.Origin;

import static org.andstatus.app.context.MyContextHolder.myContextHolder;

/**
 * @author yvolk@yurivolkov.com
 */
public class MentionedActorsListLoader extends ActorListLoader {
    private final long selectedNoteId;
    private final Origin originOfSelectedNote;

    public MentionedActorsListLoader(MyContext myContext, Origin origin, long noteId) {
        super(myContext, ActorListType.ACTORS_OF_NOTE, origin, 0, "");
        selectedNoteId = noteId;
        originOfSelectedNote = myContextHolder.getNow().origins().fromId(
                MyQuery.noteIdToOriginId(selectedNoteId));
    }

    @Override
    protected void loadInternal() {
        Audience.fromNoteId(originOfSelectedNote, selectedNoteId).getNonSpecialActors().forEach(this::addActorToList);
        if (!items.isEmpty()) super.loadInternal();
    }
}
