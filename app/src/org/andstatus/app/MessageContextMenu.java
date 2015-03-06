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

package org.andstatus.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.text.TextUtils;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View;
import android.view.View.OnCreateContextMenuListener;
import android.widget.AdapterView;
import android.widget.TextView;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.MessageForAccount;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.TimelineTypeEnum;
import org.andstatus.app.util.MyLog;

/**
 * Context menu and corresponding actions on messages from the list 
 * @author yvolk@yurivolkov.com
 */
public class MessageContextMenu implements OnCreateContextMenuListener {

    ActionableMessageList messageList;
    
    private View viewOfTheContext = null;
    /**
     * Id of the Message that was selected (clicked, or whose context menu item
     * was selected) TODO: clicked, restore position...
     */
    private long mMsgId = 0;
    /**
     *  Corresponding account information ( "Reply As..." ... ) 
     *  oh whose behalf we are going to execute an action on this line in the list (message...) 
     */
    private long actorUserIdForCurrentMessage = 0;
    String imageFilename = null;

    public void setAccountUserIdToActAs(long accountUserIdToActAs) {
        this.accountUserIdToActAs = accountUserIdToActAs;
    }
    private long accountUserIdToActAs;

    public MessageContextMenu(ActionableMessageList actionableMessageList) {
        messageList = actionableMessageList;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        long userIdForThisMessage = accountUserIdToActAs;
        viewOfTheContext = v;
        if (menuInfo != null) {
            AdapterView.AdapterContextMenuInfo info;
            try {
                info = (AdapterView.AdapterContextMenuInfo) menuInfo;
            } catch (ClassCastException e) {
                MyLog.e(this, "bad menuInfo", e);
                return;
            }

            mMsgId = info.id;
            if (userIdForThisMessage == 0) {
                userIdForThisMessage = messageList.getLinkedUserIdFromCursor(info.position);
            }
        } else {
            TextView id = (TextView) v.findViewById(R.id.id);
            mMsgId = Long.parseLong(id.getText().toString());
            if (userIdForThisMessage == 0) {
                TextView linkedUserId = (TextView) v.findViewById(R.id.linked_user_id);
                userIdForThisMessage = Long.parseLong(linkedUserId.getText().toString());
            }
        }
        actorUserIdForCurrentMessage = 0;
        MessageForAccount msg = new MessageDataForContextMenu(messageList.getActivity(),
                userIdForThisMessage, getCurrentMyAccountUserId(), messageList.getTimelineType(),
                mMsgId, accountUserIdToActAs!=0).getMsg();
        if (!msg.myAccount().isValid()) {
            return;
        }
        actorUserIdForCurrentMessage = msg.myAccount().getUserId();
        accountUserIdToActAs = 0;

        int menuItemId = 0;
        // Create the Context menu
        try {
            menu.setHeaderTitle((MyContextHolder.get().persistentAccounts().size() > 1 ? msg.myAccount().shortestUniqueAccountName() + ": " : "")
                    + msg.bodyTrimmed);

            if (!msg.isDirect()) {
                ContextMenuItem.REPLY.addTo(menu, menuItemId++, R.string.menu_item_reply);
                ContextMenuItem.REPLY_ALL.addTo(menu, menuItemId++, R.string.menu_item_reply_all);
            }
            ContextMenuItem.SHARE.addTo(menu, menuItemId++, R.string.menu_item_share);
            if (!TextUtils.isEmpty(msg.imageFilename)) {
                imageFilename = msg.imageFilename;
                ContextMenuItem.VIEW_IMAGE.addTo(menu, menuItemId++, R.string.menu_item_view_image);
            }

            // TODO: Only if he follows me?
            ContextMenuItem.DIRECT_MESSAGE.addTo(menu, menuItemId++,
                    R.string.menu_item_direct_message);

            if (!msg.isDirect()) {
                if (msg.favorited) {
                    ContextMenuItem.DESTROY_FAVORITE.addTo(menu, menuItemId++,
                            R.string.menu_item_destroy_favorite);
                } else {
                    ContextMenuItem.FAVORITE.addTo(menu, menuItemId++,
                            R.string.menu_item_favorite);
                }
                if (msg.reblogged) {
                    ContextMenuItem.DESTROY_REBLOG.addTo(menu, menuItemId++,
                            msg.myAccount().alternativeTermForResourceId(R.string.menu_item_destroy_reblog));
                } else {
                    // Don't allow a User to reblog himself
                    if (actorUserIdForCurrentMessage != msg.senderId) {
                        ContextMenuItem.REBLOG.addTo(menu, menuItemId++,
                                msg.myAccount().alternativeTermForResourceId(R.string.menu_item_reblog));
                    }
                }
            }

            if (messageList.getSelectedUserId() != msg.senderId) {
                /*
                 * Messages by the Sender of this message ("User timeline" of
                 * that user)
                 */
                ContextMenuItem.SENDER_MESSAGES.addTo(menu, menuItemId++,
                        String.format(
                                getContext().getText(R.string.menu_item_user_messages).toString(),
                                MyProvider.userIdToWebfingerId(msg.senderId)));
            }

            if (messageList.getSelectedUserId() != msg.authorId && msg.senderId != msg.authorId) {
                /*
                 * Messages by the Author of this message ("User timeline" of
                 * that user)
                 */
                ContextMenuItem.AUTHOR_MESSAGES.addTo(menu, menuItemId++,
                        String.format(
                                getContext().getText(R.string.menu_item_user_messages).toString(),
                                MyProvider.userIdToWebfingerId(msg.authorId)));
            }

            ContextMenuItem.OPEN_MESSAGE_PERMALINK.addTo(menu, menuItemId++, R.string.menu_item_open_message_permalink);
            ContextMenuItem.OPEN_CONVERSATION.addTo(menu, menuItemId++, R.string.menu_item_open_conversation);
            
            if (msg.isSender) {
                // This message is by current User, hence we may delete it.
                if (msg.isDirect()) {
                    // This is a Direct Message
                    // TODO: Delete Direct message
                } else if (!msg.reblogged) {
                    ContextMenuItem.DESTROY_STATUS.addTo(menu, menuItemId++,
                            R.string.menu_item_destroy_status);
                }
            }

            if (!msg.isSender) {
                if (msg.senderFollowed) {
                    ContextMenuItem.STOP_FOLLOWING_SENDER.addTo(menu, menuItemId++,
                            String.format(
                                    getContext().getText(R.string.menu_item_stop_following_user).toString(),
                                    MyProvider.userIdToWebfingerId(msg.senderId)));
                } else {
                    ContextMenuItem.FOLLOW_SENDER.addTo(menu, menuItemId++,
                            String.format(
                                    getContext().getText(R.string.menu_item_follow_user).toString(),
                                    MyProvider.userIdToWebfingerId(msg.senderId)));
                }
            }
            if (!msg.isAuthor && (msg.authorId != msg.senderId)) {
                if (msg.authorFollowed) {
                    ContextMenuItem.STOP_FOLLOWING_AUTHOR.addTo(menu, menuItemId++,
                            String.format(
                                    getContext().getText(R.string.menu_item_stop_following_user).toString(),
                                    MyProvider.userIdToWebfingerId(msg.authorId)));
                } else {
                    ContextMenuItem.FOLLOW_AUTHOR.addTo(menu, menuItemId++,
                            String.format(
                                    getContext().getText(R.string.menu_item_follow_user).toString(),
                                    MyProvider.userIdToWebfingerId(msg.authorId)));
                }
            }
            switch (msg.myAccount().numberOfAccountsOfThisOrigin()) {
                case 1:
                    break;
                case 2:
                    ContextMenuItem.ACT_AS_USER.addTo(menu, menuItemId++,
                            String.format(
                                    getContext().getText(R.string.menu_item_act_as_user).toString(),
                                    msg.myAccount().firstOtherAccountOfThisOrigin().shortestUniqueAccountName()));
                    break;
                default:
                    ContextMenuItem.ACT_AS.addTo(menu, menuItemId++, R.string.menu_item_act_as);
                    break;
            }
        } catch (Exception e) {
            MyLog.e(this, "onCreateContextMenu", e);
        }
    }

