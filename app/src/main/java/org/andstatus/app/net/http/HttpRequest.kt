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

package org.andstatus.app.net.http;

import android.net.Uri;

import com.github.scribejava.core.model.Verb;

import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.net.social.ApiRoutineEnum;
import org.andstatus.app.net.social.Attachment;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.service.ConnectionRequired;
import org.andstatus.app.util.UriUtils;
import org.json.JSONObject;

import java.io.File;
import java.util.Optional;

import io.vavr.control.Try;

public class HttpRequest {
    private HttpConnectionData connectionData = HttpConnectionData.EMPTY;
    public final ApiRoutineEnum apiRoutine;
    public final Uri uri;
    Verb verb = Verb.GET;
    ConnectionRequired connectionRequired = ConnectionRequired.ANY;
    boolean authenticate = true;
    private boolean isLegacyHttpProtocol = false;
    final long maxSizeBytes;

    String mediaPartName = "file";
    Optional<Uri> mediaUri = Optional.empty();
    public Optional<JSONObject> postParams = Optional.empty();
    File fileResult = null;

    public static HttpRequest of(ApiRoutineEnum apiRoutine, Uri uri) {
        return new HttpRequest(apiRoutine, uri);
    }

    public Try<HttpRequest> validate() {
        if (UriUtils.isEmpty(uri)) {
            return Try.failure(new IllegalArgumentException("URi is empty; API: " + apiRoutine));
        }
        return Try.success(this);
    }

    private HttpRequest(ApiRoutineEnum apiRoutine, Uri uri) {
        this.apiRoutine = apiRoutine;
        this.uri = uri;
        maxSizeBytes = MyPreferences.getMaximumSizeOfAttachmentBytes();
    }

    @Override
    public String toString() {
        return "uri:'" + uri + "'"
            + (verb != Verb.GET ? "; " + verb + ": " : "")
            + postParams.map(params -> "'" + params + "'").orElse("(nothing)")
            + (fileResult == null ? "" : "; save to file")
            + (isLegacyHttpProtocol() ? "; legacy HTTP" : "")
            + (authenticate ? "; authenticate" : "");
    }

    boolean isLegacyHttpProtocol() {
        return isLegacyHttpProtocol;
    }

    HttpRequest withLegacyHttpProtocol(boolean isLegacyHttpProtocol) {
        this.isLegacyHttpProtocol = isLegacyHttpProtocol;
        return this;
    }

    public HttpRequest withConnectionRequired(ConnectionRequired connectionRequired) {
        this.connectionRequired = connectionRequired;
        return this;
    }

    public HttpRequest withFile(File file) {
        this.fileResult = file;
        return this;
    }

    boolean isFileTooLarge() {
        return  fileResult != null && fileResult.isFile() && fileResult.exists()
            && fileResult.length() > MyPreferences.getMaximumSizeOfAttachmentBytes();
    }

    public HttpRequest withMediaPartName(String mediaPartName) {
        this.mediaPartName = mediaPartName;
        return asPost();
    }

    public HttpRequest withAttachmentToPost(Attachment attachment) {
        this.mediaUri = Optional.ofNullable(attachment.mediaUriToPost()).filter(UriUtils::nonEmpty);
        return asPost();
    }

    public HttpRequest withPostParams(JSONObject postParams) {
        this.postParams = postParams == null || postParams.length() == 0
                ? Optional.empty()
                : Optional.of(postParams);
        return asPost();
    }

    public HttpRequest asPost(boolean asPost) {
        return asPost ? asPost() : this;
    }

    public HttpRequest asPost() {
        verb = Verb.POST;
        return this;
    }

    public HttpRequest withAuthenticate(boolean authenticate) {
        this.authenticate = authenticate;
        return this;
    }

    public HttpRequest withConnectionData(HttpConnectionData connectionData) {
        this.connectionData = connectionData;
        return this;
    }

    HttpConnectionData connectionData() {
        return connectionData;
    }

    public MyContext myContext() {
        return connectionData.myContext();
    }

    String getLogName() {
        if (connectionData.getOriginType() == OriginType.ACTIVITYPUB
                || connectionData.getOriginType() == OriginType.PUMPIO
                || apiRoutine == ApiRoutineEnum.OAUTH_REGISTER_CLIENT) {
            return uri.getHost() + "-" + apiRoutine.name().toLowerCase();
        } else {
            return connectionData.getAccountName().getLogName() + "-" + apiRoutine.name().toLowerCase();
        }
    }

    public HttpReadResult newResult() {
        return new HttpReadResult(this);
    }
}