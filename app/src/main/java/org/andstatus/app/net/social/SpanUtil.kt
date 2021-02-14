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

package org.andstatus.app.net.social;

import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;

import androidx.annotation.NonNull;

import org.andstatus.app.data.TextMediaType;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.MyUrlSpan;
import org.andstatus.app.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.lang.Character.isLetterOrDigit;

public class SpanUtil {
    private static final int MIN_SPAN_LENGTH = 3;
    private static final int MIN_HASHTAG_LENGTH = 2;
    public static final SpannableString EMPTY = SpannableString.valueOf("");

    private SpanUtil() { /* Empty */ }

    public static class Region implements Comparable<Region> {
        final int start;
        final int end;
        public final CharSequence text;
        final Optional<MyUrlSpan> urlSpan;
        final Optional<Object> otherSpan;

        private Region(Spanned spannable, Object span) {
            int spanStart = spannable.getSpanStart(span);
            // Sometimes "@" is not included in the span
            start = spanStart > 0 &&
                "@#".indexOf(spannable.charAt(spanStart)) < 0 && "@#".indexOf(spannable.charAt(spanStart - 1)) >= 0
                    ? spanStart - 1
                    : spanStart;
            if (MyUrlSpan.class.isAssignableFrom(span.getClass())) {
                urlSpan = Optional.of((MyUrlSpan) span);
                otherSpan = Optional.empty();
            } else {
                urlSpan = Optional.empty();
                otherSpan = Optional.of(span);
            }
            end = spannable.getSpanEnd(span);
            text = spannable.subSequence(start, end);
        }

        private Region(Spanned spannable, int start, int end) {
            this.start = start;
            this.end = end;
            urlSpan = Optional.empty();
            otherSpan = Optional.empty();
            text = (start < end && spannable.length() >= end)
                    ? spannable.subSequence(start, end)
                    : "";
        }

        @Override
        public int compareTo(@NonNull Region o) {
            return Integer.compare(start, o.start);
        }

        @Override
        public String toString() {
            return "Region{" +
                    start + "-" + end +
                    " '" + text + "'" +
                    urlSpan.map(s -> ", " + s).orElse("") +
                    otherSpan.map(s -> ", otherSpan").orElse("") +
                    '}';
        }

        boolean isValid() {
            return urlSpan.isPresent() || otherSpan.isPresent() || (end - start >= MIN_SPAN_LENGTH);
        }
    }

    public static List<Region> regionsOf(Spanned spannable) {
        return Stream.concat(
                Arrays.stream(spannable.getSpans(0, spannable.length(), Object.class))
                    .map(span -> new Region(spannable, span)),
                Stream.of(new Region(spannable, spannable.length(), spannable.length() + 1)))
            .sorted()
            .reduce(
                new ArrayList<>(),
                (xs, region) -> {
                    Region prevRegion = new Region(spannable, xs.size() == 0 ? 0 : xs.get(xs.size() - 1).end, region.start);
                    if (prevRegion.isValid()) xs.add(prevRegion);
                    if (region.isValid()) xs.add(region);
                    return xs;
                },
                (xs1, xs2) -> {xs1.addAll(xs2); return xs1;});
    }

    public static Spannable textToSpannable(String text, TextMediaType mediaType, Audience audience) {
        return StringUtil.isEmpty(text)
                ? EMPTY
                : spansModifier(audience).apply(MyUrlSpan.toSpannable(
                    mediaType == TextMediaType.PLAIN ? text : MyHtml.prepareForView(text),
                    mediaType, true));
    }

    public static Function<Spannable, Spannable> spansModifier(Audience audience) {
        return spannable -> {
            regionsOf(spannable).forEach(modifySpansInRegion(spannable, audience));
            return spannable;
        };
    }

    @NonNull
    private static Consumer<Region> modifySpansInRegion(Spannable spannable, Audience audience) {
        return region -> {
            if (region.start >= spannable.length() || region.end > spannable.length()) return;

            String text = spannable.subSequence(region.start, region.end).toString();
            if (mentionAdded(spannable, audience, region, text)) return;

            hashTagAdded(spannable, audience, region, text);
        };
    }

