package org.andstatus.app.account;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.andstatus.app.R;
import org.andstatus.app.util.MyLog;

public class AccountSettingsWebActivity extends Activity {
    private static final String TAG = AccountSettingsWebActivity.class.getSimpleName();
    private static final String packageName = AccountSettingsWebActivity.class.getPackage().getName();
    public static final String EXTRA_URLTOOPEN = packageName + ".URLTOOPEN";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.account_settings_web);
        String url = getIntent().getStringExtra(EXTRA_URLTOOPEN);
        WebView webView = (WebView) findViewById(R.id.accountSettingsWebView);
        webView.setWebViewClient(new WebViewListener()); // see http://stackoverflow.com/questions/5561709/opening-webview-not-in-new-browser
        
        webView.loadUrl(url);
    }
    
    private class WebViewListener extends WebViewClient {

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            Uri uri = Uri.parse(url);
            MyLog.d(TAG, "Redirecting to: " + uri);
            if (uri != null && AccountSettingsActivity.CALLBACK_URI.getHost().equals(uri.getHost())) {
                //Intent i = new Intent(Intent.ACTION_VIEW, uri);
                Intent i = new Intent(AccountSettingsWebActivity.this, AccountSettingsActivity.class);
                i.setData(uri);
                startActivity(i);                
                finish();
                return true;
            } else {
                view.loadUrl(url);
                return true;
            }
        }

    }
}
