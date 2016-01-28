/* 
 * Copyright (C) 2012 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.util;

import android.app.Activity;
import android.content.Context;
import android.webkit.WebView;

import org.andstatus.app.R;
import org.andstatus.app.context.MyPreferences;

import java.io.StringWriter;

import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

/**
 * XSLT utils 
 * @author yvolk@yurivolkov.com
 */
public class Xslt {
    private static final String TAG = Xslt.class.getSimpleName();

    private Xslt() {
    }
    
    /**
     * Transform XML input files using supplied XSL stylesheet and return as String
     * @param resXml XML file to transform. This file is localized! It should be put into "raw-<language>" folder 
     * @param resXsl XSL stylesheet. In the "raw" folder. May be single for all languages...
     * @return empty in case of error
     */
    public static String toHtmlString(Context context, int resXml, int resXsl) {
        String output = "";
        
        // Based on http://stackoverflow.com/questions/6215001/convert-xml-file-using-xslt-in-android
        try {

            // This file is localized! 
            Source xmlSource = new StreamSource(context.getResources().openRawResource(resXml));
            Source xsltSource = new StreamSource(context.getResources().openRawResource(resXsl));

            TransformerFactory transFact = TransformerFactory.newInstance();
            Transformer trans = transFact.newTransformer(xsltSource);
            StringWriter sr = new StringWriter();
            StreamResult result = new StreamResult(sr);            
            trans.transform(xmlSource, result);
            output = result.getWriter().toString();
        } catch (TransformerConfigurationException e) {
            MyLog.e(TAG, e);
        } catch (TransformerFactoryConfigurationError e) {
            MyLog.e(TAG, e);
        } catch (TransformerException e) {
            MyLog.e(TAG, e);
        }
        return output;
    }

    /**
     * Transform XML input files using supplied XSL stylesheet and show it in the WebView
     * @param activity Activity hosting the WebView
     * @param resView WebView in which the output should be shown
     * @param resXml XML file to transform. This file is localized! It should be put into "raw-<language>" folder 
     * @param resXsl XSL stylesheet. In the "raw" folder. May be single for all languages...
     */
    public static void toWebView(Activity activity, int resView, int resXml, int resXsl) {
        String output = "";
        try {
            output = toHtmlString(activity, resXml, resXsl);
            if (!MyPreferences.isEnLocale()) {
                final String key1 = "Translator credits";
                output = output.replace(key1, activity.getText(R.string.translator_credits));
            }
            // See http://stackoverflow.com/questions/14474223/utf-8-not-encoding-html-in-webview-android
            WebView view = (WebView) activity.findViewById(resView);
            view.getSettings().setDefaultTextEncodingName("utf-8");
            view.getSettings().setBuiltInZoomControls(true); 
            view.getSettings().setJavaScriptEnabled(true);
            // Used this answer for adding a stylesheet: http://stackoverflow.com/a/7736654/297710
            // See also http://stackoverflow.com/questions/13638892/where-is-the-path-file-android-asset-documented
            view.loadDataWithBaseURL("file:///android_asset/", output,"text/html","utf-8", null);
        } catch (Exception e) {
            MyLog.e(TAG, e);
        }
    }
    
}
