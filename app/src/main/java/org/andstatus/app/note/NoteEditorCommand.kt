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

package org.andstatus.app.note;

import android.net.Uri;

import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.util.IsEmpty;
import org.andstatus.app.util.UriUtils;

import java.util.Optional;

import static org.andstatus.app.context.MyContextHolder.myContextHolder;

public class NoteEditorCommand implements IsEmpty {
    private volatile Long currentNoteId = null;
    private Uri mediaUri = Uri.EMPTY;
    private Optional<String> mediaType = Optional.empty();
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
        NoteEditorLock lock1 = new NoteEditorLock(true, getCurrentNoteId());
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
    
    public long getCurrentNoteId() {
        if (currentData.isValid()) {
            return currentData.getNoteId();
        }
        if (currentNoteId == null) {
            currentNoteId = MyPreferences.getBeingEditedNoteId();
        }
        return currentNoteId;
    }

    public void loadCurrent() {
        currentData = NoteEditorData.load(myContextHolder.getNow(), getCurrentNoteId());
    }

    public boolean isEmpty() {
        return currentData.isEmpty() && previousData.isEmpty() && mediaUri == Uri.EMPTY;
    }

    public NoteEditorCommand setMediaUri(Uri mediaUri) {
        this.mediaUri = UriUtils.notNull(mediaUri);
        return this;
    }

    public Uri getMediaUri() {
        return mediaUri;
    }

    public boolean needToSavePreviousData() {
        return previousData.isValid() && previousData.nonEmpty()
                && (previousData.getNoteId() == 0 || currentData.getNoteId() != previousData.getNoteId());
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
        if(!UriUtils.isEmpty(mediaUri)) {
            builder.append("media:'" + mediaUri + "',");
        }
        return builder.toString();
    }

    public void setMediaType(Optional<String> mediaType) {
        this.mediaType = mediaType;
    }

    public Optional<String> getMediaType() {
        return mediaType;
    }

    public boolean hasMedia() {
        return UriUtils.nonEmpty(mediaUri);
    }
}
