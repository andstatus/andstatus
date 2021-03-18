package org.andstatus.app.timeline.meta

import android.view.Menu
import org.andstatus.app.list.ContextMenuItem
import org.andstatus.app.service.CommandData
import org.andstatus.app.service.CommandEnum
import org.andstatus.app.service.MyServiceManager
import org.andstatus.app.timeline.TimelineActivity.Companion.startForTimeline
import org.andstatus.app.timeline.WhichPage

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
*/   enum class ManageTimelinesContextMenuItem : ContextMenuItem {
    OPEN_TIMELINE {
        override fun execute(menu: ManageTimelinesContextMenu, viewItem: ManageTimelinesViewItem): Boolean {
            startForTimeline(menu.getActivity().myContext, menu.getActivity(),
                    viewItem.timeline)
            return true
        }
    },
    SYNC_NOW {
        override fun execute(menu: ManageTimelinesContextMenu, viewItem: ManageTimelinesViewItem): Boolean {
            MyServiceManager.sendManualForegroundCommand(
                    CommandData.newTimelineCommand(CommandEnum.GET_TIMELINE, viewItem.timeline))
            return true
        }
    },
    DELETE {
        override fun execute(menu: ManageTimelinesContextMenu, viewItem: ManageTimelinesViewItem): Boolean {
            menu.getActivity().myContext.timelines().delete(viewItem.timeline)
            saveAndShowList(menu)
            return true
        }
    },
    MAKE_DEFAULT {
        override fun execute(menu: ManageTimelinesContextMenu, viewItem: ManageTimelinesViewItem): Boolean {
            menu.getActivity().myContext.timelines().setDefault(viewItem.timeline)
            saveAndShowList(menu)
            return true
        }
    },
    FORGET_SYNC_EVENTS {
        override fun execute(menu: ManageTimelinesContextMenu, viewItem: ManageTimelinesViewItem): Boolean {
            viewItem.timeline.forgetPositionsAndDates()
            viewItem.timeline.resetCounters(true)
            saveAndShowList(menu)
            return true
        }
    },
    UNKNOWN;

    override fun getId(): Int {
        return Menu.FIRST + ordinal + 1
    }

    fun addTo(menu: Menu, order: Int, titleRes: Int) {
        menu.add(Menu.NONE, this.getId(), order, titleRes)
    }

    open fun execute(menu: ManageTimelinesContextMenu, viewItem: ManageTimelinesViewItem): Boolean {
        return false
    }

    companion object {
        private fun saveAndShowList(menu: ManageTimelinesContextMenu) {
            menu.getActivity().myContext.timelines().saveChanged()
                    .thenRun { menu.getActivity().showList(WhichPage.CURRENT) }
        }

        fun fromId(id: Int): ManageTimelinesContextMenuItem {
            for (item in values()) {
                if (item.getId() == id) {
                    return item
                }
            }
            return UNKNOWN
        }
    }
}