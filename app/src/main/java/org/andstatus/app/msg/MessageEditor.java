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
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.UriUtils;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
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
import android.widget.TextView;
import android.widget.Toast;

import java.util.Date;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

/**
 * "Enter your message here" box 
 */
public class MessageEditor {
    static class MyLock {
        static final MyLock EMPTY = new MyLock(false, 0);
        static final AtomicReference<MyLock> lock = new AtomicReference<>(MyLock.EMPTY);

        final boolean isSave;
        final long msgId;
        long startedAt;

        MyLock(boolean isSave, long msgId) {
            this.isSave = isSave;
            this.msgId = msgId;
        }

        boolean isEmpty() {
            return this.equals(EMPTY);
        }

        boolean expired() {
            return isEmpty() || Math.abs(System.currentTimeMillis() - startedAt) > 60000;
        }

        boolean decidedToContinue() {
            boolean doContinue = true;
            for (int i=0; i<60; i++) {
                MyLock lockPrevious = lock.get();
                if (lock.get().expired()) {
                    this.startedAt = MyLog.uniqueCurrentTimeMS();
                    if (lock.compareAndSet(lockPrevious, this)) {
                        MyLog.v(this, "Received lock " + this + (lockPrevious.isEmpty() ? "" :
                                (". Replaced expired " + lockPrevious)));
                        break;
                    }
                } else {
                    if(lockPrevious.isSave == isSave && lockPrevious.msgId == msgId) {
                        MyLog.v(this, "The same operation in progress: " + lockPrevious);
                        doContinue = false;
                        break;
                    }
                }
                try {
                    // http://stackoverflow.com/questions/363681/generating-random-integers-in-a-range-with-java
                    if(Build.VERSION.SDK_INT >= 21) {
                        Thread.sleep(250 + ThreadLocalRandom.current().nextInt(0, 500));
                    } else {
                        Thread.sleep(500);
                    }
                } catch (InterruptedException e) {
                    MyLog.v(this, "Wait interrupted", e);
                    doContinue = false;
                    break;
                }
            }
            return doContinue;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            if(isSave) {
                builder.append("save,");
            }
            if(msgId != 0) {
                builder.append("msgId:" + msgId + ",");
            }
            builder.append("started:" + new Date(startedAt));
            return MyLog.formatKeyValue(this, builder.toString());

        }

        public void release() {
            if (!isEmpty()) {
                lock.compareAndSet(this, EMPTY);
            }
        }
    }

    private final ActionableMessageList mMessageList;
    private final android.view.ViewGroup mEditorView;

    /**
     * Text to be sent
     */
    private EditText bodyEditText;
    private final TextView mCharsLeftText;

    private MessageEditorData editorData = MessageEditorData.newEmpty(null);

    public MessageEditor(ActionableMessageList actionableMessageList) {
        mMessageList = actionableMessageList;

        ViewGroup layoutParent = (ViewGroup) getActivity().findViewById(R.id.myListParent);
        LayoutInflater inflater = LayoutInflater.from(getActivity());
        mEditorView = (ViewGroup) inflater.inflate(R.layout.message_editor, null);
        layoutParent.addView(mEditorView);
        
        mCharsLeftText = (TextView) mEditorView.findViewById(R.id.messageEditCharsLeftTextView);

        setupEditorButtons();
        setupEditText();
        hide();
    }

