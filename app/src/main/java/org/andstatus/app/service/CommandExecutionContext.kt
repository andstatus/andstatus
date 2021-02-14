package org.andstatus.app.service

import android.content.Context
import org.andstatus.app.account.MyAccount
import org.andstatus.app.context.MyContext
import org.andstatus.app.net.social.Connection
import org.andstatus.app.timeline.meta.Timeline

class CommandExecutionContext(myContext: MyContext?, commandData: CommandData?) {
    private val commandData: CommandData?
    val myContext: MyContext?
    fun getConnection(): Connection? {
        return getMyAccount().connection
    }

    fun getMyAccount(): MyAccount {
        if (commandData.myAccount.isValid) return commandData.myAccount
        return if (getTimeline().myAccountToSync.isValid) getTimeline().myAccountToSync else myContext.accounts().firstSucceeded
    }

    fun getMyContext(): MyContext? {
        return myContext
    }

    fun getContext(): Context? {
        return myContext.context()
    }

    fun getTimeline(): Timeline? {
        return commandData.getTimeline()
    }

    fun getCommandData(): CommandData? {
        return commandData
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

    init {
        requireNotNull(commandData) { "CommandData is null" }
        this.commandData = commandData
        this.myContext = myContext
    }
}