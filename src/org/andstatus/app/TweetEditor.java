/**
 * Copyright (C) 2011 yvolk (Yuri Volkov), http://yurivolkov.com
 * Copyright (C) 2008 Torgny Bjers
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
package org.andstatus.app;

import org.andstatus.app.MyService.CommandData;
import org.andstatus.app.MyService.CommandEnum;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/**
 * "Enter your tweet here" box 
 */
class TweetEditor {
    private TimelineActivity mActivity;
    private android.view.ViewGroup mEditor;

    // Text limits
    private int mCurrentChars = 0;
    private int mLimitChars = 140;
    
    private Button mSendButton;
    private EditText mEditText;
    private TextView mCharsLeftText;

    /**
     * Id of the Tweet to which we are replying
     */
    private long mReplyId = 0;
    
    public TweetEditor(TimelineActivity activity) {
        mActivity = activity;
        mEditor = (android.view.ViewGroup) activity.findViewById(R.id.tweetlist_editor);

        mSendButton = (Button) activity.findViewById(R.id.messageEditSendButton);
        mEditText = (EditText) activity.findViewById(R.id.edtTweetInput);
        mCharsLeftText = (TextView) activity.findViewById(R.id.messageEditCharsLeftTextView);
        
        mSendButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                updateStatus();
            }
        });

        mEditText.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                mCurrentChars = s.length();
                mCharsLeftText.setText(String.valueOf(mLimitChars - mCurrentChars));
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });

        mEditText.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    mCurrentChars = mEditText.length();
                    if (mCurrentChars == 0) {
                        mReplyId = 0;
                    }
                    switch (keyCode) {
                        case KeyEvent.KEYCODE_DPAD_CENTER:
                            updateStatus();
                            return true;
                        case KeyEvent.KEYCODE_ENTER:
                            if (event.isAltPressed()) {
                                mEditText.append("\n");
                                return true;
                            }
                        default:
                            if (keyCode != KeyEvent.KEYCODE_DEL && mCurrentChars > mLimitChars) {
                                return true;
                            }
                            mCharsLeftText.setText(String.valueOf(mLimitChars - mCurrentChars));
                            break;
                    }
                }
                return false;
            }
        });

        mEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (event != null) {
                    if (event.isAltPressed()) {
                        return false;
                    }
                }
                updateStatus();
                return true;
            }
        });
    }
    
    /**
     * @return new state of visibility
     */
    public boolean toggleVisibility() {
        boolean isVisibleNew = !isVisible();
        if (isVisibleNew) {
            show();
        } else {
            hide();
        }
        return isVisibleNew;
    }

    public void show() {
        mCurrentChars = mEditText.length();
        mCharsLeftText.setText(String.valueOf(mLimitChars - mCurrentChars));

        mEditor.setVisibility(View.VISIBLE);
        
        mEditText.requestFocus();
        /* do we need this instead?
        if (mActivity.hasHardwareKeyboard()) {
            mEditText.requestFocus();
        }
        */
        
    }
    
    public void hide() {
        mEditor.setVisibility(View.GONE);
    }
    
    public boolean isVisible() {
        return (mEditor.getVisibility() == View.VISIBLE);
    }
    
    public void startEditing(String textInitial, long replyId) {
        mReplyId = replyId;
        mEditText.setText("");
        mEditText.append(textInitial, 0, textInitial.length());
        
        show();
    }
    

    /**
     * Handles threaded sending of the message, typed in the mEditText text box.
     * Queued message sending is supported (if initial sending
     * failed for some reason). 
     */
    private void updateStatus() {
        String status = mEditText.getText().toString();
        if (TextUtils.isEmpty(status.trim())) {
            Toast.makeText(mActivity, R.string.cannot_send_empty_message,
                    Toast.LENGTH_SHORT).show();
        } else {
            CommandData commandData = new CommandData(CommandEnum.UPDATE_STATUS);
            commandData.bundle.putString(MyService.EXTRA_STATUS, status);
            commandData.bundle.putLong(MyService.EXTRA_INREPLYTOID, mReplyId);
            mActivity.sendCommand(commandData);
            closeSoftKeyboard();

            // Let's assume that everything will be Ok
            // so we may clear the text box with the sent tweet
            // text...
            mReplyId = 0;
            mEditText.setText("");

            hide();
        }
    }

    /**
     * Close the on-screen keyboard.
     */
    private void closeSoftKeyboard() {
        InputMethodManager inputMethodManager = (InputMethodManager) mActivity.getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(mEditText.getWindowToken(), 0);
    }
    
    public void saveState(Bundle outState) {
        if (outState != null) {
            outState.putLong(MyService.EXTRA_INREPLYTOID, mReplyId);
        }
    }
    
    public void loadState(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(MyService.EXTRA_INREPLYTOID)) {
                mReplyId = savedInstanceState.getLong(MyService.EXTRA_INREPLYTOID);
            }
        }
    }
}
