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
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.database.table.MsgTable;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.origin.Origin;

import java.util.List;

/**
 * @author yvolk@yurivolkov.com
 */
public class ActorsOfNoteListLoader extends ActorListLoader {
    private final long selectedMessageId;
    private final Origin originOfSelectedMessage;
    final String messageBody;
    private boolean mentionedOnly = false;

    public ActorsOfNoteListLoader(ActorListType actorListType, MyAccount ma, long centralItemId, String searchQuery) {
        super(actorListType, ma, ma.getOrigin(), centralItemId, searchQuery);

        selectedMessageId = centralItemId;
        messageBody = MyQuery.msgIdToStringColumnValue(MsgTable.BODY, selectedMessageId);
        originOfSelectedMessage = MyContextHolder.get().persistentOrigins().fromId(
                MyQuery.msgIdToOriginId(selectedMessageId));
    }

    public ActorsOfNoteListLoader setMentionedOnly(boolean mentionedOnly) {
        this.mentionedOnly = mentionedOnly;
        return this;
    }

    @Override
    protected void loadInternal() {
        addFromMessageRow();
        if (!mentionedOnly) {
            super.loadInternal();
        }
    }

    private void addFromMessageRow() {
        final long authorId = MyQuery.msgIdToLongColumnValue(MsgTable.AUTHOR_ID, selectedMessageId);
        if (mentionedOnly) {
            addUsersFromMessageBody(Actor.fromOriginAndActorId(originOfSelectedMessage, authorId));
        } else {
            Actor author = addActorIdToList(originOfSelectedMessage, authorId).actor;
            addActorIdToList(originOfSelectedMessage,
                    MyQuery.msgIdToLongColumnValue(ActivityTable.ACTOR_ID, selectedMessageId));
            addActorIdToList(originOfSelectedMessage,
                    MyQuery.msgIdToLongColumnValue(MsgTable.IN_REPLY_TO_USER_ID, selectedMessageId));
            // TODO: Add recipients
            addUsersFromMessageBody(author);
            addRebloggers();
        }
    }

    private void addUsersFromMessageBody(Actor author) {
        List<Actor> actors = author.extractActorsFromBodyText(messageBody, false);
        for (Actor actor: actors) {
            addActorToList(ActorViewItem.fromActor(actor));
        }
    }

    private void addRebloggers() {
        for (Actor reblogger : MyQuery.getRebloggers(
                MyContextHolder.get().getDatabase(), origin, selectedMessageId)) {
            addActorIdToList(originOfSelectedMessage, reblogger.actorId);
        }
    }

    @Override
    protected String getTitle() {
        return messageBody;
    }
}
