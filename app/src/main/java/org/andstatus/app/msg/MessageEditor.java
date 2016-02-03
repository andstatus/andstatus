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

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.AsyncTask;
import android.text.Editable;
import android.text.Html;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.andstatus.app.ActivityRequestCode;
import org.andstatus.app.MyBaseListActivity;
import org.andstatus.app.R;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.TimelineType;
import org.andstatus.app.net.social.Connection.ApiRoutineEnum;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.AsyncTaskLauncher;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.UriUtils;

/**
 * "Enter your message here" box 
 */
public class MessageEditor {

    private final ActionableMessageList mMessageList;
    private final android.view.ViewGroup mEditorView;

    /**
     * Text to be sent
     */
    private EditText bodyEditText;
    private final TextView mCharsLeftText;

    private MessageEditorData editorData = MessageEditorData.INVALID;

    public MessageEditor(ActionableMessageList actionableMessageList) {
        mMessageList = actionableMessageList;
        mEditorView = getEditorView();
        mCharsLeftText = (TextView) mEditorView.findViewById(R.id.messageEditCharsLeftTextView);
        setupEditText();
        hide();
    }

    private ViewGroup getEditorView() {
        ViewGroup editorView = (ViewGroup) getActivity().findViewById(R.id.message_editor);
        if (editorView == null) {
            ViewGroup layoutParent = (ViewGroup) getActivity().findViewById(R.id.myListParent);
            LayoutInflater inflater = LayoutInflater.from(getActivity());
            editorView = (ViewGroup) inflater.inflate(R.layout.message_editor, null);

            RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
            editorView.setLayoutParams(layoutParams);
            layoutParent.addView(editorView);
        }
        return editorView;
    }

