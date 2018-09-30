/*
 * Copyright (C) 2015-2018 yvolk (Yuri Volkov), http://yurivolkov.com
 * Copyright (C) 2015 CommonsWare, LLC
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

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.os.Parcel;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.text.Html;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.andstatus.app.R;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineType;

import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static android.text.Html.FROM_HTML_MODE_COMPACT;
import static java.util.stream.Collectors.joining;

/** Prevents ActivityNotFoundException for malformed links,
 * see https://github.com/andstatus/andstatus/issues/300
 * Based on http://commonsware.com/blog/2013/10/23/linkify-autolink-need-custom-urlspan.html  */
public class MyUrlSpan extends URLSpan {
    public static final MyUrlSpan EMPTY = new MyUrlSpan("");

    public static final String SOFT_HYPHEN = "\u00AD";
    public static final Spannable EMPTY_SPANNABLE = new SpannableString("");
    public static final String EMPTY_URL = "content://";

    public static final class Data {
        public final Optional<Actor> actor;
        public final Optional<String> searchQuery;
        private final Optional<String> url;

        public Data(Optional<Actor> actor, Optional<String> searchQuery, Optional<String> url) {
            this.actor = actor;
            this.searchQuery = searchQuery;
            this.url = url;
        }

        public String getURL() {
            return url.orElse(getTimeline().getClickUri().toString());
        }

        public Timeline getTimeline() {
            return searchQuery.map(s ->
                    MyContextHolder.get().timelines().get(TimelineType.SEARCH, 0, Origin.EMPTY, s))
                    .orElse(actor.map(a -> MyContextHolder.get().timelines().forUserAtHomeOrigin(TimelineType.SENT, a))
                            .orElse(Timeline.EMPTY));
        }

        @Override
        public String toString() {
            return "MyUrlSpan{" +
                    Stream.of(
                        actor.map(Actor::toString),
                        searchQuery.map(String::toString),
                        url.map(String::toString))
                            .filter(Optional::isPresent).map(Optional::get)
                            .collect(joining(", ")) +
                    '}';
        }
    }

    public final Data data;

    public static final Creator<MyUrlSpan> CREATOR = new Creator<MyUrlSpan>() {
        @Override
        public MyUrlSpan createFromParcel(Parcel in) {
            return new MyUrlSpan(in.readString());
        }

        @Override
        public MyUrlSpan[] newArray(int size) {
            return new MyUrlSpan[size];
        }
    };

    public MyUrlSpan(Data data) {
        super(EMPTY_URL);
        this.data = data;
    }

    public MyUrlSpan(String url) {
        super(url);
        this.data = new Data(Optional.empty(), Optional.empty(), Optional.of(url));
    }

