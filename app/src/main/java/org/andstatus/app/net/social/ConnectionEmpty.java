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

import androidx.annotation.NonNull;

import org.andstatus.app.net.http.HttpConnectionEmpty;
import org.andstatus.app.util.TryUtils;

import java.util.Optional;

import io.vavr.control.Try;

public class ConnectionEmpty extends Connection {
    public static final ConnectionEmpty EMPTY = new ConnectionEmpty();

    ConnectionEmpty() {
        http = HttpConnectionEmpty.EMPTY;
    }

    @Override
    @NonNull
    public Try<Actor> verifyCredentials(Optional<Uri> whoAmI) {
        return TryUtils.notFound();
    }

    @Override
    public Try<AActivity> undoLike(String noteOid) {
        return AActivity.TRY_EMPTY;
    }

    @Override
    public Try<AActivity> like(String noteOid) {
        return AActivity.TRY_EMPTY;
    }

    @Override
    public Try<Boolean> deleteNote(String noteOid) {
        return TryUtils.notFound();
    }

    @Override
    public Try<AActivity> getNote1(String noteOid) {
        return AActivity.TRY_EMPTY;
    }

    @Override
    public Try<AActivity> updateNote(Note note, String inReplyToOid, Attachments attachments) {
        return AActivity.TRY_EMPTY;
    }

    @Override
    public Try<AActivity> announce(String rebloggedNoteOid) {
        return AActivity.TRY_EMPTY;
    }

    @NonNull
    @Override
    public Try<InputTimelinePage> getTimeline(boolean syncYounger, ApiRoutineEnum apiRoutine,
                  TimelinePosition youngestPosition, TimelinePosition oldestPosition, int limit, Actor actor) {
        return Try.success(InputTimelinePage.EMPTY);
    }

    @Override
    public Try<AActivity> follow(String actorOid, Boolean follow) {
        return AActivity.TRY_EMPTY;
    }

    @Override
    public Try<Actor> getActor2(Actor actorIn) {
        return Actor.TRY_EMPTY;
    }

}
