/*
 * Copyright (C) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.timeline.meta;

import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;

import org.andstatus.app.R;
import org.andstatus.app.timeline.ContextMenuHeader;
import org.andstatus.app.timeline.LoadableListActivity;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.view.MyContextMenu;

public class ManageTimelinesContextMenu extends MyContextMenu {

    public ManageTimelinesContextMenu(LoadableListActivity listActivity) {
        super(listActivity, 0);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        final String method = "onCreateContextMenu";
        super.onCreateContextMenu(menu, v, menuInfo);
        if (getViewItem() == null) {
            return;
        }

        int order = 0;
        try {
            new ContextMenuHeader(getActivity(), menu)
                    .setTitle(getViewItem().timelineTitle.title)
                    .setSubtitle(getViewItem().timelineTitle.subTitle);
            ManageTimelinesContextMenuItem.SHOW_MESSAGES.addTo(menu, ++order, R.string.show_timeline_messages);
            if (getViewItem().timeline.isSyncable()) {
                ManageTimelinesContextMenuItem.SYNC_NOW.addTo(menu, ++order, R.string.options_menu_sync);
            }
            if (!getViewItem().timeline.isRequired()) {
                ManageTimelinesContextMenuItem.DELETE.addTo(menu, ++order, R.string.button_delete);
            }
            if (!getMyContext().persistentTimelines().getDefault().equals(getViewItem().timeline)) {
                ManageTimelinesContextMenuItem.MAKE_DEFAULT.addTo(menu, ++order, R.string.set_as_default_timeline);
            }
            ManageTimelinesContextMenuItem.FORGET_SYNC_EVENTS.addTo(menu, ++order, R.string.forget_sync_events);
        } catch (Exception e) {
            MyLog.e(this, method, e);
        }

    }

    public boolean onContextItemSelected(MenuItem item) {
        ManageTimelinesContextMenuItem contextMenuItem = ManageTimelinesContextMenuItem.fromId(item.getItemId());
        MyLog.v(this, "onContextItemSelected: " + contextMenuItem +
                "; timeline=" + getViewItem().timeline);
        return contextMenuItem.execute(this, getViewItem());
    }

    public ManageTimelinesViewItem getViewItem() {
        if (mViewItem == null) {
            return new ManageTimelinesViewItem(listActivity.getMyContext(), Timeline.EMPTY);
        }
        return (ManageTimelinesViewItem) mViewItem;
    }

}
