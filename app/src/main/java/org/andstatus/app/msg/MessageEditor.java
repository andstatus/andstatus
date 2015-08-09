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
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.TimelineType;
import org.andstatus.app.net.social.Connection.ApiRoutineEnum;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyServiceManager;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.text.Editable;
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
import android.widget.TextView;
import android.widget.Toast;

import org.andstatus.app.util.*;

/**
 * "Enter your message here" box 
 */
public class MessageEditor {
    private static final String PERSISTENCE_NAME = MessageEditor.class.getSimpleName();

    private ActionableMessageList mMessageList;
    private android.view.ViewGroup mEditorView;

    /**
     * Text to be sent
     */
    private EditText mEditText;
    private TextView mCharsLeftText;

    /**
     * Information about the message we are editing
     */
    private TextView mDetails;

    private MessageEditorData dataCurrent = new MessageEditorData();
    private MessageEditorData dataLoaded = new MessageEditorData();
    
    public MessageEditor(ActionableMessageList actionableMessageList) {
        mMessageList = actionableMessageList;

        ViewGroup layoutParent = (ViewGroup) getActivity().findViewById(R.id.myListParent);
        LayoutInflater inflater = LayoutInflater.from(getActivity());
        mEditorView = (ViewGroup) inflater.inflate(R.layout.message_editor, null);
        layoutParent.addView(mEditorView);
        
        mCharsLeftText = (TextView) mEditorView.findViewById(R.id.messageEditCharsLeftTextView);
        mDetails = (TextView) mEditorView.findViewById(R.id.messageEditDetails);
        
        setupButtons();
        setupEditText();
        hide();
    }

