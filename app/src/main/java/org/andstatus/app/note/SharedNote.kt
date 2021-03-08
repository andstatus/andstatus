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
package org.andstatus.app.note

import android.content.Intent
import android.net.Uri
import org.andstatus.app.data.TextMediaType
import org.andstatus.app.util.StringUtil
import org.andstatus.app.util.UriUtils
import java.util.*
import java.util.function.Function

class SharedNote {
    var name: Optional<String> = null
    var content: Optional<String> = null
    var textMediaType: TextMediaType? = null
    var mediaUri: Optional<Uri> = null
    var mediaType: Optional<String> = null
    fun isEmpty(): Boolean {
        return !name.isPresent() && !content.isPresent() && !mediaUri.isPresent()
    }

    override fun toString(): String {
        return "Share via this app " +
                name.map(Function { s: String? -> "; title:'$s'" }).orElse("") +
                content.map(Function { s: String? -> "; content:'$s' $textMediaType" }).orElse("") +
                mediaUri.map(Function { s: Uri? -> "; media:'" + s + "' " + mediaType.orElse("") }).orElse("")
    }

    companion object {
        fun fromIntent(intent: Intent?): Optional<SharedNote> {
            val shared = SharedNote()
            shared.name = StringUtil.optNotEmpty(intent.getStringExtra(Intent.EXTRA_SUBJECT))
            val html = StringUtil.optNotEmpty(intent.getStringExtra(Intent.EXTRA_HTML_TEXT))
            if (html.isPresent) {
                shared.content = html
                shared.textMediaType = TextMediaType.HTML
            } else {
                shared.content = StringUtil.optNotEmpty(intent.getStringExtra(Intent.EXTRA_TEXT))
                shared.textMediaType = TextMediaType.PLAIN
            }
            shared.mediaUri = Optional.ofNullable<Any?>(intent.getParcelableExtra(Intent.EXTRA_STREAM))
                    .map { obj: Any? -> obj.toString() }
                    .flatMap { obj: String? -> UriUtils.toOptional() }
            shared.mediaType = shared.mediaUri.flatMap(Function { u: Uri? -> Optional.ofNullable(intent.getType()) })
            return if (shared.isEmpty()) Optional.empty() else Optional.of(shared)
        }
    }
}