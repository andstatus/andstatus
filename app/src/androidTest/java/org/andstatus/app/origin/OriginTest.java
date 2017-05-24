
package org.andstatus.app.origin;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.context.Travis;
import org.andstatus.app.context.UserInTimeline;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MessageInserter;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.MsgTable;
import org.andstatus.app.net.http.OAuthClientKeysTest;
import org.andstatus.app.net.http.SslModeEnum;
import org.andstatus.app.net.social.MbConfig;
import org.andstatus.app.origin.Origin.Builder;
import org.andstatus.app.util.UrlUtils;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Travis
public class OriginTest {

    @Before
    public void setUp() throws Exception {
        TestSuite.initializeWithData(this);
    }

    @Test
    public void testTextLimit() {
        String urlString = "https://github.com/andstatus/andstatus/issues/41";
        String message = "I set \"Shorten URL with: QTTR_AT\" URL longer than 25 Text longer than 140. Will this be shortened: "
                + urlString;

        Origin origin = MyContextHolder.get().persistentOrigins()
                .firstOfType(OriginType.ORIGIN_TYPE_DEFAULT);
        assertEquals(origin.getOriginType(), OriginType.TWITTER);

        origin = MyContextHolder.get().persistentOrigins()
                .firstOfType(OriginType.TWITTER);
        assertEquals(origin.getOriginType(), OriginType.TWITTER);
        int textLimit = 140;
        assertEquals("Textlimit", textLimit, origin.getTextLimit());
        assertEquals("Short URL length", 23, origin.shortUrlLength);
        int charactersLeft = origin.charactersLeftForMessage(message);
        // Depending on number of spans!
        assertTrue("Characters left " + charactersLeft, charactersLeft == 18
                || charactersLeft == 2);
        assertFalse(origin.isMentionAsWebFingerId());

        origin = MyContextHolder.get().persistentOrigins()
                .firstOfType(OriginType.PUMPIO);
        textLimit = 5000;
        assertEquals("Textlimit", textLimit, origin.getTextLimit());
        assertEquals("Short URL length", 0, origin.shortUrlLength);
        assertEquals("Characters left",
                origin.getTextLimit() - message.length(),
                origin.charactersLeftForMessage(message));
        assertTrue(origin.isMentionAsWebFingerId());

        origin = MyContextHolder.get().persistentOrigins()
                .firstOfType(OriginType.GNUSOCIAL);
        textLimit = Origin.TEXT_LIMIT_FOR_WEBFINGER_ID;
        int uploadLimit = 0;
        MbConfig config = MbConfig.fromTextLimit(textLimit, uploadLimit);
        origin = new Origin.Builder(origin).save(config).build();
        assertEquals("Textlimit", textLimit, origin.getTextLimit());
        assertTrue(origin.isMentionAsWebFingerId());

        textLimit = OriginType.TWITTER.textLimitDefault;
        config = MbConfig.fromTextLimit(textLimit, uploadLimit);
        origin = new Origin.Builder(origin).save(config).build();
        assertEquals("Textlimit", textLimit, origin.getTextLimit());
        assertEquals("Short URL length", 0, origin.shortUrlLength);
        assertEquals("Characters left", textLimit - message.length(),
                origin.charactersLeftForMessage(message));
        assertFalse(origin.isMentionAsWebFingerId());

        textLimit = 0;
        config = MbConfig.fromTextLimit(textLimit, uploadLimit);
        assertFalse(config.isEmpty());
        config.shortUrlLength = 24;
        origin = new Origin.Builder(origin).save(config).build();
        assertEquals("Textlimit", OriginType.TEXT_LIMIT_MAXIMUM,
                origin.getTextLimit());
        assertEquals("Short URL length", config.shortUrlLength,
                origin.shortUrlLength);
        assertEquals("Characters left",
                origin.getTextLimit() - message.length()
                        - config.shortUrlLength + urlString.length(),
                origin.charactersLeftForMessage(message));
        assertTrue(origin.isMentionAsWebFingerId());
    }

    @Test
    public void testAddDeleteOrigin() {
        String seed = Long.toString(System.nanoTime());
        String originName = "snTest" + seed;
        OriginType originType = OriginType.GNUSOCIAL;
        String hostOrUrl = "sn" + seed + ".example.org";
        boolean isSsl = true;
        boolean allowHtml = true;
        boolean inCombinedGlobalSearch = true;
        boolean inCombinedPublicReload = true;
        
        createOneOrigin(originType, originName, hostOrUrl, isSsl, SslModeEnum.SECURE, allowHtml,
                inCombinedGlobalSearch, inCombinedPublicReload);
        createOneOrigin(originType, originName, "https://" + hostOrUrl
                + "/somepath", isSsl, SslModeEnum.INSECURE, allowHtml, 
                inCombinedGlobalSearch, false);
        createOneOrigin(originType, originName, "https://" + hostOrUrl
                + "/pathwithslash/", isSsl, SslModeEnum.MISCONFIGURED, allowHtml,
                false, inCombinedPublicReload);
        isSsl = false;
        Origin.Builder builder2 = createOneOrigin(originType, originName,
                hostOrUrl, isSsl, SslModeEnum.SECURE, allowHtml, 
                inCombinedGlobalSearch, inCombinedPublicReload);
        Origin origin = builder2.build();
        assertEquals("New origin has no children", false, origin.hasChildren());
        assertEquals("Origin deleted", true, builder2.delete());
    }

