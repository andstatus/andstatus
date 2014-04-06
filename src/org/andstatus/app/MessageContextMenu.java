/*
 * Copyright (C) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

import static org.andstatus.app.ContextMenuItem.ACT_AS;
import static org.andstatus.app.ContextMenuItem.ACT_AS_USER;
import static org.andstatus.app.ContextMenuItem.AUTHOR_MESSAGES;
import static org.andstatus.app.ContextMenuItem.DESTROY_FAVORITE;
import static org.andstatus.app.ContextMenuItem.DESTROY_REBLOG;
import static org.andstatus.app.ContextMenuItem.DESTROY_STATUS;
import static org.andstatus.app.ContextMenuItem.DIRECT_MESSAGE;
import static org.andstatus.app.ContextMenuItem.FAVORITE;
import static org.andstatus.app.ContextMenuItem.FOLLOW_AUTHOR;
import static org.andstatus.app.ContextMenuItem.FOLLOW_SENDER;
import static org.andstatus.app.ContextMenuItem.REBLOG;
import static org.andstatus.app.ContextMenuItem.REPLY;
import static org.andstatus.app.ContextMenuItem.SENDER_MESSAGES;
import static org.andstatus.app.ContextMenuItem.SHARE;
import static org.andstatus.app.ContextMenuItem.STOP_FOLLOWING_AUTHOR;
import static org.andstatus.app.ContextMenuItem.STOP_FOLLOWING_SENDER;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View;
import android.view.View.OnCreateContextMenuListener;
import android.widget.AdapterView;
import android.widget.TextView;

import org.andstatus.app.account.AccountSelector;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.TimelineTypeEnum;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.MyLog;

/**
 * Context menu and corresponding actions on messages from the list 
 * @author yvolk@yurivolkov.com
 */
public class MessageContextMenu implements OnCreateContextMenuListener {

    private ActionableMessageList messageList;
    
    private View viewOfTheContext = null;
    /**
     * Id of the Message that was selected (clicked, or whose context menu item
     * was selected) TODO: clicked, restore position...
     */
    private long mCurrentMsgId = 0;
    /**
     *  Corresponding account information ( "Reply As..." ... ) 
     *  oh whose behalf we are going to execute an action on this line in the list (message...) 
     */
    private long actorUserIdForCurrentMessage = 0;

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

