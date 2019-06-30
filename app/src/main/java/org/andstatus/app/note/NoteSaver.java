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

package org.andstatus.app.note;

import android.net.Uri;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyServiceEvent;
import org.andstatus.app.service.MyServiceEventsBroadcaster;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.service.MyServiceState;
import org.andstatus.app.util.MyLog;

import java.util.Optional;

/**
 * Asynchronously save, delete and send a note, prepared by {@link NoteEditor}
 */
public class NoteSaver extends MyAsyncTask<NoteEditorCommand, Void, NoteEditorData> {
    private final NoteEditor editor;
    private volatile NoteEditorCommand command = new NoteEditorCommand(NoteEditorData.EMPTY);

    public NoteSaver(NoteEditor editor) {
        super(PoolEnum.QUICK_UI);
        this.editor = editor;
    }

    @Override
    protected NoteEditorData doInBackground2(NoteEditorCommand commandIn) {
        command = commandIn;
        MyLog.v(NoteEditorData.TAG, () -> "Started: " + command);
        if (!command.acquireLock(true)) {
            return command.currentData;
        }
        savePreviousData();
        if (!command.currentData.isValid()) {
            command.loadCurrent();
        }
        saveCurrentData();
        return command.showAfterSave
                ? NoteEditorData.load(MyContextHolder.get(), command.currentData.getNoteId())
                : NoteEditorData.EMPTY;
    }

    private void savePreviousData() {
        if (command.needToSavePreviousData()) {
            MyLog.v(NoteEditorData.TAG, () -> "Saving previous data:" + command.previousData);
            command.previousData.save(Uri.EMPTY, Optional.empty());
            broadcastDataChanged(command.previousData);
        }
    }

    private void saveCurrentData() {
        MyLog.v(NoteEditorData.TAG, () -> "Saving current data:" + command.currentData);
        if (command.currentData.activity.getNote().getStatus() == DownloadStatus.DELETED) {
            MyProvider.deleteNoteAndItsActivities(MyContextHolder.get(), command.currentData.getNoteId());
        } else {
            command.currentData.save(command.getMediaUri(), command.getMediaType());
            if (command.beingEdited) {
                MyPreferences.setBeingEditedNoteId(command.currentData.getNoteId());
            }
            if (command.currentData.activity.getNote().getStatus() == DownloadStatus.SENDING) {
                CommandData commandData = CommandData.newUpdateStatus(
                        command.currentData.getMyAccount(),
                        command.currentData.activity.getId(), command.currentData.getNoteId());
                MyServiceManager.sendManualForegroundCommand(commandData);
            }
        }
        broadcastDataChanged(command.currentData);
    }

    private void broadcastDataChanged(NoteEditorData data) {
        if (data.isEmpty()) {
            return;
        }
        CommandData commandData = data.activity.getNote().getStatus() == DownloadStatus.DELETED ?
                CommandData.newItemCommand(CommandEnum.DELETE_NOTE, data.getMyAccount(), data.getNoteId()) :
                CommandData.newUpdateStatus(data.getMyAccount(), data.activity.getId(), data.getNoteId());
        MyServiceEventsBroadcaster.newInstance(MyContextHolder.get(), MyServiceState.UNKNOWN)
                .setCommandData(commandData).setEvent(MyServiceEvent.AFTER_EXECUTING_COMMAND).broadcast();
    }

    @Override
    protected void onCancelled() {
        command.releaseLock();
    }

    @Override
    protected void onPostExecute2(NoteEditorData data) {
        if (data.isValid()) {
            if (command.hasLock()) {
                MyLog.v(NoteEditorData.TAG, () -> "Saved; Future data: " + data);
                editor.showData(data);
            } else {
                MyLog.v(NoteEditorData.TAG, () -> "Saved; Result skipped: no lock");
            }
        } else {
            MyLog.v(NoteEditorData.TAG, "Saved; No future data");
        }
        command.releaseLock();
    }

}
