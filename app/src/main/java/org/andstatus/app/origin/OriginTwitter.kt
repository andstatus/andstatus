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

import android.net.Uri
import androidx.annotation.StringRes
import org.andstatus.app.R
import org.andstatus.app.context.ActorInTimeline
import org.andstatus.app.context.MyContext
import org.andstatus.app.data.MyQuery
import org.andstatus.app.database.table.NoteTable
import org.andstatus.app.net.social.Visibility
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.UriUtils

internal class OriginTwitter(myContext: MyContext, originType: OriginType) : Origin(myContext, originType) {
    /**
     * In order to comply with Twitter's "Developer Display Requirements"
     * https://dev.twitter.com/terms/display-requirements
     * @param resId
     * @return Id of alternative (proprietary) term/phrase
     */
    override fun alternativeTermForResourceId(@StringRes resId: Int): Int {
        val resIdOut: Int
        resIdOut = when (resId) {
            R.string.button_create_message -> R.string.button_create_message_twitter
            R.string.menu_item_destroy_reblog -> R.string.menu_item_destroy_reblog_twitter
            R.string.menu_item_reblog -> R.string.menu_item_reblog_twitter
            R.string.message -> R.string.message_twitter
            R.string.reblogged_by -> R.string.reblogged_by_twitter
            else -> resId
        }
        return resIdOut
    }

    override fun alternativeNotePermalink(noteId: Long): String {
        if (url == null) {
            return ""
        }
        val uri = fixUriForPermalink(UriUtils.fromUrl(url))
        return if (Visibility.fromNoteId(noteId).isPrivate) {
            Uri.withAppendedPath(uri, "messages").toString()
        } else {
            val username = MyQuery.noteIdToUsername(NoteTable.AUTHOR_ID, noteId, ActorInTimeline.USERNAME)
            val oid = MyQuery.noteIdToStringColumnValue(NoteTable.NOTE_OID, noteId)
            Uri.withAppendedPath(uri, "$username/status/$oid").toString()
        }
    }

    override fun getAccountNameHost(): String {
        val host = super.getAccountNameHost()
        return if (host.startsWith(API_DOT)) host.substring(API_DOT.length) else host
    }

    override fun fixUriForPermalink(uri1: Uri): Uri {
        var uri2 = uri1
        if (UriUtils.nonEmpty(uri2)) {
            try {
                if (uri2.host?.startsWith(API_DOT) == true) {
                    uri2 = Uri.parse(uri1.toString().replace("//" + API_DOT, "//"))
                }
            } catch (e: NullPointerException) {
                MyLog.d(this, "Malformed Uri from '$uri2'", e)
            }
        }
        return uri2
    }

    companion object {
        val API_DOT: String = "api."
    }
}
