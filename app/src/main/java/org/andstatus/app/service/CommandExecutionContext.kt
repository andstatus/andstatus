package org.andstatus.app.service

import android.content.Context
import org.andstatus.app.account.MyAccount
import org.andstatus.app.context.MyContext
import org.andstatus.app.net.social.Connection
import org.andstatus.app.timeline.meta.Timeline

class CommandExecutionContext(val myContext: MyContext, val commandData: CommandData) {

    fun getConnection(): Connection {
        return getMyAccount().connection
    }

    fun getMyAccount(): MyAccount {
        if (commandData.myAccount.isValid()) return commandData.myAccount
        return if (getTimeline().myAccountToSync.isValid) getTimeline().myAccountToSync else myContext.accounts().firstSucceeded
    }

    fun getContext(): Context? {
        return myContext.context()
    }

    fun getTimeline(): Timeline {
        return commandData.getTimeline()
    }

    fun getResult(): CommandResult? {
        return commandData.getResult()
    }

    override fun toString(): String {
        return commandData.toString()
    }

    // TODO: Do we need this?
    fun toExceptionContext(): String? {
        return toString()
    }

    fun getCommandSummary(): String? {
        val commandData = getCommandData() ?: return "No command"
        return commandData.toCommandSummary(getMyContext())
    }
 }