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
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.service.ConnectionRequired;
import org.json.JSONObject;

import java.io.File;
import java.util.Optional;

public class HttpRequest {

    final MyContext myContext;
    final Uri uri;
    final Verb verb;
    final ConnectionRequired connectionRequired;
    boolean authenticate = true;
    private boolean mIsLegacyHttpProtocol = false;
    final long maxSizeBytes;

    public final Optional<JSONObject> formParams;
    public final File fileResult;

    public HttpRequest(Uri uriIn, Verb verb, JSONObject formParams) {
        this (MyContextHolder.get(), ConnectionRequired.ANY, uriIn, verb, null, formParams);
    }

    public HttpRequest(MyContext myContext, ConnectionRequired connectionRequired, Uri uri, Verb verb, File file,
                       JSONObject formParams) {
        this.myContext = myContext;
        this.connectionRequired = connectionRequired;
        this.uri = uri;
        this.verb = verb;
        fileResult = file;
        this.formParams = formParams == null || formParams.length() == 0
            ? Optional.empty()
            : Optional.of(formParams);
        maxSizeBytes = MyPreferences.getMaximumSizeOfAttachmentBytes();
    }

    @Override
    public String toString() {
        return "; uri:'" + uri + "'"
            + (isLegacyHttpProtocol() ? "; legacy HTTP" : "")
            + (authenticate ? "; authenticated" : "")
            + formParams.map(params -> "; posted:'" + params + "'").orElse("")
            + (fileResult == null ? "" : "; saved to file");
    }

    boolean isLegacyHttpProtocol() {
        return mIsLegacyHttpProtocol;
    }

    HttpRequest withLegacyHttpProtocol(boolean mIsLegacyHttpProtocol) {
        this.mIsLegacyHttpProtocol = mIsLegacyHttpProtocol;
        return this;
    }

    boolean isFileTooLarge() {
        return  fileResult != null && fileResult.isFile() && fileResult.exists()
            && fileResult.length() > MyPreferences.getMaximumSizeOfAttachmentBytes();
    }

    HttpReadResult newResult() {
        return new HttpReadResult(this);
    }
}