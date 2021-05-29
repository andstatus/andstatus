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
package org.andstatus.app.util

import android.preference.PreferenceManager
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.TestSuite
import org.junit.Assert
import org.junit.Before
import org.junit.Test

/**
 * @author yvolk@yurivolkov.com
 */
class SharedPreferencesUtilTest {
    @Before
    @Throws(Exception::class)
    fun setUp() {
        TestSuite.initialize(this)
    }

    @Test
    fun testPrefsDirectory() {
        val prefsDir = SharedPreferencesUtil.prefsDirectory( MyContextHolder.myContextHolder.getNow().context())
        Assert.assertTrue("Prefs dir: " + prefsDir.absolutePath, prefsDir.exists())
        val defaultSharedPreferencesFile = SharedPreferencesUtil.defaultSharedPreferencesPath( MyContextHolder.myContextHolder.getNow().context())
        Assert.assertTrue("defaultSharedPreferencesFile: " + defaultSharedPreferencesFile.absolutePath, defaultSharedPreferencesFile.exists())
    }

    @Test
    fun testResetHasSetDefaultValues() {
        Assert.assertEquals(TriState.TRUE, SharedPreferencesUtil.areDefaultPreferenceValuesSet())
        SharedPreferencesUtil.resetHasSetDefaultValues()
        Assert.assertEquals(TriState.FALSE, SharedPreferencesUtil.areDefaultPreferenceValuesSet())
        val sp = SharedPreferencesUtil.getSharedPreferences(PreferenceManager.KEY_HAS_SET_DEFAULT_VALUES)
                ?: throw IllegalStateException("No Shared preferences")
        sp.edit().putBoolean(PreferenceManager.KEY_HAS_SET_DEFAULT_VALUES, true).commit()
        Assert.assertEquals(TriState.TRUE, SharedPreferencesUtil.areDefaultPreferenceValuesSet())
    }
}
