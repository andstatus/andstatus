package org.andstatus.app.origin;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.net.http.SslModeEnum;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.UrlUtils;

import static org.andstatus.app.context.DemoData.demoData;
import static org.andstatus.app.context.MyContextHolder.myContextHolder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class DemoOriginInserter {
    private MyContext myContext;

    public DemoOriginInserter(MyContext myContext) {
        this.myContext = myContext;
    }

    public void insert() {
        demoData.checkDataPath();
        createOneOrigin(OriginType.TWITTER, demoData.twitterTestOriginName,
                demoData.twitterTestHost,
                true, SslModeEnum.SECURE, false, true, true);
        createOneOrigin(OriginType.PUMPIO, demoData.pumpioOriginName,
                "",
                true, SslModeEnum.SECURE, true, true, true);
        createOneOrigin(OriginType.GNUSOCIAL, demoData.gnusocialTestOriginName,
                demoData.gnusocialTestHost,
                true, SslModeEnum.SECURE, true, true, true);
        String additionalOriginName = demoData.gnusocialTestOriginName + "Two";
        createOneOrigin(OriginType.GNUSOCIAL, additionalOriginName,
                 "two." + demoData.gnusocialTestHost,
                true, SslModeEnum.INSECURE, true, false, true);
        createOneOrigin(OriginType.MASTODON, demoData.mastodonTestOriginName,
                demoData.mastodonTestHost,
                true, SslModeEnum.SECURE, true, true, true);
        createOneOrigin(OriginType.ACTIVITYPUB, demoData.activityPubTestOriginName,
                "",
                true, SslModeEnum.SECURE, true, true, true);
        myContext.origins().initialize();
    }

    Origin createOneOrigin(OriginType originType,
                                                 String originName, String hostOrUrl, boolean isSsl,
                                                 SslModeEnum sslMode, boolean allowHtml,
                                                 boolean inCombinedGlobalSearch, boolean inCombinedPublicReload) {
        Origin.Builder builder = new Origin.Builder(myContext, originType).setName(originName)
                .setHostOrUrl(hostOrUrl)
                .setSsl(isSsl)
                .setSslMode(sslMode)
                .setHtmlContentAllowed(allowHtml)
                .save();
        assertTrue(builder.toString(), builder.isSaved());
        Origin origin = builder.build();

        checkAttributes(origin, originName, hostOrUrl, isSsl, sslMode, allowHtml,
                inCombinedGlobalSearch, inCombinedPublicReload);

        myContext.origins().initialize();
        Origin origin2 = myContext.origins().fromId(origin.getId());
        checkAttributes(origin2, originName, hostOrUrl, isSsl, sslMode, allowHtml,
                inCombinedGlobalSearch, inCombinedPublicReload);
        return origin;
    }

    private void checkAttributes(Origin origin, String originName, String hostOrUrl,
                                        boolean isSsl, SslModeEnum sslMode, boolean allowHtml, boolean inCombinedGlobalSearch, boolean inCombinedPublicReload) {
        assertTrue("Origin " + originName + " added", origin.isPersistent());
        assertEquals(originName, origin.getName());
        if (origin.shouldHaveUrl()) {
            if (UrlUtils.isHostOnly(UrlUtils.buildUrl(hostOrUrl, isSsl))) {
                assertEquals((isSsl ? "https" : "http") + "://" + hostOrUrl,
                        origin.getUrl().toExternalForm());
            } else {
                String hostOrUrl2 = hostOrUrl.endsWith("/") ? hostOrUrl
                        : hostOrUrl + "/";
                assertEquals("Input host or URL: '" + hostOrUrl + "'",
                        UrlUtils.buildUrl(hostOrUrl2, origin.isSsl()),
                        origin.getUrl());
            }
        } else {
            assertEquals(origin.getOriginType().getUrlDefault(), origin.getUrl());
        }
        assertEquals(isSsl, origin.isSsl());
        assertEquals(sslMode, origin.getSslMode());
        assertEquals(allowHtml, origin.isHtmlContentAllowed());
    }

    public static void assertDefaultTimelinesForOrigins() {
        MyContext myContext = myContextHolder.getNow();
        for (Origin origin : myContext.origins().collection()) {
            MyAccount myAccount = myContext.accounts().
                    getFirstSucceededForOrigin(origin);
            for (TimelineType timelineType : TimelineType.getDefaultOriginTimelineTypes()) {
                int count = 0;
                for (Timeline timeline : myContext.timelines().values()) {
                    if (timeline.getOrigin().equals(origin) &&
                            timeline.getTimelineType().equals(timelineType) &&
                            timeline.getSearchQuery().isEmpty()) {
                        count++;
                    }
                }
                if (myAccount.isValid() && origin.getOriginType().isTimelineTypeSyncable(timelineType)) {
                    assertTrue("No " + timelineType + " at " + origin + "\n"
                            + myContext.timelines().values(), count > 0);
                }
            }
        }
    }
}
