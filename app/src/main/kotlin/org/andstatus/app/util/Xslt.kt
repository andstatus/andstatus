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
package org.andstatus.app.util

import android.content.Context
import java.io.StringWriter
import javax.xml.XMLConstants
import javax.xml.transform.Source
import javax.xml.transform.TransformerException
import javax.xml.transform.TransformerFactory
import javax.xml.transform.TransformerFactoryConfigurationError
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.stream.StreamSource

/**
 * XSLT utils
 * @author yvolk@yurivolkov.com
 */
object Xslt {
    private val TAG: String = Xslt::class.simpleName!!

    /**
     * Transform XML input files using supplied XSL stylesheet and return as String
     * @param resXml XML file to transform. This file is localized! It should be put into "raw-<language>" folder
     * @param resXsl XSL stylesheet. In the "raw" folder. May be single for all languages...
     * @return empty in case of error
    </language> */
    fun toHtmlString(context: Context, resXml: Int, resXsl: Int): String {
        var output = ""

        // Based on http://stackoverflow.com/questions/6215001/convert-xml-file-using-xslt-in-android
        try {

            // This file is localized! 
            val xmlSource: Source = StreamSource(context.resources.openRawResource(resXml))
            val xsltSource: Source = StreamSource(context.resources.openRawResource(resXsl))
            val transformerFactory = TransformerFactory.newInstance()
            transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            val transformer = transformerFactory.newTransformer(xsltSource)
            val stringWriter = StringWriter()
            val result = StreamResult(stringWriter)
            transformer.transform(xmlSource, result)
            output = result.writer.toString()
        } catch (e: TransformerFactoryConfigurationError) {
            MyLog.w(TAG, "Failed to transform XML to HTML", e)
        } catch (e: TransformerException) {
            MyLog.w(TAG, "Failed to transform XML to HTML", e)
        }
        return output
    }
}
