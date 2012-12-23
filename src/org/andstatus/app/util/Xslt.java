/* 
 * Copyright (C) 2012 yvolk (Yuri Volkov), http://yurivolkov.com
 * Copyright (C) 2008 Torgny Bjers
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
 * @author yvolk
 */
public class Xslt {

    /**
     * Transform XML input files using supplied XSL stylesheet and return as String
     * @param resXml XML file to transform. This file is localized! It should be put into "raw-<language>" folder 
     * @param resXsl XSL stylesheet. In the "raw" folder. May be single for all languages...
     * @return empty in case of error
     */
    public static String toHtmlString(Context context, int resXml, int resXsl) {
        String output = "";

        //   If we decided to avoid any XSLT, we would write this:
        //   This file is localized! 
        // java.io.InputStream is = getResources().openRawResource(R.raw.resXml);
        //   This is how to show raw xml file:
        //   answer from: http://stackoverflow.com/questions/309424/read-convert-an-inputstream-to-a-string
        // java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        // output = s.hasNext() ? s.next() : "";
        
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
            e.printStackTrace();
        } catch (TransformerFactoryConfigurationError e) {
            e.printStackTrace();
        } catch (TransformerException e) {
            e.printStackTrace();
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

            WebView view = (WebView) activity.findViewById(resView);
            view.loadData(output,"text/html","utf-8");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
}
