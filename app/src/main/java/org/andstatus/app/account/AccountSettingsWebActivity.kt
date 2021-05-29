/* 
 * Copyright (c) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.account

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import org.andstatus.app.ClassInApplicationPackage
import org.andstatus.app.MyActivity
import org.andstatus.app.R
import org.andstatus.app.account.AccountSettingsWebActivity
import org.andstatus.app.net.http.HttpConnectionInterface
import org.andstatus.app.util.MyLog

class AccountSettingsWebActivity : MyActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        mLayoutId = R.layout.account_settings_web
        super.onCreate(savedInstanceState)
        try {
            val url = intent.getStringExtra(EXTRA_URLTOOPEN)
            CookieManager.getInstance().setAcceptCookie(true)
            MyLog.d(TAG, "Loading the URL: $url")
            val view = findViewById<View?>(R.id.accountSettingsWebView) as WebView
            view.settings.builtInZoomControls = true
            view.settings.javaScriptEnabled = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                view.settings.safeBrowsingEnabled = false
            }
            // see http://stackoverflow.com/questions/5561709/opening-webview-not-in-new-browser
            view.webViewClient = WebViewListener()
            view.loadUrl(url)
        } catch (e: Exception) {
            MyLog.w(this, "onCreate", e)
            finish()
        }
    }

    private inner class WebViewListener : WebViewClient() {
        @Volatile
        private var isFinishing = false
        private fun isThisCallback(url: String?): Boolean {
            var isCallback = false
            val uri = Uri.parse(url)
            if (uri != null && HttpConnectionInterface.Companion.CALLBACK_URI.getHost() == uri.host) {
                isCallback = true
                MyLog.d(TAG, "Callback to: $url")
                if (!isFinishing) {
                    isFinishing = true
                    val i = Intent(this@AccountSettingsWebActivity, AccountSettingsActivity::class.java)
                    i.data = uri
                    startActivity(i)
                    finish()
                }
            }
            return isCallback
        }

        /**
         * For some reason shouldOverrideUrlLoading is not always called on redirect,
         * this is why we use another hook
         */
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            MyLog.v(this, "onPageStarted: $url")
            isThisCallback(url)
        }
    }

    companion object {
        private val TAG: String? = AccountSettingsWebActivity::class.java.simpleName
        val EXTRA_URLTOOPEN: String? = ClassInApplicationPackage.PACKAGE_NAME + ".URLTOOPEN"
    }
}
