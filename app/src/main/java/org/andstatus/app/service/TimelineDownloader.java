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

package org.andstatus.app.service;

import android.database.sqlite.SQLiteConstraintException;

import org.andstatus.app.data.DataInserter;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.timeline.Timeline;
import org.andstatus.app.util.MyLog;

/**
 * Downloads ("loads") different types of Timelines 
 *  (i.e. Tweets and Messages) from the Internet (e.g. from twitter.com server).
 * Then Store them into local database using {@link DataInserter}
 * 
 * @author yvolk@yurivolkov.com
 */
abstract class TimelineDownloader extends CommandExecutorStrategy {

    @Override
    void execute() {
        try {
            if (execContext.getMyAccount().getConnection().isApiSupported(execContext.getTimeline().getTimelineType().getConnectionApiRoutine())) {
                MyLog.d(this, "Getting " + execContext.getCommandData().toCommandSummary(execContext.getMyContext()) +
                        " by " + execContext.getMyAccount().getAccountName() );
                download();
                onSyncEnded();
            } else {
                MyLog.v(this, execContext.getTimeline() + " is not supported for "
                        + execContext.getMyAccount().getAccountName());
            }
            logOk(true);
        } catch (ConnectionException e) {
            logConnectionException(e, "Load Timeline");
        } catch (SQLiteConstraintException e) {
            MyLog.e(this, execContext.getTimeline().toString(), e);
        }
    }

    public abstract void download() throws ConnectionException;

    protected Timeline getTimeline() {
        return execContext.getTimeline();
    }

    public void onSyncEnded() {
        getTimeline().onSyncEnded(execContext.getCommandData().getResult());
        getTimeline().save(execContext.getMyContext());
    }
}
