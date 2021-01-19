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

package org.andstatus.app.account;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.andstatus.app.ClassInApplicationPackage;
import org.andstatus.app.MyActivity;
import org.andstatus.app.R;
import org.andstatus.app.net.http.HttpConnection;
import org.andstatus.app.util.MyLog;

public class AccountSettingsWebActivity extends MyActivity {
    private static final String TAG = AccountSettingsWebActivity.class.getSimpleName();
    public static final String EXTRA_URLTOOPEN = ClassInApplicationPackage.PACKAGE_NAME + ".URLTOOPEN";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mLayoutId = R.layout.account_settings_web;
        super.onCreate(savedInstanceState);
        try {
            String url = getIntent().getStringExtra(EXTRA_URLTOOPEN);
            CookieManager.getInstance().setAcceptCookie(true);
            MyLog.d(TAG, "Loading the URL: " + url);
            WebView view = (WebView) findViewById(R.id.accountSettingsWebView);
            view.getSettings().setBuiltInZoomControls(true);
            view.getSettings().setJavaScriptEnabled(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                view.getSettings().setSafeBrowsingEnabled(false);
            }
            // see http://stackoverflow.com/questions/5561709/opening-webview-not-in-new-browser
            view.setWebViewClient(new WebViewListener());
            
            view.loadUrl(url);
        } catch (Exception e) {
            MyLog.w(this, "onCreate", e);
            finish();
        }
    }
    
    private class WebViewListener extends WebViewClient {
        private volatile boolean isFinishing = false;

        private boolean isThisCallback(String url) {
            boolean isCallback = false;
            Uri uri = Uri.parse(url);
            if (uri != null && HttpConnection.CALLBACK_URI.getHost().equals(uri.getHost())) {
                isCallback = true;
                MyLog.d(TAG, "Callback to: " + url);
                if (!isFinishing) {
                    isFinishing = true;
                    Intent i = new Intent(AccountSettingsWebActivity.this, AccountSettingsActivity.class);
                    i.setData(uri);
                    startActivity(i);                
                    finish();
                }
            }
            return isCallback;
        }

        /**
         * For some reason shouldOverrideUrlLoading is not always called on redirect,
         * this is why we use another hook
         */
        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            MyLog.v(this, "onPageStarted: " + url);
            isThisCallback(url);
        }
    }
}
