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

package org.andstatus.app;

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
        
        int toDownload = 200;
        TimelinePosition lastPosition = latestTimelineItem.getPosition();
        LatestUserMessages latestUserMessages = new LatestUserMessages();
        latestTimelineItem.onTimelineDownloaded();
        for (boolean done = false; !done || toDownload > 0; ) {
            try {
                int limit = counters.ma.getConnection().fixedDownloadLimitForApiRoutine(toDownload, 
                        counters.timelineType.getConnectionApiRoutine()); 
                List<MbTimelineItem> messages = counters.ma.getConnection().getTimeline(
                        counters.timelineType.getConnectionApiRoutine(), lastPosition, limit, userOid);
                if (messages.size() < 2) {  // We may assume that we downloaded the same message...
                    toDownload = 0;
                }
                if (!messages.isEmpty()) {
                    toDownload -= messages.size();
                    DataInserter di = new DataInserter(counters);
                    for (MbTimelineItem item : messages) {
                        latestTimelineItem.onNewMsg(item.timelineItemPosition, item.timelineItemDate);
                        switch (item.getType()) {
                            case MESSAGE:
                                di.insertOrUpdateMsg(item.mbMessage, latestUserMessages);
                                break;
                            case USER:
                                di.insertOrUpdateUser(item.mbUser);
                                break;
                            default:
                        }
                    }
                    lastPosition = latestTimelineItem.getPosition();
                }
                done = true;
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
