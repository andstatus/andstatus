package org.andstatus.app.net.http;

import org.andstatus.app.util.RawResourceUtils;
import org.andstatus.app.util.UriUtils;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class MyOAuth2AccessTokenJsonExtractorTest {

    @Test
    public void extractWhoAmI() throws IOException {
        String response = RawResourceUtils.getString(org.andstatus.app.tests.R.raw.activitypub_oauth_access_token_pleroma);
        assertEquals(UriUtils.toDownloadableOptional("https://pleroma.site/users/ActivityPubTester"),
                MyOAuth2AccessTokenJsonExtractor.extractWhoAmI(response));
    }
}
