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
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.StringUtil
import java.net.MalformedURLException
import java.net.URL

internal class OriginActivityPub(myContext: MyContext?, originType: OriginType?) : Origin(myContext, originType) {
    override fun getNotePermalink(noteId: Long): String? {
        val noteUrl = MyQuery.noteIdToStringColumnValue(NoteTable.NOTE_OID, noteId)
        if (!StringUtil.isEmpty(noteUrl)) {
            try {
                return URL(noteUrl).toExternalForm()
            } catch (e: MalformedURLException) {
                MyLog.d(this, "Malformed URL from '$noteUrl'", e)
            }
        }
        return ""
    }
}