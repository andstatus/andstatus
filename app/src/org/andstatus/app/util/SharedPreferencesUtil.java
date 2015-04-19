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
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Environment;
import android.preference.ListPreference;
import android.preference.PreferenceFragment;
import android.text.TextUtils;

import java.io.IOException;
import java.io.File;
import java.text.MessageFormat;
import java.util.Map.Entry;

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

        if (context == null || TextUtils.isEmpty(prefsFileName)) {
            if (MyLog.isVerboseEnabled()) {
                MyLog.v(TAG, "delete '" + prefsFileName + "' - nothing to do");
            }
            return false;
        }
        File prefFile = new File(prefsDirectory(context), prefsFileName + FILE_EXTENSION);
        if (prefFile.exists()) {
            isDeleted = prefFile.delete();
            if (MyLog.isVerboseEnabled()) {
                MyLog.v(TAG, "The prefs file '" + prefFile.getAbsolutePath() + "' was "
                        + (isDeleted ? "" : "not ") + " deleted");
            }
        } else {
            if (MyLog.isLoggable(TAG, MyLog.DEBUG)) {
                MyLog.d(TAG, "The prefs file '" + prefFile.getAbsolutePath() + "' was not found");
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
            if (MyLog.isVerboseEnabled()) {
                MyLog.v(TAG, "rename: Nothing to do");
            }
        } else {
            File newPrefFile = new File(prefsDirectory(context), newPrefsFileName + FILE_EXTENSION);
            if (newPrefFile.exists()) {
                try {
                    if (MyLog.isVerboseEnabled()) {
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
                    if (MyLog.isVerboseEnabled()) {
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
     * @param fragment Preference Activity
     * @param preferenceKey android:key - Name of the preference key
     * @param valuesR android:entryValues
     * @param entriesR Almost like android:entries but to show in the summary (may be the same as android:entries) 
     * @param summaryR
     */
    public static void showListPreference(PreferenceFragment fragment, String preferenceKey, int valuesR, int entriesR, int summaryR) {
        ListPreference listPref = (ListPreference) fragment.findPreference(preferenceKey);
        if (listPref != null) {
            String[] values = fragment.getResources().getStringArray(valuesR);
            String[] entries = fragment.getResources().getStringArray(entriesR);
            String summary = entries[0];
            String listValue = listPref.getValue();
            for (int i = 0; i < values.length; i++) {
                if (listValue.equals(values[i])) {
                    summary = entries[i];
                    break;
                }
            }
            MessageFormat messageFormat = new MessageFormat(fragment.getText(summaryR)
                    .toString());
            listPref.setSummary(messageFormat.format(new Object[] {
                    summary
            }));
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

    public static long copyAll(SharedPreferences from, SharedPreferences to) {
        long entryCounter = 0;
        Editor editor = to.edit();
        for (Entry<String, ?> entry : from.getAll().entrySet()) {
            Object value = entry.getValue();
            if (Boolean.class.isInstance(value)) {
                editor.putBoolean(entry.getKey(), (Boolean) value);
            } else if (Integer.class.isInstance(value)) {
                editor.putInt(entry.getKey(), (Integer) value);
            } else if (Long.class.isInstance(value)) {
                editor.putLong(entry.getKey(), (Long) value);
            } else if (Float.class.isInstance(value)) {
                editor.putFloat(entry.getKey(), (Float) value);
            } else if (String.class.isInstance(value)) {
                editor.putString(entry.getKey(), value.toString());
            } else {
                MyLog.e(TAG, "Unknown type of shared preference: "
                        + value.getClass() + ", value: " + value.toString());
                entryCounter--;
            }
            entryCounter++;
        }
        editor.commit();
        return entryCounter;
    }
}
