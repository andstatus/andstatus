/*
 * Copyright (c) 2018 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import org.andstatus.app.data.TextMediaType
import org.andstatus.app.util.MyHtml
import org.andstatus.app.util.MyUrlSpan
import java.util.*
import java.util.function.Consumer
import java.util.function.Function
import java.util.stream.Stream

object SpanUtil {
    private const val MIN_SPAN_LENGTH = 3
    private const val MIN_HASHTAG_LENGTH = 2
    val EMPTY = SpannableString.valueOf("")
    fun regionsOf(spannable: Spanned?): MutableList<Region?>? {
        return Stream.concat(
                Arrays.stream(spannable.getSpans(0, spannable.length, Any::class.java))
                        .map { span: Any? -> Region(spannable, span) },
                Stream.of(Region(spannable, spannable.length, spannable.length + 1)))
                .sorted()
                .reduce(
                        ArrayList(),
                        { xs: ArrayList<Region?>?, region: Region? ->
                            val prevRegion = Region(spannable, if (xs.size == 0) 0 else xs.get(xs.size - 1).end, region.start)
                            if (prevRegion.isValid()) xs.add(prevRegion)
                            if (region.isValid()) xs.add(region)
                            xs
                        },
                        { xs1: ArrayList<Region?>?, xs2: ArrayList<Region?>? ->
                            xs1.addAll(xs2)
                            xs1
                        })
    }

    fun textToSpannable(text: String?, mediaType: TextMediaType?, audience: Audience?): Spannable? {
        return if (text.isNullOrEmpty()) EMPTY else spansModifier(audience).apply(MyUrlSpan.Companion.toSpannable(
                if (mediaType == TextMediaType.PLAIN) text else MyHtml.prepareForView(text),
                mediaType, true))
    }

    fun spansModifier(audience: Audience?): Function<Spannable?, Spannable?>? {
        return Function { spannable: Spannable? ->
            regionsOf(spannable).forEach(modifySpansInRegion(spannable, audience))
            spannable
        }
    }

    private fun modifySpansInRegion(spannable: Spannable?, audience: Audience?): Consumer<Region?> {
        return label@ Consumer { region: Region? ->
            if (region.start >= spannable.length || region.end > spannable.length) return@label
            val text = spannable.subSequence(region.start, region.end).toString()
            if (mentionAdded(spannable, audience, region, text)) return@label
            hashTagAdded(spannable, audience, region, text)
        }
    }

    private fun mentionAdded(spannable: Spannable?, audience: Audience?, region: Region?, text: String?): Boolean {
        if (audience.hasNonSpecial() && text.contains("@")) {
            val upperText = text.toUpperCase()
            val mentionedByAtWebfingerID = audience.getNonSpecialActors().stream()
                    .filter { actor: Actor? ->
                        actor.isWebFingerIdValid() &&
                                upperText.contains("@" + actor.getWebFingerId().toUpperCase())
                    }.findAny().orElse(Actor.Companion.EMPTY)
            if (mentionedByAtWebfingerID.nonEmpty()) {
                return notesByActorSpanAdded(spannable, audience, region,
                        "@" + mentionedByAtWebfingerID.webFingerId, mentionedByAtWebfingerID)
            } else {
                val mentionedByWebfingerID = audience.getNonSpecialActors().stream()
                        .filter { actor: Actor? ->
                            actor.isWebFingerIdValid() &&
                                    upperText.contains(actor.getWebFingerId().toUpperCase())
                        }.findAny().orElse(Actor.Companion.EMPTY)
                if (mentionedByWebfingerID.nonEmpty()) {
                    return notesByActorSpanAdded(spannable, audience, region,
                            mentionedByWebfingerID.webFingerId, mentionedByWebfingerID)
                } else {
                    val mentionedByUsername = audience.getNonSpecialActors().stream()
                            .filter { actor: Actor? ->
                                actor.isUsernameValid() &&
                                        upperText.contains("@" + actor.getUsername().toUpperCase())
                            }.findAny().orElse(Actor.Companion.EMPTY)
                    if (mentionedByUsername.nonEmpty()) {
                        return notesByActorSpanAdded(spannable, audience, region,
                                "@" + mentionedByUsername.username, mentionedByUsername)
                    }
                }
            }
        }
        return false
    }

    private fun notesByActorSpanAdded(spannable: Spannable?, audience: Audience?, region: Region?, stringFound: String?, actor: Actor?): Boolean {
        return spanAdded(spannable, audience, region, stringFound, MyUrlSpan.Data(Optional.of(actor), Optional.empty(), Optional.empty()))
    }

    private fun spanAdded(spannable: Spannable?, audience: Audience?, region: Region?, stringFound: String?, spanData: MyUrlSpan.Data?): Boolean {
        if (region.urlSpan.isPresent()) {
            spannable.removeSpan(region.urlSpan.get())
            spannable.setSpan(MyUrlSpan(spanData), region.start, region.end, 0)
        } else if (region.otherSpan.isPresent()) {
            spannable.removeSpan(region.otherSpan.get())
            spannable.setSpan(MyUrlSpan(spanData), region.start, region.end, 0)
        } else {
            val indInRegion = getIndInRegion(spannable, region, stringFound)
            if (indInRegion < 0) return false
            val start2 = region.start + indInRegion
            val start3 = start2 + stringFound.length
            if (start3 > region.end + 1) return false
            spannable.setSpan(MyUrlSpan(spanData), start2, Math.min(start3, region.end), 0)
            if (indInRegion >= MIN_SPAN_LENGTH) {
                modifySpansInRegion(spannable, audience).accept(Region(spannable, region.start, start2))
            }
            if (start3 + MIN_SPAN_LENGTH <= region.end) {
                modifySpansInRegion(spannable, audience).accept(Region(spannable, start3, region.end))
            }
        }
        return true
    }

    /** Case insensitive search  */
    private fun getIndInRegion(spannable: Spannable?, region: Region?, stringFound: String?): Int {
        val substr1 = spannable.subSequence(region.start, region.end).toString()
        val indInRegion = substr1.indexOf(stringFound)
        if (indInRegion >= 0) return indInRegion
        val foundUpper = stringFound.toUpperCase()
        var ind = 0
        do {
            val ind2 = substr1.substring(ind).toUpperCase().indexOf(foundUpper)
            if (ind2 >= 0) return ind2 + ind
            ind++
        } while (ind + stringFound.length < substr1.length)
        return -1
    }

    /** As https://www.hashtags.org/definition/ shows, hashtags may have numbers only,
     * and may contain one symbol only   */
    private fun hashTagAdded(spannable: Spannable?, audience: Audience?, region: Region?, text: String?): Boolean {
        var indStart = 0
        var hashTag: String? = ""
        do {
            val indTag = text.indexOf('#', indStart)
            if (indTag < 0) return false
            hashTag = hashTagAt(text, indTag)
            indStart = indTag + 1
        } while (hashTag.length < MIN_HASHTAG_LENGTH)
        return spanAdded(spannable, audience, region, hashTag,
                MyUrlSpan.Data(Optional.empty(), Optional.of(hashTag), Optional.empty()))
    }

    private fun hashTagAt(text: String?, indStart: Int): String? {
        if (indStart + 1 >= text.length || text.get(indStart) != '#' ||
                !Character.isLetterOrDigit(text.get(indStart + 1)) ||
                indStart > 0 && Character.isLetterOrDigit(text.get(indStart - 1))) {
            return ""
        }
        var ind = indStart + 2
        while (ind < text.length) {
            val c = text.get(ind)
            if (!Character.isLetterOrDigit(c) && c != '_') break
            ind++
        }
        return text.substring(indStart, ind)
    }

    class Region : Comparable<Region?> {
        val start: Int
        val end: Int
        val text: CharSequence?
        val urlSpan: Optional<MyUrlSpan?>?
        val otherSpan: Optional<Any?>?

        private constructor(spannable: Spanned?, span: Any?) {
            val spanStart = spannable.getSpanStart(span)
            // Sometimes "@" is not included in the span
            start = if (spanStart > 0 && "@#".indexOf(spannable.get(spanStart)) < 0 && "@#".indexOf(spannable.get(spanStart - 1)) >= 0) spanStart - 1 else spanStart
            if (MyUrlSpan::class.java.isAssignableFrom(span.javaClass)) {
                urlSpan = Optional.of(span as MyUrlSpan?)
                otherSpan = Optional.empty()
            } else {
                urlSpan = Optional.empty()
                otherSpan = Optional.of(span)
            }
            end = spannable.getSpanEnd(span)
            text = spannable.subSequence(start, end)
        }

        private constructor(spannable: Spanned?, start: Int, end: Int) {
            this.start = start
            this.end = end
            urlSpan = Optional.empty()
            otherSpan = Optional.empty()
            text = if (start < end && spannable.length >= end) spannable.subSequence(start, end) else ""
        }

        override operator fun compareTo(o: Region): Int {
            return Integer.compare(start, o.start)
        }

        override fun toString(): String {
            return "Region{" +
                    start + "-" + end +
                    " '" + text + "'" +
                    urlSpan.map(Function { s: MyUrlSpan? -> ", $s" }).orElse("") +
                    otherSpan.map(Function { s: Any? -> ", otherSpan" }).orElse("") +
                    '}'
        }

        fun isValid(): Boolean {
            return urlSpan.isPresent() || otherSpan.isPresent() || end - start >= MIN_SPAN_LENGTH
        }
    }
}