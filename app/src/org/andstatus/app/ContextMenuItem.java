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

package org.andstatus.app;

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

import org.andstatus.app.account.AccountSelector;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.FileProvider;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.TimelineTypeEnum;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyLog;

public enum ContextMenuItem {
    REPLY(true) {
        @Override
        MessageEditorData executeAsync(MyAccount ma, long msgId) {
            return new MessageEditorData(ma).setInReplyToId(msgId).addMentionsToText();
        }

        @Override
        boolean executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            menu.messageList.getMessageEditor().startEditingMessage(editorData);
            return true;
        }
    },
    REPLY_ALL(true) {
        @Override
        MessageEditorData executeAsync(MyAccount ma, long msgId) {
            return new MessageEditorData(ma).setInReplyToId(msgId).setReplyAll(true).addMentionsToText();
        }

        @Override
        boolean executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            menu.messageList.getMessageEditor().startEditingMessage(editorData);
            return true;
        }
    },
    DIRECT_MESSAGE(true) {
        @Override
        MessageEditorData executeAsync(MyAccount ma, long msgId) {
            return new MessageEditorData(ma).setInReplyToId(msgId)
                    .setRecipientId(MyProvider.msgIdToUserId(MyDatabase.Msg.AUTHOR_ID, msgId))
                    .addMentionsToText();
        }

        @Override
        boolean executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            if (editorData.recipientId != 0) {
                menu.messageList.getMessageEditor().startEditingMessage(editorData);
            }
            return true;
        }
    },
    FAVORITE() {
        @Override
        boolean executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            sendCommandMsg(CommandEnum.CREATE_FAVORITE, editorData);
            return true;
        }
    },
    DESTROY_FAVORITE() {
        @Override
        boolean executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            sendCommandMsg(CommandEnum.DESTROY_FAVORITE, editorData);
            return true;
        }
    },
    REBLOG() {
        @Override
        boolean executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            sendCommandMsg(CommandEnum.REBLOG, editorData);
            return true;
        }
    },
    DESTROY_REBLOG() {
        @Override
        boolean executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            sendCommandMsg(CommandEnum.DESTROY_REBLOG, editorData);
            return true;
        }
    },
    DESTROY_STATUS() {
        @Override
        boolean executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            sendCommandMsg(CommandEnum.DESTROY_STATUS, editorData);
            return true;
        }
    },
    SHARE() {
        @Override
        boolean executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            return new MessageShare(menu.messageList.getActivity(), menu.getMsgId()).share();
        }
    },
    COPY_TEXT(true) {
        @Override
        MessageEditorData executeAsync(MyAccount ma, long msgId) {
            String body = MyProvider.msgIdToStringColumnValue(MyDatabase.Msg.BODY, msgId);
            return new MessageEditorData(ma).setMessageText(body);
        }

        @Override
        boolean executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            copyMessageText(editorData);
            return true;
        }
    },
    COPY_AUTHOR(true) {
        @Override
        MessageEditorData executeAsync(MyAccount ma, long msgId) {
            return new MessageEditorData(ma).addMentionedUserToText(
                    MyProvider.msgIdToUserId(MyDatabase.Msg.AUTHOR_ID, msgId));
        }

        @Override
        boolean executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            copyMessageText(editorData);
            return true;
        }
    },
    SENDER_MESSAGES(true) {
        @Override
        MessageEditorData executeAsync(MyAccount ma, long msgId) {
            return getUserId(ma, msgId, MyDatabase.Msg.SENDER_ID);
        }

        @Override
        boolean executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            if (editorData.recipientId != 0) {
                /**
                 * We better switch to the account selected for this message in order not to
                 * add new "MsgOfUser" entries hence duplicated messages in the combined timeline 
                 */
                MyContextHolder.get().persistentAccounts().setCurrentAccount(editorData.ma);
                menu.switchTimelineActivity(TimelineTypeEnum.USER, menu.messageList.isTimelineCombined(), editorData.recipientId);
            }
            return true;
        }
    },
    AUTHOR_MESSAGES(true) {
        @Override
        MessageEditorData executeAsync(MyAccount ma, long msgId) {
            return getUserId(ma, msgId, MyDatabase.Msg.AUTHOR_ID);
        }

        boolean executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            if (editorData.recipientId != 0) {
                /**
                 * We better switch to the account selected for this message in order not to
                 * add new "MsgOfUser" entries hence duplicated messages in the combined timeline 
                 */
                MyContextHolder.get().persistentAccounts().setCurrentAccount(editorData.ma);
                menu.switchTimelineActivity(TimelineTypeEnum.USER, menu.messageList.isTimelineCombined(), editorData.recipientId);
            }
            return true;
        }
    },
    FOLLOW_SENDER(true) {
        @Override
        MessageEditorData executeAsync(MyAccount ma, long msgId) {
            return getUserId(ma, msgId, MyDatabase.Msg.SENDER_ID);
        }

        @Override
        boolean executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            sendCommandUser(CommandEnum.FOLLOW_USER, editorData);
            return true;
        }
    },
    STOP_FOLLOWING_SENDER(true) {
        @Override
        MessageEditorData executeAsync(MyAccount ma, long msgId) {
            return getUserId(ma, msgId, MyDatabase.Msg.SENDER_ID);
        }

        @Override
        boolean executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            sendCommandUser(CommandEnum.STOP_FOLLOWING_USER, editorData);
            return true;
        }
    },
    FOLLOW_AUTHOR(true) {
        @Override
        MessageEditorData executeAsync(MyAccount ma, long msgId) {
            return getUserId(ma, msgId, MyDatabase.Msg.AUTHOR_ID);
        }

        @Override
        boolean executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            sendCommandUser(CommandEnum.FOLLOW_USER, editorData);
            return true;
        }
    },
    STOP_FOLLOWING_AUTHOR(true) {
        @Override
        MessageEditorData executeAsync(MyAccount ma, long msgId) {
            return getUserId(ma, msgId, MyDatabase.Msg.AUTHOR_ID);
        }

        @Override
        boolean executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            sendCommandUser(CommandEnum.STOP_FOLLOWING_USER, editorData);
            return true;
        }
    },
    PROFILE(),
    BLOCK(),
    ACT_AS_USER() {
        @Override
        boolean executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            menu.setAccountUserIdToActAs(editorData.ma.firstOtherAccountOfThisOrigin().getUserId());
            menu.showContextMenu();
            return true;
        }
    },
    ACT_AS() {
        @Override
        boolean executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            AccountSelector.selectAccount(menu.messageList.getActivity(), editorData.ma.getOriginId(), ActivityRequestCode.SELECT_ACCOUNT_TO_ACT_AS);
            return true;
        }
    },
    OPEN_MESSAGE_PERMALINK() {
        @Override
        boolean executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            return new MessageShare(menu.messageList.getActivity(), menu.getMsgId()).openPermalink();
        }
    },
    VIEW_IMAGE() {
        @Override
        boolean executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            FileProvider.viewImage(menu.messageList.getActivity(), menu.imageFilename);
            return true;
        }
    },
    OPEN_CONVERSATION() {
        @Override
        boolean executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
            Uri uri = MyProvider.getTimelineMsgUri(editorData.ma.getUserId(), menu.messageList.getTimelineType(), true, menu.getMsgId());
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
                menu.messageList.getActivity().startActivity(new Intent(Intent.ACTION_VIEW, uri));
            }
            return true;
        }
    },
    UNKNOWN();

    private final boolean mIsAsync;

    ContextMenuItem() {
        this(false);
    }

    ContextMenuItem(boolean isAsync) {
        this.mIsAsync = isAsync;
    }

    int getId() {
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
        MyLog.v(this, "text='" + editorData.messageText + "'");
        if (!TextUtils.isEmpty(editorData.messageText)) {
            // http://developer.android.com/guide/topics/text/copy-paste.html
            ClipboardManager clipboard = (ClipboardManager) MyContextHolder.get().context().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText(I18n.trimTextAt(editorData.messageText, 40), editorData.messageText);
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
            return true;
        } else {
            return executeOnUiThread(menu, new MessageEditorData(ma).setInReplyToId(menu.getMsgId()));
        }
    }
    
    private void executeAsync1(final MessageContextMenu menu, final MyAccount ma) {
        new AsyncTask<Void, Void, MessageEditorData>(){
            @Override
            protected MessageEditorData doInBackground(Void... params) {
                MyLog.v(ContextMenuItem.this, "execute async started");
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
        return new MessageEditorData(ma);
    }

    MessageEditorData getUserId(MyAccount ma, long msgId, String msgUserIdColumnName) {
        return new MessageEditorData(ma)
                .setRecipientId(MyProvider.msgIdToUserId(msgUserIdColumnName, msgId));
    }

    boolean executeOnUiThread(MessageContextMenu menu, MessageEditorData editorData) {
        return false;
    }
    
    void sendCommandUser(CommandEnum command, MessageEditorData editorData) {
        MyServiceManager.sendManualForegroundCommand( new CommandData(command, editorData.ma.getAccountName(), editorData.recipientId));
    }
    
    void sendCommandMsg(CommandEnum command, MessageEditorData editorData) {
        MyServiceManager.sendManualForegroundCommand( new CommandData(command, editorData.ma.getAccountName(), editorData.inReplyToId));
    }
}
