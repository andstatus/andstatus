/*
 * Copyright (C) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.origin

import android.content.Intent
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import org.andstatus.app.IntentExtra
import org.andstatus.app.R
import org.andstatus.app.context.ActivityTest
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.TestSuite
import org.andstatus.app.data.DbUtils
import org.andstatus.app.lang.SelectableEnumList
import org.andstatus.app.net.http.SslModeEnum
import org.andstatus.app.util.EspressoUtils.waitForIdleSync
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.TriState
import org.andstatus.app.util.UrlUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * @author yvolk@yurivolkov.com
 */
class OriginEditorTest : ActivityTest<OriginEditor>() {

    override fun getActivityClass(): Class<OriginEditor> {
        TestSuite.initialize(this)
        return OriginEditor::class.java
    }

    @Test
    fun test() {
        val originType = OriginType.GNUSOCIAL
        val originName = "oe" + System.currentTimeMillis()
        var host = "$originName.example.com"
        var isSsl = false
        var allowHtml = true
        forOneOrigin(originType, originName, host, isSsl, SslModeEnum.SECURE, allowHtml, TriState.UNKNOWN,
                TriState.UNKNOWN,
                true, true)
        host = "$originName.some.example.com"
        isSsl = true
        allowHtml = false
        forOneOrigin(originType, originName, host, isSsl, SslModeEnum.INSECURE, allowHtml, TriState.UNKNOWN,
                TriState.UNKNOWN,
                false, true)
        host = "$originName. я badhost.example.com"
        forOneOrigin(originType, originName, host, isSsl, SslModeEnum.MISCONFIGURED, allowHtml, TriState.UNKNOWN,
                TriState.TRUE,
                true, false)
        host = "http://$originName.fourth. example. com "
        forOneOrigin(originType, originName, host, isSsl, SslModeEnum.SECURE, allowHtml, TriState.UNKNOWN,
                TriState.FALSE,
                false, false)
        host = "http://$originName.fifth.example.com/status"
        forOneOrigin(originType, originName, host, isSsl, SslModeEnum.INSECURE, allowHtml, TriState.UNKNOWN,
                TriState.UNKNOWN,
                true, true)
    }

    private fun forOneOrigin(originType: OriginType, originName: String,
                             hostOrUrl: String, isSsl: Boolean, sslMode: SslModeEnum,
                             allowHtml: Boolean, mentionAsWebFingerId: TriState,
                             useLegacyHttpProtocol: TriState,
                             inCombinedGlobalSearch: Boolean, inCombinedPublicReload: Boolean) {
        val method = "OriginEditorTest"
        val originOld: Origin =  MyContextHolder.myContextHolder.getNow().origins.fromName(originName)
        val intent = Intent()
        if (originOld.isPersistent()) {
            intent.action = Intent.ACTION_EDIT
            intent.putExtra(IntentExtra.ORIGIN_NAME.key, originOld.name)
        } else {
            intent.action = Intent.ACTION_INSERT
        }
        val activity = activity
        activity.runOnUiThread { // This is needed because the activity is actually never
            // destroyed
            // and onCreate occurs only once.
            activity.onNewIntent(intent)
        }
        waitForIdleSync()
        val buttonSave = activity.findViewById<Button?>(R.id.button_save)
        val spinnerOriginType = activity.findViewById<Spinner?>(R.id.origin_type)
        val editTextOriginName = activity.findViewById<EditText?>(R.id.origin_name)
        val editTextHost = activity.findViewById<EditText?>(R.id.host)
        val checkBoxIsSsl = activity.findViewById<CheckBox?>(R.id.is_ssl)
        val spinnerSslMode = activity.findViewById<Spinner?>(R.id.ssl_mode)
        val checkBoxAllowHtml = activity.findViewById<CheckBox?>(R.id.allow_html)
        val spinnerMentionAsWebFingerId = activity.findViewById<Spinner?>(R.id.mention_as_webfingerid)
        val spinnerUseLegacyHttpProtocol = activity.findViewById<Spinner?>(R.id.use_legacy_http_protocol)
        val clicker: Runnable = object : Runnable {
            override fun run() {
                spinnerOriginType.setSelection(SelectableEnumList.Companion.newInstance<OriginType>(OriginType::class.java).getIndex(originType))
                editTextOriginName.setText(originName)
                editTextHost.setText(hostOrUrl)
                checkBoxIsSsl.isChecked = isSsl
                spinnerSslMode.setSelection(sslMode.getEntriesPosition())
                checkBoxAllowHtml.isChecked = allowHtml
                spinnerMentionAsWebFingerId.setSelection(mentionAsWebFingerId.getEntriesPosition())
                spinnerUseLegacyHttpProtocol.setSelection(useLegacyHttpProtocol.getEntriesPosition())
                (activity.findViewById<View?>(R.id.in_combined_global_search) as CheckBox).isChecked = inCombinedGlobalSearch
                (activity.findViewById<View?>(R.id.in_combined_public_reload) as CheckBox).isChecked = inCombinedPublicReload
                DbUtils.waitMs(method, 1000)
                MyLog.v(this, "$method-Log before click")
                buttonSave.performClick()
            }
        }
        MyLog.v(this, "$method-Log before run clicker 1")
        activity.runOnUiThread(clicker)
        waitForIdleSync()
        DbUtils.waitMs(method, 200)
        val origin: Origin =  MyContextHolder.myContextHolder.getNow().origins.fromName(originName)
        assertEquals("Origin '$originName' added", originName, origin.name)
        assertEquals(originType, origin.originType)
        if (hostOrUrl.contains("bad")) {
            assertEquals(originOld.url, origin.url)
        } else {
            val url2 = UrlUtils.buildUrl(hostOrUrl, isSsl)
            if (url2 != origin.url) {
                if (!UrlUtils.isHostOnly(url2) && !hostOrUrl.endsWith("/")) {
                    assertEquals(UrlUtils.buildUrl("$hostOrUrl/", isSsl), origin.url)
                } else {
                    assertEquals(UrlUtils.buildUrl(hostOrUrl, isSsl), origin.url)
                }
            }
        }
        assertEquals(isSsl, origin.isSsl())
        assertEquals("$origin, Spinner: ${spinnerSslMode.selectedItem}, " +
                "in activity: ${activity.spinnerSslMode?.selectedItem}", sslMode, origin.getSslMode())
        assertEquals(allowHtml, origin.isHtmlContentAllowed())
        assertEquals(mentionAsWebFingerId, origin.getMentionAsWebFingerId())
        assertEquals(useLegacyHttpProtocol, origin.useLegacyHttpProtocol())
        assertEquals(inCombinedGlobalSearch, origin.isInCombinedGlobalSearch())
        assertEquals(inCombinedPublicReload, origin.isInCombinedPublicReload())
    }

    @After
    fun tearDown() {
         MyContextHolder.myContextHolder.initialize(null, this)
    }
}
