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

package org.andstatus.app.widget;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RawRes;
import androidx.fragment.app.Fragment;

import org.andstatus.app.R;
import org.andstatus.app.context.MyLocale;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.Xslt;

public class WebViewFragment extends Fragment {
    private static final String SOURCE_XML = "sourceXml";
    private static final String SOURCE_XSL = "sourceXsl";

    public static WebViewFragment from(@RawRes int resXml, @RawRes int resXsl) {
        WebViewFragment fragment = new WebViewFragment();
        Bundle bundle = new Bundle();
        bundle.putInt(SOURCE_XML, resXml);
        bundle.putInt(SOURCE_XSL, resXsl);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Context context = inflater.getContext();
        String output = getTransformedContent(context);
        try {
            WebView view = (WebView) inflater.inflate(R.layout.webview_fragment, container, false);
            // See http://stackoverflow.com/questions/14474223/utf-8-not-encoding-html-in-webview-android
            view.getSettings().setDefaultTextEncodingName("utf-8");
            view.getSettings().setTextZoom(configuredTextZoom());
            view.getSettings().setJavaScriptEnabled(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                view.getSettings().setSafeBrowsingEnabled(false);
            }
            // Used this answer for adding a stylesheet: http://stackoverflow.com/a/7736654/297710
            // See also http://stackoverflow.com/questions/13638892/where-is-the-path-file-android-asset-documented
            view.loadDataWithBaseURL("file:///android_asset/", output,"text/html","utf-8", null);
            return view;
        } catch (Throwable e) {
            LinearLayout view = (LinearLayout) inflater.inflate(R.layout.empty_layout, container, false);
            TextView contentView = view.findViewById(R.id.content);
            contentView.setText("Error initializing WebView: " + e.getMessage() + "\n\n" + output);
            MyLog.e(this, e);
            return view;
        }
    }

    private String getTransformedContent(Context context) {
        try {
            String output = Xslt.toHtmlString(context, getArguments().getInt(SOURCE_XML),
                    getArguments().getInt(SOURCE_XSL));
            if (!MyLocale.isEnLocale()) {
                output = output.replace("Translator credits",
                        context.getText(R.string.translator_credits));
            }
            return output;
        } catch (Exception e) {
            return "Error during transformation: " + e.getMessage();
        }
    }

    private static int configuredTextZoom() {
        switch (SharedPreferencesUtil.getString(MyPreferences.KEY_THEME_SIZE, "")) {
        case "Large":
            return 170;
        case "Larger":
            return 145;
        case "Smaller":
            return 108;
        case "Small":
            return 90;
        default:
            return 125;
        }
    }
}
