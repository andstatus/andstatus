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

package org.andstatus.app.note;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.Html;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.andstatus.app.ActivityRequestCode;
import org.andstatus.app.R;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.actor.ActorAutoCompleteAdapter;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.net.social.Connection.ApiRoutineEnum;
import org.andstatus.app.os.AsyncTaskLauncher;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.timeline.LoadableListActivity;
import org.andstatus.app.util.MyCheckBox;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyUrlSpan;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.StringUtils;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.UriUtils;
import org.andstatus.app.util.ViewUtils;

import static org.andstatus.app.util.I18n.appendWithSeparator;
import static org.andstatus.app.util.I18n.appendWithSpace;

/**
 * "Enter your message here" box 
 */
public class NoteEditor {

    private final NoteEditorContainer editorContainer;
    private final android.view.ViewGroup editorView;

    /**
     * Text to be sent
     */
    private NoteEditorBodyView bodyView;
    private final TextView mCharsLeftText;

    private NoteEditorData editorData = NoteEditorData.EMPTY;

    public NoteEditor(NoteEditorContainer editorContainer) {
        this.editorContainer = editorContainer;
        editorView = getEditorView();
        mCharsLeftText = editorView.findViewById(R.id.noteEditCharsLeftTextView);
        setupEditText();
        setupFullscreenToggle();
        hide();
    }

    private ViewGroup getEditorView() {
        ViewGroup editorView = getActivity().findViewById(R.id.note_editor);
        if (editorView == null) {
            ViewGroup layoutParent = getActivity().findViewById(R.id.relative_list_parent);
            LayoutInflater inflater = LayoutInflater.from(getActivity());
            editorView = (ViewGroup) inflater.inflate(R.layout.note_editor, null);

            RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
            editorView.setLayoutParams(layoutParams);
            layoutParent.addView(editorView);
        }
        return editorView;
    }

