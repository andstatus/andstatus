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
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.view.View;
import android.widget.CheckBox;

import org.andstatus.app.context.MyContextHolder;

import java.io.File;
import java.text.MessageFormat;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

public class SharedPreferencesUtil {
    private static final String TAG = SharedPreferencesUtil.class.getSimpleName();

    public static final String FILE_EXTENSION = ".xml";
    private static final Map<String, Object> cachedValues = new ConcurrentHashMap<>();

    private SharedPreferencesUtil() {
    }

    public static void forget() {
        cachedValues.clear();
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

        if (context == null || StringUtils.isEmpty(prefsFileName)) {
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
            listPref.setSummary(getSummaryForListPreference(fragment.getActivity(),
                    listPref.getValue(), valuesR, entriesR, summaryR));
        }
    }

    public static String getSummaryForListPreference(Context context, String listValue,
                                                        int valuesR, int entriesR, int summaryR) {
        String[] values = context.getResources().getStringArray(valuesR);
        String[] entries = context.getResources().getStringArray(entriesR);
        String summary = entries[0];
        for (int i = 0; i < values.length; i++) {
            if (listValue.equals(values[i])) {
                summary = entries[i];
                break;
            }
        }
        if (summaryR != 0) {
            MessageFormat messageFormat = new MessageFormat(context.getText(summaryR)
                    .toString());
            summary = messageFormat.format(new Object[] { summary });
        }
        return summary;
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
        forget();
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
        if (sp != null) {
            sp.edit().clear().commit();
        }
        forget();
    }

    public static SharedPreferences getDefaultSharedPreferences() {
        Context context = MyContextHolder.get().context();
        if (context == null) {
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

    public static int getIntStoredAsString(@NonNull String key, int defaultValue) {
        long longValue = getLongStoredAsString(key, defaultValue);
        return (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) ? (int) longValue : defaultValue;
    }

    public static long getLongStoredAsString(@NonNull String key, long defaultValue) {
        long value = defaultValue;
        try {
            long longValueStored = Long.parseLong(getString(key, Long.toString(Long.MIN_VALUE)));
            if (longValueStored > Long.MIN_VALUE) {
                value = longValueStored;
            }
        } catch (NumberFormatException e) {
            MyLog.ignored(TAG, e);
        }
        return value;
    }

    public static void putString(@NonNull String key, String value) {
        if (value == null) {
            removeKey(key);
        } else {
            SharedPreferences sp = getDefaultSharedPreferences();
            if (sp != null) {
                sp.edit().putString(key, value).apply();
                putToCache(key, value);
            }
        }
    }

    public static void removeKey(@NonNull String key) {
        SharedPreferences sp = getDefaultSharedPreferences();
        if (sp != null) {
            sp.edit().remove(key).apply();
            putToCache(key, null);
        }
    }

    public static String getString(@NonNull String key, String defaultValue) {
        if (cachedValues.containsKey(key)) {
            try {
                return (String) cachedValues.get(key);
            } catch (ClassCastException e) {
                MyLog.ignored("getString, key=" + key, e);
                cachedValues.remove(key);
            }
        }
        String value = defaultValue;
        SharedPreferences sp = getDefaultSharedPreferences();
        if (sp != null) {
            try {
                value = sp.getString(key, defaultValue);
            } catch (ClassCastException e) {
                MyLog.ignored("getString, key=" + key, e);
                value = Long.toString(sp.getLong(key, Long.parseLong(defaultValue)));
            }
            putToCache(key, value);
        }
        return value;
    }

    private static Object putToCache(@NonNull String key, Object value) {
        if (value == null) {
            Object oldValue = cachedValues.get(key);
            cachedValues.remove(key);
            return oldValue;
        } else {
            return cachedValues.put(key, value);
        }
    }

    public static void putBoolean(@NonNull String key, View checkBox) {
        if (checkBox != null && CheckBox.class.isAssignableFrom(checkBox.getClass())) {
            final boolean value = ((CheckBox) checkBox).isChecked();
            putBoolean(key, value);
        }
    }

    public static void putBoolean(@NonNull String key, boolean value) {
        SharedPreferences sp = getDefaultSharedPreferences();
        if (sp != null) {
            sp.edit().putBoolean(key, value).apply();
            cachedValues.put(key, value);
        }
    }

    public static void putLong(@NonNull String key, long value) {
        SharedPreferences sp = getDefaultSharedPreferences();
        if (sp != null) {
            sp.edit().putLong(key, value).apply();
            cachedValues.put(key, value);
        }
    }

    public static long getLong(@NonNull String key) {
        return getLong(key, 0);
    }

    public static long getLong(@NonNull String key, long defaultValue) {
        if (cachedValues.containsKey(key)) {
            try {
                return (long) cachedValues.get(key);
            } catch (ClassCastException e) {
                MyLog.ignored("getLong, key=" + key, e);
                cachedValues.remove(key);
            }
        }
        long value = defaultValue;
        SharedPreferences sp = getDefaultSharedPreferences();
        if (sp != null) {
            try {
                value = sp.getLong(key, defaultValue);
                cachedValues.put(key, value);
            } catch (ClassCastException e) {
                removeKey(key);
                MyLog.ignored("getLong", e);
            }
        }
        return value;
    }

    public static boolean getBoolean(@NonNull String key, boolean defaultValue) {
        if (cachedValues.containsKey(key)) {
            try {
                return (boolean) cachedValues.get(key);
            } catch (ClassCastException e) {
                MyLog.ignored("getBoolean, key=" + key, e);
                cachedValues.remove(key);
            }
        }
        boolean value = defaultValue;
        SharedPreferences sp = getDefaultSharedPreferences();
        if (sp != null) {
            value = sp.getBoolean(key, defaultValue);
            cachedValues.put(key, value);
        }
        return value;
    }
}
