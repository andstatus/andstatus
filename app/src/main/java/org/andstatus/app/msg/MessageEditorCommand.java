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

import android.net.Uri;

import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.util.UriUtils;

public class MessageEditorCommand {
    private volatile Long currentMsgId = null;
    private Uri imageUriToSave = Uri.EMPTY;
    boolean beingEdited = false;
    boolean showAfterSave = false;

    volatile MessageEditorData currentData;
    final MessageEditorData previousData;

    public MessageEditorCommand(MessageEditorData currentData) {
        this(currentData, MessageEditorData.INVALID);
    }

    public MessageEditorCommand(MessageEditorData currentData, MessageEditorData previousData) {
        if (currentData == null) {
            throw new IllegalArgumentException("currentData is null");
        }
        this.currentData = currentData;
        this.previousData = previousData == null ? MessageEditorData.INVALID : previousData;
    }

    public long getCurrentMsgId() {
        if (currentData.isValid()) {
            return currentData.getMsgId();
        }
        if (currentMsgId == null) {
            currentMsgId = MyPreferences.getLong(MyPreferences.KEY_BEING_EDITED_MESSAGE_ID);
        }
        return currentMsgId;
    }

    public void loadCurrent() {
        currentData = MessageEditorData.load(getCurrentMsgId());
    }

    public boolean isEmpty() {
        return currentData.isEmpty() && previousData.isEmpty() && imageUriToSave == Uri.EMPTY;
    }

    public MessageEditorCommand setMediaUri(Uri mediaUri) {
        imageUriToSave = UriUtils.notNull(mediaUri);
        return this;
    }

    public Uri getMediaUri() {
        return imageUriToSave;
    }

    public boolean needToSavePreviousData() {
        return previousData.isValid() && !previousData.isEmpty()
                && (previousData.getMsgId() == 0 || currentData.getMsgId() != previousData.getMsgId());
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("Save ");
        if (currentData == MessageEditorData.INVALID) {
            builder.append("current draft,");
        } else {
            builder.append(currentData.toString());
        }
        if(showAfterSave) {
            builder.append("show,");
        }
        if(beingEdited) {
            builder.append("edit,");
        }
        if(!UriUtils.isEmpty(imageUriToSave)) {
            builder.append("image:'" + imageUriToSave + "',");
        }
        return builder.toString();
    }
}
