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

import org.andstatus.app.actor.ActorContextMenu
import org.andstatus.app.actor.ActorViewItem
import org.andstatus.app.note.NoteEditorContainer
import org.andstatus.app.view.MyContextMenu

class ActorOfActivityContextMenu(menuContainer: NoteEditorContainer) : ActorContextMenu(menuContainer, MENU_GROUP_ACTOR) {
    override fun getViewItem(activityViewItem: ActivityViewItem): ActorViewItem {
        return activityViewItem.actor
    }
}