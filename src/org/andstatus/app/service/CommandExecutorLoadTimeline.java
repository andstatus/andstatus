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

package org.andstatus.app.service;

import android.database.sqlite.SQLiteConstraintException;

import org.andstatus.app.appwidget.AppWidgets;
import org.andstatus.app.data.DataPruner;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.TimelineTypeEnum;
import org.andstatus.app.net.ConnectionException;
import org.andstatus.app.util.MyLog;

class CommandExecutorLoadTimeline extends CommandExecutorStrategy {
    
    /* (non-Javadoc)
     * @see org.andstatus.app.service.OneCommandExecutor#execute()
     */
    @Override
    void execute() {
        loadTimelines();
        if (!execContext.getResult().hasError() && execContext.getCommandData().getTimelineType() == TimelineTypeEnum.ALL && !isStopping()) {
            new DataPruner(execContext.getContext()).prune();
        }
        if (!execContext.getResult().hasError() || execContext.getResult().getDownloadedCount() > 0) {
            MyLog.v(this, "Notifying of timeline changes");

            notifyViaWidgets();
            
            AddedMessagesNotifier.newInstance(execContext.getMyContext()).update(
                    execContext.getResult());
            
            notifyViaContentResolver();
        }
    }

    /**
     * Load Timeline(s) for one MyAccount
     * @return True if everything Succeeded
     */
    private void loadTimelines() {
        for (TimelineTypeEnum timelineType : getTimelines()) {
            if (isStopping()) {
                break;
            }
            execContext.setTimelineType(timelineType);
            loadTimeline();
        }
    }

    private TimelineTypeEnum[] getTimelines() {
        TimelineTypeEnum[] timelineTypes;
        if (execContext.getCommandData().getTimelineType() == TimelineTypeEnum.ALL) {
            timelineTypes = new TimelineTypeEnum[] {
                    TimelineTypeEnum.HOME, TimelineTypeEnum.MENTIONS,
                    TimelineTypeEnum.DIRECT,
                    TimelineTypeEnum.FOLLOWING_USER
            };
        } else {
            timelineTypes = new TimelineTypeEnum[] {
                    execContext.getCommandData().getTimelineType()
            };
        }
        return timelineTypes;
    }

    private void loadTimeline() {
        boolean ok = false;
        try {
            if (execContext.getMyAccount().getConnection().isApiSupported(execContext.getTimelineType().getConnectionApiRoutine())) {
                long userId = execContext.getCommandData().itemId;
                if (userId == 0) {
                    userId = execContext.getMyAccount().getUserId();
                }
                execContext.setTimelineUserId(userId);
                MyLog.d(this, "Getting " + execContext.getTimelineType() + " timeline for " + execContext.getMyAccount().getAccountName() );
                TimelineDownloader.getStrategy(execContext).download();
            } else {
                MyLog.v(this, execContext.getTimelineType() + " is not supported for "
                        + execContext.getMyAccount().getAccountName());
            }
            ok = true;
            logOk(ok);
        } catch (ConnectionException e) {
            logConnectionException(e, execContext.getTimelineType().toString());
        } catch (SQLiteConstraintException e) {
            MyLog.e(this, execContext.getTimelineType().toString(), e);
        }
    }

    private void notifyViaWidgets() {
        AppWidgets appWidgets = AppWidgets.newInstance(execContext.getMyContext());
        appWidgets.updateData(execContext.getResult());
        appWidgets.updateViews();
    }

    private void notifyViaContentResolver() {
        // see http://stackoverflow.com/questions/6678046/when-contentresolver-notifychange-is-called-for-a-given-uri-are-contentobserv
        execContext.getContext().getContentResolver().notifyChange(MyProvider.TIMELINE_URI, null);
    }
}
