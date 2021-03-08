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
package org.andstatus.app.net.social

import io.vavr.control.CheckedFunction
import io.vavr.control.Try
import org.andstatus.app.data.DownloadStatus
import org.andstatus.app.data.TextMediaType
import org.andstatus.app.net.http.ConnectionException
import org.andstatus.app.net.http.HttpConnectionInterface
import org.andstatus.app.net.http.HttpReadResult
import org.andstatus.app.origin.OriginConfig
import org.andstatus.app.util.JsonUtils
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.RelativeTime
import org.andstatus.app.util.TryUtils
import org.andstatus.app.util.UriUtils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

android.net.Uri

org.andstatus.app.net.http.HttpRequestimport java.lang.StringBuilderimport java.util.ArrayListimport java.util.regex.Pattern
/**
 * Specific implementation of the Twitter API in GNU Social
 * @author yvolk@yurivolkov.com
 */
class ConnectionTwitterGnuSocial : ConnectionTwitterLike() {
    override fun getApiPathFromOrigin(routine: ApiRoutineEnum?): String {
        val url: String
        url = when (routine) {
            ApiRoutineEnum.GET_CONFIG -> "statusnet/config.json"
            ApiRoutineEnum.GET_CONVERSATION -> "statusnet/conversation/%noteId%.json"
            ApiRoutineEnum.GET_OPEN_INSTANCES -> "http://gstools.org/api/get_open_instances"
            ApiRoutineEnum.PUBLIC_TIMELINE -> "statuses/public_timeline.json"
            ApiRoutineEnum.SEARCH_NOTES -> "search.json"
            else -> ""
        }
        return if (url.isNullOrEmpty()) {
            super.getApiPathFromOrigin(routine)
        } else partialPathToApiPath(url)
    }

    override fun getFriendsOrFollowersIds(apiRoutine: ApiRoutineEnum?, actorOid: String?): Try<MutableList<String>>? {
        return getApiPath(apiRoutine)
                .map { obj: Uri? -> obj.buildUpon() }
                .map { builder: Uri.Builder? -> builder.appendQueryParameter("user_id", actorOid) }
                .map(CheckedFunction<Uri.Builder?, Uri?> { Uri.Builder.build() })
                .map(CheckedFunction<Uri?, HttpRequest?> { uri: Uri? -> HttpRequest.Companion.of(apiRoutine, uri) })
                .flatMap { request: HttpRequest? -> execute(request) }
                .flatMap { obj: HttpReadResult? -> obj.getJsonArray() }
                .flatMap { jsonArray: JSONArray? ->
                    val list: MutableList<String?> = ArrayList()
                    try {
                        var index = 0
                        while (jsonArray != null && index < jsonArray.length()) {
                            list.add(jsonArray.getString(index))
                            index++
                        }
                    } catch (e: JSONException) {
                        return@flatMap Try.failure<MutableList<String?>?>(ConnectionException.Companion.loggedJsonException(this, apiRoutine.name, e, jsonArray))
                    }
                    Try.success(list)
                }
    }

    override fun rateLimitStatus(): Try<RateLimitStatus> {
        val apiRoutine = ApiRoutineEnum.ACCOUNT_RATE_LIMIT_STATUS
        return getApiPath(apiRoutine)
                .map(CheckedFunction<Uri?, HttpRequest?> { uri: Uri? -> HttpRequest.Companion.of(apiRoutine, uri) })
                .flatMap { request: HttpRequest? -> execute(request) }
                .flatMap { obj: HttpReadResult? -> obj.getJsonObject() }
                .flatMap { result: JSONObject? ->
                    val status = RateLimitStatus()
                    if (result != null) {
                        status.remaining = result.optInt("remaining_hits")
                        status.limit = result.optInt("hourly_limit")
                    }
                    Try.success(status)
                }
    }

    override fun updateNote2(note: Note?): Try<AActivity> {
        val formParams = JSONObject()
        try {
            super.updateNoteSetFields(note, formParams)

            // This parameter was removed from Twitter API, but it still is in GNUsocial
            formParams.put("source", HttpConnectionInterface.Companion.USER_AGENT)
        } catch (e: JSONException) {
            return Try.failure(e)
        }
        return tryApiPath(data.accountActor, ApiRoutineEnum.UPDATE_NOTE)
                .map(CheckedFunction<Uri?, HttpRequest?> { uri: Uri? ->
                    HttpRequest.Companion.of(ApiRoutineEnum.UPDATE_NOTE, uri)
                            .withPostParams(formParams)
                            .withMediaPartName("media")
                            .withAttachmentToPost(note.attachments.firstToUpload)
                }
                )
                .flatMap { request: HttpRequest? -> execute(request) }
                .flatMap { obj: HttpReadResult? -> obj.getJsonObject() }
                .map { jso: JSONObject? -> activityFromJson(jso) }
    }

