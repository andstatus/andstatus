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
package org.andstatus.app.service

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.andstatus.app.R
import org.andstatus.app.net.social.Actor
import org.andstatus.app.origin.Origin
import org.andstatus.app.timeline.BaseTimelineAdapter
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.MyUrlSpan

internal class QueueViewerAdapter(private val container: QueueViewer, items: MutableList<QueueData>) :
        BaseTimelineAdapter<QueueData>(container.myContext,
        container.myContext.timelines[TimelineType.COMMANDS_QUEUE, Actor.EMPTY,  Origin.EMPTY],
        items) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: newView()
        view.setOnCreateContextMenuListener(container)
        view.setOnClickListener(this)
        setPosition(view, position)
        val item = getItem(position)
        MyUrlSpan.Companion.showText(view, R.id.queue_type, item.queueType.acronym, false, false)
        MyUrlSpan.Companion.showText(view, R.id.command_summary, item.commandData.toCommandSummary(myContext)
                + "\t "
                + item.commandData.createdDateWithLabel(myContext.context), false, false)
        MyUrlSpan.Companion.showText(view, R.id.result_summary, item.commandData.getResult().toSummary(), false, false)
        return view
    }

    private fun newView(): View {
        return LayoutInflater.from(container).inflate(R.layout.queue_item, null)
    }
}
