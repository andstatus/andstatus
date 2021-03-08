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
package org.andstatus.app.actor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.andstatus.app.R
import org.andstatus.app.timeline.BaseTimelineAdapter
import org.andstatus.app.timeline.TimelineData
import org.andstatus.app.timeline.meta.Timeline

class ActorAdapter : BaseTimelineAdapter<ActorViewItem> {
    private val contextMenu: ActorContextMenu
    private val listItemLayoutId: Int
    val populator: ActorViewItemPopulator

    constructor(contextMenu: ActorContextMenu, listData: TimelineData<ActorViewItem>) : super(contextMenu.getActivity().myContext, listData) {
        this.contextMenu = contextMenu
        listItemLayoutId = R.id.actor_wrapper
        populator = ActorViewItemPopulator(contextMenu.getActivity(), isCombined(), showAvatars)
    }

    internal constructor(contextMenu: ActorContextMenu, listItemLayoutId: Int, items: MutableList<ActorViewItem>,
                         timeline: Timeline) : super(contextMenu.getActivity().myContext, timeline, items) {
        this.contextMenu = contextMenu
        this.listItemLayoutId = listItemLayoutId
        populator = ActorViewItemPopulator(contextMenu.getActivity(), isCombined(), showAvatars)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: newView()
        view.setOnCreateContextMenuListener(contextMenu)
        view.setOnClickListener(this)
        setPosition(view, position)
        val item = getItem(position)
        populator.populateView(view, item, position)
        return view
    }

    private fun newView(): View {
        return LayoutInflater.from(contextMenu.getActivity()).inflate(listItemLayoutId, null)
    }
}