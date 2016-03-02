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

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.support.annotation.NonNull;
import android.text.Html;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.text.util.Linkify;
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

    private MyUrlSpan(String url) {
        super(url);
    }

    @Override
    public void onClick(@NonNull View widget) {
        try {
            super.onClick(widget);
        } catch (ActivityNotFoundException e) {
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

    public static void showText(View parentView, int viewId, String text, boolean linkify) {
        TextView textView = (TextView) parentView.findViewById(viewId);
        if (textView != null) {
            showText(textView, text, linkify);
        }
    }

    public static void showText(TextView textView, String text, boolean linkify) {
        if (TextUtils.isEmpty(text)) {
            textView.setText("");
            if (textView.getVisibility() != View.GONE) {
                textView.setVisibility(View.GONE);
            }
        } else {
            if (linkify) {
                textView.setFocusable(true);
                textView.setFocusableInTouchMode(true);
                textView.setLinksClickable(true);
                textView.setMovementMethod(LinkMovementMethod.getInstance());
            }
            // Android 6 bug, see https://github.com/andstatus/andstatus/issues/334
            // Setting setMovementMethod to not null causes a crash if text is SOFT_HYPHEN only:
            if (text.contains(SOFT_HYPHEN)) {
                text = text.replace(SOFT_HYPHEN, "-");
            }
            Spanned spanned = Html.fromHtml(text);
            textView.setText(spanned);
            if (linkify && !hasUrlSpans(spanned)) {
                Linkify.addLinks(textView, Linkify.ALL);
            }
            fixUrlSpans(textView);

            if (textView.getVisibility() != View.VISIBLE) {
                textView.setVisibility(View.VISIBLE);
            }
        }
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
        URLSpan[] spans= spannable.getSpans(0, spannable.length(), URLSpan.class);
        for (URLSpan span : spans) {
            int start=spannable.getSpanStart(span);
            int end=spannable.getSpanEnd(span);
            spannable.removeSpan(span);
            spannable.setSpan(new MyUrlSpan(span.getURL()), start, end, 0);
        }
    }
}
