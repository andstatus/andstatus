package org.andstatus.app.service;

import android.content.Context;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.TimelineTypeEnum;
import org.andstatus.app.util.MyLog;

public class CommandExecutionContext {
    private CommandData commandData;
    private MyAccount ma;
    private TimelineTypeEnum timelineType;    
    /**
     * The Timeline (if any) is of this User 
     */
    private long timelineUserId = 0;

    private MyContext myContext;

    public CommandExecutionContext(CommandData commandData, MyAccount ma) {
        this(MyContextHolder.get(), commandData, ma);
    }
    
    public CommandExecutionContext(MyContext myContext, CommandData commandData, MyAccount ma) {
        if (commandData == null) {
            throw new IllegalArgumentException( "CommandData is null");
        }
        this.commandData = commandData;
        this.ma = ma;
        this.timelineType = commandData.getTimelineType();
        this.myContext = myContext;
    }

    public MyAccount getMyAccount() {
        return ma;
    }
    public void setMyAccount(MyAccount ma) {
        this.ma = ma;
    }

    public MyContext getMyContext() {
        return myContext;
    }
    
    public Context getContext() {
        return myContext.context();
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
    
    public CommandData getCommandDataForOneStep() {
        return CommandData.forOneExecStep(this);
    }
    
    public CommandResult getResult() {
        return commandData.getResult();
    }
    
    @Override
    public String toString() {
        return MyLog.formatKeyValue("CommandExecutionContext",
        (ma==null ? "" : ma.toString() + ",")
        + timelineType.toString() + ","
        + (timelineUserId==0 ? "" : "forUserId:" + timelineUserId + ",")
        + commandData.toString());
    }
}
