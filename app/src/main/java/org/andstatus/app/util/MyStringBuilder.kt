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
package org.andstatus.app.util

import java.util.*

/** Adds convenience methods to [StringBuilder]  */
class MyStringBuilder constructor(val builder: StringBuilder = StringBuilder()) : CharSequence, IsEmpty {
    private constructor(text: CharSequence) : this(StringBuilder(text))

    fun <T> withCommaNonEmpty(label: CharSequence?, obj: T?): MyStringBuilder {
        return withComma(label, obj, { it -> nonEmptyObj(it) })
    }

    fun <T> withComma(label: CharSequence?, obj: T?, predicate: (T) -> Boolean): MyStringBuilder {
        return if (obj == null || !predicate(obj)) this else withComma(label, obj)
    }

    fun withComma(label: CharSequence?, obj: Any?, filter: () -> Boolean): MyStringBuilder {
        return if (obj == null || !filter()) this else withComma(label, obj)
    }

    fun withComma(label: CharSequence?, obj: Any?): MyStringBuilder {
        return append(label, obj, ", ", false)
    }

    fun withCommaQuoted(label: CharSequence?, obj: Any?, quoted: Boolean): MyStringBuilder {
        return append(label, obj, ", ", quoted)
    }

    fun withComma(text: CharSequence?): MyStringBuilder {
        return append("", text, ", ", false)
    }

    fun withSpaceQuoted(text: CharSequence?): MyStringBuilder {
        return append("", text, " ", true)
    }

    fun withSpace(text: CharSequence?): MyStringBuilder {
        return append("", text, " ", false)
    }

    fun atNewLine(label: CharSequence?, text: CharSequence?): MyStringBuilder {
        return append(label, text, ", \n", false)
    }

    fun atNewLine(text: CharSequence?): MyStringBuilder {
        return append("", text, ", \n", false)
    }

    fun append(label: CharSequence?, obj: Any?, separator: String, quoted: Boolean): MyStringBuilder {
        if (obj == null) return this
        val text = obj.toString()
        if (text.isEmpty()) return this
        if (builder.isNotEmpty()) builder.append(separator)
        if (!label.isNullOrEmpty()) builder.append(label).append(": ")
        if (quoted) builder.append("\"")
        builder.append(text)
        if (quoted) builder.append("\"")
        return this
    }

    fun append(text: CharSequence?): MyStringBuilder {
        if (!text.isNullOrEmpty()) {
            builder.append(text)
        }
        return this
    }

    override val length: Int
        get() = builder.length

    override fun get(index: Int): Char = builder[index]

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        return builder.subSequence(startIndex, endIndex)
    }

    override fun toString(): String {
        return builder.toString()
    }

    fun prependWithSeparator(text: CharSequence, separator: String): MyStringBuilder {
        if (text.isNotEmpty()) {
            builder.insert(0, separator)
            builder.insert(0, text)
        }
        return this
    }

    fun apply(unaryOperator: (MyStringBuilder) -> MyStringBuilder): MyStringBuilder {
        return unaryOperator(this)
    }

    override val isEmpty: Boolean
        get() {
            return length == 0
        }

    fun toKeyValue(key: Any): String {
        return formatKeyValue(key, toString())
    }

    companion object {
        const val COMMA: String = ","
        fun of(text: CharSequence?): MyStringBuilder {
            return MyStringBuilder(text ?: "")
        }

        fun of(content: Optional<String>): MyStringBuilder {
            return content.map { text: String -> of(text) }.orElse(MyStringBuilder())
        }

        fun formatKeyValue(keyIn: Any?, valueIn: Any?): String {
            val key = objToTag(keyIn)
            if (keyIn == null) {
                return key
            }
            var value = "null"
            if (valueIn != null) {
                value = valueIn.toString()
            }
            return formatKeyValue(key, value)
        }

        /** Strips value from leading and trailing commas  */
        fun formatKeyValue(key: Any?, value: String?): String {
            var out = ""
            if (!value.isNullOrEmpty()) {
                out = value.trim { it <= ' ' }
                if (out.substring(0, 1) == COMMA) {
                    out = out.substring(1)
                }
                val ind = out.lastIndexOf(COMMA)
                if (ind > 0 && ind == out.length - 1) {
                    out = out.substring(0, ind)
                }
            }
            return objToTag(key) + ":{" + out + "}"
        }

        fun objToTag(objTag: Any?): String {
            val tag: String = when (objTag) {
                null -> "(null)"
                is IdentifiableInstance -> objTag.instanceTag()
                is TaggedClass -> objTag.classTag()
                is String -> objTag
                is Enum<*> -> objTag.toString()
                is Class<*> -> objTag.simpleName
                else -> objTag.javaClass.simpleName
            }
            return if (tag.trim { it <= ' ' }.isEmpty()) {
                "(empty)"
            } else tag
        }

        private fun <T> nonEmptyObj(obj: T?): Boolean {
            return !isEmptyObj(obj)
        }

        private fun <T> isEmptyObj(obj: T?): Boolean = when (obj) {
            null -> true
            is IsEmpty -> obj.isEmpty
            is Number -> obj.toLong() == 0L
            is String -> obj.isEmpty()
            else -> false
        }

        fun appendWithSpace(builder: StringBuilder, text: CharSequence?): StringBuilder {
            return MyStringBuilder(builder).withSpace(text).builder
        }
    }
}