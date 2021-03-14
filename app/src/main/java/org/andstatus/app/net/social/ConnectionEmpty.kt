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
package org.andstatus.app.net.social

import android.net.Uri
import io.vavr.control.Try
import org.andstatus.app.net.http.HttpConnection
import org.andstatus.app.util.TryUtils
import java.util.*

class ConnectionEmpty internal constructor() : Connection() {
    override fun verifyCredentials(whoAmI: Optional<Uri>): Try<Actor?> {
        return TryUtils.notFound()
    }

    override fun undoLike(noteOid: String?): Try<AActivity> {
        return AActivity.TRY_EMPTY
    }

    override fun like(noteOid: String): Try<AActivity> {
        return AActivity.TRY_EMPTY
    }

    override fun deleteNote(noteOid: String): Try<Boolean> {
        return TryUtils.notFound()
    }

    public override fun getNote1(noteOid: String?): Try<AActivity> {
        return AActivity.TRY_EMPTY
    }

    override fun updateNote(note: Note?): Try<AActivity> {
        return AActivity.TRY_EMPTY
    }

    override fun announce(rebloggedNoteOid: String?): Try<AActivity> {
        return AActivity.TRY_EMPTY
    }

    override fun getTimeline(syncYounger: Boolean, apiRoutine: ApiRoutineEnum,
                             youngestPosition: TimelinePosition, oldestPosition: TimelinePosition,
                             limit: Int, actor: Actor): Try<InputTimelinePage> {
        return Try.success(InputTimelinePage.EMPTY)
    }

    override fun follow(actorOid: String, follow: Boolean): Try<AActivity> {
        return AActivity.TRY_EMPTY
    }

    public override fun getActor2(actorIn: Actor): Try<Actor> {
        return Actor.TRY_EMPTY
    }

    companion object {
        val EMPTY: ConnectionEmpty = ConnectionEmpty()
    }

    init {
        http = HttpConnection.EMPTY
    }
}