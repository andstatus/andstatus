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

package org.andstatus.app.note;

import android.view.View;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.data.AccountToNote;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.util.MyLog;

import java.util.function.Consumer;

import androidx.annotation.NonNull;

class NoteContextMenuData {
    private static final int MAX_SECONDS_TO_LOAD = 10;
    static NoteContextMenuData EMPTY = new NoteContextMenuData(NoteViewItem.EMPTY);

    enum StateForSelectedViewItem {
        READY,
        LOADING,
        NEW
    }

    private final BaseNoteViewItem viewItem;
    AccountToNote accountToNote = AccountToNote.EMPTY;
    private MyAsyncTask<Void, Void, AccountToNote> loader;

    static void loadAsync(@NonNull final NoteContextMenu noteContextMenu,
                          final View view,
                          final BaseNoteViewItem viewItem,
                          final Consumer<NoteContextMenu> next) {

        final NoteContextMenuContainer menuContainer = noteContextMenu.menuContainer;
        NoteContextMenuData data = new NoteContextMenuData(viewItem);

        if (menuContainer != null && view != null && viewItem != null && viewItem.getNoteId() != 0) {
            final long noteId = viewItem.getNoteId();
            data.loader = new MyAsyncTask<Void, Void, AccountToNote>(
                    NoteContextMenuData.class.getSimpleName() + noteId, MyAsyncTask.PoolEnum.QUICK_UI) {

                @Override
                protected AccountToNote doInBackground2(Void... params) {
                    @NonNull final MyAccount selectedMyAccount = noteContextMenu.getSelectedActingAccount();
                    MyAccount currentMyAccount = menuContainer.getActivity().getMyContext().accounts().getCurrentAccount();
                    AccountToNote accountToNote = AccountToNote.getAccountToActOnNote(
                            menuContainer.getActivity().getMyContext(), viewItem.getActivityId(),
                            noteId, selectedMyAccount, currentMyAccount);
                    if (MyLog.isVerboseEnabled()) {
                        MyLog.v(noteContextMenu, "acting:" + accountToNote.getMyAccount().getAccountName()
                            + (accountToNote.getMyAccount().equals(selectedMyAccount) || selectedMyAccount.nonValid()
                                ? "" : ", selected:" + selectedMyAccount.getAccountName())
                            + (accountToNote.getMyAccount().equals(currentMyAccount) || currentMyAccount.nonValid()
                                ? "" : ", current:" + currentMyAccount.getAccountName())
                            + "\n " + accountToNote);
                    }
                    return accountToNote.getMyAccount().isValid() ? accountToNote : AccountToNote.EMPTY;
                }

                @Override
                protected void onFinish(AccountToNote accountToNote, boolean success) {
                    data.accountToNote = accountToNote == null ? AccountToNote.EMPTY : accountToNote;
                    noteContextMenu.setMenuData(data);
                    if (data.accountToNote.noteForAnyAccount.noteId != 0 && viewItem.equals(noteContextMenu.getViewItem())) {
                        if (next != null) {
                            next.accept(noteContextMenu);
                        } else {
                            noteContextMenu.showContextMenu();
                        }
                    }
                }
            };
        }
        noteContextMenu.setMenuData(data);
        if (data.loader != null) {
            data.loader.setMaxCommandExecutionSeconds(MAX_SECONDS_TO_LOAD);
            data.loader.execute();
        }
    }

    private NoteContextMenuData(final BaseNoteViewItem viewItem) {
        this.viewItem = viewItem;
    }

    public long getNoteId() {
        return viewItem == null ? 0 : viewItem.getNoteId();
    }

    StateForSelectedViewItem getStateFor(BaseNoteViewItem currentItem) {
        if (viewItem == null || currentItem == null || loader == null || !viewItem.equals(currentItem)) {
            return StateForSelectedViewItem.NEW;
        }
        if (loader.isReallyWorking()) {
            return StateForSelectedViewItem.LOADING;
        }
        return currentItem.getNoteId() == accountToNote.noteForAnyAccount.noteId
                ? StateForSelectedViewItem.READY
                : StateForSelectedViewItem.NEW;
    }

    boolean isFor(long noteId) {
        return noteId != 0 && loader != null && !loader.needsBackgroundWork()
                && noteId == accountToNote.noteForAnyAccount.noteId;
    }
}
