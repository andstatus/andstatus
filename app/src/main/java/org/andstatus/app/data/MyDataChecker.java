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

package org.andstatus.app.data;

import org.andstatus.app.backup.ProgressLogger;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.os.AsyncTaskLauncher;
import org.andstatus.app.os.MyAsyncTask;

/**
 * @author yvolk@yurivolkov.com
 */
public class MyDataChecker {
    private final MyContext myContext;
    private final ProgressLogger logger;

    public MyDataChecker(MyContext myContext, ProgressLogger logger) {
        this.myContext = myContext;
        this.logger = logger;
    }

    public static void fixDataAsync(ProgressLogger.ProgressCallback progressCallback) {
        final ProgressLogger logger = new ProgressLogger(progressCallback);
        AsyncTaskLauncher.execute(
                progressCallback,
                false,
                new MyAsyncTask<Void, Void, Void>(MyDataChecker.class.getSimpleName(),
                MyAsyncTask.PoolEnum.LONG_UI) {

                    @Override
                    protected Void doInBackground2(Void... params) {
                        new MyDataChecker(MyContextHolder.get(), logger).fixData();
                        DbUtils.waitMs(MyDataChecker.class.getSimpleName(), 3000);
                        return null;
                    }

                    @Override
                    protected void onCancelled() {
                        logger.logFailure();
                        super.onCancelled();
                    }

                    @Override
                    protected void onPostExecute(Void aVoid) {
                        logger.logSuccess();
                        super.onPostExecute(aVoid);
                    }
                });
    }

    public void fixData() {
        new MyDataCheckerMergeUsers(myContext, logger).fixData();
        new MyDataCheckerConversations(myContext, logger).fixData();
    }
}
