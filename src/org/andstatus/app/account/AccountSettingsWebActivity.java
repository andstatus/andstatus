/* 
 * Copyright (c) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.andstatus.app.R;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.util.MyLog;

public class AccountSettingsWebActivity extends Activity {
    private static final String TAG = AccountSettingsWebActivity.class.getSimpleName();
    private static final String packageName = AccountSettingsWebActivity.class.getPackage().getName();
    public static final String EXTRA_URLTOOPEN = packageName + ".URLTOOPEN";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.account_settings_web);
            String url = getIntent().getStringExtra(EXTRA_URLTOOPEN);
            MyLog.d(TAG, "Loading the URL: " + url);
            WebView webView = (WebView) findViewById(R.id.accountSettingsWebView);
            webView.setWebViewClient(new WebViewListener()); // see http://stackoverflow.com/questions/5561709/opening-webview-not-in-new-browser
            
            webView.loadUrl(url);
        } catch (Exception e) {
            MyLog.e(this, "onCreate", e);
            finish();
        }
    }
    
    private class WebViewListener extends WebViewClient {
        private boolean isFinishing = false;

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            MyLog.v(this, "shouldOverrideUrlLoading to: " + url);
            if (isThisCallback(url)) {
                return true;
            }
            view.loadUrl(url);
            return true;
        }

        private boolean isThisCallback(String url) {
            boolean isCallback = false;
            Uri uri = Uri.parse(url);
            if (uri != null && Origin.CALLBACK_URI.getHost().equals(uri.getHost())) {
                isCallback = true;
                MyLog.d(TAG, "Callback to: " + url);
                if (!isFinishing) {
                    isFinishing = true;
                    //Intent i = new Intent(Intent.ACTION_VIEW, uri);
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
            if (!isThisCallback(url)) {
                super.onPageStarted(view, url, favicon);
            }
        }
        
    }
}
