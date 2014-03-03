/*
 * Copyright (C) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.text.TextUtils;

import org.andstatus.app.data.DataInserter;
import org.andstatus.app.data.LatestTimelineItem;
import org.andstatus.app.data.LatestUserMessages;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.MyDatabase.OidEnum;
import org.andstatus.app.net.ConnectionException;
import org.andstatus.app.net.MbTimelineItem;
import org.andstatus.app.net.TimelinePosition;
import org.andstatus.app.net.ConnectionException.StatusCode;
import org.andstatus.app.util.MyLog;

import java.util.Date;
import java.util.List;

public class TimelineDownloaderMsg extends TimelineDownloader {
    private static final String TAG = TimelineDownloaderMsg.class.getSimpleName();
    private static final int MAXIMUM_NUMBER_OF_MESSAGES_TO_DOWNLOAD = 200;

    @Override
    public void download() throws ConnectionException {
        LatestTimelineItem latestTimelineItem = new LatestTimelineItem(counters.timelineType, userId);
        
        if (MyLog.isLoggable(TAG, MyLog.DEBUG)) {
            String strLog = "Loading timeline " + counters.timelineType.save() + "; account=" 
        + counters.ma.getAccountName()
        + "; user=" + MyProvider.userIdToName(userId);
            if (latestTimelineItem.getTimelineItemDate() > 0) {
                strLog += "; last Timeline item at=" + (new Date(latestTimelineItem.getTimelineItemDate()).toString())
                        + "; last time downloaded at=" +  (new Date(latestTimelineItem.getTimelineDownloadedDate()).toString());
            }
            MyLog.d(TAG, strLog);
        }
        String userOid =  MyProvider.idToOid(OidEnum.USER_OID, userId, 0);
        if (TextUtils.isEmpty(userOid)) {
            throw new ConnectionException("User oId is not found for id=" + userId);
        }
        int toDownload = MAXIMUM_NUMBER_OF_MESSAGES_TO_DOWNLOAD;
        TimelinePosition lastPosition = latestTimelineItem.getPosition();
        LatestUserMessages latestUserMessages = new LatestUserMessages();
        latestTimelineItem.onTimelineDownloaded();
        DataInserter di = new DataInserter(counters);
        for (boolean done = false; !done; ) {
            try {
                int limit = counters.ma.getConnection().fixedDownloadLimitForApiRoutine(toDownload, 
                        counters.timelineType.getConnectionApiRoutine()); 
                List<MbTimelineItem> messages = counters.ma.getConnection().getTimeline(
                        counters.timelineType.getConnectionApiRoutine(), lastPosition, limit, userOid);
                for (MbTimelineItem item : messages) {
                    toDownload--;
                    latestTimelineItem.onNewMsg(item.timelineItemPosition, item.timelineItemDate);
                    switch (item.getType()) {
                        case MESSAGE:
                            di.insertOrUpdateMsg(item.mbMessage, latestUserMessages);
                            break;
                        case USER:
                            di.insertOrUpdateUser(item.mbUser);
                            break;
                        default:
                            break;
                    }
                }
                if (toDownload <= 0
                        || lastPosition == latestTimelineItem.getPosition()) {
                    done = true;
                } else {
                    lastPosition = latestTimelineItem.getPosition();
                }
            } catch (ConnectionException e) {
                if (e.getStatusCode() != StatusCode.NOT_FOUND) {
                    throw e;
                }
                if (lastPosition.isEmpty()) {
                    e.setHardError(true);
                    throw e;
                }
                MyLog.d(TAG, "The timeline was not found, last position='" + lastPosition +"'", e);
                lastPosition = TimelinePosition.getEmpty();
            }
        }
        latestUserMessages.save();
        latestTimelineItem.save();
    }

}