    private void setupEditText() {
        bodyView = editorView.findViewById(R.id.noteBodyEditText);
        bodyView.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                editorData.setContent(s.toString());
                MyLog.v(NoteEditorData.TAG, "Content updated to '" + editorData.getContent() + "'");
                mCharsLeftText.setText(String.valueOf(editorData.getMyAccount()
                        .charactersLeftForNote(editorData.getContent())));
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Nothing to do
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Nothing to do
            }
        });

        bodyView.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                switch (keyCode) {
                    case KeyEvent.KEYCODE_DPAD_CENTER:
                        sendAndHide();
                        return true;
                    default:
                        break;
                }
            }
            return false;
        });

        bodyView.setOnEditorActionListener((v, actionId, event) -> {
            if (event != null && (event.isAltPressed() ||
                    !SharedPreferencesUtil.getBoolean(MyPreferences.KEY_ENTER_SENDS_NOTE, false))) {
                return false;
            }
            sendAndHide();
            return true;
        });

        // Allow vertical scrolling
        // See http://stackoverflow.com/questions/16605486/edit-text-not-scrollable-inside-scroll-view
        bodyView.setOnTouchListener((v, event) -> {
            if (v.getId() == bodyView.getId()) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
                switch (event.getAction() & MotionEvent.ACTION_MASK) {
                    case MotionEvent.ACTION_UP:
                        v.getParent().requestDisallowInterceptTouchEvent(false);
                        break;
                }
            }
            return false;
        });

        bodyView.setTokenizer(new NoteBodyTokenizer());
    }

    private void setupFullscreenToggle() {
        setupFullscreenToggleFor(R.id.note_editor_above_body);
        setupFullscreenToggleFor(R.id.note_editor_below_body);
    }

    private void setupFullscreenToggleFor(int id) {
        View view = getActivity().findViewById(id);
        if (view != null) {
            view.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            getActivity().toggleFullscreen(TriState.UNKNOWN);
                        }
                    }
            );
        }
    }

    public void onCreateOptionsMenu(Menu menu) {
        getActivity().getMenuInflater().inflate(R.menu.note_editor, menu);
        createCreateNoteButton(menu);
        createAttachButton(menu);
        createSendButton(menu);
        createSaveDraftButton(menu);
        createDiscardButton(menu);
    }

    private void createCreateNoteButton(Menu menu) {
        MenuItem item = menu.findItem(R.id.createNoteButton);
        if (item != null) {
            item.setOnMenuItemClickListener(
                    new MenuItem.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            MyAccount accountForButton = accountForCreateNoteButton();
                            if (accountForButton != null) {
                                startEditingNote(NoteEditorData.newEmpty(accountForButton));
                            }
                            return false;
                        }
                    });
        }
    }

    private void createAttachButton(Menu menu) {
        MenuItem item = menu.findItem(R.id.attach_menu_id);
        if (item != null) {
            item.setOnMenuItemClickListener(
                    new MenuItem.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            onAttach();
                            return false;
                        }
                    });
        }
    }

    private void createSendButton(Menu menu) {
        MenuItem item = menu.findItem(R.id.noteSendButton);
        if (item != null) {
            item.setOnMenuItemClickListener(
                    item1 -> {
                        sendAndHide();
                        return false;
                    });
        }
    }

    private void createSaveDraftButton(Menu menu) {
        MenuItem item = menu.findItem(R.id.saveDraftButton);
        if (item != null) {
            item.setOnMenuItemClickListener(
                    new MenuItem.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            saveDraft();
                            return false;
                        }
                    });
        }
    }

    private void createDiscardButton(Menu menu) {
        MenuItem item = menu.findItem(R.id.discardButton);
        if (item != null) {
            item.setOnMenuItemClickListener(
                    new MenuItem.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            discardAndHide();
                            return false;
                        }
                    });
        }
    }

    public void onPrepareOptionsMenu(Menu menu) {
        prepareCreateNoteButton(menu);
        prepareAttachButton(menu);
        prepareSendButton(menu);
        prepareSaveDraftButton(menu);
        prepareDiscardButton(menu);
    }

    private void prepareCreateNoteButton(Menu menu) {
        MenuItem item = menu.findItem(R.id.createNoteButton);
        if (item != null) {
            item.setVisible(!isVisible() && accountForCreateNoteButton().isValidAndSucceeded());
        }
    }

    private MyAccount accountForCreateNoteButton() {
        if (isVisible()) {
            return editorData.getMyAccount();
        } else {
            return editorContainer.getCurrentMyAccount();
        }
    }

    private void prepareAttachButton(Menu menu) {
        MenuItem item = menu.findItem(R.id.attach_menu_id);
        if (item != null) {
            boolean enableAttach = isVisible()
                    && SharedPreferencesUtil.getBoolean(MyPreferences.KEY_ATTACH_IMAGES_TO_MY_NOTES, true)
                    && (editorData.getPublic().notFalse || editorData.getMyAccount().getOrigin().getOriginType()
                    .allowAttachmentForPrivateNote());
            item.setEnabled(enableAttach);
            item.setVisible(enableAttach);
        }
    }

    private void prepareSendButton(Menu menu) {
        MenuItem item = menu.findItem(R.id.noteSendButton);
        if (item != null) {
            item.setEnabled(isVisible());
            item.setVisible(isVisible());
        }
    }

    private void prepareSaveDraftButton(Menu menu) {
        MenuItem item = menu.findItem(R.id.saveDraftButton);
        if (item != null) {
            item.setVisible(isVisible());
        }
    }

    private void prepareDiscardButton(Menu menu) {
        MenuItem item = menu.findItem(R.id.discardButton);
        if (item != null) {
            item.setVisible(isVisible());
        }
    }

    public void show() {
        if (!isVisible()) {
            editorView.setVisibility(View.VISIBLE);
            bodyView.requestFocus();
            if (!isHardwareKeyboardAttached()) {
                openSoftKeyboard();
            }
            editorContainer.onNoteEditorVisibilityChange();
        }
    }
    
    private boolean isHardwareKeyboardAttached() {
        Configuration c = getActivity().getResources().getConfiguration();
        switch (c.keyboard) {
            case Configuration.KEYBOARD_12KEY:
            case Configuration.KEYBOARD_QWERTY:
                return true;
            default:
                return false;
        }
    }

    private void openSoftKeyboard() {
        InputMethodManager inputMethodManager = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.toggleSoftInputFromWindow(bodyView.getWindowToken(), InputMethodManager.SHOW_FORCED, 0);
    }
    
    public void hide() {
        editorData = NoteEditorData.EMPTY;
        updateScreen();
        if (isVisible()) {
            editorView.setVisibility(View.GONE);
            closeSoftKeyboard();
            editorContainer.onNoteEditorVisibilityChange();
        }
    }

    private void closeSoftKeyboard() {
        InputMethodManager inputMethodManager = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(bodyView.getWindowToken(), 0);
    }
    
    public boolean isVisible() {
        return editorView.getVisibility() == View.VISIBLE;
    }

    public void startEditingSharedData(final MyAccount ma, final String textToShare, final Uri mediaToShare) {
        MyLog.v(NoteEditorData.TAG, "startEditingSharedData " + textToShare + " uri: " + mediaToShare);
        updateDataFromScreen();
        NoteEditorCommand command = new NoteEditorCommand(
                NoteEditorData.newEmpty(ma).setContent(textToShare), editorData)
                .setMediaUri(mediaToShare);
        command.showAfterSave = true;
        command.beingEdited = true;
        saveData(command);
    }

    public void startEditingNote(NoteEditorData data) {
        if (!data.isValid()) {
            MyLog.v(NoteEditorData.TAG, "Not a valid data " + data);
            return;
        }
        if (!data.mayBeEdited()) {
            MyLog.v(NoteEditorData.TAG, "Cannot be edited " + data);
            return;
        }
        data.activity.getNote().setStatus(DownloadStatus.DRAFT);
        updateDataFromScreen();
        NoteEditorCommand command = new NoteEditorCommand(data, editorData);
        command.showAfterSave = true;
        command.beingEdited = true;
        saveData(command);
        if (data.getMyAccount().getConnection().isApiSupported(ApiRoutineEnum.ACCOUNT_RATE_LIMIT_STATUS)) {
            MyServiceManager.sendForegroundCommand(
                    CommandData.newAccountCommand(CommandEnum.RATE_LIMIT_STATUS, data.getMyAccount()));
        }
    }

    public void startEditingCurrentWithAttachedMedia(Uri mediaUri) {
        updateDataFromScreen();
        NoteEditorCommand command = new NoteEditorCommand(editorData.copy());
        command.beingEdited = true;
        command.showAfterSave = true;
        command.setMediaUri(mediaUri);
        saveData(command);
    }

    public void updateScreen() {
        setAdapter();
        ViewUtils.showView(editorView, R.id.is_public, editorData.canChangeIsPublic());
        MyCheckBox.set(getActivity(), R.id.is_public, editorData.getPublic().isTrue, true);
        MyUrlSpan.showText(editorView, R.id.note_name_edit, editorData.activity.getNote().getName(), false,
                editorData.ma.getOrigin().getOriginType().hasNoteName);
        String body = editorData.activity.getNote().getContent().trim();
        if (!body.equals(bodyView.getText().toString().trim())) {
            if (!TextUtils.isEmpty(body)) {
                body += " ";
            }
            if (!TextUtils.isEmpty(bodyView.getText()) && !TextUtils.isEmpty(body)) {
                MyLog.v(NoteEditorData.TAG, "Body updated '" + bodyView.getText()
                + "' to '" + body + "'", new IllegalStateException());
            }
            bodyView.setText(body);
            bodyView.setSelection(bodyView.getText().toString().length());
        }
        MyUrlSpan.showText(editorView, R.id.note_author, shouldShowAccountName()
                ? editorData.getMyAccount().getAccountName() : "", false, false);
        showNoteDetails();
        MyUrlSpan.showText(editorView, R.id.inReplyToBody,
                Html.fromHtml(editorData.activity.getNote().getInReplyTo().getNote().getContent()).toString(),
                false, false);
        mCharsLeftText.setText(String.valueOf(editorData.getMyAccount()
                .charactersLeftForNote(body)));
        showAttachedImage();
    }

    private void setAdapter() {
        if (editorData == NoteEditorData.EMPTY) {
            bodyView.setAdapter(null);
        } else {
            ActorAutoCompleteAdapter adapterOld = (ActorAutoCompleteAdapter) bodyView.getAdapter();
            if (adapterOld == null || !adapterOld.getOrigin().equals(editorData.getMyAccount().getOrigin())) {
                ActorAutoCompleteAdapter adapter = new ActorAutoCompleteAdapter(getActivity(),
                        editorData.getMyAccount().getOrigin());
                bodyView.setAdapter(adapter);
            }
        }
    }

    private void showNoteDetails() {
        StringBuilder noteDetails = new StringBuilder();
        String replyToName = editorData.getInReplyToNoteId() == 0
                ? "" : MyQuery.noteIdToUsername(NoteTable.AUTHOR_ID, editorData.getInReplyToNoteId(),
                MyPreferences.getActorInTimeline());
        if (StringUtils.nonEmpty(replyToName)) {
            appendWithSpace(noteDetails, String.format(getActivity().getText(R.string.message_source_in_reply_to).toString(), replyToName));
        }
        if (editorData.activity.getNote().audience().hasNonPublic()) {
            String recipientNames = editorData.activity.getNote().audience().getUsernames();
            if (StringUtils.nonEmpty(recipientNames) && !recipientNames.equals(replyToName) ) {
                appendWithSeparator(noteDetails, String.format(getActivity().getText(R.string.message_source_to).toString(), recipientNames), "\n");
            }
        }
        if (editorData.getAttachment().nonEmpty()) {
            appendWithSpace(noteDetails,"("
                    + getActivity().getText(R.string.label_with_media).toString() + " "
                    + editorData.getAttachment().mediaMetadata.toDetails() + ", "
                    + editorData.getAttachment().fileSize/1024 + "K"
                    + ")");
        }
        MyUrlSpan.showText(editorView, R.id.noteEditDetails, noteDetails.toString(), false, false);
    }

    private boolean shouldShowAccountName() {
        return getActivity().getMyContext().accounts().size() > 1;
    }

    private void showAttachedImage() {
        ImageView imageView = editorView.findViewById(R.id.attached_image);
        if (editorData.image == null) {
            imageView.setVisibility(View.GONE);
        } else {
            imageView.setImageDrawable(editorData.image.getDrawable());
            imageView.setVisibility(View.VISIBLE);
        }
    }

    private void sendAndHide() {
        updateDataFromScreen();
        if (!editorData.isValid()) {
            discardAndHide();
        } else if (editorData.isEmpty()) {
            Toast.makeText(getActivity(), R.string.cannot_send_empty_message,
                    Toast.LENGTH_SHORT).show();
        } else if (editorData.getMyAccount().charactersLeftForNote(editorData.getContent()) < 0) {
            Toast.makeText(getActivity(), R.string.message_is_too_long,
                    Toast.LENGTH_SHORT).show();
        } else {
            NoteEditorCommand command = new NoteEditorCommand(editorData.copy());
            command.currentData.activity.getNote().setStatus(DownloadStatus.SENDING);
            MyLog.onSendingNoteStart();
            saveData(command);
        }
    }

    private void updateDataFromScreen() {
        editorData.setName(MyUrlSpan.getText(editorView, R.id.note_name_edit));
        editorData.setContent(bodyView.getText().toString());
        editorData.setPublic(MyCheckBox.isChecked(getActivity(), R.id.is_public, false));
    }

    private void discardAndHide() {
        NoteEditorCommand command = new NoteEditorCommand(editorData.copy());
        command.currentData.activity.getNote().setDiscarded();
        saveData(command);
    }

    public void saveAsBeingEditedAndHide() {
        saveAndHide(true);
    }

    private void saveDraft() {
        saveAndHide(false);
    }

    private void saveAndHide(boolean beingEdited) {
        updateDataFromScreen();
        NoteEditorCommand command = new NoteEditorCommand(editorData.copy());
        command.beingEdited = beingEdited;
        saveData(command);
    }

    private void saveData(NoteEditorCommand command) {
        command.acquireLock(false);
        SharedPreferencesUtil.putLong(MyPreferences.KEY_BEING_EDITED_NOTE_ID,
                command.beingEdited ? command.getCurrentNoteId() : 0);
        hide();
        if (!command.isEmpty()) {
            MyLog.v(NoteEditorData.TAG, "Requested: " + command);
            new AsyncTaskLauncher<NoteEditorCommand>().execute(this, true,
                    new NoteSaver(this), command);
        } else {
            if (command.showAfterSave) {
                showData(command.currentData);
            }
            command.releaseLock();
        }
    }

    public void loadCurrentDraft() {
        if (editorData.isValid()) {
            MyLog.v(NoteEditorData.TAG, "loadCurrentDraft skipped: Editor data is valid");
            show();
            return;
        }
        long noteId = SharedPreferencesUtil.getLong(MyPreferences.KEY_BEING_EDITED_NOTE_ID);
        if (noteId == 0) {
            MyLog.v(NoteEditorData.TAG, "loadCurrentDraft: no current draft");
            return;
        }
        MyLog.v(NoteEditorData.TAG, "loadCurrentDraft requested, noteId=" + noteId);
        new AsyncTaskLauncher<Long>().execute(this, true,
                new MyAsyncTask<Long, Void, NoteEditorData>(NoteEditor.this.toString(),
                        MyAsyncTask.PoolEnum.QUICK_UI) {
                    volatile NoteEditorLock lock = NoteEditorLock.EMPTY;

                    @Override
                    protected NoteEditorData doInBackground2(Long... params) {
                        long noteId = params[0];
                        MyLog.v(NoteEditorData.TAG, "loadCurrentDraft started, noteId=" + noteId);
                        NoteEditorLock potentialLock = new NoteEditorLock(false, noteId);
                        if (!potentialLock.acquire(true)) {
                            return NoteEditorData.EMPTY;
                        }
                        lock = potentialLock;
                        MyLog.v(NoteEditorData.TAG, "loadCurrentDraft acquired lock");

                        final NoteEditorData data = NoteEditorData.load(editorContainer.getActivity().getMyContext(),
                                noteId);
                        if (data.mayBeEdited()) {
                            return data;
                        } else {
                            MyLog.v(NoteEditorData.TAG, "Cannot be edited " + data);
                            SharedPreferencesUtil.putLong(MyPreferences.KEY_BEING_EDITED_NOTE_ID, 0);
                            return NoteEditorData.EMPTY;
                        }
                    }

                    @Override
                    protected void onCancelled() {
                        lock.release();
                    }

                    @Override
                    protected void onPostExecute2(NoteEditorData data) {
                        if (lock.acquired() && data.isValid()) {
                            if (editorData.isValid()) {
                                MyLog.v(NoteEditorData.TAG, "Loaded draft is not used: Editor data is valid");
                                show();
                            } else {
                                showData(data);
                            }
                        }
                        lock.release();
                    }
                }
                , noteId);
    }

    private void onAttach() {
		Intent intent = SharedPreferencesUtil.getBoolean(MyPreferences.KEY_MODERN_INTERFACE_TO_SELECT_AN_ATTACHMENT, true) ?
            getIntentForKitKatMediaChooser() :
            getIntentToPickImages();
        getActivity().startActivityForResult(intent, ActivityRequestCode.ATTACH.id);
    }

    /**
     * See http://stackoverflow.com/questions/2169649/get-pick-an-image-from-androids-built-in-gallery-app-programmatically
     */
	private Intent getIntentForKitKatMediaChooser() {
        Intent intent = new Intent()
                .setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"})
                .setAction(Intent.ACTION_GET_CONTENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .addFlags(UriUtils.flagsToTakePersistableUriPermission());
        return Intent.createChooser(intent, getActivity().getText(R.string.options_menu_attach));
	}
	
    /**
     * See http://stackoverflow.com/questions/19837358/android-kitkat-securityexception-when-trying-to-read-from-mediastore
     */
	private Intent getIntentToPickImages() {
        return new Intent(Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                .setType("*/*")
                .addFlags(UriUtils.flagsToTakePersistableUriPermission());
	}

    public void showData(NoteEditorData data) {
        if (data.isValid()) {
            editorData = data;
            updateScreen();
            show();
        }
    }

    private LoadableListActivity getActivity() {
        return editorContainer.getActivity();
    }

    public NoteEditorData getData() {
        return editorData;
    }
}
