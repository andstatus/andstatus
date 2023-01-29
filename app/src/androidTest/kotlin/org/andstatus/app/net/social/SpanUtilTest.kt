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
package org.andstatus.app.net.social

import android.net.Uri
import android.text.Spannable
import android.text.SpannableString
import org.andstatus.app.account.MyAccount
import org.andstatus.app.context.DemoData
import org.andstatus.app.context.TestSuite
import org.andstatus.app.data.ParsedUri
import org.andstatus.app.data.TextMediaType
import org.andstatus.app.origin.OriginPumpio
import org.andstatus.app.timeline.meta.Timeline
import org.andstatus.app.util.MyUrlSpan
import org.junit.Assert
import org.junit.Test
import java.util.*
import java.util.function.Function

class SpanUtilTest {
    init {
        TestSuite.initializeWithAccounts(this)
    }

    @Test
    fun linkifyPlainText() {
        val ma: MyAccount = DemoData.demoData.getMyAccount(DemoData.demoData.conversationAccountName)
        val audience = Audience(ma.origin)
        audience.add(ma.actor)
        val uniqueName2 = "second@identi.ca"
        addRecipient(ma, audience, uniqueName2, OriginPumpio.Companion.ACCOUNT_PREFIX + uniqueName2)
        val modifier = SpanUtil.spansModifier(audience)
        val text = "Hello @${ma.actor.webFingerId}. Thank you for noticing.\n@$uniqueName2"
        val spannable: Spannable = SpannableString.valueOf(text)
        val modified = modifier.apply(spannable)
        val spans = modified.getSpans(0, modified.length, Any::class.java)
        Assert.assertEquals("Spans created: " + Arrays.toString(spans), 2, spans.size.toLong())
    }

    @Test
    fun linkifyHtml() {
        val ma: MyAccount = DemoData.demoData.getGnuSocialAccount()
        val audience = Audience(ma.origin)
        addRecipient(ma, audience, "johnsmith", "232380")
        val username2 = ma.username
        audience.add(ma.actor)
        val modifier = SpanUtil.spansModifier(audience)
        val text = "@<span class=\"vcard\"><a href=\"http://micro.site.com/johnsmith\" class=\"url\"" +
            " title=\"johnsmith\"><span class=\"fn nickname mention\">johnsmith</span></a></span>" +
            " @<span class=\"vcard\"><a href=\"http://micro.site.com/" + username2 +
            "\" class=\"url\" title=\"" + username2 + "\">" +
            "<span class=\"fn nickname mention\">" + username2 + "</span></a></span>" +
            " Please apply your #logic #Логика to another subject."
        val spannable: Spannable = MyUrlSpan.Companion.toSpannable(text, TextMediaType.HTML, true)
        val regions1 = SpanUtil.regionsOf(spannable)
        val message1 = "Regions before change: $regions1"
        Assert.assertEquals(message1, 3, regions1.size.toLong())
        val modified = modifier.apply(spannable)
        val regions2 = SpanUtil.regionsOf(spannable)
        val spans = modified.getSpans(0, modified.length, Any::class.java)
        val message2 = "$message1\nRegions after change: $regions2"
        Assert.assertEquals(message2, 4, spans.size.toLong())
        Assert.assertEquals(message2, 6, regions2.size.toLong())
        oneMention(regions2, message2, 0, "johnsmith")
        Assert.assertEquals(
            message2, "content://timeline.app.andstatus.org/note/" + ma.actor.actorId +
                "/lt/sent/origin/${ma.origin.id}/actor/" + ma.actor.actorId,
            regions2[1].urlSpan.get().url
        )
        oneHashTag(regions2, message2, 3, "logic")
        oneHashTag(
            regions2, message2, 4, "#Логика",
            "content://timeline.app.andstatus.org/note/0/lt/search/origin/0/actor/0/search/" +
                "%23%D0%9B%D0%BE%D0%B3%D0%B8%D0%BA%D0%B0"
        )
    }

    private fun oneMention(regions: List<SpanUtil.Region>, message: String, index: Int, username: String) {
        val region = regions.get(index)
        val urlSpan = region.urlSpan
        val actor = urlSpan.flatMap { u: MyUrlSpan -> u.data.actor }.orElseThrow {
            AssertionError("No actor: Region $index should be a mention $region\n$message")
        }
        val accountToSync = actor.origin.myContext.accounts.toSyncThatActor(actor)
        Assert.assertEquals("Region $index $message",
            "content://timeline.app.andstatus.org/note/" +
                accountToSync.actorId +
                "/lt/sent/origin/${actor.origin.id}/actor/0",
            urlSpan.map { obj: MyUrlSpan -> obj.getURL() }.orElse("")
        )
        Assert.assertEquals(
            "Username in region $index $message",
            username.toUpperCase(), actor.getUsername().toUpperCase()
        )
    }

