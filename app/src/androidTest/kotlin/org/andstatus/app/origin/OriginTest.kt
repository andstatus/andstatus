package org.andstatus.app.origin

import org.andstatus.app.context.ActorInTimeline
import org.andstatus.app.context.DemoData
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.TestSuite
import org.andstatus.app.data.DemoNoteInserter
import org.andstatus.app.data.DownloadStatus
import org.andstatus.app.data.MyQuery
import org.andstatus.app.database.table.NoteTable
import org.andstatus.app.net.http.SslModeEnum
import org.andstatus.app.net.social.AActivity
import org.junit.After
import org.junit.Assert
import org.junit.Test

class OriginTest {
    private val myContext: MyContext = TestSuite.initializeWithAccounts(this)

    @Test
    fun testTextLimit() {
        val urlString = "https://github.com/andstatus/andstatus/issues/41"
        val body =
            ("I set \"Shorten URL with: QTTR_AT\" URL longer than 25 Text longer than 140. Will this be shortened: "
                    + urlString)
        var origin: Origin = myContext.origins.firstOfType(OriginType.Companion.ORIGIN_TYPE_DEFAULT)
        Assert.assertEquals(OriginType.TWITTER, origin.originType)
        origin = myContext.origins.firstOfType(OriginType.TWITTER)
        Assert.assertEquals(OriginType.TWITTER, origin.originType)
        var textLimit = 280
        Assert.assertEquals("Textlimit", textLimit.toLong(), origin.getTextLimit().toLong())
        Assert.assertEquals("Short URL length", 23, origin.shortUrlLength.toLong())
        val charactersLeft = origin.charactersLeftForNote(body)
        // Depending on number of spans!
        Assert.assertTrue(
            "Characters left $charactersLeft", charactersLeft == 158
                    || charactersLeft == 142
        )
        Assert.assertFalse(origin.isMentionAsWebFingerId())
        origin = myContext.origins.firstOfType(OriginType.PUMPIO)
        textLimit = TEXT_LIMIT_MAXIMUM
        Assert.assertEquals("Textlimit", textLimit.toLong(), origin.getTextLimit().toLong())
        Assert.assertEquals("Short URL length", 0, origin.shortUrlLength.toLong())
        Assert.assertEquals(
            "Characters left", (
                    origin.getTextLimit() - body.length).toLong(),
            origin.charactersLeftForNote(body).toLong()
        )
        Assert.assertTrue(origin.isMentionAsWebFingerId())
        origin = myContext.origins.firstOfType(OriginType.GNUSOCIAL)
        textLimit = Origin.Companion.TEXT_LIMIT_FOR_WEBFINGER_ID
        val uploadLimit = 0
        var config: OriginConfig = OriginConfig.Companion.fromTextLimit(textLimit, uploadLimit.toLong())
        origin = Origin.Builder(origin).save(config).build()
        Assert.assertEquals("Textlimit", textLimit.toLong(), origin.getTextLimit().toLong())
        Assert.assertTrue(origin.isMentionAsWebFingerId())
        textLimit = 140
        config = OriginConfig.Companion.fromTextLimit(textLimit, uploadLimit.toLong())
        origin = Origin.Builder(origin).save(config).build()
        Assert.assertEquals("Textlimit", textLimit.toLong(), origin.getTextLimit().toLong())
        Assert.assertEquals("Short URL length", 0, origin.shortUrlLength.toLong())
        Assert.assertEquals(
            "Characters left", (textLimit - body.length).toLong(),
            origin.charactersLeftForNote(body).toLong()
        )
        Assert.assertFalse(origin.isMentionAsWebFingerId())
        textLimit = 0
        config = OriginConfig.Companion.fromTextLimit(textLimit, uploadLimit.toLong())
        Assert.assertTrue(config.nonEmpty)
        origin = Origin.Builder(origin).save(config).build()
        Assert.assertEquals("Textlimit", TEXT_LIMIT_MAXIMUM.toLong(), origin.getTextLimit().toLong())
        Assert.assertEquals(
            "Characters left $origin", (
                    origin.getTextLimit() - body.length
                            + if (origin.shortUrlLength > 0) origin.shortUrlLength - urlString.length else 0).toLong(),
            origin.charactersLeftForNote(body).toLong()
        )
        Assert.assertTrue(origin.isMentionAsWebFingerId())
    }

