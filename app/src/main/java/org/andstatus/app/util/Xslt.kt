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

import android.content.Context;

import java.io.StringWriter;

import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
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

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            Transformer transformer = transformerFactory.newTransformer(xsltSource);
            StringWriter stringWriter = new StringWriter();
            StreamResult result = new StreamResult(stringWriter);
            transformer.transform(xmlSource, result);
            output = result.getWriter().toString();
        } catch (TransformerFactoryConfigurationError | TransformerException e) {
            MyLog.w(TAG, "Failed to transform XML to HTML", e);
        }
        return output;
    }
}
