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
package org.andstatus.app.origin

import org.andstatus.app.context.MyContext
import org.andstatus.app.data.MyQuery
import org.andstatus.app.database.table.NoteTable
import org.andstatus.app.net.social.Visibility
import org.andstatus.app.util.MyLog
import java.net.MalformedURLException
import java.net.URL
import java.util.*

internal class OriginGnuSocial(myContext: MyContext?, originType: OriginType?) : Origin(myContext, originType) {
    override fun alternativeNotePermalink(noteId: Long): String? {
        try {
            return URL(url,
                    (if (Visibility.Companion.fromNoteId(noteId).isPrivate) "message" else "notice") + "/"
                            + MyQuery.noteIdToStringColumnValue(NoteTable.NOTE_OID, noteId)).toExternalForm()
        } catch (e: MalformedURLException) {
            MyLog.d(this, "Malformed URL from '" + url.toExternalForm() + "'", e)
        }
        return ""
    }

    override fun groupActorReferenceChar(): Optional<Char> {
        return GROUP_ACTOR_REFERENCE_CHARACTER
    }

    companion object {
        private val GROUP_ACTOR_REFERENCE_CHARACTER: Optional<Char> = Optional.of('!')
    }
}