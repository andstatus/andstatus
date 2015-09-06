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
public class MessageEditorSaver extends AsyncTask<MessageEditor, Void, Void> {
    volatile MessageEditor editor = null;

    @Override
    protected Void doInBackground(MessageEditor... params) {
        editor = params[0];
        MyLog.v(this,"Editor is " + (editor.isVisible() ? "visible" : "hidden")
        + "; " + editor.getData());
        if (editor.getData().status == DownloadStatus.DELETED) {
            deleteDraft(editor.getData());
        } else {
            saveOrSend(editor.getData());
        }
        return null;
    }

    private void deleteDraft(MessageEditorData data) {
        DownloadData.deleteAllOfThisMsg(data.msgId);
        MyContextHolder.get().context().getContentResolver()
                .delete(MatchedUri.getMsgUri(0, data.msgId), null, null);
    }

    private void saveOrSend(MessageEditorData data) {
        MbMessage message = MbMessage.fromOriginAndOid(data.getMyAccount().getOriginId(), "",
                data.status);
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
        if (!data.image.getUri().equals(Uri.EMPTY)) {
            message.attachments.add(MbAttachment.fromUriAndContentType(data.image.getUri(),
                    MyContentType.IMAGE));
        }
        DataInserter di = new DataInserter(data.getMyAccount());
        data.msgId = di.insertOrUpdateMsg(message);

        MyPreferences.putLong(MyPreferences.KEY_DRAFT_MESSAGE_ID, data.status == DownloadStatus.DRAFT ? data.msgId : 0);

        if (data.status == DownloadStatus.SENDING) {
            CommandData commandData = CommandData.updateStatus(data.getMyAccount().getAccountName(), data.msgId);
            MyServiceManager.sendManualForegroundCommand(commandData);
        }
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        MyLog.v(this,"Completed; " + editor.getData());
        editor.dataSaved();
    }

}
