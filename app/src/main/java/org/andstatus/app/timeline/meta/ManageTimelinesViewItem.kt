/*
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
package org.andstatus.app.timeline.meta

import org.andstatus.app.account.MyAccount
import org.andstatus.app.context.MyContext
import org.andstatus.app.timeline.ViewItem

/**
 * @author yvolk@yurivolkov.com
 */
class ManageTimelinesViewItem(myContext: MyContext?, val timeline: Timeline?,
                              accountToHide: MyAccount, namesAreHidden: Boolean) : ViewItem<ManageTimelinesViewItem>(false, timeline.getLastChangedDate()) {
    val timelineTitle: TimelineTitle?
    val countSince: Long
    override fun getId(): Long {
        return timeline?.id ?: 0
    }

    override fun getDate(): Long {
        return timeline?.lastSyncedDate ?: 0
    }

    init {
        timelineTitle = TimelineTitle.Companion.from(myContext, timeline, accountToHide, namesAreHidden,
                TimelineTitle.Destination.DEFAULT)
        countSince = timeline.getCountSince()
    }
}