/*
 * Copyright (c) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.data.checker

import org.andstatus.app.backup.ProgressLogger
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.data.DbUtils
import org.andstatus.app.os.AsyncTaskLauncher
import org.andstatus.app.os.MyAsyncTask
import org.andstatus.app.service.MyServiceManager
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.StopWatch
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors
import kotlin.properties.Delegates

/**
 * @author yvolk@yurivolkov.com
 */
abstract class DataChecker {
    var myContext: MyContext by Delegates.notNull()
    var logger: ProgressLogger = ProgressLogger.getEmpty("DataChecker")
    var includeLong = false
    var countOnly = false

    fun setMyContext(myContext: MyContext): DataChecker {
        this.myContext = myContext
        return this
    }

    fun setLogger(logger: ProgressLogger): DataChecker {
        this.logger = logger.makeServiceUnavalable()
        return this
    }

    private fun setIncludeLong(includeLong: Boolean): DataChecker {
        this.includeLong = includeLong
        return this
    }

    fun setCountOnly(countOnly: Boolean): DataChecker {
        this.countOnly = countOnly
        return this
    }

    private fun checkerName(): String {
        return this.javaClass.simpleName
    }

    /**
     * @return number of changed items (or needed to change)
     */
    fun fix(): Long {
        val stopWatch: StopWatch = StopWatch.createStarted()
        logger.logProgress(checkerName() + " checker started")
        val changedCount = fixInternal()
        logger.logProgress(checkerName() + " checker ended in " + stopWatch.getTime(TimeUnit.SECONDS) + " sec, " +
                if (changedCount > 0) (if (countOnly) "need to change " else "changed ") + changedCount + " items" else " no changes were needed")
        pauseToShowCount(checkerName(), changedCount)
        return changedCount
    }

    abstract fun fixInternal(): Long

    protected fun pauseToShowCount(tag: Any?, count: Number) {
        DbUtils.waitMs(
            tag,
            if (count == 0) {
                if (myContext.isTestRun) 500 else 1000
            } else {
                if (myContext.isTestRun) 1000 else 3000
            }
        )
    }

    companion object {
        private val TAG: String = DataChecker::class.java.simpleName
        fun getSomeOfTotal(some: Long, total: Long): String {
            return ((if (some == 0L) "none" else if (some == total) "all" else some.toString())
                    + " of " + total)
        }

        fun fixDataAsync(logger: ProgressLogger, includeLong: Boolean, countOnly: Boolean) {
            AsyncTaskLauncher.execute(
                    logger.logTag,
                    object : MyAsyncTask<Void?, Void?, Void?>(logger.logTag, PoolEnum.thatCannotBeShutDown()) {
                        override fun doInBackground2(aVoid: Void?): Void? {
                            fixData(logger, includeLong, countOnly)
                            DbUtils.waitMs(TAG, 3000)
                            MyContextHolder.myContextHolder.release { "fixDataAsync" }
                            MyContextHolder.myContextHolder.initialize(null, TAG).getBlocking()
                            return null
                        }

                        override fun onCancelled() {
                            logger.logFailure()
                        }

                        override fun onPostExecute2(aVoid: Void?) {
                            logger.logSuccess()
                        }
                    })
        }

        fun fixData(logger: ProgressLogger, includeLong: Boolean, countOnly: Boolean): Long {
            var counter: Long = 0
            val myContext: MyContext = MyContextHolder.myContextHolder.getNow()
            if (!myContext.isReady) {
                MyLog.w(TAG, "fixData skipped: context is not ready $myContext")
                return counter
            }
            val stopWatch: StopWatch = StopWatch.createStarted()
            try {
                MyLog.i(TAG, "fixData started" + if (includeLong) ", including long tasks" else "")
                val allCheckers = Arrays.asList(
                        CheckTimelines(),
                        CheckDownloads(),
                        MergeActors(),
                        CheckUsers(),
                        CheckConversations(),
                        CheckAudience(),
                        SearchIndexUpdate())

                // TODO: define scope in parameters
                val scope = "All"
                val selectedCheckers = allCheckers.stream()
                        .filter { c: DataChecker -> scope.contains("All") || scope.contains(c.javaClass.simpleName) }
                        .collect(Collectors.toList())
                for (checker in selectedCheckers) {
                    if (logger.isCancelled) break
                    MyServiceManager.setServiceUnavailable()
                    counter += checker.setMyContext(myContext).setIncludeLong(includeLong).setLogger(logger)
                            .setCountOnly(countOnly)
                            .fix()
                }
            } finally {
                MyServiceManager.setServiceAvailable()
            }
            MyLog.i(TAG, "fixData ended in " + stopWatch.getTime(TimeUnit.MINUTES) + " min, counted: " + counter)
            return counter
        }
    }
}
