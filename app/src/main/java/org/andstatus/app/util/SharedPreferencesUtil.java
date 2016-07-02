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
import android.preference.ListPreference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import org.andstatus.app.context.MyContextHolder;

import java.io.File;
import java.text.MessageFormat;
import java.util.Map.Entry;

public class SharedPreferencesUtil {
    private static final String TAG = SharedPreferencesUtil.class.getSimpleName();

    public static final String FILE_EXTENSION = ".xml";

    private SharedPreferencesUtil() {
    }

    public static File defaultSharedPreferencesPath(Context context) {
        return new File(prefsDirectory(context),
                context.getPackageName() + "_preferences" + FILE_EXTENSION);
    }

    /**
     * @return Directory for files of SharedPreferences
     */
    public static File prefsDirectory(Context context) {
        String dataDir = context.getApplicationInfo().dataDir;
        return new File(dataDir, "shared_prefs");
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
     * @param fragment Preference Activity
     * @param preferenceKey android:key - Name of the preference key
     * @param valuesR android:entryValues
     * @param entriesR Almost like android:entries but to show in the summary (may be the same as android:entries) 
     * @param summaryR If 0 then the selected entry will be put into the summary as is.
     */
    public static void showListPreference(PreferenceFragment fragment, String preferenceKey,
                                          int valuesR, int entriesR, int summaryR) {
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
            if (summaryR != 0) {
                MessageFormat messageFormat = new MessageFormat(fragment.getText(summaryR)
                        .toString());
                summary = messageFormat.format(new Object[] { summary });
            }
            listPref.setSummary(summary);
        }
    }

    /**
     * @return true if input string is null, a zero-length string, or "null"
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

    public static TriState areDefaultPreferenceValuesSet() {
        SharedPreferences sp = getSharedPreferences(PreferenceManager.KEY_HAS_SET_DEFAULT_VALUES);
        if (sp == null) {
            return TriState.UNKNOWN;
        } else {
            return TriState.fromBoolean(
                    sp.getBoolean(PreferenceManager.KEY_HAS_SET_DEFAULT_VALUES, false));
        }
    }

    public static void resetHasSetDefaultValues() {
        SharedPreferences sp = getSharedPreferences(PreferenceManager.KEY_HAS_SET_DEFAULT_VALUES);
        sp.edit().clear().commit();
    }

    public static SharedPreferences getDefaultSharedPreferences() {
        Context context = MyContextHolder.get().context();
        if (context == null) {
            MyLog.e(TAG, "getDefaultSharedPreferences - Was not initialized yet");
            for(StackTraceElement element : Thread.currentThread().getStackTrace()) {
                MyLog.v(TAG, element.toString());
            }
            return null;
        } else {
            return PreferenceManager.getDefaultSharedPreferences(context);
        }
    }

    public static SharedPreferences getSharedPreferences(String name) {
        Context context = MyContextHolder.get().context();
        if (context == null) {
            MyLog.e(TAG, "getSharedPreferences for " + name + " - were not initialized yet");
            for(StackTraceElement element : Thread.currentThread().getStackTrace()) {
                MyLog.v(TAG, element.toString());
            }
            return null;
        } else {
            return context.getSharedPreferences(name, Context.MODE_PRIVATE);
        }
    }

    public static long getLongStoredAsString(String key, long defaultValue) {
        long longValue = defaultValue;
        try {
            long longValueStored = Long.parseLong(getString(key, "0"));
            if (longValueStored > 0) {
                longValue = longValueStored;
            }
        } catch (NumberFormatException e) {
            MyLog.ignored(TAG, e);
        }
        return longValue;
    }

    public static String getString(String key, String defaultValue) {
        String longValue = defaultValue;
        SharedPreferences sp = getDefaultSharedPreferences();
        if (sp != null) {
            longValue = sp.getString(key, defaultValue);
        }
        return longValue;
    }

    public static void putLong(String key, long value) {
        SharedPreferences sp = getDefaultSharedPreferences();
        if (sp != null) {
            sp.edit().putLong(key, value).apply();
        }
    }

    public static void putBoolean(String key, boolean value) {
        SharedPreferences sp = getDefaultSharedPreferences();
        if (sp != null) {
            sp.edit().putBoolean(key, value).apply();
        }
    }

    public static long getLong(String key) {
        long value = 0;
        SharedPreferences sp = getDefaultSharedPreferences();
        if (sp != null) {
            try {
                value = sp.getLong(key, 0);
            } catch (ClassCastException e) {
                // Ignore
            }
        }
        return value;
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        boolean value = defaultValue;
        SharedPreferences sp = getDefaultSharedPreferences();
        if (sp != null) {
            value = sp.getBoolean(key, defaultValue);
        }
        return value;
    }
}
