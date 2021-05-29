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

import android.content.ContentUris
import android.content.UriMatcher
import android.net.Uri
import org.andstatus.app.ClassInApplicationPackage
import org.andstatus.app.account.MyAccount
import org.andstatus.app.actor.ActorsScreenType
import org.andstatus.app.database.DatabaseHolder
import org.andstatus.app.database.table.ActivityTable
import org.andstatus.app.database.table.ActorTable
import org.andstatus.app.database.table.NoteTable
import org.andstatus.app.database.table.OriginTable
import org.andstatus.app.timeline.meta.Timeline

/**
 * Classifier of Uri-s, passed to our content provider
 * @author yvolk@yurivolkov.com
 */
enum class MatchedUri(private val code: Int) {
    /**
     * This Uri is for some Timeline
     */
    TIMELINE(1),
    TIMELINE_SEARCH(3),

    /**
     * The Timeline URI contains Note (item) id
     */
    TIMELINE_ITEM(4),

    /**
     * Operations on [org.andstatus.app.database.table.ActivityTable] and dependent tables
     */
    ACTIVITY(7),
    NOTE_ITEM(10),
    ORIGIN(8),
    ORIGIN_ITEM(11),

    /**
     * Actors screens
     */
    ACTORS(5),
    ACTORS_SEARCH(12),
    ACTORS_ITEM(13),

    /**
     * Operations on [ActorTable] itself
     */
    ACTOR(6),
    ACTOR_ITEM(9),
    UNKNOWN(0);

