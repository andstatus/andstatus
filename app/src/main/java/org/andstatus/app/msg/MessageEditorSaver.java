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
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.MyLog;

/**
 * Asynchronously save, delete and send a message, prepared by {@link MessageEditor}
 */
public class MessageEditorSaver extends AsyncTask<MessageEditor, Void, MessageEditorData> {
    volatile MessageEditor editor = null;

    @Override
    protected MessageEditorData doInBackground(MessageEditor... params) {
        editor = params[0];
        MessageEditorData data = editor.getData();
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
        return loadFutureData(data, msgId);
    }

    private void deleteDraft(MessageEditorData data) {
        DownloadData.deleteAllOfThisMsg(data.msgId);
        MyContextHolder.get().context().getContentResolver()
                .delete(MatchedUri.getMsgUri(0, data.msgId), null, null);
    }

    private long save(MessageEditorData data) {
        MbMessage message = MbMessage.fromOriginAndOid(data.getMyAccount().getOriginId(), "",
                data.status);
        message.msgId = data.msgId;
        message.actor = MbUser.fromOriginAndUserOid(data.getMyAccount().getOriginId(),
                data.getMyAccount().getUserOid());
        message.sender = message.actor;
        message.sentDate = System.currentTimeMillis();
        message.setBody(data.messageText);
        if (data.recipientId != 0) {
            message.recipient = MbUser.fromOriginAndUserOid(data.getMyAccount().getOriginId(),
                    MyQuery.idToOid(MyDatabase.OidEnum.USER_OID, data.recipientId, 0));
        }
        if (data.inReplyToId != 0) {
            message.inReplyToMessage = MbMessage.fromOriginAndOid(data.getMyAccount().getOriginId(),
                    MyQuery.idToOid(MyDatabase.OidEnum.MSG_OID, data.inReplyToId, 0),
                    DownloadStatus.UNKNOWN);
        }
        Uri imageUri = data.imageUriToSave.equals(Uri.EMPTY) ? data.image.getUri() : data.imageUriToSave;
        if (!imageUri.equals(Uri.EMPTY)) {
            message.attachments.add(
                    MbAttachment.fromUriAndContentType(imageUri, MyContentType.IMAGE));
        }
        DataInserter di = new DataInserter(data.getMyAccount());
        return di.insertOrUpdateMsg(message);
    }

    private MessageEditorData loadFutureData(MessageEditorData oldData, long msgId) {
        boolean loadNew = oldData.status != DownloadStatus.DRAFT;
        MyPreferences.putLong(MyPreferences.KEY_DRAFT_MESSAGE_ID, loadNew ? 0 : msgId);
        MessageEditorData newData = loadNew ?
                MessageEditorData.newEmpty(oldData.getMyAccount()) : MessageEditorData.load();
        newData.showAfterSaveOrLoad = oldData.showAfterSaveOrLoad;
        return newData;
    }

    @Override
    protected void onPostExecute(MessageEditorData data) {
        MyLog.v(MessageEditorData.TAG, "Saved; Future data: " + data);
        editor.dataSavedCallback(data);
    }

}
