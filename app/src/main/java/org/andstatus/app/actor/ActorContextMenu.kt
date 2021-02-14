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

open class ActorContextMenu(val menuContainer: NoteEditorContainer?, menuGroup: Int) : MyContextMenu(menuContainer.getActivity(), menuGroup) {
    override fun onCreateContextMenu(menu: ContextMenu?, v: View?, menuInfo: ContextMenuInfo?) {
        val method = "onCreateContextMenu"
        super.onCreateContextMenu(menu, v, menuInfo)
        if (getViewItem().isEmpty) {
            return
        }
        val actor = getViewItem().actor
        if (!myContext.accounts().succeededForSameUser(actor).contains(selectedActingAccount)) {
            selectedActingAccount = myContext.accounts()
                    .firstOtherSucceededForSameUser(actor, actingAccount)
        }
        var order = 0
        try {
            ContextMenuHeader(activity, menu)
                    .setTitle(actor.toActorTitle())
                    .setSubtitle(actingAccount.accountName)
            val shortName = actor.username
            if (actor.groupType.isGroupLike) {
                ActorContextMenuItem.GROUP_NOTES.addTo(menu, menuGroup, order++,
                        StringUtil.format(activity, R.string.group_notes, shortName))
            }
            if (actor.isIdentified) {
                ActorContextMenuItem.NOTES_BY_ACTOR.addTo(menu, menuGroup, order++,
                        StringUtil.format(activity, R.string.menu_item_user_messages, shortName))
                ActorContextMenuItem.FRIENDS.addTo(menu, menuGroup, order++,
                        StringUtil.format(activity, R.string.friends_of, shortName))
                ActorContextMenuItem.FOLLOWERS.addTo(menu, menuGroup, order++,
                        StringUtil.format(activity, R.string.followers_of, shortName))
                if (actingAccount.actor.notSameUser(actor)) {
                    if (actingAccount.isFollowing(actor)) {
                        ActorContextMenuItem.STOP_FOLLOWING.addTo(menu, menuGroup, order++,
                                StringUtil.format(activity, R.string.menu_item_stop_following_user, shortName))
                    } else {
                        ActorContextMenuItem.FOLLOW.addTo(menu, menuGroup, order++,
                                StringUtil.format(activity, R.string.menu_item_follow_user, shortName))
                    }
                    if (!menuContainer.getNoteEditor().isVisible) {
                        ActorContextMenuItem.POST_TO.addTo(menu, menuGroup, order++,
                                StringUtil.format(activity, R.string.post_to, shortName))
                    }
                }
                when (myContext.accounts().succeededForSameUser(actor).size) {
                    0, 1 -> {
                    }
                    2 -> ActorContextMenuItem.ACT_AS_FIRST_OTHER_ACCOUNT.addTo(menu, menuGroup, order++,
                            StringUtil.format(
                                    activity, R.string.menu_item_act_as_user,
                                    myContext.accounts()
                                            .firstOtherSucceededForSameUser(actor, actingAccount)
                                            .shortestUniqueAccountName))
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

    fun onContextItemSelected(item: MenuItem?): Boolean {
        val ma = actingAccount
        return if (ma.isValid) {
            val contextMenuItem: ActorContextMenuItem = ActorContextMenuItem.Companion.fromId(item.getItemId())
            MyLog.v(this) {
                ("onContextItemSelected: " + contextMenuItem + "; account="
                        + ma.accountName + "; actor=" + getViewItem().actor.uniqueName)
            }
            contextMenuItem.execute(this)
        } else {
            false
        }
    }

    fun getViewItem(): ActorViewItem {
        if (mViewItem.isEmpty) {
            return ActorViewItem.Companion.EMPTY
        }
        return if (mViewItem is ActivityViewItem) {
            getViewItem(mViewItem as ActivityViewItem)
        } else mViewItem as ActorViewItem
    }

    protected open fun getViewItem(activityViewItem: ActivityViewItem?): ActorViewItem {
        return activityViewItem.getObjActorItem()
    }

    fun getOrigin(): Origin? {
        return getViewItem().actor.origin
    }
}