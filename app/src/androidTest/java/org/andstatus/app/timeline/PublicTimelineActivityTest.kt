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

package org.andstatus.app.timeline;

import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.espresso.action.ReplaceTextAction;

import org.andstatus.app.ActivityTestHelper;
import org.andstatus.app.R;
import org.andstatus.app.activity.ActivityViewItem;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.Visibility;
import org.andstatus.app.note.BaseNoteViewItem;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.MyLog;
import org.junit.Test;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.pressImeActionButton;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withResourceName;
import static org.andstatus.app.context.DemoData.demoData;
import static org.andstatus.app.context.MyContextHolder.myContextHolder;
import static org.andstatus.app.util.EspressoUtils.setChecked;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author yvolk@yurivolkov.com
 */
public class PublicTimelineActivityTest extends TimelineActivityTest<ActivityViewItem> {

    @Override
    protected Intent getActivityIntent() {
        MyLog.i(this, "setUp started");
        TestSuite.initializeWithData(this);

        final Origin origin = myContextHolder.getNow().origins().fromName(demoData.gnusocialTestOriginName);
        assertTrue(origin.toString(), origin.isValid());
        MyLog.i(this, "setUp ended");

        return new Intent(Intent.ACTION_VIEW, myContextHolder.getNow().timelines()
                .get(TimelineType.PUBLIC, Actor.EMPTY, origin).getUri());
    }

    @Test
    public void testGlobalSearchInOptionsMenu() throws InterruptedException {
        oneSearchTest("testGlobalSearchInOptionsMenu", demoData.globalPublicNoteText, true);
    }

    @Test
    public void testSearch() throws InterruptedException {
        oneSearchTest("testSearch", demoData.publicNoteText, false);
    }

    private void oneSearchTest(String method, String noteText, boolean internetSearch) throws InterruptedException {
        int menu_id = R.id.search_menu_id;
        assertTrue("MyService is available", MyServiceManager.isServiceAvailable());
        TestSuite.waitForListLoaded(getActivity(), 2);
        assertEquals(TimelineType.PUBLIC, getActivity().getTimeline().getTimelineType());

        assertFalse("Screen is locked", TestSuite.isScreenLocked(getActivity()));
        ActivityTestHelper<TimelineActivity> helper = new ActivityTestHelper<>(getActivity(),
                TimelineActivity.class);
        helper.clickMenuItem(method, menu_id);

        onView(withId(R.id.internet_search)).perform(setChecked(internetSearch));
        onView(withResourceName("search_text")).perform(new ReplaceTextAction(noteText), pressImeActionButton());
        TimelineActivity nextActivity = (TimelineActivity) helper.waitForNextActivity(method, 40000);
        waitForButtonClickedEvidence(nextActivity, method, noteText);
        assertNotesArePublic(nextActivity, noteText);
        nextActivity.finish();
    }

    private volatile String stringFound = "";
    private void waitForButtonClickedEvidence(final TimelineActivity timelineActivity, String caption,
                                              String queryString) throws InterruptedException {
        final String method = "waitForButtonClickedEvidence";
        boolean found = false;
        assertNotNull("Timeline activity is null", timelineActivity);
        for (int attempt = 0; attempt < 6; attempt++) {
            TestSuite.waitForIdleSync();
            
            Runnable probe = () -> {
                TextView item = timelineActivity.findViewById(R.id.timelineTypeButton);
                if (item != null) {
                    stringFound = item.getText().toString();
                }
            };
            timelineActivity.runOnUiThread(probe);

            if (stringFound.contains(queryString)) {
                found = true;
                break;
            }
            DbUtils.waitMs(method, 1000 * (attempt + 1));
        }
        assertTrue(caption + ", query:'" + queryString
                + "', found:'" + stringFound + "'", found);
    }

    private void assertNotesArePublic(TimelineActivity timelineActivity, String publicNoteText) throws InterruptedException {
        final String method = "assertNotesArePublic";
        int msgCount = 0;
        for (int attempt=0; attempt < 3; attempt++) {
            TestSuite.waitForIdleSync();
            msgCount = oneAttempt(timelineActivity, publicNoteText);
            if (msgCount > 0 || DbUtils.waitMs(method, 2000 * (attempt + 1))) {
                break;
            }
        }
        assertTrue("Notes should be found", msgCount > 0);
    }

    private int oneAttempt(TimelineActivity timelineActivity, String publicNoteText) {
        final ViewGroup list = timelineActivity.findViewById(android.R.id.list);
        int msgCount = 0;
        for (int index = 0; index < list.getChildCount(); index++) {
            View noteView = list.getChildAt(index);
            TextView bodyView = noteView.findViewById(R.id.note_body);
            final BaseNoteViewItem viewItem = ListScreenTestHelper.toBaseNoteViewItem(
                    timelineActivity.getListAdapter().getItem(noteView));
            if (bodyView != null) {
                assertTrue("Note #" + viewItem.getId() + " '" + viewItem.getContent()
                                + "' contains '" + publicNoteText + "'\n" + viewItem,
                        String.valueOf(viewItem.getContent()).contains(publicNoteText));
                assertNotEquals("Note #" + viewItem.getId() + " '" + viewItem.getContent()
                        + "' is private" + "\n" + viewItem, Visibility.PRIVATE,
                            Visibility.fromNoteId(viewItem.getId()));
                msgCount++;
            }
        }
        MyLog.v(this, "Public notes with '" + publicNoteText + "' found: " + msgCount);
        return msgCount;
    }
}
