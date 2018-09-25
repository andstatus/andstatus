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
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.function.Function;

import static org.andstatus.app.context.DemoData.demoData;
import static org.junit.Assert.assertEquals;

public class SpanUtilTest {

    @Before
    public void setUp() throws Exception {
        TestSuite.initializeWithData(this);
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
}
