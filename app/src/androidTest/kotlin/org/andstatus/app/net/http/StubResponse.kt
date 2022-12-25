package org.andstatus.app.net.http

import io.vavr.control.CheckedFunction
import org.andstatus.app.util.IsEmpty
import java.io.InputStream

class StubResponse(
    val strResponse: String? = null,
    val location: String? = null,
    val exception: Exception? = null,
    val streamSupplier: CheckedFunction<Unit, InputStream>? = null,
    val requestUriSubstring: String? = null
) : IsEmpty {
    companion object {
        val EMPTY: StubResponse = StubResponse()
    }

    override val isEmpty: Boolean
        get() = strResponse.isNullOrEmpty() && location.isNullOrEmpty() && streamSupplier == null

    override fun toString(): String = "Response: {" +
        (if (strResponse.isNullOrEmpty()) "" else "str:'$strResponse', ") +
        (if (location.isNullOrEmpty()) "" else "location:$location, ") +
        (if (exception == null) "" else "exception:$exception, ") +
        (if (streamSupplier == null) "" else "stream:present") +
        "}"
}