    companion object {
        /**
         * "Authority", represented by this Content Provider and declared in the application's manifest.
         * (see [&lt;provider&gt;](http://developer.android.com/guide/topics/manifest/provider-element.html))
         *
         * Note: This is historical constant, remained to preserve compatibility without reinstallation
         */
        val AUTHORITY: String = ClassInApplicationPackage.PACKAGE_NAME + ".data.MyProvider"
        private val ORIGIN_SEGMENT: String = "origin"
        private val SEARCH_SEGMENT: String = "search"
        private val LISTTYPE_SEGMENT: String = "lt"
        private val CONTENT_SEGMENT: String = "content"
        private val CONTENT_ITEM_SEGMENT: String = "item"
        private val ACTOR_SEGMENT: String = "actor"
        private val CENTRAL_ITEM_SEGMENT: String = "cnt"
        private val CONTENT_URI_SCHEME_AND_HOST: String = "content://$AUTHORITY"
        private val CONTENT_URI_PREFIX: String = "$CONTENT_URI_SCHEME_AND_HOST/"
        val ACTIVITY_CONTENT_URI: Uri = Uri.parse(CONTENT_URI_PREFIX + ActivityTable.TABLE_NAME + "/" + CONTENT_SEGMENT)

        fun fromUri(uri: Uri?): MatchedUri {
            if (uri == null || uri == Uri.EMPTY) {
                return UNKNOWN
            }
            val codeIn = URI_MATCHER.match(replaceClickHost(uri))
            for (matched in values()) {
                if (matched.code == codeIn) {
                    return matched
                }
            }
            return UNKNOWN
        }

        private fun replaceClickHost(uri: Uri): Uri {
            return if (uri.host == Timeline.TIMELINE_CLICK_HOST) Uri.parse(CONTENT_URI_SCHEME_AND_HOST + uri.encodedPath) else uri
        }

        private val URI_MATCHER: UriMatcher = UriMatcher(UriMatcher.NO_MATCH)

        /**
         * MIME types should be like in android:mimeType in AndroidManifest.xml
         */
        private val CONTENT_TYPE_PREFIX: String = ("vnd.android.cursor.dir/"
                + ClassInApplicationPackage.PACKAGE_NAME + ".provider.")
        private val CONTENT_ITEM_TYPE_PREFIX: String = ("vnd.android.cursor.item/"
                + ClassInApplicationPackage.PACKAGE_NAME + ".provider.")

        /** Uri for the note in the account's timeline  */
        fun getTimelineItemUri(timeline: Timeline, noteId: Long): Uri {
            var uri = timeline.getUri()
            uri = Uri.withAppendedPath(uri, CONTENT_ITEM_SEGMENT)
            uri = ContentUris.withAppendedId(uri, noteId)
            return uri
        }

        fun getTimelineUri(timeline: Timeline): Uri {
            var uri = getBaseAccountUri(timeline.myAccountToSync.actorId, NoteTable.TABLE_NAME)
            uri = Uri.withAppendedPath(uri, LISTTYPE_SEGMENT + "/" + timeline.timelineType.save())
            uri = Uri.withAppendedPath(uri, ORIGIN_SEGMENT + "/" + timeline.getOrigin().id)
            uri = Uri.withAppendedPath(uri, ACTOR_SEGMENT)
            uri = ContentUris.withAppendedId(uri, timeline.getActorId())
            if (timeline.getSearchQuery().isNotEmpty()) {
                uri = Uri.withAppendedPath(uri, SEARCH_SEGMENT)
                uri = Uri.withAppendedPath(uri, Uri.encode(timeline.getSearchQuery()))
            }
            return uri
        }

        fun getMsgUri(accountActorId: Long, noteId: Long): Uri {
            return getContentItemUri(accountActorId, NoteTable.TABLE_NAME, noteId)
        }

        /**
         * Build Uri for this Actor on an ActorsScreen / [MyAccount]
         * @param searchQuery
         */
        fun getActorsScreenUri(actorsScreenType: ActorsScreenType, originId: Long, centralItemId: Long,
                               searchQuery: String?): Uri {
            var uri = getBaseAccountUri(0, ActorTable.TABLE_NAME)
            uri = Uri.withAppendedPath(uri, LISTTYPE_SEGMENT + "/" + actorsScreenType.save())
            uri = Uri.withAppendedPath(uri, "$ORIGIN_SEGMENT/$originId")
            uri = Uri.withAppendedPath(uri, CENTRAL_ITEM_SEGMENT)
            uri = ContentUris.withAppendedId(uri, centralItemId)
            if (!searchQuery.isNullOrEmpty()) {
                uri = Uri.withAppendedPath(uri, SEARCH_SEGMENT)
                uri = Uri.withAppendedPath(uri, Uri.encode(searchQuery))
            }
            return uri
        }

        fun getActorUri(accountActorId: Long, actorId: Long): Uri {
            return getContentItemUri(accountActorId, ActorTable.TABLE_NAME, actorId)
        }

        fun getOriginUri(originId: Long): Uri {
            return getContentItemUri(0, OriginTable.TABLE_NAME, originId)
        }

        /**
         * @param accountActorId actorId of MyAccount or 0 if not needed
         * @param tableName name in the [DatabaseHolder]
         * @param itemId ID or 0 - if the Item doesn't exist
         */
        private fun getContentItemUri(accountActorId: Long, tableName: String?, itemId: Long): Uri {
            var uri = getBaseAccountUri(accountActorId, tableName)
            uri = Uri.withAppendedPath(uri, CONTENT_ITEM_SEGMENT)
            uri = ContentUris.withAppendedId(uri, itemId)
            return uri
        }

        private fun getBaseAccountUri(accountActorId: Long, tableName: String?): Uri {
            return ContentUris.withAppendedId(Uri.parse(CONTENT_URI_PREFIX + tableName),
                    accountActorId)
        }

        init {
            /**
             * The order of PathSegments (parameters of timelines) in the URI
             * 1. MyAccount ACTOR_ID is the first parameter (this is his timeline of the type specified below!)
             * 2 - 3. LISTTYPE_SEGMENT + actual type
             * 4 - 5. ORIGIN_SEGMENT +  0 or 1  (1 for combined timeline)
             * 6 - 7. MyDatabase.NOTE_TABLE_NAME + "/" + NOTE_ID  (optional, used to access specific Note)
             */
            URI_MATCHER.addURI(AUTHORITY, NoteTable.TABLE_NAME + "/#/" + LISTTYPE_SEGMENT + "/*/" + ORIGIN_SEGMENT + "/#/" + ACTOR_SEGMENT + "/#/" + CONTENT_ITEM_SEGMENT + "/#", TIMELINE_ITEM.code)
            URI_MATCHER.addURI(AUTHORITY, NoteTable.TABLE_NAME + "/#/" + LISTTYPE_SEGMENT + "/*/" + ORIGIN_SEGMENT + "/#/" + ACTOR_SEGMENT + "/#/" + SEARCH_SEGMENT + "/*", TIMELINE_SEARCH.code)
            URI_MATCHER.addURI(AUTHORITY, NoteTable.TABLE_NAME + "/#/" + LISTTYPE_SEGMENT + "/*/" + ORIGIN_SEGMENT + "/#/" + ACTOR_SEGMENT + "/#/rnd/#", TIMELINE.code)
            URI_MATCHER.addURI(AUTHORITY, NoteTable.TABLE_NAME + "/#/" + LISTTYPE_SEGMENT + "/*/" + ORIGIN_SEGMENT + "/#/" + ACTOR_SEGMENT + "/#", TIMELINE.code)
            URI_MATCHER.addURI(AUTHORITY, NoteTable.TABLE_NAME + "/#/" + CONTENT_ITEM_SEGMENT + "/#", NOTE_ITEM.code)
            URI_MATCHER.addURI(AUTHORITY, ActivityTable.TABLE_NAME + "/" + CONTENT_SEGMENT, ACTIVITY.code)
            URI_MATCHER.addURI(AUTHORITY, OriginTable.TABLE_NAME + "/#/" + CONTENT_ITEM_SEGMENT + "/#", ORIGIN_ITEM.code)
            URI_MATCHER.addURI(AUTHORITY, OriginTable.TABLE_NAME + "/" + CONTENT_SEGMENT, ORIGIN.code)
            URI_MATCHER.addURI(AUTHORITY, ActorTable.TABLE_NAME + "/#/" + LISTTYPE_SEGMENT + "/*/" + ORIGIN_SEGMENT + "/#/" + CONTENT_ITEM_SEGMENT + "/#", ACTORS_ITEM.code)
            URI_MATCHER.addURI(AUTHORITY, ActorTable.TABLE_NAME + "/#/" + LISTTYPE_SEGMENT + "/*/" + ORIGIN_SEGMENT + "/#/" + CENTRAL_ITEM_SEGMENT + "/#/" + SEARCH_SEGMENT + "/*", ACTORS_SEARCH.code)
            URI_MATCHER.addURI(AUTHORITY, ActorTable.TABLE_NAME + "/#/" + LISTTYPE_SEGMENT + "/*/" + ORIGIN_SEGMENT + "/#/" + CENTRAL_ITEM_SEGMENT + "/#", ACTORS.code)
            URI_MATCHER.addURI(AUTHORITY, ActorTable.TABLE_NAME + "/#/" + CONTENT_ITEM_SEGMENT + "/#", ACTOR_ITEM.code)
            URI_MATCHER.addURI(AUTHORITY, ActorTable.TABLE_NAME + "/#/" + CONTENT_SEGMENT, ACTOR.code)
        }
    }

    /**
     * Implements [android.content.ContentProvider.getType]
     */
    fun getMimeType(): String? {
        return when (this) {
            ACTIVITY -> CONTENT_TYPE_PREFIX + ActivityTable.TABLE_NAME
            TIMELINE,
            TIMELINE_SEARCH -> CONTENT_TYPE_PREFIX + NoteTable.TABLE_NAME
            TIMELINE_ITEM,
            NOTE_ITEM -> CONTENT_ITEM_TYPE_PREFIX + NoteTable.TABLE_NAME
            ORIGIN_ITEM -> CONTENT_ITEM_TYPE_PREFIX + OriginTable.TABLE_NAME
            ACTOR,
            ACTORS -> CONTENT_TYPE_PREFIX + ActorTable.TABLE_NAME
            ACTOR_ITEM -> CONTENT_ITEM_TYPE_PREFIX + ActorTable.TABLE_NAME
            else -> null
        }
    }
}
