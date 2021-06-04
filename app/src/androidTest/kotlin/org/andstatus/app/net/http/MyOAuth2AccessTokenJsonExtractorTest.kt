package org.andstatus.app.net.http

import org.andstatus.app.util.RawResourceUtils
import org.andstatus.app.util.UriUtils
import org.junit.Assert
import org.junit.Test
import java.io.IOException

class MyOAuth2AccessTokenJsonExtractorTest {
    @Test
    @Throws(IOException::class)
    fun extractWhoAmI() {
        val response = RawResourceUtils.getString(org.andstatus.app.tests.R.raw.activitypub_oauth_access_token_pleroma)
        Assert.assertEquals(UriUtils.toDownloadableOptional("https://pleroma.site/users/ActivityPubTester"),
                MyOAuth2AccessTokenJsonExtractor.Companion.extractWhoAmI(response))
    }
}
