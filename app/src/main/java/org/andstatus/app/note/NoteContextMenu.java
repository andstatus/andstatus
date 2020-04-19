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

package org.andstatus.app.note;

import android.os.Bundle;
import android.text.style.URLSpan;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.accessibility.AccessibilityManager;

import androidx.annotation.NonNull;

import org.andstatus.app.IntentExtra;
import org.andstatus.app.R;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.activity.ActivityViewItem;
import org.andstatus.app.data.AccountToNote;
import org.andstatus.app.data.MyContentType;
import org.andstatus.app.data.NoteForAnyAccount;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.Connection;
import org.andstatus.app.net.social.Note;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.timeline.ContextMenuHeader;
import org.andstatus.app.timeline.TimelineActivity;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyUrlSpan;
import org.andstatus.app.util.StringUtil;
import org.andstatus.app.view.MyContextMenu;

import java.util.function.Consumer;

import static android.content.Context.ACCESSIBILITY_SERVICE;

/**
 * Context menu and corresponding actions on notes from the list
 * @author yvolk@yurivolkov.com
 */
public class NoteContextMenu extends MyContextMenu {
    final NoteContextMenuContainer menuContainer;
    private volatile NoteContextMenuData menuData = NoteContextMenuData.EMPTY;
    private String selectedMenuItemTitle = "";

    public NoteContextMenu(NoteContextMenuContainer menuContainer) {
        super(menuContainer.getActivity(), MyContextMenu.MENU_GROUP_NOTE);
        this.menuContainer = menuContainer;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        onCreateContextMenu(menu, v, menuInfo, null);
    }

