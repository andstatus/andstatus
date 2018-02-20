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

import android.support.annotation.NonNull;
import android.view.View;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.data.NoteForAccount;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.util.MyLog;

import java.util.function.Consumer;

class NoteContextMenuData {
    private static final int MAX_SECONDS_TO_LOAD = 10;
    static NoteContextMenuData EMPTY = new NoteContextMenuData(NoteViewItem.EMPTY);

    enum StateForSelectedViewItem {
        READY,
        LOADING,
        NEW
    }

    private final BaseNoteViewItem viewItem;
    NoteForAccount noteForAccount = NoteForAccount.EMPTY;
    private MyAsyncTask<Void, Void, NoteForAccount> loader;

    static void loadAsync(@NonNull final NoteContextMenu noteContextMenu,
                          final View view,
                          final BaseNoteViewItem viewItem,
                          final Consumer<NoteContextMenu> next) {

        @NonNull final MyAccount myActor = noteContextMenu.getMyActor();
        final NoteContextMenuContainer menuContainer = noteContextMenu.menuContainer;
        NoteContextMenuData data = new NoteContextMenuData(viewItem);

        if (menuContainer != null && view != null && viewItem != null && viewItem.getNoteId() != 0) {
            final long noteId = viewItem.getNoteId();
            data.loader = new MyAsyncTask<Void, Void, NoteForAccount>(
                    NoteContextMenuData.class.getSimpleName() + noteId, MyAsyncTask.PoolEnum.QUICK_UI) {

                @Override
                protected NoteForAccount doInBackground2(Void... params) {
                    MyAccount currentMyAccount = menuContainer.getCurrentMyAccount();
                    final MyContext myContext = menuContainer.getActivity().getMyContext();
                    final Origin origin = myContext.origins().fromId(MyQuery.noteIdToOriginId(noteId));
                    MyAccount ma1 = myContext.accounts()
                            .getAccountForThisNote(origin, myActor, viewItem.getLinkedMyAccount(), false);
                    NoteForAccount noteForAccount = new NoteForAccount(origin, 0, noteId, ma1);
                    boolean changedToCurrent = !ma1.equals(currentMyAccount) && !myActor.isValid() && ma1.isValid()
                            && !noteForAccount.isTiedToThisAccount()
                            && !menuContainer.getTimeline().getTimelineType().isForUser()
                            && currentMyAccount.isValid() && ma1.getOrigin().equals(currentMyAccount.getOrigin());
                    if (changedToCurrent) {
                        noteForAccount = new NoteForAccount(origin, 0, noteId, currentMyAccount);
                    }
                    if (MyLog.isVerboseEnabled()) {
                        MyLog.v(noteContextMenu, "actor:" + noteForAccount.getMyAccount()
                                + (changedToCurrent ? " <- to current" : "")
                                + (noteForAccount.getMyAccount().equals(myActor) ? "" : " <- myActor:" + myActor)
                                + (myActor.equals(viewItem.getLinkedMyAccount())
                                    || !viewItem.getLinkedMyAccount().isValid() ? "" : " <- linked:"
                                    + viewItem.getLinkedMyAccount())
                                + "; noteId:" + noteId);
                    }
                    return noteForAccount.getMyAccount().isValid() ? noteForAccount : NoteForAccount.EMPTY;
                }

                @Override
                protected void onFinish(NoteForAccount noteForAccount, boolean success) {
                    data.noteForAccount = noteForAccount == null ? NoteForAccount.EMPTY : noteForAccount;
                    noteContextMenu.setMenuData(data);
                    if (data.noteForAccount.noteId != 0 && viewItem.equals(noteContextMenu.getViewItem())) {
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
        return currentItem.getNoteId() == noteForAccount.noteId ? StateForSelectedViewItem.READY : StateForSelectedViewItem.NEW;
    }

    boolean isFor(long noteId) {
        return noteId != 0 && loader != null && !loader.needsBackgroundWork() && noteId == noteForAccount.noteId;
    }
}
