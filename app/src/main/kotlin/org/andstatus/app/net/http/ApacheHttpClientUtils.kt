/*
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.andstatus.app.net.http

import cz.msebera.android.httpclient.HttpEntity
import cz.msebera.android.httpclient.NameValuePair
import cz.msebera.android.httpclient.client.HttpClient
import cz.msebera.android.httpclient.entity.ContentType
import cz.msebera.android.httpclient.entity.mime.MultipartEntityBuilder
import cz.msebera.android.httpclient.message.BasicNameValuePair
import cz.msebera.android.httpclient.protocol.HTTP
import org.andstatus.app.data.MyContentType.Companion.uri2MimeType
import org.andstatus.app.util.FileUtils
import org.andstatus.app.util.JsonUtils
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.*

internal object ApacheHttpClientUtils {
    fun buildMultipartFormEntityBytes(request: HttpRequest): MultipartFormEntityBytes {
        val httpEntity = multiPartFormEntity(request)
        return MultipartFormEntityBytes(
                httpEntity.getContentType().name,
                httpEntity.getContentType().value,
                httpEntityToBytes(httpEntity))
    }

    fun multiPartFormEntity(request: HttpRequest): HttpEntity {
        val builder = MultipartEntityBuilder.create()
        request.postParams.ifPresent { formParams: JSONObject ->
            val iterator = formParams.keys()
            val textContentType = ContentType.create(HTTP.PLAIN_TEXT_TYPE, HTTP.UTF_8)
            while (iterator.hasNext()) {
                val name = iterator.next()
                val value = JsonUtils.optString(formParams, name)
                // see http://stackoverflow.com/questions/19292169/multipartentitybuilder-and-charset
                builder.addTextBody(name, value, textContentType)
            }
        }
        val contentResolver = request.myContext().context.contentResolver
                ?: throw ConnectionException.fromStatusCode(StatusCode.NOT_FOUND,
                        "Content Resolver is null in " + request.myContext().context
                )
        if (request.mediaUri.isPresent) {
            val mediaUri = request.mediaUri.get()
            try {
                contentResolver.openInputStream(mediaUri).use { ins ->
                    val mediaContentType = ContentType.create(
                            uri2MimeType(contentResolver, mediaUri))
                    builder.addBinaryBody(request.mediaPartName, FileUtils.getBytes(ins), mediaContentType, mediaUri.path)
                }
            } catch (e: SecurityException) {
                throw ConnectionException.hardConnectionException("mediaUri='$mediaUri'", e)
            } catch (e: IOException) {
                throw ConnectionException.hardConnectionException("mediaUri='$mediaUri'", e)
            }
        }
        return builder.build()
    }

    private fun httpEntityToBytes(httpEntity: HttpEntity): ByteArray {
        val out = ByteArrayOutputStream()
        try {
            httpEntity.writeTo(out)
            out.flush()
        } catch (e: IOException) {
            throw ConnectionException("httpEntityToBytes", e)
        }
        return out.toByteArray()
    }

    fun jsonToNameValuePair(jso: JSONObject): MutableList<NameValuePair> {
        val formParams: MutableList<NameValuePair> = ArrayList()
        val iterator = jso.keys()
        while (iterator.hasNext()) {
            val name = iterator.next()
            val value = JsonUtils.optString(jso, name)
            if (value.isNotEmpty()) {
                formParams.add(BasicNameValuePair(name, value))
            }
        }
        return formParams
    }

    fun getHttpClient(sslMode: SslModeEnum): HttpClient {
        return if (sslMode == SslModeEnum.MISCONFIGURED) MisconfiguredSslHttpClientFactory.getHttpClient()
        else MyHttpClientFactory.getHttpClient(sslMode)
    }
}
