package org.andstatus.app.service

import android.content.Context
import org.andstatus.app.account.MyAccount
import org.andstatus.app.context.MyContext
import org.andstatus.app.net.social.Connection
import org.andstatus.app.timeline.meta.Timeline

class CommandExecutionContext(val myContext: MyContext, val commandData: CommandData) {

    val connection: Connection get() = myAccount.connection

    val myAccount: MyAccount
        get() {
            if (commandData.myAccount.isValid) return commandData.myAccount
            return if (timeline.myAccountToSync.isValid) timeline.myAccountToSync
            else myContext.accounts.getFirstSucceeded()
        }

    val context: Context get() = myContext.context

    val timeline: Timeline get() = commandData.timeline

    val result: CommandResult get() = commandData.result

    override fun toString(): String {
        return commandData.toString()
    }

    // TODO: Do we need this?
    val toExceptionContext: String get() = toString()

    val commandSummary: String get() = commandData.toCommandSummary(myContext)
}
