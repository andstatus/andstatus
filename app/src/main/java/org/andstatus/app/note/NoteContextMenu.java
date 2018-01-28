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
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.text.style.URLSpan;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.accessibility.AccessibilityManager;

import org.andstatus.app.IntentExtra;
import org.andstatus.app.R;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.activity.ActivityViewItem;
import org.andstatus.app.data.NoteForAccount;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.net.social.Connection;
import org.andstatus.app.net.social.Note;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.timeline.ContextMenuHeader;
import org.andstatus.app.timeline.TimelineActivity;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyUrlSpan;
import org.andstatus.app.util.StringUtils;
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
        NoteForAccount msg = menuData.msg;
        int order = 0;
        try {
            new ContextMenuHeader(getActivity(), menu).setTitle(msg.getBodyTrimmed())
                    .setSubtitle(msg.getMyAccount().getAccountName());
            if (((AccessibilityManager) getMyContext().context().
                    getSystemService(ACCESSIBILITY_SERVICE)).isTouchExplorationEnabled()) {
                addMessageLinksSubmenu(menu, v, order++);
            }
            if (!ConversationActivity.class.isAssignableFrom(getActivity().getClass())) {
                NoteContextMenuItem.OPEN_CONVERSATION.addTo(menu, order++, R.string.menu_item_open_conversation);
            }
            if (viewItem.isCollapsed()) {
                NoteContextMenuItem.SHOW_DUPLICATES.addTo(menu, order++, R.string.show_duplicates);
            } else if (getActivity().getListData().canBeCollapsed(getActivity().getPositionOfContextMenu())) {
                NoteContextMenuItem.COLLAPSE_DUPLICATES.addTo(menu, order++, R.string.collapse_duplicates);
            }
            NoteContextMenuItem.ACTORS_OF_NOTE.addTo(menu, order++, R.string.users_of_message);

            if (msg.isAuthorSucceededMyAccount() && Note.mayBeEdited(msg.origin.getOriginType(), msg.status)) {
                NoteContextMenuItem.EDIT.addTo(menu, order++, R.string.menu_item_edit);
            }
            if (msg.status.mayBeSent()) {
                NoteContextMenuItem.RESEND.addTo(menu, order++, R.string.menu_item_resend);
            }

            if (isEditorVisible()) {
                NoteContextMenuItem.COPY_TEXT.addTo(menu, order++, R.string.menu_item_copy_text);
                NoteContextMenuItem.COPY_AUTHOR.addTo(menu, order++, R.string.menu_item_copy_author);
            }

            // "Actor" is about an Activity, not about a Message
            if (menuContainer.getTimeline().getActorId() != msg.actorId) {
                // Notes, where an Actor of this note is an Actor ("Actor timeline" of that actor)
                NoteContextMenuItem.ACTOR_MESSAGES.addTo(menu, order++,
                        String.format(
                                getActivity().getText(R.string.menu_item_user_messages).toString(),
                                MyQuery.actorIdToWebfingerId(msg.actorId)));
                if (!msg.isActor) {
                    if (msg.actorFollowed) {
                        NoteContextMenuItem.STOP_FOLLOWING_ACTOR.addTo(menu, order++,
                                String.format(
                                        getActivity().getText(R.string.menu_item_stop_following_user).toString(),
                                        MyQuery.actorIdToWebfingerId(msg.actorId)));
                    } else {
                        NoteContextMenuItem.FOLLOW_ACTOR.addTo(menu, order++,
                                String.format(
                                        getActivity().getText(R.string.menu_item_follow_user).toString(),
                                        MyQuery.actorIdToWebfingerId(msg.actorId)));
                    }
                }
            }

            if (menuContainer.getTimeline().getActorId() != msg.authorId && msg.actorId != msg.authorId) {
                // Messages by an Author of this message ("Actor timeline" of that actor)
                NoteContextMenuItem.AUTHOR_MESSAGES.addTo(menu, order++,
                        String.format(
                                getActivity().getText(R.string.menu_item_user_messages).toString(),
                                MyQuery.actorIdToWebfingerId(msg.authorId)));
                if (!msg.isAuthor) {
                    if (msg.authorFollowed) {
                        NoteContextMenuItem.STOP_FOLLOWING_AUTHOR.addTo(menu, order++,
                                String.format(
                                        getActivity().getText(R.string.menu_item_stop_following_user).toString(),
                                        MyQuery.actorIdToWebfingerId(msg.authorId)));
                    } else {
                        NoteContextMenuItem.FOLLOW_AUTHOR.addTo(menu, order++,
                                String.format(
                                        getActivity().getText(R.string.menu_item_follow_user).toString(),
                                        MyQuery.actorIdToWebfingerId(msg.authorId)));
                    }
                }
            }

            if (msg.isLoaded() && (!msg.isPrivate() ||
                    msg.origin.getOriginType().isDirectMessageAllowsReply()) && !isEditorVisible()) {
                NoteContextMenuItem.REPLY.addTo(menu, order++, R.string.menu_item_reply);
                NoteContextMenuItem.REPLY_TO_CONVERSATION_PARTICIPANTS.addTo(menu, order++,
                        R.string.menu_item_reply_to_conversation_participants);
                NoteContextMenuItem.REPLY_TO_MENTIONED_ACTORS.addTo(menu, order++,
                        R.string.menu_item_reply_to_mentioned_users);
            }
            NoteContextMenuItem.SHARE.addTo(menu, order++, R.string.menu_item_share);
            if (!TextUtils.isEmpty(getImageFilename())) {
                NoteContextMenuItem.VIEW_IMAGE.addTo(menu, order++, R.string.menu_item_view_image);
            }

            if (!isEditorVisible()) {
                // TODO: Only if he follows me?
                NoteContextMenuItem.PRIVATE_NOTE.addTo(menu, order++,
                        R.string.menu_item_private_message);
            }

            if (msg.isLoaded() && !msg.isPrivate()) {
                if (msg.favorited) {
                    NoteContextMenuItem.DESTROY_FAVORITE.addTo(menu, order++,
                            R.string.menu_item_destroy_favorite);
                } else {
                    NoteContextMenuItem.FAVORITE.addTo(menu, order++,
                            R.string.menu_item_favorite);
                }
                if (msg.reblogged) {
                    NoteContextMenuItem.DELETE_REBLOG.addTo(menu, order++,
                            msg.getMyAccount().alternativeTermForResourceId(R.string.menu_item_destroy_reblog));
                } else {
                    // Don't allow an Actor to reblog himself
                    if (getMyActor().getActorId() != msg.actorId) {
                        NoteContextMenuItem.REBLOG.addTo(menu, order++,
                                msg.getMyAccount().alternativeTermForResourceId(R.string.menu_item_reblog));
                    }
                }
            }

            if (msg.isLoaded()) {
                NoteContextMenuItem.OPEN_NOTE_PERMALINK.addTo(menu, order++, R.string.menu_item_open_message_permalink);
            }

            if (msg.isAuthorSucceededMyAccount()) {
                if (msg.isLoaded()) {
                    if (msg.isPrivate()) {
                        // TODO: Delete private (direct) message
                    } else if (!msg.reblogged && msg.getMyAccount().getConnection()
                            .isApiSupported(Connection.ApiRoutineEnum.DELETE_NOTE)) {
                        NoteContextMenuItem.DELETE_NOTE.addTo(menu, order++,
                                R.string.menu_item_destroy_status);
                    }
                } else {
                    NoteContextMenuItem.DELETE_NOTE.addTo(menu, order++, R.string.button_discard);
                }
            }

            if (msg.isLoaded()) {
                switch (msg.getMyAccount().numberOfAccountsOfThisOrigin()) {
                    case 0:
                    case 1:
                        break;
                    case 2:
                        NoteContextMenuItem.ACT_AS_FIRST_OTHER_ACCOUNT.addTo(menu, order++,
                                String.format(
                                        getActivity().getText(R.string.menu_item_act_as_user).toString(),
                                        msg.getMyAccount().firstOtherAccountOfThisOrigin().getShortestUniqueAccountName(getMyContext())));
                        break;
                    default:
                        NoteContextMenuItem.ACT_AS.addTo(menu, order++, R.string.menu_item_act_as);
                        break;
                }
            }
            NoteContextMenuItem.GET_NOTE.addTo(menu, order, R.string.get_message);
        } catch (Exception e) {
            MyLog.e(this, method, e);
        }
    }

    @NonNull
    BaseNoteViewItem getViewItem() {
        if (mViewItem.isEmpty()) {
            return NoteViewItem.EMPTY;
        }
        if (BaseNoteViewItem.class.isAssignableFrom(mViewItem.getClass())) {
            return (BaseNoteViewItem) mViewItem;
        } else if (ActivityViewItem.class.isAssignableFrom(mViewItem.getClass())){
            return ((ActivityViewItem) mViewItem).noteViewItem;
        }
        return NoteViewItem.EMPTY;
    }

    private void addMessageLinksSubmenu(ContextMenu menu, View v, int order) {
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
                        String.format(getActivity().getText(R.string.n_message_links).toString(),
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
    public void setMyActor(@NonNull MyAccount myAccount) {
        if (!myAccount.equals(menuData.msg.getMyAccount())) {
            menuData = NoteContextMenuData.EMPTY;
        }
        super.setMyActor(myAccount);
    }

    @NonNull
    String getImageFilename() {
        return StringUtils.notNull(menuData.msg.imageFilename);
    }

    private boolean isEditorVisible() {
        return menuContainer.getNoteEditor().isVisible();
    }

    public void onContextItemSelected(MenuItem item) {
        if (item != null) {
            this.selectedMenuItemTitle = StringUtils.notNull(String.valueOf(item.getTitle()));
            onContextItemSelected(NoteContextMenuItem.fromId(item.getItemId()), getMsgId());
        }
    }

    void onContextItemSelected(NoteContextMenuItem contextMenuItem, long msgId) {
        if (menuData.isFor(msgId)) {
            contextMenuItem.execute(this);
        }
    }

    void switchTimelineActivityView(Timeline timeline) {
        if (TimelineActivity.class.isAssignableFrom(getActivity().getClass())) {
            ((TimelineActivity) getActivity()).switchView(timeline, null);
        } else {
            TimelineActivity.startForTimeline(getMyContext(), getActivity(),  timeline, null, false);
        }
    }

    public void loadState(Bundle savedInstanceState) {
        if (savedInstanceState != null && savedInstanceState.containsKey(IntentExtra.ACCOUNT_NAME.key)) {
            setMyActor(menuContainer.getActivity().getMyContext().persistentAccounts().fromAccountName(
                    savedInstanceState.getString(IntentExtra.ACCOUNT_NAME.key,
                            menuContainer.getCurrentMyAccount().getAccountName())));
        }
    }

    public void saveState(Bundle outState) {
        outState.putString(IntentExtra.ACCOUNT_NAME.key, menuData.msg.getMyAccount().getAccountName());
    }

    public long getMsgId() {
        return menuData.getMsgId();
    }

    @NonNull
    public Origin getOrigin() {
        return menuData.msg.origin;
    }

    public long getActorId() {
        return menuData.msg.actorId;
    }

    public long getAuthorId() {
        return menuData.msg.authorId;
    }

    @NonNull
    String getSelectedMenuItemTitle() {
        return selectedMenuItemTitle;
    }

    void setMenuData(NoteContextMenuData menuData) {
        this.menuData = menuData;
        setMyActor(menuData.msg.getMyAccount());
    }
}
