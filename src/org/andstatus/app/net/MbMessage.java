package org.andstatus.app.net;

import android.text.TextUtils;

/**
 * 'Mb' stands for "Microblogging system" 
 * @author Yuri Volkov
 */
public class MbMessage {
    public String oid="";
    public long sentDate = 0;
    public MbUser sender = null;
    public MbUser recipient = null; //TODO: Multiple recipients needed?!
    public String body = "";
    public MbMessage rebloggedMessage = null;
    public MbMessage inReplyToMessage = null;
    public String via = "";

    /**
     * Some additional attributes may appear from the Reader's
     * point of view (usually - from the point of view of the authenticated user)
     */
    public MbUser reader = null;
    public Boolean favoritedByReader = null;
    
    public TimelinePosition timelineItemPosition = null;
    public long timelineItemDate = 0;
    
    // In our system
    public long originId = 0L;

    
    public static MbMessage fromOriginAndOid(long originId, String oid) {
        MbMessage message = new MbMessage();
        message.originId = originId;
        message.oid = oid;
        message.timelineItemPosition = new TimelinePosition(oid);
        return message;
    }
    
    public static MbMessage getEmpty() {
        MbMessage message = new MbMessage();
        return message;
    }

    private MbMessage() {}
    
    public boolean isEmpty() {
        return (TextUtils.isEmpty(oid) || originId==0);
    }
}
