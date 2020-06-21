package org.andstatus.app.timeline.meta;
/*
 * Copyright (c) 2016-2020 yvolk (Yuri Volkov), http://yurivolkov.com
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

import org.andstatus.app.list.ContextMenuItem;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.timeline.TimelineActivity;
import org.andstatus.app.timeline.WhichPage;

public enum ManageTimelinesContextMenuItem implements ContextMenuItem {
    OPEN_TIMELINE() {
        @Override
        public boolean execute(ManageTimelinesContextMenu menu, ManageTimelinesViewItem viewItem) {
            TimelineActivity.startForTimeline(menu.getActivity().getMyContext(), menu.getActivity(),
                    viewItem.timeline);
            return true;
        }
    },
    SYNC_NOW() {
        @Override
        public boolean execute(ManageTimelinesContextMenu menu, ManageTimelinesViewItem viewItem) {
            MyServiceManager.sendManualForegroundCommand(
                    CommandData.newTimelineCommand(CommandEnum.GET_TIMELINE, viewItem.timeline));
            return true;
        }
    },
    DELETE() {
        @Override
        public boolean execute(ManageTimelinesContextMenu menu, ManageTimelinesViewItem viewItem) {
            menu.getActivity().getMyContext().timelines().delete(viewItem.timeline);
            saveAndShowList(menu);
            return true;
        }
    },
    MAKE_DEFAULT() {
        @Override
        public boolean execute(ManageTimelinesContextMenu menu, ManageTimelinesViewItem viewItem) {
            menu.getActivity().getMyContext().timelines().setDefault(viewItem.timeline);
            saveAndShowList(menu);
            return true;
        }
    },
    FORGET_SYNC_EVENTS() {
        @Override
        public boolean execute(ManageTimelinesContextMenu menu, ManageTimelinesViewItem viewItem) {
            viewItem.timeline.forgetPositionsAndDates();
            viewItem.timeline.resetCounters(true);
            saveAndShowList(menu);
            return true;
        }
    },
    UNKNOWN();

    private static void saveAndShowList(ManageTimelinesContextMenu menu) {
        menu.getActivity().getMyContext().timelines().saveChanged()
                .thenRun(() -> menu.getActivity().showList(WhichPage.CURRENT));
    }

    @Override
    public int getId() {
        return Menu.FIRST + ordinal() + 1;
    }

    public static ManageTimelinesContextMenuItem fromId(int id) {
        for (ManageTimelinesContextMenuItem item : ManageTimelinesContextMenuItem.values()) {
            if (item.getId() == id) {
                return item;
            }
        }
        return UNKNOWN;
    }

    public void addTo(Menu menu, int order, int titleRes) {
        menu.add(Menu.NONE, this.getId(), order, titleRes);
    }

    public boolean execute(ManageTimelinesContextMenu menu, ManageTimelinesViewItem viewItem) {
        return false;
    }
}
