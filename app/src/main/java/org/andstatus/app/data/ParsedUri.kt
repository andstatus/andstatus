/* 
 * Copyright (C) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.data

import android.content.Intent
import android.net.Uri
import org.andstatus.app.IntentExtra
import org.andstatus.app.actor.ActorsScreenType
import org.andstatus.app.context.MyContext
import org.andstatus.app.origin.Origin
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.StringUtil

class ParsedUri private constructor(intent: Intent?) {
    private val uri: Uri = intent?.data ?: Uri.EMPTY
    private val matchedUri: MatchedUri = MatchedUri.fromUri(uri)
    val searchQuery: String = intent?.getStringExtra(IntentExtra.SEARCH_QUERY.key) ?: ""
        get() {
            if (field.isNotEmpty()) {
                return field
            }
            try {
                when (matchedUri) {
                    MatchedUri.TIMELINE_SEARCH, MatchedUri.ACTORS_SEARCH -> return StringUtil.notNull(uri.getPathSegments()[9])
                    else -> {
                    }
                }
            } catch (e: Exception) {
                MyLog.d(this, toString(), e)
            }
            return ""
        }


    fun matched(): MatchedUri {
        return matchedUri
    }

    override fun toString(): String {
        return "Uri:'" + uri + "'; matched:" + matched()
    }

    fun getUri(): Uri {
        return uri
    }

    fun isEmpty(): Boolean {
        return uri === Uri.EMPTY
    }

    fun getAccountActorId(): Long {
        var accountActorId: Long = 0
        try {
            when (matchedUri) {
                MatchedUri.TIMELINE,
                MatchedUri.TIMELINE_SEARCH,
                MatchedUri.TIMELINE_ITEM,
                MatchedUri.NOTE_ITEM,
                MatchedUri.ORIGIN_ITEM,
                MatchedUri.ACTORS,
                MatchedUri.ACTORS_SEARCH,
                MatchedUri.ACTOR_ITEM -> accountActorId = uri.getPathSegments()[1].toLong()
                else -> {
                }
            }
        } catch (e: Exception) {
            MyLog.d(this, toString(), e)
        }
        return accountActorId
    }

    fun getActorId(): Long {
        var actorId: Long = 0
        try {
            when (matchedUri) {
                MatchedUri.TIMELINE_SEARCH, MatchedUri.TIMELINE_ITEM, MatchedUri.TIMELINE -> actorId = uri.getPathSegments()[7].toLong()
                MatchedUri.ACTOR_ITEM -> actorId = uri.getPathSegments()[3].toLong()
                MatchedUri.ACTORS, MatchedUri.ACTORS_SEARCH -> if (getActorsScreenType() == ActorsScreenType.FOLLOWERS || getActorsScreenType() == ActorsScreenType.FRIENDS) {
                    actorId = getItemId()
                }
                MatchedUri.ACTORS_ITEM -> actorId = getItemId()
                else -> {
                }
            }
        } catch (e: Exception) {
            MyLog.e(this, toString(), e)
        }
        return actorId
    }

    fun getTimelineType(): TimelineType {
        try {
            when (matchedUri) {
                MatchedUri.TIMELINE,
                MatchedUri.TIMELINE_SEARCH,
                MatchedUri.TIMELINE_ITEM -> return TimelineType.load(uri.getPathSegments()[3])
                else -> {
                }
            }
        } catch (e: Exception) {
            MyLog.d(this, toString(), e)
        }
        return TimelineType.UNKNOWN
    }

    fun getActorsScreenType(): ActorsScreenType {
        try {
            when (matchedUri) {
                MatchedUri.ACTORS,
                MatchedUri.ACTORS_ITEM,
                MatchedUri.ACTORS_SEARCH -> return ActorsScreenType.load(uri.getPathSegments()[3])
                else -> { }
            }
        } catch (e: Exception) {
            MyLog.d(this, toString(), e)
        }
        return ActorsScreenType.UNKNOWN
    }

    fun getOriginId(): Long {
        var originId: Long = 0
        try {
            if (uri.getPathSegments().size > 4) {
                originId = uri.getPathSegments()[5].toLong()
            }
        } catch (e: Exception) {
            MyLog.d(this, toString(), e)
        }
        return originId
    }

    fun getOrigin(myContext: MyContext): Origin {
        return myContext.origins().fromId(getOriginId())
    }

    fun getNoteId(): Long {
        var noteId: Long = 0
        try {
            when (matchedUri) {
                MatchedUri.TIMELINE_ITEM -> noteId = uri.getPathSegments()[9].toLong()
                MatchedUri.NOTE_ITEM -> noteId = uri.getPathSegments()[3].toLong()
                MatchedUri.ACTORS, MatchedUri.ACTORS_SEARCH -> if (getActorsScreenType() == ActorsScreenType.ACTORS_OF_NOTE) {
                    noteId = getItemId()
                }
                else -> {
                }
            }
        } catch (e: Exception) {
            MyLog.d(this, toString(), e)
        }
        return noteId
    }

    fun isSearch(): Boolean {
        return searchQuery.isNotEmpty()
    }

    fun getItemId(): Long {
        return when (getActorsScreenType()) {
            ActorsScreenType.UNKNOWN -> when (getTimelineType()) {
                TimelineType.UNKNOWN -> 0
                else -> getNoteId()
            }
            else -> uri.getPathSegments()[7].toLong()
        }
    }

    companion object {
        fun fromUri(uri: Uri?): ParsedUri {
            return fromIntent(Intent(Intent.ACTION_DEFAULT, uri))
        }

        fun fromIntent(intent: Intent?): ParsedUri {
            return ParsedUri(intent)
        }
    }
}
