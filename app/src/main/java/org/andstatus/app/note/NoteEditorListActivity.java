/**
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

package org.andstatus.app.note;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;

import org.andstatus.app.ActivityRequestCode;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.timeline.LoadableListActivity;
import org.andstatus.app.timeline.ViewItem;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.UriUtils;

import java.util.Optional;

/**
 * @author yvolk@yurivolkov.com
 */
abstract public class NoteEditorListActivity<T extends ViewItem<T>> extends LoadableListActivity<T> implements NoteEditorContainer {
    private NoteEditor noteEditor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!isFinishing()) {
            noteEditor = new NoteEditor(this);
        }
    }

    @Override
    public boolean canSwipeRefreshChildScrollUp() {
        if (noteEditor != null && noteEditor.isVisible()) {
            return true;
        }
        return super.canSwipeRefreshChildScrollUp();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (ActivityRequestCode.fromId(requestCode)) {
            case ATTACH:
                if (resultCode == RESULT_OK && data != null) {
                    attachmentSelected(data);
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }

    private void attachmentSelected(Intent data) {
        Uri uri = UriUtils.notNull(data.getData());
        if (!UriUtils.isEmpty(uri)) {
            UriUtils.takePersistableUriPermission(getActivity(), uri, data.getFlags());
            getNoteEditor().startEditingCurrentWithAttachedMedia(uri, Optional.ofNullable(data.getType()));
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0
                && noteEditor != null && noteEditor.isVisible()) {
            if (getActivity().isFullScreen()) {
                getActivity().toggleFullscreen(TriState.FALSE);
            } else noteEditor.saveDraft();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }


    @Override
    protected void onPause() {
        if (noteEditor != null) noteEditor.saveAsBeingEditedAndHide();
        super.onPause();
    }

    @Override
    protected void onReceiveAfterExecutingCommand(CommandData commandData) {
        super.onReceiveAfterExecutingCommand(commandData);
        switch (commandData.getCommand()) {
            case UPDATE_NOTE:
                noteEditor.loadCurrentDraft();
                break;
            default:
                break;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!mFinishing) {
            noteEditor.loadCurrentDraft();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (noteEditor != null) {
            noteEditor.onCreateOptionsMenu(menu);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (noteEditor != null) {
            noteEditor.onPrepareOptionsMenu(menu);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public NoteEditor getNoteEditor() {
        return noteEditor;
    }

    @Override
    public void onNoteEditorVisibilityChange() {
        invalidateOptionsMenu();
    }

    @Override
    protected void onFullScreenToggle(boolean fullscreenNew) {
        if (noteEditor != null) noteEditor.onScreenToggle(false, fullscreenNew);
    }
}
