/*
 * Copyright (C) 2014-2017 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.provider.MediaStore
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import io.vavr.control.Try
import org.andstatus.app.ActivityRequestCode
import org.andstatus.app.R
import org.andstatus.app.account.MyAccount
import org.andstatus.app.actor.ActorAutoCompleteAdapter
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.data.DownloadStatus
import org.andstatus.app.data.TextMediaType
import org.andstatus.app.graphics.IdentifiableImageView
import org.andstatus.app.net.social.ApiRoutineEnum
import org.andstatus.app.os.AsyncTaskLauncher
import org.andstatus.app.os.MyAsyncTask
import org.andstatus.app.service.CommandData
import org.andstatus.app.service.CommandEnum
import org.andstatus.app.service.MyServiceManager
import org.andstatus.app.timeline.LoadableListActivity
import org.andstatus.app.util.MyCheckBox
import org.andstatus.app.util.MyHtml
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.MyUrlSpan
import org.andstatus.app.util.SharedPreferencesUtil
import org.andstatus.app.util.StringUtil
import org.andstatus.app.util.TriState
import org.andstatus.app.util.TryUtils
import org.andstatus.app.util.UriUtils
import org.andstatus.app.util.ViewUtils
import java.util.*
import kotlin.properties.Delegates

/**
 * "Enter your message here" box
 */
class NoteEditor(private val editorContainer: NoteEditorContainer) {
    private val editorView: ViewGroup
    private val noteBodyTokenizer: NoteBodyTokenizer = NoteBodyTokenizer()

    enum class ScreenToggleState(private val nextState: ScreenToggleState?, val isFullScreen: Boolean) {
        SHOW_TIMELINE(null, true), MAXIMIZE_EDITOR(SHOW_TIMELINE, true), INITIAL(MAXIMIZE_EDITOR, false), EMPTY(INITIAL, false);

        fun toggle(isNextState: Boolean, isFullscreen: Boolean): ScreenToggleState {
            val state = if (isNextState) nextState ?: INITIAL else if (this == EMPTY) INITIAL else this
            return if (isNextState || state.isFullScreen == isFullscreen) state else INITIAL
        }
    }

    var screenToggleState: ScreenToggleState = ScreenToggleState.EMPTY
    private val editorContentMediaType: TextMediaType = TextMediaType.PLAIN

    /**
     * Text to be sent
     */
    private var bodyView: NoteEditorBodyView by Delegates.notNull()
    private var mCharsLeftText: TextView by Delegates.notNull()
    private var editorData: NoteEditorData = NoteEditorData.EMPTY
    
