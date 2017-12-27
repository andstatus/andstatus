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
import android.text.TextUtils;
import android.text.util.Linkify;

import org.apache.commons.lang3.text.translate.AggregateTranslator;
import org.apache.commons.lang3.text.translate.CharSequenceTranslator;
import org.apache.commons.lang3.text.translate.EntityArrays;
import org.apache.commons.lang3.text.translate.LookupTranslator;
import org.apache.commons.lang3.text.translate.NumericEntityUnescaper;

import java.util.regex.Pattern;

public class MyHtml {
    private static final Pattern GNU_SOCIAL_FAVORITED_SOMETHING_BY_PATTERN = Pattern.compile(
            "(?s)([^ ]+) favorited something by [^ ]+ (.+)");
    private static final Pattern SPACES_PATTERN = Pattern.compile("[\\[\\](){}\n\'\"<>,:;\\s]+");
    private static final Pattern PUNCTUATION_BEFORE_COMMA_PATTERN = Pattern.compile("[,.!?]+,");
    private static final Pattern MENTION_HASH_PREFIX_PATTERN = Pattern.compile("(,[@#!]([^@#!,]+))");
    private static final String NEWLINE_SEARCH = "\n";
    private static final String MULTIPLE_NEWLINES_REGEX = NEWLINE_SEARCH + "\\s*" + NEWLINE_SEARCH;
    private static final Pattern MULTIPLE_NEWLINES_PATTERN = Pattern.compile(MULTIPLE_NEWLINES_REGEX);
    private static final String NEWLINE_REPLACE = "\n";

    private MyHtml() {
        // Empty
    }

    public static String prepareForView(String text) {
        String text2 = stripUnnecessaryNewlines(text);
        if (text2.endsWith("</p>") && StringUtils.countOfOccurrences(text2, "<p") == 1) {
            text2 = text2.replaceAll("<p[^>]*>","").replaceAll("</p>","");
        }
        return text2;
    }

    @NonNull
	public static String htmlify(String text) {
		if (TextUtils.isEmpty(text)) return "";
        return stripUnnecessaryNewlines(hasHtmlMarkup(text) ? text : htmlifyPlain(text));
    }
	
    private static String htmlifyPlain(String text) {
        SpannableString spannable = SpannableString.valueOf(text);
        Linkify.addLinks(spannable, Linkify.WEB_URLS);
        return Html.toHtml(spannable);
    }

    @NonNull
    public static String getBodyToSearch(String body) {
        return normalizeWordsForSearch(fromHtml(body)).toLowerCase();
    }

    /** Strips HTML markup from the String */
    public static String fromHtml(String text) {
        return TextUtils.isEmpty(text) ? "" :
            stripUnnecessaryNewlines(
                    unescapeHtml(
                            MyHtml.hasHtmlMarkup(text) ? Html.fromHtml(text).toString() : text
                    )
            );
    }

    public static String unescapeHtml(String text2) {
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
    public static String stripUnnecessaryNewlines(String text) {
        if (TextUtils.isEmpty(text)) {
            return "";
        } else {
            String text2 = MULTIPLE_NEWLINES_PATTERN.matcher(text.trim()).replaceAll(NEWLINE_REPLACE);
            if (text2.endsWith(NEWLINE_REPLACE)) {
                return text2.substring(0, text2.length() - NEWLINE_REPLACE.length());
            }
            return text2;
        }
    }

    static String normalizeWordsForSearch(String text) {
        if (TextUtils.isEmpty(text)) {
            return "";
        } else {
            return MENTION_HASH_PREFIX_PATTERN.matcher(
                PUNCTUATION_BEFORE_COMMA_PATTERN.matcher(
                    SPACES_PATTERN.matcher(
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

    public static boolean isFavoritingAction(String body) {
        return GNU_SOCIAL_FAVORITED_SOMETHING_BY_PATTERN.matcher(
                fromHtml(body).toLowerCase()
        ).matches();
    }

    @NonNull
    public static String getCleanedBody(String body) {
        return GNU_SOCIAL_FAVORITED_SOMETHING_BY_PATTERN.matcher(
            SPACES_PATTERN.matcher(
                    fromHtml(body).toLowerCase()
            ).replaceAll(" ")
        ).replaceFirst("$2");
    }
}
