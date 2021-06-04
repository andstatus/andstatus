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
package org.andstatus.app.util

import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
import android.view.View
import android.widget.CheckBox
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import org.andstatus.app.context.MyContextHolder
import java.io.File
import java.text.MessageFormat
import java.util.concurrent.ConcurrentHashMap

object SharedPreferencesUtil {
    private val TAG: String = SharedPreferencesUtil::class.java.simpleName
    val FILE_EXTENSION: String = ".xml"
    private val cachedValues: MutableMap<String, Any?> = ConcurrentHashMap()
    fun forget() {
        cachedValues.clear()
    }

    fun defaultSharedPreferencesPath(context: Context): File {
        return File(prefsDirectory(context),
                context.getPackageName() + "_preferences" + FILE_EXTENSION)
    }

    /**
     * @return Directory for files of SharedPreferences
     */
    fun prefsDirectory(context: Context): File {
        val dataDir = context.getApplicationInfo().dataDir
        return File(dataDir, "shared_prefs")
    }

    /**
     * Delete the preferences file!
     *
     * @return Was the file deleted?
     */
    fun delete(context: Context?, prefsFileName: String?): Boolean {
        val isDeleted: Boolean
        if (context == null || prefsFileName.isNullOrEmpty()) {
            MyLog.v(TAG) { "delete '$prefsFileName' - nothing to do" }
            return false
        }
        val prefFile = File(prefsDirectory(context), prefsFileName + FILE_EXTENSION)
        if (prefFile.exists()) {
            isDeleted = prefFile.delete()
            if (MyLog.isVerboseEnabled()) {
                MyLog.v(TAG) {
                    ("The prefs file '" + prefFile.absolutePath + "' was "
                            + (if (isDeleted) "" else "not ") + " deleted")
                }
            }
        } else {
            isDeleted = false
            if (MyLog.isLoggable(TAG, MyLog.DEBUG)) {
                MyLog.d(TAG, "The prefs file '" + prefFile.absolutePath + "' was not found")
            }
        }
        return isDeleted
    }

    /**
     * @param fragment Preference Activity
     * @param preferenceKey android:key - Name of the preference key
     * @param valuesR android:entryValues
     * @param entriesR Almost like android:entries but to show in the summary (may be the same as android:entries)
     * @param summaryR If 0 then the selected entry will be put into the summary as is.
     */
    fun showListPreference(fragment: PreferenceFragmentCompat, preferenceKey: String,
                           valuesR: Int, entriesR: Int, summaryR: Int) {
        val listPref = fragment.findPreference<Preference?>(preferenceKey) as ListPreference?
        val context = fragment.getActivity()
        if (listPref != null && context != null) {
            listPref.summary = getSummaryForListPreference(context,
                    listPref.value, valuesR, entriesR, summaryR)
        }
    }

    fun getSummaryForListPreference(context: Context, listValue: String?,
                                    valuesR: Int, entriesR: Int, summaryR: Int): String {
        val values = context.getResources().getStringArray(valuesR)
        val entries = context.getResources().getStringArray(entriesR)
        var summary = entries[0]
        for (i in values.indices) {
            if (listValue == values[i]) {
                summary = entries[i]
                break
            }
        }
        if (summaryR != 0) {
            val messageFormat = MessageFormat(context.getText(summaryR).toString())
            summary = messageFormat.format(arrayOf<Any?>(summary))
        }
        return summary
    }

    /**
     * @return true if input string is null, a zero-length string, or "null"
     */
    fun isEmpty(str: CharSequence?): Boolean {
        return TextUtils.isEmpty(str) || "null" == str
    }

    fun isTrueAsInt(o: Any?): Int {
        return if (isTrue(o)) 1 else 0
    }

