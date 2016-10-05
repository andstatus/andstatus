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
import android.text.TextUtils;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;

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

/**
 * Context menu and corresponding actions on messages from the list 
 * @author yvolk@yurivolkov.com
 */
public class MessageContextMenu extends MyContextMenu {

    public final ActionableMessageList messageList;
    
    /**
     * Id of the Message that was selected (clicked, or whose context menu item
     * was selected) TODO: clicked, restore position...
     */
    private long mMsgId = 0;
    public String imageFilename = null;

    private MessageForAccount msg;

    public MessageContextMenu(ActionableMessageList actionableMessageList) {
        super(actionableMessageList.getActivity());
        messageList = actionableMessageList;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        final String method = "onCreateContextMenu";
        super.onCreateContextMenu(menu, v, menuInfo);
        if (msg == null) {
            return;
        }

        MessageViewItem viewItem = (MessageViewItem) oViewItem;
        int order = 0;
        try {
            new ContextMenuHeader(getActivity(), menu).setTitle(msg.bodyTrimmed)
                    .setSubtitle(msg.getMyAccount().getAccountName());

            MessageListContextMenuItem.OPEN_CONVERSATION.addTo(menu, order++, R.string.menu_item_open_conversation);
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

            if (messageList.getTimeline().getUserId() != msg.senderId) {
                // Messages by a Sender of this message ("User timeline" of that user)
                MessageListContextMenuItem.SENDER_MESSAGES.addTo(menu, order++,
                        String.format(
                                getActivity().getText(R.string.menu_item_user_messages).toString(),
                                MyQuery.userIdToWebfingerId(msg.senderId)));
            }

            if (messageList.getTimeline().getUserId() != msg.authorId && msg.senderId != msg.authorId) {
                // Messages by an Author of this message ("User timeline" of that user)
                MessageListContextMenuItem.AUTHOR_MESSAGES.addTo(menu, order++,
                        String.format(
                                getActivity().getText(R.string.menu_item_user_messages).toString(),
                                MyQuery.userIdToWebfingerId(msg.authorId)));
            }

            if (msg.isLoaded() && !msg.isDirect() && !isEditorVisible()) {
                MessageListContextMenuItem.REPLY.addTo(menu, order++, R.string.menu_item_reply);
                MessageListContextMenuItem.REPLY_ALL.addTo(menu, order++, R.string.menu_item_reply_all);
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
                if (msg.isDirect()) {
                    // This is a Direct Message
                    // TODO: Delete Direct message
                } else if (!msg.reblogged) {
                    MessageListContextMenuItem.DESTROY_STATUS.addTo(menu, order++,
                            R.string.menu_item_destroy_status);
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

    protected void saveContextOfSelectedItem(View v) {
        final String method = "saveContextOfSelectedItem";
        mMsgId = 0;
        msg = null;
        super.saveContextOfSelectedItem(v);
        if (oViewItem == null) {
            return;
        }

        MyAccount myActorForThisMessage = getMyActor();
        String logMsg = method;
        MessageViewItem viewItem = (MessageViewItem) oViewItem;
        mMsgId = viewItem.getMsgId();
        logMsg += "; id=" + mMsgId;
        if (!myActorForThisMessage.isValid()) {
            myActorForThisMessage = viewItem.getLinkedMyAccount();
        }
        MyLog.v(this, logMsg);

        MessageForAccount msg2 = getMessageForAccount(myActorForThisMessage, messageList.getCurrentMyAccount());
        setMyActor(msg2.getMyAccount());
        if (msg2.getMyAccount().isValid()) {
            msg = msg2;
        }
    }

    private MessageForAccount getMessageForAccount(MyAccount linkedUser, MyAccount currentMyAccount) {
        long originId = MyQuery.msgIdToOriginId(mMsgId);
        MyAccount ma1 = getMyContext().persistentAccounts()
                .getAccountForThisMessage(originId, mMsgId, linkedUser, currentMyAccount, false);
        MessageForAccount msg = new MessageForAccount(mMsgId, originId, ma1);
        boolean forceFirstUser = myActor.isValid();
        if (ma1.isValid() && !forceFirstUser
                && !msg.isTiedToThisAccount()
                && ma1.getUserId() != currentMyAccount.getUserId()
                && !messageList.getTimeline().getTimelineType().isForUser()) {
            if (currentMyAccount.isValid() && ma1.getOriginId() == currentMyAccount.getOriginId()) {
                msg = new MessageForAccount(mMsgId, originId, currentMyAccount);
            }
        }
        return msg;
    }

    private boolean isEditorVisible() {
        return messageList.getMessageEditor().isVisible();
    }

    protected long getCurrentMyAccountUserId() {
        return messageList.getCurrentMyAccount().getUserId();
    }

    public void onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info;
        String msgInfo = "";
        try {
            info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
            if (info != null) {
                mMsgId = info.id;
            } else {
                msgInfo = "; info==null";
            }
        } catch (ClassCastException e) {
            MyLog.e(this, "bad menuInfo", e);
            return;
        }
        if (mMsgId == 0) {
            MyLog.e(this, "message id == 0" + msgInfo);
            return;
        }
        onContextMenuItemSelected(MessageListContextMenuItem.fromId(item.getItemId()), mMsgId,
                getMyActor());
    }

    public void onContextMenuItemSelected(MessageListContextMenuItem contextMenuItem, long msgId,
                                          MyAccount actor) {
        final String method = "onContextMenuItemSelected";
        if (msgId == 0 || !actor.isValid()) {
            MyLog.d(this, method + "; msgId=" + msgId + "; myActor=" + actor);
            return;
        }
        mMsgId = msgId;
        setMyActor(actor);
        MyLog.v(this, method + "; " + contextMenuItem
                + "; myActor=" + actor + "; msgId=" + msgId);
        contextMenuItem.execute(this);
    }

    public void switchTimelineActivityView(Timeline timeline) {
        if (TimelineActivity.class.isAssignableFrom(getActivity().getClass())) {
            ((TimelineActivity) getActivity()).switchView(timeline, null);
        } else {
            TimelineActivity.startForTimeline(getMyContext(), getActivity(),  timeline, null);
        }
    }

    public void loadState(Bundle savedInstanceState) {
        if (savedInstanceState != null 
                && savedInstanceState.containsKey(IntentExtra.ITEM_ID.key)) {
            mMsgId = savedInstanceState.getLong(IntentExtra.ITEM_ID.key, 0);
        }
    }

    public void saveState(Bundle outState) {
        outState.putLong(IntentExtra.ITEM_ID.key, mMsgId);
    }

    public long getMsgId() {
        return mMsgId;
    }

    public Origin getOrigin() {
        return msg == null ? Origin.getEmpty() : msg.origin;
    }
}