    override fun getConfig(): Try<OriginConfig> {
        val apiRoutine = ApiRoutineEnum.GET_CONFIG
        return getApiPath(apiRoutine)
                .map(CheckedFunction<Uri?, HttpRequest?> { uri: Uri? -> HttpRequest.Companion.of(apiRoutine, uri) })
                .flatMap { request: HttpRequest? -> execute(request) }
                .flatMap { obj: HttpReadResult? -> obj.getJsonObject() }
                .map { result: JSONObject? ->
                    var config: OriginConfig = OriginConfig.Companion.getEmpty()
                    if (result != null) {
                        val site = result.optJSONObject("site")
                        if (site != null) {
                            val textLimit = site.optInt("textlimit")
                            var uploadLimit = 0
                            val attachments = site.optJSONObject("attachments")
                            if (attachments != null && site.optBoolean("uploads")) {
                                uploadLimit = site.optInt("file_quota")
                            }
                            config = OriginConfig.Companion.fromTextLimit(textLimit, uploadLimit.toLong())
                            // "shorturllength" is not used
                        }
                    }
                    config
                }
    }

    override fun getConversation(conversationOid: String?): Try<MutableList<AActivity>>? {
        if (UriUtils.nonRealOid(conversationOid)) return TryUtils.emptyList()
        val apiRoutine = ApiRoutineEnum.GET_CONVERSATION
        return getApiPathWithNoteId(apiRoutine, conversationOid)
                .map(CheckedFunction<Uri?, HttpRequest?> { uri: Uri? -> HttpRequest.Companion.of(apiRoutine, uri) })
                .flatMap { request: HttpRequest? -> execute(request) }
                .flatMap { obj: HttpReadResult? -> obj.getJsonArray() }
                .flatMap { jsonArray: JSONArray? -> jArrToTimeline(jsonArray, apiRoutine) }
    }

    @Throws(JSONException::class)
    override fun setNoteBodyFromJson(note: Note?, jso: JSONObject?) {
        if (data.origin.isHtmlContentAllowed && !jso.isNull(HTML_BODY_FIELD_NAME)) {
            note.setContent(jso.getString(HTML_BODY_FIELD_NAME), TextMediaType.HTML)
        } else if (jso.has("text")) {
            note.setContent(jso.getString("text"), TextMediaType.PLAIN)
        }
    }

    @Throws(ConnectionException::class)
    public override fun activityFromJson2(jso: JSONObject?): AActivity {
        if (jso == null) return AActivity.Companion.EMPTY
        val method = "activityFromJson2"
        val activity = super.activityFromJson2(jso)
        val note = activity.note
        note.url = JsonUtils.optString(jso, "external_url")
        note.setConversationOid(JsonUtils.optString(jso, CONVERSATION_ID_FIELD_NAME))
        if (!jso.isNull(ATTACHMENTS_FIELD_NAME)) {
            try {
                val jArr = jso.getJSONArray(ATTACHMENTS_FIELD_NAME)
                for (ind in 0 until jArr.length()) {
                    val jsonAttachment = jArr[ind] as JSONObject
                    val uri = UriUtils.fromAlternativeTags(jsonAttachment, "url", "thumb_url")
                    val attachment: Attachment = Attachment.Companion.fromUriAndMimeType(uri, JsonUtils.optString(jsonAttachment, "mimetype"))
                    if (attachment.isValid) {
                        activity.addAttachment(attachment)
                    } else {
                        MyLog.d(this, "$method; invalid attachment #$ind; $jArr")
                    }
                }
            } catch (e: JSONException) {
                MyLog.d(this, method, e)
            }
        }
        return createLikeActivity(activity)
    }

    public override fun actorBuilderFromJson(jso: JSONObject?): Actor {
        return if (jso == null) Actor.EMPTY else super.actorBuilderFromJson(jso)
                .setProfileUrl(JsonUtils.optString(jso, "statusnet_profile_url"))
    }

