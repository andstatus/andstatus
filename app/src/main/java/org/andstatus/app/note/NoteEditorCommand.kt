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
package org.andstatus.app.note

import android.net.Uri
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.note.NoteEditorData
import org.andstatus.app.util.IsEmpty
import org.andstatus.app.util.UriUtils
import java.util.*

class NoteEditorCommand @JvmOverloads constructor(currentData: NoteEditorData?, previousData: NoteEditorData? = NoteEditorData.Companion.EMPTY) : IsEmpty {
    @Volatile
    private var currentNoteId: Long? = null
    private var mediaUri = Uri.EMPTY
    private var mediaType: Optional<String> = Optional.empty()
    var beingEdited = false
    var showAfterSave = false

    @Volatile
    private var lock: NoteEditorLock? = NoteEditorLock.Companion.EMPTY

    @Volatile
    var currentData: NoteEditorData?
    val previousData: NoteEditorData?
    fun acquireLock(wait: Boolean): Boolean {
        if (hasLock()) {
            return true
        }
        val lock1 = NoteEditorLock(true, getCurrentNoteId())
        if (lock1.acquire(wait)) {
            lock = lock1
            return true
        }
        return false
    }

    fun releaseLock(): Boolean {
        return lock.release()
    }

    fun hasLock(): Boolean {
        return lock.acquired()
    }

    fun getCurrentNoteId(): Long {
        if (currentData.isValid()) {
            return currentData.getNoteId()
        }
        if (currentNoteId == null) {
            currentNoteId = MyPreferences.getBeingEditedNoteId()
        }
        return currentNoteId
    }

    fun loadCurrent() {
        currentData = NoteEditorData.Companion.load( MyContextHolder.myContextHolder.getNow(), getCurrentNoteId())
    }

    override val isEmpty: Boolean
        get() {
            return currentData.isEmpty && previousData.isEmpty && mediaUri === Uri.EMPTY
        }

    fun setMediaUri(mediaUri: Uri?): NoteEditorCommand? {
        this.mediaUri = UriUtils.notNull(mediaUri)
        return this
    }

    fun getMediaUri(): Uri? {
        return mediaUri
    }

    fun needToSavePreviousData(): Boolean {
        return (previousData.isValid() && previousData.nonEmpty
                && (previousData.getNoteId() == 0L || currentData.getNoteId() != previousData.getNoteId()))
    }

    override fun toString(): String {
        val builder = StringBuilder("Save ")
        if (currentData === NoteEditorData.Companion.EMPTY) {
            builder.append("current draft,")
        } else {
            builder.append(currentData.toString())
        }
        if (showAfterSave) {
            builder.append("show,")
        }
        if (beingEdited) {
            builder.append("edit,")
        }
        if (!UriUtils.isEmpty(mediaUri)) {
            builder.append("media:'$mediaUri',")
        }
        return builder.toString()
    }

    fun setMediaType(mediaType: Optional<String>) {
        this.mediaType = mediaType
    }

    fun getMediaType(): Optional<String> {
        return mediaType
    }

    fun hasMedia(): Boolean {
        return UriUtils.nonEmpty(mediaUri)
    }

    init {
        requireNotNull(currentData) { "currentData is null" }
        this.currentData = currentData
        this.previousData = previousData ?: NoteEditorData.Companion.EMPTY
    }
}