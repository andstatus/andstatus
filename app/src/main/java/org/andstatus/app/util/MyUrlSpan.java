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
import android.text.SpannableString;
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
    public MyUrlSpan(String url) {
        super(url);
    }

    @Override
    public void onClick(View widget) {
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

    public static final void addLinks(TextView textView) {
        Linkify.addLinks(textView, Linkify.ALL);
        fixUrlSpans(textView);
    }

    private static void fixUrlSpans(TextView textView) {
        SpannableString spannable=(SpannableString)textView.getText();
        URLSpan[] spans= spannable.getSpans(0, spannable.length(), URLSpan.class);
        for (URLSpan span : spans) {
            int start=spannable.getSpanStart(span);
            int end=spannable.getSpanEnd(span);
            spannable.removeSpan(span);
            spannable.setSpan(new MyUrlSpan(span.getURL()), start, end, 0);
        }
    }
}
