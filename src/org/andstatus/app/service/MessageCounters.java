package org.andstatus.app.service;

import android.content.Context;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.data.TimelineTypeEnum;

public class MessageCounters {
    public MyAccount ma;
    public Context context;
    public TimelineTypeEnum timelineType;    

    // Raw counters
    public int newMessagesCount = 0;
    public int newMentionsCount = 0;
    public int newRepliesCount = 0;
    public int totalMessagesDownloaded = 0;

    // Accumulated counters to use for user notifications
    public int msgAdded = 0;
    public int mentionsAdded = 0;
    public int directedAdded = 0;
    public int downloadedCount = 0;
    
    public MessageCounters(MyAccount ma, Context context, TimelineTypeEnum timelineType) {
        this.ma = ma;
        this.context = context;
        this.timelineType = timelineType;
    }
    
    public void accumulate() {
        downloadedCount  += totalMessagesDownloaded;
        switch (timelineType) {
            case MENTIONS:
                mentionsAdded += newMentionsCount;
                break;
            case HOME:
                msgAdded += newMessagesCount;
                mentionsAdded += newMentionsCount;
                break;
            case DIRECT:
                directedAdded += newMessagesCount;
                break;
            case FOLLOWING_USER:
            case USER:
                // Don't count anything for now...
                break;
            default:
                break;
        }
        newMessagesCount = 0;
        newMentionsCount = 0;
        newRepliesCount = 0;
        totalMessagesDownloaded = 0;
    }
    
    public String accumulatedToString() {
        String message = "";
        if (downloadedCount > 0) {
            message += ", " + downloadedCount + " downloaded";
        }
        if (msgAdded > 0) {
            message += ", " + msgAdded + " messages";
        }
        if (mentionsAdded > 0) {
            message += ", " + mentionsAdded + " mentions";
        }
        if (directedAdded > 0) {
            message += ", " + directedAdded + " directs";
        }
        return message;
    }
}
