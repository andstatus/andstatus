/* 
 * Copyright (c) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.andstatus.app.service

import io.vavr.control.Try
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.net.http.ConnectionException
import org.andstatus.app.net.social.Actor
import org.andstatus.app.net.social.ApiRoutineEnum
import org.andstatus.app.net.social.Connection
import org.andstatus.app.service.CommandEnum
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.IdentifiableInstance
import org.andstatus.app.util.InstanceId
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.RelativeTime
import org.andstatus.app.util.StopWatch
import org.andstatus.app.util.TryUtils
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

internal open class CommandExecutorStrategy(protected val execContext: CommandExecutionContext?) : CommandExecutorParent, IdentifiableInstance {
    protected val instanceId = InstanceId.next()
    private var parent: CommandExecutorParent? = null
    protected var lastProgressBroadcastAt: Long = 0
    private val stopWatch: StopWatch? = StopWatch.Companion.createStarted()
    fun logSoftErrorIfStopping(): Boolean {
        if (isStopping) {
            if (!execContext.getResult().hasError()) {
                execContext.getResult().incrementNumIoExceptions()
                execContext.getResult().message = "Service is stopping"
            }
            return true
        }
        return false
    }

    protected fun <T> onParseException(message: String?): Try<T?>? {
        execContext.getResult().incrementParseExceptions()
        MyLog.w(this, message)
        return TryUtils.failure(message)
    }

    fun broadcastProgress(progress: String?, notTooOften: Boolean) {
        if (notTooOften) {
            if (!RelativeTime.moreSecondsAgoThan(lastProgressBroadcastAt, MIN_PROGRESS_BROADCAST_PERIOD_SECONDS)) {
                return
            }
        }
        MyLog.v(this) { "Progress: $progress" }
        lastProgressBroadcastAt = System.currentTimeMillis()
        MyServiceEventsBroadcaster.Companion.newInstance(MyContextHolder.Companion.myContextHolder.getNow(), MyServiceState.RUNNING)
                .setCommandData(execContext.getCommandData())
                .setProgress(progress)
                .setEvent(MyServiceEvent.PROGRESS_EXECUTING_COMMAND).broadcast()
    }

    private fun setParent(parent: CommandExecutorParent?): CommandExecutorStrategy? {
        this.parent = parent
        return this
    }

    override fun isStopping(): Boolean {
        return if (parent != null) {
            parent.isStopping()
        } else {
            false
        }
    }

    fun <T> logException(t: Throwable?, detailedMessage: String?): Try<T?>? {
        val e: ConnectionException = ConnectionException.Companion.of(t)
        val isHard = t != null && e.isHardError
        val builder: MyStringBuilder = MyStringBuilder.Companion.of(detailedMessage)
        if (t != null) {
            builder.atNewLine(e.toString())
        }
        return logExecutionError(isHard, builder.toString())
    }

    fun <T> logExecutionError(isHard: Boolean, detailedMessage: String?): Try<T?>? {
        if (isHard) {
            execContext.getResult().incrementParseExceptions()
        } else {
            execContext.getResult().incrementNumIoExceptions()
        }
        val builder: MyStringBuilder = MyStringBuilder.Companion.of(detailedMessage).atNewLine(execContext.toExceptionContext())
        execContext.getResult().message = builder.toString()
        MyLog.w(this, builder.toString())
        return TryUtils.failure(detailedMessage)
    }

    /** @return Success and false means soft error occurred
     */
    open fun execute(): Try<Boolean?>? {
        MyLog.d(this, "Doing nothing")
        return Try.success(true)
    }

    fun noErrors(): Boolean {
        return !execContext.getResult().hasError()
    }

    fun getActor(): Actor? {
        return execContext.getCommandData().timeline.actor
    }

    open fun isApiSupported(routine: ApiRoutineEnum?): Boolean {
        return getConnection().hasApiEndpoint(routine)
    }

    fun getConnection(): Connection? {
        return execContext.getConnection()
    }

    override fun getInstanceId(): Long {
        return instanceId
    }

    override fun classTag(): String? {
        return TAG
    }

    companion object {
        private val TAG: String? = CommandExecutorStrategy::class.java.simpleName
        protected const val MIN_PROGRESS_BROADCAST_PERIOD_SECONDS: Long = 1
        fun executeCommand(commandData: CommandData?, parent: CommandExecutorParent?) {
            val strategy = getStrategy(
                    CommandExecutionContext(commandData.myAccount.origin.myContext, commandData)).setParent(parent)
            commandData.getResult().prepareForLaunch()
            logLaunch(strategy)
            // This may cause recursive calls to executors...
            strategy.execute()
                    .onSuccess(Consumer { ok: Boolean? ->
                        strategy.execContext.getResult().setSoftErrorIfNotOk(ok)
                        MyLog.d(strategy, strategy.execContext.getCommandSummary() + if (ok) " succeeded" else " soft errors")
                    })
                    .onFailure { t: Throwable? -> strategy.logException<Any?>(t, strategy.execContext.getCommandSummary()) }
            commandData.getResult().afterExecutionEnded()
            logEnd(strategy)
        }

        private fun logLaunch(strategy: CommandExecutorStrategy?) {
            strategy.stopWatch.restart()
            MyLog.d(strategy, "Launching " + strategy.execContext)
        }

        private fun logEnd(strategy: CommandExecutorStrategy?) {
            val time = strategy.stopWatch.getTime()
            if (time < TimeUnit.SECONDS.toMillis(MIN_PROGRESS_BROADCAST_PERIOD_SECONDS)) {
                MyLog.d(strategy, "commandExecutedMs:" + time + "; " + strategy.execContext)
            } else {
                MyLog.i(strategy, "commandExecutedMs:" + time + "; " + strategy.execContext)
            }
        }

        fun getStrategy(commandData: CommandData?, parent: CommandExecutorParent?): CommandExecutorStrategy? {
            return getStrategy(
                    CommandExecutionContext(commandData.myAccount.origin.myContext, commandData)).setParent(parent)
        }

        private fun getStrategy(execContext: CommandExecutionContext?): CommandExecutorStrategy? {
            val strategy: CommandExecutorStrategy
            strategy = when (execContext.getCommandData().command) {
                CommandEnum.GET_ATTACHMENT, CommandEnum.GET_AVATAR -> CommandExecutorOther(execContext)
                CommandEnum.GET_OPEN_INSTANCES -> CommandExecutorGetOpenInstances(execContext)
                else -> if (execContext.getMyAccount().isValidAndSucceeded) {
                    when (execContext.getCommandData().command) {
                        CommandEnum.GET_TIMELINE, CommandEnum.GET_OLDER_TIMELINE -> if (execContext.getCommandData().timeline.isSyncable) {
                            when (execContext.getCommandData().timelineType) {
                                TimelineType.FOLLOWERS, TimelineType.FRIENDS -> TimelineDownloaderFollowers(execContext)
                                else -> TimelineDownloaderOther(execContext)
                            }
                        } else {
                            MyLog.v(CommandExecutorStrategy::class.java) {
                                "Dummy commandExecutor for " +
                                        execContext.getCommandData().timeline
                            }
                            CommandExecutorStrategy(execContext)
                        }
                        CommandEnum.GET_FOLLOWERS, CommandEnum.GET_FRIENDS -> CommandExecutorFollowers(execContext)
                        else -> CommandExecutorOther(execContext)
                    }
                } else {
                    MyLog.v(CommandExecutorStrategy::class.java) {
                        ("Dummy commandExecutor for "
                                + execContext.getMyAccount())
                    }
                    CommandExecutorStrategy(execContext)
                }
            }
            return strategy
        }
    }
}