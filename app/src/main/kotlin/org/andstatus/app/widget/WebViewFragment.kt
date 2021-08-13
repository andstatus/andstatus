/*
 * Copyright (C) 2018 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.widget

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RawRes
import androidx.fragment.app.Fragment
import org.andstatus.app.R
import org.andstatus.app.context.MyLocale
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.SharedPreferencesUtil
import org.andstatus.app.util.Xslt

class WebViewFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val context = inflater.context
        val output = getTransformedContent(context)
        return try {
            val view = inflater.inflate(R.layout.webview_fragment, container, false) as WebView
            // See http://stackoverflow.com/questions/14474223/utf-8-not-encoding-html-in-webview-android
            view.settings.defaultTextEncodingName = "utf-8"
            view.settings.textZoom = configuredTextZoom()
            view.settings.javaScriptEnabled = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                view.settings.safeBrowsingEnabled = false
            }
            // Used this answer for adding a stylesheet: http://stackoverflow.com/a/7736654/297710
            // See also http://stackoverflow.com/questions/13638892/where-is-the-path-file-android-asset-documented
            view.loadDataWithBaseURL("file:///android_asset/", output, "text/html", "utf-8", null)
            view
        } catch (e: Throwable) {
            val view = inflater.inflate(R.layout.empty_layout, container, false) as LinearLayout
            val contentView = view.findViewById<TextView?>(R.id.content)
            val text = "Error initializing WebView: ${e.message}\n\n$output"
            contentView.text = text
            MyLog.w(this, text, e)
            view
        }
    }

    private fun getTransformedContent(context: Context): String {
        return try {
            var output: String = Xslt.toHtmlString(context, arguments?.getInt(SOURCE_XML) ?: 0,
                    arguments?.getInt(SOURCE_XSL) ?: 0)
            if (!MyLocale.isEnLocale()) {
                output = output.replace("Translator credits",
                        context.getText(R.string.translator_credits) as String)
            }
            output
        } catch (e: Exception) {
            "Error during transformation: " + e.message
        }
    }

    companion object {
        private val SOURCE_XML: String = "sourceXml"
        private val SOURCE_XSL: String = "sourceXsl"
        fun from(@RawRes resXml: Int, @RawRes resXsl: Int): WebViewFragment {
            val fragment = WebViewFragment()
            val bundle = Bundle()
            bundle.putInt(SOURCE_XML, resXml)
            bundle.putInt(SOURCE_XSL, resXsl)
            fragment.arguments = bundle
            return fragment
        }

        private fun configuredTextZoom(): Int {
            return when (SharedPreferencesUtil.getString(MyPreferences.KEY_THEME_SIZE, "")) {
                "Large" -> 170
                "Larger" -> 145
                "Smaller" -> 108
                "Small" -> 90
                else -> 125
            }
        }
    }
}
