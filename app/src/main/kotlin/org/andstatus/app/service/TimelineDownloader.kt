/* 
 * Copyright (c) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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
import org.andstatus.app.data.DataPruner
import org.andstatus.app.data.DataUpdater
import org.andstatus.app.net.http.ConnectionException
import org.andstatus.app.net.http.StatusCode
import org.andstatus.app.timeline.meta.Timeline
import org.andstatus.app.util.MyLog

/**
 * Downloads ("loads") different types of Timelines
 * (i.e. Tweets and Messages) from the Internet (e.g. from twitter.com server).
 * Then Store them into local database using [DataUpdater]
 *
 * @author yvolk@yurivolkov.com
 */
internal abstract class TimelineDownloader(execContext: CommandExecutionContext) :
    CommandExecutorStrategy(execContext) {

    override suspend fun execute(): Try<Boolean> {
        if (!isApiSupported(execContext.timeline.timelineType.connectionApiRoutine)) {
            return Try.failure(
                ConnectionException.fromStatusCode(
                    StatusCode.UNSUPPORTED_API,
                    execContext.timeline.timelineType.title(execContext.context).toString() +
                        " is not supported for " + execContext.myAccount.accountName
                )
            )
        }
        MyLog.d(
            this, "Getting " + execContext.commandData.toCommandSummary(execContext.myContext) +
                " by " + execContext.myAccount.accountName
        )
        return download()
    }

    abstract suspend fun download(): Try<Boolean>
    protected val timeline: Timeline get() = execContext.timeline

    override fun onResultIsReady() {
        execContext.result.takeIf { it.executed }?.let { result ->
            timeline.onSyncEnded(execContext.myContext, result)
            if (result.downloadedCount > 0) {
                if (!result.hasError && !isStopping()) {
                    DataPruner(execContext.myContext).prune()
                }
                MyLog.v(this, "Notifying of timeline changes")
                execContext.myContext.notifier.update()
            }
        }
    }

    protected fun isSyncYounger(): Boolean {
        return execContext.commandData.command != CommandEnum.GET_OLDER_TIMELINE
    }
}
