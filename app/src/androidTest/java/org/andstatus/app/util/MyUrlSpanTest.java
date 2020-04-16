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

import android.text.SpannedString;
import android.text.style.URLSpan;
import android.widget.TextView;

import androidx.viewpager.widget.ViewPager;

import org.andstatus.app.HelpActivity;
import org.andstatus.app.R;
import org.andstatus.app.context.ActivityTest;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.TextMediaType;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.Audience;
import org.andstatus.app.net.social.SpanUtil;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import io.vavr.control.Try;

import static org.andstatus.app.context.DemoData.demoData;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/** See https://github.com/andstatus/andstatus/issues/300 */
public class MyUrlSpanTest extends ActivityTest<HelpActivity> {

    @Override
    protected Class<HelpActivity> getActivityClass() {
        return HelpActivity.class;
    }

    @Before
    public void setUp() {
        TestSuite.initializeWithAccounts(this);
        getActivity();
        getInstrumentation().waitForIdleSync();
    }

    @Test
    public void testMyUrlSpan() {
        String text = "The string has malformed links "
                + MyUrlSpan.SOFT_HYPHEN + " "
                + "Details: /press-release/nasa-confirms-evidence-that-liquid-water-flows-on-today-s-mars/ #MarsAnnouncement"
                + " and this "
                + "feed://radio.example.org/archives.xml"
                + " two links";

        forOneString(text, Audience.EMPTY, spannedString -> TryUtils.SUCCESS);

        String part0 = "A post to ";
        String part1 = "@AndStatus@pleroma.site";
        Audience audience2 = new Audience(demoData.getAccountActorByOid(demoData.activityPubTestAccountActorOid).origin);
        Actor actor2 = Actor.fromOid(audience2.origin, "https://pleroma.site/users/AndStatus");
        actor2.setUsername("AndStatus");
        actor2.setProfileUrl(actor2.oid);
        actor2.setWebFingerId("andstatus@pleroma.site");
        audience2.add(actor2);
        forOneString(part0 + part1, audience2, spannedString -> {
            List<SpanUtil.Region> regions = SpanUtil.regionsOf(spannedString);
            if (regions.size() != 2) return TryUtils.failure("Two regions expected " + regions);
            if (!part0.equals(regions.get(0).text.toString())) {
                return TryUtils.failure("Region(0) is wrong. " + regions);
            }
            if (!part1.equals(regions.get(1).text.toString())) {
                return TryUtils.failure("Region(1) is wrong. " + regions);
            }
            return TryUtils.SUCCESS;
        }).onFailure(t -> fail(t.getMessage()));
    }

    private Try<Void> forOneString(final String text, final Audience audience,
                                   Function<SpannedString, Try<Void>> spannedStringChecker) {
        final String method = "forOneString";
        DbUtils.waitMs(method, 1000);
        final ViewPager pager = getActivity().findViewById(R.id.help_flipper);
        assertNotNull(pager);
        final TextView textView = getActivity().findViewById(R.id.splash_payoff_line);
        AtomicReference<Try<Void>> result = new AtomicReference<>(TryUtils.notFound());
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                pager.setCurrentItem(HelpActivity.PAGE_LOGO);
                MyUrlSpan.showSpannable(textView,
                        SpanUtil.textToSpannable(text, TextMediaType.UNKNOWN, audience), false);
                CharSequence text1 = textView.getText();
                if (text1 instanceof SpannedString) {
                    SpannedString str = (SpannedString) text1;
                    result.set(spannedStringChecker.apply(str));
                    URLSpan[] spans = str.getSpans(0, str.length(), URLSpan.class);
                    for (URLSpan span : spans) {
                        MyLog.i(this, "Clicking on: " + span.getURL());
                        span.onClick(textView);
                    }
                }
            }
        });
        DbUtils.waitMs(method, 1000);
        return result.get();
    }

    @Test
    public void testSoftHyphen() {
        String text = MyUrlSpan.SOFT_HYPHEN;
        forOneString(text, Audience.EMPTY, spannedString -> TryUtils.SUCCESS);
    }
}