    private fun oneHashTag(regions: List<SpanUtil.Region>, message: String, index: Int, term: String) {
        oneHashTag(
            regions, message, index, "#$term",
            "content://timeline.app.andstatus.org/note/0/lt/search/origin/0/actor/0/search/%23$term"
        )
    }

    private fun oneHashTag(regions: List<SpanUtil.Region>, message: String, index: Int, hashTag: String, url: String) {
        val region = regions.get(index)
        val urlSpan = region.urlSpan
        Assert.assertEquals("Region $index should be a hashtag $region\n$message", hashTag,
            urlSpan.flatMap { u: MyUrlSpan -> u.data.searchQuery }.orElse("")
        )
        val timeline = urlSpan.map { u: MyUrlSpan -> u.data.getTimeline() }.orElse(Timeline.EMPTY)
        Assert.assertEquals(message, hashTag, timeline.searchQuery)
        val onClickUrl = urlSpan.map { obj: MyUrlSpan -> obj.getURL() }.orElse("")
        Assert.assertEquals(message, url, onClickUrl)
        val parsedUri: ParsedUri = ParsedUri.Companion.fromUri(Uri.parse(onClickUrl))
        Assert.assertEquals("$parsedUri\n${timeline.toString()}", hashTag, parsedUri.searchQuery)
    }

    @Test
    fun manyHashtags() {
        val modifier = SpanUtil.spansModifier(Audience.Companion.EMPTY)
        val text = "As somebody said there are 5 'grand challenges' to stopping floods," +
            " hurricanes, and drought    https://andstatus.org/somelink" +
            " #ActOnClimate #SR15 #IPCC #1o5 #Agriculture #Electricity" +
            " #.testnonhashtag" +  // Invalid hashtag
            " #transportation  #buildings  #Manufacturing" +
            " #s #1" +  // Valid testing hashtags
            " #ßCleanEnergy #GHG" +
            " ß #ZeroCarbon" // "ß" Upper case is "SS"
        val spannable: Spannable = MyUrlSpan.Companion.toSpannable(text, TextMediaType.HTML, true)
        val regions1 = SpanUtil.regionsOf(spannable)
        val message1 = "Regions before change: $regions1"
        Assert.assertEquals(message1, 3, regions1.size.toLong())
        val modified = modifier.apply(spannable)
        val regions2 = SpanUtil.regionsOf(modified)
        val message2 = "$message1\nRegions after change: $regions2"
        Assert.assertEquals("Wrong number of regions before change\n$message2", 3, regions1.size.toLong())
        Assert.assertEquals("Wrong number of regions after change\n$message2", 18, regions2.size.toLong())
        oneHashTag(regions2, message2, 2, "ActOnClimate")
        oneHashTag(regions2, message2, 3, "SR15")
        oneHashTag(regions2, message2, 4, "IPCC")
        oneHashTag(regions2, message2, 5, "1o5")
        oneHashTag(regions2, message2, 6, "Agriculture")
        oneHashTag(regions2, message2, 7, "Electricity")
        notAHashTag(regions2, message2, 8)
        oneHashTag(regions2, message2, 9, "transportation")
        oneHashTag(regions2, message2, 10, "buildings")
        oneHashTag(regions2, message2, 11, "Manufacturing")
        oneHashTag(regions2, message2, 12, "s")
        oneHashTag(regions2, message2, 13, "1")
        oneHashTag(
            regions2, message2, 14, "#ßCleanEnergy",
            "content://timeline.app.andstatus.org/note/0/lt/search/origin/0/actor/0/search/" +
                "%23%C3%9FCleanEnergy"
        )
        oneHashTag(regions2, message2, 15, "GHG")
        notAHashTag(regions2, message2, 16)
        oneHashTag(regions2, message2, 17, "ZeroCarbon")
    }

    private fun notAHashTag(regions: List<SpanUtil.Region>, message: String, index: Int) {
        val region = regions.get(index)
        val urlSpan = region.urlSpan
        Assert.assertEquals("Region $index should not be a hashtag $region\n$message", "",
            urlSpan.flatMap { u: MyUrlSpan -> u.data.searchQuery }.orElse("")
        )
    }

    @Test
    fun urlWithFragment() {
        val ma: MyAccount = DemoData.demoData.getMyAccount(DemoData.demoData.mastodonTestAccountName)
        val audience = Audience(ma.origin)
        addRecipient(ma, audience, "niPos", "526703")
        addRecipient(ma, audience, "er1n", "181388")
        addRecipient(ma, audience, "switchingsocial", "355551")
        val modifier = SpanUtil.spansModifier(audience)
        textWithMentions1(modifier)
        textWithMentions2(modifier)
    }

