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

package org.andstatus.app.note;

import android.net.Uri;

import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.UriUtils;

public class NoteEditorCommand {
    private volatile Long currentMsgId = null;
    private Uri imageUriToSave = Uri.EMPTY;
    boolean beingEdited = false;
    boolean showAfterSave = false;
    private volatile NoteEditorLock lock = NoteEditorLock.EMPTY;
    
    volatile NoteEditorData currentData;
    final NoteEditorData previousData;

    public NoteEditorCommand(NoteEditorData currentData) {
        this(currentData, NoteEditorData.EMPTY);
    }

    public NoteEditorCommand(NoteEditorData currentData, NoteEditorData previousData) {
        if (currentData == null) {
            throw new IllegalArgumentException("currentData is null");
        }
        this.currentData = currentData;
        this.previousData = previousData == null ? NoteEditorData.EMPTY : previousData;
    }

    public boolean acquireLock(boolean wait) {
        if (hasLock()) {
            return true;
        }
        NoteEditorLock lock1 = new NoteEditorLock(true, getCurrentMsgId());
        if (lock1.acquire(wait)) {
            lock = lock1;
            return true;
        }
        return false;
    }

    public boolean releaseLock() {
        return lock.release();
    }

    public boolean hasLock() {
        return lock.acquired();
    }
    
    public long getCurrentMsgId() {
        if (currentData.isValid()) {
            return currentData.getMsgId();
        }
        if (currentMsgId == null) {
            currentMsgId = SharedPreferencesUtil.getLong(MyPreferences.KEY_BEING_EDITED_MESSAGE_ID);
        }
        return currentMsgId;
    }

    public void loadCurrent() {
        currentData = NoteEditorData.load(getCurrentMsgId());
    }

    public boolean isEmpty() {
        return currentData.isEmpty() && previousData.isEmpty() && imageUriToSave == Uri.EMPTY;
    }

    public NoteEditorCommand setMediaUri(Uri mediaUri) {
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
        if (currentData == NoteEditorData.EMPTY) {
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
