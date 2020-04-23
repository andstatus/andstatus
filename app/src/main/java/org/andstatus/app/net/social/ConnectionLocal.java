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

package org.andstatus.app.net.social;

import android.net.Uri;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.net.http.HttpReadResult;
import org.andstatus.app.net.http.HttpRequest;
import org.andstatus.app.service.ConnectionRequired;

import java.io.File;

import io.vavr.control.Try;

/**
 * Connection to local resources
 */
public class ConnectionLocal extends ConnectionEmpty {

    @Override
    public Try<HttpReadResult> downloadFile(ConnectionRequired connectionRequired, Uri uri, File file) {
        HttpRequest request = new HttpRequest(MyContextHolder.get(), uri).withFile(file);
        HttpReadResult result = request.newResult();
        return result.readStream("mediaUri='" + uri + "'",
                o -> MyContextHolder.get().context().getContentResolver().openInputStream(uri))
            .flatMap(HttpReadResult::tryToParse);
    }
}
