/* Copyright (c) 2009 Matthias Kaeppler
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package oauth.signpost.commonshttp

import cz.msebera.android.httpclient.HttpRequest
import cz.msebera.android.httpclient.client.methods.HttpUriRequest
import oauth.signpost.AbstractOAuthConsumer

/**
 * Supports signing HTTP requests of type [cz.msebera.android.httpclient.HttpRequest].
 *
 * @author Matthias Kaeppler
 */
class CommonsHttpOAuthConsumer(consumerKey: String?, consumerSecret: String?) : AbstractOAuthConsumer(consumerKey, consumerSecret) {
    override fun wrap(request: Any): oauth.signpost.http.HttpRequest {
        require(request is HttpRequest) {
            ("This consumer expects requests of type "
                    + HttpRequest::class.java.canonicalName)
        }
        return HttpRequestAdapter(request as HttpUriRequest)
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}
