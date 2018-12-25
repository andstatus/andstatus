/*
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.util;

import androidx.annotation.NonNull;
import android.text.Html;

import org.andstatus.app.data.TextMediaType;
import org.apache.commons.lang3.text.translate.AggregateTranslator;
import org.apache.commons.lang3.text.translate.CharSequenceTranslator;
import org.apache.commons.lang3.text.translate.EntityArrays;
import org.apache.commons.lang3.text.translate.LookupTranslator;
import org.apache.commons.lang3.text.translate.NumericEntityUnescaper;

import java.util.regex.Pattern;

public class MyHtml {
    private static final Pattern SPACES_PATTERN = Pattern.compile("[\n\\s]+");
    private static final Pattern SPACES_FOR_SEARCH_PATTERN = Pattern.compile("[\\[\\](){}\n\'\"<>,:;\\s]+");
    private static final Pattern PUNCTUATION_BEFORE_COMMA_PATTERN = Pattern.compile("[,.!?]+,");
    private static final Pattern MENTION_HASH_PREFIX_PATTERN = Pattern.compile("(,[@#!]([^@#!,]+))");
    public static final String LINEBREAK_HTML = "<br />";
    private static final Pattern HTML_LINEBREAK_PATTERN = Pattern.compile(LINEBREAK_HTML);
    private static final String LINEBREAK_PLAIN = "\n";
    private static final Pattern LINEBREAK_PATTERN = Pattern.compile(LINEBREAK_PLAIN);
    private static final Pattern LINEBREAK_ESCAPED_PATTERN = Pattern.compile("&#10;");
    private static final Pattern SPACE_ESCAPED_PATTERN = Pattern.compile("&nbsp;");
    private static final String THREE_LINEBREAKS_REGEX = "\n\\s*\n\\s*\n";
    private static final Pattern THREE_LINEBREAKS_PATTERN = Pattern.compile(THREE_LINEBREAKS_REGEX);
    private static final String DOUBLE_LINEBREAK_REPLACE = "\n\n";
    private static final Pattern PLAIN_LINEBREAK_AFTER_HTML_LINEBREAK = Pattern.compile(
            "(</p>|<br[ /]*>)(\n\\s*)");

    private MyHtml() {
        // Empty
    }

    public static String prepareForView(String html) {
        if (StringUtils.isEmpty(html)) return "";

        String endWith = html.endsWith("</p>") ? "</p>" : html.endsWith("</p>\n") ? "</p>\n" : "";
        if (StringUtils.nonEmpty(endWith) && StringUtils.countOfOccurrences(html, "<p") == 1) {
           return html.replaceAll("<p[^>]*>","").replaceAll(endWith,"");
        }
        return html;
    }

    /** Following ActivityStreams convention, default mediaType for content is "text/html" */
    @NonNull
    public static String toContentStored(String text, TextMediaType inputMediaType, boolean isHtmlContentAllowed) {
        if (StringUtils.isEmpty(text)) return "";

        TextMediaType mediaType = inputMediaType == TextMediaType.UNKNOWN
                ? calcTextMediaType(text)
                : inputMediaType;

        String escaped;
        switch (mediaType) {
            case HTML:
                if (isHtmlContentAllowed) return text;

                escaped = Html.fromHtml(text, Html.FROM_HTML_MODE_COMPACT).toString();
                break;
            case PLAIN:
                escaped = escapeHtmlExceptLineBreaksAndSpace(text);
                break;
            case PLAIN_ESCAPED:
                escaped = (isHtmlContentAllowed || !hasHtmlMarkup(text))
                        ? text
                        : escapeHtmlExceptLineBreaksAndSpace(text);
                break;
            default:
                escaped = escapeHtmlExceptLineBreaksAndSpace(text);
                break;
        }
        String withLineBreaks = LINEBREAK_PATTERN.matcher(stripExcessiveLineBreaks(escaped)).replaceAll(LINEBREAK_HTML);

        // Maybe htmlify...

        return withLineBreaks;
    }

    private static TextMediaType calcTextMediaType(String text) {
        if (StringUtils.isEmpty(text)) return TextMediaType.PLAIN;

        if (hasHtmlMarkup(text)) return TextMediaType.HTML;

        return TextMediaType.PLAIN;
    }

    @NonNull
    public static String fromContentStored(String html, TextMediaType outputMediaType) {
        if (StringUtils.isEmpty(html)) return "";

        switch (outputMediaType) {
            case HTML:
                return html;
            case PLAIN:
                return htmlToPlainText(html);
            case PLAIN_ESCAPED:
                return escapeHtmlExceptLineBreaksAndSpace(htmlToPlainText(html));
            default:
                return html;
        }
    }

    @NonNull
    public static String getContentToSearch(String html) {
        return normalizeWordsForSearch(htmlToCompactPlainText(html)).toLowerCase();
    }

    /** Strips ALL markup from the String, including line breaks. And remove all whiteSpace */
    @NonNull
    public static String htmlToCompactPlainText(String html) {
        return SPACES_PATTERN.matcher(htmlToPlainText(html)).replaceAll(" ");
    }

    /** Strips ALL markup from the String, excluding line breaks */
    @NonNull
    public static String htmlToPlainText(String html) {
        if (StringUtils.isEmpty(html)) return "";

        String str0 = HTML_LINEBREAK_PATTERN.matcher(html).replaceAll("\n");
        String str1 = hasHtmlMarkup(str0)
                ? Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString()
                : str0;
        String str2 = unescapeHtml(str1);
        return stripExcessiveLineBreaks(str2);
    }

    private static String unescapeHtml(String textEscaped) {
        return StringUtils.isEmpty(textEscaped)
                ? ""
                :  UNESCAPE_HTML.translate(textEscaped)
                    // This is needed to avoid visible text truncation,
                    // see https://github.com/andstatus/andstatus/issues/441
                    .replaceAll("<>","< >");
    }

    private static final CharSequenceTranslator UNESCAPE_HTML =
            new AggregateTranslator(
                    new LookupTranslator(EntityArrays.BASIC_UNESCAPE()),
                    new LookupTranslator(EntityArrays.ISO8859_1_UNESCAPE()),
                    new LookupTranslator(EntityArrays.HTML40_EXTENDED_UNESCAPE()),
                    new LookupTranslator(EntityArrays.APOS_UNESCAPE()),
                    new NumericEntityUnescaper()
            );

    private static String escapeHtmlExceptLineBreaksAndSpace(String plainText) {
        return StringUtils.isEmpty(plainText)
            ? ""
            : SPACE_ESCAPED_PATTERN.matcher(
                    LINEBREAK_ESCAPED_PATTERN.matcher(Html.escapeHtml(plainText)).replaceAll(LINEBREAK_PLAIN)
                ).replaceAll(" ");
    }

    @NonNull
    static String stripExcessiveLineBreaks(String plainText) {
        if (StringUtils.isEmpty(plainText)) {
            return "";
        } else {
            String text2 = THREE_LINEBREAKS_PATTERN.matcher(plainText.trim()).replaceAll(DOUBLE_LINEBREAK_REPLACE).trim();
            while (text2.endsWith(LINEBREAK_PLAIN)) {
                text2 = text2.substring(0, text2.length() - LINEBREAK_PLAIN.length()).trim();
            }
            return text2;
        }
    }

    static String normalizeWordsForSearch(String text) {
        if (StringUtils.isEmpty(text)) {
            return "";
        } else {
            return MENTION_HASH_PREFIX_PATTERN.matcher(
                PUNCTUATION_BEFORE_COMMA_PATTERN.matcher(
                    SPACES_FOR_SEARCH_PATTERN.matcher(
                            "," + text + ","
                    ).replaceAll(",")
                ).replaceAll(",")
            ).replaceAll(",$2$1");
        }
    }

    /** Very simple method */
    public static boolean hasHtmlMarkup(String text) {
        if (StringUtils.isEmpty(text)) return false;

        return text.contains("<") && text.contains(">");
    }
}
