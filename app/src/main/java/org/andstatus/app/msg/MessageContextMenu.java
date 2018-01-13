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

package org.andstatus.app.msg;

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
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MessageForAccount;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.net.social.Connection;
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
 * Context menu and corresponding actions on messages from the list 
 * @author yvolk@yurivolkov.com
 */
public class MessageContextMenu extends MyContextMenu {
    final MessageListContextMenuContainer menuContainer;
    private volatile MessageContextMenuData menuData = MessageContextMenuData.EMPTY;
    private String selectedMenuItemTitle = "";

    public MessageContextMenu(MessageListContextMenuContainer menuContainer) {
        super(menuContainer.getActivity(), MyContextMenu.MENU_GROUP_MESSAGE);
        this.menuContainer = menuContainer;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        onCreateContextMenu(menu, v, menuInfo, null);
    }

    void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo, Consumer<MessageContextMenu> next) {
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
                MessageContextMenuData.loadAsync(this, v, getViewItem(), next);
                break;
            default:
                break;
        }
    }

    private void createContextMenu(ContextMenu menu, View v, BaseMessageViewItem viewItem) {
        final String method = "createContextMenu";
        MessageForAccount msg = menuData.msg;
        int order = 0;
        try {
            new ContextMenuHeader(getActivity(), menu).setTitle(msg.getBodyTrimmed())
                    .setSubtitle(msg.getMyAccount().getAccountName());
            if (((AccessibilityManager) getMyContext().context().
                    getSystemService(ACCESSIBILITY_SERVICE)).isTouchExplorationEnabled()) {
                addMessageLinksSubmenu(menu, v, order++);
            }
            if (!ConversationActivity.class.isAssignableFrom(getActivity().getClass())) {
                MessageListContextMenuItem.OPEN_CONVERSATION.addTo(menu, order++, R.string.menu_item_open_conversation);
            }
            if (viewItem.isCollapsed()) {
                MessageListContextMenuItem.SHOW_DUPLICATES.addTo(menu, order++, R.string.show_duplicates);
            } else if (getActivity().getListData().canBeCollapsed(getActivity().getPositionOfContextMenu())) {
                MessageListContextMenuItem.COLLAPSE_DUPLICATES.addTo(menu, order++, R.string.collapse_duplicates);
            }
            MessageListContextMenuItem.USERS_OF_MESSAGE.addTo(menu, order++, R.string.users_of_message);

            if (msg.isAuthorSucceededMyAccount() &&
                    (msg.status != DownloadStatus.LOADED ||
                            getOrigin().getOriginType().allowEditing())) {
                MessageListContextMenuItem.EDIT.addTo(menu, order++, R.string.menu_item_edit);
            }
            if (msg.status.mayBeSent()) {
                MessageListContextMenuItem.RESEND.addTo(menu, order++, R.string.menu_item_resend);
            }

            if (isEditorVisible()) {
                MessageListContextMenuItem.COPY_TEXT.addTo(menu, order++, R.string.menu_item_copy_text);
                MessageListContextMenuItem.COPY_AUTHOR.addTo(menu, order++, R.string.menu_item_copy_author);
            }

            // "Actor" is about an Activity, not about a Message
            if (menuContainer.getTimeline().getUserId() != msg.actorId) {
                // Messages by a Sender of this message ("User timeline" of that user)
                MessageListContextMenuItem.ACTOR_MESSAGES.addTo(menu, order++,
                        String.format(
                                getActivity().getText(R.string.menu_item_user_messages).toString(),
                                MyQuery.userIdToWebfingerId(msg.actorId)));
                if (!msg.isActor) {
                    if (msg.actorFollowed) {
                        MessageListContextMenuItem.STOP_FOLLOWING_ACTOR.addTo(menu, order++,
                                String.format(
                                        getActivity().getText(R.string.menu_item_stop_following_user).toString(),
                                        MyQuery.userIdToWebfingerId(msg.actorId)));
                    } else {
                        MessageListContextMenuItem.FOLLOW_ACTOR.addTo(menu, order++,
                                String.format(
                                        getActivity().getText(R.string.menu_item_follow_user).toString(),
                                        MyQuery.userIdToWebfingerId(msg.actorId)));
                    }
                }
            }

            if (menuContainer.getTimeline().getUserId() != msg.authorId && msg.actorId != msg.authorId) {
                // Messages by an Author of this message ("User timeline" of that user)
                MessageListContextMenuItem.AUTHOR_MESSAGES.addTo(menu, order++,
                        String.format(
                                getActivity().getText(R.string.menu_item_user_messages).toString(),
                                MyQuery.userIdToWebfingerId(msg.authorId)));
                if (!msg.isAuthor) {
                    if (msg.authorFollowed) {
                        MessageListContextMenuItem.STOP_FOLLOWING_AUTHOR.addTo(menu, order++,
                                String.format(
                                        getActivity().getText(R.string.menu_item_stop_following_user).toString(),
                                        MyQuery.userIdToWebfingerId(msg.authorId)));
                    } else {
                        MessageListContextMenuItem.FOLLOW_AUTHOR.addTo(menu, order++,
                                String.format(
                                        getActivity().getText(R.string.menu_item_follow_user).toString(),
                                        MyQuery.userIdToWebfingerId(msg.authorId)));
                    }
                }
            }

            if (msg.isLoaded() && (!msg.isPrivate() ||
                    msg.origin.getOriginType().isDirectMessageAllowsReply()) && !isEditorVisible()) {
                MessageListContextMenuItem.REPLY.addTo(menu, order++, R.string.menu_item_reply);
                MessageListContextMenuItem.REPLY_TO_CONVERSATION_PARTICIPANTS.addTo(menu, order++,
                        R.string.menu_item_reply_to_conversation_participants);
                MessageListContextMenuItem.REPLY_TO_MENTIONED_USERS.addTo(menu, order++,
                        R.string.menu_item_reply_to_mentioned_users);
            }
            MessageListContextMenuItem.SHARE.addTo(menu, order++, R.string.menu_item_share);
            if (!TextUtils.isEmpty(getImageFilename())) {
                MessageListContextMenuItem.VIEW_IMAGE.addTo(menu, order++, R.string.menu_item_view_image);
            }

            if (!isEditorVisible()) {
                // TODO: Only if he follows me?
                MessageListContextMenuItem.DIRECT_MESSAGE.addTo(menu, order++,
                        R.string.menu_item_private_message);
            }

            if (msg.isLoaded() && !msg.isPrivate()) {
                if (msg.favorited) {
                    MessageListContextMenuItem.DESTROY_FAVORITE.addTo(menu, order++,
                            R.string.menu_item_destroy_favorite);
                } else {
                    MessageListContextMenuItem.FAVORITE.addTo(menu, order++,
                            R.string.menu_item_favorite);
                }
                if (msg.reblogged) {
                    MessageListContextMenuItem.DESTROY_REBLOG.addTo(menu, order++,
                            msg.getMyAccount().alternativeTermForResourceId(R.string.menu_item_destroy_reblog));
                } else {
                    // Don't allow a User to reblog himself
                    if (getMyActor().getUserId() != msg.actorId) {
                        MessageListContextMenuItem.REBLOG.addTo(menu, order++,
                                msg.getMyAccount().alternativeTermForResourceId(R.string.menu_item_reblog));
                    }
                }
            }

            if (msg.isLoaded()) {
                MessageListContextMenuItem.OPEN_MESSAGE_PERMALINK.addTo(menu, order++, R.string.menu_item_open_message_permalink);
            }

            if (msg.isAuthorSucceededMyAccount()) {
                if (msg.isLoaded()) {
                    if (msg.isPrivate()) {
                        // TODO: Delete private (direct) message
                    } else if (!msg.reblogged && msg.getMyAccount().getConnection()
                            .isApiSupported(Connection.ApiRoutineEnum.DESTROY_MESSAGE)) {
                        MessageListContextMenuItem.DESTROY_STATUS.addTo(menu, order++,
                                R.string.menu_item_destroy_status);
                    }
                } else {
                    MessageListContextMenuItem.DESTROY_STATUS.addTo(menu, order++, R.string.button_discard);
                }
            }

            if (msg.isLoaded()) {
                switch (msg.getMyAccount().numberOfAccountsOfThisOrigin()) {
                    case 0:
                    case 1:
                        break;
                    case 2:
                        MessageListContextMenuItem.ACT_AS_FIRST_OTHER_USER.addTo(menu, order++,
                                String.format(
                                        getActivity().getText(R.string.menu_item_act_as_user).toString(),
                                        msg.getMyAccount().firstOtherAccountOfThisOrigin().getShortestUniqueAccountName(getMyContext())));
                        break;
                    default:
                        MessageListContextMenuItem.ACT_AS.addTo(menu, order++, R.string.menu_item_act_as);
                        break;
                }
            }
            MessageListContextMenuItem.GET_MESSAGE.addTo(menu, order, R.string.get_message);
        } catch (Exception e) {
            MyLog.e(this, method, e);
        }
    }

    @NonNull
    BaseMessageViewItem getViewItem() {
        if (mViewItem.isEmpty()) {
            return MessageViewItem.EMPTY;
        }
        if (BaseMessageViewItem.class.isAssignableFrom(mViewItem.getClass())) {
            return (BaseMessageViewItem) mViewItem;
        } else if (ActivityViewItem.class.isAssignableFrom(mViewItem.getClass())){
            return ((ActivityViewItem) mViewItem).getMessage();
        }
        return MessageViewItem.EMPTY;
    }

    private void addMessageLinksSubmenu(ContextMenu menu, View v, int order) {
        URLSpan[] links = MyUrlSpan.getUrlSpans(v.findViewById(R.id.message_body));
        switch (links.length) {
            case 0:
                break;
            case 1:
                menu.add(ContextMenu.NONE, MessageListContextMenuItem.MESSAGE_LINK.getId(),
                            order, getActivity().getText(R.string.n_message_link).toString() +
                                MessageListContextMenuItem.MESSAGE_LINK_SEPARATOR +
                                links[0].getURL());
                break;
            default:
                SubMenu subMenu = menu.addSubMenu(ContextMenu.NONE, ContextMenu.NONE, order,
                        String.format(getActivity().getText(R.string.n_message_links).toString(),
                                        links.length));
                int orderSubmenu = 0;
                for (URLSpan link : links) {
                    subMenu.add(ContextMenu.NONE, MessageListContextMenuItem.MESSAGE_LINK.getId(),
                            orderSubmenu++, link.getURL());
                }
                break;
        }
    }

    @Override
    public void setMyActor(@NonNull MyAccount myAccount) {
        if (!myAccount.equals(menuData.msg.getMyAccount())) {
            menuData = MessageContextMenuData.EMPTY;
        }
        super.setMyActor(myAccount);
    }

    @NonNull
    String getImageFilename() {
        return StringUtils.notNull(menuData.msg.imageFilename);
    }

    private boolean isEditorVisible() {
        return menuContainer.getMessageEditor().isVisible();
    }

    public void onContextItemSelected(MenuItem item) {
        if (item != null) {
            this.selectedMenuItemTitle = StringUtils.notNull(String.valueOf(item.getTitle()));
            onContextItemSelected(MessageListContextMenuItem.fromId(item.getItemId()), getMsgId());
        }
    }

    void onContextItemSelected(MessageListContextMenuItem contextMenuItem, long msgId) {
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

    void setMenuData(MessageContextMenuData menuData) {
        this.menuData = menuData;
        setMyActor(menuData.msg.getMyAccount());
    }
}
