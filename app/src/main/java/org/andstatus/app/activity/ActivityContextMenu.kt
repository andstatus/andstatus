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
package org.andstatus.app.activity

import android.os.Bundle
import android.view.MenuItem
import org.andstatus.app.actor.ActorContextMenu
import org.andstatus.app.note.NoteContextMenu
import org.andstatus.app.note.NoteContextMenuContainer
import org.andstatus.app.view.MyContextMenu

class ActivityContextMenu(container: NoteContextMenuContainer) {
    val actor: ActorOfActivityContextMenu = ActorOfActivityContextMenu(container)
    val note: NoteContextMenu = NoteContextMenu(container)
    val objActor: ActorContextMenu = ActorContextMenu(container, MyContextMenu.MENU_GROUP_OBJACTOR)

    fun onContextItemSelected(item: MenuItem) {
        when (item.groupId) {
            MyContextMenu.MENU_GROUP_ACTOR -> actor.onContextItemSelected(item)
            MyContextMenu.MENU_GROUP_NOTE -> note.onContextItemSelected(item)
            MyContextMenu.MENU_GROUP_OBJACTOR -> objActor.onContextItemSelected(item)
            else -> {
            }
        }
    }

    fun saveState(outState: Bundle?) {
        note.saveState(outState)
    }

}