    private static boolean mentionAdded(Spannable spannable, Audience audience, Region region, String text) {
        if (audience.hasNonSpecial() && text.contains("@")) {
            String upperText = text.toUpperCase();
            Actor mentionedByAtWebfingerID = audience.getNonSpecialActors().stream()
                    .filter(actor -> actor.isWebFingerIdValid() &&
                            upperText.contains("@" + actor.getWebFingerId().toUpperCase())).findAny().orElse(Actor.EMPTY);
            if (mentionedByAtWebfingerID.nonEmpty()) {
                return notesByActorSpanAdded(spannable, audience, region,
                        "@" + mentionedByAtWebfingerID.getWebFingerId(), mentionedByAtWebfingerID);
            } else {
                Actor mentionedByWebfingerID = audience.getNonSpecialActors().stream()
                        .filter(actor -> actor.isWebFingerIdValid() &&
                                upperText.contains(actor.getWebFingerId().toUpperCase())).findAny().orElse(Actor.EMPTY);
                if (mentionedByWebfingerID.nonEmpty()) {
                    return notesByActorSpanAdded(spannable, audience, region,
                            mentionedByWebfingerID.getWebFingerId(), mentionedByWebfingerID);
                } else {
                    Actor mentionedByUsername = audience.getNonSpecialActors().stream()
                            .filter(actor -> actor.isUsernameValid() &&
                                    upperText.contains("@" + actor.getUsername().toUpperCase())).findAny().orElse(Actor.EMPTY);
                    if (mentionedByUsername.nonEmpty()) {
                        return notesByActorSpanAdded(spannable, audience, region,
                                "@" + mentionedByUsername.getUsername(), mentionedByUsername);
                    }
                }
            }
        }
        return false;
    }

    private static boolean notesByActorSpanAdded(Spannable spannable, Audience audience, Region region, String stringFound, Actor actor) {
        return spanAdded(spannable, audience, region, stringFound, new MyUrlSpan.Data(Optional.of(actor), Optional.empty(), Optional.empty()));
    }

    private static boolean spanAdded(Spannable spannable, Audience audience, Region region, String stringFound, MyUrlSpan.Data spanData) {
        if (region.urlSpan.isPresent()) {
            spannable.removeSpan(region.urlSpan.get());
            spannable.setSpan(new MyUrlSpan(spanData), region.start, region.end, 0);
        } else if (region.otherSpan.isPresent()) {
            spannable.removeSpan(region.otherSpan.get());
            spannable.setSpan(new MyUrlSpan(spanData), region.start, region.end, 0);
        } else {
            int indInRegion = getIndInRegion(spannable, region, stringFound);
            if (indInRegion < 0) return false;

            int start2 = region.start + indInRegion;
            int start3 = start2 + stringFound.length();
            if (start3 > region.end + 1) return false;

            spannable.setSpan(new MyUrlSpan(spanData), start2, Math.min(start3, region.end), 0);
            if (indInRegion >= MIN_SPAN_LENGTH) {
                modifySpansInRegion(spannable, audience).accept(new Region(spannable, region.start, start2));
            }
            if (start3 + MIN_SPAN_LENGTH <= region.end) {
                modifySpansInRegion(spannable, audience).accept(new Region(spannable, start3, region.end));
            }
        }
        return true;
    }

    /** Case insensitive search */
    private static int getIndInRegion(Spannable spannable, Region region, String stringFound) {
        final String substr1 = spannable.subSequence(region.start, region.end).toString();
        int indInRegion = substr1.indexOf(stringFound);
        if (indInRegion >= 0) return indInRegion;

        final String foundUpper = stringFound.toUpperCase();
        int ind = 0;
        do {
            int ind2 = substr1.substring(ind).toUpperCase().indexOf(foundUpper);
            if (ind2 >= 0) return ind2 + ind;
            ind++;
        } while(ind + stringFound.length() < substr1.length());
        return -1;
    }

    /** As https://www.hashtags.org/definition/ shows, hashtags may have numbers only,
     *  and may contain one symbol only  */
    private static boolean hashTagAdded(Spannable spannable, Audience audience, Region region, String text) {
        int indStart = 0;
        String hashTag = "";
        do {
            int indTag = text.indexOf('#', indStart);
            if (indTag < 0) return false;

            hashTag = hashTagAt(text, indTag);
            indStart = indTag + 1;
        } while (hashTag.length() < MIN_HASHTAG_LENGTH);

        return spanAdded(spannable, audience, region, hashTag,
                new MyUrlSpan.Data(Optional.empty(), Optional.of(hashTag), Optional.empty()));
    }

    private static String hashTagAt(String text, int indStart) {
        if (indStart + 1 >= text.length() ||
            text.charAt(indStart) != '#' ||
            !isLetterOrDigit(text.charAt(indStart + 1)) ||
            (indStart > 0 && isLetterOrDigit(text.charAt(indStart - 1)))
        ) {
            return "";
        }
        int ind = indStart + 2;
        while (ind < text.length()) {
            final char c = text.charAt(ind);
            if (!isLetterOrDigit(c) && c != '_') break;
            ind++;
        }
        return text.substring(indStart, ind);
    }
}