    private void setupEditorButtons() {
        View sendButton = mEditorView.findViewById(R.id.messageEditSendButton);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendAndHide();
            }
        });
    }

    private void setupEditText() {
        bodyEditText = (EditText) mEditorView.findViewById(R.id.messageBodyEditText);
        bodyEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                mCharsLeftText.setText(String.valueOf(editorData.getMyAccount().charactersLeftForMessage(s.toString())));
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
                            mCharsLeftText.setText(String.valueOf(editorData.getMyAccount()
                                    .charactersLeftForMessage(bodyEditText.getText().toString())));
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

    public boolean onCreateOptionsMenu(Menu menu) {
        createCreateMessageButton(menu);
        createAttachButton(menu);
        createSaveDraftButton(menu);
        createDiscardButton(menu);
        return true;
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

    private void createSaveDraftButton(Menu menu) {
        MenuItem item = menu.findItem(R.id.saveDraftButton);
        if (item != null) {
            item.setOnMenuItemClickListener(
                    new MenuItem.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            saveAndHide();
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
            mMessageList.onMessageEditorVisibilityChange(true);
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
        if (isVisible()) {
            mEditorView.setVisibility(View.GONE);
            closeSoftKeyboard();
            mMessageList.onMessageEditorVisibilityChange(false);
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
        MessageEditorData data = MessageEditorData.newEmpty(ma).setBody(textToShare);
        data.setMediaUri(mediaToShare);
        data.showAfterSaveOrLoad = true;
        editorData = data;
        saveData();
    }

    public void startEditingMessage(MessageEditorData dataIn) {
        if (!dataIn.getMyAccount().isValid()) {
            return;
        }
        dataIn.showAfterSaveOrLoad = true;
        editorData = dataIn;
        saveData();
        if (editorData.getMyAccount().getConnection().isApiSupported(ApiRoutineEnum.ACCOUNT_RATE_LIMIT_STATUS)) {
            // Start asynchronous task that will show Rate limit status
            MyServiceManager.sendForegroundCommand(new CommandData(CommandEnum.RATE_LIMIT_STATUS, editorData.getMyAccount().getAccountName()));
        }
    }

    public void updateScreen() {
        bodyEditText.setText(editorData.body);
        showIfNotEmpty(R.id.message_author,
                shouldShowAccountName() ? editorData.getMyAccount().getAccountName() : "");
        showMessageDetails();
        showIfNotEmpty(R.id.inReplyToBody, editorData.inReplyToBody);
        mCharsLeftText.setText(String.valueOf(editorData.getMyAccount()
                .charactersLeftForMessage(bodyEditText.getText().toString())));
        showAttachedImage();
    }

    private void showIfNotEmpty(int viewId, String value) {
        TextView textView = (TextView) mEditorView.findViewById(viewId);
        textView.setText(Html.fromHtml(value));
        if (TextUtils.isEmpty(value)) {
            textView.setVisibility(View.GONE);
        } else {
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
                    + " " + editorData.getImageFileSize()/1024 + "kb" +
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
        editorData.body = bodyEditText.getText().toString();
        if (!editorData.getMyAccount().isValid()) {
            discardAndHide();
        } else if (TextUtils.isEmpty(editorData.body.trim())) {
            Toast.makeText(getActivity(), R.string.cannot_send_empty_message,
                    Toast.LENGTH_SHORT).show();
        } else if (editorData.getMyAccount().charactersLeftForMessage(editorData.body) < 0) {
            Toast.makeText(getActivity(), R.string.message_is_too_long,
                    Toast.LENGTH_SHORT).show();
        } else {
			if (MyPreferences.getBoolean(MyPreferences.KEY_SENDING_MESSAGES_LOG_ENABLED, false)) {
				MyLog.setLogToFile(true);
			}
            editorData.status = DownloadStatus.SENDING;
            saveData();
        }
    }

    private void saveAndHide() {
        editorData.hideAfterSave = true;
        saveState();
	}

    private void discardAndHide() {
        editorData.status = DownloadStatus.DELETED;
        saveData();
    }

    public void saveState() {
        if (bodyEditText != null) {
            editorData.body = bodyEditText.getText().toString();
            saveData();
        }
    }

    private void saveData() {
        if (editorData.isEmpty()) {
            dataSavedCallback(editorData);
        } else {
            MyLog.v(MessageEditorData.TAG, "Save requested for " + editorData);
            new MessageEditorSaver().execute(this);
        }
    }

    public void loadState(long msgId) {
        saveState();
        MyLog.v(MessageEditorData.TAG, "loadState started" + (msgId == 0 ? "" : ", msgId=" + msgId));
        new AsyncTask<Long, Void, MessageEditorData>() {
            volatile MessageEditor.MyLock lock = MessageEditor.MyLock.EMPTY;

            @Override
            protected MessageEditorData doInBackground(Long... params) {
                long msgId = params[0];
                MyLog.v(MessageEditorData.TAG, "Load requested for " + msgId);
                MessageEditor.MyLock potentialLock = new MessageEditor.MyLock(false, msgId);
                if (!potentialLock.decidedToContinue()) {
                    return MessageEditorData.newEmpty(null);
                }
                lock = potentialLock;
                MyLog.v(MessageEditorData.TAG, "loadState passed wait for save");

                if (msgId == 0) {
                    msgId = MyPreferences.getLong(MyPreferences.KEY_BEING_EDITED_DRAFT_MESSAGE_ID);
                }
                if (msgId != 0) {
                    DownloadStatus status = DownloadStatus.load(MyQuery.msgIdToLongColumnValue(MyDatabase.Msg.MSG_STATUS, msgId));
                    if (status != DownloadStatus.DRAFT) {
                        msgId = 0;
                    }
                }
                MyPreferences.putLong(MyPreferences.KEY_BEING_EDITED_DRAFT_MESSAGE_ID, msgId);
                return MessageEditorData.load(msgId);
            }

            @Override
            protected void onCancelled() {
                lock.release();
            }

            @Override
            protected void onPostExecute(MessageEditorData data) {
                if (!lock.isEmpty()) {
                    data.showAfterSaveOrLoad = !data.isEmpty();
                    dataLoadedCallback(data);
                    lock.release();
                }
            }
        }.execute(msgId);
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

    public void setMedia(Uri mediaUri) {
        editorData.setMediaUri(mediaUri);
        saveData();
    }

    public void dataSavedCallback(MessageEditorData data) {
        dataLoadedCallback(data);
    }

    public void dataLoadedCallback(MessageEditorData data) {
        editorData = data;
        updateScreen();
        if (editorData.status == DownloadStatus.DRAFT) {
            if (editorData.showAfterSaveOrLoad) {
                show();
            } else if (editorData.hideAfterSave || editorData.isEmpty()) {
                hide();
            }
        } else {
            hide();
        }
        editorData.showAfterSaveOrLoad = false;
        editorData.hideAfterSave = false;
    }

    private MyBaseListActivity getActivity() {
        return mMessageList.getActivity();
    }

    public MessageEditorData getData() {
        return editorData;
    }
}
