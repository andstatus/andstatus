package org.andstatus.app.util

import android.net.Uri
import io.vavr.control.Try
import java.net.URL

/**
 * Magnet URI scheme: https://en.wikipedia.org/wiki/Magnet_URI_scheme
 *
 * Feature discussion: https://github.com/andstatus/andstatus/issues/535
 * Related discussion: https://socialhub.activitypub.rocks/t/content-addressing-and-urn-resolution/1674
 */
data class MagnetUri(val dn: String, val xt: List<Uri>, val xs: Uri) {

    companion object {
        fun Uri.getDownloadableUrl(): URL? = takeIf { UriUtils.isDownloadable(it) }
            ?.let { uri -> uri.toString().tryMargetUri().map(MagnetUri::xs).getOrElse(uri) }
            ?.let { URL(it.toString()) }


        fun String?.tryMargetUri(): Try<MagnetUri> = this?.trim()
            ?.split("magnet:?")
            ?.takeIf { it.size == 2 && it.get(0).isEmpty() }
            ?.get(1)
            ?.let { Try.success(it) }
            ?.flatMap(::parseData)
            ?: TryUtils.notFound()

        private fun parseData(data: String): Try<MagnetUri> {
            val params = data.split("&")
                .fold(HashMap<String, List<String>>()) { acc, param ->
                    val parts = param.split('=', ignoreCase = false, limit = 2)
                    if (parts.size == 2) {
                        val name = parts[0].let { nn ->
                            val nameParts = nn.split('.', ignoreCase = false, limit = 2)
                            if (nameParts.size == 2 && nameParts[0].isNotEmpty())
                                nameParts[0]
                            else nn
                        }
                        val value = parts[1]
                        if (value.isNotEmpty()) {
                            acc.compute(name) { k, v ->
                                v?.let { v + value } ?: listOf(value)
                            }
                        }
                    }
                    acc
                }
            return params["xs"]
                ?.mapNotNull { it ->
                    UriUtils.toDownloadableOptional(it).orElseGet { -> null }
                }
                ?.firstOrNull()
                ?.let { xs ->
                    val dn = params["dn"]?.firstOrNull() ?: ""
                    val xt = params["xt"]
                        ?.mapNotNull { it -> UriUtils.toOptional(it).orElseGet { -> null } }
                        ?: emptyList()
                    Try.success(MagnetUri(dn, xt, xs))
                }
                ?: TryUtils.failure("Failed to parse Magnet URI data: '$data'")
        }

    }
}
