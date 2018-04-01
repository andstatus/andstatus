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

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.table.NoteTable;

/**
 * @author yvolk@yurivolkov.com
 */
public class ConversationLoaderFactory<T extends ConversationItem<T>> {

    public ConversationLoader<T> getLoader(T emptyItem, MyContext myContext, MyAccount ma,
            long noteId, boolean sync) {
        // TODO: to clarify...
        boolean recursiveLoader = ma.getOrigin().getOriginType().isPrivateNoteAllowsReply();
        if (!recursiveLoader) {
            recursiveLoader = MyQuery.noteIdToTriState(NoteTable.PUBLIC, noteId).notFalse;
        }
        if (recursiveLoader) {
            return new RecursiveConversationLoader<>(emptyItem, myContext, ma, noteId, sync);
        }  else {
            return new PrivateNotesConversationLoader<>(emptyItem, myContext, ma, noteId, sync);
        }
    }
}
