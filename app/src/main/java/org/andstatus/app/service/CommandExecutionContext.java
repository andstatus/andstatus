package org.andstatus.app.service;

import android.content.Context;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.net.social.Connection;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.util.MyLog;

public class CommandExecutionContext {
    private CommandData commandData;
    public final MyContext myContext;

    public CommandExecutionContext(CommandData commandData) {
        this(MyContextHolder.get(), commandData);
    }

    public CommandExecutionContext(MyContext myContext, CommandData commandData) {
        if (commandData == null) {
            throw new IllegalArgumentException( "CommandData is null");
        }
        this.commandData = commandData;
        this.myContext = myContext;
    }

    public Connection getConnection() {
        return getMyAccount().getConnection();
    }

    public MyAccount getMyAccount() {
        MyAccount myAccount = getTimeline().getMyAccountToSync(myContext);
        if (!myAccount.isValid()) {
            myAccount = myContext.persistentAccounts().getFirstSucceeded();
        }
        return myAccount;
    }

    public MyContext getMyContext() {
        return myContext;
    }

    public Context getContext() {
        return myContext.context();
    }

    public Timeline getTimeline() {
        return commandData.getTimeline();
    }

    public CommandData getCommandData() {
        return commandData;
    }

    public CommandResult getResult() {
        return commandData.getResult();
    }

    @Override
    public String toString() {
        return MyLog.formatKeyValue(this, getMyAccount().toString() + ", " + commandData.toString());
    }

    // TODO: Do we need this?
    public String toExceptionContext() {
        return toString();
    }

    public String getCommandSummary() {
        CommandData commandData = getCommandData();
        if (commandData == null) {
            return "No command";
        }
        return commandData.toCommandSummary(getMyContext());
    }

}
