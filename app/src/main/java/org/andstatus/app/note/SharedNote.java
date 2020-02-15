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

package org.andstatus.app.note;

import android.content.Intent;
import android.net.Uri;

import org.andstatus.app.data.TextMediaType;
import org.andstatus.app.util.StringUtil;
import org.andstatus.app.util.UriUtils;

import java.util.Optional;

import androidx.annotation.NonNull;

public class SharedNote {
    Optional<String> name;
    Optional<String> content;
    TextMediaType textMediaType;
    Optional<Uri> mediaUri;
    Optional<String> mediaType;

    public static Optional<SharedNote> fromIntent(Intent intent) {
        SharedNote shared = new SharedNote();
        shared.name = StringUtil.optNotEmpty(intent.getStringExtra(Intent.EXTRA_SUBJECT));
        Optional<String> html = StringUtil.optNotEmpty(intent.getStringExtra(Intent.EXTRA_HTML_TEXT));
        if (html.isPresent()) {
            shared.content = html;
            shared.textMediaType = TextMediaType.HTML;
        } else {
            shared.content = StringUtil.optNotEmpty(intent.getStringExtra(Intent.EXTRA_TEXT));
            shared.textMediaType = TextMediaType.PLAIN;
        }
        shared.mediaUri = Optional.ofNullable(intent.getParcelableExtra(Intent.EXTRA_STREAM))
                .map(Object::toString)
                .flatMap(UriUtils::toOptional);
        shared.mediaType = shared.mediaUri.flatMap(u -> Optional.ofNullable(intent.getType()));
        return shared.isEmpty() ? Optional.empty() : Optional.of(shared);
    }

    boolean isEmpty() {
        return !name.isPresent() && !content.isPresent() && !mediaUri.isPresent();
    }

    @NonNull
    @Override
    public String toString() {
        return "Share via this app " +
            name.map(s -> "; title:'" + s + "'").orElse("") +
            content.map(s -> "; content:'" + s + "' " + textMediaType).orElse("") +
            mediaUri.map(s -> "; media:'" + s + "' " + mediaType.orElse("")).orElse("");
    }
}
