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

package org.andstatus.app.data.checker;

import org.andstatus.app.backup.ProgressLogger;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.os.AsyncTaskLauncher;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StopWatch;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.andstatus.app.context.MyContextHolder.myContextHolder;

/**
 * @author yvolk@yurivolkov.com
 */
public abstract class DataChecker {
    private static final String TAG = DataChecker.class.getSimpleName();

    MyContext myContext;
    ProgressLogger logger = ProgressLogger.getEmpty("DataChecker");
    boolean includeLong = false;
    boolean countOnly = false;

    static String getSomeOfTotal(long some, long total) {
        return (some == 0
                ? "none"
                : some == total
                    ? "all"
                    : String.valueOf(some))
                + " of " + total;
    }

    public DataChecker setMyContext(MyContext myContext) {
        this.myContext = myContext;
        return this;
    }

    public DataChecker setLogger(ProgressLogger logger) {
        this.logger = logger.makeServiceUnavalable();
        return this;
    }

    public static void fixDataAsync(ProgressLogger logger, boolean includeLong, boolean countOnly) {
        AsyncTaskLauncher.execute(
                logger.logTag,
                new MyAsyncTask<Void, Void, Void>(logger.logTag, MyAsyncTask.PoolEnum.thatCannotBeShutDown()) {

                    @Override
                    protected Void doInBackground2(Void aVoid) {
                        fixData(logger, includeLong, countOnly);
                        DbUtils.waitMs(TAG, 3000);
                        myContextHolder.release(() -> "fixDataAsync");
                        myContextHolder.initialize(null, TAG).getBlocking();
                        return null;
                    }

                    @Override
                    protected void onCancelled() {
                        logger.logFailure();
                    }

                    @Override
                    protected void onPostExecute2(Void aVoid) {
                        logger.logSuccess();
                    }
                });
    }

    public static long fixData(final ProgressLogger logger, final boolean includeLong, boolean countOnly) {
        long counter = 0;
        MyContext myContext = myContextHolder.getNow();
        if (!myContext.isReady()) {
            MyLog.w(TAG, "fixData skipped: context is not ready " + myContext);
            return counter;
        }
        StopWatch stopWatch = StopWatch.createStarted();
        try {
            MyLog.i(TAG, "fixData started" + (includeLong ? ", including long tasks" : ""));
            List<DataChecker> allCheckers = Arrays.asList(
                    new CheckTimelines(),
                    new CheckDownloads(),
                    new MergeActors(),
                    new CheckUsers(),
                    new CheckConversations(),
                    new CheckAudience(),
                    new SearchIndexUpdate());

            // TODO: define scope in parameters
            String scope = "All";
            List<DataChecker> selectedCheckers = allCheckers.stream()
                    .filter(c -> scope.contains("All") || scope.contains(c.getClass().getSimpleName()))
                    .collect(Collectors.toList());

            for(DataChecker checker : selectedCheckers) {
                if (logger.isCancelled()) break;

                MyServiceManager.setServiceUnavailable();
                counter += checker.setMyContext(myContext).setIncludeLong(includeLong).setLogger(logger)
                        .setCountOnly(countOnly)
                        .fix();
            }
        } finally {
            MyServiceManager.setServiceAvailable();
        }
        MyLog.i(TAG, "fixData ended in " + stopWatch.getTime(TimeUnit.MINUTES) + " min, counted: " + counter);
        return counter;
    }

    private DataChecker setIncludeLong(boolean includeLong) {
        this.includeLong = includeLong;
        return this;
    }

    public DataChecker setCountOnly(boolean countOnly) {
        this.countOnly = countOnly;
        return this;
    }

    private String checkerName() {
        return this.getClass().getSimpleName();
    }

    /**
     * @return number of changed items (or needed to change)
     */
    public long fix() {
        StopWatch stopWatch = StopWatch.createStarted();
        logger.logProgress(checkerName() + " checker started");
        long changedCount = fixInternal();
        logger.logProgress(checkerName() + " checker ended in " + stopWatch.getTime(TimeUnit.SECONDS) + " sec, " +
            (changedCount > 0
                ? (countOnly ? "need to change " : "changed ") + changedCount + " items"
                : " no changes were needed"));
        DbUtils.waitMs(checkerName(), changedCount == 0 ? 1000 : 3000);
        return changedCount;
    }

    abstract long fixInternal();
}
