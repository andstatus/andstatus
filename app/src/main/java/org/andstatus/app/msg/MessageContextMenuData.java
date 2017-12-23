/*
 * Copyright (C) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.support.annotation.NonNull;
import android.view.View;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.data.MessageForAccount;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.util.MyLog;

import java.util.function.Consumer;

class MessageContextMenuData {
    private static final int MAX_SECONDS_TO_LOAD = 10;
    static MessageContextMenuData EMPTY = new MessageContextMenuData(MessageViewItem.EMPTY);

    enum StateForSelectedViewItem {
        READY,
        LOADING,
        NEW
    }

    private final BaseMessageViewItem viewItem;
    MessageForAccount msg = MessageForAccount.EMPTY;
    private MyAsyncTask<Void, Void, MessageForAccount> loader;

    static void loadAsync(@NonNull final MessageContextMenu messageContextMenu,
                          final View view,
                          final BaseMessageViewItem viewItem,
                          final Consumer<MessageContextMenu> next) {

        @NonNull final MyAccount myActor = messageContextMenu.getMyActor();
        final MessageListContextMenuContainer menuContainer = messageContextMenu.menuContainer;
        MessageContextMenuData data = new MessageContextMenuData(viewItem);

        if (menuContainer != null && view != null && viewItem != null && viewItem.getMsgId() != 0) {
            final long msgId = viewItem.getMsgId();
            data.loader = new MyAsyncTask<Void, Void, MessageForAccount>(
                    MessageContextMenuData.class.getSimpleName() + msgId, MyAsyncTask.PoolEnum.QUICK_UI) {

                @Override
                protected MessageForAccount doInBackground2(Void... params) {
                    MyAccount currentMyAccount = menuContainer.getCurrentMyAccount();
                    long originId = MyQuery.msgIdToOriginId(msgId);
                    MyAccount ma1 = menuContainer.getActivity().getMyContext().persistentAccounts()
                            .getAccountForThisMessage(originId, myActor, viewItem.getLinkedMyAccount(), false);
                    MessageForAccount msgNew = new MessageForAccount(originId, 0, msgId, ma1);
                    boolean changedToCurrent = !ma1.equals(currentMyAccount) && !myActor.isValid() && ma1.isValid()
                            && !msgNew.isTiedToThisAccount()
                            && !menuContainer.getTimeline().getTimelineType().isForAccount()
                            && currentMyAccount.isValid() && ma1.getOriginId() == currentMyAccount.getOriginId();
                    if (changedToCurrent) {
                        msgNew = new MessageForAccount(originId, 0, msgId, currentMyAccount);
                    }
                    if (MyLog.isVerboseEnabled()) {
                        MyLog.v(messageContextMenu, "actor:" + msgNew.getMyAccount()
                                + (changedToCurrent ? " <- to current" : "")
                                + (msgNew.getMyAccount().equals(myActor) ? "" : " <- myActor:" + myActor)
                                + (myActor.equals(viewItem.getLinkedMyAccount())
                                    || !viewItem.getLinkedMyAccount().isValid() ? "" : " <- linked:"
                                    + viewItem.getLinkedMyAccount())
                                + "; msgId:" + msgId);
                    }
                    return msgNew.getMyAccount().isValid() ? msgNew : MessageForAccount.EMPTY;
                }

                @Override
                protected void onFinish(MessageForAccount messageForAccount, boolean success) {
                    data.msg = messageForAccount == null ? MessageForAccount.EMPTY : messageForAccount;
                    messageContextMenu.setMenuData(data);
                    if (data.msg.msgId != 0 && viewItem.equals(messageContextMenu.getViewItem())) {
                        if (next != null) {
                            next.accept(messageContextMenu);
                        } else {
                            messageContextMenu.showContextMenu();
                        }
                    }
                }
            };
        }
        messageContextMenu.setMenuData(data);
        if (data.loader != null) {
            data.loader.setMaxCommandExecutionSeconds(MAX_SECONDS_TO_LOAD);
            data.loader.execute();
        }
    }

    private MessageContextMenuData(final BaseMessageViewItem viewItem) {
        this.viewItem = viewItem;
    }

    public long getMsgId() {
        return viewItem == null ? 0 : viewItem.getMsgId();
    }

    StateForSelectedViewItem getStateFor(BaseMessageViewItem currentItem) {
        if (viewItem == null || currentItem == null || loader == null || !viewItem.equals(currentItem)) {
            return StateForSelectedViewItem.NEW;
        }
        if (loader.isReallyWorking()) {
            return StateForSelectedViewItem.LOADING;
        }
        return currentItem.getMsgId() == msg.msgId ? StateForSelectedViewItem.READY : StateForSelectedViewItem.NEW;
    }

    boolean isFor(long msgId) {
        return msgId != 0 && loader != null && !loader.needsBackgroundWork() && msgId == msg.msgId;
    }
}
