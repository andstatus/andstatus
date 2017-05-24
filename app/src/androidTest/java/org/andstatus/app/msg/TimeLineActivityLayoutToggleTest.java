/**
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

package org.andstatus.app.msg;

import android.content.Intent;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.timeline.Timeline;
import org.andstatus.app.timeline.TimelineType;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.junit.After;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertTrue;

public class TimeLineActivityLayoutToggleTest extends TimelineActivityTest {
    private static final AtomicInteger iteration = new AtomicInteger();
    private static final boolean showAttachedImagesOld = MyPreferences.getDownloadAndDisplayAttachedImages();
    private boolean showAttachedImages = false;
    private  static final boolean showAvatarsOld = MyPreferences.getShowAvatars();
    private boolean showAvatars = false;

    @Override
    protected Intent getActivityIntent() {
        TestSuite.initializeWithData(this);
        switch (iteration.incrementAndGet()) {
            case 2:
                showAttachedImages = showAttachedImagesOld;
                showAvatars = !showAvatarsOld;
                break;
            case 3:
                showAttachedImages = !showAttachedImagesOld;
                showAvatars = showAvatarsOld;
                break;
            case 4:
                showAttachedImages = !showAttachedImagesOld;
                showAvatars = !showAvatarsOld;
                iteration.set(0);
                break;
            default:
                showAttachedImages = showAttachedImagesOld;
                showAvatars = showAvatarsOld;
                break;
        }
        setPreferences();
        logStartStop("setUp started");

        MyAccount ma = MyContextHolder.get().persistentAccounts().fromAccountName(TestSuite.CONVERSATION_ACCOUNT_NAME);
        assertTrue(ma.isValid());
        MyContextHolder.get().persistentAccounts().setCurrentAccount(ma);

        logStartStop("setUp ended");
        return new Intent(Intent.ACTION_VIEW,
                MatchedUri.getTimelineUri(Timeline.getTimeline(TimelineType.HOME, ma, 0, null)));
    }

    private void logStartStop(String text) {
        MyLog.i(this, text + ";" 
                + " iteration " + iteration.get()
                + (showAvatars ? " avatars;" : "")
                + (showAttachedImages ? " attached images;" : ""));
    }

    @Test
    public void testToggleAttachedImages1() throws InterruptedException {
        oneIteration();
    }

    private void oneIteration() throws InterruptedException {
        assertTrue("MyService is available", MyServiceManager.isServiceAvailable());
        TestSuite.waitForListLoaded(getActivity(), 3);
    }

    @Test
    public void testToggleAttachedImages2() throws InterruptedException {
        oneIteration();
    }

    @Test
    public void testToggleAttachedImages3() throws InterruptedException {
        oneIteration();
    }

    @Test
    public void testToggleAttachedImages4() throws InterruptedException {
        oneIteration();
    }

    private void setPreferences() {
        SharedPreferencesUtil.putBoolean(MyPreferences.KEY_DOWNLOAD_AND_DISPLAY_ATTACHED_IMAGES, showAttachedImages);
        SharedPreferencesUtil.putBoolean(MyPreferences.KEY_SHOW_AVATARS, showAvatars);
    }
    
    @After
    public void tearDown() throws Exception {
        logStartStop("tearDown started");
        showAttachedImages = showAttachedImagesOld;
        showAvatars = showAvatarsOld;
        setPreferences();
    }
}
