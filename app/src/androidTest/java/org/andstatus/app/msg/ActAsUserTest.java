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

import org.andstatus.app.ListActivityTestHelper;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.data.TimelineType;
import org.andstatus.app.util.MyLog;

/**
 * @author yvolk@yurivolkov.com
 */
public class ActAsUserTest extends android.test.ActivityInstrumentationTestCase2<TimelineActivity> {
    private TimelineActivity mActivity;

    public ActAsUserTest() {
        super(TimelineActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        MyLog.i(this, "setUp started");
        TestSuite.initializeWithData(this);

        MyAccount ma = MyContextHolder.get().persistentAccounts().fromAccountName(TestSuite.GNUSOCIAL_TEST_ACCOUNT_NAME);
        assertTrue(ma.isValid());
        MyContextHolder.get().persistentAccounts().setCurrentAccount(ma);

        Intent intent = new Intent(Intent.ACTION_VIEW,
                MatchedUri.getTimelineUri(ma.getUserId(), TimelineType.HOME, false, 0));
        setActivityIntent(intent);

        mActivity = getActivity();
        MyLog.i(this, "setUp ended");
    }

    public void testActAsUser() throws InterruptedException {
        final String method = "testActAsUser";
        TestSuite.waitForListLoaded(this, mActivity, 2);
        ListActivityTestHelper<TimelineActivity> helper = new ListActivityTestHelper<TimelineActivity>(this, ConversationActivity.class);
        long msgId = helper.getListItemIdOfLoadedReply();
        String logMsg = "msgId=" + msgId;

        helper.invokeContextMenuAction4ListItemId(method, msgId, ContextMenuItem.NONEXISTENT);
        long userId1 = getActivity().getContextMenu().getActorUserIdForCurrentMessage();
        logMsg += "; userId1=" + userId1;
        assertTrue(logMsg, userId1 != 0 );

        helper.invokeContextMenuAction4ListItemId(method, msgId, ContextMenuItem.ACT_AS_USER);
        long userId2 = getActivity().getContextMenu().getActorUserIdForCurrentMessage();
        logMsg += "; userId2=" + userId2;
        assertTrue(logMsg, userId1 != userId2 );
    }
}
