package org.andstatus.app.service;

import android.content.Context;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.net.social.Connection;
import org.andstatus.app.timeline.meta.Timeline;

import androidx.annotation.NonNull;

public class CommandExecutionContext {
    private CommandData commandData;
    public final MyContext myContext;

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

    @NonNull
    public MyAccount getMyAccount() {
        if (commandData.myAccount.isValid()) return commandData.myAccount;

        if (getTimeline().myAccountToSync.isValid()) return getTimeline().myAccountToSync;

        return myContext.accounts().getFirstSucceeded();
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
        return commandData.toString();
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
