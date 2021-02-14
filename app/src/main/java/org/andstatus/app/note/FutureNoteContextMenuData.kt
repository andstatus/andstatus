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
import org.andstatus.app.data.NoteContextMenuData;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.util.MyLog;

import java.util.function.Consumer;

import androidx.annotation.NonNull;

class FutureNoteContextMenuData {
    private static final String TAG = FutureNoteContextMenuData.class.getSimpleName();
    private static final int MAX_SECONDS_TO_LOAD = 10;
    static final FutureNoteContextMenuData EMPTY = new FutureNoteContextMenuData(NoteViewItem.EMPTY);

    enum StateForSelectedViewItem {
        READY,
        LOADING,
        NEW
    }

    private final long activityId;
    private final long noteId;
    volatile NoteContextMenuData menuData = NoteContextMenuData.EMPTY;
    volatile private MyAsyncTask<Void, Void, NoteContextMenuData> loader;

    static void loadAsync(@NonNull final NoteContextMenu noteContextMenu,
                          final View view,
                          final BaseNoteViewItem viewItem,
                          final Consumer<NoteContextMenu> next) {

        final NoteContextMenuContainer menuContainer = noteContextMenu.menuContainer;
        FutureNoteContextMenuData future = new FutureNoteContextMenuData(viewItem);

        if (menuContainer != null && view != null && future.noteId != 0) {
            future.loader = new MyAsyncTask<Void, Void, NoteContextMenuData>(
                    TAG + future.noteId, MyAsyncTask.PoolEnum.QUICK_UI) {

                @Override
                protected NoteContextMenuData doInBackground2(Void aVoid) {
                    @NonNull final MyAccount selectedMyAccount = noteContextMenu.getSelectedActingAccount();
                    MyAccount currentMyAccount = menuContainer.getActivity().getMyContext().accounts().getCurrentAccount();
                    NoteContextMenuData accountToNote = NoteContextMenuData.getAccountToActOnNote(
                            menuContainer.getActivity().getMyContext(), future.activityId,
                            future.noteId, selectedMyAccount, currentMyAccount);
                    if (MyLog.isVerboseEnabled()) {
                        MyLog.v(noteContextMenu, "acting:" + accountToNote.getMyAccount().getAccountName()
                            + (accountToNote.getMyAccount().equals(selectedMyAccount) || selectedMyAccount.nonValid()
                                ? "" : ", selected:" + selectedMyAccount.getAccountName())
                            + (accountToNote.getMyAccount().equals(currentMyAccount) || currentMyAccount.nonValid()
                                ? "" : ", current:" + currentMyAccount.getAccountName())
                            + "\n " + accountToNote);
                    }
                    return accountToNote.getMyAccount().isValid() ? accountToNote : NoteContextMenuData.EMPTY;
                }

                @Override
                protected void onFinish(NoteContextMenuData menuData, boolean success) {
                    future.menuData = menuData == null ? NoteContextMenuData.EMPTY : menuData;
                    noteContextMenu.setFutureData(future);
                    if (future.menuData.noteForAnyAccount.noteId != 0
                            && noteContextMenu.getViewItem().getNoteId() == future.noteId) {
                        if (next != null) {
                            next.accept(noteContextMenu);
                        } else {
                            noteContextMenu.showContextMenu();
                        }
                    }
                }
            };
        }
        noteContextMenu.setFutureData(future);
        if (future.loader != null) {
            future.loader.setMaxCommandExecutionSeconds(MAX_SECONDS_TO_LOAD);
            future.loader.execute();
        }
    }

    private FutureNoteContextMenuData(final BaseNoteViewItem viewItem) {
        activityId = viewItem == null ? 0 : viewItem.getActivityId();
        noteId = viewItem == null ? 0 : viewItem.getNoteId();
    }

    public long getNoteId() {
        return noteId;
    }

    StateForSelectedViewItem getStateFor(BaseNoteViewItem currentItem) {
        if (noteId == 0 || currentItem == null || loader == null || currentItem.getNoteId() != noteId) {
            return StateForSelectedViewItem.NEW;
        }
        if (loader.isReallyWorking()) {
            return StateForSelectedViewItem.LOADING;
        }
        return currentItem.getNoteId() == menuData.noteForAnyAccount.noteId
                ? StateForSelectedViewItem.READY
                : StateForSelectedViewItem.NEW;
    }

    boolean isFor(long noteId) {
        return noteId != 0 && loader != null && !loader.needsBackgroundWork()
                && noteId == menuData.noteForAnyAccount.noteId;
    }
}
