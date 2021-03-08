/*
 * Copyright (C) 2013-2017 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.note

import android.content.Context
import android.os.Bundle
import android.text.style.URLSpan
import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import android.view.MenuItem
import android.view.View
import android.view.accessibility.AccessibilityManager
import org.andstatus.app.IntentExtra
import org.andstatus.app.R
import org.andstatus.app.account.MyAccount
import org.andstatus.app.activity.ActivityViewItem
import org.andstatus.app.net.social.Actor
import org.andstatus.app.net.social.ApiRoutineEnum
import org.andstatus.app.net.social.Note
import org.andstatus.app.note.FutureNoteContextMenuData.StateForSelectedViewItem
import org.andstatus.app.note.NoteViewItem
import org.andstatus.app.origin.Origin
import org.andstatus.app.timeline.ContextMenuHeader
import org.andstatus.app.timeline.TimelineActivity
import org.andstatus.app.timeline.TimelineActivity.Companion.startForTimeline
import org.andstatus.app.timeline.meta.Timeline
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyUrlSpan
import org.andstatus.app.util.StringUtil
import org.andstatus.app.view.MyContextMenu
import java.util.function.Consumer

/**
 * Context menu and corresponding actions on notes from the list
 * @author yvolk@yurivolkov.com
 */
class NoteContextMenu(val menuContainer: NoteContextMenuContainer) : MyContextMenu(menuContainer.getActivity(), MyContextMenu.Companion.MENU_GROUP_NOTE) {
    @Volatile
    private var futureData: FutureNoteContextMenuData = FutureNoteContextMenuData.EMPTY
    private var selectedMenuItemTitle: String = ""
    override fun onCreateContextMenu(menu: ContextMenu?, v: View?, menuInfo: ContextMenuInfo?) {
        onCreateContextMenu(menu, v, menuInfo, null)
    }

    fun onCreateContextMenu(menu: ContextMenu?, v: View?, menuInfo: ContextMenuInfo?, next: Consumer<NoteContextMenu>) {
        super.onCreateContextMenu(menu, v, menuInfo)
        when (futureData.getStateFor(getViewItem())) {
            StateForSelectedViewItem.READY -> {
                next?.accept(this)
                if (menu != null) {
                    createContextMenu(menu, v, getViewItem())
                }
            }
            StateForSelectedViewItem.NEW -> FutureNoteContextMenuData.Companion.loadAsync(this, v, getViewItem(), next)
            else -> {
            }
        }
    }

