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
import android.view.View;

import org.andstatus.app.ActivityTestHelper;
import org.andstatus.app.R;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.activity.ActivityViewItem;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.net.http.HttpReadResult;
import org.andstatus.app.net.social.Audience;
import org.andstatus.app.net.social.ConnectionMock;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.timeline.TimelineActivity;
import org.andstatus.app.timeline.TimelineActivityTest;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.MyLog;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import androidx.test.espresso.action.ReplaceTextAction;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.andstatus.app.context.DemoData.demoData;
import static org.hamcrest.Matchers.isIn;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

public class NoteEditorActivityPubTest extends TimelineActivityTest<ActivityViewItem> {
    private ConnectionMock mock;

    @Override
    protected Intent getActivityIntent() {
        MyLog.i(this, "setUp started");
        TestSuite.initializeWithAccounts(this);

        mock = ConnectionMock.newFor(demoData.activityPubTestAccountName);
        MyAccount ma = mock.getData().getMyAccount();
        MyContextHolder.get().accounts().setCurrentAccount(ma);
        assertTrue("isValidAndSucceeded " + ma, ma.isValidAndSucceeded());

        MyLog.i(this, "setUp ended");
        return new Intent(Intent.ACTION_VIEW,
                Timeline.getTimeline(TimelineType.HOME, ma.getActorId(), Origin.EMPTY).getUri());
    }

    @Test
    public void sending() throws InterruptedException {
        final String method = "sending";
        TestSuite.waitForListLoaded(getActivity(), 2);

        ActivityTestHelper<TimelineActivity> aHelper = new ActivityTestHelper<>(getActivity());
        aHelper.clickMenuItem(method + " hide editor", R.id.saveDraftButton);

        View editorView = getActivity().findViewById(R.id.note_editor);
        aHelper.clickMenuItem(method + " clicker createNoteButton", R.id.createNoteButton);
        ActivityTestHelper.waitViewVisible(method + "; Editor appeared", editorView);

        String actorUniqueName = "me" + demoData.testRunUid + "@mastodon.example.com";
        final String content = "Sending note to the unknown yet Actor @" + actorUniqueName;
        // TypeTextAction doesn't work here due to auto-correction
        onView(withId(R.id.noteBodyEditText)).perform(new ReplaceTextAction(content));
        TestSuite.waitForIdleSync();

        ActivityTestHelper<TimelineActivity> helper2 = new ActivityTestHelper<>(getActivity());
        helper2.clickMenuItem(method + " clicker Send", R.id.noteSendButton);
        ActivityTestHelper.waitViewInvisible(method, editorView);

        String sql = "SELECT " + NoteTable._ID + " FROM " + NoteTable.TABLE_NAME + " WHERE "
                + NoteTable.CONTENT + " LIKE('%" + content + "%')";
        long noteId = 0;
        for (int attempt=0; attempt < 10; attempt++) {
            noteId = MyQuery.getLongs(sql).stream().findFirst().orElse(0L);
            if (noteId != 0) break;
            if (DbUtils.waitMs(method, 2000)) break;
        }
        assertTrue("Note '" + content + "' was not saved", noteId != 0);

        List<DownloadStatus> expected = Collections.singletonList(DownloadStatus.SENDING);
        DownloadStatus status = DownloadStatus.load(MyQuery.noteIdToLongColumnValue(NoteTable.NOTE_STATUS, noteId));
        assertThat(status, isIn(expected));

        Audience audience = Audience.load(mock.getData().getOrigin(), noteId);
        assertTrue("Audience should contain " + actorUniqueName +  "\n " + audience,
                audience.getActors().stream().anyMatch(a -> actorUniqueName.equals(a.getUniqueName())));

        Optional<HttpReadResult> result = Optional.empty();
        for (int attempt=0; attempt < 10; attempt++) {
            result = mock.getHttpMock().getResults().stream()
                    .filter(r -> r.formParams.toString().contains(actorUniqueName))
                    .findFirst();
            if (result.isPresent()) break;
            if (DbUtils.waitMs(method, 2000)) break;
        }
        assertTrue("The content should be sent: " + content, result.isPresent());
    }
}
