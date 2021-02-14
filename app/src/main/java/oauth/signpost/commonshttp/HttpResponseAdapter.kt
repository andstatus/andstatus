package oauth.signpost.commonshttp

import cz.msebera.android.httpclient.HttpResponse
import java.io.IOException
import java.io.InputStream

class HttpResponseAdapter(private val response: HttpResponse?) : oauth.signpost.http.HttpResponse {
    @Throws(IOException::class)
    override fun getContent(): InputStream? {
        return response.getEntity().content
    }

    @Throws(IOException::class)
    override fun getStatusCode(): Int {
        return response.getStatusLine().statusCode
    }

    @Throws(Exception::class)
    override fun getReasonPhrase(): String? {
        return response.getStatusLine().reasonPhrase
    }

    override fun unwrap(): Any? {
        return response
    }
}