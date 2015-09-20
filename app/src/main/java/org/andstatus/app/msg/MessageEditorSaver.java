package org.andstatus.app.msg;

import android.net.Uri;
import android.os.AsyncTask;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.DataInserter;
import org.andstatus.app.data.DownloadData;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.data.MyContentType;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.net.social.MbAttachment;
import org.andstatus.app.net.social.MbMessage;
import org.andstatus.app.net.social.MbUser;
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
public class MessageEditorSaver extends AsyncTask<MessageEditor, Void, MessageEditorData> {
    volatile MessageEditor editor = null;
    volatile MessageEditor.MyLock lock = MessageEditor.MyLock.EMPTY;

    @Override
    protected MessageEditorData doInBackground(MessageEditor... params) {
        editor = params[0];
        MessageEditorData data = editor.getData();
        MessageEditor.MyLock potentialLock = new MessageEditor.MyLock(true, data.getMsgId());
        if (!potentialLock.decidedToContinue()) {
            return data;
        }
        lock = potentialLock;

        MyLog.v(MessageEditorData.TAG, "Saver started, Editor is " + (editor.isVisible() ? "visible" : "hidden")
        + "; " + data);
        long msgId = 0;
        if (data.status == DownloadStatus.DELETED) {
            deleteDraft(data);
        } else {
            msgId = save(data);
            if (data.status == DownloadStatus.SENDING) {
                CommandData commandData = CommandData.updateStatus(data.getMyAccount().getAccountName(), msgId);
                MyServiceManager.sendManualForegroundCommand(commandData);
            }
        }
        broadcastDataChanged(data);
        return loadFutureData(data, msgId);
    }

    private void deleteDraft(MessageEditorData data) {
        DownloadData.deleteAllOfThisMsg(data.getMsgId());
        MyContextHolder.get().context().getContentResolver()
                .delete(MatchedUri.getMsgUri(0, data.getMsgId()), null, null);
    }

    private long save(MessageEditorData data) {
        MbMessage message = MbMessage.fromOriginAndOid(data.getMyAccount().getOriginId(), "",
                data.status);
        message.msgId = data.getMsgId();
        message.actor = MbUser.fromOriginAndUserOid(data.getMyAccount().getOriginId(),
                data.getMyAccount().getUserOid());
        message.sender = message.actor;
        message.sentDate = System.currentTimeMillis();
        message.setBody(data.body);
        if (data.recipientId != 0) {
            message.recipient = MbUser.fromOriginAndUserOid(data.getMyAccount().getOriginId(),
                    MyQuery.idToOid(MyDatabase.OidEnum.USER_OID, data.recipientId, 0));
        }
        if (data.inReplyToId != 0) {
            message.inReplyToMessage = MbMessage.fromOriginAndOid(data.getMyAccount().getOriginId(),
                    MyQuery.idToOid(MyDatabase.OidEnum.MSG_OID, data.inReplyToId, 0),
                    DownloadStatus.UNKNOWN);
        }
        if (!data.getMediaUri().equals(Uri.EMPTY)) {
            message.attachments.add(
                    MbAttachment.fromUriAndContentType(data.getMediaUri(), MyContentType.IMAGE));
        }
        DataInserter di = new DataInserter(data.getMyAccount());
        return di.insertOrUpdateMsg(message);
    }

    private void broadcastDataChanged(MessageEditorData data) {
        CommandData commandData = new CommandData(
                data.status == DownloadStatus.DELETED ? CommandEnum.DESTROY_STATUS : CommandEnum.UPDATE_STATUS,
                data.getMyAccount().getAccountName(), data.getMsgId());
        MyServiceEventsBroadcaster.newInstance(MyContextHolder.get(), MyServiceState.UNKNOWN)
                .setCommandData(commandData).setEvent(MyServiceEvent.AFTER_EXECUTING_COMMAND).broadcast();
    }

    private MessageEditorData loadFutureData(MessageEditorData oldData, long msgId) {
        boolean loadNewEmpty = oldData.status != DownloadStatus.DRAFT || oldData.hideAfterSave;
        MessageEditorData newData = loadNewEmpty ?
                MessageEditorData.newEmpty(oldData.getMyAccount()) : MessageEditorData.load(msgId);
        newData.showAfterSaveOrLoad = oldData.showAfterSaveOrLoad;
        newData.hideAfterSave = oldData.hideAfterSave;
        if (newData.status == DownloadStatus.DRAFT && newData.getMsgId() != 0) {
            MyPreferences.putLong(MyPreferences.KEY_BEING_EDITED_DRAFT_MESSAGE_ID, newData.getMsgId());
        } else if ( oldData.hideAfterSave) {
            MyPreferences.putLong(MyPreferences.KEY_BEING_EDITED_DRAFT_MESSAGE_ID, 0);
        }
        return newData;
    }

    @Override
    protected void onCancelled() {
        lock.release();
    }

    @Override
    protected void onPostExecute(MessageEditorData data) {
        if (lock.isEmpty()) {
            MyLog.v(MessageEditorData.TAG, "Saver skipped saving data: " + data);

        } else {
            MyLog.v(MessageEditorData.TAG, "Saved; Future data: " + data);
            editor.dataSavedCallback(data);
            lock.release();
        }
    }

}
