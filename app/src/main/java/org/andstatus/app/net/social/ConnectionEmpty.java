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

import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.http.HttpConnectionEmpty;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import androidx.annotation.NonNull;

public class ConnectionEmpty extends Connection {
    public static final ConnectionEmpty EMPTY = new ConnectionEmpty();

    ConnectionEmpty() {
        http = HttpConnectionEmpty.EMPTY;
    }

    @Override
    public RateLimitStatus rateLimitStatus() throws ConnectionException {
        return new RateLimitStatus();
    }

    @Override
    @NonNull
    public Actor verifyCredentials(Optional<Uri> whoAmI) throws ConnectionException {
        return Actor.EMPTY;
    }

    @Override
    public AActivity undoLike(String noteOid) throws ConnectionException {
        return AActivity.EMPTY;
    }

    @Override
    public AActivity like(String noteOid) throws ConnectionException {
        return AActivity.EMPTY;
    }

    @Override
    public boolean deleteNote(String noteOid) throws ConnectionException {
        return false;
    }

    @Override
    public AActivity getNote1(String noteOid) throws ConnectionException {
        return AActivity.EMPTY;
    }

    @Override
    public AActivity updateNote(String name, String content, String noteOid, Audience audience, String inReplyToOid,
                                Uri mediaUri) throws ConnectionException {
        return AActivity.EMPTY;
    }

    @Override
    public AActivity announce(String rebloggedNoteOid) throws ConnectionException {
        return AActivity.EMPTY;
    }

    @NonNull
    @Override
    public List<AActivity> getTimeline(ApiRoutineEnum apiRoutine, TimelinePosition youngestPosition,
                                       TimelinePosition oldestPosition, int limit, Actor actor)
            throws ConnectionException {
        return new ArrayList<>();
    }

    @Override
    public AActivity follow(String actorOid, Boolean follow) throws ConnectionException {
        return AActivity.EMPTY;
    }

    @Override
    public Actor getActor2(Actor actorIn) throws ConnectionException {
        return Actor.EMPTY;
    }

}
