/*
 * Copyright (C) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.activity;

import android.content.Intent;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.timeline.TimelineActivityTest;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.MyLog;
import org.junit.Test;

import static org.andstatus.app.context.DemoData.demoData;
import static org.junit.Assert.assertTrue;

public class NotificationsTimelineTest extends TimelineActivityTest {

    @Override
    protected Intent getActivityIntent() {
        MyLog.i(this, "setUp started");
        TestSuite.initializeWithData(this);

        final MyAccount ma = demoData.getMyAccount(demoData.CONVERSATION_ACCOUNT_NAME);
        assertTrue(ma.isValid());
        MyContextHolder.get().persistentAccounts().setCurrentAccount(ma);

        MyLog.i(this, "setUp ended");
        return new Intent(Intent.ACTION_VIEW,
                MatchedUri.getTimelineUri(Timeline.getTimeline(TimelineType.NOTIFICATIONS, ma, 0, ma.getOrigin())));
    }

    @Test
    public void openNotifications() throws InterruptedException {
        final String method = "openNotifications";
        TestSuite.waitForListLoaded(getActivity(), 4);
    }
}
