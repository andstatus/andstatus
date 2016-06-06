/**
 * Copyright (C) 2013-2015 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Menu;

import org.andstatus.app.ActivityRequestCode;
import org.andstatus.app.ContextMenuItem;
import org.andstatus.app.MyAction;
import org.andstatus.app.account.AccountSelector;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.FileProvider;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.TimelineType;
import org.andstatus.app.database.MsgTable;
import org.andstatus.app.os.AsyncTaskLauncher;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.user.UserListType;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.MyLog;

public enum MessageListContextMenuItem implements ContextMenuItem {
    REPLY(true) {
        @Override
        MessageEditorData executeAsync(MyAccount ma, long msgId) {
            return MessageEditorData.newEmpty(ma).setInReplyToId(msgId).addMentionsToText();
        }

        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            menu.messageList.getMessageEditor().startEditingMessage(editorData);
        }
    },
    EDIT(true) {
        @Override
        MessageEditorData executeAsync(MyAccount maIn, long msgId) {
            return MessageEditorData.load(msgId);
        }

        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            menu.messageList.getMessageEditor().startEditingMessage(editorData);
        }
    },
    RESEND(true) {
        @Override
        MessageEditorData executeAsync(MyAccount maIn, long msgId) {
            MyAccount ma = MyContextHolder.get().persistentAccounts().fromUserId(
                    MyQuery.msgIdToLongColumnValue(MsgTable.SENDER_ID, msgId));
            CommandData commandData = CommandData.newUpdateStatus(ma, msgId);
            MyServiceManager.sendManualForegroundCommand(commandData);
            return null;
        }
    },
    REPLY_ALL(true) {
        @Override
        MessageEditorData executeAsync(MyAccount ma, long msgId) {
            return MessageEditorData.newEmpty(ma).setInReplyToId(msgId).setReplyAll(true).addMentionsToText();
        }

        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            menu.messageList.getMessageEditor().startEditingMessage(editorData);
        }
    },
    DIRECT_MESSAGE(true) {
        @Override
        MessageEditorData executeAsync(MyAccount ma, long msgId) {
            return MessageEditorData.newEmpty(ma).setInReplyToId(msgId)
                    .setRecipientId(MyQuery.msgIdToUserId(MsgTable.AUTHOR_ID, msgId))
                    .addMentionsToText();
        }

        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            if (editorData.recipientId != 0) {
                menu.messageList.getMessageEditor().startEditingMessage(editorData);
            }
        }
    },
    FAVORITE() {
        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            sendMsgCommand(CommandEnum.CREATE_FAVORITE, editorData);
        }
    },
    DESTROY_FAVORITE() {
        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            sendMsgCommand(CommandEnum.DESTROY_FAVORITE, editorData);
        }
    },
    REBLOG() {
        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            sendMsgCommand(CommandEnum.REBLOG, editorData);
        }
    },
    DESTROY_REBLOG() {
        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            sendMsgCommand(CommandEnum.DESTROY_REBLOG, editorData);
        }
    },
    DESTROY_STATUS() {
        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            sendMsgCommand(CommandEnum.DESTROY_STATUS, editorData);
        }
    },
    SHARE(true) {
        private volatile MessageShare messageShare = null;

        @Override
        MessageEditorData executeAsync(MyAccount ma, long msgId) {
            messageShare = new MessageShare(msgId);
            return null;
        }

        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            messageShare.share(menu.getActivity());
        }
    },
    COPY_TEXT(true) {
        @Override
        MessageEditorData executeAsync(MyAccount ma, long msgId) {
            String body = MyQuery.msgIdToStringColumnValue(MsgTable.BODY, msgId);
            if (ma.getOrigin().isHtmlContentAllowed()) {
                body = MyHtml.fromHtml(body);
            }
            return MessageEditorData.newEmpty(ma).setBody(body);
        }

        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            copyMessageText(editorData);
        }
    },
    COPY_AUTHOR(true) {
        @Override
        MessageEditorData executeAsync(MyAccount ma, long msgId) {
            return MessageEditorData.newEmpty(ma).addMentionedUserToText(
                    MyQuery.msgIdToUserId(MsgTable.AUTHOR_ID, msgId));
        }

        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            copyMessageText(editorData);
        }
    },
    SENDER_MESSAGES(true) {
        @Override
        MessageEditorData executeAsync(MyAccount ma, long msgId) {
            return getUserId(ma, msgId, MsgTable.SENDER_ID);
        }

        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            if (editorData.recipientId != 0) {
                /**
                 * We better switch to the account selected for this message in order not to
                 * add new "MsgOfUser" entries hence duplicated messages in the combined timeline 
                 */
                menu.switchTimelineActivity(editorData.ma, TimelineType.USER,
                        menu.messageList.isTimelineCombined(), editorData.recipientId);
            }
        }
    },
    AUTHOR_MESSAGES(true) {
        @Override
        MessageEditorData executeAsync(MyAccount ma, long msgId) {
            return getUserId(ma, msgId, MsgTable.AUTHOR_ID);
        }

        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            if (editorData.recipientId != 0) {
                /**
                 * We better switch to the account selected for this message in order not to
                 * add new "MsgOfUser" entries hence duplicated messages in the combined timeline 
                 */
                menu.switchTimelineActivity(editorData.ma, TimelineType.USER,
                        menu.messageList.isTimelineCombined(), editorData.recipientId);
            }
        }
    },
    FOLLOW_SENDER(true) {
        @Override
        MessageEditorData executeAsync(MyAccount ma, long msgId) {
            return getUserId(ma, msgId, MsgTable.SENDER_ID);
        }

        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            sendUserCommand(CommandEnum.FOLLOW_USER, editorData);
        }
    },
    STOP_FOLLOWING_SENDER(true) {
        @Override
        MessageEditorData executeAsync(MyAccount ma, long msgId) {
            return getUserId(ma, msgId, MsgTable.SENDER_ID);
        }

        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            sendUserCommand(CommandEnum.STOP_FOLLOWING_USER, editorData);
        }
    },
    FOLLOW_AUTHOR(true) {
        @Override
        MessageEditorData executeAsync(MyAccount ma, long msgId) {
            return getUserId(ma, msgId, MsgTable.AUTHOR_ID);
        }

        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            sendUserCommand(CommandEnum.FOLLOW_USER, editorData);
        }
    },
    STOP_FOLLOWING_AUTHOR(true) {
        @Override
        MessageEditorData executeAsync(MyAccount ma, long msgId) {
            return getUserId(ma, msgId, MsgTable.AUTHOR_ID);
        }

        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            sendUserCommand(CommandEnum.STOP_FOLLOWING_USER, editorData);
        }
    },
    PROFILE(),
    BLOCK(),
    ACT_AS_USER() {
        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            menu.setAccountUserIdToActAs(editorData.ma.firstOtherAccountOfThisOrigin().getUserId());
            menu.showContextMenu();
        }
    },
    ACT_AS() {
        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            AccountSelector.selectAccount(menu.getActivity(), ActivityRequestCode.SELECT_ACCOUNT_TO_ACT_AS, editorData.ma.getOriginId());
        }
    },
    OPEN_MESSAGE_PERMALINK(true) {
        private volatile MessageShare messageShare = null;

        @Override
        MessageEditorData executeAsync(MyAccount ma, long msgId) {
            messageShare = new MessageShare(msgId);
            return null;
        }

        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            messageShare.openPermalink(menu.getActivity());
        }
    },
    VIEW_IMAGE() {
        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            FileProvider.viewImage(menu.getActivity(), menu.imageFilename);
        }
    },
    OPEN_CONVERSATION() {
        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            Uri uri = MatchedUri.getTimelineItemUri(editorData.ma.getUserId(),
                    menu.messageList.getTimelineType(), menu.messageList.isTimelineCombined(),
                    menu.messageList.getSelectedUserId(), menu.getMsgId());
            String action = menu.getActivity().getIntent().getAction();
            if (Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action)) {
                if (MyLog.isLoggable(this, MyLog.DEBUG)) {
                    MyLog.d(this, "onItemClick, setData=" + uri);
                }
                menu.getActivity().setResult(Activity.RESULT_OK, new Intent().setData(uri));
            } else {
                if (MyLog.isLoggable(this, MyLog.DEBUG)) {
                    MyLog.d(this, "onItemClick, startActivity=" + uri);
                }
                menu.getActivity().startActivity(MyAction.VIEW_CONVERSATION.getIntent(uri));
            }
        }
    },
    USERS_OF_MESSAGE() {
        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            Uri uri = MatchedUri.getUserListUri(editorData.ma.getUserId(),
                    UserListType.USERS_OF_MESSAGE, menu.messageList.isTimelineCombined(),
                    menu.getMsgId());
            if (MyLog.isLoggable(this, MyLog.DEBUG)) {
                MyLog.d(this, "onItemClick, startActivity=" + uri);
            }
            menu.getActivity().startActivity(MyAction.VIEW_USERS.getIntent(uri));
        }
    },
    NONEXISTENT(),
    UNKNOWN();

    private static final String TAG = MessageListContextMenuItem.class.getSimpleName();
    private final boolean mIsAsync;

    MessageListContextMenuItem() {
        this(false);
    }

    MessageListContextMenuItem(boolean isAsync) {
        this.mIsAsync = isAsync;
    }

    @Override
    public int getId() {
        return Menu.FIRST + ordinal() + 1;
    }
    
    public static MessageListContextMenuItem fromId(int id) {
        for (MessageListContextMenuItem item : MessageListContextMenuItem.values()) {
            if (item.getId() == id) {
                return item;
            }
        }
        return UNKNOWN;
    }

    protected void copyMessageText(MessageEditorData editorData) {
        MyLog.v(this, "text='" + editorData.body + "'");
        if (!TextUtils.isEmpty(editorData.body)) {
            // http://developer.android.com/guide/topics/text/copy-paste.html
            ClipboardManager clipboard = (ClipboardManager) MyContextHolder.get().context().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText(I18n.trimTextAt(editorData.body, 40), editorData.body);
            clipboard.setPrimaryClip(clip);
            MyLog.v(this, "clip='" + clip.toString() + "'");
        }
    }

    public void addTo(Menu menu, int order, int titleRes) {
        menu.add(Menu.NONE, this.getId(), order, titleRes);
    }

    public void addTo(Menu menu, int order, CharSequence title) {
        menu.add(Menu.NONE, this.getId(), order, title);
    }
    
    public boolean execute(MessageContextMenu menu, MyAccount ma) {
        MyLog.v(this, "execute started");
        if (mIsAsync) {
            executeAsync1(menu, ma);
        } else {
            executeOnUiThread(menu, MessageEditorData.newEmpty(ma).setMsgId(menu.getMsgId()));
        }
        return false;
    }
    
    private void executeAsync1(final MessageContextMenu menu, final MyAccount ma) {
        AsyncTaskLauncher.execute(TAG,
                new MyAsyncTask<Void, Void, MessageEditorData>(TAG + name(), MyAsyncTask.PoolEnum.QUICK_UI) {
                    @Override
                    protected MessageEditorData doInBackground2(Void... params) {
                        MyLog.v(MessageListContextMenuItem.this, "execute async started. msgId=" + menu.getMsgId());
                        return executeAsync(ma, menu.getMsgId());
                    }

                    @Override
                    protected void onPostExecute(MessageEditorData editorData) {
                        MyLog.v(MessageListContextMenuItem.this, "execute async ended");
                        executeOnUiThread(menu, editorData);
                    }
                }
        );
    }

    MessageEditorData executeAsync(MyAccount ma, long msgId) {
        return MessageEditorData.newEmpty(ma);
    }

    MessageEditorData getUserId(MyAccount ma, long msgId, String msgUserIdColumnName) {
        return MessageEditorData.newEmpty(ma)
                .setRecipientId(MyQuery.msgIdToUserId(msgUserIdColumnName, msgId));
    }

    void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
        // Empty
    }
    
    void sendUserCommand(CommandEnum command, MessageEditorData editorData) {
        MyServiceManager.sendManualForegroundCommand(
                CommandData.newUserCommand(command, editorData.ma, editorData.recipientId, ""));
    }
    
    void sendMsgCommand(CommandEnum command, MessageEditorData editorData) {
        MyServiceManager.sendManualForegroundCommand(
                CommandData.newItemCommand(command, editorData.ma, editorData.getMsgId()));
    }
}
