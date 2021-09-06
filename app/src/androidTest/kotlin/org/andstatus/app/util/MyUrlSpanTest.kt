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
package org.andstatus.app.util

import android.text.SpannedString
import android.text.style.URLSpan
import android.widget.TextView
import androidx.viewpager.widget.ViewPager
import io.vavr.control.Try
import org.andstatus.app.HelpActivity
import org.andstatus.app.R
import org.andstatus.app.context.ActivityTest
import org.andstatus.app.context.DemoData
import org.andstatus.app.context.TestSuite
import org.andstatus.app.data.DbUtils
import org.andstatus.app.data.TextMediaType
import org.andstatus.app.net.social.Actor
import org.andstatus.app.net.social.Audience
import org.andstatus.app.net.social.SpanUtil
import org.andstatus.app.util.EspressoUtils.waitForIdleSync
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Function

/** See https://github.com/andstatus/andstatus/issues/300  */
class MyUrlSpanTest : ActivityTest<HelpActivity>() {
    init {
        TestSuite.initializeWithAccounts(this)
    }

    override fun getActivityClass(): Class<HelpActivity> {
        return HelpActivity::class.java
    }

    @Before
    fun setUp() {
        activity
        waitForIdleSync()
    }

    @Test
    fun testMyUrlSpan() {
        val text = ("The string has malformed links "
                + MyUrlSpan.Companion.SOFT_HYPHEN + " "
                + "Details: /press-release/nasa-confirms-evidence-that-liquid-water-flows-on-today-s-mars/ #MarsAnnouncement"
                + " and this "
                + "feed://radio.example.org/archives.xml"
                + " two links")
        forOneString(text, Audience.Companion.EMPTY) { TryUtils.SUCCESS }
        val part0 = "A post to "
        val part1 = "@AndStatus@pleroma.site"
        val audience2 = Audience(DemoData.demoData.getAccountActorByOid(DemoData.demoData.activityPubTestAccountActorOid).origin)
        val actor2: Actor = Actor.Companion.fromOid(audience2.origin, "https://pleroma.site/users/AndStatus")
        actor2.setUsername( "AndStatus")
        actor2.setProfileUrl(actor2.oid)
        actor2.setWebFingerId("andstatus@pleroma.site")
        audience2.add(actor2)
        forOneString(part0 + part1, audience2) { spannedString: SpannedString ->
            val regions = SpanUtil.regionsOf(spannedString)
            if (regions.size != 2) return@forOneString TryUtils.failure<Unit>("Two regions expected $regions")
            if (part0 != regions[0].text.toString()) {
                return@forOneString TryUtils.failure<Unit>("Region(0) is wrong. $regions")
            }
            if (part1 != regions[1].text.toString()) {
                return@forOneString TryUtils.failure<Unit>("Region(1) is wrong. $regions")
            }
            TryUtils.SUCCESS
        }.onFailure { t: Throwable -> Assert.fail(t.message) }
    }

    private fun forOneString(text: String, audience: Audience,
                             spannedStringChecker: Function<SpannedString, Try<Unit>>): Try<Unit> {
        val method = "forOneString"
        DbUtils.waitMs(method, 1000)
        val pager = activity.findViewById<ViewPager?>(R.id.help_flipper)
        Assert.assertNotNull(pager)
        val textView = activity.findViewById<TextView?>(R.id.splash_payoff_line)
        val result = AtomicReference(TryUtils.notFound<Unit>())
        activity.runOnUiThread {
            pager.currentItem = HelpActivity.Companion.PAGE_LOGO
            MyUrlSpan.Companion.showSpannable(textView,
                    SpanUtil.textToSpannable(text, TextMediaType.UNKNOWN, audience), false)
            val text1 = textView.text
            if (text1 is SpannedString) {
                result.set(spannedStringChecker.apply(text1))
                val spans = text1.getSpans(0, text1.length, URLSpan::class.java)
                for (span in spans) {
                    MyLog.i(this, "Clicking on: " + span.url)
                    span.onClick(textView)
                }
            }
        }
        DbUtils.waitMs(method, 1000)
        return result.get()
    }

    @Test
    fun testSoftHyphen() {
        val text: String = MyUrlSpan.Companion.SOFT_HYPHEN
        forOneString(text, Audience.Companion.EMPTY) { TryUtils.SUCCESS }
    }
}
