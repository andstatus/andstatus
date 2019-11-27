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
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.os.AsyncTaskLauncher;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.MyLog;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author yvolk@yurivolkov.com
 */
public abstract class DataChecker {
    static final int PROGRESS_REPORT_PERIOD_SECONDS = 20;
    MyContext myContext;
    ProgressLogger logger = new ProgressLogger(null);
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
                logger.callback,
                false,
                new MyAsyncTask<Void, Void, Void>(DataChecker.class.getSimpleName(),
                MyAsyncTask.PoolEnum.LONG_UI) {

                    @Override
                    protected Void doInBackground2(Void aVoid) {
                        fixData(logger, includeLong, countOnly);
                        DbUtils.waitMs(DataChecker.class, 3000);
                        MyContextHolder.release(() -> "fixDataAsync");
                        MyContextHolder.initialize(MyContextHolder.get().context(), DataChecker.class);
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
        MyContext myContext = MyContextHolder.get();
        if (!myContext.isReady()) {
            MyLog.w(DataChecker.class, "fixData skipped: context is not ready " + myContext);
            return counter;
        }
        try {
            MyLog.i(DataChecker.class, "fixData started" + (includeLong ? ", including long tasks" : ""));
            List<DataChecker> allCheckers = Arrays.asList(
                    new CheckTimelines(),
                    new CheckDownloads(),
                    new MergeActors(),
                    new CheckUsers(),
                    new CheckConversations(),
                    new CheckAudience(),
                    new SearchIndexUpdate());

            // TODO: define scope in parameters
            String scope = "CheckUsers";
            List<DataChecker> selectedCheckers = allCheckers.stream()
                    .filter(c -> scope.contains("All") || scope.contains(c.getClass().getSimpleName()))
                    .collect(Collectors.toList());

            for(DataChecker checker : selectedCheckers) {
                MyServiceManager.setServiceUnavailable();
                counter += checker.setMyContext(myContext).setIncludeLong(includeLong).setLogger(logger)
                        .setCountOnly(countOnly)
                        .fix();
            }
        } finally {
            MyServiceManager.setServiceAvailable();
        }
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
        logger.logProgress(checkerName() + " checker started");
        long changedCount = fixInternal();
        logger.logProgress(checkerName() + " checker ended, " + (changedCount > 0
                ? (countOnly ? "need to change " : "changed ") + changedCount + " items"
                : " no changes were needed"));
        DbUtils.waitMs(checkerName(), changedCount == 0 ? 1000 : 3000);
        return changedCount;
    }

    abstract long fixInternal();
}
