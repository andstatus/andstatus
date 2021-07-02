package org.andstatus.app.net.http

import org.andstatus.app.util.RawResourceUtils
import org.andstatus.app.util.UriUtils
import org.junit.Assert
import org.junit.Test

class MyOAuth2AccessTokenJsonExtractorTest {
    @Test
    fun extractWhoAmI() {
        val response = RawResourceUtils.getString(org.andstatus.app.test.R.raw.activitypub_oauth_access_token_pleroma)
        Assert.assertEquals(UriUtils.toDownloadableOptional("https://pleroma.site/users/ActivityPubTester"),
                MyOAuth2AccessTokenJsonExtractor.Companion.extractWhoAmI(response))
    }
}
