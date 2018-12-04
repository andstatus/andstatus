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

import android.support.annotation.NonNull;
import android.text.Html;
import android.text.SpannableString;
import android.text.util.Linkify;

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
    private static final Pattern LINEBREAK_PATTERN = Pattern.compile("\n");
    public static final String LINEBREAK_HTML = "<br />";
    private static final String LINEBREAK_REPLACE = "\n";
    private static final Pattern LINEBREAK_ESCAPED_PATTERN = Pattern.compile("&#10;");
    private static final String THREE_LINEBREAKS_REGEX = "\n\\s*\n\\s*\n";
    private static final Pattern THREE_LINEBREAKS_PATTERN = Pattern.compile(THREE_LINEBREAKS_REGEX);
    private static final String DOUBLE_LINEBREAK_REPLACE = "\n\n";

    private MyHtml() {
        // Empty
    }

    public static String prepareForView(String text) {
        if (StringUtils.isEmpty(text)) return "";

        String endWith = text.endsWith("</p>") ? "</p>" : text.endsWith("</p>\n") ? "</p>\n" : "";
        if (StringUtils.nonEmpty(endWith) && StringUtils.countOfOccurrences(text, "<p") == 1) {
            text = text.replaceAll("<p[^>]*>","").replaceAll(endWith,"");
        }
        return text;
    }

    @NonNull
	public static String htmlify(String text) {
		if (StringUtils.isEmpty(text)) return "";

        SpannableString spannable = SpannableString.valueOf(text);
        Linkify.addLinks(spannable, Linkify.WEB_URLS);
        return Html.toHtml(spannable, Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE);
    }

    /** Following ActivityStreams convention, default mediaType for content is "text/html" */
    @NonNull
    public static String toContentStoredAsHtml(String text, TextMediaType inputMediaType, boolean isHtmlContentAllowed) {
        if (StringUtils.isEmpty(text)) {
            return "";
        } else {
            String str2 = inputMediaType == TextMediaType.PLAIN
                    ? LINEBREAK_ESCAPED_PATTERN.matcher(Html.escapeHtml(text)).replaceAll(LINEBREAK_REPLACE)
                    : text;
            String str3 = isHtmlContentAllowed || inputMediaType == TextMediaType.PLAIN || !hasHtmlMarkup(str2)
                    ? str2
                    : Html.fromHtml(str2, Html.FROM_HTML_MODE_COMPACT).toString();
            String str4 = stripExcessiveLineBreaks(str3);
            return  (inputMediaType == TextMediaType.HTML && isHtmlContentAllowed) || hasHtmlMarkup(str4)
                    ? str4
                    : LINEBREAK_PATTERN.matcher(str4.trim()).replaceAll(LINEBREAK_HTML);
        }
    }

    @NonNull
    public static String getContentToSearch(String text) {
        return normalizeWordsForSearch(toCompactPlainText(text)).toLowerCase();
    }

    /** Strips ALL markup from the String, including line breaks. And remove all whiteSpace */
    @NonNull
    public static String toCompactPlainText(String text) {
        return SPACES_PATTERN.matcher(toPlainText(text)).replaceAll(" ");
    }

    /** Strips ALL markup from the String, excluding line breaks */
    @NonNull
    public static String toPlainText(String text) {
        if (StringUtils.isEmpty(text)) return "";

        String str1 = hasHtmlMarkup(text)
                ? Html.fromHtml(text, Html.FROM_HTML_MODE_COMPACT).toString()
                : text;
        String str2 = unescapeHtml(str1);
        return stripExcessiveLineBreaks(str2);
    }

    private static String unescapeHtml(String text2) {
        return UNESCAPE_HTML.translate(text2)
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

    @NonNull
    public static String stripExcessiveLineBreaks(String text) {
        if (StringUtils.isEmpty(text)) {
            return "";
        } else {
            String text2 = THREE_LINEBREAKS_PATTERN.matcher(text.trim()).replaceAll(DOUBLE_LINEBREAK_REPLACE).trim();
            while (text2.endsWith(LINEBREAK_REPLACE)) {
                text2 = text2.substring(0, text2.length() - LINEBREAK_REPLACE.length()).trim();
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

    /** Very simple method  
     */
    public static boolean hasHtmlMarkup(String text) {
        boolean has = false;
        if (text != null){
            has = text.contains("<") && text.contains(">");
        }
        return has; 
    }
}
