/*
 * Copyright (C) 2019 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.andstatus.app.note;

import android.content.Intent;

import androidx.test.espresso.action.ReplaceTextAction;

import org.andstatus.app.ActivityTestHelper;
import org.andstatus.app.R;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.activity.ActivityViewItem;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.net.http.HttpReadResult;
import org.andstatus.app.net.social.ConnectionMock;
import org.andstatus.app.net.social.Note;
import org.andstatus.app.net.social.Visibility;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.timeline.TimelineActivityTest;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.JsonUtils;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.MyLog;
import org.json.JSONObject;
import org.junit.Test;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isNotChecked;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.andstatus.app.context.DemoData.demoData;
import static org.andstatus.app.context.MyContextHolder.myContextHolder;
import static org.andstatus.app.note.NoteEditorTest.attachImages;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NoteEditorActivityPubTest extends TimelineActivityTest<ActivityViewItem> {
    private ConnectionMock mock;

    @Override
    protected Intent getActivityIntent() {
        MyLog.i(this, "setUp started");
        TestSuite.initializeWithAccounts(this);

        mock = ConnectionMock.newFor(demoData.activityPubTestAccountName);
        MyAccount ma = mock.getData().getMyAccount();
        myContextHolder.getBlocking().accounts().setCurrentAccount(ma);
        assertTrue("isValidAndSucceeded " + ma, ma.isValidAndSucceeded());

        MyLog.i(this, "setUp ended");
        return new Intent(Intent.ACTION_VIEW,
                myContextHolder.getNow().timelines().get(TimelineType.HOME, ma.getActor(), Origin.EMPTY).getUri());
    }

    @Test
    public void sendingPublic() throws InterruptedException {
        final String method = "sendingPublic";
        TestSuite.waitForListLoaded(getActivity(), 2);

        ActivityTestHelper.hideEditorAndSaveDraft(method, getActivity());
        ActivityTestHelper.openEditor(method, getActivity());

        String actorUniqueName = "me" + demoData.testRunUid + "@mastodon.example.com";
        final String content = "Sending note to the unknown yet Actor @" + actorUniqueName;
        // TypeTextAction doesn't work here due to auto-correction
        onView(withId(R.id.noteBodyEditText)).perform(new ReplaceTextAction(content));
        TestSuite.waitForIdleSync();

        ActivityTestHelper.clickSendButton(method, getActivity());

        long noteId = ActivityTestHelper.waitAndGetIdOfStoredNote(method, content);
        Note note = Note.loadContentById(mock.connection.myContext(), noteId);
        assertEquals("Note " + note, DownloadStatus.SENDING, note.getStatus());
        assertEquals("Visibility " + note, Visibility.PUBLIC_AND_TO_FOLLOWERS, note.audience().getVisibility());
        assertFalse("Not sensitive " + note, note.isSensitive());
        assertTrue("Audience should contain " + actorUniqueName +  "\n " + note,
            note.audience().getNonSpecialActors().stream().anyMatch(a -> actorUniqueName.equals(a.getUniqueName())));
    }

    @Test
    public void sendingSensitive() throws Exception {
        final String method = "sendingSensitive";
        TestSuite.waitForListLoaded(getActivity(), 2);

        ActivityTestHelper.hideEditorAndSaveDraft(method, getActivity());
        ActivityTestHelper.openEditor(method, getActivity());

        final String content = "Sending sensitive note " + demoData.testRunUid;
        onView(withId(R.id.is_sensitive)).check(matches(isNotChecked())).perform(scrollTo(), click());
        // TypeTextAction doesn't work here due to auto-correction
        onView(withId(R.id.noteBodyEditText)).perform(new ReplaceTextAction(content));
        TestSuite.waitForIdleSync();

        ActivityTestHelper.clickSendButton(method, getActivity());

        long noteId = ActivityTestHelper.waitAndGetIdOfStoredNote(method, content);
        Note note = Note.loadContentById(mock.connection.myContext(), noteId);
        assertEquals("Note " + note, DownloadStatus.SENDING, note.getStatus());
        assertEquals("Visibility " + note, Visibility.PUBLIC_AND_TO_FOLLOWERS, note.audience().getVisibility());
        assertTrue("Sensitive " + note, note.isSensitive());

        HttpReadResult result = mock.getHttpMock().waitForPostContaining(content);
        JSONObject postedObject = result.request.postParams.get();
        JSONObject jso = postedObject.getJSONObject("object");
        assertFalse("No name " + postedObject, jso.has("name"));
        assertEquals("Note content " + postedObject, content,
                MyHtml.htmlToPlainText(jso.getString("content")));
        assertEquals("Sensitive " + postedObject, "true",
                JsonUtils.optString(jso, "sensitive", "(not found)"));
    }

    @Test
    public void attachOneImage() throws InterruptedException {
        attachImages(this,1, 1);
    }

    @Test
    public void attachTwoImages() throws InterruptedException {
        attachImages(this,2, 2);
    }

}
