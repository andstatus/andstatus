/**
 * Copyright (C) 2013-2105 yvolk (Yuri Volkov), http://yurivolkov.com
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
import android.os.AsyncTask;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;

import org.andstatus.app.ActivityRequestCode;
import org.andstatus.app.MyAction;
import org.andstatus.app.account.AccountSelector;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.FileProvider;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.TimelineType;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyHtml;

public enum ContextMenuItem {
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
    EDIT() {
        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            menu.messageList.getMessageEditor().loadState(editorData.getMsgId());
        }
    },
    RESEND(true) {
        @Override
        MessageEditorData executeAsync(MyAccount maIn, long msgId) {
            MyAccount ma = MyContextHolder.get().persistentAccounts().fromUserId(
                    MyQuery.msgIdToLongColumnValue(MyDatabase.Msg.SENDER_ID, msgId));
            CommandData commandData = CommandData.updateStatus(ma.getAccountName(), msgId);
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
                    .setRecipientId(MyQuery.msgIdToUserId(MyDatabase.Msg.AUTHOR_ID, msgId))
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
            sendCommandMsg(CommandEnum.CREATE_FAVORITE, editorData);
        }
    },
    DESTROY_FAVORITE() {
        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            sendCommandMsg(CommandEnum.DESTROY_FAVORITE, editorData);
        }
    },
    REBLOG() {
        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            sendCommandMsg(CommandEnum.REBLOG, editorData);
        }
    },
    DESTROY_REBLOG() {
        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            sendCommandMsg(CommandEnum.DESTROY_REBLOG, editorData);
        }
    },
    DESTROY_STATUS() {
        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            sendCommandMsg(CommandEnum.DESTROY_STATUS, editorData);
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
            messageShare.share(menu.messageList.getActivity());
        }
    },
    COPY_TEXT(true) {
        @Override
        MessageEditorData executeAsync(MyAccount ma, long msgId) {
            String body = MyQuery.msgIdToStringColumnValue(MyDatabase.Msg.BODY, msgId);
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
                    MyQuery.msgIdToUserId(MyDatabase.Msg.AUTHOR_ID, msgId));
        }

        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            copyMessageText(editorData);
        }
    },
    SENDER_MESSAGES(true) {
        @Override
        MessageEditorData executeAsync(MyAccount ma, long msgId) {
            return getUserId(ma, msgId, MyDatabase.Msg.SENDER_ID);
        }

        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            if (editorData.recipientId != 0) {
                /**
                 * We better switch to the account selected for this message in order not to
                 * add new "MsgOfUser" entries hence duplicated messages in the combined timeline 
                 */
                MyContextHolder.get().persistentAccounts().setCurrentAccount(editorData.ma);
                menu.switchTimelineActivity(TimelineType.USER, menu.messageList.isTimelineCombined(), editorData.recipientId);
            }
        }
    },
    AUTHOR_MESSAGES(true) {
        @Override
        MessageEditorData executeAsync(MyAccount ma, long msgId) {
            return getUserId(ma, msgId, MyDatabase.Msg.AUTHOR_ID);
        }

        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            if (editorData.recipientId != 0) {
                /**
                 * We better switch to the account selected for this message in order not to
                 * add new "MsgOfUser" entries hence duplicated messages in the combined timeline 
                 */
                MyContextHolder.get().persistentAccounts().setCurrentAccount(editorData.ma);
                menu.switchTimelineActivity(TimelineType.USER, menu.messageList.isTimelineCombined(), editorData.recipientId);
            }
        }
    },
    FOLLOW_SENDER(true) {
        @Override
        MessageEditorData executeAsync(MyAccount ma, long msgId) {
            return getUserId(ma, msgId, MyDatabase.Msg.SENDER_ID);
        }

        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            sendCommandUser(CommandEnum.FOLLOW_USER, editorData);
        }
    },
    STOP_FOLLOWING_SENDER(true) {
        @Override
        MessageEditorData executeAsync(MyAccount ma, long msgId) {
            return getUserId(ma, msgId, MyDatabase.Msg.SENDER_ID);
        }

        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            sendCommandUser(CommandEnum.STOP_FOLLOWING_USER, editorData);
        }
    },
    FOLLOW_AUTHOR(true) {
        @Override
        MessageEditorData executeAsync(MyAccount ma, long msgId) {
            return getUserId(ma, msgId, MyDatabase.Msg.AUTHOR_ID);
        }

        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            sendCommandUser(CommandEnum.FOLLOW_USER, editorData);
        }
    },
    STOP_FOLLOWING_AUTHOR(true) {
        @Override
        MessageEditorData executeAsync(MyAccount ma, long msgId) {
            return getUserId(ma, msgId, MyDatabase.Msg.AUTHOR_ID);
        }

        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            sendCommandUser(CommandEnum.STOP_FOLLOWING_USER, editorData);
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
            AccountSelector.selectAccount(menu.messageList.getActivity(), editorData.ma.getOriginId(), ActivityRequestCode.SELECT_ACCOUNT_TO_ACT_AS);
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
            messageShare.openPermalink(menu.messageList.getActivity());
        }
    },
    VIEW_IMAGE() {
        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            FileProvider.viewImage(menu.messageList.getActivity(), menu.imageFilename);
        }
    },
    OPEN_CONVERSATION() {
        @Override
        void executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            Uri uri = MatchedUri.getTimelineItemUri(editorData.ma.getUserId(),
                    menu.messageList.getTimelineType(), menu.messageList.isTimelineCombined(),
                    menu.messageList.getSelectedUserId(), menu.getMsgId());
            String action = menu.messageList.getActivity().getIntent().getAction();
            if (Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action)) {
                if (MyLog.isLoggable(this, MyLog.DEBUG)) {
                    MyLog.d(this, "onItemClick, setData=" + uri);
                }
                menu.messageList.getActivity().setResult(Activity.RESULT_OK, new Intent().setData(uri));
            } else {
                if (MyLog.isLoggable(this, MyLog.DEBUG)) {
                    MyLog.d(this, "onItemClick, startActivity=" + uri);
                }
                menu.messageList.getActivity().startActivity(MyAction.VIEW_CONVERSATION.getIntent(uri));
            }
        }
    },
    NONEXISTENT(),
    UNKNOWN();

    private final boolean mIsAsync;

    ContextMenuItem() {
        this(false);
    }

    ContextMenuItem(boolean isAsync) {
        this.mIsAsync = isAsync;
    }

    public int getId() {
        return Menu.FIRST + ordinal() + 1;
    }
    
    public static ContextMenuItem fromId(int id) {
        for (ContextMenuItem item : ContextMenuItem.values()) {
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

    public MenuItem addTo(Menu menu, int order, int titleRes) {
        return menu.add(Menu.NONE, this.getId(), order, titleRes);
    }

    public MenuItem addTo(Menu menu, int order, CharSequence title) {
        return menu.add(Menu.NONE, this.getId(), order, title);
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
        new AsyncTask<Void, Void, MessageEditorData>(){
            @Override
            protected MessageEditorData doInBackground(Void... params) {
                MyLog.v(ContextMenuItem.this, "execute async started. msgId=" + menu.getMsgId());
                return executeAsync(ma, menu.getMsgId());
            }

            @Override
            protected void onPostExecute(MessageEditorData editorData) {
                MyLog.v(ContextMenuItem.this, "execute async ended");
                executeOnUiThread(menu, editorData);
            }
        }.execute();
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
    
    void sendCommandUser(CommandEnum command, MessageEditorData editorData) {
        MyServiceManager.sendManualForegroundCommand(
                new CommandData(command, editorData.ma.getAccountName(), editorData.recipientId));
    }
    
    void sendCommandMsg(CommandEnum command, MessageEditorData editorData) {
        MyServiceManager.sendManualForegroundCommand(
                new CommandData(command, editorData.ma.getAccountName(), editorData.getMsgId()));
    }
}
