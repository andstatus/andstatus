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
import org.andstatus.app.data.TextMediaType;
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
        String uniqueName2 = "second@identi.ca";
        addRecipient(ma, audience, uniqueName2, OriginPumpio.ACCOUNT_PREFIX + uniqueName2);
        Function<Spannable, Spannable> modifier = SpanUtil.spansModifier(audience);

        String text = "Hello @" + ma.getActor().getWebFingerId() + ". Thank you for noticing.\n@" + uniqueName2;
        Spannable spannable = SpannableString.valueOf(text);
        Spannable modified = modifier.apply(spannable);
        final Object[] spans = modified.getSpans(0, modified.length(), Object.class);
        assertEquals("Spans created: " + Arrays.toString(spans), 2, spans.length);

    }

    @Test
    public void linkifyHtml() {
        MyAccount ma = demoData.getGnuSocialAccount();
        Audience audience = new Audience(ma.getOrigin());
        addRecipient(ma, audience, "johnsmith", "232380");
        final String username2 = ma.getUsername();
        audience.add(ma.getActor());

        Function<Spannable, Spannable> modifier = SpanUtil.spansModifier(audience);

        String text = "@<span class=\"vcard\"><a href=\"http://micro.site.com/johnsmith\" class=\"url\"" +
                " title=\"johnsmith\"><span class=\"fn nickname mention\">johnsmith</span></a></span>" +
                " @<span class=\"vcard\"><a href=\"http://micro.site.com/" + username2 +
                "\" class=\"url\" title=\"" + username2 + "\">" +
                "<span class=\"fn nickname mention\">" + username2 + "</span></a></span>" +
                " Please apply your #logic #Логика to another subject.";

        Spannable spannable = MyUrlSpan.toSpannable(text, TextMediaType.HTML, true);
        List<SpanUtil.Region> regions1 = SpanUtil.regionsOf(spannable);
        final String message1 = "Regions before change: " + regions1;
        assertEquals(message1, 3, regions1.size());

        Spannable modified = modifier.apply(spannable);
        List<SpanUtil.Region> regions2 = SpanUtil.regionsOf(spannable);
        final Object[] spans = modified.getSpans(0, modified.length(), Object.class);
        final String message2 = message1 + "\nRegions after change: " + regions2;
        assertEquals(message2, 4, spans.length);
        assertEquals(message2, 6, regions2.size());
        oneMention(regions2, message2, 0, "johnsmith");
        assertEquals(message2, "content://timeline.app.andstatus.org/note/" + ma.getActor().actorId +
                        "/lt/sent/origin/0/actor/" + ma.getActor().actorId,
                regions2.get(1).urlSpan.get().getURL());

        oneHashTag(regions2, message2, 3, "logic");
        oneHashTag(regions2, message2, 4, "#Логика",
                "content://timeline.app.andstatus.org/note/0/lt/search/origin/0/actor/0/search/" +
                "%23%D0%9B%D0%BE%D0%B3%D0%B8%D0%BA%D0%B0");

    }

    private void oneMention(List<SpanUtil.Region> regions, String message, int index, String username) {
        final SpanUtil.Region region = regions.get(index);
        final Optional<MyUrlSpan> urlSpan = region.urlSpan;
        final Optional<Actor> actor = urlSpan.flatMap(u -> u.data.actor);
        final Optional<MyAccount> accountToSync = actor.map(a ->
                a.origin.myContext.accounts().toSyncThatActor(a));
        assertTrue("Region " + index + " should be a mention " + region + "\n" + message, actor.isPresent());
        assertEquals("Region " + index + " " + message,
                "content://timeline.app.andstatus.org/note/" +
                        accountToSync.map(MyAccount::getActorId).orElse(0L) +
                        "/lt/sent/origin/0/actor/0",
                urlSpan.map(MyUrlSpan::getURL).orElse(""));
        assertEquals("Username in region " + index + " " + message,
                username.toUpperCase(), actor.map(Actor::getUsername).orElse("").toUpperCase());
    }

    private void oneHashTag(List<SpanUtil.Region> regions, String message, int index, String term) {
        oneHashTag(regions, message, index, "#" + term,
                "content://timeline.app.andstatus.org/note/0/lt/search/origin/0/actor/0/search/%23" + term);
    }

    private void oneHashTag(List<SpanUtil.Region> regions, String message, int index, String hashTag, String url) {
        final SpanUtil.Region region = regions.get(index);
        final Optional<MyUrlSpan> urlSpan = region.urlSpan;
        assertEquals("Region " + index + " should be a hashtag " + region + "\n" + message, hashTag,
                urlSpan.flatMap(u -> u.data.searchQuery).orElse(""));
        final Timeline timeline = urlSpan.map(u -> u.data.getTimeline()).orElse(Timeline.EMPTY);
        assertEquals(message, hashTag, timeline.getSearchQuery());
        final String onClickUrl = urlSpan.map(MyUrlSpan::getURL).orElse("");
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
                " #ßCleanEnergy #GHG" +
                " ß #ZeroCarbon"; // "ß" Upper case is "SS"

        Spannable spannable = MyUrlSpan.toSpannable(text, TextMediaType.HTML, true);
        List<SpanUtil.Region> regions1 = SpanUtil.regionsOf(spannable);
        final String message1 = "Regions before change: " + regions1;
        assertEquals(message1, 3, regions1.size());

        Spannable modified = modifier.apply(spannable);
        List<SpanUtil.Region> regions2 = SpanUtil.regionsOf(modified);
        final String message2 = message1 + "\nRegions after change: " + regions2;
        assertEquals("Wrong number of regions before change\n" + message2, 3, regions1.size());
        assertEquals("Wrong number of regions after change\n" + message2, 18, regions2.size());

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
        oneHashTag(regions2, message2, 14, "#ßCleanEnergy",
                "content://timeline.app.andstatus.org/note/0/lt/search/origin/0/actor/0/search/" +
                        "%23%C3%9FCleanEnergy");
        oneHashTag(regions2, message2, 15, "GHG");
        notAHashTag(regions2, message2, 16);
        oneHashTag(regions2, message2, 17, "ZeroCarbon");
    }

    private void notAHashTag(List<SpanUtil.Region> regions, String message, int index) {
        final SpanUtil.Region region = regions.get(index);
        final Optional<MyUrlSpan> urlSpan = region.urlSpan;
        assertEquals("Region " + index + " should not be a hashtag " + region + "\n" + message, "",
                urlSpan.flatMap(u -> u.data.searchQuery).orElse(""));
    }


    @Test
    public void urlWithFragment() {
        MyAccount ma = demoData.getMyAccount(demoData.mastodonTestAccountName);
        Audience audience = new Audience(ma.getOrigin());

        addRecipient(ma, audience, "niPos", "526703");
        addRecipient(ma, audience, "er1n", "181388");
        addRecipient(ma, audience, "switchingsocial", "355551");

        Function<Spannable, Spannable> modifier = SpanUtil.spansModifier(audience);

        textWithMentions1(modifier);
        textWithMentions2(modifier);
    }

    private void textWithMentions1(Function<Spannable, Spannable> modifier) {
        String text = "<p><span class=\"h-card\"><a href=\"https://netzkombin.at/users/nipos\" class=\"u-url mention\">" +
                "@<span>nipos</span></a></span> Unfortunately, <a href=\"https://mastodon.social/tags/mastodon\"" +
                " class=\"mention hashtag\" rel=\"tag\">#<span>Mastodon</span></a> API uses instance-specific" +
                " identifiers (just like <a href=\"https://mastodon.social/tags/twitter\" class=\"mention hashtag\"" +
                " rel=\"tag\">#<span>Twitter</span></a>, which _is_ a single instance from a user&apos;s point of view)," +
                " which locks a user to this instance. Long story is starting from this comment:" +
                " <a href=\"https://github.com/andstatus/andstatus/issues/419#issuecomment-254031208\"" +
                " rel=\"nofollow noopener\" target=\"_blank\">" +
                "<span class=\"invisible\">https://</span><span class=\"ellipsis\">github.com/andstatus/andstatus</span>" +
                "<span class=\"invisible\">/issues/419#issuecomment-254031208</span></a><br />" +
                "<span class=\"h-card\">" +
                "<a href=\"https://social.mecanis.me/@er1n\" class=\"u-url mention\">@<span>er1n</span></a></span>" +
                " <span class=\"h-card\"><a href=\"https://mastodon.at/@switchingsocial\" class=\"u-url mention\">" +
                "@<span>switchingsocial</span></a></span></p>";

        Spannable spannable = MyUrlSpan.toSpannable(text, TextMediaType.HTML, true);
        List<SpanUtil.Region> regions1 = SpanUtil.regionsOf(spannable);
        final String message1 = "Regions before change: " + regions1;
        assertEquals(message1, 9, regions1.size());

        Spannable modified = modifier.apply(spannable);
        List<SpanUtil.Region> regions2 = SpanUtil.regionsOf(modified);
        final String message2 = message1 + "\nRegions after change: " + regions2;
        assertEquals("Wrong number of regions after change\n" + message2, 9, regions2.size());

        oneMention(regions2, message2, 0, "nipos");
        oneHashTag(regions2, message2, 2, "Mastodon");
        notAHashTag(regions2, message2, 3);
        oneHashTag(regions2, message2, 4, "Twitter");
        notAHashTag(regions2, message2, 5);
        notAHashTag(regions2, message2, 6);
        oneMention(regions2, message2, 7, "er1n");
        oneMention(regions2, message2, 8, "switchingsocial");
    }

    private void textWithMentions2(Function<Spannable, Spannable> modifier) {
        String text = "Same mentions @nIpos but in a different case ß @er1N";

        Spannable spannable = MyUrlSpan.toSpannable(text, TextMediaType.HTML, true);
        List<SpanUtil.Region> regions1 = SpanUtil.regionsOf(spannable);
        final String message1 = "Regions before change: " + regions1;
        assertEquals(message1, 1, regions1.size());

        Spannable modified = modifier.apply(spannable);
        List<SpanUtil.Region> regions2 = SpanUtil.regionsOf(modified);
        final String message2 = message1 + "\nRegions after change: " + regions2;
        assertEquals("Wrong number of regions after change\n" + message2, 4, regions2.size());

        notAHashTag(regions2, message2, 0);
        oneMention(regions2, message2, 1, "nipos");
        notAHashTag(regions2, message2, 2);
        oneMention(regions2, message2, 3, "er1n");
    }

    @Test
    public void startingWithMention() {
        MyAccount ma = demoData.getMyAccount(demoData.mastodonTestAccountName);
        Audience audience = new Audience(ma.getOrigin());

        addRecipient(ma, audience, "AndStatus", "5962");
        addRecipient(ma, audience, "qwertystop", "329431");

        Function<Spannable, Spannable> modifier = SpanUtil.spansModifier(audience);

        String text = "@<a href=\"https://mastodon.social/users/AndStatus\" class=\"h-card u-url p-nickname mention\" " +
                "rel=\"nofollow noopener\" target=\"_blank\">andstatus</a> " +
                "@<a href=\"https://wandering.shop/users/qwertystop\" class=\"h-card u-url p-nickname mention\" " +
                "rel=\"nofollow noopener\" target=\"_blank\">qwertystop</a> Which is how it is encoded in the XML. <br>" +
                "    <br> CW should properly have been implemented using either some new field, or using in-line stuff" +
                " like #<span class=\"\"><a href=\"https://social.umeahackerspace.se/tag/cw\" rel=\"nofollow noopener\"" +
                " target=\"_blank\">cw</a></span> &lt;foo&gt; on the first line.";

        Spannable spannable = MyUrlSpan.toSpannable(text, TextMediaType.HTML, true);
        List<SpanUtil.Region> regions1 = SpanUtil.regionsOf(spannable);
        final String message1 = "Regions before change: " + regions1;
        assertEquals(message1, 5, regions1.size());

        Spannable modified = modifier.apply(spannable);
        List<SpanUtil.Region> regions2 = SpanUtil.regionsOf(modified);
        final String message2 = message1 + "\nRegions after change: " + regions2;
        assertEquals("Wrong number of regions after change\n" + message2, 5, regions2.size());

        oneMention(regions2, message2, 0, "andstatus");
        oneMention(regions2, message2, 1, "qwertystop");
        notAHashTag(regions2, message2, 2);
        oneHashTag(regions2, message2, 3, "cw");
        notAHashTag(regions2, message2, 4);
    }

    private void addRecipient(MyAccount ma, Audience audience, String uniqueName, String actorOid) {
        final Actor actor1 = Actor.fromOid(ma.getOrigin(), actorOid);
        actor1.withUniqueName(uniqueName);
        audience.add(actor1);
    }

}