    override fun getOpenInstances(): Try<MutableList<Server>>? {
        val apiRoutine = ApiRoutineEnum.GET_OPEN_INSTANCES
        return getApiPath(apiRoutine)
                .map(CheckedFunction<Uri?, HttpRequest?> { path: Uri? ->
                    HttpRequest.Companion.of(apiRoutine, path)
                            .withAuthenticate(false)
                })
                .flatMap { requestIn: HttpRequest? -> http.execute(requestIn) }
                .flatMap { obj: HttpReadResult? -> obj.getJsonObject() }
                .map { result: JSONObject? ->
                    val origins: MutableList<Server?> = ArrayList()
                    val logMessage = StringBuilder(apiRoutine.toString())
                    var error = false
                    if (result == null) {
                        MyStringBuilder.Companion.appendWithSpace(logMessage, "Response is null JSON")
                        error = true
                    }
                    if (!error && JsonUtils.optString(result, "status") != "OK") {
                        MyStringBuilder.Companion.appendWithSpace(logMessage, "gtools service returned the error: '" +
                                JsonUtils.optString(result, "error") + "'")
                        error = true
                    }
                    if (!error) {
                        val data = result.optJSONObject("data")
                        if (data != null) {
                            try {
                                val iterator = data.keys()
                                while (iterator.hasNext()) {
                                    val key = iterator.next()
                                    val instance = data.getJSONObject(key)
                                    origins.add(Server(JsonUtils.optString(instance, "instance_name"),
                                            JsonUtils.optString(instance, "instance_address"),
                                            instance.optLong("users_count"),
                                            instance.optLong("notices_count")))
                                }
                            } catch (e: JSONException) {
                                throw ConnectionException.Companion.loggedJsonException(this, logMessage.toString(), e, data)
                            }
                        }
                    }
                    if (error) {
                        throw ConnectionException(logMessage.toString())
                    }
                    origins
                }
    }

    companion object {
        private val ATTACHMENTS_FIELD_NAME: String? = "attachments"
        private val CONVERSATION_ID_FIELD_NAME: String? = "statusnet_conversation_id"
        private val HTML_BODY_FIELD_NAME: String? = "statusnet_html"
        private val GNU_SOCIAL_FAVORITED_SOMETHING_BY_PATTERN = Pattern.compile(
                "(?s)([^ ]+) favorited something by [^ ]+ (.+)")
        private val GNU_SOCIAL_FAVOURITED_A_STATUS_BY_PATTERN = Pattern.compile(
                "(?s)([^ ]+) favourited (a status by [^ ]+)")

        fun createLikeActivity(activityIn: AActivity?): AActivity? {
            val noteIn = activityIn.getNote()
            var matcher = GNU_SOCIAL_FAVORITED_SOMETHING_BY_PATTERN.matcher(noteIn.content)
            if (!matcher.matches()) {
                matcher = GNU_SOCIAL_FAVOURITED_A_STATUS_BY_PATTERN.matcher(noteIn.content)
            }
            if (!matcher.matches()) return activityIn
            val inReplyTo = noteIn.inReplyTo
            val favoritedActivity: AActivity
            if (UriUtils.isRealOid(inReplyTo.note.oid)) {
                favoritedActivity = inReplyTo
            } else {
                favoritedActivity = AActivity.Companion.from(activityIn.accountActor, ActivityType.UPDATE)
                favoritedActivity.setActor(activityIn.getActor())
                favoritedActivity.setNote(noteIn)
            }
            favoritedActivity.updatedDate = RelativeTime.SOME_TIME_AGO
            val note = favoritedActivity.note
            note.setContent(matcher.replaceFirst("$2"), TextMediaType.HTML)
            note.updatedDate = RelativeTime.SOME_TIME_AGO
            note.status = DownloadStatus.LOADED // TODO: Maybe we need to invent some other status for partially loaded...
            note.setInReplyTo(AActivity.Companion.EMPTY)
            val activity: AActivity = AActivity.Companion.from(activityIn.accountActor, ActivityType.LIKE)
            activity.oid = activityIn.getOid()
            activity.setActor(activityIn.getActor())
            activity.updatedDate = activityIn.getUpdatedDate()
            activity.setActivity(favoritedActivity)
            return activity
        }
    }
}