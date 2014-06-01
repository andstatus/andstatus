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
import android.os.Environment;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.text.TextUtils;

import java.io.IOException;
import java.io.File;
import java.text.MessageFormat;

public class SharedPreferencesUtil {
    private static final String TAG = SharedPreferencesUtil.class.getSimpleName();
    
    public static final String FILE_EXTENSION = ".xml";

    private SharedPreferencesUtil() {
    }

    public static File sharedPreferencesPath(Context context) {
        return new File(prefsDirectory(context),
                context.getPackageName() + "_preferences" + FILE_EXTENSION);
    }
    
    /**
     * @param Context
     * @return Directory for files of SharedPreferences
     */
    public static File prefsDirectory(Context context) {
        File dir1 = new File(Environment.getDataDirectory(), "data/"
                + context.getPackageName());
        return new File(dir1, "shared_prefs");
    }

    /**
     * Does the preferences file exist?
     */
    public static boolean exists(Context context, String prefsFileName) {
        boolean yes = false;

        if (context == null || prefsFileName == null || prefsFileName.length() == 0) {
            // no
        } else {
            try {
                File prefFile = new File(prefsDirectory(context), prefsFileName + FILE_EXTENSION);
                yes = prefFile.exists();
            } catch (Exception e) {
                MyLog.e(TAG, e);
            }
        }
        return yes;
    }

    /**
     * Delete the preferences file!
     * 
     * @return Was the file deleted?
     */
    public static boolean delete(Context context, String prefsFileName) {
        boolean isDeleted = false;

        if (context == null || prefsFileName == null || prefsFileName.length() == 0) {
            if (MyLog.isLoggable(TAG, MyLog.VERBOSE)) {
                MyLog.v(TAG, "delete: Nothing to do");
            }
        } else {
            File prefFile = new File(prefsDirectory(context), prefsFileName + FILE_EXTENSION);
            if (prefFile.exists()) {
                isDeleted = prefFile.delete();
                if (MyLog.isLoggable(TAG, MyLog.VERBOSE)) {
                    MyLog.v(TAG, "The prefs file '" + prefFile.getAbsolutePath() + "' was "
                            + (isDeleted ? "" : "not ") + " deleted");
                }
            } else {
                if (MyLog.isLoggable(TAG, MyLog.DEBUG)) {
                    MyLog.d(TAG, "The prefs file '" + prefFile.getAbsolutePath() + "' was not found");
                }
            }
        }
        return isDeleted;
    }

    /**
     * Rename the preferences file
     * 
     * @return Was the file renamed?
     */
    public static boolean rename(Context context, String oldPrefsFileName, String newPrefsFileName) {
        boolean isRenamed = false;

        if (context == null || oldPrefsFileName == null || oldPrefsFileName.length() == 0
                || newPrefsFileName == null || newPrefsFileName.length() == 0) {
            if (MyLog.isLoggable(TAG, MyLog.VERBOSE)) {
                MyLog.v(TAG, "rename: Nothing to do");
            }
        } else {
            File newPrefFile = new File(prefsDirectory(context), newPrefsFileName + FILE_EXTENSION);
            if (newPrefFile.exists()) {
                try {
                    if (MyLog.isLoggable(TAG, MyLog.VERBOSE)) {
                        MyLog.v(TAG, "rename: New file already exists: \""
                                + newPrefFile.getCanonicalPath() + "\"");
                    }
                } catch (IOException e) {
                    MyLog.e(TAG, e);
                }
            } else {
                File oldPrefFile = new File(prefsDirectory(context), oldPrefsFileName + FILE_EXTENSION);
                if (oldPrefFile.exists()) {
                    isRenamed = oldPrefFile.renameTo(newPrefFile);
                    if (MyLog.isLoggable(TAG, MyLog.VERBOSE)) {
                        MyLog.v(TAG, "The prefs file '" + oldPrefFile.getAbsolutePath() + "' was "
                                + (isRenamed ? "" : "not ") + " renamed");
                    }
                } else {
                    if (MyLog.isLoggable(TAG, MyLog.DEBUG)) {
                        MyLog.d(TAG, "The prefs file '" + oldPrefFile.getAbsolutePath()
                                + "' was not found");
                    }
                }
            }
        }
        return isRenamed;
    }

    /**
     * @param pa Preference Activity
     * @param keyPreference android:key - Name of the preference key
     * @param entryValuesR android:entryValues
     * @param displayR Almost like android:entries but to show in the summary (may be the same as android:entries) 
     * @param summaryR
     */
    public static void showListPreference(PreferenceActivity pa, String keyPreference, int entryValuesR, int displayR, int summaryR) {
        String displayParm = "";
        ListPreference listPref = (ListPreference) pa.findPreference(keyPreference);
        if (listPref != null) {
            String[] k = pa.getResources().getStringArray(entryValuesR);
            String[] d = pa.getResources().getStringArray(displayR);
            displayParm = d[0];
            String listValue = listPref.getValue();
            for (int i = 0; i < k.length; i++) {
                if (listValue.equals(k[i])) {
                    displayParm = d[i];
                    break;
                }
            }
            MessageFormat sf = new MessageFormat(pa.getText(summaryR)
                    .toString());
            listPref.setSummary(sf.format(new Object[] {
                displayParm
            }));
        } else {
            displayParm = keyPreference + " was not found";
        }
    }

    /**
     * Returns true if the string is null or 0-length or "null"
     * 
     * @param str the string to be examined
     * @return true if str is null or zero length or "null"
     */
    public static boolean isEmpty(CharSequence str) {
        return TextUtils.isEmpty(str) || "null".equals(str);
    }

    public static int isTrueAsInt(Object o) {
        return isTrue(o) ? 1 : 0;
    }
    
    /**
     * Returns true not only for boolean true or String "true", but for "1" also
     */
    public static boolean isTrue(Object o) {
        boolean is = false;
        try {
            if (o != null) {
                if (o instanceof Boolean) {
                    is = (Boolean) o;
                } else {
                    String string = o.toString();
                    if (!isEmpty(string)) {
                        is = Boolean.parseBoolean(string);
                        if (!is && string.compareTo("1") == 0) {
                            is = true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            MyLog.v(TAG, o.toString(), e);
        }
        return is;
    }
}
