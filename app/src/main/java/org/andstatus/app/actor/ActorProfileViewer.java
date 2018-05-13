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

import org.andstatus.app.R;
import org.andstatus.app.note.NoteContextMenuContainer;
import org.andstatus.app.timeline.LoadableListActivity;
import org.andstatus.app.view.MyContextMenu;

public class ActorProfileViewer {
    public final ActorContextMenu contextMenu;
    private final ActorViewItemPopulator populator;
    public final View profileView;

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

    public void populateView() {
        populator.populateView(profileView, getActivity().getListData().actorViewItem, 0);
    }

}
