/*
 * Copyright (C) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.activity;

import android.os.Bundle;
import android.view.MenuItem;

import org.andstatus.app.actor.ActorContextMenu;
import org.andstatus.app.note.NoteContextMenu;
import org.andstatus.app.note.NoteContextMenuContainer;
import org.andstatus.app.view.MyContextMenu;

public class ActivityContextMenu {
    public final ActorOfActivityContextMenu actor;
    public final NoteContextMenu note;
    public final ActorContextMenu objActor;

    public ActivityContextMenu(NoteContextMenuContainer container) {
        actor = new ActorOfActivityContextMenu(container);
        note = new NoteContextMenu(container);
        objActor = new ActorContextMenu(container, MyContextMenu.MENU_GROUP_OBJACTOR);
    }

    public void onContextItemSelected(MenuItem item) {
        switch (item.getGroupId()) {
            case MyContextMenu.MENU_GROUP_ACTOR:
                actor.onContextItemSelected(item);
                break;
            case MyContextMenu.MENU_GROUP_NOTE:
                note.onContextItemSelected(item);
                break;
            case MyContextMenu.MENU_GROUP_OBJACTOR:
                objActor.onContextItemSelected(item);
                break;
            default:
                break;
        }
    }

    public void saveState(Bundle outState) {
        note.saveState(outState);
    }
}
