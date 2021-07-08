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
package org.andstatus.app.note

import android.view.View
import io.vavr.control.Try
import org.andstatus.app.data.NoteContextMenuData
import org.andstatus.app.os.AsyncTaskLauncher
import org.andstatus.app.os.MyAsyncTask
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.TryUtils

class FutureNoteContextMenuData private constructor(viewItem: BaseNoteViewItem<*>?) {
    enum class StateForSelectedViewItem {
        READY, LOADING, NEW
    }

    private val activityId: Long = viewItem?.getActivityId() ?: 0
    private val noteId: Long = viewItem?.getNoteId() ?: 0

    @Volatile
    var menuData: NoteContextMenuData = NoteContextMenuData.EMPTY

    @Volatile
    private var loader: MyAsyncTask<Void?, Void?, NoteContextMenuData>? = null

    fun getNoteId(): Long {
        return noteId
    }

    fun getStateFor(currentItem: BaseNoteViewItem<*>?): StateForSelectedViewItem {
        if (noteId == 0L || currentItem == null || loader == null || currentItem.getNoteId() != noteId) {
            return StateForSelectedViewItem.NEW
        }
        if (loader?.isReallyWorking() == true) {
            return StateForSelectedViewItem.LOADING
        }
        return if (currentItem.getNoteId() == menuData.noteForAnyAccount.noteId) StateForSelectedViewItem.READY else StateForSelectedViewItem.NEW
    }

    fun isFor(noteId: Long): Boolean {
        return (noteId != 0L && loader?.needsBackgroundWork() == false
                && noteId == menuData.noteForAnyAccount.noteId)
    }

    companion object {
        private val TAG: String = FutureNoteContextMenuData::class.java.simpleName
        private const val MAX_SECONDS_TO_LOAD = 10
        val EMPTY: FutureNoteContextMenuData = FutureNoteContextMenuData(NoteViewItem.EMPTY)

        fun loadAsync(noteContextMenu: NoteContextMenu,
                      view: View?,
                      viewItem: BaseNoteViewItem<*>?,
                      next: (NoteContextMenu) -> Unit) {
            val menuContainer = noteContextMenu.menuContainer
            val future = FutureNoteContextMenuData(viewItem)
            if (view != null && future.noteId != 0L) {
                future.loader = object : MyAsyncTask<Void?, Void?, NoteContextMenuData>(
                        TAG + future.noteId, PoolEnum.QUICK_UI) {

                    override suspend fun doInBackground(params: Void?): Try<NoteContextMenuData> {
                        val selectedMyAccount = noteContextMenu.getSelectedActingAccount()
                        val currentMyAccount = menuContainer.getActivity().myContext.accounts.currentAccount
                        val accountToNote: NoteContextMenuData = NoteContextMenuData.getAccountToActOnNote(
                                menuContainer.getActivity().myContext, future.activityId,
                                future.noteId, selectedMyAccount, currentMyAccount)
                        if (MyLog.isVerboseEnabled()) {
                            MyLog.v(noteContextMenu, "acting:${accountToNote.getMyAccount().getAccountName()}" +
                                    "${if (accountToNote.getMyAccount() == selectedMyAccount || selectedMyAccount.nonValid) "" 
                                    else ", selected:" + selectedMyAccount.getAccountName()}" +
                                    "${if (accountToNote.getMyAccount() == currentMyAccount || currentMyAccount.nonValid) "" 
                                    else ", current:" + currentMyAccount.getAccountName()} $accountToNote")
                        }
                        return if (accountToNote.getMyAccount().isValid) Try.success(accountToNote)
                            else TryUtils.notFound()

                    }

                    override suspend fun onPostExecute(result: Try<NoteContextMenuData>) {
                        future.menuData = result.getOrElse(NoteContextMenuData.EMPTY)
                        noteContextMenu.setFutureData(future)
                        if (future.menuData.noteForAnyAccount.noteId != 0L && noteContextMenu.getViewItem().getNoteId() == future.noteId) {
                            next(noteContextMenu)
                        }
                    }
                }
            }
            noteContextMenu.setFutureData(future)
            future.loader?.let { loader ->
                loader.maxCommandExecutionSeconds = MAX_SECONDS_TO_LOAD.toLong()
                AsyncTaskLauncher.execute(this, loader)
            }
        }
    }

}
