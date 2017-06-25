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

package org.andstatus.app.net.social;

import android.net.Uri;
import android.support.annotation.NonNull;

import org.andstatus.app.net.http.ConnectionException;

import java.util.ArrayList;
import java.util.List;

public class ConnectionEmpty extends Connection {

    @Override
    protected String getApiPath1(ApiRoutineEnum routine) {
        return "";
    }

    @Override
    public MbRateLimitStatus rateLimitStatus() throws ConnectionException {
        return new MbRateLimitStatus();
    }

    @Override
    public MbUser verifyCredentials() throws ConnectionException {
        return MbUser.EMPTY;
    }

    @Override
    public MbActivity destroyFavorite(String statusId) throws ConnectionException {
        return MbActivity.EMPTY;
    }

    @Override
    public MbActivity createFavorite(String statusId) throws ConnectionException {
        return MbActivity.EMPTY;
    }

    @Override
    public boolean destroyStatus(String statusId) throws ConnectionException {
        return false;
    }

    @Override
    public MbActivity getMessage1(String statusId) throws ConnectionException {
        return MbActivity.EMPTY;
    }

    @Override
    public MbActivity updateStatus(String message, String statusId, String inReplyToId, Uri mediaUri) throws ConnectionException {
        return MbActivity.EMPTY;
    }

    @Override
    public MbActivity postDirectMessage(String message, String statusId, String userId, Uri mediaUri) throws ConnectionException {
        return MbActivity.EMPTY;
    }

    @Override
    public MbActivity postReblog(String rebloggedId) throws ConnectionException {
        return MbActivity.EMPTY;
    }

    @NonNull
    @Override
    public List<MbActivity> getTimeline(ApiRoutineEnum apiRoutine, TimelinePosition youngestPosition,
                                        TimelinePosition oldestPosition, int limit, String userId)
            throws ConnectionException {
        return new ArrayList<>();
    }

    @Override
    public MbActivity followUser(String userId, Boolean follow) throws ConnectionException {
        return MbActivity.EMPTY;
    }

    @Override
    public MbUser getUser(String userId, String userName) throws ConnectionException {
        return MbUser.EMPTY;
    }

}
