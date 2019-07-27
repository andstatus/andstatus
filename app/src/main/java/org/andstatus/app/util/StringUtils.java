/*
 * Copyright (c) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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

import java.util.Arrays;
import java.util.IllegalFormatException;
import java.util.Optional;

/**
 * @author yvolk@yurivolkov.com
 */
public class StringUtils {
    private static final String TEMP_OID_PREFIX = "andstatustemp:";

    public static String stripTempPrefix(String oid) {
        return isTemp(oid) ? oid.substring(TEMP_OID_PREFIX.length()) : oid;
    }

    public static String toTempOid(String oid) {
        return toTempOidIf(true, oid);
    }

    public static String toTempOidIf(boolean transformToTempOid, String oid) {
        return !transformToTempOid || isTemp(oid) ? oid : TEMP_OID_PREFIX + oid;
    }

    public static boolean nonEmptyNonTemp(String string) {
        return !isEmptyOrTemp(string);
    }

    public static boolean isEmptyOrTemp(String string) {
        return isEmpty(string) || string.startsWith(TEMP_OID_PREFIX);
    }

    public static boolean isTemp(String string) {
        return nonEmpty(string) && string.startsWith(TEMP_OID_PREFIX);
    }

    /** empty and null strings are treated as the same */
    public static boolean equalsNotEmpty(String first, String second) {
        return notEmpty(first, "").equals(notEmpty(second, ""));
    }

    public static boolean nonEmpty(CharSequence value) {
        return !isEmpty(value);
    }

    public static boolean isEmpty(CharSequence value) {
        return value == null || value.length() == 0;
    }

    public static String notEmpty(String value, String valueIfEmpty) {
        return StringUtils.isEmpty(value) ? valueIfEmpty : value;
    }

    public static Optional<String> optNotEmpty(Object value) {
        return Optional.ofNullable(value).map(Object::toString).map(String::trim).filter(s -> s.length() > 0);
    }

    public static String notNull(String value) {
        return value == null ? "" : value;
    }

    public static long toLong(String s) {
        long value = 0;
        try {
            value = Long.parseLong(s);
        } catch (NumberFormatException e) {
            MyLog.ignored(s, e);
        }
        return value;
    }

    /**
     * From http://stackoverflow.com/questions/767759/occurrences-of-substring-in-a-string
     */
    public static int countOfOccurrences(String str, String findStr) {
        int lastIndex = 0;
        int count = 0;
        while (lastIndex != -1) {
            lastIndex = str.indexOf(findStr, lastIndex);
            if (lastIndex != -1) {
                count++;
                lastIndex += findStr.length();
            }
        }
        return count;
    }

    public static String[] addBeforeArray(String[] array, String s) {
        int length = array == null ? 0 : array.length;
        String[] ans = new String[length + 1];
        if (length > 0) {
            System.arraycopy(array, 0, ans, 1, length);
        }
        ans[0] = s;
        return ans;
    }

    public static boolean isFilled(String value) {
        return !StringUtils.isEmpty(value);
    }

    public static boolean isNewFilledValue(String oldValue, String newValue) {
        return isFilled(newValue) && (oldValue == null || !oldValue.equals(newValue));
    }

    /** Doesn't throw exceptions */
    public static String format(Context context, int resourceId, Object ... params) {
        if (resourceId == 0) return "";
        if (context == null) return "Error showing resourceId=" + resourceId + paramsToString(params);
        try {
            String format = context.getText(resourceId).toString();
            if (params == null || params.length == 0) {
                return format;
            }
            try {
                return String.format(format, params);
            } catch (IllegalFormatException e) {
                MyLog.e(context, "Error formatting resourceId=" + resourceId, e);
                return format + paramsToString(params);
            }
        } catch (Exception e2) {
            String msg = "Error formatting resourceId=" + resourceId;
            MyLog.e(context, msg, e2);
            return msg;
        }
    }

    private static String paramsToString(Object[] params) {
        return params == null
            ? ""
            : " " + (params.length == 1
                ? params[0].toString()
                : Arrays.toString(params));
    }

}
