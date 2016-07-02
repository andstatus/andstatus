package org.andstatus.app.timeline;/*
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

import android.view.Menu;

import org.andstatus.app.ContextMenuItem;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyServiceManager;

public enum TimelineListContextMenuItem implements ContextMenuItem {
    SYNC_NOW() {
        @Override
        public boolean execute(TimelineListContextMenu timelineListContextMenu, TimelineListViewItem viewItem) {
            MyServiceManager.sendManualForegroundCommand(
                    CommandData.newTimelineCommand(CommandEnum.FETCH_TIMELINE, viewItem.timeline));
            return true;
        }
    },
    UNKNOWN();

    @Override
    public int getId() {
        return Menu.FIRST + ordinal() + 1;
    }

    public static TimelineListContextMenuItem fromId(int id) {
        for (TimelineListContextMenuItem item : TimelineListContextMenuItem.values()) {
            if (item.getId() == id) {
                return item;
            }
        }
        return UNKNOWN;
    }

    public void addTo(Menu menu, int order, int titleRes) {
        menu.add(Menu.NONE, this.getId(), order, titleRes);
    }

    public boolean execute(TimelineListContextMenu timelineListContextMenu, TimelineListViewItem viewItem) {
        return false;
    }
}