    /**
     * Returns true not only for boolean true or String "true", but for "1" also
     */
    fun isTrue(o: Any?): Boolean {
        var outValue = false
        try {
            if (o != null) {
                if (o is Boolean) {
                    outValue = o
                } else {
                    val string = o.toString()
                    if (!isEmpty(string)) {
                        outValue = java.lang.Boolean.parseBoolean(string)
                        if (!outValue && string.compareTo("1") == 0) {
                            outValue = true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            MyLog.v(TAG, o.toString(), e)
        }
        return outValue
    }

    fun copyAll(source: SharedPreferences?, destination: SharedPreferences?): Long {
        if (source == null || destination == null) return 0
        var entryCounter: Long = 0
        val editor = destination.edit()
        for ((key, value) in source.all) {
            if (Boolean::class.java.isInstance(value)) {
                editor.putBoolean(key, value as Boolean)
            } else if (Int::class.java.isInstance(value)) {
                editor.putInt(key, value as Int)
            } else if (Long::class.java.isInstance(value)) {
                editor.putLong(key, value as Long)
            } else if (Float::class.java.isInstance(value)) {
                editor.putFloat(key, value as Float)
            } else if (String::class.java.isInstance(value)) {
                editor.putString(key, value.toString())
            } else {
                MyLog.e(TAG, "Unknown type of shared preference: "
                        + value?.javaClass + ", value: " + value.toString())
                entryCounter--
            }
            entryCounter++
        }
        editor.commit()
        forget()
        return entryCounter
    }

    fun areDefaultPreferenceValuesSet(): TriState {
        val sp = getSharedPreferences(PreferenceManager.KEY_HAS_SET_DEFAULT_VALUES)
        return if (sp == null) {
            TriState.UNKNOWN
        } else {
            TriState.fromBoolean(sp.getBoolean(PreferenceManager.KEY_HAS_SET_DEFAULT_VALUES, false))
        }
    }

    fun resetHasSetDefaultValues() {
        val sp = getSharedPreferences(PreferenceManager.KEY_HAS_SET_DEFAULT_VALUES)
        sp?.edit()?.clear()?.commit()
        forget()
    }

    fun getDefaultSharedPreferences(): SharedPreferences? {
        MyContextHolder.myContextHolder.getNow().let {
            return if (it.isEmpty) null else PreferenceManager.getDefaultSharedPreferences(it.context)
        }
    }

    fun getSharedPreferences(name: String?): SharedPreferences? {
        val myContext = MyContextHolder.myContextHolder.getNow()
        return if (myContext.isEmpty) {
            MyLog.e(TAG, "getSharedPreferences for $name - were not initialized yet")
            for (element in Thread.currentThread().stackTrace) {
                MyLog.v(TAG) { element.toString() }
            }
            null
        } else {
            myContext.context.getSharedPreferences(name, Context.MODE_PRIVATE)
        }
    }

    fun getIntStoredAsString(key: String, defaultValue: Int): Int {
        val longValue = getLongStoredAsString(key, defaultValue.toLong())
        return if (longValue >= Int.MIN_VALUE && longValue <= Int.MAX_VALUE) longValue.toInt() else defaultValue
    }

    fun getLongStoredAsString(key: String, defaultValue: Long): Long {
        var value = defaultValue
        try {
            val longValueStored = getString(key, java.lang.Long.toString(Long.MIN_VALUE)).toLong()
            if (longValueStored > Long.MIN_VALUE) {
                value = longValueStored
            }
        } catch (e: NumberFormatException) {
            MyLog.ignored(TAG, e)
        }
        return value
    }

    fun putString(key: String, value: String?) {
        if (value == null) {
            removeKey(key)
        } else {
            val sp = getDefaultSharedPreferences()
            if (sp != null) {
                sp.edit().putString(key, value).apply()
                putToCache(key, value)
            }
        }
    }

    fun removeKey(key: String) {
        val sp = getDefaultSharedPreferences()
        if (sp != null) {
            sp.edit().remove(key).apply()
            putToCache(key, null)
        }
    }

    fun getString(key: String, defaultValue: String): String {
        val cachedValue = cachedValues.get(key)
        if (cachedValue != null) {
            try {
                return cachedValue as String
            } catch (e: ClassCastException) {
                MyLog.ignored("getString, key=$key", e)
                cachedValues.remove(key)
            }
        }
        var value = defaultValue
        val sp = getDefaultSharedPreferences()
        if (sp != null) {
            value = try {
                sp.getString(key, defaultValue) ?: ""
            } catch (e: ClassCastException) {
                MyLog.ignored("getString, key=$key", e)
                sp.getLong(key, defaultValue.toLong()).toString()
            }
            putToCache(key, value)
        }
        return value
    }

    private fun putToCache(key: String, value: Any?): Any? {
        return if (value == null) {
            val oldValue = cachedValues.get(key)
            cachedValues.remove(key)
            oldValue
        } else {
            cachedValues.put(key, value)
        }
    }

    fun putBoolean(key: String, checkBox: View?) {
        if (checkBox != null && CheckBox::class.java.isAssignableFrom(checkBox.javaClass)) {
            val value = (checkBox as CheckBox).isChecked()
            putBoolean(key, value)
        }
    }

    fun putBoolean(key: String, value: Boolean) {
        val sp = getDefaultSharedPreferences()
        if (sp != null) {
            sp.edit().putBoolean(key, value).apply()
            cachedValues[key] = value
        }
    }

    fun putLong(key: String, value: Long) {
        val sp = getDefaultSharedPreferences()
        if (sp != null) {
            sp.edit().putLong(key, value).apply()
            cachedValues[key] = value
        }
    }

    fun getLong(key: String): Long {
        return getLong(key, 0)
    }

    fun getLong(key: String, defaultValue: Long): Long {
        val cachedValue = cachedValues.get(key)
        if (cachedValue != null) {
            try {
                return cachedValue as Long
            } catch (e: ClassCastException) {
                MyLog.ignored("getLong, key=$key", e)
                cachedValues.remove(key)
            }
        }
        var value = defaultValue
        val sp = getDefaultSharedPreferences()
        if (sp != null) {
            try {
                value = sp.getLong(key, defaultValue)
                cachedValues[key] = value
            } catch (e: ClassCastException) {
                removeKey(key)
                MyLog.ignored("getLong", e)
            }
        }
        return value
    }

    fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        val cachedValue = cachedValues.get(key)
        if (cachedValue != null) {
            try {
                return cachedValue as Boolean
            } catch (e: ClassCastException) {
                MyLog.ignored("getBoolean, key=$key", e)
                cachedValues.remove(key)
            }
        }
        var value = defaultValue
        val sp = getDefaultSharedPreferences()
        if (sp != null) {
            value = sp.getBoolean(key, defaultValue)
            cachedValues[key] = value
        }
        return value
    }
}
