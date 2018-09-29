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

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.origin.OriginPumpio;
import org.andstatus.app.util.MyUrlSpan;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static org.andstatus.app.context.DemoData.demoData;
import static org.junit.Assert.assertEquals;

public class SpanUtilTest {

    @Before
    public void setUp() throws Exception {
        TestSuite.initializeWithAccounts(this);
    }

    @Test
    public void linkifyPlainText() {
        MyAccount ma = demoData.getMyAccount(demoData.conversationAccountName);
        Audience audience = new Audience(ma.getOrigin());
        audience.add(ma.getActor());
        String username2 = "second@identi.ca";
        final Actor actor2 = Actor.fromOriginAndActorOid(ma.getOrigin(), OriginPumpio.ACCOUNT_PREFIX + username2);
        actor2.setUsername(username2);
        audience.add(actor2);
        Function<Spannable, Spannable> modifier = SpanUtil.spansModifier(audience);

        String text = "Hello @" + ma.getActor().getWebFingerId() + ". Thank you for noticing.\n@" + username2;
        Spannable spannable = SpannableString.valueOf(text);
        Spannable modified = modifier.apply(spannable);
        final Object[] spans = modified.getSpans(0, modified.length(), Object.class);
        assertEquals("Spans created: " + Arrays.toString(spans), 2, spans.length);

    }

    @Test
    public void linkifyHtml() {
        MyAccount ma = demoData.getMyAccount(demoData.gnusocialTestAccountName);
        Audience audience = new Audience(ma.getOrigin());

        String username1 = "speeddefrost";
        final Actor actor1 = Actor.fromOriginAndActorOid(ma.getOrigin(), "232380");
        actor1.setUsername(username1);
        audience.add(actor1);

        String username2 = "mcnalu";
        final Actor actor2 = Actor.fromOriginAndActorOid(ma.getOrigin(), "099842");
        actor2.setUsername(username2);
        audience.add(actor2);

        Function<Spannable, Spannable> modifier = SpanUtil.spansModifier(audience);

        String text = "@<span class=\"vcard\"><a href=\"http://micro.fragdev.com/speeddefrost\" class=\"url\"" +
                " title=\"speeddefrost\"><span class=\"fn nickname mention\">speeddefrost</span></a></span>" +
                " @<span class=\"vcard\"><a href=\"http://micro.fragdev.com/mcnalu\" class=\"url\" title=\"mcnalu\">" +
                "<span class=\"fn nickname mention\">mcnalu</span></a></span>" +
                " Just transfer your logic to GTK or Qt instead systemd and you'll end up with a wider, bigger problem.";

        Spannable spannable = MyUrlSpan.toSpannable(text, true);
        List<SpanUtil.Region> regions1 = SpanUtil.regionsOf(spannable);
        final String message1 = "Regions before change: " + regions1;
        assertEquals(message1, 3, regions1.size());

        Spannable modified = modifier.apply(spannable);
        List<SpanUtil.Region> regions2 = SpanUtil.regionsOf(spannable);
        final Object[] spans = modified.getSpans(0, modified.length(), Object.class);
        final String message2 = message1 + "\nRegions after change: " + regions2;
        assertEquals(message2, 2, spans.length);
        assertEquals(message2, 3, regions2.size());
        assertEquals(message2, "content://timeline.app.andstatus.org/note/0/lt/sent/origin/0/actor/0",
                regions2.get(0).urlSpan.get().url);
        assertEquals(message2, "content://timeline.app.andstatus.org/note/0/lt/sent/origin/0/actor/0",
                regions2.get(1).urlSpan.get().url);
    }
}