    @Test
    fun testAddDeleteOrigin() {
        val seed = System.nanoTime().toString()
        val originName = "snTest$seed"
        val originType = OriginType.GNUSOCIAL
        val hostOrUrl = "sn$seed.example.org"
        var isSsl = true
        val allowHtml = true
        val inCombinedGlobalSearch = true
        val inCombinedPublicReload = true
        val originInserter = DemoOriginInserter(myContext)
        originInserter.createOneOrigin(
            originType, originName, hostOrUrl, isSsl, SslModeEnum.SECURE, allowHtml,
            inCombinedGlobalSearch, inCombinedPublicReload
        )
        originInserter.createOneOrigin(
            originType, originName, "https://" + hostOrUrl
                    + "/somepath", isSsl, SslModeEnum.INSECURE, allowHtml,
            inCombinedGlobalSearch, false
        )
        originInserter.createOneOrigin(
            originType, originName, "https://" + hostOrUrl
                    + "/pathwithslash/", isSsl, SslModeEnum.MISCONFIGURED, allowHtml,
            false, inCombinedPublicReload
        )
        isSsl = false
        val origin = originInserter.createOneOrigin(
            originType, originName,
            hostOrUrl, isSsl, SslModeEnum.SECURE, allowHtml,
            inCombinedGlobalSearch, inCombinedPublicReload
        )
        Assert.assertEquals("New origin should have no notes", false, origin.hasNotes())
        Assert.assertEquals("Origin deleted", true, Origin.Builder(origin).delete())
    }

    @Test
    fun testPermalink() {
        val origin: Origin = myContext.origins.firstOfType(OriginType.TWITTER)
        Assert.assertEquals(OriginType.TWITTER, origin.originType)
        val body = "Posting to Twitter " + DemoData.demoData.testRunUid
        val noteOid = "2578909845023" + DemoData.demoData.testRunUid
        val activity: AActivity = DemoNoteInserter.Companion.addNoteForAccount(
            DemoData.demoData.getMyAccount(DemoData.demoData.twitterTestAccountName),
            body, noteOid, DownloadStatus.LOADED
        )
        val noteId = activity.getNote().noteId
        Assert.assertNotEquals(0, noteId)
        val userName = MyQuery.noteIdToUsername(
            NoteTable.AUTHOR_ID, noteId,
            ActorInTimeline.USERNAME
        )
        val permalink = origin.getNotePermalink(noteId) ?: throw IllegalStateException("No permalink")
        val desc = ("Permalink of Twitter note '" + noteOid + "' by '" + userName
                + "' at " + origin.toString() + " is " + permalink)
        Assert.assertTrue(desc, permalink.contains("$userName/status/$noteOid"))
        Assert.assertFalse(desc, permalink.contains("://api."))
    }

    @Test
    fun testNameFix() {
        checkOneName("o.mrblog.nl", " o. mrblog. nl ")
        checkOneName("o.mr-blog.nl", " o.   mr-blog. nl ")
        checkOneName("Aqeel.s.instance", "Aqeel's instance")
        checkOneName("BKA.li.Public.GS", "BKA.li Public GS")
        checkOneName("Quitter.Espanol", "Quitter Español")
        checkOneName("This.is.a.funky.String", "Tĥïŝ ĩš â fůňķŷ Šťŕĭńġ")
    }

    private fun checkOneName(out: String?, `in`: String?) {
        Assert.assertEquals(out, Origin.Builder(myContext, OriginType.GNUSOCIAL).setName(`in`).build().name)
    }

    @Test
    fun testHostFix() {
        checkOneHost("https://o.mrblog.nl", " o. mrBlog. nl ", true)
        checkOneHost("http://o.mrblog.nl", " o.   mrblog. nl ", false)
    }

    private fun checkOneHost(out: String?, inHost: String?, ssl: Boolean) {
        Assert.assertEquals(
            out, Origin.Builder(myContext, OriginType.GNUSOCIAL)
                .setHostOrUrl(inHost).setSsl(ssl).build().url?.toExternalForm()
        )
    }

    @Test
    fun testUsernameIsValid() {
        var origin: Origin = myContext.origins.fromName(DemoData.demoData.gnusocialTestOriginName)
        checkUsernameIsValid(origin, "", false)
        checkUsernameIsValid(origin, "someUser.", false)
        checkUsernameIsValid(origin, "someUser ", false)
        checkUsernameIsValid(origin, "someUser", true)
        checkUsernameIsValid(origin, "some.user", true)
        checkUsernameIsValid(origin, "some.user/GnuSocial", false)
        checkUsernameIsValid(origin, "some@user", false)
        origin = myContext.origins.fromName(DemoData.demoData.pumpioOriginName)
        checkUsernameIsValid(origin, "", false)
        checkUsernameIsValid(origin, "someUser.", false)
        checkUsernameIsValid(origin, "someUser ", false)
        checkUsernameIsValid(origin, "someUser", true)
        checkUsernameIsValid(origin, "some.user", true)
        checkUsernameIsValid(origin, "some.user@example.com", false)
        checkUsernameIsValid(origin, "t131t@identi.ca/PumpIo", false)
        checkUsernameIsValid(origin, "some@example.com.", false)
        checkUsernameIsValid(origin, "some@example.com", false)
        checkUsernameIsValid(origin, "some@user", false)
        checkUsernameIsValid(origin, "AndStatus@datamost.com", false)
    }

    private fun checkUsernameIsValid(origin: Origin, username: String?, valid: Boolean) {
        Assert.assertEquals(
            "Username '" + username + "' " + if (valid) "is not valid" else "is valid", valid,
            origin.isUsernameValid(username)
        )
    }

    @After
    fun tearDown() {
        myContext.setExpired { this.toString() }
    }
}
