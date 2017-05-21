/*
 * Copyright (C) 2013-2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

import org.andstatus.app.ContextMenuHeader;
import org.andstatus.app.IntentExtra;
import org.andstatus.app.MyContextMenu;
import org.andstatus.app.R;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MessageForAccount;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.timeline.Timeline;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyUrlSpan;
import org.andstatus.app.util.StringUtils;

import static android.content.Context.ACCESSIBILITY_SERVICE;

/**
 * Context menu and corresponding actions on messages from the list 
 * @author yvolk@yurivolkov.com
 */
class MessageContextMenu extends MyContextMenu {
    final MessageListContextMenuContainer menuContainer;
    
    String imageFilename = null;

    /**
     * Message that was selected (clicked, or whose context menu item was selected)
     */
    private MessageForAccount msg = MessageForAccount.EMPTY;
    private String selectedItemTitle = "";

    MessageContextMenu(MessageListContextMenuContainer menuContainer) {
        super(menuContainer.getActivity());
        this.menuContainer = menuContainer;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        final String method = "onCreateContextMenu";
        super.onCreateContextMenu(menu, v, menuInfo);
        if (getMsgId() == 0) {
            return;
        }

        MessageViewItem viewItem = (MessageViewItem) mViewItem;
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

            if (msg.isSenderMySucceededAccount() &&
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

            if (menuContainer.getTimeline().getUserId() != msg.senderId) {
                // Messages by a Sender of this message ("User timeline" of that user)
                MessageListContextMenuItem.SENDER_MESSAGES.addTo(menu, order++,
                        String.format(
                                getActivity().getText(R.string.menu_item_user_messages).toString(),
                                MyQuery.userIdToWebfingerId(msg.senderId)));
                if (!msg.isSender) {
                    if (msg.senderFollowed) {
                        MessageListContextMenuItem.STOP_FOLLOWING_SENDER.addTo(menu, order++,
                                String.format(
                                        getActivity().getText(R.string.menu_item_stop_following_user).toString(),
                                        MyQuery.userIdToWebfingerId(msg.senderId)));
                    } else {
                        MessageListContextMenuItem.FOLLOW_SENDER.addTo(menu, order++,
                                String.format(
                                        getActivity().getText(R.string.menu_item_follow_user).toString(),
                                        MyQuery.userIdToWebfingerId(msg.senderId)));
                    }
                }
            }

            if (menuContainer.getTimeline().getUserId() != msg.authorId && msg.senderId != msg.authorId) {
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

            if (msg.isLoaded() && (!msg.isDirect() ||
                    msg.origin.getOriginType().isDirectMessageAllowsReply()) && !isEditorVisible()) {
                MessageListContextMenuItem.REPLY.addTo(menu, order++, R.string.menu_item_reply);
                MessageListContextMenuItem.REPLY_TO_CONVERSATION_PARTICIPANTS.addTo(menu, order++,
                        R.string.menu_item_reply_to_conversation_participants);
                MessageListContextMenuItem.REPLY_TO_MENTIONED_USERS.addTo(menu, order++,
                        R.string.menu_item_reply_to_mentioned_users);
            }
            MessageListContextMenuItem.SHARE.addTo(menu, order++, R.string.menu_item_share);
            if (!TextUtils.isEmpty(msg.imageFilename)) {
                imageFilename = msg.imageFilename;
                MessageListContextMenuItem.VIEW_IMAGE.addTo(menu, order++, R.string.menu_item_view_image);
            }

            if (!isEditorVisible()) {
                // TODO: Only if he follows me?
                MessageListContextMenuItem.DIRECT_MESSAGE.addTo(menu, order++,
                        R.string.menu_item_direct_message);
            }

            if (msg.isLoaded() && !msg.isDirect()) {
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
                    if (getMyActor().getUserId() != msg.senderId) {
                        MessageListContextMenuItem.REBLOG.addTo(menu, order++,
                                msg.getMyAccount().alternativeTermForResourceId(R.string.menu_item_reblog));
                    }
                }
            }

            if (msg.isLoaded()) {
                MessageListContextMenuItem.OPEN_MESSAGE_PERMALINK.addTo(menu, order++, R.string.menu_item_open_message_permalink);
            }

            if (msg.isSenderMySucceededAccount()) {
                if (msg.isLoaded()) {
                    if (msg.isDirect()) {
                        // This is a Direct Message
                        // TODO: Delete Direct message
                    } else if (!msg.reblogged) {
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
            MessageListContextMenuItem.GET_MESSAGE.addTo(menu, order++, R.string.get_message);
        } catch (Exception e) {
            MyLog.e(this, method, e);
        }
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

    protected void saveContextOfSelectedItem(View v) {
        final String method = "saveContextOfSelectedItem";
        super.saveContextOfSelectedItem(v);
        setMessageForAccount(method,
                mViewItem == null ? 0 : ((MessageViewItem) mViewItem).getMsgId(),
                getMyActor().isValid() || mViewItem == null ? getMyActor() :
                        ((MessageViewItem) mViewItem).getLinkedMyAccount());
    }

    private void setMessageForAccount(String method, long msgId, MyAccount myActorIn) {
        MyAccount myActor = myActorIn == null ? MyAccount.EMPTY : myActorIn;
        MyAccount currentMyAccount = menuContainer.getCurrentMyAccount();
        long originId = MyQuery.msgIdToOriginId(msgId);
        MyAccount ma1 = getMyContext().persistentAccounts()
                .getAccountForThisMessage(originId, myActor, currentMyAccount, false);
        MessageForAccount msgNew = new MessageForAccount(msgId, originId, ma1);
        if (!ma1.equals(currentMyAccount)
                && ma1.isValid() && !myActor.isValid() && !msg.isTiedToThisAccount()
                && !menuContainer.getTimeline().getTimelineType().isForUser()
                && currentMyAccount.isValid() && ma1.getOriginId() == currentMyAccount.getOriginId()) {
            msgNew = new MessageForAccount(msgId, originId, currentMyAccount);
        }
        MyLog.v(this, method + "; myActor=" + myActor +
                (msgNew.getMyAccount().equals(myActor) ? "" : " -> " + msgNew.getMyAccount()) +
                "; msgId=" + msgId);
        setMyActor(msgNew.getMyAccount());
        msg = msgNew.getMyAccount().isValid() ? msgNew : MessageForAccount.EMPTY;
    }

    private boolean isEditorVisible() {
        return menuContainer.getMessageEditor().isVisible();
    }

    protected long getCurrentMyAccountUserId() {
        return menuContainer.getCurrentMyAccount().getUserId();
    }

    public void onContextItemSelected(MenuItem item) {
        if (item != null) {
            setSelectedItemTitle(String.valueOf(item.getTitle()));
            onContextMenuItemSelected(MessageListContextMenuItem.fromId(item.getItemId()), getMsgId(), getMyActor());
        }
    }

    public void onContextMenuItemSelected(MessageListContextMenuItem contextMenuItem, long msgId,
                                          MyAccount actor) {
        final String method = "onContextMenuItemSelected";
        setMessageForAccount(method + "; " + contextMenuItem, msgId, actor);
        contextMenuItem.execute(this);
    }

    public void switchTimelineActivityView(Timeline timeline) {
        if (TimelineActivity.class.isAssignableFrom(getActivity().getClass())) {
            ((TimelineActivity) getActivity()).switchView(timeline, null);
        } else {
            TimelineActivity.startForTimeline(getMyContext(), getActivity(),  timeline, null, false);
        }
    }

    public void loadState(Bundle savedInstanceState) {
        if (savedInstanceState != null && savedInstanceState.containsKey(IntentExtra.ITEM_ID.key)) {
            setMessageForAccount("loadState",
                    savedInstanceState.getLong(IntentExtra.ITEM_ID.key, 0),
                    menuContainer.getActivity().getMyContext().persistentAccounts().fromAccountName(
                            savedInstanceState.getString(IntentExtra.ACCOUNT_NAME.key,
                            menuContainer.getCurrentMyAccount().getAccountName()))
            );
        }
    }

    public void saveState(Bundle outState) {
        outState.putLong(IntentExtra.ITEM_ID.key, getMsgId());
        outState.putString(IntentExtra.ACCOUNT_NAME.key, msg.getMyAccount().getAccountName());
    }

    public long getMsgId() {
        return msg.msgId;
    }

    public Origin getOrigin() {
        return msg == null ? Origin.getEmpty() : msg.origin;
    }

    @NonNull
    public String getSelectedItemTitle() {
        return selectedItemTitle;
    }

    public void setSelectedItemTitle(String selectedItemTitle) {
        this.selectedItemTitle = StringUtils.notNull(selectedItemTitle);
    }
}
