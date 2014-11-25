/*
 * Copyright (C) 2010-2014 yvolk (Yuri Volkov), http://yurivolkov.com
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
import android.text.TextUtils;

import java.text.MessageFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * i18n - Internationalization utilities 
 */
public class I18n {
    private static final String TAG = I18n.class.getSimpleName();
    
    private I18n() {
    }
    
    /**
     * The function enables to have different localized message formats
     *  (actually, any number of them)
     *  for different quantities of something.
     *  
     * E.g. in Russian we need at least three different messages notifying User 
     *  about the number of new tweets:
     *  1 твит  (same for 21, 31, ...)
     *  2 твита ( same for 3, 4, 22, ... )
     *  5 твитов (same for 5, ... 11, 12, 20 ...)
     *  ...
     *  see /res/values-ru/arrays.xml (R.array.appwidget_message_patterns)
     * 
     * @author yvolk@yurivolkov.com
     */
    public static String formatQuantityMessage(Context context, int messageFormat,
            int quantityOfSomething, int arrayPatterns, int arrayFormats) {
        String submessage = "";
        String message = "";
        String toMatch = Integer.toString(quantityOfSomething);
        String[] p = context.getResources().getStringArray(arrayPatterns);
        String[] f = context.getResources().getStringArray(arrayFormats);
        String subformat = "{0} ???";
        for (int i = 0; i < p.length; i++) {
            Pattern pattern = Pattern.compile(p[i]);
            Matcher m = pattern.matcher(toMatch);
            if (m.matches()) {
                subformat = f[i];
                break;
            }
        }
        MessageFormat msf = new MessageFormat(subformat);
        submessage = msf.format(new Object[] { quantityOfSomething });
        
        if (messageFormat == 0) {
            message = submessage;
        } else {
            MessageFormat mf = new MessageFormat(context.getText(messageFormat).toString());
            message = mf.format(new Object[] { submessage });
        }
        return message;
    }
    
    public static CharSequence trimTextAt(CharSequence text, int maxLength) {
        if (TextUtils.isEmpty(text) || maxLength < 1) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        if (text.length() == maxLength+1 && isSpace(text.charAt(maxLength))) {
            return text.subSequence(0, maxLength);
        }
        if (maxLength == 1 && !isSpace(text.charAt(0))) {
            return text.subSequence(0, 1);
        }
        int lastSpace = maxLength-1;
        while (lastSpace > 1) {
            if (isSpace(text.charAt(lastSpace))) {
                break;
            }
            lastSpace--;
        }
        MyLog.v(TAG, "'" + text.subSequence(0, lastSpace) + "…" + "' max=" + maxLength);
        return text.subSequence(0, lastSpace) + "…";
    }

    private static boolean isSpace(char charAt) {
        return " ,.;:()[]{}-_=+\"'".indexOf(charAt) >=0;
    }
}
