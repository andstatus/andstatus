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

import io.vavr.control.Try
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.data.DownloadStatus
import org.andstatus.app.data.MyProvider
import org.andstatus.app.os.AsyncEnum
import org.andstatus.app.os.AsyncResult
import org.andstatus.app.service.CommandData
import org.andstatus.app.service.CommandEnum
import org.andstatus.app.service.MyServiceEvent
import org.andstatus.app.service.MyServiceEventsBroadcaster
import org.andstatus.app.service.MyServiceManager
import org.andstatus.app.service.MyServiceState
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.TryUtils

/**
 * Asynchronously save, delete and send a note, prepared by [NoteEditor]
 */
class NoteSaver(private val editor: NoteEditor) : AsyncResult<NoteEditorCommand?, NoteEditorData>(AsyncEnum.QUICK_UI) {
    val noteEditorCommandEmpty = NoteEditorCommand(NoteEditorData.EMPTY)

    @Volatile
    private var command: NoteEditorCommand = noteEditorCommandEmpty

    override suspend fun doInBackground(params: NoteEditorCommand?): Try<NoteEditorData> {
        command = params ?: noteEditorCommandEmpty
        MyLog.v(NoteEditorData.TAG) { "Started: $command" }
        if (!command.acquireLock(true)) {
            return TryUtils.ofNullable(command.currentData)
        }
        savePreviousData()
        if (command.currentData?.isValid() == false) {
            command.loadCurrent()
        }
        saveCurrentData()
        return Try.success(
            if (command.showAfterSave)
                NoteEditorData.load(MyContextHolder.myContextHolder.getNow(), command.currentData?.getNoteId() ?: 0)
            else NoteEditorData.EMPTY
        )
    }

    private fun savePreviousData() {
        if (command.needToSavePreviousData()) {
            MyLog.v(NoteEditorData.TAG) { "Saving previous data:" + command.previousData }
            command.previousData?.save()
            command.previousData?.let { broadcastDataChanged(it) }
        }
    }

    private fun saveCurrentData() {
        val currentData = command.currentData ?: return

        MyLog.v(NoteEditorData.TAG) { "Saving current data: $currentData" }
        if (currentData.activity.getNote().getStatus() == DownloadStatus.DELETED) {
            MyProvider.deleteNoteAndItsActivities( MyContextHolder.myContextHolder.getNow(), currentData.getNoteId())
        } else {
            if (command.hasMedia()) {
                currentData.addAttachment(command.getMediaUri(), command.getMediaType())
            }
            currentData.save()
            if (command.beingEdited) {
                MyPreferences.setBeingEditedNoteId(currentData.getNoteId())
            }
            if (currentData.activity.getNote().getStatus() == DownloadStatus.SENDING) {
                val commandData: CommandData = CommandData.newUpdateStatus(
                        currentData.getMyAccount(),
                        currentData.activity.getId(), currentData.getNoteId())
                MyServiceManager.sendManualForegroundCommand(commandData)
            }
        }
        broadcastDataChanged(currentData)
    }

    private fun broadcastDataChanged(data: NoteEditorData) {
        if (data.isEmpty) {
            return
        }
        val commandData: CommandData = if (data.activity.getNote().getStatus() == DownloadStatus.DELETED)
            CommandData.newItemCommand(CommandEnum.DELETE_NOTE, data.getMyAccount(), data.getNoteId())
        else CommandData.newUpdateStatus(data.getMyAccount(), data.activity.getId(), data.getNoteId())
        MyServiceEventsBroadcaster.newInstance( MyContextHolder.myContextHolder.getNow(), MyServiceState.UNKNOWN)
                .setCommandData(commandData).setEvent(MyServiceEvent.AFTER_EXECUTING_COMMAND).broadcast()
    }

    override suspend fun onPostExecute(result: Try<NoteEditorData>) {
        result.onSuccess { data ->
            if (data.isValid()) {
                if (command.hasLock()) {
                    MyLog.v(NoteEditorData.TAG) { "Saved; Future data: $result" }
                    editor.showData(data)
                } else {
                    MyLog.v(NoteEditorData.TAG) { "Saved; Result skipped: no lock" }
                }
            } else {
                MyLog.v(NoteEditorData.TAG, "Saved; No future data")
            }
        }.onFailure {
            MyLog.v(NoteEditorData.TAG, "Saved; No future data")
        }
        command.releaseLock()
    }
}
