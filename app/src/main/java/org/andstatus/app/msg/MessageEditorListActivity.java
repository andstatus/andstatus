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

package org.andstatus.app.msg;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;

import org.andstatus.app.ActivityRequestCode;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.timeline.LoadableListActivity;
import org.andstatus.app.timeline.ViewItem;
import org.andstatus.app.util.UriUtils;

/**
 * @author yvolk@yurivolkov.com
 */
abstract public class MessageEditorListActivity<T extends ViewItem> extends LoadableListActivity<T> implements MessageEditorContainer {
    private MessageEditor messageEditor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!isFinishing()) {
            messageEditor = new MessageEditor(this);
        }
    }

    @Override
    public boolean canSwipeRefreshChildScrollUp() {
        if (messageEditor != null && messageEditor.isVisible()) {
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
            getMessageEditor().startEditingCurrentWithAttachedMedia(uri);
        }
    }

    @Override
    protected void onPause() {
        messageEditor.saveAsBeingEditedAndHide();
        super.onPause();
    }

    @Override
    protected void onReceiveAfterExecutingCommand(CommandData commandData) {
        super.onReceiveAfterExecutingCommand(commandData);
        switch (commandData.getCommand()) {
            case UPDATE_STATUS:
                messageEditor.loadCurrentDraft();
                break;
            default:
                break;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!mFinishing) {
            messageEditor.loadCurrentDraft();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (messageEditor != null) {
            messageEditor.onCreateOptionsMenu(menu);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (messageEditor != null) {
            messageEditor.onPrepareOptionsMenu(menu);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public MessageEditor getMessageEditor() {
        return messageEditor;
    }

    @Override
    public void onMessageEditorVisibilityChange() {
        invalidateOptionsMenu();
    }
}
