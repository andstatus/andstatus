package oauth.signpost.commonshttp

import cz.msebera.android.httpclient.HttpResponse
import java.io.InputStream

class HttpResponseAdapter(private val response: HttpResponse) : oauth.signpost.http.HttpResponse {
    override fun getContent(): InputStream? {
        return response.entity.content
    }

    override fun getStatusCode(): Int {
        return response.statusLine.statusCode
    }

    override fun getReasonPhrase(): String? {
        return response.statusLine.reasonPhrase
    }

    override fun unwrap(): Any {
        return response
    }
}
