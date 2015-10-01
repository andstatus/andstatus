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
public class MessageEditorSaver extends AsyncTask<MessageEditorData, Void, MessageEditorData> {
    final MessageEditor editor;
    volatile MessageEditor.MyLock lock = MessageEditor.MyLock.EMPTY;

    public MessageEditorSaver(MessageEditor editor) {
        this.editor = editor;
    }

    @Override
    protected MessageEditorData doInBackground(MessageEditorData... params) {
        MessageEditorData data = params[0];
        MessageEditor.MyLock potentialLock = new MessageEditor.MyLock(true, data.getMsgId());
        if (!potentialLock.decidedToContinue()) {
            return data;
        }
        lock = potentialLock;

        MessageEditorData dataFirst = params[1];
        if (dataFirst != null && !dataFirst.isEmpty()
                && (dataFirst.getMsgId() == 0 || data.getMsgId() != dataFirst.getMsgId())) {
            MyLog.v(MessageEditorData.TAG, "Saver saving first data:" + dataFirst);
            dataFirst.setMsgId(save(dataFirst));
            broadcastDataChanged(dataFirst);
        }
        if (data.isEmpty()) {
            return data;
        } else {
            MyLog.v(MessageEditorData.TAG, "Saver saving last data:" + data);
            if (data.status == DownloadStatus.DELETED) {
                deleteDraft(data);
            } else {
                data.setMsgId(save(data));
                if (data.status == DownloadStatus.SENDING) {
                    CommandData commandData = CommandData.updateStatus(data.getMyAccount().getAccountName(), data.getMsgId());
                    MyServiceManager.sendManualForegroundCommand(commandData);
                }
            }
            broadcastDataChanged(data);
            return loadFutureData(data);
        }
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
        if (data.isEmpty()) {
            return;
        }
        CommandData commandData = new CommandData(
                data.status == DownloadStatus.DELETED ? CommandEnum.DESTROY_STATUS : CommandEnum.UPDATE_STATUS,
                data.getMyAccount().getAccountName(), data.getMsgId());
        MyServiceEventsBroadcaster.newInstance(MyContextHolder.get(), MyServiceState.UNKNOWN)
                .setCommandData(commandData).setEvent(MyServiceEvent.AFTER_EXECUTING_COMMAND).broadcast();
    }

    private MessageEditorData loadFutureData(MessageEditorData oldData) {
        MessageEditorData newData = oldData.hideBeforeSave
                ? MessageEditorData.newEmpty()
                : MessageEditorData.load(oldData.getMsgId());
        if (oldData.beingEdited) {
            MyPreferences.putLong(MyPreferences.KEY_BEING_EDITED_MESSAGE_ID, oldData.getMsgId());
        }
        return newData;
    }

    @Override
    protected void onCancelled() {
        lock.release();
    }

    @Override
    protected void onPostExecute(MessageEditorData data) {
        MyLog.v(MessageEditorData.TAG, "Saved; Future data: " + data);
        if (!data.isEmpty()) {
            editor.dataLoadedCallback(data);
        }
        lock.release();
    }

}
