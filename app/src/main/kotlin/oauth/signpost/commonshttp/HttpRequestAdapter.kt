package oauth.signpost.commonshttp

import cz.msebera.android.httpclient.HttpEntity
import cz.msebera.android.httpclient.HttpEntityEnclosingRequest
import cz.msebera.android.httpclient.client.methods.HttpUriRequest
import oauth.signpost.http.HttpRequest
import java.io.InputStream
import java.util.*

class HttpRequestAdapter(private val request: HttpUriRequest) : HttpRequest {
    private val entity: HttpEntity? = if (request is HttpEntityEnclosingRequest) {
        (request as HttpEntityEnclosingRequest).entity
    } else null

    override fun getMethod(): String? {
        return request.requestLine.method
    }

    override fun getRequestUrl(): String {
        return request.uri.toString()
    }

    override fun setRequestUrl(url: String?) {
        throw RuntimeException(UnsupportedOperationException())
    }

    override fun getHeader(name: String?): String? {
        val header = request.getFirstHeader(name) ?: return null
        return header.value
    }

    override fun setHeader(name: String?, value: String?) {
        request.setHeader(name, value)
    }

    override fun getAllHeaders(): MutableMap<String, String?> {
        val origHeaders = request.allHeaders
        val headers = HashMap<String, String?>()
        for (h in origHeaders) {
            headers[h.name] = h.value
        }
        return headers
    }

    override fun getContentType(): String? {
        if (entity == null) {
            return null
        }
        val header = entity.contentType ?: return null
        return header.value
    }

    override fun getMessagePayload(): InputStream? {
        return entity?.content
    }

    override fun unwrap(): Any {
        return request
    }

}
