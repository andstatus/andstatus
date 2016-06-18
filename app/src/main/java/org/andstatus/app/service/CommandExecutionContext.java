package org.andstatus.app.service;

import android.content.Context;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.timeline.TimelineType;
import org.andstatus.app.util.MyLog;

public class CommandExecutionContext {
    private CommandData commandData;

    private TimelineType timelineType;
    /**
     * The Timeline (if any) is of this User 
     */
    private long timelineUserId = 0;

    private MyContext myContext;

    public CommandExecutionContext(CommandData commandData) {
        this(MyContextHolder.get(), commandData);
    }

    public CommandExecutionContext(MyContext myContext, CommandData commandData) {
        if (commandData == null) {
            throw new IllegalArgumentException( "CommandData is null");
        }
        this.commandData = commandData;
        this.timelineType = commandData.getTimelineType();
        this.myContext = myContext;
    }

    public MyAccount getMyAccount() {
        return commandData.getAccount();
    }

    public MyContext getMyContext() {
        return myContext;
    }

    public Context getContext() {
        return myContext.context();
    }

    public TimelineType getTimelineType() {
        return timelineType;
    }
    public CommandExecutionContext setTimelineType(TimelineType timelineType) {
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
        return MyLog.formatKeyValue(
                "CommandExecutionContext",
                getMyAccount().toString() + ","
                + (TimelineType.UNKNOWN.equals(timelineType) ? "" : timelineType
                        .toString() + ",")
                        + (timelineUserId == 0 ? "" : "userId:" + timelineUserId + ",")
                        + commandData.toString());
    }

    public String toExceptionContext() {
        StringBuilder builder = new StringBuilder(100);
        builder.append(getMyAccount().getAccountName() + ", ");
        if (!TimelineType.UNKNOWN.equals(timelineType)) {
            builder.append(timelineType.toString() + ", ");
        }
        if (timelineUserId != 0) {
            builder.append("userId:" + timelineUserId + ", ");
        }
        return builder.toString();
    }

    public String getCommandSummary() {
        CommandData commandData = getCommandData();
        if (commandData == null) {
            return "No command";
        }
        return commandData.toCommandSummary(getMyContext());
    }
}
