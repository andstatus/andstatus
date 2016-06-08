/* 
 * Copyright (c) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
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

import org.andstatus.app.appwidget.AppWidgets;
import org.andstatus.app.data.DataPruner;
import org.andstatus.app.timeline.TimelineType;
import org.andstatus.app.notification.AddedMessagesNotifier;
import org.andstatus.app.util.MyLog;

class CommandExecutorLoadAllTimelines extends CommandExecutorStrategy {
    
    @Override
    void execute() {
        for (TimelineType timelineType : getTimelines()) {
            if (logSoftErrorIfStopping()) {
                break;
            }
            execContext.setTimelineType(timelineType);
            CommandExecutorStrategy.executeStep(execContext, this);
        }
        if (!execContext.getResult().hasError() && !isStopping()) {
            new DataPruner(execContext.getMyContext()).prune();
        }
        if (execContext.getResult().getDownloadedCount() > 0) {
            MyLog.v(this, "Notifying of timeline changes");

            notifyViaWidgets();
            
            AddedMessagesNotifier.newInstance(execContext.getMyContext()).update(
                    execContext.getResult());
        }
    }

    private TimelineType[] getTimelines() {
        TimelineType[] timelineTypes;
        if (execContext.getCommandData().getTimelineType() == TimelineType.ALL
                || execContext.getCommandData().getTimelineType() == TimelineType.EVERYTHING) {
            timelineTypes = new TimelineType[] {
                    TimelineType.HOME, TimelineType.MENTIONS,
                    TimelineType.DIRECT
            };
        } else {
            timelineTypes = new TimelineType[] {
                    execContext.getCommandData().getTimelineType()
            };
        }
        return timelineTypes;
    }

    private void notifyViaWidgets() {
        AppWidgets appWidgets = AppWidgets.newInstance(execContext.getMyContext());
        appWidgets.updateData(execContext.getResult());
        appWidgets.updateViews();
    }
}
