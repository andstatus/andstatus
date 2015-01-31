/**
 * Copyright (C) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app;

import android.view.Menu;
import android.view.MenuItem;

public enum ContextMenuItem {
    REPLY(1),
    DIRECT_MESSAGE(2),
    FAVORITE(3),
    DESTROY_FAVORITE(4),
    REBLOG(5),
    DESTROY_REBLOG(6),
    DESTROY_STATUS(7),
    SHARE(8),
    SENDER_MESSAGES(9),
    AUTHOR_MESSAGES(10),
    FOLLOW_SENDER(11),
    STOP_FOLLOWING_SENDER(12),
    FOLLOW_AUTHOR(13),
    STOP_FOLLOWING_AUTHOR(14),
    PROFILE(15),
    BLOCK(16),
    ACT_AS_USER(17),
    ACT_AS(18),
    OPEN_MESSAGE_PERMALINK(19),
    VIEW_IMAGE(20),
    OPEN_CONVERSATION(21),
    UNKNOWN(100);

    private final int id;

    ContextMenuItem(int id) {
        this.id = Menu.FIRST + id;
    }

    public static ContextMenuItem fromId(int id) {
        for (ContextMenuItem item : ContextMenuItem.values()) {
            if (item.id == id) {
                return item;
            }
        }
        return UNKNOWN;
    }

    public MenuItem addTo(Menu menu, int order, int titleRes) {
        return menu.add(Menu.NONE, this.id, order, titleRes);
    }

    public MenuItem addTo(Menu menu, int order, CharSequence title) {
        return menu.add(Menu.NONE, this.id, order, title);
    }
}
