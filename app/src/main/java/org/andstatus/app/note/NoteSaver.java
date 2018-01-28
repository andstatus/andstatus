/**
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
import org.andstatus.app.util.SharedPreferencesUtil;

/**
 * Asynchronously save, delete and send a note, prepared by {@link NoteEditor}
 */
public class NoteSaver extends MyAsyncTask<NoteEditorCommand, Void, NoteEditorData> {
    final NoteEditor editor;
    volatile NoteEditorCommand command = new NoteEditorCommand(NoteEditorData.EMPTY);

    public NoteSaver(NoteEditor editor) {
        super(PoolEnum.QUICK_UI);
        this.editor = editor;
    }

    @Override
    protected NoteEditorData doInBackground2(NoteEditorCommand... params) {
        command = params[0];
        MyLog.v(NoteEditorData.TAG, "Started: " + command);
        if (!command.acquireLock(true)) {
            return command.currentData;
        }
        savePreviousData();
        if (!command.currentData.isValid()) {
            command.loadCurrent();
        }
        saveCurrentData();
        return command.showAfterSave ? NoteEditorData.load(command.currentData.getMsgId()) : NoteEditorData.EMPTY;
    }

    private void savePreviousData() {
        if (command.needToSavePreviousData()) {
            MyLog.v(NoteEditorData.TAG, "Saving previous data:" + command.previousData);
            command.previousData.save(Uri.EMPTY);
            broadcastDataChanged(command.previousData);
        }
    }

    private void saveCurrentData() {
        MyLog.v(NoteEditorData.TAG, "Saving current data:" + command.currentData);
        if (command.currentData.status == DownloadStatus.DELETED) {
            MyProvider.deleteNote(MyContextHolder.get().context(), command.currentData.getMsgId());
        } else {
            command.currentData.save(command.getMediaUri());
            if (command.beingEdited) {
                SharedPreferencesUtil.putLong(MyPreferences.KEY_BEING_EDITED_NOTE_ID,
                        command.currentData.getMsgId());
            }
            if (command.currentData.status == DownloadStatus.SENDING) {
                CommandData commandData = CommandData.newUpdateStatus(
                        command.currentData.getMyAccount(),
                        command.currentData.getMsgId());
                MyServiceManager.sendManualForegroundCommand(commandData);
            }
        }
        broadcastDataChanged(command.currentData);
    }

    private void broadcastDataChanged(NoteEditorData data) {
        if (data.isEmpty()) {
            return;
        }
        CommandData commandData = data.status == DownloadStatus.DELETED ?
                CommandData.newItemCommand(CommandEnum.DELETE_NOTE, data.getMyAccount(), data.getMsgId()) :
                CommandData.newUpdateStatus(data.getMyAccount(), data.getMsgId());
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
                MyLog.v(NoteEditorData.TAG, "Saved; Future data: " + data);
                editor.showData(data);
            } else {
                MyLog.v(NoteEditorData.TAG, "Saved; Result skipped: no lock");
            }
        } else {
            MyLog.v(NoteEditorData.TAG, "Saved; No future data");
        }
        command.releaseLock();
    }

}
