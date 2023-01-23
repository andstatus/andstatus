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
import org.andstatus.app.net.http.ConnectionException
import org.andstatus.app.net.http.HttpReadResult.Companion.toHttpReadResult
import org.andstatus.app.net.social.Actor
import org.andstatus.app.net.social.ApiRoutineEnum
import org.andstatus.app.net.social.Connection
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.Identifiable
import org.andstatus.app.util.InstanceId
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.RelativeTime
import org.andstatus.app.util.StopWatch
import org.andstatus.app.util.TryUtils
import org.andstatus.app.util.TryUtils.onFailureAsConnectionException
import java.util.*

open class CommandExecutorStrategy(val execContext: CommandExecutionContext) : CommandExecutorParent, Identifiable {
    override val instanceId = InstanceId.next()
    private var parent: CommandExecutorParent? = null
    protected var lastProgressBroadcastAt: Long = 0
    private val stopWatch: StopWatch = StopWatch.createStarted()

    fun logSoftErrorIfStopping(): Boolean {
        if (isStopping()) {
            if (!execContext.result.hasError) {
                execContext.result.incrementNumIoExceptions()
                execContext.result.message = "Service is stopping"
            }
            return true
        }
        return false
    }

    protected fun <T> onParseException(message: String?): Try<T> {
        execContext.result.incrementParseExceptions()
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
        MyServiceEventsBroadcaster.newInstance(execContext.commandData.myContext, MyServiceState.RUNNING)
            .setCommandData(execContext.commandData)
            .setProgress(progress)
            .setEvent(MyServiceEvent.PROGRESS_EXECUTING_COMMAND).broadcast()
    }

    private fun setParent(parent: CommandExecutorParent?): CommandExecutorStrategy {
        this.parent = parent
        return this
    }

    override fun isStopping(): Boolean {
        return parent?.isStopping() ?: false
    }

    fun logConnectionException(e: ConnectionException, detailedMessage: String?): Try<Boolean> {
        val builder: MyStringBuilder = MyStringBuilder.of(detailedMessage)
            .atNewLine(e.toString())
        return logExecutionError(e.isHardError, builder.toString())
    }

    fun <T> logExecutionError(isHard: Boolean, detailedMessage: String?): Try<T> {
        if (isHard) {
            execContext.result.incrementParseExceptions()
        } else {
            execContext.result.incrementNumIoExceptions()
        }
        val builder: MyStringBuilder = MyStringBuilder.of(detailedMessage)
            .atNewLine(execContext.toExceptionContext)
        execContext.result.message = builder.toString()
        MyLog.w(this, builder.toString())
        return TryUtils.failure(detailedMessage)
    }

    /** @return Success and false means soft error occurred
     */
    open suspend fun execute(): Try<Boolean> {
        MyLog.d(this, "Doing nothing")
        return TryUtils.TRUE
    }

    fun noErrors(): Boolean {
        return !execContext.result.hasError
    }

    fun getActor(): Actor {
        return execContext.commandData.timeline.actor
    }

    open fun isApiSupported(routine: ApiRoutineEnum): Boolean {
        return getConnection().hasApiEndpoint(routine)
    }

    fun getConnection(): Connection {
        return execContext.connection
    }

    override val classTag: String get() = TAG

    open fun onResultIsReady() {}

    companion object {
        private val TAG: String = CommandExecutorStrategy::class.simpleName!!
        protected const val MIN_PROGRESS_BROADCAST_PERIOD_SECONDS: Long = 1

        suspend fun executeCommand(commandData: CommandData, parent: CommandExecutorParent?) {
            val strategy = getStrategy(
                CommandExecutionContext(commandData.myContext, commandData)
            ).setParent(parent)
            commandData.result.prepareForLaunch()
            logLaunch(strategy)
            // This may cause recursive calls to executors...
            strategy.execute()
                .onSuccess { ok: Boolean ->
                    strategy.execContext.result.setSoftErrorIfNotOk(ok)
                    MyLog.d(
                        strategy, strategy.execContext.commandSummary +
                            if (ok) " succeeded" else " soft errors"
                    )
                }
                .onFailureAsConnectionException { ce: ConnectionException ->
                    strategy.logConnectionException(ce, strategy.execContext.commandSummary)
                }
                .also {
                    it.toHttpReadResult()?.let { result ->
                        with(commandData.result) {
                            delayedTill = result.delayedTill
                            delayedTill?.let { millis ->
                                if (message.isEmpty()) {
                                    message = "Delayed till ${Date(millis)}"
                                }
                            }
                        }
                    }
                }
            commandData.result.afterExecutionEnded()
            strategy.onResultIsReady()
            logEnd(strategy)
        }

        private fun logLaunch(strategy: CommandExecutorStrategy) {
            strategy.stopWatch.restart()
            MyLog.d(strategy, "Launching " + strategy.execContext)
        }

        private fun logEnd(strategy: CommandExecutorStrategy) {
            MyLog.d(strategy) { "commandExecutedMs:" + strategy.stopWatch.getTime() + "; " + strategy.execContext }
        }

        fun getStrategy(commandData: CommandData, parent: CommandExecutorParent?): CommandExecutorStrategy {
            return getStrategy(
                CommandExecutionContext(commandData.myContext, commandData)
            ).setParent(parent)
        }

        private fun getStrategy(execContext: CommandExecutionContext): CommandExecutorStrategy =
            when (execContext.commandData.command) {
                CommandEnum.GET_ATTACHMENT,
                CommandEnum.GET_AVATAR -> CommandExecutorOther(execContext)
                CommandEnum.GET_OPEN_INSTANCES -> CommandExecutorGetOpenInstances(execContext)
                else -> if (execContext.myAccount.isValidAndSucceeded()) {
                    when (execContext.commandData.command) {
                        CommandEnum.GET_TIMELINE,
                        CommandEnum.GET_OLDER_TIMELINE ->
                            if (execContext.commandData.timeline.isSyncable) {
                                when (execContext.commandData.getTimelineType()) {
                                    TimelineType.FOLLOWERS, TimelineType.FRIENDS -> TimelineDownloaderFollowers(
                                        execContext
                                    )
                                    else -> TimelineDownloaderOther(execContext)
                                }
                            } else {
                                MyLog.v(CommandExecutorStrategy::class.java) {
                                    "Dummy commandExecutor for " +
                                        execContext.commandData.timeline
                                }
                                CommandExecutorStrategy(execContext)
                            }
                        CommandEnum.GET_FOLLOWERS,
                        CommandEnum.GET_FRIENDS -> CommandExecutorFollowers(execContext)
                        else -> CommandExecutorOther(execContext)
                    }
                } else {
                    MyLog.v(CommandExecutorStrategy::class.java) {
                        ("Dummy commandExecutor for " + execContext.myAccount)
                    }
                    CommandExecutorStrategy(execContext)
                }
            }
    }
}
