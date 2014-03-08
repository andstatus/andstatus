package org.andstatus.app.service;

import android.content.Context;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.TimelineTypeEnum;

public class CommandExecutionContext {
    private CommandData commandData;
    private MyAccount ma;
    private TimelineTypeEnum timelineType;    
    /**
     * The Timeline (if any) is of this User 
     */
    private long timelineUserId = 0;

    private Context context;
    
    public CommandExecutionContext(CommandData commandData, MyAccount ma) {
        if (commandData == null) {
            throw new IllegalArgumentException( "CommandData is null");
        }
        this.commandData = commandData;
        this.ma = ma;
        this.timelineType = commandData.getTimelineType();
        context = MyContextHolder.get().context();
    }

    public MyAccount getMyAccount() {
        return ma;
    }
    public void setMyAccount(MyAccount ma) {
        this.ma = ma;
    }

    public Context getContext() {
        return context;
    }

    public TimelineTypeEnum getTimelineType() {
        return timelineType;
    }
    public CommandExecutionContext setTimelineType(TimelineTypeEnum timelineType) {
        this.timelineType = timelineType;
        return this;
    }

    public long getTimelineUserId() {
        return timelineUserId;
    }

    public CommandExecutionContext setTimelineUserId(long timelineUserId) {
        this.timelineUserId = timelineUserId;
        return this;
    }

    public CommandData getCommandData() {
        return commandData;
    }
    
    public CommandResult getResult() {
        return commandData.getResult();
    }
    
    @Override
    public String toString() {
        String message = "CommandExecutionContext {";
        message += ma==null ? "" : "account=" + ma.toString() + "; ";
        message += timelineType.toString();
        message += timelineUserId==0 ? "" : "for userId=" + timelineUserId;
        message += "; " + commandData.toString();
        message += "}";
        return message;
    }
}