    private fun getEditorView(): ViewGroup {
        var editorView = getActivity().findViewById<ViewGroup?>(R.id.note_editor)
        if (editorView == null) {
            val layoutParent = getActivity().findViewById<ViewGroup?>(R.id.relative_list_parent)
            val inflater = LayoutInflater.from(getActivity())
            editorView = inflater.inflate(R.layout.note_editor, null) as ViewGroup
            val layoutParams = RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE)
            editorView.layoutParams = layoutParams
            layoutParent.addView(editorView)
        }
        return editorView
    }

    private fun setupEditText() {
        bodyView = editorView.findViewById(R.id.noteBodyEditText)
        bodyView.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                editorData.setContent(s.toString(), editorContentMediaType)
                MyLog.v(NoteEditorData.TAG) { "Content updated to '" + editorData.getContent() + "'" }
                mCharsLeftText.setText(
                        editorData.getMyAccount().charactersLeftForNote(editorData.getContent()).toString())
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // Nothing to do
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Nothing to do
            }
        })
        bodyView.setOnKeyListener { v: View, keyCode: Int, event: KeyEvent ->
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER -> {
                        sendAndHide()
                        true
                    }
                    else -> false
                }
            } else false
        }
        bodyView.setOnEditorActionListener { _: TextView?, _: Int, _: KeyEvent? ->
            sendAndHide()
            true
        }

        // Allow vertical scrolling
        // See http://stackoverflow.com/questions/16605486/edit-text-not-scrollable-inside-scroll-view
        bodyView.setOnTouchListener { v: View, event: MotionEvent ->
            if (v.getId() == bodyView.getId()) {
                v.getParent().requestDisallowInterceptTouchEvent(true)
                when (event.getAction() and MotionEvent.ACTION_MASK) {
                    MotionEvent.ACTION_UP -> v.getParent().requestDisallowInterceptTouchEvent(false)
                }
            }
            false
        }
        bodyView.setTokenizer(noteBodyTokenizer)
    }

    private fun setupInternalButtons() {
        if (SharedPreferencesUtil.getBoolean(MyPreferences.KEY_SEND_IN_EDITOR_BUTTON, false)) {
            val sendButton: ImageView = editorView.findViewById(R.id.sendInEditorButton)
            sendButton.visibility = View.VISIBLE
            sendButton.setOnClickListener {
                sendAndHide()
            }
        }
    }

    private fun setupFullscreenToggle() {
        setupFullscreenToggleFor(R.id.note_editor_above_body)
        setupFullscreenToggleFor(R.id.note_editor_below_body)
        onScreenToggle(false, getActivity().isFullScreen())
    }

    private fun setupFullscreenToggleFor(id: Int) {
        val view = getActivity().findViewById<View?>(id)
        view?.setOnClickListener { v: View? -> onScreenToggle(true, getActivity().isFullScreen()) }
    }

    fun onCreateOptionsMenu(menu: Menu) {
        getActivity().getMenuInflater().inflate(R.menu.note_editor, menu)
        createCreateNoteButton(menu)
        createAttachButton(menu)
        createSendButton(menu)
        createSaveDraftButton(menu)
        createDiscardButton(menu)
    }

    private fun createCreateNoteButton(menu: Menu) {
        val item = menu.findItem(R.id.createNoteButton)
        item?.setOnMenuItemClickListener { item1: MenuItem? ->
            val accountForButton = accountForCreateNoteButton()
            if (accountForButton.isValid) {
                startEditingNote(NoteEditorData.newEmpty(accountForButton))
            }
            false
        }
    }

    private fun createAttachButton(menu: Menu) {
        val item = menu.findItem(R.id.attach_menu_id)
        item?.setOnMenuItemClickListener { item1: MenuItem? ->
            onAttach()
            false
        }
    }

    private fun createSendButton(menu: Menu) {
        val item = menu.findItem(R.id.noteSendButton)
        item?.setOnMenuItemClickListener { item1: MenuItem? ->
            sendAndHide()
            false
        }
    }

    private fun createSaveDraftButton(menu: Menu) {
        val item = menu.findItem(R.id.saveDraftButton)
        item?.setOnMenuItemClickListener { item1: MenuItem? ->
            saveDraft()
            false
        }
    }

    private fun createDiscardButton(menu: Menu) {
        val item = menu.findItem(R.id.discardButton)
        item?.setOnMenuItemClickListener { item1: MenuItem? ->
            discardAndHide()
            true
        }
    }

    fun onPrepareOptionsMenu(menu: Menu) {
        prepareCreateNoteButton(menu)
        prepareAttachButton(menu)
        prepareSendButton(menu)
        prepareSaveDraftButton(menu)
        prepareDiscardButton(menu)
    }

    private fun prepareCreateNoteButton(menu: Menu) {
        val item = menu.findItem(R.id.createNoteButton)
        if (item != null) {
            item.isVisible = !isVisible() && accountForCreateNoteButton().isValidAndSucceeded()
        }
    }

    private fun accountForCreateNoteButton(): MyAccount {
        return if (isVisible()) {
            editorData.getMyAccount()
        } else {
            editorContainer.getActivity().myContext.accounts.currentAccount
        }
    }

    private fun prepareAttachButton(menu: Menu) {
        val item = menu.findItem(R.id.attach_menu_id)
        if (item != null) {
            val enableAttach = (isVisible()
                    && SharedPreferencesUtil.getBoolean(MyPreferences.KEY_ATTACH_IMAGES_TO_MY_NOTES, true)
                    && (!editorData.visibility.isPrivate || editorData.getMyAccount().origin.originType
                    .allowAttachmentForPrivateNote()))
            item.isEnabled = enableAttach
            item.isVisible = enableAttach
        }
    }

    private fun prepareSendButton(menu: Menu) {
        val item = menu.findItem(R.id.noteSendButton)
        if (item != null) {
            item.isEnabled = isVisible()
            item.isVisible = isVisible()
        }
    }

    private fun prepareSaveDraftButton(menu: Menu) {
        val item = menu.findItem(R.id.saveDraftButton)
        if (item != null) {
            item.isVisible = isVisible()
        }
    }

    private fun prepareDiscardButton(menu: Menu) {
        val item = menu.findItem(R.id.discardButton)
        if (item != null) {
            item.isVisible = isVisible()
        }
    }

    fun show() {
        if (!isVisible()) {
            editorView.setVisibility(View.VISIBLE)
            bodyView.requestFocus()
            if (!isHardwareKeyboardAttached()) {
                openSoftKeyboard()
            }
            editorContainer.onNoteEditorVisibilityChange()
        }
    }

    private fun isHardwareKeyboardAttached(): Boolean {
        val c = getActivity().getResources().configuration
        return when (c.keyboard) {
            Configuration.KEYBOARD_12KEY, Configuration.KEYBOARD_QWERTY -> true
            else -> false
        }
    }

    private fun openSoftKeyboard() {
        val inputMethodManager = getActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.toggleSoftInputFromWindow(bodyView.getWindowToken(), InputMethodManager.SHOW_FORCED, 0)
    }

    fun hide() {
        editorData = NoteEditorData.EMPTY
        updateScreen()
        if (isVisible()) {
            editorView.setVisibility(View.GONE)
            closeSoftKeyboard()
            editorContainer.onNoteEditorVisibilityChange()
        }
    }

    private fun closeSoftKeyboard() {
        val inputMethodManager = getActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(bodyView.getWindowToken(), 0)
    }

    fun isVisible(): Boolean {
        return editorView.visibility == View.VISIBLE
    }

    fun startEditingSharedData(ma: MyAccount, shared: SharedNote) {
        MyLog.v(NoteEditorData.TAG) { "startEditingSharedData " + shared.toString() }
        updateDataFromScreen()
        val contentWithName: MyStringBuilder = MyStringBuilder.of(shared.content)
        if (!ma.origin.originType.hasNoteName && subjectHasAdditionalContent(shared.name, shared.content)) {
            shared.name.ifPresent { name: String -> contentWithName.prependWithSeparator(name, if (shared.textMediaType == TextMediaType.HTML) "<br/>" else "\n") }
        }
        val currentData: NoteEditorData = NoteEditorData.newEmpty(ma).setContent(contentWithName.toString(),
                shared.textMediaType ?: TextMediaType.UNKNOWN)
        if (ma.origin.originType.hasNoteName) {
            shared.name.ifPresent { name: String -> currentData.setName(name) }
        }
        val command = NoteEditorCommand(currentData, editorData)
        shared.mediaUri.ifPresent { mediaUri: Uri -> command.setMediaUri(mediaUri) }
        command.setMediaType(shared.mediaType)
        command.showAfterSave = true
        command.beingEdited = true
        saveData(command)
    }

    fun startEditingNote(data: NoteEditorData) {
        if (!data.isValid()) {
            MyLog.v(NoteEditorData.TAG) { "Not a valid data $data" }
            return
        }
        if (!data.mayBeEdited()) {
            MyLog.v(NoteEditorData.TAG) { "Cannot be edited $data" }
            return
        }
        data.activity.getNote().setStatus(DownloadStatus.DRAFT)
        updateDataFromScreen()
        val command = NoteEditorCommand(data, editorData)
        command.showAfterSave = true
        command.beingEdited = true
        saveData(command)
        if (data.getMyAccount().connection.hasApiEndpoint(ApiRoutineEnum.ACCOUNT_RATE_LIMIT_STATUS)) {
            MyServiceManager.sendForegroundCommand(
                    CommandData.newAccountCommand(CommandEnum.RATE_LIMIT_STATUS, data.getMyAccount()))
        }
    }

    fun startEditingCurrentWithAttachedMedia(mediaUri: Uri?, mediaType: Optional<String>) {
        updateDataFromScreen()
        val command = NoteEditorCommand(editorData.copy())
        command.beingEdited = true
        command.showAfterSave = true
        command.setMediaUri(mediaUri)
        command.setMediaType(mediaType)
        saveData(command)
    }

    fun updateScreen() {
        setAdapter()
        ViewUtils.showView(editorView, R.id.is_public, editorData.canChangeVisibility())
        MyCheckBox.set(getActivity(), R.id.is_public, editorData.visibility.isPublicCheckbox(), true)
        ViewUtils.showView(editorView, R.id.is_followers, editorData.canChangeIsFollowers())
        MyCheckBox.set(getActivity(), R.id.is_followers, editorData.visibility.isFollowers(), true)
        ViewUtils.showView(editorView, R.id.is_sensitive, editorData.canChangeIsSensitive())
        MyCheckBox.set(getActivity(), R.id.is_sensitive, editorData.getSensitive(), true)
        MyUrlSpan.showText(editorView, R.id.note_name_edit, editorData.activity.getNote().getName(), false,
                editorData.ma.origin.originType.hasNoteName)
        MyUrlSpan.showText(editorView, R.id.summary_edit, editorData.activity.getNote().summary, false,
                editorData.ma.origin.originType.hasNoteSummary)
        var body = MyHtml.fromContentStored(editorData.getContent(), editorContentMediaType)
        if (body != bodyView.getText().toString().trim { it <= ' ' }) {
            if (body.isNotEmpty()) {
                body += " "
            }
            if (!TextUtils.isEmpty(bodyView.getText()) && body.isNotEmpty()) {
                MyLog.v(NoteEditorData.TAG, "Body updated\n'${bodyView.getText()}' to \n'$body'", Exception())
            }
            bodyView.setText(body)
            bodyView.setSelection(bodyView.getText().toString().length)
        }
        MyUrlSpan.showText(editorView, R.id.note_author, if (shouldShowAccountName())
            editorData.getMyAccount().getAccountName() else "", false, false)
        showNoteDetails()
        MyUrlSpan.showText(editorView, R.id.inReplyToBody,
                editorData.activity.getNote().getInReplyTo().getNote().content, TextMediaType.HTML,
                false, false)
        mCharsLeftText.setText(editorData.getMyAccount().charactersLeftForNote(editorData.getContent()).toString())
        showAttachedImages()
    }

    private fun setAdapter() {
        if (editorData === NoteEditorData.EMPTY) {
            bodyView.setAdapter(null)
        } else {
            val adapterOld = bodyView.getAdapter() as ActorAutoCompleteAdapter?
            if (adapterOld == null || adapterOld.getOrigin() != editorData.getMyAccount().origin) {
                val adapter = ActorAutoCompleteAdapter(getActivity(),
                        editorData.getMyAccount().origin)
                bodyView.setAdapter(adapter)
            }
        }
    }

    private fun showNoteDetails() {
        val builder = MyStringBuilder()
        val inReplyToAuthor = editorData.activity.getNote().getInReplyTo().getAuthor()
        if (inReplyToAuthor.nonEmpty) {
            builder.withSpace(StringUtil.format(getActivity(), R.string.message_source_in_reply_to,
                    inReplyToAuthor.actorNameInTimeline))
        }
        if (editorData.getAttachedImageFiles().nonEmpty) {
            builder.withSpace("("
                    + getActivity().getText(R.string.label_with_media).toString() + " "
                    + editorData.getAttachedImageFiles().toMediaSummary(getActivity())
                    + ")")
        }
        MyUrlSpan.showText(editorView, R.id.noteEditDetails, builder.toString(), false, false)
    }

    private fun shouldShowAccountName(): Boolean {
        return getActivity().myContext.accounts.size() > 1
    }

    private fun showAttachedImages() {
        val attachmentsList = editorView.findViewById<LinearLayout?>(R.id.attachments_wrapper) ?: return
        if (!getActivity().isMyResumed() || screenToggleState == ScreenToggleState.SHOW_TIMELINE) {
            attachmentsList.visibility = View.GONE
            return
        }
        attachmentsList.removeAllViewsInLayout()
        for (imageFile in editorData.getAttachedImageFiles().list) {
            if (!imageFile.imageOrLinkMayBeShown()) continue
            val attachmentLayout = if (imageFile.imageMayBeShown()) if (imageFile.isTargetVideo())
                R.layout.attachment_video_preview else R.layout.attachment_image else R.layout.attachment_link
            val attachmentView = LayoutInflater.from(getActivity())
                    .inflate(attachmentLayout, attachmentsList, false)
            if (imageFile.imageMayBeShown()) {
                val imageView: IdentifiableImageView = attachmentView.findViewById(R.id.attachment_image)
                imageFile.showImage(getActivity(), imageView)
            } else {
                MyUrlSpan.showText(attachmentView, R.id.attachment_link,
                        imageFile.getTargetUri().toString(), true, false)
            }
            attachmentsList.addView(attachmentView)
        }
        attachmentsList.visibility = View.VISIBLE
    }

    private fun sendAndHide() {
        updateDataFromScreen()
        if (!editorData.isValid()) {
            discardAndHide()
        } else if (editorData.isEmpty) {
            Toast.makeText(getActivity(), R.string.cannot_send_empty_message, Toast.LENGTH_SHORT).show()
        } else if (editorData.getMyAccount().charactersLeftForNote(editorData.getContent()) < 0) {
            Toast.makeText(getActivity(), R.string.message_is_too_long, Toast.LENGTH_SHORT).show()
        } else if (editorData.getAttachedImageFiles()
                        .tooLargeAttachment(MyPreferences.getMaximumSizeOfAttachmentBytes()).isPresent) {
            Toast.makeText(getActivity(), R.string.attachment_is_too_large, Toast.LENGTH_SHORT).show()
        } else {
            val command = NoteEditorCommand(editorData.copy())
            command.currentData?.activity?.getNote()?.setStatus(DownloadStatus.SENDING)
            saveData(command)
        }
    }

    private fun updateDataFromScreen() {
        editorData
                .setPublicAndFollowers(MyCheckBox.isChecked(getActivity(), R.id.is_public, false),
                        MyCheckBox.isChecked(getActivity(), R.id.is_followers, false))
                .setSensitive(MyCheckBox.isChecked(getActivity(), R.id.is_sensitive, false))
                .setName(MyUrlSpan.getText(editorView, R.id.note_name_edit))
                .setSummary(MyUrlSpan.getText(editorView, R.id.summary_edit))
                .setContent(bodyView.getText().toString(), editorContentMediaType)
    }

    private fun discardAndHide() {
        val command = NoteEditorCommand(editorData.copy())
        command.currentData?.activity?.getNote()?.setDiscarded()
        saveData(command)
    }

    fun saveAsBeingEditedAndHide() {
        saveAndHide(true)
    }

    fun saveDraft() {
        saveAndHide(false)
    }

    private fun saveAndHide(beingEdited: Boolean) {
        updateDataFromScreen()
        val command = NoteEditorCommand(editorData.copy())
        command.beingEdited = beingEdited
        saveData(command)
    }

    private fun saveData(command: NoteEditorCommand) {
        command.acquireLock(false)
        MyPreferences.setBeingEditedNoteId(if (command.beingEdited) command.getCurrentNoteId() else 0)
        getActivity().toggleFullscreen(TriState.FALSE)
        hide()
        if (command.nonEmpty) {
            MyLog.v(NoteEditorData.TAG) { "Requested: $command" }
            AsyncTaskLauncher.execute(this, NoteSaver(this), command)
        } else {
            if (command.showAfterSave) {
                command.currentData?.let { showData(it) }
            }
            command.releaseLock()
        }
    }

    fun loadCurrentDraft() {
        if (editorData.isValid()) {
            MyLog.v(NoteEditorData.TAG, "loadCurrentDraft skipped: Editor data is valid")
            show()
            return
        }
        val noteId = MyPreferences.getBeingEditedNoteId()
        if (noteId == 0L) {
            MyLog.v(NoteEditorData.TAG, "loadCurrentDraft: no current draft")
            return
        }
        MyLog.v(NoteEditorData.TAG) { "loadCurrentDraft requested, noteId=$noteId" }
        AsyncTaskLauncher.execute(this,
                object : MyAsyncTask<Long, Void?, NoteEditorData>(this@NoteEditor.toString(),
                        PoolEnum.QUICK_UI) {
                    @Volatile
                    var lock: NoteEditorLock = NoteEditorLock.EMPTY

                    override suspend fun doInBackground(params: Long): Try<NoteEditorData> {
                        MyLog.v(NoteEditorData.TAG) { "loadCurrentDraft started, noteId=$params" }
                        val potentialLock = NoteEditorLock(false, params)
                        if (!potentialLock.acquire(true)) {
                            return TryUtils.notFound()
                        }
                        lock = potentialLock
                        MyLog.v(NoteEditorData.TAG, "loadCurrentDraft acquired lock")
                        val data: NoteEditorData = NoteEditorData.load(editorContainer.getActivity().myContext, params)
                        return if (data.mayBeEdited()) {
                            Try.success(data)
                        } else {
                            MyLog.v(NoteEditorData.TAG) { "Cannot be edited $data" }
                            MyPreferences.setBeingEditedNoteId(0)
                            TryUtils.notFound()
                        }
                    }

                    override suspend fun onPostExecute(result: Try<NoteEditorData>) {
                        result.onSuccess {
                            if (lock.acquired() && it.isValid() == true) {
                                if (editorData.isValid()) {
                                    MyLog.v(NoteEditorData.TAG, "Loaded draft is not used: Editor data is valid")
                                    show()
                                } else {
                                    showData(it)
                                }
                            }
                        }
                        lock.release()
                    }

                }, noteId)
    }

    private fun onAttach() {
        val intent = if (SharedPreferencesUtil.getBoolean(MyPreferences.KEY_MODERN_INTERFACE_TO_SELECT_AN_ATTACHMENT, true)) getIntentForKitKatMediaChooser() else getIntentToPickImages()
        getActivity().startActivityForResult(intent, ActivityRequestCode.ATTACH.id)
    }

    /**
     * See http://stackoverflow.com/questions/2169649/get-pick-an-image-from-androids-built-in-gallery-app-programmatically
     */
    private fun getIntentForKitKatMediaChooser(): Intent? {
        val intent = Intent()
                .setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf<String?>("image/*", "video/*"))
                .setAction(Intent.ACTION_GET_CONTENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .addFlags(UriUtils.flagsToTakePersistableUriPermission())
        return Intent.createChooser(intent, getActivity().getText(R.string.options_menu_attach))
    }

    /**
     * See http://stackoverflow.com/questions/19837358/android-kitkat-securityexception-when-trying-to-read-from-mediastore
     */
    private fun getIntentToPickImages(): Intent {
        return Intent(Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                .setType("image/*")
                .addFlags(UriUtils.flagsToTakePersistableUriPermission())
    }

    fun showData(data: NoteEditorData) {
        if (data.isValid()) {
            editorData = data
            noteBodyTokenizer.setOrigin(data.ma.origin)
            updateScreen()
            show()
        }
    }

    private fun getActivity(): LoadableListActivity<*> {
        return editorContainer.getActivity()
    }

    fun getData(): NoteEditorData {
        return editorData
    }

    fun onScreenToggle(isNextState: Boolean, isFullscreen: Boolean) {
        if (screenToggleState != ScreenToggleState.EMPTY && !isNextState
                && isFullscreen == screenToggleState.isFullScreen) return
        screenToggleState = screenToggleState.toggle(isNextState, isFullscreen)
        if (isNextState && isFullscreen != screenToggleState.isFullScreen) {
            getActivity().toggleFullscreen(TriState.fromBoolean(screenToggleState.isFullScreen))
        }
        val inReplyToBody = editorView.findViewById<TextView?>(R.id.inReplyToBody)
        when (screenToggleState) {
            ScreenToggleState.SHOW_TIMELINE -> {
                if (inReplyToBody != null) inReplyToBody.maxLines = 3
                bodyView.setMaxLines(5)
            }
            ScreenToggleState.MAXIMIZE_EDITOR -> {
                if (inReplyToBody != null) inReplyToBody.maxLines = Int.MAX_VALUE
                bodyView.setMaxLines(15)
            }
            else -> {
                if (inReplyToBody != null) inReplyToBody.maxLines = 5
                bodyView.setMaxLines(8)
            }
        }
        showAttachedImages()
    }

    companion object {
        fun subjectHasAdditionalContent(name: Optional<String>, content: Optional<String>): Boolean {
            if (!name.isPresent()) return false
            return if (!content.isPresent()) true else content.flatMap { c: String -> name
                    .map { n: String -> !c.startsWith(stripEllipsis(stripBeginning(n))) } }
                    .orElse(false)
        }

        /**
         * Strips e.g. "Note - " or "Note:"
         */
        fun stripBeginning(textIn: String?): String {
            if (textIn.isNullOrEmpty()) {
                return ""
            }
            var ind = textIn.indexOf("-")
            if (ind < 0) {
                ind = textIn.indexOf(":")
            }
            if (ind < 0) {
                return textIn
            }
            val beginningSeparators = "-:;,.[] "
            while (ind < textIn.length && beginningSeparators.contains(textIn.get(ind).toString())) {
                ind++
            }
            return if (ind >= textIn.length) {
                textIn
            } else textIn.substring(ind)
        }

        fun stripEllipsis(textIn: String?): String {
            if (textIn.isNullOrEmpty()) {
                return ""
            }
            var ind = textIn.length - 1
            val ellipsis = "… ."
            while (ind >= 0 && ellipsis.contains(textIn.get(ind).toString())) {
                ind--
            }
            return if (ind < -1) {
                ""
            } else textIn.substring(0, ind + 1)
        }
    }

    init {
        editorView = getEditorView()
        mCharsLeftText = editorView.findViewById(R.id.noteEditCharsLeftTextView)
        setupEditText()
        setupInternalButtons()
        setupFullscreenToggle()
        hide()
    }
}
