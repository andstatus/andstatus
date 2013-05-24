package org.andstatus.app;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyProvider;


/**
 * Helper class for the message context menu creation 
 * @author yvolk
 */
class MessageDataForContextMenu {
    /**
     * MyAccount, suitable for this message, null if nothing was found
     */
    public MyAccount ma = null;
    
    public String body = "";
    boolean isDirect = false;
    long authorId = 0;
    long senderId = 0;
    boolean favorited = false;
    boolean reblogged = false;
    boolean senderFollowed = false;
    boolean authorFollowed = false;
    /**
     * If this message was sent by current User, we may delete it.
     */
    boolean isSender = false;
    boolean isAuthor = false;
    
    boolean canUseCurrentAccountInsteadOfLinked = false;
    
    public MessageDataForContextMenu(Context context, long msgId, long linkedUserId, long currentMyAccountUserId) {
        ma = MyAccount.getMyAccountLinkedToThisMessage(msgId, linkedUserId,
                currentMyAccountUserId);
        if (ma == null) {
            return;
        }

        // Get the record for the currently selected item
        Uri uri = MyProvider.getTimelineMsgUri(ma.getUserId(), msgId, false);
        Cursor c = context.getContentResolver().query(uri, new String[] {
                MyDatabase.Msg._ID, MyDatabase.Msg.BODY, MyDatabase.Msg.SENDER_ID,
                MyDatabase.Msg.AUTHOR_ID, MyDatabase.MsgOfUser.FAVORITED,
                MyDatabase.Msg.RECIPIENT_ID,
                MyDatabase.MsgOfUser.REBLOGGED,
                MyDatabase.FollowingUser.SENDER_FOLLOWED,
                MyDatabase.FollowingUser.AUTHOR_FOLLOWED
        }, null, null, null);
        try {
            if (c != null && c.getCount() > 0) {
                c.moveToFirst();
                isDirect = !c.isNull(c.getColumnIndex(MyDatabase.Msg.RECIPIENT_ID));
                authorId = c.getLong(c.getColumnIndex(MyDatabase.Msg.AUTHOR_ID));
                senderId = c.getLong(c.getColumnIndex(MyDatabase.Msg.SENDER_ID));
                favorited = c.getInt(c.getColumnIndex(MyDatabase.MsgOfUser.FAVORITED)) == 1;
                reblogged = c.getInt(c.getColumnIndex(MyDatabase.MsgOfUser.REBLOGGED)) == 1;
                senderFollowed = c.getInt(c
                        .getColumnIndex(MyDatabase.FollowingUser.SENDER_FOLLOWED)) == 1;
                authorFollowed = c.getInt(c
                        .getColumnIndex(MyDatabase.FollowingUser.AUTHOR_FOLLOWED)) == 1;
                /**
                 * If this message was sent by current User, we may delete it.
                 */
                isSender = (ma.getUserId() == senderId);
                isAuthor = (ma.getUserId() == authorId);

                body = c.getString(c.getColumnIndex(MyDatabase.Msg.BODY));
                
                /*
                 * Let's check if we can use current account instead of linked
                 * to this message
                 */
                if (!isDirect && !favorited && !reblogged && !isSender && !senderFollowed && !authorFollowed
                        && ma.getUserId() != currentMyAccountUserId) {
                    MyAccount ma2 = MyAccount.getMyAccount(currentMyAccountUserId);
                    if (ma2 != null && ma.getOriginId() == ma2.getOriginId()) {
                        // Yes, use current Account!
                        canUseCurrentAccountInsteadOfLinked = true;
                    }
                }
                
            }
        } finally {
            if (c != null && !c.isClosed())
                c.close();
        }
    }
}
