/*
 * Copyright (C) 2019 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.content.Context;
import android.net.Uri;

import org.andstatus.app.R;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.net.http.HttpReadResult;
import org.andstatus.app.net.http.HttpRequest;
import org.andstatus.app.os.AsyncTaskLauncher;
import org.andstatus.app.util.DialogFactory;
import org.andstatus.app.util.TryUtils;

import java.util.Optional;

import io.vavr.control.Try;

/** Send any GET requests using current account */
public class ApiDebugger {
    private static volatile String previousValue = "";
    private final MyContext myContext;
    private final Context activityContext;

    public ApiDebugger(MyContext myContext, Context activityContext) {
        this.myContext = myContext;
        this.activityContext = activityContext;
    }

    public void debugGet() {
        DialogFactory.showTextInputBox(activityContext,"Debug Social network API",
                "Type API path to GET e.g.\nstatusnet/conversation/12345.json\nor complete URL",
                this::debugGet, previousValue);
    }

    private void debugGet(String text) {
        AsyncTaskLauncher.execute(null, p -> debugApiAsync(text), p -> this::debugApiSync);
    }

    private Try<HttpReadResult> debugApiAsync(String text) {
        previousValue = text;
        Connection connection = myContext.accounts().getCurrentAccount().getConnection();
        Optional<Uri> optUri = connection.pathToUri(connection.partialPathToApiPath(text));
        return TryUtils.fromOptional(optUri)
        .map(uri -> HttpRequest.of(myContext, ApiRoutineEnum.HOME_TIMELINE, uri))
        .flatMap(connection::execute);
    }

    private void debugApiSync(Try<HttpReadResult> results) {
        results
        .onSuccess(result -> {
            DialogFactory.showOkAlertDialog(this, activityContext, android.R.string.ok, result.getResponse());
        })
        .onFailure( e ->
            DialogFactory.showOkAlertDialog(this, activityContext, R.string.error_connection_error, e.toString())
        );
    }
}