    private fun createContextMenu(menu: ContextMenu?, v: View?, viewItem: BaseNoteViewItem<*>?) {
        val method = "createContextMenu"
        val menuData = futureData.menuData
        val noteForAnyAccount = futureData.menuData.noteForAnyAccount
        if (selectedActingAccount.isValid && selectedActingAccount != menuData.myAccount) {
            selectedActingAccount = menuData.myAccount
        }
        if (futureData === FutureNoteContextMenuData.Companion.EMPTY) return
        var order = 0
        try {
            ContextMenuHeader(activity, menu).setTitle(noteForAnyAccount.bodyTrimmed)
                    .setSubtitle(actingAccount.accountName)
            if ((myContext.context().getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager).isTouchExplorationEnabled) {
                addNoteLinksSubmenu(menu, v, order++)
            }
            if (!ConversationActivity::class.java.isAssignableFrom(activity.javaClass)) {
                NoteContextMenuItem.OPEN_CONVERSATION.addTo(menu, order++, R.string.menu_item_open_conversation)
            }
            if (menuContainer.getTimeline().actor.notSameUser(noteForAnyAccount.actor)
                    || menuContainer.getTimeline().timelineType != TimelineType.SENT) {
                // Notes, where an Actor of this note is an Actor ("Sent timeline" of that actor)
                NoteContextMenuItem.NOTES_BY_ACTOR.addTo(menu, order++,
                        StringUtil.format(
                                activity, R.string.menu_item_user_messages,
                                noteForAnyAccount.actor.actorNameInTimeline))
            }
            if (viewItem.isCollapsed()) {
                NoteContextMenuItem.SHOW_DUPLICATES.addTo(menu, order++, R.string.show_duplicates)
            } else if (activity.listData.canBeCollapsed(activity.positionOfContextMenu)) {
                NoteContextMenuItem.COLLAPSE_DUPLICATES.addTo(menu, order++, R.string.collapse_duplicates)
            }
            NoteContextMenuItem.ACTORS_OF_NOTE.addTo(menu, order++, R.string.users_of_message)
            if (menuData.isAuthorSucceededMyAccount && Note.Companion.mayBeEdited(
                            noteForAnyAccount.origin.originType,
                            noteForAnyAccount.status)) {
                NoteContextMenuItem.EDIT.addTo(menu, order++, R.string.menu_item_edit)
            }
            if (noteForAnyAccount.status.mayBeSent()) {
                NoteContextMenuItem.RESEND.addTo(menu, order++, R.string.menu_item_resend)
            }
            if (isEditorVisible()) {
                NoteContextMenuItem.COPY_TEXT.addTo(menu, order++, R.string.menu_item_copy_text)
                NoteContextMenuItem.COPY_AUTHOR.addTo(menu, order++, R.string.menu_item_copy_author)
            }
            if (menuData.myActor.notSameUser(noteForAnyAccount.actor)) {
                if (menuData.actorFollowed) {
                    NoteContextMenuItem.UNDO_FOLLOW_ACTOR.addTo(menu, order++,
                            StringUtil.format(
                                    activity, R.string.menu_item_stop_following_user,
                                    noteForAnyAccount.actor.actorNameInTimeline))
                } else {
                    NoteContextMenuItem.FOLLOW_ACTOR.addTo(menu, order++,
                            StringUtil.format(
                                    activity, R.string.menu_item_follow_user,
                                    noteForAnyAccount.actor.actorNameInTimeline))
                }
            }
            if (noteForAnyAccount.actor.notSameUser(noteForAnyAccount.author)) {
                if (menuContainer.getTimeline().actor.notSameUser(noteForAnyAccount.author)
                        || menuContainer.getTimeline().timelineType != TimelineType.SENT) {
                    // Sent timeline of that actor
                    NoteContextMenuItem.NOTES_BY_AUTHOR.addTo(menu, order++,
                            StringUtil.format(
                                    activity, R.string.menu_item_user_messages,
                                    noteForAnyAccount.author.actorNameInTimeline))
                }
                if (menuData.myActor.notSameUser(noteForAnyAccount.author)) {
                    if (menuData.authorFollowed) {
                        NoteContextMenuItem.UNDO_FOLLOW_AUTHOR.addTo(menu, order++,
                                StringUtil.format(
                                        activity, R.string.menu_item_stop_following_user,
                                        noteForAnyAccount.author.actorNameInTimeline))
                    } else {
                        NoteContextMenuItem.FOLLOW_AUTHOR.addTo(menu, order++,
                                StringUtil.format(
                                        activity, R.string.menu_item_follow_user,
                                        noteForAnyAccount.author.actorNameInTimeline))
                    }
                }
            }
            if (noteForAnyAccount.isLoaded && (!noteForAnyAccount.visibility.isPrivate ||
                            noteForAnyAccount.origin.originType.isPrivateNoteAllowsReply) && !isEditorVisible()) {
                NoteContextMenuItem.REPLY.addTo(menu, order++, R.string.menu_item_reply)
                NoteContextMenuItem.REPLY_TO_CONVERSATION_PARTICIPANTS.addTo(menu, order++,
                        R.string.menu_item_reply_to_conversation_participants)
                NoteContextMenuItem.REPLY_TO_MENTIONED_ACTORS.addTo(menu, order++,
                        R.string.menu_item_reply_to_mentioned_users)
            }
            NoteContextMenuItem.SHARE.addTo(menu, order++, R.string.menu_item_share)
            if (!getAttachedMedia().isEmpty) {
                NoteContextMenuItem.VIEW_MEDIA.addTo(menu, order++,
                        if (getAttachedMedia().firstToShare.contentType.isImage) R.string.menu_item_view_image else R.string.view_media)
            }
            if (noteForAnyAccount.isLoaded && !noteForAnyAccount.visibility.isPrivate) {
                if (menuData.favorited) {
                    NoteContextMenuItem.UNDO_LIKE.addTo(menu, order++,
                            R.string.menu_item_destroy_favorite)
                } else {
                    NoteContextMenuItem.LIKE.addTo(menu, order++,
                            R.string.menu_item_favorite)
                }
                if (menuData.reblogged) {
                    NoteContextMenuItem.UNDO_ANNOUNCE.addTo(menu, order++,
                            actingAccount.alternativeTermForResourceId(R.string.menu_item_destroy_reblog))
                } else {
                    // Don't allow an Actor to reblog himself
                    if (actingAccount.actorId != noteForAnyAccount.actor.actorId) {
                        NoteContextMenuItem.ANNOUNCE.addTo(menu, order++,
                                actingAccount.alternativeTermForResourceId(R.string.menu_item_reblog))
                    }
                }
            }
            if (noteForAnyAccount.isLoaded) {
                NoteContextMenuItem.OPEN_NOTE_PERMALINK.addTo(menu, order++, R.string.menu_item_open_message_permalink)
            }
            if (noteForAnyAccount.isLoaded) {
                when (myContext.accounts().succeededForSameOrigin(noteForAnyAccount.origin).size) {
                    0, 1 -> {
                    }
                    2 -> NoteContextMenuItem.ACT_AS_FIRST_OTHER_ACCOUNT.addTo(menu, order++,
                            StringUtil.format(
                                    activity, R.string.menu_item_act_as_user,
                                    myContext.accounts()
                                            .firstOtherSucceededForSameOrigin(noteForAnyAccount.origin, actingAccount)
                                            .shortestUniqueAccountName))
                    else -> NoteContextMenuItem.ACT_AS.addTo(menu, order++, R.string.menu_item_act_as)
                }
            }
            if (noteForAnyAccount.isPresentAtServer) {
                NoteContextMenuItem.GET_NOTE.addTo(menu, order, R.string.get_message)
            }
            if (menuData.isAuthorSucceededMyAccount) {
                if (noteForAnyAccount.isPresentAtServer) {
                    if (!menuData.reblogged && actingAccount.connection
                                    .hasApiEndpoint(ApiRoutineEnum.DELETE_NOTE)) {
                        NoteContextMenuItem.DELETE_NOTE.addTo(menu, order++,
                                R.string.menu_item_destroy_status)
                    }
                } else {
                    NoteContextMenuItem.DELETE_NOTE.addTo(menu, order++, R.string.button_discard)
                }
            } else {
                NoteContextMenuItem.DELETE_NOTE.addTo(menu, order++, R.string.menu_item_delete_note_from_local_cache)
            }
        } catch (e: Exception) {
            MyLog.w(this, method, e)
        }
    }

