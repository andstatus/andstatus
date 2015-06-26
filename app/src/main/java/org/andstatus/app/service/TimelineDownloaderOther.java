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
import org.andstatus.app.data.MyDatabase.OidEnum;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.social.MbTimelineItem;
import org.andstatus.app.net.social.TimelinePosition;
import org.andstatus.app.net.http.ConnectionException.StatusCode;
import org.andstatus.app.util.MyLog;

import java.util.Date;
import java.util.List;

class TimelineDownloaderOther extends TimelineDownloader {
    private static final int MAXIMUM_NUMBER_OF_MESSAGES_TO_DOWNLOAD = 200;

    @Override
    public void download() throws ConnectionException {
        LatestTimelineItem latestTimelineItem = new LatestTimelineItem(execContext.getTimelineType(), execContext.getTimelineUserId());
        
        if (MyLog.isLoggable(this, MyLog.DEBUG)) {
            String strLog = "Loading " + execContext.getTimelineType() + "; account=" 
        + execContext.getMyAccount().getAccountName()
        + "; user=" + MyQuery.userIdToWebfingerId(execContext.getTimelineUserId());
            if (latestTimelineItem.getTimelineItemDate() > 0) {
                strLog += "; last Timeline item at=" + (new Date(latestTimelineItem.getTimelineItemDate()).toString())
                        + "; last time downloaded at=" +  (new Date(latestTimelineItem.getTimelineDownloadedDate()).toString());
            }
            MyLog.d(this, strLog);
        }
        String userOid =  MyQuery.idToOid(OidEnum.USER_OID, execContext.getTimelineUserId(), 0);
        if (TextUtils.isEmpty(userOid)) {
            throw new ConnectionException("User oId is not found for id=" + execContext.getTimelineUserId());
        }
        int toDownload = MAXIMUM_NUMBER_OF_MESSAGES_TO_DOWNLOAD;
        TimelinePosition lastPosition = latestTimelineItem.getPosition();
        LatestUserMessages latestUserMessages = new LatestUserMessages();
        latestTimelineItem.onTimelineDownloaded();
        DataInserter di = new DataInserter(execContext);
        for (int loopCounter=0; loopCounter < 100; loopCounter++ ) {
            try {
                int limit = execContext.getMyAccount().getConnection().fixedDownloadLimitForApiRoutine(toDownload, 
                        execContext.getTimelineType().getConnectionApiRoutine()); 
                List<MbTimelineItem> messages = execContext.getMyAccount().getConnection().getTimeline(
                        execContext.getTimelineType().getConnectionApiRoutine(), lastPosition, limit, userOid);
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
                    break;
                } else {
                    lastPosition = latestTimelineItem.getPosition();
                }
            } catch (ConnectionException e) {
                if (e.getStatusCode() != StatusCode.NOT_FOUND) {
                    throw e;
                }
                if (lastPosition.isEmpty()) {
                    throw ConnectionException.hardConnectionException("No last position", e);
                }
                MyLog.d(this, "The timeline was not found, last position='" + lastPosition +"'", e);
                lastPosition = TimelinePosition.getEmpty();
            }
        }
        latestUserMessages.save();
        latestTimelineItem.save();
    }

}