    private fun textWithMentions1(modifier: Function<Spannable, Spannable>) {
        val text = "<p><span class=\"h-card\"><a href=\"https://netzkombin.at/users/nipos\" class=\"u-url mention\">" +
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
            "@<span>switchingsocial</span></a></span></p>"
        val spannable: Spannable = MyUrlSpan.Companion.toSpannable(text, TextMediaType.HTML, true)
        val regions1 = SpanUtil.regionsOf(spannable)
        val message1 = "Regions before change: $regions1"
        Assert.assertEquals(message1, 9, regions1.size.toLong())
        val modified = modifier.apply(spannable)
        val regions2 = SpanUtil.regionsOf(modified)
        val message2 = "$message1\nRegions after change: $regions2"
        Assert.assertEquals("Wrong number of regions after change\n$message2", 9, regions2.size.toLong())
        oneMention(regions2, message2, 0, "nipos")
        oneHashTag(regions2, message2, 2, "Mastodon")
        notAHashTag(regions2, message2, 3)
        oneHashTag(regions2, message2, 4, "Twitter")
        notAHashTag(regions2, message2, 5)
        notAHashTag(regions2, message2, 6)
        oneMention(regions2, message2, 7, "er1n")
        oneMention(regions2, message2, 8, "switchingsocial")
    }

    private fun textWithMentions2(modifier: Function<Spannable, Spannable>) {
        val text = "Same mentions @nIpos but in a different case ß @er1N"
        val spannable: Spannable = MyUrlSpan.Companion.toSpannable(text, TextMediaType.HTML, true)
        val regions1 = SpanUtil.regionsOf(spannable)
        val message1 = "Regions before change: $regions1"
        Assert.assertEquals(message1, 1, regions1.size.toLong())
        val modified = modifier.apply(spannable)
        val regions2 = SpanUtil.regionsOf(modified)
        val message2 = "$message1\nRegions after change: $regions2"
        Assert.assertEquals("Wrong number of regions after change\n$message2", 4, regions2.size.toLong())
        notAHashTag(regions2, message2, 0)
        oneMention(regions2, message2, 1, "nipos")
        notAHashTag(regions2, message2, 2)
        oneMention(regions2, message2, 3, "er1n")
    }

    @Test
    fun startingWithMention() {
        val ma: MyAccount = DemoData.demoData.getMyAccount(DemoData.demoData.mastodonTestAccountName)
        val audience = Audience(ma.origin)
        addRecipient(ma, audience, "AndStatus", "5962")
        addRecipient(ma, audience, "qwertystop", "329431")
        val modifier = SpanUtil.spansModifier(audience)
        val text = "@<a href=\"https://mastodon.social/users/AndStatus\" class=\"h-card u-url p-nickname mention\" " +
            "rel=\"nofollow noopener\" target=\"_blank\">andstatus</a> " +
            "@<a href=\"https://wandering.shop/users/qwertystop\" class=\"h-card u-url p-nickname mention\" " +
            "rel=\"nofollow noopener\" target=\"_blank\">qwertystop</a> Which is how it is encoded in the XML. <br>" +
            "    <br> CW should properly have been implemented using either some new field, or using in-line stuff" +
            " like #<span class=\"\"><a href=\"https://social.umeahackerspace.se/tag/cw\" rel=\"nofollow noopener\"" +
            " target=\"_blank\">cw</a></span> &lt;foo&gt; on the first line."
        val spannable: Spannable = MyUrlSpan.Companion.toSpannable(text, TextMediaType.HTML, true)
        val regions1 = SpanUtil.regionsOf(spannable)
        val message1 = "Regions before change: $regions1"
        Assert.assertEquals(message1, 5, regions1.size.toLong())
        val modified = modifier.apply(spannable)
        val regions2 = SpanUtil.regionsOf(modified)
        val message2 = "$message1\nRegions after change: $regions2"
        Assert.assertEquals("Wrong number of regions after change\n$message2", 5, regions2.size.toLong())
        oneMention(regions2, message2, 0, "andstatus")
        oneMention(regions2, message2, 1, "qwertystop")
        notAHashTag(regions2, message2, 2)
        oneHashTag(regions2, message2, 3, "cw")
        notAHashTag(regions2, message2, 4)
    }

    private fun addRecipient(ma: MyAccount, audience: Audience, uniqueName: String, actorOid: String?) {
        val actor1: Actor = Actor.Companion.fromOid(ma.origin, actorOid)
        actor1.withUniqueName(uniqueName)
        audience.add(actor1)
    }
}
