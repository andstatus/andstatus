/*
 * Copyright (C) 2011-2020 yvolk (Yuri Volkov), http://yurivolkov.com
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

/**
 * API routines (functions, "resources" in terms of Twitter)  enumerated
 * @author yvolk@yurivolkov.com
 */
enum class ApiRoutineEnum constructor(private val isNotePrivate: Boolean = false) {
    ACCOUNT_RATE_LIMIT_STATUS, ACCOUNT_VERIFY_CREDENTIALS,

    /** Returns most recent notes privately sent to the authenticating user  */
    PRIVATE_NOTES(true), LIKE, UNDO_LIKE, FOLLOW, GET_CONFIG, GET_CONVERSATION,

    /** List of actors  */
    GET_FRIENDS,

    /** List of Actors' IDs  */
    GET_FRIENDS_IDS, GET_FOLLOWERS, GET_FOLLOWERS_IDS, GET_OPEN_INSTANCES, GET_ACTOR, UPDATE_NOTE, UPDATE_PRIVATE_NOTE, UPLOAD_MEDIA, ANNOUNCE, UNDO_ANNOUNCE, DELETE_NOTE,

    /**
     * Get the Home timeline (whatever it is...).
     * This is the equivalent of /home on the Web.
     */
    HOME_TIMELINE,

    /** Notifications in a separate API  */
    NOTIFICATIONS_TIMELINE,

    /**
     * Get the Actor timeline for an actor with the selectedActorId.
     * We use credentials of our Account, which may be not the same the actor.
     */
    ACTOR_TIMELINE, PUBLIC_TIMELINE, TAG_TIMELINE, LIKED_TIMELINE, SEARCH_NOTES, SEARCH_ACTORS, GET_NOTE, UNDO_FOLLOW, DOWNLOAD_FILE,

    /**
     * OAuth APIs
     */
    OAUTH_ACCESS_TOKEN, OAUTH_AUTHORIZE, OAUTH_REQUEST_TOKEN,

    /** For the "OAuth Dynamic Client Registration",
     * is the link proper?: http://hdknr.github.io/docs/identity/oauth_reg.html   */
    OAUTH_REGISTER_CLIENT,

    /**
     * Simply ignore this API call
     */
    DUMMY_API;

    fun isNotePrivate(): Boolean {
        return isNotePrivate
    }

    fun isOriginApi(): Boolean {
        return when (this) {
            ApiRoutineEnum.OAUTH_ACCESS_TOKEN, ApiRoutineEnum.OAUTH_AUTHORIZE, ApiRoutineEnum.OAUTH_REGISTER_CLIENT, ApiRoutineEnum.OAUTH_REQUEST_TOKEN, ApiRoutineEnum.DOWNLOAD_FILE, ApiRoutineEnum.DUMMY_API, ApiRoutineEnum.GET_OPEN_INSTANCES, ApiRoutineEnum.ACCOUNT_VERIFY_CREDENTIALS -> false
            else -> true
        }
    }
}
