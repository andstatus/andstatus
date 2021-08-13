package org.andstatus.app.net.http

import com.github.scribejava.core.base64.Base64
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
        val instance = Base64.getInstance()
        assertEquals("Encoding '$str1' using ${instance::class.qualifiedName}", "dG9FbmNvZGU=",
            Base64.encode(str1.toByteArray(Charset.forName("UTF-8"))))
    }
}
