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
    private SpanUtil() { /* Empty */ }

    public static class Region implements Comparable<Region> {
        final int start;
        final int end;
        final Optional<Object> urlSpan;
        final Optional<Object> otherSpan;

        private Region(Spannable spannable, Object span) {
            if (MyUrlSpan.class.isAssignableFrom(span.getClass())) {
                int start = spannable.getSpanStart(span);
                if ( start > 0 && !spannable.subSequence(start, start+1).toString().equals("@")
                        && spannable.subSequence(start - 1, start).toString().equals("@")) {
                    // Sometimes "@" is not included in the span
                    start = start - 1;
                }
                this.start = start;
                urlSpan = Optional.of(span);
                otherSpan = Optional.empty();
            } else {
                this.start = spannable.getSpanStart(span);
                urlSpan = Optional.empty();
                otherSpan = Optional.of(span);
            }
            this.end = spannable.getSpanEnd(span);
        }

        private Region(int start, int end) {
            this.start = start;
            this.end = end;
            urlSpan = Optional.empty();
            otherSpan = Optional.empty();
        }

        @Override
        public int compareTo(@NonNull Region o) {
            return Integer.compare(start, o.start);
        }
    }

    private static List<Region> regionsOf(Spannable spannable) {
        return Stream.concat(
                Arrays.stream(spannable.getSpans(0, spannable.length(), Object.class))
                    .map(span -> new Region(spannable, span)),
                Stream.of(new Region(spannable.length(), spannable.length() + 1)))
            .sorted()
            .reduce(
                new ArrayList<Region>(),
                (xs, region) -> {
                        final int prevEnd = xs.size() == 0 ? 0 : xs.get(xs.size() - 1).end;
                        if (prevEnd < region.start) {
                            xs.add(new Region(prevEnd, region.start));
                        }
                        xs.add(region);
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
                Actor mentionedByAtWebfingerID = audience.getRecipients().stream()
                        .filter(actor -> actor.isWebFingerIdValid() &&
                                text.contains("@" + actor.getWebFingerId())).findAny().orElse(Actor.EMPTY);
                if (mentionedByAtWebfingerID.nonEmpty()) {
                    addNotesByActorSpan(spannable, audience, region,
                            "@" + mentionedByAtWebfingerID.getWebFingerId(), mentionedByAtWebfingerID);
                } else {
                    Actor mentionedByWebfingerID = audience.getRecipients().stream()
                            .filter(actor -> actor.isWebFingerIdValid() &&
                                    text.contains(actor.getWebFingerId())).findAny().orElse(Actor.EMPTY);
                    if (mentionedByWebfingerID.nonEmpty()) {
                        addNotesByActorSpan(spannable, audience, region,
                                mentionedByWebfingerID.getWebFingerId(), mentionedByWebfingerID);
                    } else {
                        Actor mentionedByUsername = audience.getRecipients().stream()
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
            spannable.removeSpan(region.urlSpan);
            spannable.setSpan(new MyUrlSpan(timeline.getClickUri().toString()), region.start, region.end, 0);
        } else if (region.otherSpan.isPresent()) {
            spannable.removeSpan(region.otherSpan);
            spannable.setSpan(new MyUrlSpan(timeline.getClickUri().toString()), region.start, region.end, 0);
        } else {
            int indInRegion = spannable.subSequence(region.start, region.end).toString().indexOf(stringFound);
            if (indInRegion >= 0) {
                int start2 = region.start + indInRegion;
                int start3 = start2 + stringFound.length();
                spannable.setSpan(new MyUrlSpan(timeline.getClickUri().toString()), start2, start3, 0);
                if (indInRegion > 2) {
                    modifySpansInRegion(spannable, audience).accept(new Region(region.start, start2));
                }
                if (start3 + 2 < region.end) {
                    modifySpansInRegion(spannable, audience).accept(new Region(start3, region.end));
                }
            }
        }
    }
}
