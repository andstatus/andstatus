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

import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import android.view.MenuItem
import android.view.View
import org.andstatus.app.R
import org.andstatus.app.activity.ActivityViewItem
import org.andstatus.app.note.NoteEditorContainer
import org.andstatus.app.origin.Origin
import org.andstatus.app.timeline.ContextMenuHeader
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.StringUtil
import org.andstatus.app.view.MyContextMenu

open class ActorContextMenu(val menuContainer: NoteEditorContainer, menuGroup: Int) : MyContextMenu(menuContainer.getActivity(), menuGroup) {

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenuInfo?) {
        val method = "onCreateContextMenu"
        saveContextOfSelectedItem(v)
        if (getViewItem().isEmpty) {
            return
        }
        val actor = getViewItem().actor
        if (!getMyContext().accounts.succeededForSameUser(actor).contains(getSelectedActingAccount())) {
            setSelectedActingAccount(
                    getMyContext().accounts.firstOtherSucceededForSameUser(actor, getActingAccount())
            )
        }
        var order = 0
        try {
            ContextMenuHeader(getActivity(), menu)
                    .setTitle(actor.toActorTitle())
                    .setSubtitle(getActingAccount().getAccountName())
            val shortName = actor.getUsername()
            if (actor.groupType.isGroupLike) {
                ActorContextMenuItem.GROUP_NOTES.addTo(menu, menuGroup, order++,
                        StringUtil.format(getActivity(), R.string.group_notes, shortName))
            }
            if (actor.isIdentified()) {
                ActorContextMenuItem.NOTES_BY_ACTOR.addTo(menu, menuGroup, order++,
                        StringUtil.format(getActivity(), R.string.menu_item_user_messages, shortName))
                if (actor.origin.originType.hasListsOfUser) {
                    ActorContextMenuItem.LISTS.addTo(menu, menuGroup, order++,
                        StringUtil.format(getActivity(), R.string.lists_of_user, shortName))
                }
                ActorContextMenuItem.FRIENDS.addTo(menu, menuGroup, order++,
                        StringUtil.format(getActivity(), R.string.friends_of, shortName))
                ActorContextMenuItem.FOLLOWERS.addTo(menu, menuGroup, order++,
                        StringUtil.format(getActivity(), R.string.followers_of, shortName))
                if (getActingAccount().actor.notSameUser(actor)) {
                    if (getActingAccount().isFollowing(actor)) {
                        ActorContextMenuItem.STOP_FOLLOWING.addTo(menu, menuGroup, order++,
                                StringUtil.format(getActivity(), R.string.menu_item_stop_following_user, shortName))
                    } else {
                        ActorContextMenuItem.FOLLOW.addTo(menu, menuGroup, order++,
                                StringUtil.format(getActivity(), R.string.menu_item_follow_user, shortName))
                    }
                    if (menuContainer.getNoteEditor()?.isVisible() == false) {
                        ActorContextMenuItem.POST_TO.addTo(menu, menuGroup, order++,
                                StringUtil.format(getActivity(), R.string.post_to, shortName))
                    }
                }
                when (getMyContext().accounts.succeededForSameUser(actor).size) {
                    0, 1 -> {
                    }
                    2 -> ActorContextMenuItem.ACT_AS_FIRST_OTHER_ACCOUNT.addTo(menu, menuGroup, order++,
                            StringUtil.format(
                                    getActivity(), R.string.menu_item_act_as_user,
                                    getMyContext().accounts
                                            .firstOtherSucceededForSameUser(actor, getActingAccount())
                                            .getShortestUniqueAccountName()))
                    else -> ActorContextMenuItem.ACT_AS.addTo(menu, menuGroup, order++, R.string.menu_item_act_as)
                }
            }
            if (actor.canGetActor()) {
                ActorContextMenuItem.GET_ACTOR.addTo(menu, menuGroup, order++, R.string.get_user)
            }
        } catch (e: Exception) {
            MyLog.i(this, method, e)
        }
    }

    fun onContextItemSelected(item: MenuItem): Boolean {
        val ma = getActingAccount()
        return if (ma.isValid) {
            val contextMenuItem: ActorContextMenuItem = ActorContextMenuItem.fromId(item.getItemId())
            MyLog.v(this) {
                ("onContextItemSelected: " + contextMenuItem + "; account="
                        + ma.getAccountName() + "; actor=" + getViewItem().actor.uniqueName)
            }
            contextMenuItem.execute(this)
        } else {
            false
        }
    }

    fun getViewItem(): ActorViewItem {
        if (mViewItem.isEmpty) {
            return ActorViewItem.EMPTY
        }
        return if (mViewItem is ActivityViewItem) {
            getViewItem(mViewItem as ActivityViewItem)
        } else mViewItem as ActorViewItem
    }

    protected open fun getViewItem(activityViewItem: ActivityViewItem): ActorViewItem {
        return activityViewItem.getObjActorItem()
    }

    fun getOrigin(): Origin {
        return getViewItem().actor.origin
    }
}