    private void setupButtons() {
        View sendButton = mEditorView.findViewById(R.id.messageEditSendButton);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMessageAndCloseEditor();
            }
        });
    }

    public void onPrepareOptionsMenu(Menu menu) {
        prepareCreateMessageButton(menu);
        prepareHideMessageButton(menu);
        prepareAttachButton(menu);
    }
    
    private void prepareCreateMessageButton(Menu menu) {
        MenuItem item = menu.findItem(R.id.createMessageButton);
        if (item == null) {
            return;
        }
        item.setOnMenuItemClickListener(
                new MenuItem.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        MyAccount accountForButton = accountforCreateMessageButton();
                        if (accountForButton != null) {
                            startEditingMessage(new MessageEditorData(accountForButton));
                        }
                        return false;
                    }
                });
        MyAccount accountForButton = accountforCreateMessageButton();
        item.setVisible(!isVisible()
                && accountForButton.isValidAndSucceeded() 
                && mMessageList.getTimelineType() != TimelineType.DIRECT
                && mMessageList.getTimelineType() != TimelineType.MESSAGESTOACT);
    }

    private MyAccount accountforCreateMessageButton() {
        if (isVisible()) {
            return dataCurrent.getMyAccount();
        } else {
            return MyContextHolder.get().persistentAccounts().getCurrentAccount();
        }
    }
    
    private void prepareHideMessageButton(Menu menu) {
        MenuItem item = menu.findItem(R.id.hideMessageButton);
        if (item == null) {
            return;
        }
        item.setOnMenuItemClickListener(
                new MenuItem.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        clearAndHide();
                        return false;
                    }
                });
        item.setVisible(isVisible());
    }

    private void prepareAttachButton(Menu menu) {
        MenuItem item = menu.findItem(R.id.attach_menu_id);
        if (item == null) {
            return;
        }
        item.setOnMenuItemClickListener(
                new MenuItem.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        onAttach();
                        return false;
                    }
                });
        boolean enableAttach = isVisible()
                && MyPreferences.showAttachedImages()
                && (dataCurrent.recipientId == 0 || dataCurrent.getMyAccount().getOrigin().getOriginType()
                        .allowAttachmentForDirectMessage());
        item.setEnabled(enableAttach);
        item.setVisible(enableAttach);
    }
    
    private void setupEditText() {
        mEditText = (EditText) mEditorView.findViewById(R.id.messageBodyEditText);
        mEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                mCharsLeftText.setText(String.valueOf(dataCurrent.getMyAccount().charactersLeftForMessage(s.toString())));
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

        mEditText.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    switch (keyCode) {
                        case KeyEvent.KEYCODE_DPAD_CENTER:
                            sendMessageAndCloseEditor();
                            return true;
                        default:
                            mCharsLeftText.setText(String.valueOf(dataCurrent.getMyAccount()
                                    .charactersLeftForMessage(mEditText.getText().toString())));
                            break;
                    }
                }
                return false;
            }
        });

        mEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (event != null && (event.isAltPressed() || 
                        !MyPreferences.getBoolean(MyPreferences.KEY_ENTER_SENDS_MESSAGE, true))) {
                    return false;
                }
                sendMessageAndCloseEditor();
                return true;
            }
        });
    }

    public void show() {
        mCharsLeftText.setText(String.valueOf(dataCurrent.getMyAccount()
                .charactersLeftForMessage(mEditText.getText().toString())));
        
        if (!isVisible()) {
            mEditorView.setVisibility(View.VISIBLE);
            mEditText.requestFocus();
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
        inputMethodManager.toggleSoftInputFromWindow(mEditText.getWindowToken(), InputMethodManager.SHOW_FORCED, 0);        
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
        inputMethodManager.hideSoftInputFromWindow(mEditText.getWindowToken(), 0);
    }
    
    public boolean isVisible() {
        return mEditorView.getVisibility() == View.VISIBLE;
    }
    
    public void startEditingMessage(MessageEditorData dataIn) {
        if (!dataIn.getMyAccount().isValid()) {
            return;
        }
        if (dataCurrent.isEmpty() || !dataCurrent.sameContext(dataIn)) {
            dataCurrent = dataIn;
            mEditText.setText(dataCurrent.messageText);
            showMessageDetails();
        }
        
        if (dataCurrent.getMyAccount().getConnection().isApiSupported(ApiRoutineEnum.ACCOUNT_RATE_LIMIT_STATUS)) {
            // Start asynchronous task that will show Rate limit status
            MyServiceManager.sendForegroundCommand(new CommandData(CommandEnum.RATE_LIMIT_STATUS, dataCurrent.getMyAccount().getAccountName()));
        }
        
        show();
    }

    private void showMessageDetails() {
        String messageDetails = showAccountName() ? dataCurrent.getMyAccount().getAccountName() : "";
        if (dataCurrent.recipientId == 0) {
            if (dataCurrent.inReplyToId != 0) {
                String replyToName = MyQuery.msgIdToUsername(MyDatabase.Msg.AUTHOR_ID, dataCurrent.inReplyToId, MyPreferences.userInTimeline());
                messageDetails += " " + String.format(
                        MyContextHolder.get().context().getText(R.string.message_source_in_reply_to).toString(), 
                        replyToName);
            }
        } else {
            String recipientName = MyQuery.userIdToWebfingerId(dataCurrent.recipientId);
            if (!TextUtils.isEmpty(recipientName)) {
                messageDetails += " " + String.format(
                        MyContextHolder.get().context().getText(R.string.message_source_to).toString(), 
                        recipientName);
            }
        }
        if (!UriUtils.isEmpty(dataCurrent.mediaUri)) {
            messageDetails += " (" + MyContextHolder.get().context().getText(R.string.label_with_media).toString() + ")"; 
        }
        mDetails.setText(messageDetails);
        if (TextUtils.isEmpty(messageDetails)) {
            mDetails.setVisibility(View.GONE);
        } else {
            mDetails.setVisibility(View.VISIBLE);
        }
    }
    
    private boolean showAccountName() {
        boolean show = mMessageList.isTimelineCombined() 
                || mMessageList.getTimelineType() == TimelineType.USER;
        if (!show) {
            show = dataCurrent.getMyAccount().getUserId() != mMessageList.getCurrentMyAccountUserId();
        }
        return show;
    }
    
    private void sendMessageAndCloseEditor() {
        String status = mEditText.getText().toString();
        if (!dataCurrent.getMyAccount().isValid()) {
            clearAndHide();
        } else if (TextUtils.isEmpty(status.trim())) {
            Toast.makeText(getActivity(), R.string.cannot_send_empty_message,
                    Toast.LENGTH_SHORT).show();
        } else if (dataCurrent.getMyAccount().charactersLeftForMessage(status) < 0) {
            Toast.makeText(getActivity(), R.string.message_is_too_long,
                    Toast.LENGTH_SHORT).show();
        } else {
			if (MyPreferences.getBoolean(MyPreferences.KEY_SENDING_MESSAGES_LOG_ENABLED, false)) {
				MyLog.setLogToFile(true);
			}
            CommandData commandData = CommandData.updateStatus(dataCurrent.getMyAccount().getAccountName(), status, dataCurrent.inReplyToId, dataCurrent.recipientId, dataCurrent.mediaUri);
            MyServiceManager.sendForegroundCommand(commandData);
            clearAndHide();
        }
    }

    private void clearAndHide() {
        clearEditor();
        hide();
    }
    
	public void clearEditor() {
        if (mEditText != null) {
            mEditText.setText("");
        }
	    dataCurrent = new MessageEditorData(messageListAccount());
	}

    private MyAccount messageListAccount() {
        return MyContextHolder.get().persistentAccounts().fromUserId(
                mMessageList.getCurrentMyAccountUserId());
    }

	private MyBaseListActivity getActivity() {
		return mMessageList.getActivity();
	}
    
    public void saveState() {
        SharedPreferences.Editor outState = MyPreferences.getSharedPreferences(PERSISTENCE_NAME).edit();
        if (outState != null && mEditText != null) {
            dataCurrent.messageText = mEditText.getText().toString();
            dataCurrent.save(outState);
        }
        outState.commit();
    }
    
    public void loadState() {
        SharedPreferences storedState = MyPreferences.getSharedPreferences(PERSISTENCE_NAME);
        if (storedState != null) {
            dataLoaded = MessageEditorData.load(storedState);
        }
    }
    
    public boolean isStateLoaded() {
        return !dataLoaded.isEmpty();
    }
    
    public void continueEditingLoadedState() {
        if (isStateLoaded()) {
            startEditingMessage(dataLoaded);
            dataLoaded = new MessageEditorData(messageListAccount());
        }
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
        dataCurrent.mediaUri = UriUtils.notNull(mediaUri);
        showMessageDetails();
    }

    public MessageEditorData getData() {
        return dataCurrent;
    }

    public void updateScreen() {
        if (isStateLoaded()) {
            continueEditingLoadedState();
        }
    }
}
