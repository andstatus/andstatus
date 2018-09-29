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

import android.support.annotation.NonNull;
import android.text.Spannable;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.MyUrlSpan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

public class SpanUtil {
    private static final int MIN_SPAN_LENGTH = 3;

    private SpanUtil() { /* Empty */ }

    public static class Region implements Comparable<Region> {
        final int start;
        final int end;
        final CharSequence text;
        final Optional<MyUrlSpan> urlSpan;
        final Optional<Object> otherSpan;

        private Region(Spannable spannable, Object span) {
            int spanStart = spannable.getSpanStart(span);
            // Sometimes "@" is not included in the span
            start = spanStart > 0 && spannable.charAt(spanStart) != '@' && spannable.charAt(spanStart - 1) == '@'
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

        private Region(Spannable spannable, int start, int end) {
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

    public static List<Region> regionsOf(Spannable spannable) {
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
            if (text.contains("@")) {
                Actor mentionedByAtWebfingerID = audience.getActors().stream()
                        .filter(actor -> actor.isWebFingerIdValid() &&
                                text.contains("@" + actor.getWebFingerId())).findAny().orElse(Actor.EMPTY);
                if (mentionedByAtWebfingerID.nonEmpty()) {
                    addNotesByActorSpan(spannable, audience, region,
                            "@" + mentionedByAtWebfingerID.getWebFingerId(), mentionedByAtWebfingerID);
                } else {
                    Actor mentionedByWebfingerID = audience.getActors().stream()
                            .filter(actor -> actor.isWebFingerIdValid() &&
                                    text.contains(actor.getWebFingerId())).findAny().orElse(Actor.EMPTY);
                    if (mentionedByWebfingerID.nonEmpty()) {
                        addNotesByActorSpan(spannable, audience, region,
                                mentionedByWebfingerID.getWebFingerId(), mentionedByWebfingerID);
                    } else {
                        Actor mentionedByUsername = audience.getActors().stream()
                                .filter(actor -> actor.isUsernameValid() &&
                                        text.contains("@" + actor.getUsername())).findAny().orElse(Actor.EMPTY);
                        if (mentionedByUsername.nonEmpty()) {
                            addNotesByActorSpan(spannable, audience, region,
                                    "@" + mentionedByUsername.getUsername(), mentionedByUsername);
                        }
                    }
                }
            }
        };
    }

    private static void addNotesByActorSpan(Spannable spannable, Audience audience, Region region, String stringFound, Actor actor) {
        Timeline timeline = MyContextHolder.get().timelines().forUserAtHomeOrigin(TimelineType.SENT, actor);
        if (region.urlSpan.isPresent()) {
            spannable.removeSpan(region.urlSpan.get());
            spannable.setSpan(new MyUrlSpan(timeline.getClickUri().toString()), region.start, region.end, 0);
        } else if (region.otherSpan.isPresent()) {
            spannable.removeSpan(region.otherSpan.get());
            spannable.setSpan(new MyUrlSpan(timeline.getClickUri().toString()), region.start, region.end, 0);
        } else {
            int indInRegion = spannable.subSequence(region.start, region.end).toString().indexOf(stringFound);
            if (indInRegion >= 0) {
                int start2 = region.start + indInRegion;
                int start3 = start2 + stringFound.length();
                spannable.setSpan(new MyUrlSpan(timeline.getClickUri().toString()), start2, start3, 0);
                if (indInRegion >= MIN_SPAN_LENGTH) {
                    modifySpansInRegion(spannable, audience).accept(new Region(spannable, region.start, start2));
                }
                if (start3 + MIN_SPAN_LENGTH <= region.end) {
                    modifySpansInRegion(spannable, audience).accept(new Region(spannable, start3, region.end));
                }
            }
        }
    }
}
