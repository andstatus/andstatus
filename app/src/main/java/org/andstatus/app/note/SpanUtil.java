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

package org.andstatus.app.note;

import android.text.Spannable;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.MyUrlSpan;

import java.util.Arrays;
import java.util.function.Function;

public class SpanUtil {
    private SpanUtil() { /* Empty */ }

    public static <T extends BaseNoteViewItem<T>> Function<Spannable, Spannable> modifySpans(T item) {
        return spannable -> {
            Arrays.stream(spannable.getSpans(0, spannable.length(), MyUrlSpan.class)).forEach(span -> {
                int start = spannable.getSpanStart(span);
                if ( start > 0 && !spannable.subSequence(start, start+1).toString().equals("@")
                        && spannable.subSequence(start - 1, start).toString().equals("@")) {
                    // Sometimes "@" is not included in the span
                    start = start - 1;
                }
                int end = spannable.getSpanEnd(span);
                String text = spannable.subSequence(start, end).toString();
                if (text.contains("@")) {
                    Actor mentionedByAtWebfingerID = item.audience.getRecipients().stream()
                            .filter(actor -> text.contains("@" + actor.getWebFingerId())).findAny().orElse(Actor.EMPTY);
                    if (mentionedByAtWebfingerID.nonEmpty()) {
                        spanNotesByActor(spannable, span, start, mentionedByAtWebfingerID);
                    } else {
                        Actor mentionedByWebfingerID = item.audience.getRecipients().stream()
                                .filter(actor -> actor.isWebFingerIdValid() && text.contains(actor.getWebFingerId())).findAny().orElse(Actor.EMPTY);
                        if (mentionedByWebfingerID.nonEmpty()) {
                            spanNotesByActor(spannable, span, start, mentionedByWebfingerID);
                        } else {
                            Actor mentionedByUsername = item.audience.getRecipients().stream()
                                    .filter(actor -> text.contains("@" + actor.getUsername())).findAny().orElse(Actor.EMPTY);
                            if (mentionedByUsername.nonEmpty()) {
                                spanNotesByActor(spannable, span, start, mentionedByUsername);
                            }
                        }
                    }
                }
            });
            return spannable;
        };
    }

    private static void spanNotesByActor(Spannable spannable, MyUrlSpan span, int start, Actor actor) {
        Timeline timeline = MyContextHolder.get().timelines().forUserAtHomeOrigin(TimelineType.SENT, actor);
        int end = spannable.getSpanEnd(span);
        spannable.removeSpan(span);
        spannable.setSpan(new MyUrlSpan(timeline.getClickUri().toString()), start, end, 0);
    }
}