    @Override
    public void onClick(@NonNull View widget) {
        try {
            super.onClick(widget);
        } catch (ActivityNotFoundException | SecurityException e) {
            MyLog.v(this, e);
            try {
                MyLog.i(this, "Malformed link:'" + getURL() + "', " + data);
                Context context = MyContextHolder.get().context();
                if (context != null) {
                    Toast.makeText(context, context.getText(R.string.malformed_link)
                                    + "\n URL:'" + getURL() + "'", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e2) {
                MyLog.d(this, "Couldn't show a toast", e2);
            }
        }
    }

    @Override
    public String getURL() {
        return data.getURL();
    }

    public static void showLabel(Activity activity, @IdRes int viewId, @StringRes int stringResId) {
        showText((TextView) activity.findViewById(viewId), activity.getText(stringResId).toString(), false, false);
    }

    public static void showText(Activity activity, @IdRes int viewId, String text, boolean linkify, boolean
            showIfEmpty) {
        showText((TextView) activity.findViewById(viewId), text, linkify, showIfEmpty);
    }

    public static void showText(View parentView, @IdRes int viewId, String text, boolean linkify, boolean showIfEmpty) {
        showText((TextView) parentView.findViewById(viewId), text, linkify, showIfEmpty);
    }

    public static void showText(TextView textView, String text, boolean linkify, boolean showIfEmpty) {
        showSpannable(textView, toSpannable(text, linkify), showIfEmpty);
    }

    public static void showSpannable(TextView textView, String text, Function<Spannable, Spannable> modifySpans) {
        showSpannable(textView, modifySpans.apply(toSpannable(text, true)), false);
    }

    public static void showSpannable(TextView textView, @NonNull Spannable spannable, boolean showIfEmpty) {
        if (textView == null) return;
        if (spannable.length() == 0) {
            textView.setText("");
            ViewUtils.showView(textView, showIfEmpty);
        } else {
            textView.setText(spannable);
            if (hasSpans(spannable)) {
                textView.setFocusable(true);
                textView.setFocusableInTouchMode(true);
                textView.setLinksClickable(true);
                setOnTouchListener(textView);
            }
            ViewUtils.showView(textView, true);
        }
    }

    public static Spannable toSpannable(String text, boolean linkify) {
        if (StringUtils.isEmpty(text)) return EMPTY_SPANNABLE;

        // Android 6 bug, see https://github.com/andstatus/andstatus/issues/334
        // Setting setMovementMethod to not null causes a crash if text is SOFT_HYPHEN only:
        if (text.contains(SOFT_HYPHEN)) {
            text = text.replace(SOFT_HYPHEN, "-");
        }
        Spannable spannable = MyHtml.hasHtmlMarkup(text)
                ? htmlToSpannable(text)
                : new SpannableString(text);
        if (linkify && !hasUrlSpans(spannable)) {
            Linkify.addLinks(spannable, Linkify.WEB_URLS);
        }
        fixUrlSpans(spannable);
        return spannable;
    }

    private static Spannable htmlToSpannable(String text) {
        final Spanned spanned = Html.fromHtml(text, FROM_HTML_MODE_COMPACT);
        return Spannable.class.isAssignableFrom(spanned.getClass())
                ? (Spannable) spanned
                : SpannableString.valueOf(spanned);
    }

    public static String getText(View parentView, @IdRes int viewId) {
        View view = parentView.findViewById(viewId);
        return view == null || !TextView.class.isAssignableFrom(view.getClass()) ? ""
                : ((TextView) view).getText().toString();
    }

    /**
     * Substitute for: textView.setMovementMethod(LinkMovementMethod.getInstance());
     * setMovementMethod intercepts click on a text part without links,
     * so we replace it with our own method.
     * Solution to have clickable both links and other text is found here:
     * http://stackoverflow.com/questions/7236840/android-textview-linkify-intercepts-with-parent-view-gestures
     * following an advice from here:
     * http://stackoverflow.com/questions/7515710/listview-onclick-event-doesnt-fire-with-linkified-email-address?rq=1
     */
    public static void setOnTouchListener(TextView textView) {
        textView.setMovementMethod(null);
        textView.setOnTouchListener((v, event) -> onTouchEvent(v, event));
    }

    private static boolean onTouchEvent(View view, MotionEvent event) {
        TextView widget = (TextView) view;
        Object text = widget.getText();
        if (text instanceof Spanned) {
            Spanned buffer = (Spanned) text;
            int action = event.getAction();
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_DOWN) {
                int x = (int) event.getX();
                int y = (int) event.getY();

                x -= widget.getTotalPaddingLeft();
                y -= widget.getTotalPaddingTop();

                x += widget.getScrollX();
                y += widget.getScrollY();

                Layout layout = widget.getLayout();
                int line = layout.getLineForVertical(y);
                int off = layout.getOffsetForHorizontal(line, x);

                ClickableSpan[] link = buffer.getSpans(off, off,
                        ClickableSpan.class);

                if (link.length > 0) {
                    if (action == MotionEvent.ACTION_UP) {
                        link[0].onClick(widget);
                    } else if (buffer instanceof Spannable) {
                            Selection.setSelection( (Spannable) buffer,
                                    buffer.getSpanStart(link[0]),
                                    buffer.getSpanEnd(link[0]));
                    }
                    return true;
                }
            }
        }
        return false;
    }


    private static boolean hasSpans (Spanned spanned) {
        if (spanned == null) return  false;

        Object[] spans = spanned.getSpans(0, spanned.length(), Object.class);
        return spans != null && spans.length > 0;
    }

    private static boolean hasUrlSpans (Spanned spanned) {
        if (spanned == null) return  false;

        URLSpan[] spans = spanned.getSpans(0, spanned.length(), URLSpan.class);
        return spans != null && spans.length > 0;
    }

    private static void fixUrlSpans(Spannable spannable) {
        URLSpan[] spans = spannable.getSpans(0, spannable.length(), URLSpan.class);
        for (URLSpan span : spans) {
            if (!MyUrlSpan.class.isAssignableFrom(span.getClass())) {
                int start = spannable.getSpanStart(span);
                int end = spannable.getSpanEnd(span);
                spannable.removeSpan(span);
                spannable.setSpan(new MyUrlSpan(span.getURL()), start, end, 0);
            }
        }
    }

    @Override
    public String toString() {
        return data.toString();
    }

    public static URLSpan[] getUrlSpans(View view) {
        if (view != null && TextView.class.isAssignableFrom(view.getClass())) {
            CharSequence text = ((TextView) view).getText();
            if (Spanned.class.isAssignableFrom(text.getClass())) {
                return ((Spanned) text).getSpans(0, text.length(), URLSpan.class);
            }
        }
        return new URLSpan[] {};
    }
}
