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

package org.andstatus.app.msg;

import android.net.Uri;
import android.os.AsyncTask;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.DownloadData;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyServiceEvent;
import org.andstatus.app.service.MyServiceEventsBroadcaster;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.service.MyServiceState;
import org.andstatus.app.util.MyLog;

/**
 * Asynchronously save, delete and send a message, prepared by {@link MessageEditor}
 */
public class MessageEditorSaver extends AsyncTask<MessageEditorCommand, Void, MessageEditorData> {
    final MessageEditor editor;
    volatile MessageEditor.MyLock lock = MessageEditor.MyLock.EMPTY;

    public MessageEditorSaver(MessageEditor editor) {
        this.editor = editor;
    }

    @Override
    protected MessageEditorData doInBackground(MessageEditorCommand... params) {
        MessageEditorCommand command = params[0];
        if (!acquireLock(command.getCurrentMsgId())) {
            return command.currentData;
        }
        savePreviousData(command);
        if (!command.currentData.isValid()) {
            command.loadCurrent();
        }
        saveCurrentData(command);
        return command.showAfterSave ? MessageEditorData.load(command.currentData.getMsgId()) : MessageEditorData.INVALID;
    }

    private boolean acquireLock(long msgId) {
        lock = new MessageEditor.MyLock(true, msgId);
        return lock.decidedToContinue();
    }

    private void savePreviousData(MessageEditorCommand command) {
        if (command.needToSavePreviousData()) {
            MyLog.v(MessageEditorData.TAG, "Saving previous data:" + command.previousData);
            command.previousData.save(Uri.EMPTY);
            broadcastDataChanged(command.previousData);
        }
    }

    private void saveCurrentData(MessageEditorCommand command) {
        MyLog.v(MessageEditorData.TAG, "Saving current data:" + command.currentData);
        if (command.currentData.status == DownloadStatus.DELETED) {
            deleteDraft(command.currentData);
        } else {
            command.currentData.save(command.getMediaUri());
            if (command.beingEdited) {
                MyPreferences.putLong(MyPreferences.KEY_BEING_EDITED_MESSAGE_ID,
                        command.currentData.getMsgId());
            }
            if (command.currentData.status == DownloadStatus.SENDING) {
                CommandData commandData = CommandData.updateStatus(
                        command.currentData.getMyAccount().getAccountName(),
                        command.currentData.getMsgId());
                MyServiceManager.sendManualForegroundCommand(commandData);
            }
        }
        broadcastDataChanged(command.currentData);
    }

    private void deleteDraft(MessageEditorData data) {
        DownloadData.deleteAllOfThisMsg(data.getMsgId());
        MyContextHolder.get().context().getContentResolver()
                .delete(MatchedUri.getMsgUri(0, data.getMsgId()), null, null);
    }

    private void broadcastDataChanged(MessageEditorData data) {
        if (data.isEmpty()) {
            return;
        }
        CommandData commandData = new CommandData(
                data.status == DownloadStatus.DELETED ? CommandEnum.DESTROY_STATUS : CommandEnum.UPDATE_STATUS,
                data.getMyAccount().getAccountName(), data.getMsgId());
        MyServiceEventsBroadcaster.newInstance(MyContextHolder.get(), MyServiceState.UNKNOWN)
                .setCommandData(commandData).setEvent(MyServiceEvent.AFTER_EXECUTING_COMMAND).broadcast();
    }

    @Override
    protected void onCancelled() {
        lock.release();
    }

    @Override
    protected void onPostExecute(MessageEditorData data) {
        MyLog.v(MessageEditorData.TAG, "Saved; Future data: " + data);
        editor.showData(data);
        lock.release();
    }

}
