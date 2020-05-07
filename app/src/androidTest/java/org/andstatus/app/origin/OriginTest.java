
package org.andstatus.app.origin;

import org.andstatus.app.context.ActorInTimeline;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.DemoNoteInserter;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.net.http.SslModeEnum;
import org.andstatus.app.net.social.AActivity;
import org.junit.Before;
import org.junit.Test;

import static org.andstatus.app.context.DemoData.demoData;
import static org.andstatus.app.context.MyContextHolder.myContextHolder;
import static org.andstatus.app.origin.OriginType.TEXT_LIMIT_MAXIMUM;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class OriginTest {

    @Before
    public void setUp() throws Exception {
        TestSuite.initializeWithAccounts(this);
    }

    @Test
    public void testTextLimit() {
        String urlString = "https://github.com/andstatus/andstatus/issues/41";
        String body = "I set \"Shorten URL with: QTTR_AT\" URL longer than 25 Text longer than 140. Will this be shortened: "
                + urlString;

        Origin origin = myContextHolder.getNow().origins().firstOfType(OriginType.ORIGIN_TYPE_DEFAULT);
        assertEquals(OriginType.TWITTER, origin.getOriginType());

        origin = myContextHolder.getNow().origins().firstOfType(OriginType.TWITTER);
        assertEquals(OriginType.TWITTER, origin.getOriginType());
        int textLimit = 280;
        assertEquals("Textlimit", textLimit, origin.getTextLimit());
        assertEquals("Short URL length", 23, origin.shortUrlLength);
        int charactersLeft = origin.charactersLeftForNote(body);
        // Depending on number of spans!
        assertTrue("Characters left " + charactersLeft, charactersLeft == 158
                || charactersLeft == 142);
        assertFalse(origin.isMentionAsWebFingerId());

        origin = myContextHolder.getNow().origins().firstOfType(OriginType.PUMPIO);
        textLimit = TEXT_LIMIT_MAXIMUM;
        assertEquals("Textlimit", textLimit, origin.getTextLimit());
        assertEquals("Short URL length", 0, origin.shortUrlLength);
        assertEquals("Characters left",
                origin.getTextLimit() - body.length(),
                origin.charactersLeftForNote(body));
        assertTrue(origin.isMentionAsWebFingerId());

        origin = myContextHolder.getNow().origins().firstOfType(OriginType.GNUSOCIAL);
        textLimit = Origin.TEXT_LIMIT_FOR_WEBFINGER_ID;
        int uploadLimit = 0;
        OriginConfig config = OriginConfig.fromTextLimit(textLimit, uploadLimit);
        origin = new Origin.Builder(origin).save(config).build();
        assertEquals("Textlimit", textLimit, origin.getTextLimit());
        assertTrue(origin.isMentionAsWebFingerId());

        textLimit = 140;
        config = OriginConfig.fromTextLimit(textLimit, uploadLimit);
        origin = new Origin.Builder(origin).save(config).build();
        assertEquals("Textlimit", textLimit, origin.getTextLimit());
        assertEquals("Short URL length", 0, origin.shortUrlLength);
        assertEquals("Characters left", textLimit - body.length(),
                origin.charactersLeftForNote(body));
        assertFalse(origin.isMentionAsWebFingerId());

        textLimit = 0;
        config = OriginConfig.fromTextLimit(textLimit, uploadLimit);
        assertTrue(config.nonEmpty());
        origin = new Origin.Builder(origin).save(config).build();
        assertEquals("Textlimit", TEXT_LIMIT_MAXIMUM, origin.getTextLimit());
        assertEquals("Characters left " + origin,
                origin.getTextLimit() - body.length()
                        + (origin.shortUrlLength > 0 ? origin.shortUrlLength - urlString.length() : 0),
                origin.charactersLeftForNote(body));
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

        DemoOriginInserter originInserter = new DemoOriginInserter(myContextHolder.getNow());
        originInserter.createOneOrigin(originType, originName, hostOrUrl, isSsl, SslModeEnum.SECURE, allowHtml,
                inCombinedGlobalSearch, inCombinedPublicReload);
        originInserter.createOneOrigin(originType, originName, "https://" + hostOrUrl
                + "/somepath", isSsl, SslModeEnum.INSECURE, allowHtml, 
                inCombinedGlobalSearch, false);
        originInserter.createOneOrigin(originType, originName, "https://" + hostOrUrl
                + "/pathwithslash/", isSsl, SslModeEnum.MISCONFIGURED, allowHtml,
                false, inCombinedPublicReload);
        isSsl = false;
        Origin origin = originInserter.createOneOrigin(originType, originName,
                hostOrUrl, isSsl, SslModeEnum.SECURE, allowHtml, 
                inCombinedGlobalSearch, inCombinedPublicReload);
        assertEquals("New origin has no children", false, origin.hasChildren());
        assertEquals("Origin deleted", true, new Origin.Builder(origin).delete());
    }

    @Test
    public void testPermalink() {
        Origin origin = myContextHolder.getNow().origins().firstOfType(OriginType.TWITTER);
        assertEquals(OriginType.TWITTER, origin.getOriginType());
        String body = "Posting to Twitter " + demoData.testRunUid;
        String noteOid = "2578909845023" + demoData.testRunUid;
        AActivity activity = DemoNoteInserter.addNoteForAccount(
                demoData.getMyAccount(demoData.twitterTestAccountName),
                body, noteOid, DownloadStatus.LOADED);
        final long noteId = activity.getNote().noteId;
        assertNotEquals(0, noteId);
        String userName = MyQuery.noteIdToUsername(NoteTable.AUTHOR_ID, noteId,
                ActorInTimeline.USERNAME);
        String permalink = origin.getNotePermalink(noteId);
        String desc = "Permalink of Twitter note '" + noteOid + "' by '" + userName
                + "' at " + origin.toString() + " is " + permalink;
        assertTrue(desc, permalink.contains(userName + "/status/" + noteOid));
        assertFalse(desc, permalink.contains("://api."));
    }

    @Test
    public void testNameFix() {
        checkOneName("o.mrblog.nl", " o. mrblog. nl ");
        checkOneName("o.mr-blog.nl", " o.   mr-blog. nl ");
        checkOneName("Aqeel.s.instance", "Aqeel's instance");
        checkOneName("BKA.li.Public.GS", "BKA.li Public GS");
        checkOneName("Quitter.Espanol", "Quitter Español");
        checkOneName("This.is.a.funky.String", "Tĥïŝ ĩš â fůňķŷ Šťŕĭńġ");
    }
    
    private void checkOneName(String out, String in) {
        assertEquals(out, new Origin.Builder(myContextHolder.getNow(), OriginType.GNUSOCIAL).setName(in).build().getName());
    }

    @Test
    public void testHostFix() {
        checkOneHost("https://o.mrblog.nl", " o. mrBlog. nl ", true);
        checkOneHost("http://o.mrblog.nl", " o.   mrblog. nl ", false);
    }
    
    private void checkOneHost(String out, String in, boolean ssl) {
        assertEquals(out, new Origin.Builder(myContextHolder.getNow(), OriginType.GNUSOCIAL).setHostOrUrl(in).setSsl(ssl).build().getUrl().toExternalForm());
    }

    @Test
    public void testUsernameIsValid() {
        Origin origin = myContextHolder.getNow().origins().fromName(demoData.gnusocialTestOriginName);
        checkUsernameIsValid(origin, "", false);
        checkUsernameIsValid(origin, "someUser.", false);
        checkUsernameIsValid(origin, "someUser ", false);
        checkUsernameIsValid(origin, "someUser", true);
        checkUsernameIsValid(origin, "some.user", true);
        checkUsernameIsValid(origin, "some.user/GnuSocial", false);
        checkUsernameIsValid(origin, "some@user", false);

        origin = myContextHolder.getNow().origins().fromName(demoData.pumpioOriginName);
        checkUsernameIsValid(origin, "", false);
        checkUsernameIsValid(origin, "someUser.", false);
        checkUsernameIsValid(origin, "someUser ", false);
        checkUsernameIsValid(origin, "someUser", true);
        checkUsernameIsValid(origin, "some.user", true);
        checkUsernameIsValid(origin, "some.user@example.com", false);
        checkUsernameIsValid(origin, "t131t@identi.ca/PumpIo", false);
        checkUsernameIsValid(origin, "some@example.com.", false);
        checkUsernameIsValid(origin, "some@example.com", false);
        checkUsernameIsValid(origin, "some@user", false);
        checkUsernameIsValid(origin, "AndStatus@datamost.com", false);
    }

    private void checkUsernameIsValid(Origin origin, String username, boolean valid) {
        assertEquals("Username '" + username + "' " + (valid ? "is not valid" : "is valid"), valid,
                origin.isUsernameValid(username));
    }
}
