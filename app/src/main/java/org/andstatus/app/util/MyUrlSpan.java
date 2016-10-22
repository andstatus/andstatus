/**
 * Copyright (C) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
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
import android.text.Html;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.andstatus.app.R;
import org.andstatus.app.context.MyContextHolder;

/** Prevents ActivityNotFoundException for malformed links,
 * see https://github.com/andstatus/andstatus/issues/300
 * Based on http://commonsware.com/blog/2013/10/23/linkify-autolink-need-custom-urlspan.html  */
public class MyUrlSpan extends URLSpan {

    public static final String SOFT_HYPHEN = "\u00AD";

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

    private MyUrlSpan(String url) {
        super(url);
    }

    @Override
    public void onClick(@NonNull View widget) {
        try {
            super.onClick(widget);
        } catch (ActivityNotFoundException e) {
            MyLog.v(this, e);
            try {
                MyLog.i(this, "Malformed link:'" + getURL() + "'");
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

    public static void showText(Activity activity, @IdRes int viewId, String text, boolean linkify, boolean showIfEmpty) {
        showText((TextView) activity.findViewById(viewId), text, linkify, showIfEmpty);
    }

    public static void showText(View parentView, @IdRes int viewId, String text, boolean linkify, boolean showIfEmpty) {
        showText((TextView) parentView.findViewById(viewId), text, linkify, showIfEmpty);
    }

    public static void showText(TextView textView, String text, boolean linkify, boolean showIfEmpty) {
        if (textView == null) return;
        if (TextUtils.isEmpty(text)) {
            textView.setText("");
            showView(textView, showIfEmpty);
        } else {
            if (linkify) {
                textView.setFocusable(true);
                textView.setFocusableInTouchMode(true);
                textView.setLinksClickable(true);
            }
            // Android 6 bug, see https://github.com/andstatus/andstatus/issues/334
            // Setting setMovementMethod to not null causes a crash if text is SOFT_HYPHEN only:
            if (text.contains(SOFT_HYPHEN)) {
                text = text.replace(SOFT_HYPHEN, "-");
            }
            Spanned spanned = MyHtml.hasHtmlMarkup(text) ? Html.fromHtml(text) : new SpannableString(text);
            textView.setText(spanned);
            if (linkify && !hasUrlSpans(spanned)) {
                Linkify.addLinks(textView, Linkify.WEB_URLS);
            }
            fixUrlSpans(textView);
            if (linkify) {
                setOnTouchListener(textView);
            }
            showView(textView, true);
        }
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
        textView.setOnTouchListener(
                new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        return onTouchEvent(v, event);
                    }
                }
        );
    }

    private static boolean onTouchEvent(View view, MotionEvent event) {
        TextView widget = (TextView) view;
        Object text = widget.getText();
        if (text instanceof Spanned) {
            Spanned buffer = (Spanned) text;

            int action = event.getAction();

            if (action == MotionEvent.ACTION_UP
                    || action == MotionEvent.ACTION_DOWN) {
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

                if (link.length != 0) {
                    if (action == MotionEvent.ACTION_UP) {
                        link[0].onClick(widget);
                    } else if (action == MotionEvent.ACTION_DOWN) {
                        if (buffer instanceof Spannable) {
                            Selection.setSelection( (Spannable) buffer,
                                    buffer.getSpanStart(link[0]),
                                    buffer.getSpanEnd(link[0]));
                        }
                    }
                    return true;
                }
            }

        }

        return false;
    }

    /**
     * @return true if succeeded
     */
    public static boolean showView(View parentView, int viewId, boolean show) {
        return parentView != null &&
            showView(parentView.findViewById(viewId), show);
    }

    /**
     * @return true if succeeded
     */
    public static boolean showView(View view, boolean show) {
        boolean success = view != null;
        if (success) {
            if (show) {
                if (view.getVisibility() != View.VISIBLE) {
                    view.setVisibility(View.VISIBLE);
                }
            } else {
                if (view.getVisibility() != View.GONE) {
                    view.setVisibility(View.GONE);
                }
            }
        }
        return success;
    }

    private static boolean hasUrlSpans (Spanned spanned) {
        boolean has = false;
        if (spanned != null){
            URLSpan[] spans = spanned.getSpans(0, spanned.length(), URLSpan.class);
            has = spans != null && spans.length > 0;
        }
        return has;
    }

    private static void fixUrlSpans(TextView textView) {
        CharSequence text = textView.getText();
        SpannableString spannable = SpannableString.class.isAssignableFrom(text.getClass())
                ? (SpannableString) text : SpannableString.valueOf(text);
        URLSpan[] spans = spannable.getSpans(0, spannable.length(), URLSpan.class);
        for (URLSpan span : spans) {
            int start = spannable.getSpanStart(span);
            int end = spannable.getSpanEnd(span);
            spannable.removeSpan(span);
            spannable.setSpan(new MyUrlSpan(span.getURL()), start, end, 0);
        }
        textView.setText(spannable);
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
