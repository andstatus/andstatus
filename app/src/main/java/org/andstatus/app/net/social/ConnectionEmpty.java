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
    public RateLimitStatus rateLimitStatus() throws ConnectionException {
        return new RateLimitStatus();
    }

    @Override
    public Actor verifyCredentials() throws ConnectionException {
        return Actor.EMPTY;
    }

    @Override
    public AActivity destroyFavorite(String statusId) throws ConnectionException {
        return AActivity.EMPTY;
    }

    @Override
    public AActivity createFavorite(String statusId) throws ConnectionException {
        return AActivity.EMPTY;
    }

    @Override
    public boolean destroyStatus(String statusId) throws ConnectionException {
        return false;
    }

    @Override
    public AActivity getMessage1(String statusId) throws ConnectionException {
        return AActivity.EMPTY;
    }

    @Override
    public AActivity updateStatus(String message, String statusId, String inReplyToId, Uri mediaUri) throws ConnectionException {
        return AActivity.EMPTY;
    }

    @Override
    public AActivity postPrivateMessage(String message, String statusId, String actorId, Uri mediaUri) throws ConnectionException {
        return AActivity.EMPTY;
    }

    @Override
    public AActivity postReblog(String rebloggedId) throws ConnectionException {
        return AActivity.EMPTY;
    }

    @NonNull
    @Override
    public List<AActivity> getTimeline(ApiRoutineEnum apiRoutine, TimelinePosition youngestPosition,
                                       TimelinePosition oldestPosition, int limit, String actorId)
            throws ConnectionException {
        return new ArrayList<>();
    }

    @Override
    public AActivity followActor(String actorId, Boolean follow) throws ConnectionException {
        return AActivity.EMPTY;
    }

    @Override
    public Actor getActor(String actorId, String actorName) throws ConnectionException {
        return Actor.EMPTY;
    }

}
