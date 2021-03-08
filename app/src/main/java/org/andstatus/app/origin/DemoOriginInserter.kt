package org.andstatus.app.origin

import org.andstatus.app.context.DemoData
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.net.http.SslModeEnum
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.UrlUtils
import org.junit.Assert

class DemoOriginInserter(private val myContext: MyContext?) {
    fun insert() {
        DemoData.demoData.checkDataPath()
        createOneOrigin(OriginType.TWITTER, DemoData.demoData.twitterTestOriginName,
                DemoData.demoData.twitterTestHost,
                true, SslModeEnum.SECURE, false, true, true)
        createOneOrigin(OriginType.PUMPIO, DemoData.demoData.pumpioOriginName,
                "",
                true, SslModeEnum.SECURE, true, true, true)
        createOneOrigin(OriginType.GNUSOCIAL, DemoData.demoData.gnusocialTestOriginName,
                DemoData.demoData.gnusocialTestHost,
                true, SslModeEnum.SECURE, true, true, true)
        val additionalOriginName: String = DemoData.demoData.gnusocialTestOriginName + "Two"
        createOneOrigin(OriginType.GNUSOCIAL, additionalOriginName,
                "two." + DemoData.demoData.gnusocialTestHost,
                true, SslModeEnum.INSECURE, true, false, true)
        createOneOrigin(OriginType.MASTODON, DemoData.demoData.mastodonTestOriginName,
                DemoData.demoData.mastodonTestHost,
                true, SslModeEnum.SECURE, true, true, true)
        createOneOrigin(OriginType.ACTIVITYPUB, DemoData.demoData.activityPubTestOriginName,
                "",
                true, SslModeEnum.SECURE, true, true, true)
        myContext.origins().initialize()
    }

    fun createOneOrigin(originType: OriginType?,
                        originName: String?, hostOrUrl: String?, isSsl: Boolean,
                        sslMode: SslModeEnum?, allowHtml: Boolean,
                        inCombinedGlobalSearch: Boolean, inCombinedPublicReload: Boolean): Origin? {
        val builder = Origin.Builder(myContext, originType).setName(originName)
                .setHostOrUrl(hostOrUrl)
                .setSsl(isSsl)
                .setSslMode(sslMode)
                .setHtmlContentAllowed(allowHtml)
                .save()
        Assert.assertTrue(builder.toString(), builder.isSaved)
        val origin = builder.build()
        checkAttributes(origin, originName, hostOrUrl, isSsl, sslMode, allowHtml,
                inCombinedGlobalSearch, inCombinedPublicReload)
        myContext.origins().initialize()
        val origin2 = myContext.origins().fromId(origin.getId())
        checkAttributes(origin2, originName, hostOrUrl, isSsl, sslMode, allowHtml,
                inCombinedGlobalSearch, inCombinedPublicReload)
        return origin
    }

    private fun checkAttributes(origin: Origin?, originName: String?, hostOrUrl: String?,
                                isSsl: Boolean, sslMode: SslModeEnum?, allowHtml: Boolean, inCombinedGlobalSearch: Boolean, inCombinedPublicReload: Boolean) {
        Assert.assertTrue("Origin $originName added", origin.isPersistent())
        Assert.assertEquals(originName, origin.name)
        if (origin.shouldHaveUrl()) {
            if (UrlUtils.isHostOnly(UrlUtils.buildUrl(hostOrUrl, isSsl))) {
                Assert.assertEquals((if (isSsl) "https" else "http") + "://" + hostOrUrl,
                        origin.getUrl().toExternalForm())
            } else {
                val hostOrUrl2 = if (hostOrUrl.endsWith("/")) hostOrUrl else "$hostOrUrl/"
                Assert.assertEquals("Input host or URL: '$hostOrUrl'",
                        UrlUtils.buildUrl(hostOrUrl2, origin.isSsl()),
                        origin.getUrl())
            }
        } else {
            Assert.assertEquals(origin.originType.urlDefault, origin.getUrl())
        }
        Assert.assertEquals(isSsl, origin.isSsl())
        Assert.assertEquals(sslMode, origin.getSslMode())
        Assert.assertEquals(allowHtml, origin.isHtmlContentAllowed())
    }

    companion object {
        fun assertDefaultTimelinesForOrigins() {
            val myContext: MyContext =  MyContextHolder.myContextHolder.getNow()
            for (origin in myContext.origins().collection()) {
                val myAccount = myContext.accounts().getFirstPreferablySucceededForOrigin(origin)
                for (timelineType in TimelineType.Companion.getDefaultOriginTimelineTypes()) {
                    var count = 0
                    for (timeline in myContext.timelines().values()) {
                        if (timeline.origin == origin && timeline.timelineType == timelineType &&
                                timeline.searchQuery.isEmpty()) {
                            count++
                        }
                    }
                    if (myAccount.isValid && origin.originType.isTimelineTypeSyncable(timelineType)) {
                        Assert.assertTrue("""No $timelineType at $origin
${myContext.timelines().values()}""", count > 0)
                    }
                }
            }
        }
    }
}