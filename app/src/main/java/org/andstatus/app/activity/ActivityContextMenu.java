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

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.msg.MessageContextMenu;
import org.andstatus.app.msg.MessageListContextMenuContainer;
import org.andstatus.app.user.ActorListContextMenu;
import org.andstatus.app.view.MyContextMenu;

public class ActivityContextMenu {
    public final ActorContextMenu actor;
    public final MessageContextMenu message;
    public final ActorListContextMenu objActor;

    public ActivityContextMenu(MessageListContextMenuContainer container) {
        actor = new ActorContextMenu(container);
        message = new MessageContextMenu(container);
        objActor = new ActorListContextMenu(container, MyContextMenu.MENU_GROUP_OBJACTOR);
    }

    public void onContextItemSelected(MenuItem item) {
        switch (item.getGroupId()) {
            case MyContextMenu.MENU_GROUP_ACTOR:
                actor.onContextItemSelected(item);
                break;
            case MyContextMenu.MENU_GROUP_MESSAGE:
                message.onContextItemSelected(item);
                break;
            case MyContextMenu.MENU_GROUP_OBJACTOR:
                objActor.onContextItemSelected(item);
                break;
            default:
                break;
        }
    }

    public void setMyActor(MyAccount myActor) {
        message.setMyActor(myActor);
    }

    public void saveState(Bundle outState) {
        message.saveState(outState);
    }
}
