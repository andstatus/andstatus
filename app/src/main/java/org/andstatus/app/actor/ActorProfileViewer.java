/*
 * Copyright (C) 2018 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.view.View;
import android.widget.ListView;

import org.andstatus.app.R;
import org.andstatus.app.note.NoteContextMenuContainer;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.timeline.LoadableListActivity;
import org.andstatus.app.util.MyUrlSpan;
import org.andstatus.app.util.RelativeTime;
import org.andstatus.app.util.ViewUtils;
import org.andstatus.app.view.MyContextMenu;

import java.util.List;

public class ActorProfileViewer {
    public final ActorContextMenu contextMenu;
    private final ActorViewItemPopulator populator;
    private final View profileView;

    public ActorProfileViewer(NoteContextMenuContainer container) {
        this.contextMenu = new ActorContextMenu(container, MyContextMenu.MENU_GROUP_ACTOR_PROFILE);
        populator = new ActorViewItemPopulator(getActivity(), false, true);
        profileView = View.inflate(getActivity(), R.layout.actor_profile, null);
        setContextMenuTo(R.id.actor_wrapper);
    }

    private LoadableListActivity getActivity() {
        return contextMenu.getActivity();
    }

    private void setContextMenuTo(int viewId) {
        View view = profileView.findViewById(viewId);
        view.setOnCreateContextMenuListener(contextMenu);
        view.setOnClickListener(View::showContextMenu);
    }

    public void ensureView(boolean added) {
        final ListView listView = getActivity().getListView();
        if (listView == null) return;

        if (listView.findViewById(profileView.getId()) == null ^ added) return;

        if (added) {
            listView.addHeaderView(profileView);
        } else {
            listView.removeHeaderView(profileView);
        }
    }

    public void populateView() {
        final ActorViewItem item = getActivity().getListData().actorViewItem;
        populator.populateView(profileView, item, 0);
        showOrigin(item);
        MyUrlSpan.showText(profileView, R.id.profileAge, RelativeTime.getDifference(
                contextMenu.getMyContext().context(), item.actor.getUpdatedDate()), false, false);
    }

    private void showOrigin(ActorViewItem item) {
        MyUrlSpan.showText(profileView, R.id.selectProfileOriginButton, item.actor.origin.getName(), false, true);
        List<Origin> origins = item.actor.user.knownInOrigins(contextMenu.getMyContext());
        ViewUtils.showView(profileView, R.id.selectProfileOriginDropDown, origins.size() > 1);
    }

}
