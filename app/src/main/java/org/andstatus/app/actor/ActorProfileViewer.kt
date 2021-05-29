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
package org.andstatus.app.actor

import android.view.View
import org.andstatus.app.R
import org.andstatus.app.note.NoteContextMenuContainer
import org.andstatus.app.timeline.LoadableListActivity
import org.andstatus.app.util.MyUrlSpan
import org.andstatus.app.util.RelativeTime
import org.andstatus.app.util.ViewUtils
import org.andstatus.app.view.MyContextMenu

class ActorProfileViewer(container: NoteContextMenuContainer) {
    val contextMenu: ActorContextMenu
    private val populator: ActorViewItemPopulator
    private val profileView: View
    private fun getActivity(): LoadableListActivity<*> {
        return contextMenu.getActivity()
    }

    private fun setContextMenuTo(viewId: Int) {
        val view = profileView.findViewById<View?>(viewId)
        view.setOnCreateContextMenuListener(contextMenu)
        view.setOnClickListener { obj: View -> obj.showContextMenu() }
    }

    fun ensureView(added: Boolean) {
        val listView = getActivity().listView ?: return
        if ( (listView.findViewById<View?>(R.id.actor_profile_wrapper) == null) xor added) return
        if (added) {
            listView.addHeaderView(profileView)
        } else {
            listView.removeHeaderView(profileView)
        }
    }

    fun populateView() {
        val item = getActivity().getListData().getActorViewItem()
        populator.populateView(profileView, item, 0)
        showOrigin(item)
        MyUrlSpan.showText(profileView, R.id.profileAge, RelativeTime.getDifference(
                contextMenu.getMyContext().context(), item.actor.getUpdatedDate()), false, false)
    }

    private fun showOrigin(item: ActorViewItem) {
        MyUrlSpan.showText(profileView, R.id.selectProfileOriginButton, item.actor.origin.name, false, true)
        val origins = item.actor.user.knownInOrigins(contextMenu.getMyContext())
        ViewUtils.showView(profileView, R.id.selectProfileOriginDropDown, origins.size > 1)
    }

    init {
        contextMenu = ActorContextMenu(container, MyContextMenu.MENU_GROUP_ACTOR_PROFILE)
        populator = ActorViewItemPopulator(getActivity(), false, true)
        profileView = View.inflate(getActivity(), R.layout.actor_profile, null)
        setContextMenuTo(R.id.actor_profile_wrapper)
    }
}