    void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo, Consumer<NoteContextMenu> next) {
        super.onCreateContextMenu(menu, v, menuInfo);
        switch (menuData.getStateFor(getViewItem())) {
            case READY:
                if (next != null) {
                    next.accept(this);
                }
                if (menu != null) {
                    createContextMenu(menu, v, getViewItem());
                }
                break;
            case NEW:
                NoteContextMenuData.loadAsync(this, v, getViewItem(), next);
                break;
            default:
                break;
        }
    }

    private void createContextMenu(ContextMenu menu, View v, BaseNoteViewItem viewItem) {
        final String method = "createContextMenu";
        AccountToNote accountToNote = menuData.accountToNote;
        NoteForAnyAccount noteForAnyAccount = menuData.accountToNote.noteForAnyAccount;
        if (getSelectedActingAccount().isValid() && !getSelectedActingAccount().equals(accountToNote.getMyAccount())) {
            setSelectedActingAccount(accountToNote.getMyAccount());
        }
        if (menuData.equals(NoteContextMenuData.EMPTY)) return;

        int order = 0;
        try {
            new ContextMenuHeader(getActivity(), menu).setTitle(noteForAnyAccount.getBodyTrimmed())
                    .setSubtitle(getActingAccount().getAccountName());
            if (((AccessibilityManager) getMyContext().context().
                    getSystemService(ACCESSIBILITY_SERVICE)).isTouchExplorationEnabled()) {
                addNoteLinksSubmenu(menu, v, order++);
            }
            if (!ConversationActivity.class.isAssignableFrom(getActivity().getClass())) {
                NoteContextMenuItem.OPEN_CONVERSATION.addTo(menu, order++, R.string.menu_item_open_conversation);
            }

            if (menuContainer.getTimeline().actor.notSameUser(noteForAnyAccount.actor)
                    || menuContainer.getTimeline().getTimelineType() != TimelineType.SENT) {
                // Notes, where an Actor of this note is an Actor ("Sent timeline" of that actor)
                NoteContextMenuItem.NOTES_BY_ACTOR.addTo(menu, order++,
                        StringUtil.format(
                                getActivity(), R.string.menu_item_user_messages,
                                noteForAnyAccount.actor.getTimelineUsername()));
            }

            if (viewItem.isCollapsed()) {
                NoteContextMenuItem.SHOW_DUPLICATES.addTo(menu, order++, R.string.show_duplicates);
            } else if (getActivity().getListData().canBeCollapsed(getActivity().getPositionOfContextMenu())) {
                NoteContextMenuItem.COLLAPSE_DUPLICATES.addTo(menu, order++, R.string.collapse_duplicates);
            }
            NoteContextMenuItem.ACTORS_OF_NOTE.addTo(menu, order++, R.string.users_of_message);

            if (accountToNote.isAuthorSucceededMyAccount() && Note.mayBeEdited(
                    noteForAnyAccount.origin.getOriginType(),
                    noteForAnyAccount.status)) {
                NoteContextMenuItem.EDIT.addTo(menu, order++, R.string.menu_item_edit);
            }
            if (noteForAnyAccount.status.mayBeSent()) {
                NoteContextMenuItem.RESEND.addTo(menu, order++, R.string.menu_item_resend);
            }

            if (isEditorVisible()) {
                NoteContextMenuItem.COPY_TEXT.addTo(menu, order++, R.string.menu_item_copy_text);
                NoteContextMenuItem.COPY_AUTHOR.addTo(menu, order++, R.string.menu_item_copy_author);
            }

            if (accountToNote.getMyActor().notSameUser(noteForAnyAccount.actor)) {
                if (accountToNote.actorFollowed) {
                    NoteContextMenuItem.UNDO_FOLLOW_ACTOR.addTo(menu, order++,
                            StringUtil.format(
                                    getActivity(), R.string.menu_item_stop_following_user,
                                    noteForAnyAccount.actor.getTimelineUsername()));
                } else {
                    NoteContextMenuItem.FOLLOW_ACTOR.addTo(menu, order++,
                            StringUtil.format(
                                    getActivity(), R.string.menu_item_follow_user,
                                    noteForAnyAccount.actor.getTimelineUsername()));
                }
            }

            if (noteForAnyAccount.actor.notSameUser(noteForAnyAccount.author)) {
                if (menuContainer.getTimeline().actor.notSameUser(noteForAnyAccount.author)
                        || menuContainer.getTimeline().getTimelineType() != TimelineType.SENT) {
                    // Sent timeline of that actor
                    NoteContextMenuItem.NOTES_BY_AUTHOR.addTo(menu, order++,
                            StringUtil.format(
                                    getActivity(), R.string.menu_item_user_messages,
                                    noteForAnyAccount.author.getTimelineUsername()));
                }
                if (accountToNote.getMyActor().notSameUser(noteForAnyAccount.author)) {
                    if (accountToNote.authorFollowed) {
                        NoteContextMenuItem.UNDO_FOLLOW_AUTHOR.addTo(menu, order++,
                                StringUtil.format(
                                        getActivity(), R.string.menu_item_stop_following_user,
                                        noteForAnyAccount.author.getTimelineUsername()));
                    } else {
                        NoteContextMenuItem.FOLLOW_AUTHOR.addTo(menu, order++,
                                StringUtil.format(
                                        getActivity(), R.string.menu_item_follow_user,
                                        noteForAnyAccount.author.getTimelineUsername()));
                    }
                }
            }

            if (noteForAnyAccount.isLoaded() && (noteForAnyAccount.visibility.notFalse ||
                    noteForAnyAccount.origin.getOriginType().isPrivateNoteAllowsReply()) && !isEditorVisible()) {
                NoteContextMenuItem.REPLY.addTo(menu, order++, R.string.menu_item_reply);
                NoteContextMenuItem.REPLY_TO_CONVERSATION_PARTICIPANTS.addTo(menu, order++,
                        R.string.menu_item_reply_to_conversation_participants);
                NoteContextMenuItem.REPLY_TO_MENTIONED_ACTORS.addTo(menu, order++,
                        R.string.menu_item_reply_to_mentioned_users);
            }
            NoteContextMenuItem.SHARE.addTo(menu, order++, R.string.menu_item_share);
            if (!getAttachedMedia().isEmpty()) {
                NoteContextMenuItem.VIEW_MEDIA.addTo(menu, order++,
                        getAttachedMedia().getFirstToShare().getContentType() == MyContentType.IMAGE
                                ? R.string.menu_item_view_image
                                : R.string.view_media);
            }

            if (noteForAnyAccount.isLoaded() && noteForAnyAccount.visibility.notFalse) {
                if (accountToNote.favorited) {
                    NoteContextMenuItem.UNDO_LIKE.addTo(menu, order++,
                            R.string.menu_item_destroy_favorite);
                } else {
                    NoteContextMenuItem.LIKE.addTo(menu, order++,
                            R.string.menu_item_favorite);
                }
                if (accountToNote.reblogged) {
                    NoteContextMenuItem.UNDO_ANNOUNCE.addTo(menu, order++,
                            getActingAccount().alternativeTermForResourceId(R.string.menu_item_destroy_reblog));
                } else {
                    // Don't allow an Actor to reblog himself
                    if (getActingAccount().getActorId() != noteForAnyAccount.actor.actorId) {
                        NoteContextMenuItem.ANNOUNCE.addTo(menu, order++,
                                getActingAccount().alternativeTermForResourceId(R.string.menu_item_reblog));
                    }
                }
            }

            if (noteForAnyAccount.isLoaded()) {
                NoteContextMenuItem.OPEN_NOTE_PERMALINK.addTo(menu, order++, R.string.menu_item_open_message_permalink);
            }

            if (noteForAnyAccount.isLoaded()) {
                switch (getMyContext().accounts().succeededForSameOrigin(noteForAnyAccount.origin).size()) {
                    case 0:
                    case 1:
                        break;
                    case 2:
                        NoteContextMenuItem.ACT_AS_FIRST_OTHER_ACCOUNT.addTo(menu, order++,
                                StringUtil.format(
                                getActivity(), R.string.menu_item_act_as_user,
                                getMyContext().accounts()
                                    .firstOtherSucceededForSameOrigin(noteForAnyAccount.origin, getActingAccount())
                                    .getShortestUniqueAccountName()));
                        break;
                    default:
                        NoteContextMenuItem.ACT_AS.addTo(menu, order++, R.string.menu_item_act_as);
                        break;
                }
            }
            if (noteForAnyAccount.isPresentAtServer()) {
                NoteContextMenuItem.GET_NOTE.addTo(menu, order, R.string.get_message);
            }
            if (accountToNote.isAuthorSucceededMyAccount()) {
                if (noteForAnyAccount.isPresentAtServer()) {
                    if (!accountToNote.reblogged && getActingAccount().getConnection()
                            .hasApiEndpoint(Connection.ApiRoutineEnum.DELETE_NOTE)) {
                        NoteContextMenuItem.DELETE_NOTE.addTo(menu, order++,
                                R.string.menu_item_destroy_status);
                    }
                } else {
                    NoteContextMenuItem.DELETE_NOTE.addTo(menu, order++, R.string.button_discard);
                }
            } else {
                NoteContextMenuItem.DELETE_NOTE.addTo(menu, order++, R.string.menu_item_delete_note_from_local_cache);
            }
        } catch (Exception e) {
            MyLog.e(this, method, e);
        }
    }

    @NonNull
    BaseNoteViewItem getViewItem() {
        if (mViewItem.isEmpty()) {
            return NoteViewItem.EMPTY;
        }
        if (mViewItem instanceof BaseNoteViewItem) {
            return (BaseNoteViewItem) mViewItem;
        } else if (mViewItem instanceof ActivityViewItem){
            return ((ActivityViewItem) mViewItem).noteViewItem;
        }
        return NoteViewItem.EMPTY;
    }

    private void addNoteLinksSubmenu(ContextMenu menu, View v, int order) {
        URLSpan[] links = MyUrlSpan.getUrlSpans(v.findViewById(R.id.note_body));
        switch (links.length) {
            case 0:
                break;
            case 1:
                menu.add(ContextMenu.NONE, NoteContextMenuItem.OPEN_NOTE_LINK.getId(),
                            order, getActivity().getText(R.string.n_message_link).toString() +
                                NoteContextMenuItem.NOTE_LINK_SEPARATOR +
                                links[0].getURL());
                break;
            default:
                SubMenu subMenu = menu.addSubMenu(ContextMenu.NONE, ContextMenu.NONE, order,
                        StringUtil.format(getActivity(), R.string.n_message_links,
                                        links.length));
                int orderSubmenu = 0;
                for (URLSpan link : links) {
                    subMenu.add(ContextMenu.NONE, NoteContextMenuItem.OPEN_NOTE_LINK.getId(),
                            orderSubmenu++, link.getURL());
                }
                break;
        }
    }

    @Override
    public void setSelectedActingAccount(@NonNull MyAccount myAccount) {
        if (!myAccount.equals(menuData.accountToNote.getMyAccount())) {
            menuData = NoteContextMenuData.EMPTY;
        }
        super.setSelectedActingAccount(myAccount);
    }

    @NonNull
    NoteDownloads getAttachedMedia() {
        return menuData.accountToNote.noteForAnyAccount.downloads;
    }

    private boolean isEditorVisible() {
        return menuContainer.getNoteEditor().isVisible();
    }

    public void onContextItemSelected(MenuItem item) {
        if (item != null) {
            this.selectedMenuItemTitle = StringUtil.notNull(String.valueOf(item.getTitle()));
            onContextItemSelected(NoteContextMenuItem.fromId(item.getItemId()), getNoteId());
        }
    }

    void onContextItemSelected(NoteContextMenuItem contextMenuItem, long noteId) {
        if (menuData.isFor(noteId)) {
            contextMenuItem.execute(this);
        }
    }

    void switchTimelineActivityView(Timeline timeline) {
        if (TimelineActivity.class.isAssignableFrom(getActivity().getClass())) {
            ((TimelineActivity) getActivity()).switchView(timeline);
        } else {
            TimelineActivity.startForTimeline(getMyContext(), getActivity(),  timeline);
        }
    }

    public void loadState(Bundle savedInstanceState) {
        if (savedInstanceState != null && savedInstanceState.containsKey(IntentExtra.ACCOUNT_NAME.key)) {
            setSelectedActingAccount(menuContainer.getActivity().getMyContext().accounts().fromAccountName(
                    savedInstanceState.getString(IntentExtra.ACCOUNT_NAME.key,
                            menuContainer.getActivity().getMyContext().accounts().getCurrentAccount().getAccountName())));
        }
    }

    public void saveState(Bundle outState) {
        outState.putString(IntentExtra.ACCOUNT_NAME.key, getSelectedActingAccount().getAccountName());
    }

    public long getNoteId() {
        return menuData.getNoteId();
    }

    @NonNull
    public Origin getOrigin() {
        return menuData.accountToNote.noteForAnyAccount.origin;
    }

    @NonNull
    @Override
    public MyAccount getActingAccount() {
        return getSelectedActingAccount().nonEmpty()
                ? getSelectedActingAccount()
                : menuData.accountToNote.getMyAccount();
    }

    public Actor getActor() {
        return menuData.accountToNote.noteForAnyAccount.actor;
    }

    public Actor getAuthor() {
        return menuData.accountToNote.noteForAnyAccount.author;
    }

    @NonNull
    String getSelectedMenuItemTitle() {
        return selectedMenuItemTitle;
    }

    void setMenuData(NoteContextMenuData menuData) {
        this.menuData = menuData;
    }
}