            mCurrentMsgId = info.id;
            if (userIdForThisMessage == 0) {
                userIdForThisMessage = messageList.getLinkedUserIdFromCursor(info.position);
            }
        } else {
            TextView id = (TextView) v.findViewById(R.id.id);
            mCurrentMsgId = Long.parseLong(id.getText().toString());
            if (userIdForThisMessage == 0) {
                TextView linkedUserId = (TextView) v.findViewById(R.id.linked_user_id);
                userIdForThisMessage = Long.parseLong(linkedUserId.getText().toString());
            }
        }
        actorUserIdForCurrentMessage = 0;
        MessageDataForContextMenu md = new MessageDataForContextMenu(messageList.getActivity(), userIdForThisMessage, messageList.getCurrentMyAccountUserId(), messageList.getTimelineType(), mCurrentMsgId
                );
        if (md.ma == null) {
            return;
        }
        if (accountUserIdToActAs==0 && md.canUseSecondAccountInsteadOfFirst) {
            // Yes, use current Account!
            md = new MessageDataForContextMenu(getContext(), messageList.getCurrentMyAccountUserId(), 0, messageList.getTimelineType(), mCurrentMsgId);
        }
        actorUserIdForCurrentMessage = md.ma.getUserId();
        accountUserIdToActAs = 0;

        int menuItemId = 0;
        // Create the Context menu
        try {
            menu.setHeaderTitle((MyContextHolder.get().persistentAccounts().size() > 1 ? md.ma.shortestUniqueAccountName() + ": " : "")
                    + md.body);

            // Add menu items
            if (!md.isDirect) {
                REPLY.addTo(menu, menuItemId++, R.string.menu_item_reply);
            }
            SHARE.addTo(menu, menuItemId++, R.string.menu_item_share);

            // TODO: Only if he follows me?
            DIRECT_MESSAGE.addTo(menu, menuItemId++,
                    R.string.menu_item_direct_message);

            if (!md.isDirect) {
                if (md.favorited) {
                    DESTROY_FAVORITE.addTo(menu, menuItemId++,
                            R.string.menu_item_destroy_favorite);
                } else {
                    FAVORITE.addTo(menu, menuItemId++,
                            R.string.menu_item_favorite);
                }
                if (md.reblogged) {
                    DESTROY_REBLOG.addTo(menu, menuItemId++,
                            md.ma.alternativeTermForResourceId(R.string.menu_item_destroy_reblog));
                } else {
                    // Don't allow a User to reblog himself
                    if (actorUserIdForCurrentMessage != md.senderId) {
                        REBLOG.addTo(menu, menuItemId++,
                                md.ma.alternativeTermForResourceId(R.string.menu_item_reblog));
                    }
                }
            }

            if (messageList.getSelectedUserId() != md.senderId) {
                /*
                 * Messages by the Sender of this message ("User timeline" of
                 * that user)
                 */
                SENDER_MESSAGES.addTo(menu, menuItemId++,
                        String.format(MyContextHolder.get().getLocale(),
                                getContext().getText(R.string.menu_item_user_messages).toString(),
                                MyProvider.userIdToName(md.senderId)));
            }

            if (messageList.getSelectedUserId() != md.authorId && md.senderId != md.authorId) {
                /*
                 * Messages by the Author of this message ("User timeline" of
                 * that user)
                 */
                AUTHOR_MESSAGES.addTo(menu, menuItemId++,
                        String.format(MyContextHolder.get().getLocale(),
                                getContext().getText(R.string.menu_item_user_messages).toString(),
                                MyProvider.userIdToName(md.authorId)));
            }

            if (md.isSender) {
                // This message is by current User, hence we may delete it.
                if (md.isDirect) {
                    // This is a Direct Message
                    // TODO: Delete Direct message
                } else if (!md.reblogged) {
                    DESTROY_STATUS.addTo(menu, menuItemId++,
                            R.string.menu_item_destroy_status);
                }
            }

            if (!md.isSender) {
                if (md.senderFollowed) {
                    STOP_FOLLOWING_SENDER.addTo(menu, menuItemId++,
                            String.format(MyContextHolder.get().getLocale(),
                                    getContext().getText(R.string.menu_item_stop_following_user).toString(),
                                    MyProvider.userIdToName(md.senderId)));
                } else {
                    FOLLOW_SENDER.addTo(menu, menuItemId++,
                            String.format(MyContextHolder.get().getLocale(),
                                    getContext().getText(R.string.menu_item_follow_user).toString(),
                                    MyProvider.userIdToName(md.senderId)));
                }
            }
            if (!md.isAuthor && (md.authorId != md.senderId)) {
                if (md.authorFollowed) {
                    STOP_FOLLOWING_AUTHOR.addTo(menu, menuItemId++,
                            String.format(MyContextHolder.get().getLocale(),
                                    getContext().getText(R.string.menu_item_stop_following_user).toString(),
                                    MyProvider.userIdToName(md.authorId)));
                } else {
                    FOLLOW_AUTHOR.addTo(menu, menuItemId++,
                            String.format(MyContextHolder.get().getLocale(),
                                    getContext().getText(R.string.menu_item_follow_user).toString(),
                                    MyProvider.userIdToName(md.authorId)));
                }
            }
            switch (md.ma.accountsOfThisOrigin()) {
                case 1:
                    break;
                case 2:
                    ACT_AS_USER.addTo(menu, menuItemId++,
                            String.format(MyContextHolder.get().getLocale(),
                                    getContext().getText(R.string.menu_item_act_as_user).toString(),
                                    md.ma.firstOtherAccountOfThisOrigin().shortestUniqueAccountName()));
                    break;
                default:
                    ACT_AS.addTo(menu, menuItemId++, R.string.menu_item_act_as);
                    break;
            }
        } catch (Exception e) {
            MyLog.e(this, "onCreateContextMenu", e);
        }
    }

    private Context getContext() {
        return messageList.getActivity();
    }
    
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info;
        try {
            info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
            if (info != null) {
                mCurrentMsgId = info.id;
            }
        } catch (ClassCastException e) {
            MyLog.e(this, "bad menuInfo", e);
            return false;
        }
        if (mCurrentMsgId == 0) {
            MyLog.e(this, "message id == 0");
            return false;
        }

        MyAccount ma = MyContextHolder.get().persistentAccounts().fromUserId(actorUserIdForCurrentMessage);
        if (ma != null) {
            long authorId;
            long senderId;
            ContextMenuItem contextMenuItem = ContextMenuItem.fromId(item.getItemId());
            MyLog.v(this, "onContextItemSelected: " + contextMenuItem + "; actor=" + ma.getAccountName());
            switch (contextMenuItem) {
                case REPLY:
                    messageList.getMessageEditor().startEditingMessage("", mCurrentMsgId, 0, ma, messageList.isTimelineCombined());
                    return true;
                case DIRECT_MESSAGE:
                    authorId = MyProvider.msgIdToUserId(MyDatabase.Msg.AUTHOR_ID, mCurrentMsgId);
                    if (authorId != 0) {
                        messageList.getMessageEditor().startEditingMessage("", mCurrentMsgId, authorId, ma, messageList.isTimelineCombined());
                        return true;
                    }
                    break;
                case REBLOG:
                    MyServiceManager.sendCommand( new CommandData(CommandEnum.REBLOG, ma.getAccountName(), mCurrentMsgId));
                    return true;
                case DESTROY_REBLOG:
                    MyServiceManager.sendCommand( new CommandData(CommandEnum.DESTROY_REBLOG, ma.getAccountName(), mCurrentMsgId));
                    return true;
                case DESTROY_STATUS:
                    MyServiceManager.sendCommand( new CommandData(CommandEnum.DESTROY_STATUS, ma.getAccountName(), mCurrentMsgId));
                    return true;
                case FAVORITE:
                    MyServiceManager.sendCommand( new CommandData(CommandEnum.CREATE_FAVORITE, ma.getAccountName(), mCurrentMsgId));
                    return true;
                case DESTROY_FAVORITE:
                    MyServiceManager.sendCommand( new CommandData(CommandEnum.DESTROY_FAVORITE, ma.getAccountName(), mCurrentMsgId));
                    return true;
                case SHARE:
                    String userName = MyProvider.msgIdToUsername(MyDatabase.Msg.AUTHOR_ID, mCurrentMsgId);
                    Uri uri = MyProvider.getTimelineMsgUri(ma.getUserId(), messageList.getTimelineType(), true, mCurrentMsgId);
                    Cursor cursor = null;
                    try {
                        cursor = getContext().getContentResolver().query(uri, new String[] {
                                MyDatabase.Msg.MSG_ID, MyDatabase.Msg.BODY
                        }, null, null, null);
                        if (cursor != null && cursor.getCount() > 0) {
                            cursor.moveToFirst();
        
                            StringBuilder subject = new StringBuilder();
                            StringBuilder text = new StringBuilder();
                            String msgBody = cursor.getString(cursor.getColumnIndex(MyDatabase.Msg.BODY));
        
                            subject.append(getContext().getText(ma.alternativeTermForResourceId(R.string.message)));
                            subject.append(" - " + msgBody);
                            int maxlength = 80;
                            if (subject.length() > maxlength) {
                                subject.setLength(maxlength);
                                // Truncate at the last space
                                subject.setLength(subject.lastIndexOf(" "));
                                subject.append("...");
                            }
        
                            text.append(msgBody);
                            text.append("\n-- \n" + userName);
                            text.append("\n URL: " + ma.messagePermalink(userName, 
                                    cursor.getLong(cursor.getColumnIndex(MyDatabase.Msg.MSG_ID))));
                            
                            Intent share = new Intent(android.content.Intent.ACTION_SEND); 
                            share.setType("text/plain"); 
                            share.putExtra(Intent.EXTRA_SUBJECT, subject.toString()); 
                            share.putExtra(Intent.EXTRA_TEXT, text.toString()); 
                            messageList.getActivity().startActivity(Intent.createChooser(share, getContext().getText(R.string.menu_item_share)));
                        }
                    } catch (Exception e) {
                        MyLog.e(this, "onContextItemSelected", e);
                        return false;
                    } finally {
                        DbUtils.closeSilently(cursor);
                    }
                    return true;
                case SENDER_MESSAGES:
                    senderId = MyProvider.msgIdToUserId(MyDatabase.Msg.SENDER_ID, mCurrentMsgId);
                    if (senderId != 0) {
                        /**
                         * We better switch to the account selected for this message in order not to
                         * add new "MsgOfUser" entries hence duplicated messages in the combined timeline 
                         */
                        MyContextHolder.get().persistentAccounts().setCurrentAccount(ma);
                        switchTimelineActivity(TimelineTypeEnum.USER, messageList.isTimelineCombined(), senderId);
                        return true;
                    }
                    break;
                case AUTHOR_MESSAGES:
                    authorId = MyProvider.msgIdToUserId(MyDatabase.Msg.AUTHOR_ID, mCurrentMsgId);
                    if (authorId != 0) {
                        /**
                         * We better switch to the account selected for this message in order not to
                         * add new "MsgOfUser" entries hence duplicated messages in the combined timeline 
                         */
                        MyContextHolder.get().persistentAccounts().setCurrentAccount(ma);
                        switchTimelineActivity(TimelineTypeEnum.USER, messageList.isTimelineCombined(), authorId);
                        return true;
                    }
                    break;
                case FOLLOW_SENDER:
                    senderId = MyProvider.msgIdToUserId(MyDatabase.Msg.SENDER_ID, mCurrentMsgId);
                    MyServiceManager.sendCommand( new CommandData(CommandEnum.FOLLOW_USER, ma.getAccountName(), senderId));
                    return true;
                case STOP_FOLLOWING_SENDER:
                    senderId = MyProvider.msgIdToUserId(MyDatabase.Msg.SENDER_ID, mCurrentMsgId);
                    MyServiceManager.sendCommand( new CommandData(CommandEnum.STOP_FOLLOWING_USER, ma.getAccountName(), senderId));
                    return true;
                case FOLLOW_AUTHOR:
                    authorId = MyProvider.msgIdToUserId(MyDatabase.Msg.AUTHOR_ID, mCurrentMsgId);
                    MyServiceManager.sendCommand( new CommandData(CommandEnum.FOLLOW_USER, ma.getAccountName(), authorId));
                    return true;
                case STOP_FOLLOWING_AUTHOR:
                    authorId = MyProvider.msgIdToUserId(MyDatabase.Msg.AUTHOR_ID, mCurrentMsgId);
                    MyServiceManager.sendCommand( new CommandData(CommandEnum.STOP_FOLLOWING_USER, ma.getAccountName(), authorId));
                    return true;
                case ACT_AS:
                    Intent i = new Intent(getContext(), AccountSelector.class);
                    i.putExtra(IntentExtra.ORIGIN_ID.key, ma.getOriginId());
                    messageList.getActivity().startActivityForResult(i, ActivityRequestCode.SELECT_ACCOUNT_TO_ACT_AS.id);
                    return true;
                case ACT_AS_USER:
                    setAccountUserIdToActAs(ma.firstOtherAccountOfThisOrigin().getUserId());
                    showContextMenu();
                    return true;
                default:
                    break;
            }
        }

        return false;
    }

    void switchTimelineActivity(TimelineTypeEnum timelineType, boolean isTimelineCombined, long selectedUserId) {
        Intent intent;
        if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
            MyLog.v(this, "switchTimelineActivity; " + timelineType + "; isCombined=" + (isTimelineCombined ? "yes" : "no"));
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
                    viewOfTheContext.showContextMenu();
                }
            });                    
        }
    }
    
    public void loadState(Bundle savedInstanceState) {
        if (savedInstanceState != null 
                && savedInstanceState.containsKey(IntentExtra.EXTRA_ITEMID.key)) {
            mCurrentMsgId = savedInstanceState.getLong(IntentExtra.EXTRA_ITEMID.key);
        }
    }
    
    public void saveState(Bundle outState) {
        if (outState != null) {
            outState.putLong(IntentExtra.EXTRA_ITEMID.key, mCurrentMsgId);
        }
    }
}
