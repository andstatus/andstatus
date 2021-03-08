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
import org.andstatus.app.service.CommandEnum
import org.andstatus.app.timeline.meta.Timeline
import org.andstatus.app.util.MyLog
import java.util.function.Consumer

/**
 * Downloads ("loads") different types of Timelines
 * (i.e. Tweets and Messages) from the Internet (e.g. from twitter.com server).
 * Then Store them into local database using [DataUpdater]
 *
 * @author yvolk@yurivolkov.com
 */
internal abstract class TimelineDownloader(execContext: CommandExecutionContext?) : CommandExecutorStrategy(execContext) {
    public override fun execute(): Try<Boolean> {
        if (!isApiSupported(execContext.timeline.timelineType.connectionApiRoutine)) {
            MyLog.v(this) {
                (execContext.timeline.toString() + " is not supported for "
                        + execContext.myAccount.accountName)
            }
            return Try.success(true)
        }
        MyLog.d(this, "Getting " + execContext.commandData.toCommandSummary(execContext.getMyContext()) +
                " by " + execContext.myAccount.accountName)
        return download()
                .onSuccess(Consumer { b: Boolean? -> onSyncEnded() })
                .onFailure { e: Throwable? -> onSyncEnded() }
    }

    abstract fun download(): Try<Boolean>
    protected fun getTimeline(): Timeline? {
        return execContext.timeline
    }

    fun onSyncEnded() {
        getTimeline().onSyncEnded(execContext.getMyContext(), execContext.commandData.result)
        if (execContext.result.downloadedCount > 0) {
            if (!execContext.result.hasError() && !isStopping) {
                DataPruner(execContext.getMyContext()).prune()
            }
            MyLog.v(this, "Notifying of timeline changes")
            execContext.getMyContext().notifier.update()
        }
    }

    protected fun isSyncYounger(): Boolean {
        return execContext.commandData.command != CommandEnum.GET_OLDER_TIMELINE
    }
}