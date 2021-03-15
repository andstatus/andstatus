/**
 * Copyright (C) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.note

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import org.andstatus.app.ActivityRequestCode
import org.andstatus.app.service.CommandData
import org.andstatus.app.service.CommandEnum
import org.andstatus.app.timeline.LoadableListActivity
import org.andstatus.app.timeline.ViewItem
import org.andstatus.app.util.TriState
import org.andstatus.app.util.UriUtils
import java.util.*

/**
 * @author yvolk@yurivolkov.com
 */
abstract class NoteEditorListActivity<T : ViewItem<T>> : LoadableListActivity<T>(), NoteEditorContainer {
    private var noteEditor: NoteEditor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!isFinishing) {
            noteEditor = NoteEditor(this)
        }
    }

    override fun canSwipeRefreshChildScrollUp(): Boolean {
        return noteEditor?.isVisible() ?: super.canSwipeRefreshChildScrollUp()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (ActivityRequestCode.fromId(requestCode)) {
            ActivityRequestCode.ATTACH -> if (resultCode == RESULT_OK && data != null) {
                attachmentSelected(data)
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun attachmentSelected(data: Intent) {
        val uri = UriUtils.notNull(data.data)
        if (!UriUtils.isEmpty(uri)) {
            UriUtils.takePersistableUriPermission(getActivity(), uri, data.flags)
            getNoteEditor()?.startEditingCurrentWithAttachedMedia(uri, Optional.ofNullable(data.type))
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.repeatCount == 0 && noteEditor != null &&
                noteEditor?.isVisible() == true) {
            if (getActivity().isFullScreen()) {
                getActivity().toggleFullscreen(TriState.FALSE)
            } else noteEditor?.saveDraft()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onPause() {
        noteEditor?.saveAsBeingEditedAndHide()
        super.onPause()
    }

    override fun onReceiveAfterExecutingCommand(commandData: CommandData) {
        super.onReceiveAfterExecutingCommand(commandData)
        when (commandData.command) {
            CommandEnum.UPDATE_NOTE -> noteEditor?.loadCurrentDraft()
            else -> {
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isFinishing) {
            noteEditor?.loadCurrentDraft()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.let { noteEditor?.onCreateOptionsMenu(menu) }
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        noteEditor?.onPrepareOptionsMenu(menu)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun getNoteEditor(): NoteEditor? {
        return noteEditor
    }

    override fun onNoteEditorVisibilityChange() {
        invalidateOptionsMenu()
    }

    override fun onFullScreenToggle(fullscreenNew: Boolean) {
        noteEditor?.onScreenToggle(false, fullscreenNew)
    }
}