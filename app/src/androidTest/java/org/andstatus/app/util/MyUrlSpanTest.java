/*
 * Copyright (C) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
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

import androidx.viewpager.widget.ViewPager;
import android.text.SpannableString;
import android.text.style.URLSpan;
import android.widget.TextView;

import org.andstatus.app.HelpActivity;
import org.andstatus.app.R;
import org.andstatus.app.context.ActivityTest;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.TextMediaType;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/** See https://github.com/andstatus/andstatus/issues/300 */
public class MyUrlSpanTest extends ActivityTest<HelpActivity> {

    @Override
    protected Class<HelpActivity> getActivityClass() {
        return HelpActivity.class;
    }

    @Test
    public void testMyUrlSpan() {
        String text = "The string has malformed links "
                + MyUrlSpan.SOFT_HYPHEN + " "
                + "Details: /press-release/nasa-confirms-evidence-that-liquid-water-flows-on-today-s-mars/ #MarsAnnouncement"
                + " and this "
                + "feed://radio.example.org/archives.xml"
                + " two links";

        forOneString(text);
    }

    private void forOneString(final String text) {
        final String method = "forOneString";
        final ViewPager pager = getActivity().findViewById(R.id.help_flipper);
        assertTrue(pager != null);
        final TextView textView = getActivity().findViewById(R.id.splash_payoff_line);
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                pager.setCurrentItem(HelpActivity.PAGE_LOGO);
                MyUrlSpan.showText(textView, text, TextMediaType.UNKNOWN, true, false);
                if (SpannableString.class.isAssignableFrom(textView.getClass())) {
                    SpannableString spannable = (SpannableString) textView.getText();
                    URLSpan[] spans = spannable.getSpans(0, spannable.length(), URLSpan.class);
                    for (URLSpan span : spans) {
                        MyLog.i(this, "Clicking on: " + span.getURL());
                        span.onClick(textView);
                    }
                }
            }
        });
        DbUtils.waitMs(method, 1000);
    }

    @Test
    public void testSoftHyphen() {
        String text = MyUrlSpan.SOFT_HYPHEN;
        forOneString(text);
    }
}
