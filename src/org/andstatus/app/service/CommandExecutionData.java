package org.andstatus.app.service;

import android.content.Context;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.data.TimelineTypeEnum;

public class CommandExecutionData {
    private MyAccount ma;
    private Context context;
    private TimelineTypeEnum timelineType = TimelineTypeEnum.ALL;    
    /**
     * The Timeline (if any) is of this User 
     */
    private long timelineUserId = 0;

    // Raw counters
    private int newMessagesCount = 0;
    private int newMentionsCount = 0;
    private int newDownloadedCount = 0;

    // Accumulated counters to use for user notifications
    private int messagesAdded = 0;
    private int mentionsAdded = 0;
    private int directedAdded = 0;
    private int downloadedCount = 0;
    
    public CommandExecutionData(MyAccount ma, Context context) {
        this.ma = ma;
        this.context = context;
    }

    public MyAccount getMyAccount() {
        return ma;
    }

    public Context getContext() {
        return context;
    }

    public TimelineTypeEnum getTimelineType() {
        return timelineType;
    }
    public CommandExecutionData setTimelineType(TimelineTypeEnum timelineType) {
        this.timelineType = timelineType;
        return this;
    }

    public long getTimelineUserId() {
        return timelineUserId;
    }

    public CommandExecutionData setTimelineUserId(long timelineUserId) {
        this.timelineUserId = timelineUserId;
        return this;
    }

    public void incrementMessagesCount() {
        newMessagesCount++;
    }

    public void incrementMentionsCount() {
        newMentionsCount++;
    }

    public void incrementDownloadedCount() {
        newDownloadedCount++;
    }
    
    public void accumulate() {
        downloadedCount  += newDownloadedCount;
        switch (getTimelineType()) {
            case MENTIONS:
                mentionsAdded += newMentionsCount;
                break;
            case HOME:
                messagesAdded += newMessagesCount;
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
        newDownloadedCount = 0;
    }
    
    @Override
    public String toString() {
        String message = "";
        if (downloadedCount > 0) {
            message += ", " + downloadedCount + " downloaded";
        }
        if (getMessagesAdded() > 0) {
            message += ", " + messagesAdded + " messages";
        }
        if (mentionsAdded > 0) {
            message += ", " + mentionsAdded + " mentions";
        }
        if (directedAdded > 0) {
            message += ", " + directedAdded + " directs";
        }
        return message;
    }
    
    protected int getMessagesAdded() {
        return messagesAdded;
    }

    protected int getMentionsAdded() {
        return mentionsAdded;
    }

    protected int getDirectedAdded() {
        return directedAdded;
    }
}
