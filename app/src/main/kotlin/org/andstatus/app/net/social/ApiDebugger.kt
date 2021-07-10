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
package org.andstatus.app.net.social

import android.content.Context
import android.net.Uri
import io.vavr.control.Try
import org.andstatus.app.R
import org.andstatus.app.context.MyContext
import org.andstatus.app.net.http.HttpReadResult
import org.andstatus.app.net.http.HttpRequest
import org.andstatus.app.os.AsyncResult
import org.andstatus.app.os.AsyncEnum
import org.andstatus.app.util.DialogFactory

/** Send any GET requests using current account  */
class ApiDebugger(private val myContext: MyContext, private val activityContext: Context) {
    fun debugGet() {
        DialogFactory.showTextInputBox(activityContext, "Debug Social network API",
                "Type API path to GET e.g.\nstatusnet/conversation/12345.json\nor complete URL",
                { text: String -> this.debugGet(text) }, previousValue)
    }

    private fun debugGet(text: String) {
        AsyncResult<Unit, HttpReadResult>(taskId = null, pool = AsyncEnum.DEFAULT_POOL)
            .doInBackground { debugApiAsync(text) }
            .onPostExecute { _: Any?, results: Try<HttpReadResult> -> debugApiSync(results) }
            .execute(this, Unit)
    }

    private fun debugApiAsync(text: String): Try<HttpReadResult> {
        previousValue = text
        val connection = myContext.accounts.currentAccount.connection
        return connection.pathToUri(connection.partialPathToApiPath(text))
                .map { uri: Uri -> HttpRequest.of(ApiRoutineEnum.HOME_TIMELINE, uri) }
                .flatMap { request: HttpRequest -> connection.execute(request) }
    }

    private fun debugApiSync(results: Try<HttpReadResult>) {
        results
                .onSuccess { result: HttpReadResult -> DialogFactory.showOkAlertDialog(this, activityContext, android.R.string.ok, result.getResponse()) }
                .onFailure { e: Throwable -> DialogFactory.showOkAlertDialog(this, activityContext, R.string.error_connection_error, e.toString()) }
    }

    companion object {
        @Volatile
        private var previousValue: String? = ""
    }
}
