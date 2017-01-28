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

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.DownloadData;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MatchedUri;
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
 * Asynchronously save, delete and send a message, prepared by {@link MessageEditor}
 */
public class MessageEditorSaver extends MyAsyncTask<MessageEditorCommand, Void, MessageEditorData> {
    final MessageEditor editor;
    volatile MessageEditorCommand command = new MessageEditorCommand(MessageEditorData.INVALID);

    public MessageEditorSaver(MessageEditor editor) {
        super(PoolEnum.QUICK_UI);
        this.editor = editor;
    }

    @Override
    protected MessageEditorData doInBackground2(MessageEditorCommand... params) {
        command = params[0];
        MyLog.v(MessageEditorData.TAG, "Started: " + command);
        if (!command.acquireLock(true)) {
            return command.currentData;
        }
        savePreviousData();
        if (!command.currentData.isValid()) {
            command.loadCurrent();
        }
        saveCurrentData();
        return command.showAfterSave ? MessageEditorData.load(command.currentData.getMsgId()) : MessageEditorData.INVALID;
    }

    private void savePreviousData() {
        if (command.needToSavePreviousData()) {
            MyLog.v(MessageEditorData.TAG, "Saving previous data:" + command.previousData);
            command.previousData.save(Uri.EMPTY);
            broadcastDataChanged(command.previousData);
        }
    }

    private void saveCurrentData() {
        MyLog.v(MessageEditorData.TAG, "Saving current data:" + command.currentData);
        if (command.currentData.status == DownloadStatus.DELETED) {
            deleteDraft(command.currentData);
        } else {
            command.currentData.save(command.getMediaUri());
            if (command.beingEdited) {
                SharedPreferencesUtil.putLong(MyPreferences.KEY_BEING_EDITED_MESSAGE_ID,
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

    private void deleteDraft(MessageEditorData data) {
        DownloadData.deleteAllOfThisMsg(data.getMsgId());
        MyContextHolder.get().context().getContentResolver()
                .delete(MatchedUri.getMsgUri(0, data.getMsgId()), null, null);
    }

    private void broadcastDataChanged(MessageEditorData data) {
        if (data.isEmpty()) {
            return;
        }
        CommandData commandData = data.status == DownloadStatus.DELETED ?
                CommandData.newItemCommand(CommandEnum.DESTROY_STATUS, data.getMyAccount(), data.getMsgId()) :
                CommandData.newUpdateStatus(data.getMyAccount(), data.getMsgId());
        MyServiceEventsBroadcaster.newInstance(MyContextHolder.get(), MyServiceState.UNKNOWN)
                .setCommandData(commandData).setEvent(MyServiceEvent.AFTER_EXECUTING_COMMAND).broadcast();
    }

    @Override
    protected void onCancelled() {
        command.releaseLock();
    }

    @Override
    protected void onPostExecute(MessageEditorData data) {
        if (data.isValid()) {
            if (command.hasLock()) {
                MyLog.v(MessageEditorData.TAG, "Saved; Future data: " + data);
                editor.showData(data);
            } else {
                MyLog.v(MessageEditorData.TAG, "Saved; Result skipped: no lock");
            }
        } else {
            MyLog.v(MessageEditorData.TAG, "Saved; No future data");
        }
        command.releaseLock();
    }

}
