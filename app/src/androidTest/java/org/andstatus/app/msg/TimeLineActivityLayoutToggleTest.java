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

public class TimeLineActivityLayoutToggleTest extends android.test.ActivityInstrumentationTestCase2<TimelineActivity> {
    private TimelineActivity activity;
    private static int iteration = 0;
    static boolean showAttachedImagesOld = false;
    boolean showAttachedImages = false;
    static boolean showAvatarsOld = false;
    boolean showAvatars = false;

    public TimeLineActivityLayoutToggleTest() {
        super(TimelineActivity.class);
    }
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestSuite.initializeWithData(this);
        iteration = (iteration >= 4 ? 1 : iteration + 1);
        switch (iteration) {
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
                break;
            default:
                showAttachedImagesOld = MyPreferences.getDownloadAndDisplayAttachedImages();
                showAvatarsOld = MyPreferences.getShowAvatars();
                showAttachedImages = showAttachedImagesOld; 
                showAvatars = showAvatarsOld;
                break;
        }
        setPreferences();
        MyLog.setLogToFile(true);
        logStartStop("setUp started");

        MyAccount ma = MyContextHolder.get().persistentAccounts().fromAccountName(TestSuite.CONVERSATION_ACCOUNT_NAME);
        assertTrue(ma.isValid());
        MyContextHolder.get().persistentAccounts().setCurrentAccount(ma);
        
        Intent intent = new Intent(Intent.ACTION_VIEW, 
                MatchedUri.getTimelineUri(new Timeline(TimelineType.HOME, ma, 0, null)));
        setActivityIntent(intent);
        
        activity = getActivity();

        assertTrue("MyService is available", MyServiceManager.isServiceAvailable());
        logStartStop("setUp ended");
    }

    private void logStartStop(String text) {
        MyLog.i(this, text + ";" 
                + " iteration " + iteration 
                + (showAvatars ? " avatars;" : "")
                + (showAttachedImages ? " attached images;" : ""));
    }

    public void testToggleAttachedImages1() throws InterruptedException {
        oneIteration();
    }

    private void oneIteration() throws InterruptedException {
        TestSuite.waitForListLoaded(this, activity, 3);
    }

    public void testToggleAttachedImages2() throws InterruptedException {
        oneIteration();
    }
    
    public void testToggleAttachedImages3() throws InterruptedException {
        oneIteration();
    }
    
    public void testToggleAttachedImages4() throws InterruptedException {
        oneIteration();
    }
    
    private void setPreferences() {
        SharedPreferencesUtil.getDefaultSharedPreferences().edit()
                .putBoolean(MyPreferences.KEY_DOWNLOAD_AND_DISPLAY_ATTACHED_IMAGES, showAttachedImages)
                .putBoolean(MyPreferences.KEY_SHOW_AVATARS, showAvatars)
                .commit();
    }
    
    @Override
    protected void tearDown() throws Exception {
        logStartStop("tearDown started");
        showAttachedImages = showAttachedImagesOld;
        showAvatars = showAvatarsOld;
        setPreferences();
        MyLog.setLogToFile(false);
        super.tearDown();
    }
}
