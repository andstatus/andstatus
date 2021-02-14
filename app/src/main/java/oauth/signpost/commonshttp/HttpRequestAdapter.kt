package oauth.signpost.commonshttp

import cz.msebera.android.httpclient.HttpEntity
import cz.msebera.android.httpclient.HttpEntityEnclosingRequest
import cz.msebera.android.httpclient.client.methods.HttpUriRequest
import oauth.signpost.http.HttpRequest
import java.io.IOException
import java.io.InputStream
import java.util.*

class HttpRequestAdapter(private val request: HttpUriRequest?) : HttpRequest {
    private val entity: HttpEntity? = null
    override fun getMethod(): String? {
        return request.getRequestLine().method
    }

    override fun getRequestUrl(): String? {
        return request.getURI().toString()
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

    override fun getAllHeaders(): MutableMap<String?, String?>? {
        val origHeaders = request.getAllHeaders()
        val headers = HashMap<String?, String?>()
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

    @Throws(IOException::class)
    override fun getMessagePayload(): InputStream? {
        return entity?.content
    }

    override fun unwrap(): Any? {
        return request
    }

    init {
        if (request is HttpEntityEnclosingRequest) {
            entity = (request as HttpEntityEnclosingRequest?).getEntity()
        }
    }
}