    protected long getCurrentMyAccountUserId() {
        return messageList.getCurrentMyAccountUserId();
    }

    protected Context getContext() {
        return messageList.getActivity();
    }
    
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info;
        try {
            info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
            if (info != null) {
                mMsgId = info.id;
            }
        } catch (ClassCastException e) {
            MyLog.e(this, "bad menuInfo", e);
            return false;
        }
        if (mMsgId == 0) {
            MyLog.e(this, "message id == 0");
            return false;
        }

        MyAccount ma = MyContextHolder.get().persistentAccounts().fromUserId(actorUserIdForCurrentMessage);
        if (ma.isValid()) {
            ContextMenuItem contextMenuItem = ContextMenuItem.fromId(item.getItemId());
            MyLog.v(this, "onContextItemSelected: " + contextMenuItem + "; actor=" + ma.getAccountName());
            return contextMenuItem.execute(this, ma);
        } else {
            return false;
        }
    }

    void switchTimelineActivity(TimelineTypeEnum timelineType, boolean isTimelineCombined, long selectedUserId) {
        Intent intent;
        if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
            MyLog.v(this, "switchTimelineActivity; " + timelineType 
                    + "; isCombined=" + (isTimelineCombined ? "yes" : "no")
                    + "; userId=" + selectedUserId);
        }
        
        // Actually we use one Activity for all timelines...
        intent = new Intent(getContext(), TimelineActivity.class);
        intent.putExtra(IntentExtra.EXTRA_TIMELINE_TYPE.key, TimelineTypeSelector.selectableType(timelineType).save());
        intent.putExtra(IntentExtra.EXTRA_TIMELINE_IS_COMBINED.key, isTimelineCombined);
        intent.putExtra(IntentExtra.EXTRA_SELECTEDUSERID.key, selectedUserId);
        // We don't use Intent.ACTION_SEARCH action anywhere, so there is no need it setting it.
        // - we're analyzing query instead!
        messageList.getActivity().startActivity(intent);
    }

    public void showContextMenu() {
        if (viewOfTheContext != null) {
            viewOfTheContext.post(new Runnable() {

                @Override
                public void run() {
                    try {
                        viewOfTheContext.showContextMenu();
                    } catch (NullPointerException e) {
                        MyLog.d(this, "on showContextMenu; " + (viewOfTheContext != null ? "viewOfTheContext is not null" : ""), e);
                    }
                }
            });                    
        }
    }
    
    public void loadState(SharedPreferences savedInstanceState) {
        if (savedInstanceState != null 
                && savedInstanceState.contains(IntentExtra.EXTRA_ITEMID.key)) {
            mMsgId = savedInstanceState.getLong(IntentExtra.EXTRA_ITEMID.key, 0);
        }
    }
    
    public long getMsgId() {
        return mMsgId;
    }

    public void saveState(Editor outState) {
        if (outState != null) {
            outState.putLong(IntentExtra.EXTRA_ITEMID.key, mMsgId);
        }
    }
}
