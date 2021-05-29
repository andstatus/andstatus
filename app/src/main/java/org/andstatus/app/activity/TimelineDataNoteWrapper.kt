/*
 * Copyright (c) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.activity

import org.andstatus.app.note.NoteViewItem
import org.andstatus.app.timeline.TimelineData

internal class TimelineDataNoteWrapper(listData: TimelineData<ActivityViewItem>) : TimelineDataWrapper<NoteViewItem>(listData) {
    override fun getItem(position: Int): NoteViewItem {
        return listData.getItem(position).noteViewItem
    }

    override fun getPositionById(itemId: Long): Int {
        val position = -1
        if (itemId != 0L) {
            for (ind in 0 until listData.size()) {
                val item = listData.getItem(ind)
                if (item.noteViewItem.getId() == itemId) {
                    return position
                } else if (item.isCollapsed()) {
                    for (child in item.getChildren()) {
                        if (child.noteViewItem.getId() == itemId) {
                            return position
                        }
                    }
                }
            }
        }
        return -1
    }
}