    private void setupEditText() {
        bodyEditText = (EditText) mEditorView.findViewById(R.id.messageBodyEditText);
        bodyEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                editorData.body = s.toString();
                MyLog.v(MessageEditorData.TAG, "Body updated to '" + editorData.body + "'");
                mCharsLeftText.setText(String.valueOf(editorData.getMyAccount().charactersLeftForMessage(editorData.body)));
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

        bodyEditText.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
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
            }
        });

        bodyEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (event != null && (event.isAltPressed() ||
                        !MyPreferences.getBoolean(MyPreferences.KEY_ENTER_SENDS_MESSAGE, true))) {
                    return false;
                }
                sendAndHide();
                return true;
            }
        });
    }

    public void onCreateOptionsMenu(Menu menu) {
        createCreateMessageButton(menu);
        createAttachButton(menu);
        createSendButton(menu);
        createSaveDraftButton(menu);
        createDiscardButton(menu);
    }

    private void createCreateMessageButton(Menu menu) {
        MenuItem item = menu.findItem(R.id.createMessageButton);
        if (item != null) {
            item.setOnMenuItemClickListener(
                    new MenuItem.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            MyAccount accountForButton = accountForCreateMessageButton();
                            if (accountForButton != null) {
                                startEditingMessage(MessageEditorData.newEmpty(accountForButton));
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
        MenuItem item = menu.findItem(R.id.messageSendButton);
        if (item != null) {
            item.setOnMenuItemClickListener(
                    new MenuItem.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            sendAndHide();
                            return false;
                        }
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
        prepareCreateMessageButton(menu);
        prepareAttachButton(menu);
        prepareSendButton(menu);
        prepareSaveDraftButton(menu);
        prepareDiscardButton(menu);
    }

    private void prepareCreateMessageButton(Menu menu) {
        MenuItem item = menu.findItem(R.id.createMessageButton);
        if (item != null) {
            MyAccount accountForButton = accountForCreateMessageButton();
            item.setVisible(!isVisible()
                    && accountForButton.isValidAndSucceeded()
                    && mMessageList.getTimelineType() != TimelineType.DIRECT
                    && mMessageList.getTimelineType() != TimelineType.MESSAGES_TO_ACT);
        }
    }

    private MyAccount accountForCreateMessageButton() {
        if (isVisible()) {
            return editorData.getMyAccount();
        } else {
            return MyContextHolder.get().persistentAccounts().fromUserId(
                    mMessageList.getCurrentMyAccountUserId());
        }
    }

    private void prepareAttachButton(Menu menu) {
        MenuItem item = menu.findItem(R.id.attach_menu_id);
        if (item != null) {
            boolean enableAttach = isVisible()
                    && MyPreferences.getBoolean(MyPreferences.KEY_ATTACH_IMAGES, true)
                    && (editorData.recipientId == 0 || editorData.getMyAccount().getOrigin().getOriginType()
                    .allowAttachmentForDirectMessage());
            item.setEnabled(enableAttach);
            item.setVisible(enableAttach);
        }
    }

    private void prepareSendButton(Menu menu) {
        MenuItem item = menu.findItem(R.id.messageSendButton);
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
            mEditorView.setVisibility(View.VISIBLE);
            bodyEditText.requestFocus();
            if (!isHardwareKeyboardAttached()) {
                openSoftKeyboard();
            }
            mMessageList.onMessageEditorVisibilityChange();
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
        inputMethodManager.toggleSoftInputFromWindow(bodyEditText.getWindowToken(), InputMethodManager.SHOW_FORCED, 0);
    }
    
    public void hide() {
        editorData = MessageEditorData.INVALID;
        updateScreen();
        if (isVisible()) {
            mEditorView.setVisibility(View.GONE);
            closeSoftKeyboard();
            mMessageList.onMessageEditorVisibilityChange();
        }
    }

    private void closeSoftKeyboard() {
        InputMethodManager inputMethodManager = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(bodyEditText.getWindowToken(), 0);
    }
    
    public boolean isVisible() {
        return mEditorView.getVisibility() == View.VISIBLE;
    }

    public void startEditingSharedData(final MyAccount ma, final String textToShare, final Uri mediaToShare) {
        MyLog.v(MessageEditorData.TAG, "startEditingSharedData " + textToShare + " uri: " + mediaToShare);
        updateDataFromScreen();
        MessageEditorCommand command = new MessageEditorCommand(
                MessageEditorData.newEmpty(ma).setBody(textToShare), editorData)
                .setMediaUri(mediaToShare);
        command.showAfterSave = true;
        command.beingEdited = true;
        saveData(command);
    }

    public void startEditingMessage(MessageEditorData data) {
        if (!data.isValid()) {
            MyLog.v(MessageEditorData.TAG, "Not a valid data " + data);
            return;
        }
        if (!data.status.mayBeEdited()) {
            MyLog.v(MessageEditorData.TAG, "Cannot be edited " + data);
            return;
        }
        data.status = DownloadStatus.DRAFT;
        updateDataFromScreen();
        MessageEditorCommand command = new MessageEditorCommand(data, editorData);
        command.showAfterSave = true;
        command.beingEdited = true;
        saveData(command);
        if (data.getMyAccount().getConnection().isApiSupported(ApiRoutineEnum.ACCOUNT_RATE_LIMIT_STATUS)) {
            // Start asynchronous task that will show Rate limit status
            MyServiceManager.sendForegroundCommand(new CommandData(CommandEnum.RATE_LIMIT_STATUS, data.getMyAccount().getAccountName()));
        }
    }

    public void startEditingCurrentWithAttachedMedia(Uri mediaUri) {
        updateDataFromScreen();
        MessageEditorCommand command = new MessageEditorCommand(editorData.copy());
        command.beingEdited = true;
        command.showAfterSave = true;
        command.setMediaUri(mediaUri);
        saveData(command);
    }

    public void updateScreen() {
        if (!editorData.body.trim().equals(bodyEditText.getText().toString().trim())) {
            if (!TextUtils.isEmpty(bodyEditText.getText()) && !TextUtils.isEmpty(editorData.body)) {
                MyLog.v(MessageEditorData.TAG, "Body updated '" + bodyEditText.getText()
                + "' to '" + editorData.body + "'", new IllegalStateException());
            }
            bodyEditText.setText(editorData.body);
            bodyEditText.setSelection(bodyEditText.getText().toString().length());
        }
        showIfNotEmpty(R.id.message_author,
                shouldShowAccountName() ? editorData.getMyAccount().getAccountName() : "");
        showMessageDetails();
        showIfNotEmpty(R.id.inReplyToBody, editorData.inReplyToBody);
        mCharsLeftText.setText(String.valueOf(editorData.getMyAccount()
                .charactersLeftForMessage(editorData.body)));
        showAttachedImage();
    }

    private void showIfNotEmpty(int viewId, String value) {
        TextView textView = (TextView) mEditorView.findViewById(viewId);
        if (TextUtils.isEmpty(value)) {
            textView.setText("");
            textView.setVisibility(View.GONE);
        } else {
            textView.setText(Html.fromHtml(value));
            textView.setVisibility(View.VISIBLE);
        }
    }

    private void showMessageDetails() {
        String messageDetails = "";
        if (editorData.inReplyToId != 0) {
            String replyToName = MyQuery.msgIdToUsername(MyDatabase.Msg.AUTHOR_ID, editorData.inReplyToId, MyPreferences.userInTimeline());
            messageDetails += " " + String.format(
                    MyContextHolder.get().context().getText(R.string.message_source_in_reply_to).toString(),
                    replyToName);
        }
        if (editorData.recipientId != 0) {
            String recipientName = MyQuery.userIdToWebfingerId(editorData.recipientId);
            if (!TextUtils.isEmpty(recipientName)) {
                messageDetails += " " + String.format(
                        MyContextHolder.get().context().getText(R.string.message_source_to).toString(),
                        recipientName);
            }
        }
        if (!UriUtils.isEmpty(editorData.getMediaUri())) {
            messageDetails += " (" + MyContextHolder.get().context().getText(R.string.label_with_media).toString()
                    + " " + editorData.getImageSize().x + "x" + editorData.getImageSize().y
                    + ", " + editorData.getImageFileSize()/1024 + "K" +
                    ")";
        }
        showIfNotEmpty(R.id.messageEditDetails, messageDetails);
    }

    private boolean shouldShowAccountName() {
        boolean should = mMessageList.isTimelineCombined()
                || mMessageList.getTimelineType() == TimelineType.USER;
        if (!should) {
            should = editorData.getMyAccount().getUserId() != mMessageList.getCurrentMyAccountUserId();
        }
        return should;
    }

    private void showAttachedImage() {
        ImageView imageView = (ImageView) mEditorView.findViewById(R.id.attached_image);
        if (editorData.imageDrawable == null) {
            imageView.setVisibility(View.GONE);
        } else {
            imageView.setImageDrawable(editorData.imageDrawable);
            imageView.setVisibility(View.VISIBLE);
        }
    }

    private void sendAndHide() {
        updateDataFromScreen();
        if (!editorData.isValid()) {
            discardAndHide();
        } else if (TextUtils.isEmpty(editorData.body.trim())) {
            Toast.makeText(getActivity(), R.string.cannot_send_empty_message,
                    Toast.LENGTH_SHORT).show();
        } else if (editorData.getMyAccount().charactersLeftForMessage(editorData.body) < 0) {
            Toast.makeText(getActivity(), R.string.message_is_too_long,
                    Toast.LENGTH_SHORT).show();
        } else {
            MessageEditorCommand command = new MessageEditorCommand(editorData.copy());
			if (MyPreferences.getBoolean(MyPreferences.KEY_SENDING_MESSAGES_LOG_ENABLED, false)) {
				MyLog.setLogToFile(true);
			}
            command.currentData.status = DownloadStatus.SENDING;
            saveData(command);
        }
    }

    private void updateDataFromScreen() {
        editorData.body = bodyEditText.getText().toString();
    }

    private void discardAndHide() {
        MessageEditorCommand command = new MessageEditorCommand(editorData.copy());
        command.currentData.status = DownloadStatus.DELETED;
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
        MessageEditorCommand command = new MessageEditorCommand(editorData.copy());
        command.beingEdited = beingEdited;
        saveData(command);
    }

    private void saveData(MessageEditorCommand command) {
        command.acquireLock(false);
        MyPreferences.putLong(MyPreferences.KEY_BEING_EDITED_MESSAGE_ID,
                command.beingEdited ? command.getCurrentMsgId() : 0);
        hide();
        if (!command.isEmpty()) {
            MyLog.v(MessageEditorData.TAG, "Requested: " + command);
            new AsyncTaskLauncher<MessageEditorCommand>().execute(this,
                    new MessageEditorSaver(this), true, command);
        } else {
            if (command.showAfterSave) {
                showData(command.currentData);
            }
            command.releaseLock();
        }
    }

    public void loadCurrentDraft() {
        if (editorData.isValid()) {
            MyLog.v(MessageEditorData.TAG, "loadCurrentDraft skipped: Editor data is valid");
            show();
            return;
        }
        long msgId = MyPreferences.getLong(MyPreferences.KEY_BEING_EDITED_MESSAGE_ID);
        if (msgId == 0) {
            MyLog.v(MessageEditorData.TAG, "loadCurrentDraft: no current draft");
            return;
        }
        MyLog.v(MessageEditorData.TAG, "loadCurrentDraft requested, msgId=" + msgId);
        new AsyncTaskLauncher<Long>().execute(this,
                new AsyncTask<Long, Void, MessageEditorData>() {
                    volatile MessageEditorLock lock = MessageEditorLock.EMPTY;

                    @Override
                    protected MessageEditorData doInBackground(Long... params) {
                        long msgId = params[0];
                        MyLog.v(MessageEditorData.TAG, "loadCurrentDraft started, msgId=" + msgId);
                        MessageEditorLock potentialLock = new MessageEditorLock(false, msgId);
                        if (!potentialLock.acquire(true)) {
                            return MessageEditorData.INVALID;
                        }
                        lock = potentialLock;
                        MyLog.v(MessageEditorData.TAG, "loadCurrentDraft acquired lock");

                        DownloadStatus status = DownloadStatus.load(MyQuery.msgIdToLongColumnValue(MyDatabase.Msg.MSG_STATUS, msgId));
                        if (status.mayBeEdited()) {
                            return MessageEditorData.load(msgId);
                        } else {
                            MyLog.v(MessageEditorData.TAG, "Cannot be edited " + msgId + " state:" + status);
                            MyPreferences.putLong(MyPreferences.KEY_BEING_EDITED_MESSAGE_ID, 0);
                            return MessageEditorData.INVALID;
                        }
                    }

                    @Override
                    protected void onCancelled() {
                        lock.release();
                    }

                    @Override
                    protected void onPostExecute(MessageEditorData data) {
                        if (lock.acquired() && data.isValid()) {
                            if (editorData.isValid()) {
                                MyLog.v(MessageEditorData.TAG, "Loaded draft is not used: Editor data is valid");
                                show();
                            } else {
                                showData(data);
                            }
                        }
                        lock.release();
                    }

                    @Override
                    public String toString() {
                        return "MessageEditor#loadCurrentDraft; " + super.toString();
                    }
                }
                , true, msgId);
    }

    public void onAttach() {
		Intent intent = MyPreferences.getBoolean(MyPreferences.KEY_USE_KITKAT_MEDIA_CHOOSER, true) ?
            getIntentForKitKatMediaChooser() :
            getIntentToPickImages();
        getActivity().startActivityForResult(intent, ActivityRequestCode.ATTACH.id);
    }

    /**
     * See http://stackoverflow.com/questions/2169649/get-pick-an-image-from-androids-built-in-gallery-app-programmatically
     */
	private Intent getIntentForKitKatMediaChooser() {
		Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(UriUtils.flagsToTakePersistableUriPermission());
        return Intent.createChooser(intent,
                getActivity().getText(R.string.options_menu_attach));
	}
	
    /**
     * See http://stackoverflow.com/questions/19837358/android-kitkat-securityexception-when-trying-to-read-from-mediastore
     */
	private Intent getIntentToPickImages() {
        return new Intent(Intent.ACTION_PICK,
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                .addFlags(UriUtils.flagsToTakePersistableUriPermission());
	}

    public void showData(MessageEditorData data) {
        if (data.isValid()) {
            editorData = data;
            updateScreen();
            show();
        }
    }

    private MyBaseListActivity getActivity() {
        return mMessageList.getActivity();
    }

    public MessageEditorData getData() {
        return editorData;
    }
}
