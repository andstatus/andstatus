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
package org.andstatus.app.util

import android.text.Html
import org.andstatus.app.data.TextMediaType
import org.apache.commons.lang3.text.translate.AggregateTranslator
import org.apache.commons.lang3.text.translate.CharSequenceTranslator
import org.apache.commons.lang3.text.translate.EntityArrays
import org.apache.commons.lang3.text.translate.LookupTranslator
import org.apache.commons.lang3.text.translate.NumericEntityUnescaper
import java.util.*
import java.util.regex.Pattern

object MyHtml {
    private val SPACES_PATTERN = Pattern.compile("[\n\\s]+")
    private val SPACES_FOR_SEARCH_PATTERN = Pattern.compile("[\\[\\](){}\n\'\"<>,:;\\s]+")
    private val PUNCTUATION_BEFORE_COMMA_PATTERN = Pattern.compile("[,.!?]+,")
    private val MENTION_HASH_PREFIX_PATTERN = Pattern.compile("(,[@#!]([^@#!,]+))")
    val LINEBREAK_HTML: String = "<br />"
    private val HTML_LINEBREAK_PATTERN = Pattern.compile(LINEBREAK_HTML)
    private val LINEBREAK_PLAIN: String = "\n"
    private val LINEBREAK_PATTERN = Pattern.compile(LINEBREAK_PLAIN)
    private val LINEBREAK_ESCAPED_PATTERN = Pattern.compile("&#10;")
    private val SPACE_ESCAPED_PATTERN = Pattern.compile("&nbsp;")
    private val THREE_LINEBREAKS_REGEX: String = "\n\\s*\n\\s*\n"
    private val THREE_LINEBREAKS_PATTERN = Pattern.compile(THREE_LINEBREAKS_REGEX)
    private val DOUBLE_LINEBREAK_REPLACE: String = "\n\n"
    private val PLAIN_LINEBREAK_AFTER_HTML_LINEBREAK = Pattern.compile(
            "(</p>|<br[ /]*>)(\n\\s*)")

    fun prepareForView(html: String?): String {
        if (html.isNullOrEmpty()) return ""
        val endWith = if (html.endsWith("</p>")) "</p>" else if (html.endsWith("</p>\n")) "</p>\n" else ""
        return if (!endWith.isNullOrEmpty() && StringUtil.countOfOccurrences(html, "<p") == 1) {
            html.replace("<p[^>]*>".toRegex(), "").replace(endWith.toRegex(), "")
        } else html
    }

    /** Following ActivityStreams convention, default mediaType for content is "text/html"  */
    fun toContentStored(text: String?, inputMediaType: TextMediaType?, isHtmlContentAllowed: Boolean): String {
        if (text.isNullOrEmpty()) return ""
        val mediaType = if (inputMediaType == TextMediaType.UNKNOWN) calcTextMediaType(text) else inputMediaType
        val escaped: String?
        escaped = when (mediaType) {
            TextMediaType.HTML -> {
                if (isHtmlContentAllowed) return text
                Html.fromHtml(text, Html.FROM_HTML_MODE_COMPACT).toString()
            }
            TextMediaType.PLAIN -> escapeHtmlExceptLineBreaksAndSpace(text)
            TextMediaType.PLAIN_ESCAPED -> if (isHtmlContentAllowed || !hasHtmlMarkup(text)) text else escapeHtmlExceptLineBreaksAndSpace(text)
            else -> escapeHtmlExceptLineBreaksAndSpace(text)
        }

        // Maybe htmlify...
        return LINEBREAK_PATTERN.matcher(stripExcessiveLineBreaks(escaped)).replaceAll(LINEBREAK_HTML)
    }

    private fun calcTextMediaType(text: String?): TextMediaType {
        if (text.isNullOrEmpty()) return TextMediaType.PLAIN
        return if (hasHtmlMarkup(text)) TextMediaType.HTML else TextMediaType.PLAIN
    }

    fun fromContentStored(html: String?, outputMediaType: TextMediaType?): String {
        return if (html.isNullOrEmpty()) "" else when (outputMediaType) {
            TextMediaType.HTML -> html
            TextMediaType.PLAIN -> htmlToPlainText(html)
            TextMediaType.PLAIN_ESCAPED -> escapeHtmlExceptLineBreaksAndSpace(htmlToPlainText(html))
            else -> html
        }
    }

    fun getContentToSearch(html: String?): String {
        return normalizeWordsForSearch(htmlToCompactPlainText(html)).toLowerCase(Locale.ENGLISH)
    }

    /** Strips ALL markup from the String, including line breaks. And remove all whiteSpace  */
    fun htmlToCompactPlainText(html: String?): String {
        return SPACES_PATTERN.matcher(htmlToPlainText(html)).replaceAll(" ")
    }

    /** Strips ALL markup from the String, excluding line breaks  */
    fun htmlToPlainText(html: String?): String {
        if (html.isNullOrEmpty()) return ""
        val str0 = HTML_LINEBREAK_PATTERN.matcher(html).replaceAll("\n")
        val str1 = if (hasHtmlMarkup(str0)) Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString() else str0
        val str2 = unescapeHtml(str1)
        return stripExcessiveLineBreaks(str2)
    }

    private fun unescapeHtml(textEscaped: String?): String {
        return if (textEscaped.isNullOrEmpty()) ""
        else UNESCAPE_HTML.translate(textEscaped) // This is needed to avoid visible text truncation,
                // see https://github.com/andstatus/andstatus/issues/441
                .replace("<>".toRegex(), "< >")
    }

    private val UNESCAPE_HTML: CharSequenceTranslator = AggregateTranslator(
            LookupTranslator(*EntityArrays.BASIC_UNESCAPE()),
            LookupTranslator(*EntityArrays.ISO8859_1_UNESCAPE()),
            LookupTranslator(*EntityArrays.HTML40_EXTENDED_UNESCAPE()),
            LookupTranslator(*EntityArrays.APOS_UNESCAPE()),
            NumericEntityUnescaper()
    )

    private fun escapeHtmlExceptLineBreaksAndSpace(plainText: String?): String {
        return if (plainText.isNullOrEmpty()) "" else SPACE_ESCAPED_PATTERN.matcher(
                LINEBREAK_ESCAPED_PATTERN.matcher(Html.escapeHtml(plainText)).replaceAll(LINEBREAK_PLAIN)
        ).replaceAll(" ")
    }

    fun stripExcessiveLineBreaks(plainText: String?): String {
        return if (plainText.isNullOrEmpty()) {
            ""
        } else {
            var text2 = THREE_LINEBREAKS_PATTERN.matcher(plainText.trim { it <= ' ' }).replaceAll(DOUBLE_LINEBREAK_REPLACE).trim { it <= ' ' }
            while (text2.endsWith(LINEBREAK_PLAIN)) {
                text2 = text2.substring(0, text2.length - LINEBREAK_PLAIN.length).trim { it <= ' ' }
            }
            text2
        }
    }

    fun normalizeWordsForSearch(text: String?): String {
        return if (text.isNullOrEmpty()) {
            ""
        } else {
            MENTION_HASH_PREFIX_PATTERN.matcher(
                    PUNCTUATION_BEFORE_COMMA_PATTERN.matcher(
                            SPACES_FOR_SEARCH_PATTERN.matcher(
                                    ",$text,"
                            ).replaceAll(",")
                    ).replaceAll(",")
            ).replaceAll(",$2$1")
        }
    }

    /** Very simple method  */
    fun hasHtmlMarkup(text: String?): Boolean {
        return if (text.isNullOrEmpty()) false else text.contains("<") && text.contains(">")
    }
}