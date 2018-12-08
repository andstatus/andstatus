/*
 * Copyright (C) 2018 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.data;

/** On mediaType see https://www.w3.org/TR/activitystreams-vocabulary/#dfn-mediatype */
public enum TextMediaType {
    PLAIN("text/plain", false, false),
    PLAIN_ESCAPED("text/plain", false, true), // Twitter has such
    HTML("text/html", true, true),
    UNKNOWN("text/*", true, true);

    public final String mimeType;

    public final boolean hasHtml;
    public final boolean isHtmlEscaped;

    TextMediaType(String mimeType, boolean hasHtml, boolean isHtmlEscaped) {
        this.mimeType = mimeType;
        this.hasHtml = hasHtml;
        this.isHtmlEscaped = isHtmlEscaped;
    }
}