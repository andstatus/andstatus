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

import android.net.Uri;
import android.text.Spannable;
import android.text.SpannableString;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.ParsedUri;
import org.andstatus.app.origin.OriginPumpio;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.util.MyUrlSpan;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.andstatus.app.context.DemoData.demoData;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

        String username1 = "johnsmith";
        final Actor actor1 = Actor.fromOriginAndActorOid(ma.getOrigin(), "232380");
        actor1.setUsername(username1);
        audience.add(actor1);

        audience.add(ma.getActor());
        final String username2 = ma.getUsername();

        Function<Spannable, Spannable> modifier = SpanUtil.spansModifier(audience);

        String text = "@<span class=\"vcard\"><a href=\"http://micro.site.com/johnsmith\" class=\"url\"" +
                " title=\"johnsmith\"><span class=\"fn nickname mention\">johnsmith</span></a></span>" +
                " @<span class=\"vcard\"><a href=\"http://micro.site.com/" + username2 +
                "\" class=\"url\" title=\"" + username2 + "\">" +
                "<span class=\"fn nickname mention\">" + username2 + "</span></a></span>" +
                " Please apply your #logic #Логика to another subject.";

        Spannable spannable = MyUrlSpan.toSpannable(text, true);
        List<SpanUtil.Region> regions1 = SpanUtil.regionsOf(spannable);
        final String message1 = "Regions before change: " + regions1;
        assertEquals(message1, 3, regions1.size());

        Spannable modified = modifier.apply(spannable);
        List<SpanUtil.Region> regions2 = SpanUtil.regionsOf(spannable);
        final Object[] spans = modified.getSpans(0, modified.length(), Object.class);
        final String message2 = message1 + "\nRegions after change: " + regions2;
        assertEquals(message2, 4, spans.length);
        assertEquals(message2, 6, regions2.size());
        assertEquals(message2, "content://timeline.app.andstatus.org/note/0/lt/sent/origin/0/actor/0",
                regions2.get(0).urlSpan.get().getURL());
        assertEquals(message2, "content://timeline.app.andstatus.org/note/" + ma.getActor().actorId +
                        "/lt/sent/origin/0/actor/" + ma.getActor().actorId,
                regions2.get(1).urlSpan.get().getURL());

        oneHashTag(regions2, message2, 3, "logic");
        oneHashTag(regions2, message2, 4, "#Логика",
                "content://timeline.app.andstatus.org/note/0/lt/search/origin/0/actor/0/search/" +
                "%23%D0%9B%D0%BE%D0%B3%D0%B8%D0%BA%D0%B0");

    }

    private void oneHashTag(List<SpanUtil.Region> regions, String message, int index, String term) {
        oneHashTag(regions, message, index, "#" + term,
                "content://timeline.app.andstatus.org/note/0/lt/search/origin/0/actor/0/search/%23" + term);
    }

    private void oneHashTag(List<SpanUtil.Region> regions, String message, int index, String hashTag, String url) {
        final SpanUtil.Region region = regions.get(index);
        final Optional<MyUrlSpan> urlSpan = region.urlSpan;
        assertTrue("Should be a hashtag " + region + "\n" + message, urlSpan.isPresent());
        final Timeline timeline = urlSpan.get().data.getTimeline();
        assertEquals(message, hashTag, timeline.getSearchQuery());
        final String onClickUrl = urlSpan.get().getURL();
        assertEquals(message, url, onClickUrl);
        ParsedUri parsedUri = ParsedUri.fromUri(Uri.parse(onClickUrl));
        assertEquals(parsedUri.toString() + "\n" + timeline.toString(), hashTag, parsedUri.getSearchQuery());
    }

    @Test
    public void manyHashtags() {
        Function<Spannable, Spannable> modifier = SpanUtil.spansModifier(Audience.EMPTY);

        String text = "As somebody said there are 5 'grand challenges' to stopping floods," +
                " hurricanes, and drought    https://andstatus.org/somelink" +
                " #ActOnClimate #SR15 #IPCC #1o5 #Agriculture #Electricity" +
                " #.testnonhashtag" + // Invalid hashtag
                " #transportation  #buildings  #Manufacturing" +
                " #s #1" + // Valid testing hashtags
                " #CleanEnergy #GHG  #ZeroCarbon";

        Spannable spannable = MyUrlSpan.toSpannable(text, true);
        List<SpanUtil.Region> regions1 = SpanUtil.regionsOf(spannable);
        final String message1 = "Regions before change: " + regions1;
        assertEquals(message1, 3, regions1.size());

        Spannable modified = modifier.apply(spannable);
        List<SpanUtil.Region> regions2 = SpanUtil.regionsOf(spannable);
        final Object[] spans = modified.getSpans(0, modified.length(), Object.class);
        final String message2 = message1 + "\nRegions after change: " + regions2;
        assertEquals("Wrong number of regions before change\n" + message2, 3, regions1.size());
        assertEquals("Wrong number of regions after change\n" + message2, 17, regions2.size());

        oneHashTag(regions2, message2, 2, "ActOnClimate");
        oneHashTag(regions2, message2, 3, "SR15");
        oneHashTag(regions2, message2, 4, "IPCC");
        oneHashTag(regions2, message2, 5, "1o5");
        oneHashTag(regions2, message2, 6, "Agriculture");
        oneHashTag(regions2, message2, 7, "Electricity");
        notAHashTag(regions2, message2, 8);
        oneHashTag(regions2, message2, 9, "transportation");
        oneHashTag(regions2, message2, 10, "buildings");
        oneHashTag(regions2, message2, 11, "Manufacturing");
        oneHashTag(regions2, message2, 12, "s");
        oneHashTag(regions2, message2, 13, "1");
        oneHashTag(regions2, message2, 14, "CleanEnergy");
        oneHashTag(regions2, message2, 15, "GHG");
        oneHashTag(regions2, message2, 16, "ZeroCarbon");
    }

    private void notAHashTag(List<SpanUtil.Region> regions, String message, int index) {
        final SpanUtil.Region region = regions.get(index);
        final Optional<MyUrlSpan> urlSpan = region.urlSpan;
        assertFalse("Should not be a hashtag " + region + "\n" + message, urlSpan.isPresent());
    }

}
