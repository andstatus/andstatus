/*
 * Copyright (C) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
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

import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.data.DownloadStatus
import org.andstatus.app.data.MyProvider
import org.andstatus.app.note.NoteEditorData
import org.andstatus.app.os.MyAsyncTask
import org.andstatus.app.service.CommandData
import org.andstatus.app.service.CommandEnum
import org.andstatus.app.service.MyServiceEvent
import org.andstatus.app.service.MyServiceEventsBroadcaster
import org.andstatus.app.service.MyServiceManager
import org.andstatus.app.service.MyServiceState
import org.andstatus.app.util.MyLog

/**
 * Asynchronously save, delete and send a note, prepared by [NoteEditor]
 */
class NoteSaver(private val editor: NoteEditor?) : MyAsyncTask<NoteEditorCommand?, Void?, NoteEditorData?>(PoolEnum.QUICK_UI) {
    @Volatile
    private var command: NoteEditorCommand? = NoteEditorCommand(NoteEditorData.Companion.EMPTY)
    override fun doInBackground2(commandIn: NoteEditorCommand?): NoteEditorData? {
        command = commandIn
        MyLog.v(NoteEditorData.Companion.TAG) { "Started: $command" }
        if (!command.acquireLock(true)) {
            return command.currentData
        }
        savePreviousData()
        if (!command.currentData.isValid) {
            command.loadCurrent()
        }
        saveCurrentData()
        return if (command.showAfterSave) NoteEditorData.Companion.load( MyContextHolder.myContextHolder.getNow(), command.currentData.noteId) else NoteEditorData.Companion.EMPTY
    }

    private fun savePreviousData() {
        if (command.needToSavePreviousData()) {
            MyLog.v(NoteEditorData.Companion.TAG) { "Saving previous data:" + command.previousData }
            command.previousData.save()
            broadcastDataChanged(command.previousData)
        }
    }

    private fun saveCurrentData() {
        MyLog.v(NoteEditorData.Companion.TAG) { "Saving current data:" + command.currentData }
        if (command.currentData.activity.note.status == DownloadStatus.DELETED) {
            MyProvider.Companion.deleteNoteAndItsActivities( MyContextHolder.myContextHolder.getNow(), command.currentData.noteId)
        } else {
            if (command.hasMedia()) {
                command.currentData.addAttachment(command.getMediaUri(), command.getMediaType())
            }
            command.currentData.save()
            if (command.beingEdited) {
                MyPreferences.setBeingEditedNoteId(command.currentData.noteId)
            }
            if (command.currentData.activity.note.status == DownloadStatus.SENDING) {
                val commandData: CommandData = CommandData.Companion.newUpdateStatus(
                        command.currentData.myAccount,
                        command.currentData.activity.id, command.currentData.noteId)
                MyServiceManager.Companion.sendManualForegroundCommand(commandData)
            }
        }
        broadcastDataChanged(command.currentData)
    }

    private fun broadcastDataChanged(data: NoteEditorData?) {
        if (data.isEmpty()) {
            return
        }
        val commandData: CommandData = if (data.activity.note.status == DownloadStatus.DELETED) CommandData.Companion.newItemCommand(CommandEnum.DELETE_NOTE, data.getMyAccount(), data.getNoteId()) else CommandData.Companion.newUpdateStatus(data.getMyAccount(), data.activity.id, data.getNoteId())
        MyServiceEventsBroadcaster.Companion.newInstance( MyContextHolder.myContextHolder.getNow(), MyServiceState.UNKNOWN)
                .setCommandData(commandData).setEvent(MyServiceEvent.AFTER_EXECUTING_COMMAND).broadcast()
    }

    override fun onCancelled() {
        command.releaseLock()
    }

    override fun onPostExecute2(data: NoteEditorData?) {
        if (data.isValid()) {
            if (command.hasLock()) {
                MyLog.v(NoteEditorData.Companion.TAG) { "Saved; Future data: $data" }
                editor.showData(data)
            } else {
                MyLog.v(NoteEditorData.Companion.TAG) { "Saved; Result skipped: no lock" }
            }
        } else {
            MyLog.v(NoteEditorData.Companion.TAG, "Saved; No future data")
        }
        command.releaseLock()
    }
}