package org.andstatus.app.msg;

import android.net.Uri;
import android.os.AsyncTask;

import org.andstatus.app.data.DataInserter;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MyContentType;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.net.social.MbAttachment;
import org.andstatus.app.net.social.MbMessage;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.MyServiceManager;

/**
 * Asynchronously send a message, prepared by {@link MessageEditor}
 */
public class MessageSender extends AsyncTask<MessageEditorData, Void, Void> {
    @Override
    protected Void doInBackground(MessageEditorData... params) {
        MessageEditorData data = params[0];

        MbMessage message = MbMessage.fromOriginAndOid(data.getMyAccount().getOriginId(), "",
                DownloadStatus.SENDING);
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
        if (data.mediaUri != Uri.EMPTY) {
            message.attachments.add(MbAttachment.fromUriAndContentType(data.mediaUri,
                    MyContentType.IMAGE));
        }
        DataInserter di = new DataInserter(data.getMyAccount());
        message.msgId = di.insertOrUpdateMsg(message);

        CommandData commandData = CommandData.updateStatus(data.getMyAccount().getAccountName(), message.msgId);
        MyServiceManager.sendForegroundCommand(commandData);
        return null;
    }
}