    public static Builder createOneOrigin(OriginType originType,
            String originName, String hostOrUrl, boolean isSsl,
            SslModeEnum sslMode, boolean allowHtml, 
            boolean inCombinedGlobalSearch, boolean inCombinedPublicReload) {
        Origin.Builder builder = new Origin.Builder(originType).setName(originName)
                .setHostOrUrl(hostOrUrl)
                .setSsl(isSsl)
                .setSslMode(sslMode)
                .setHtmlContentAllowed(allowHtml)
                .save();
        assertTrue(builder.toString(), builder.isSaved());
        Origin origin = builder.build();
        if (origin.isOAuthDefault() || origin.canChangeOAuth()) {
            OAuthClientKeysTest.insertTestKeys(origin);
        }

        checkAttributes(origin, originName, hostOrUrl, isSsl, sslMode, allowHtml,
                inCombinedGlobalSearch, inCombinedPublicReload);

        MyContextHolder.get().persistentOrigins().initialize();
        Origin origin2 = MyContextHolder.get().persistentOrigins()
                .fromId(origin.getId());
        checkAttributes(origin2, originName, hostOrUrl, isSsl, sslMode, allowHtml,
                inCombinedGlobalSearch, inCombinedPublicReload);

        return builder;
    }

    private static void checkAttributes(Origin origin, String originName, String hostOrUrl,
            boolean isSsl, SslModeEnum sslMode, boolean allowHtml, boolean inCombinedGlobalSearch, boolean inCombinedPublicReload) {
        assertTrue("Origin " + originName + " added", origin.isPersistent());
        assertEquals(originName, origin.getName());
        if (origin.canSetUrlOfOrigin()) {
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
            assertEquals(origin.getOriginType().urlDefault, origin.getUrl());
        }
        assertEquals(isSsl, origin.isSsl());
        assertEquals(sslMode, origin.getSslMode());
        assertEquals(allowHtml, origin.isHtmlContentAllowed());
    }

    @Test
    public void testPermalink() {
        Origin origin = MyContextHolder.get().persistentOrigins().firstOfType(OriginType.TWITTER);
        assertEquals(origin.getOriginType(), OriginType.TWITTER);
        String body = "Posting to Twitter " + TestSuite.TESTRUN_UID;
        String messageOid = "2578909845023" + TestSuite.TESTRUN_UID;
        long msgId = MessageInserter.addMessageForAccount(
                TestSuite.getMyAccount(TestSuite.TWITTER_TEST_ACCOUNT_NAME),
                body, messageOid, DownloadStatus.LOADED);
        assertTrue(msgId != 0);
        String userName = MyQuery.msgIdToUsername(MsgTable.AUTHOR_ID, msgId,
                UserInTimeline.USERNAME);
        String permalink = origin.messagePermalink(msgId);
        String desc = "Permalink of Twitter message '" + messageOid + "' by '" + userName
                + "' at " + origin.toString() + " is " + permalink;
        assertTrue(desc, permalink.contains(userName + "/status/" + messageOid));
        assertFalse(desc, permalink.contains("://api."));
    }

    @Test
    public void testNameFix() {
        checkOneName("o.mrblog.nl", " o. mrblog. nl ");
        checkOneName("o.mrblog.nl", " o.   mrblog. nl ");
        checkOneName("Aqeel.s.instance", "Aqeel's instance");
        checkOneName("BKA.li.Public.GS", "BKA.li Public GS");
        checkOneName("Quitter.Espanol", "Quitter Español");
    }
    
    private void checkOneName(String out, String in) {
        assertEquals(out, new Origin.Builder(OriginType.GNUSOCIAL).setName(in).build().getName());
    }

    @Test
    public void testHostFix() {
        checkOneHost("https://o.mrblog.nl", " o. mrBlog. nl ", true);
        checkOneHost("http://o.mrblog.nl", " o.   mrblog. nl ", false);
    }
    
    private void checkOneHost(String out, String in, boolean ssl) {
        assertEquals(out, new Origin.Builder(OriginType.GNUSOCIAL).setHostOrUrl(in).setSsl(ssl).build().getUrl().toExternalForm());
    }

    @Test
    public void testUsernameIsValid() {
        Origin origin = MyContextHolder.get().persistentOrigins().fromName(TestSuite.GNUSOCIAL_TEST_ORIGIN_NAME);
        checkUsernameIsValid(origin, "", false);
        checkUsernameIsValid(origin, "someUser.", false);
        checkUsernameIsValid(origin, "someUser ", false);
        checkUsernameIsValid(origin, "someUser", true);
        checkUsernameIsValid(origin, "some.user", true);
        checkUsernameIsValid(origin, "some.user/GnuSocial", false);
        checkUsernameIsValid(origin, "some@user", false);

        origin = MyContextHolder.get().persistentOrigins().fromName(TestSuite.PUMPIO_ORIGIN_NAME);
        checkUsernameIsValid(origin, "", false);
        checkUsernameIsValid(origin, "someUser.", false);
        checkUsernameIsValid(origin, "someUser ", false);
        checkUsernameIsValid(origin, "someUser", false);
        checkUsernameIsValid(origin, "some.user", false);
        checkUsernameIsValid(origin, "some.user@example.com", true);
        checkUsernameIsValid(origin, "t131t@identi.ca/PumpIo", false);
        checkUsernameIsValid(origin, "some@example.com.", false);
        checkUsernameIsValid(origin, "some@example.com", true);
        checkUsernameIsValid(origin, "some@user", false);
        checkUsernameIsValid(origin, "AndStatus@datamost.com", true);
    }

    private void checkUsernameIsValid(Origin origin, String userName, boolean valid) {
        assertEquals("Username '" + userName + "' " + (valid ? "is not valid" : "is valid"), valid,
                origin.isUsernameValid(userName));
    }
}
