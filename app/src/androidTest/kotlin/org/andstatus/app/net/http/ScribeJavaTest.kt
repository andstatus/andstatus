package org.andstatus.app.net.http

import com.github.scribejava.core.java8.Base64
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.charset.Charset

class ScribeJavaTest {

    /** Once on Android 7.0 we got this exception:
     * java.lang.NoSuchMethodError:
     * No direct method <init>(I[BZ)V in class Lorg\/apache\/commons\/codec\/binary\/Base64;
     * or its super classes (declaration of 'org.apache.commons.codec.binary.Base64'
     * appears in \/system\/framework\/org.apache.http.legacy.boot.jar)
     * at com.github.scribejava.core.base64.CommonsCodecBase64.<clinit>(CommonsCodecBase64.java:11)
     * */
    @Test
    fun base64Test() {
        val str1 = "toEncode"
        val instance = Base64.getEncoder()
        assertEquals("Encoding '$str1' using ${instance::class.qualifiedName}", "dG9FbmNvZGU=",
            instance.encodeToString(str1.toByteArray(Charset.forName("UTF-8"))))
    }

    @Test
    fun testOAuth2ServiceScopeBehavior() {
        val http = HttpConnectionOAuthStub()
        val oauth2 = HttpConnectionOAuth2()
        oauth2.data = http.data
        oauth2.oauthClientKeys = OAuthClientKeys.fromConnectionData(http.data).also {
            it.setConsumerKeyAndSecret("test_client_id", "test_client_secret")
        }

        val serviceRedirect = oauth2.getOauth2Service(true)
        val serviceRefresh = oauth2.getOauth2Service(false)

        org.junit.Assert.assertNotNull("Service with redirect (auth) should not be null", serviceRedirect)
        org.junit.Assert.assertNotNull("Service without redirect (refresh) should not be null", serviceRefresh)
    }
}
