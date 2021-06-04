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
package org.andstatus.app.note

import org.andstatus.app.util.IsEmpty
import org.andstatus.app.util.MyHtml
import org.andstatus.app.util.StringUtil
import java.util.*

class KeywordsFilter(keywordsIn: String?) : IsEmpty {
    internal class Keyword @JvmOverloads constructor(val value: String, val contains: Boolean = false) {
        val nonEmpty: Boolean

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || javaClass != other.javaClass) return false
            val keyword = other as Keyword
            return contains == keyword.contains && value == keyword.value
        }

        override fun hashCode(): Int {
            return Objects.hash(value, contains)
        }

        override fun toString(): String {
            return "{" + (if (contains) CONTAINS_PREFIX else "") + value + "}"
        }

        init {
            nonEmpty = value.isNotEmpty()
        }
    }

    internal val keywordsToFilter: MutableList<Keyword>
    private val keywordsRaw: MutableList<String>
    private fun parseFilterString(text: String?): MutableList<String> {
        val keywords: MutableList<String> = ArrayList()
        if (text.isNullOrEmpty()) {
            return keywords
        }
        var inQuote = false
        var atPos = 0
        while (atPos < text.length) {
            val separatorInd = if (inQuote) nextQuote(text, atPos) else nextSeparatorInd(text, atPos)
            if (atPos > separatorInd) {
                break
            }
            val item = text.substring(atPos, separatorInd)
            if (item.isNotEmpty() && !keywords.contains(item)) {
                keywords.add(item)
            }
            if (separatorInd < text.length && text[separatorInd] == '"') {
                inQuote = !inQuote
            }
            atPos = separatorInd + 1
        }
        return keywords
    }

    private fun nextQuote(text: String, atPos: Int): Int {
        for (ind in atPos until text.length) {
            if (DOUBLE_QUOTE == text[ind]) {
                return ind
            }
        }
        return text.length
    }

    private fun nextSeparatorInd(text: String, atPos: Int): Int {
        val SEPARATORS = ", $DOUBLE_QUOTE"
        for (ind in atPos until text.length) {
            if (SEPARATORS.indexOf(text[ind]) >= 0) {
                return ind
            }
        }
        return text.length
    }

    private fun rawToActual(keywordsRaw: MutableList<String>): MutableList<Keyword> {
        val keywords: MutableList<Keyword> = ArrayList()
        for (itemRaw in keywordsRaw) {
            val contains = itemRaw.startsWith(CONTAINS_PREFIX)
            val contentToSearch = MyHtml.getContentToSearch(if (contains) itemRaw.substring(CONTAINS_PREFIX.length) else itemRaw)
            val withContains = if (contains && contentToSearch.length > 2) contentToSearch.substring(1, contentToSearch.length - 1) else contentToSearch
            val item = Keyword(withContains, contains)
            if (item.nonEmpty && !keywords.contains(item)) {
                keywords.add(item)
            }
        }
        return keywords
    }

    fun matchedAny(s: String?): Boolean {
        if (keywordsToFilter.isEmpty() || s.isNullOrEmpty()) {
            return false
        }
        for (keyword in keywordsToFilter) {
            if (s.contains(keyword.value)) {
                return true
            }
        }
        return false
    }

    fun matchedAll(s: String?): Boolean {
        if (keywordsToFilter.isEmpty() || s.isNullOrEmpty()) {
            return false
        }
        for (keyword in keywordsToFilter) {
            if (!s.contains(keyword.value)) {
                return false
            }
        }
        return true
    }

    fun getSqlSelection(fieldName: String?): String {
        if (isEmpty) {
            return ""
        }
        val selection = StringBuilder()
        for (ind in keywordsToFilter.indices) {
            if (ind > 0) {
                selection.append(" AND ")
            }
            selection.append("$fieldName LIKE ?")
        }
        return if (selection.isEmpty()) "" else "($selection)"
    }

    fun prependSqlSelectionArgs(selectionArgs: Array<String>): Array<String> {
        var selectionArgsOut = selectionArgs
        for (keyword in keywordsToFilter) {
            selectionArgsOut = StringUtil.addBeforeArray(selectionArgsOut, "%" + keyword.value + "%")
        }
        return selectionArgsOut
    }

    fun getFirstTagOrFirstKeyword(): String {
        for (keyword in keywordsRaw) {
            if (keyword.startsWith("#")) {
                return keyword.substring(1)
            }
        }
        return if (keywordsRaw.isEmpty()) "" else keywordsRaw[0]
    }

    override val isEmpty: Boolean
        get() {
            return keywordsToFilter.isEmpty()
        }

    override fun toString(): String {
        val builder = StringBuilder()
        for (keyword in keywordsRaw) {
            if (builder.isNotEmpty()) {
                builder.append(", ")
            }
            builder.append("\"" + keyword + "\"")
        }
        return builder.toString()
    }

    companion object {
        val CONTAINS_PREFIX: String = "contains:"
        private const val DOUBLE_QUOTE = '"'
    }

    init {
        keywordsRaw = parseFilterString(keywordsIn)
        keywordsToFilter = rawToActual(keywordsRaw)
    }
}