    fun getViewItem(): BaseNoteViewItem<*> {
        if (mViewItem.isEmpty) {
            return NoteViewItem.Companion.EMPTY
        }
        if (mViewItem is BaseNoteViewItem<*>) {
            return mViewItem as BaseNoteViewItem<*>
        } else if (mViewItem is ActivityViewItem) {
            return (mViewItem as ActivityViewItem).noteViewItem
        }
        return NoteViewItem.Companion.EMPTY
    }

    private fun addNoteLinksSubmenu(menu: ContextMenu?, v: View?, order: Int) {
        val links: Array<URLSpan?> = MyUrlSpan.Companion.getUrlSpans(v.findViewById<View?>(R.id.note_body))
        when (links.size) {
            0 -> {
            }
            1 -> menu.add(ContextMenu.NONE, NoteContextMenuItem.OPEN_NOTE_LINK.id,
                    order, activity.getText(R.string.n_message_link).toString() +
                    NoteContextMenuItem.Companion.NOTE_LINK_SEPARATOR +
                    links[0].getURL())
            else -> {
                val subMenu = menu.addSubMenu(ContextMenu.NONE, ContextMenu.NONE, order,
                        StringUtil.format(activity, R.string.n_message_links,
                                links.size))
                var orderSubmenu = 0
                for (link in links) {
                    subMenu.add(ContextMenu.NONE, NoteContextMenuItem.OPEN_NOTE_LINK.id,
                            orderSubmenu++, link.getURL())
                }
            }
        }
    }

    override fun setSelectedActingAccount(myAccount: MyAccount) {
        if (myAccount != futureData.menuData.myAccount) {
            futureData = FutureNoteContextMenuData.Companion.EMPTY
        }
        super.setSelectedActingAccount(myAccount)
    }

    fun getAttachedMedia(): NoteDownloads {
        return futureData.menuData.noteForAnyAccount.downloads
    }

    private fun isEditorVisible(): Boolean {
        return menuContainer.getNoteEditor().isVisible
    }

    fun onContextItemSelected(item: MenuItem?) {
        if (item != null) {
            selectedMenuItemTitle = StringUtil.notNull(item.title.toString())
            onContextItemSelected(NoteContextMenuItem.Companion.fromId(item.itemId), getNoteId())
        }
    }

    fun onContextItemSelected(contextMenuItem: NoteContextMenuItem, noteId: Long) {
        if (futureData.isFor(noteId)) {
            contextMenuItem.execute(this)
        }
    }

    fun switchTimelineActivityView(timeline: Timeline?) {
        if (TimelineActivity::class.java.isAssignableFrom(activity.javaClass)) {
            (activity as TimelineActivity<*>).switchView(timeline)
        } else {
            startForTimeline(myContext, activity, timeline)
        }
    }

    fun loadState(savedInstanceState: Bundle?) {
        if (savedInstanceState != null && savedInstanceState.containsKey(IntentExtra.ACCOUNT_NAME.key)) {
            selectedActingAccount = menuContainer.getActivity().myContext.accounts().fromAccountName(
                    savedInstanceState.getString(IntentExtra.ACCOUNT_NAME.key,
                            menuContainer.getActivity().myContext.accounts().currentAccount.accountName))
        }
    }

    fun saveState(outState: Bundle?) {
        outState.putString(IntentExtra.ACCOUNT_NAME.key, selectedActingAccount.accountName)
    }

    fun getNoteId(): Long {
        return futureData.getNoteId()
    }

    fun getOrigin(): Origin {
        return futureData.menuData.noteForAnyAccount.origin
    }

    override fun getActingAccount(): MyAccount {
        return if (selectedActingAccount.nonEmpty) selectedActingAccount else futureData.menuData.myAccount
    }

    fun getActor(): Actor? {
        return futureData.menuData.noteForAnyAccount.actor
    }

    fun getAuthor(): Actor? {
        return futureData.menuData.noteForAnyAccount.author
    }

    fun getSelectedMenuItemTitle(): String {
        return selectedMenuItemTitle
    }

    fun setFutureData(futureData: FutureNoteContextMenuData?) {
        this.futureData = futureData
    }
}