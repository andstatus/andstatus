/* Copyright (C) 2020 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.net.http

import android.net.Uri
import com.github.scribejava.core.model.Verb
import io.vavr.control.Try
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.net.social.ApiRoutineEnum
import org.andstatus.app.net.social.Attachment
import org.andstatus.app.origin.OriginType
import org.andstatus.app.service.ConnectionRequired
import org.andstatus.app.util.UriUtils
import org.json.JSONObject
import java.io.File
import java.util.*

class HttpRequest private constructor(val apiRoutine: ApiRoutineEnum, val uri: Uri) {
    private var connectionData: HttpConnectionData = HttpConnectionData.EMPTY
    var verb: Verb = Verb.GET
    var connectionRequired: ConnectionRequired = ConnectionRequired.ANY
    var authenticate = true
    private var isLegacyHttpProtocol = false
    val maxSizeBytes: Long = MyPreferences.getMaximumSizeOfAttachmentBytes()
    var mediaPartName: String = "file"
    var mediaUri: Optional<Uri> = Optional.empty()
    var postParams: Optional<JSONObject> = Optional.empty()
    var fileResult: File? = null

    val hostAndPort: String? get() = uri.host ?.let { it + ":" + uri.port }

    fun validate(): Try<HttpRequest> {
        return if (UriUtils.isEmpty(uri)) {
            Try.failure(IllegalArgumentException("URi is empty; API: $apiRoutine"))
        } else Try.success(this)
    }

    override fun toString(): String {
        return ("uri: '" + uri + "'"
                + (if (verb != Verb.GET) ", verb: $verb" else "")
                + postParams.map { params: JSONObject -> ", params: $params" }.orElse(", (no params)")
                + (if (fileResult == null) "" else ", save to file")
                + (if (isLegacyHttpProtocol()) ", legacy HTTP" else "")
                + if (authenticate) ", authenticate" else "")
    }

    fun isLegacyHttpProtocol(): Boolean {
        return isLegacyHttpProtocol
    }

    fun withLegacyHttpProtocol(isLegacyHttpProtocol: Boolean): HttpRequest {
        this.isLegacyHttpProtocol = isLegacyHttpProtocol
        return this
    }

    fun withConnectionRequired(connectionRequired: ConnectionRequired): HttpRequest {
        this.connectionRequired = connectionRequired
        return this
    }

    fun withFile(file: File?): HttpRequest {
        fileResult = file
        return this
    }

    fun isFileTooLarge(): Boolean {
        return fileResult?.let {
            it.isFile && it.exists() && it.length() > MyPreferences.getMaximumSizeOfAttachmentBytes()
        } ?: false
    }

    fun withMediaPartName(mediaPartName: String): HttpRequest {
        this.mediaPartName = mediaPartName
        return asPost()
    }

    fun withAttachmentToPost(attachment: Attachment): HttpRequest {
        mediaUri = Optional.ofNullable(attachment.mediaUriToPost()).filter(UriUtils::nonEmpty)
        return asPost()
    }

    fun withPostParams(postParams: JSONObject?): HttpRequest {
        this.postParams = if (postParams == null || postParams.length() == 0) Optional.empty() else Optional.of(postParams)
        return asPost()
    }

    fun asPost(asPost: Boolean): HttpRequest {
        return if (asPost) asPost() else this
    }

    fun asPost(): HttpRequest {
        verb = Verb.POST
        return this
    }

    fun withAuthenticate(authenticate: Boolean): HttpRequest {
        this.authenticate = authenticate
        return this
    }

    fun withConnectionData(connectionData: HttpConnectionData): HttpRequest {
        this.connectionData = connectionData
        return this
    }

    fun connectionData(): HttpConnectionData {
        return connectionData
    }

    fun myContext(): MyContext {
        return connectionData.myContext()
    }

    fun getLogName(): String {
        return if (connectionData.getOriginType() === OriginType.ACTIVITYPUB ||
                connectionData.getOriginType() === OriginType.PUMPIO ||
                apiRoutine == ApiRoutineEnum.OAUTH_REGISTER_CLIENT ||
            apiRoutine == ApiRoutineEnum.AUTHORIZATION_SERVER_METADATA) {
            uri.getHost() + "-" + apiRoutine.name.toLowerCase()
        } else {
            connectionData.getAccountName().logName + "-" + apiRoutine.name.toLowerCase()
        }
    }

    fun newResult(): HttpReadResult {
        return HttpReadResult(this)
    }

    companion object {
        fun of(apiRoutine: ApiRoutineEnum, uri: Uri): HttpRequest {
            return HttpRequest(apiRoutine, uri)
        }
    }

}
