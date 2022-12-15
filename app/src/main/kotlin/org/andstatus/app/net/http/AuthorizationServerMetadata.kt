package org.andstatus.app.net.http

import java.net.URL

/** https://datatracker.ietf.org/doc/html/rfc8414 */
class AuthorizationServerMetadata(
    val issuer: URL,
    val authorization_endpoint: String?,
    val token_endpoint: String?,
    val registration_endpoint: String?
) {
    companion object {

    }
}
