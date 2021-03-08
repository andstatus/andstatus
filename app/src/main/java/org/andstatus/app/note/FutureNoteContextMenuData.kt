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
import org.andstatus.app.data.NoteContextMenuData
import org.andstatus.app.note.NoteViewItem
import org.andstatus.app.os.MyAsyncTask
import org.andstatus.app.util.MyLog
import java.util.function.Consumer

internal class FutureNoteContextMenuData private constructor(viewItem: BaseNoteViewItem<*>?) {
    internal enum class StateForSelectedViewItem {
        READY, LOADING, NEW
    }

    private val activityId: Long
    private val noteId: Long

    @Volatile
    var menuData: NoteContextMenuData = NoteContextMenuData.Companion.EMPTY

    @Volatile
    private var loader: MyAsyncTask<Void?, Void?, NoteContextMenuData?>? = null
    fun getNoteId(): Long {
        return noteId
    }

    fun getStateFor(currentItem: BaseNoteViewItem<*>?): StateForSelectedViewItem? {
        if (noteId == 0L || currentItem == null || loader == null || currentItem.noteId != noteId) {
            return StateForSelectedViewItem.NEW
        }
        if (loader.isReallyWorking()) {
            return StateForSelectedViewItem.LOADING
        }
        return if (currentItem.noteId == menuData.noteForAnyAccount.noteId) StateForSelectedViewItem.READY else StateForSelectedViewItem.NEW
    }

    fun isFor(noteId: Long): Boolean {
        return (noteId != 0L && loader != null && !loader.needsBackgroundWork()
                && noteId == menuData.noteForAnyAccount.noteId)
    }

    companion object {
        private val TAG: String = FutureNoteContextMenuData::class.java.simpleName
        private const val MAX_SECONDS_TO_LOAD = 10
        val EMPTY: FutureNoteContextMenuData = FutureNoteContextMenuData(NoteViewItem.Companion.EMPTY)
        fun loadAsync(noteContextMenu: NoteContextMenu,
                      view: View?,
                      viewItem: BaseNoteViewItem<*>?,
                      next: Consumer<NoteContextMenu?>?) {
            val menuContainer = noteContextMenu.menuContainer
            val future = FutureNoteContextMenuData(viewItem)
            if (menuContainer != null && view != null && future.noteId != 0L) {
                future.loader = object : MyAsyncTask<Void?, Void?, NoteContextMenuData?>(
                        TAG + future.noteId, PoolEnum.QUICK_UI) {
                    override fun doInBackground2(aVoid: Void?): NoteContextMenuData? {
                        val selectedMyAccount = noteContextMenu.selectedActingAccount
                        val currentMyAccount = menuContainer.activity.myContext.accounts().currentAccount
                        val accountToNote: NoteContextMenuData = NoteContextMenuData.Companion.getAccountToActOnNote(
                                menuContainer.activity.myContext, future.activityId,
                                future.noteId, selectedMyAccount, currentMyAccount)
                        if (MyLog.isVerboseEnabled()) {
                            MyLog.v(noteContextMenu, """acting:${accountToNote.myAccount.accountName}${if (accountToNote.myAccount == selectedMyAccount || selectedMyAccount.nonValid) "" else ", selected:" + selectedMyAccount.accountName}${if (accountToNote.myAccount == currentMyAccount || currentMyAccount.nonValid()) "" else ", current:" + currentMyAccount.accountName}
 $accountToNote""")
                        }
                        return if (accountToNote.myAccount.isValid) accountToNote else NoteContextMenuData.Companion.EMPTY
                    }

                    override fun onFinish(menuData: NoteContextMenuData?, success: Boolean) {
                        future.menuData = menuData ?: NoteContextMenuData.Companion.EMPTY
                        noteContextMenu.setFutureData(future)
                        if (future.menuData.noteForAnyAccount.noteId != 0L && noteContextMenu.viewItem.noteId == future.noteId) {
                            if (next != null) {
                                next.accept(noteContextMenu)
                            } else {
                                noteContextMenu.showContextMenu()
                            }
                        }
                    }
                }
            }
            noteContextMenu.setFutureData(future)
            if (future.loader != null) {
                future.loader.setMaxCommandExecutionSeconds(MAX_SECONDS_TO_LOAD.toLong())
                future.loader.execute()
            }
        }
    }

    init {
        activityId = viewItem?.activityId ?: 0
        noteId = viewItem?.noteId ?: 0
    }
}