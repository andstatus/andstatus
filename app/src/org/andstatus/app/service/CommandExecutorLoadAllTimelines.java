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

import org.andstatus.app.appwidget.AppWidgets;
import org.andstatus.app.data.DataPruner;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.TimelineTypeEnum;
import org.andstatus.app.notification.AddedMessagesNotifier;
import org.andstatus.app.util.MyLog;

class CommandExecutorLoadAllTimelines extends CommandExecutorStrategy {
    
    @Override
    void execute() {
        for (TimelineTypeEnum timelineType : getTimelines()) {
            if (isStopping()) {
                break;
            }
            execContext.setTimelineType(timelineType);
            CommandExecutorStrategy.executeStep(execContext, this);
        }
        if (!execContext.getResult().hasError() && !isStopping()) {
            new DataPruner(execContext.getMyContext()).prune();
        }
        if (!execContext.getResult().hasError() || execContext.getResult().getDownloadedCount() > 0) {
            MyLog.v(this, "Notifying of timeline changes");

            notifyViaWidgets();
            
            AddedMessagesNotifier.newInstance(execContext.getMyContext()).update(
                    execContext.getResult());
            
            notifyViaContentResolver();
        }
    }

    private TimelineTypeEnum[] getTimelines() {
        TimelineTypeEnum[] timelineTypes;
        if (execContext.getCommandData().getTimelineType() == TimelineTypeEnum.ALL
                || execContext.getCommandData().getTimelineType() == TimelineTypeEnum.EVERYTHING) {
            timelineTypes = new TimelineTypeEnum[] {
                    TimelineTypeEnum.HOME, TimelineTypeEnum.MENTIONS,
                    TimelineTypeEnum.DIRECT
            };
        } else {
            timelineTypes = new TimelineTypeEnum[] {
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

    private void notifyViaContentResolver() {
        // see http://stackoverflow.com/questions/6678046/when-contentresolver-notifychange-is-called-for-a-given-uri-are-contentobserv
        execContext.getContext().getContentResolver().notifyChange(MyProvider.TIMELINE_URI, null);
    }
}
