/*
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.timeline

import android.content.Intent
import org.andstatus.app.account.MyAccount
import org.andstatus.app.activity.ActivityViewItem
import org.andstatus.app.context.DemoData
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.context.TestSuite
import org.andstatus.app.origin.Origin
import org.andstatus.app.service.MyServiceManager
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.SharedPreferencesUtil
import org.junit.After
import org.junit.Assert
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class TimeLineActivityLayoutToggleTest : TimelineActivityTest<ActivityViewItem>() {
    private var showAttachedImages = false
    private var showAvatars = false

    override fun getActivityIntent(): Intent {
        TestSuite.initializeWithData(this)
        when (iteration.incrementAndGet()) {
            2 -> {
                showAttachedImages = showAttachedImagesOld
                showAvatars = !showAvatarsOld
            }
            3 -> {
                showAttachedImages = !showAttachedImagesOld
                showAvatars = showAvatarsOld
            }
            4 -> {
                showAttachedImages = !showAttachedImagesOld
                showAvatars = !showAvatarsOld
                iteration.set(0)
            }
            else -> {
                showAttachedImages = showAttachedImagesOld
                showAvatars = showAvatarsOld
            }
        }
        setPreferences()
        logStartStop("setUp started")
        val ma: MyAccount = DemoData.demoData.getMyAccount(DemoData.demoData.conversationAccountName)
        Assert.assertTrue(ma.isValid)
         MyContextHolder.myContextHolder.getNow().accounts.setCurrentAccount(ma)
        logStartStop("setUp ended")
        return Intent(Intent.ACTION_VIEW,  MyContextHolder.myContextHolder.getNow().timelines.get(TimelineType.HOME, ma.actor,
                 Origin.EMPTY).getUri())
    }

    private fun logStartStop(text: String?) {
        MyLog.i(this, text + ";"
                + " iteration " + iteration.get()
                + (if (showAvatars) " avatars;" else "")
                + if (showAttachedImages) " attached images;" else "")
    }

    @Test
    @Throws(InterruptedException::class)
    fun testToggleAttachedImages1() {
        oneIteration()
    }

    @Throws(InterruptedException::class)
    private fun oneIteration() {
        Assert.assertTrue("MyService is available", MyServiceManager.Companion.isServiceAvailable())
        TestSuite.waitForListLoaded(activity, 3)
    }

    @Test
    @Throws(InterruptedException::class)
    fun testToggleAttachedImages2() {
        oneIteration()
    }

    @Test
    @Throws(InterruptedException::class)
    fun testToggleAttachedImages3() {
        oneIteration()
    }

    @Test
    @Throws(InterruptedException::class)
    fun testToggleAttachedImages4() {
        oneIteration()
    }

    private fun setPreferences() {
        SharedPreferencesUtil.putBoolean(MyPreferences.KEY_DOWNLOAD_AND_DISPLAY_ATTACHED_IMAGES, showAttachedImages)
        SharedPreferencesUtil.putBoolean(MyPreferences.KEY_SHOW_AVATARS, showAvatars)
    }

    @After
    @Throws(Exception::class)
    fun tearDown() {
        logStartStop("tearDown started")
        showAttachedImages = showAttachedImagesOld
        showAvatars = showAvatarsOld
        setPreferences()
    }

    companion object {
        private val iteration: AtomicInteger = AtomicInteger()
        private val showAttachedImagesOld = MyPreferences.getDownloadAndDisplayAttachedImages()
        private val showAvatarsOld = MyPreferences.getShowAvatars()
    }
}
