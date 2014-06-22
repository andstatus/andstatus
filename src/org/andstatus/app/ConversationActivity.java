/* 
 * Copyright (c) 2012-2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.AsyncTask.Status;
import android.os.Bundle;
import android.os.Handler;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import net.jcip.annotations.GuardedBy;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.TimelineTypeEnum;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.MyServiceEvent;
import org.andstatus.app.service.MyServiceListener;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.service.MyServiceReceiver;
import org.andstatus.app.util.InstanceId;
import org.andstatus.app.util.MyLog;

/**
 * One selected message and, optionally, the whole conversation
 * 
 * @author yvolk@yurivolkov.com
 */
public class ConversationActivity extends Activity implements MyServiceListener, ActionableMessageList, MyActionBarContainer {

    /**
     * Id of current Message, which is sort of a "center" of the conversation view
     */
    protected long selectedMessageId = 0;
    /**
     * We use this for message requests
     */
    private volatile MyAccount ma = null;

    protected long instanceId;
    MyServiceReceiver myServiceReceiver;

    private MessageContextMenu contextMenu;
    /** 
     * Controls of the TweetEditor
     */
    private MessageEditor messageEditor;

    private boolean listPositionOnEnterSet = false;
    
    private Object messagesLock = new Object(); 
    @GuardedBy("messagesLock")
    private List<ConversationOneMessage> messages = new ArrayList<ConversationOneMessage>();

    private final Object loaderLock = new Object();
    @GuardedBy("loaderLock")
    private ContentLoader contentLoader = new ContentLoader();
    private boolean isPaused = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        MyActionBar actionBar = new MyActionBar(this, R.layout.conversation_actions);
        super.onCreate(savedInstanceState);

        if (instanceId == 0) {
            instanceId = InstanceId.next();
            MyLog.v(this, "onCreate instanceId=" + instanceId);
        } else {
            MyLog.v(this, "onCreate reuse the same instanceId=" + instanceId);
        }
        MyServiceManager.setServiceAvailable();
        myServiceReceiver = new MyServiceReceiver(this);

        final Intent intent = getIntent();
        Uri uri = intent.getData();

        selectedMessageId = MyProvider.uriToMessageId(uri);

        setContentView(R.layout.conversation);
        actionBar.attach();
        
        messageEditor = new MessageEditor(this);
        messageEditor.hide();
        contextMenu = new MessageContextMenu(this);
        
        showConversation();
    }

    protected void showConversation() {
        MyLog.v(this, "showConversation, instanceId=" + instanceId);
        synchronized (loaderLock) {
            if (selectedMessageId != 0 && contentLoader.getStatus() != Status.RUNNING) {
                if (contentLoader.getStatus() == Status.FINISHED) {
                    contentLoader = new ContentLoader();
                }
                contentLoader.execute();
            }
        }
    }

    private class ContentLoader extends AsyncTask<Void, Void, ConversationViewLoader> {
        private volatile long timeStarted = 0;
        private volatile long timeLoaded = 0;
        private volatile long timeCompleted = 0;

        @Override
        protected ConversationViewLoader doInBackground(Void... params) {
            timeStarted = System.currentTimeMillis();

            if (ma == null) {
                ma = MyContextHolder
                        .get()
                        .persistentAccounts()
                        .getAccountWhichMayBeLinkedToThisMessage(selectedMessageId, 0,
                                MyProvider.uriToAccountUserId(getIntent().getData()));
            }
            ConversationViewLoader loader = new ConversationViewLoader(
                    ConversationActivity.this, ma, selectedMessageId, contextMenu);
            if (ma != null) {
                loader.load();
            }
            timeLoaded = System.currentTimeMillis();
            return loader;
        }
        
        @Override
        protected void onPostExecute(ConversationViewLoader loader) {
            try {
                if (!isPaused) {
                    loader.createViews();
                }
                if (!isPaused) {
                    recreateTheConversationView(loader);
                }
            } catch (Exception e) {
                MyLog.i(this,"on Recreating view", e);
            }
            timeCompleted = System.currentTimeMillis();
            long timeTotal = timeCompleted - timeStarted;
            MyLog.v(this, "ContentLoader completed, " + loader.getMsgs().size() + " items, " + timeTotal + "ms total, " 
            + (timeCompleted - timeLoaded) + "ms in the foreground");
        }
    }
    
    private void recreateTheConversationView(ConversationViewLoader loader) {
        List<ConversationOneMessage> oMsgs = loader.getMsgs();
        TextView titleText = (TextView) findViewById(R.id.titleText);
        titleText.setText( oMsgs.size() > 1 ? R.string.label_conversation : R.string.message);
        ViewGroup list = (ViewGroup) findViewById(android.R.id.list);
        ScrollView scroll = (ScrollView) list.getParent();
        View currentView = null;
        if (listPositionOnEnterSet) {
            int yOld = scroll.getScrollY();
            for (int ind = 0; ind < list.getChildCount(); ind++) {
                View view = list.getChildAt(ind);
                if (view.getTop() >= yOld) {
                    currentView = view;
                    break;
                }
            }
        }
        list.removeAllViews();
        int indOfCurrentMessage = 0;
        for (int ind = 0; ind < oMsgs.size(); ind++) {
            list.addView(oMsgs.get(ind).view);
            if (oMsgs.get(ind).msgId == selectedMessageId) {
                indOfCurrentMessage = ind;
            }
        }
        synchronized(messagesLock) {
            messages = oMsgs;
        }
        if (!listPositionOnEnterSet) {
            listPositionOnEnterSet = true;
            if (indOfCurrentMessage > 0) {
                currentView = list.getChildAt(indOfCurrentMessage);
            }
        }
        scrollToView(scroll, currentView);
    }

    private void scrollToView(final ScrollView scroll, final View currentView) {
        if (currentView == null) {
            return;
        }
        new Handler().post(new Runnable() {
            @Override
            public void run() {
                scroll.scrollTo(0, currentView.getTop()); 
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (ActivityRequestCode.fromId(requestCode)) {
            case SELECT_ACCOUNT_TO_ACT_AS:
                if (resultCode == RESULT_OK) {
                    MyAccount myAccount = MyContextHolder.get().persistentAccounts().fromAccountName(data.getStringExtra(IntentExtra.EXTRA_ACCOUNT_NAME.key));
                    if (myAccount != null) {
                        contextMenu.setAccountUserIdToActAs(myAccount.getUserId());
                        contextMenu.showContextMenu();
                    }
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }
    
    @Override
    protected void onResume() {
        isPaused = false;
        super.onResume();
        myServiceReceiver.registerReceiver(this);
        MyContextHolder.get().setInForeground(true);
    }

    @Override
    protected void onPause() {
        isPaused = true;
        super.onPause();
        myServiceReceiver.unregisterReceiver(this);
        MyContextHolder.get().setInForeground(false);
    }

    @Override
    public void onReceive(CommandData commandData, MyServiceEvent event) {
        switch(commandData.getCommand()) {
            case GET_STATUS:
                if (!commandData.getResult().hasError()) {
                    showConversation();
                }
                break;
            default:
                break;
        }
        
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        contextMenu.onContextItemSelected(item);
        return super.onContextItemSelected(item);
    }

    @Override
    public Activity getActivity() {
        return this;
    }

    @Override
    public MessageEditor getMessageEditor() {
        return messageEditor;
    }

    @Override
    public long getLinkedUserIdFromCursor(int position) {
        synchronized(messagesLock) {
            if (position < 0 || position >= messages.size() ) {
                return 0;
            } else {
                return messages.get(position).linkedUserId;
            }
        }
    }

    @Override
    public long getCurrentMyAccountUserId() {
        if (ma == null) {
            return 0;
        } else {
            return ma.getUserId();
        }
    }

    @Override
    public long getSelectedUserId() {
        return 0;
    }

    @Override
    public TimelineTypeEnum getTimelineType() {
        return TimelineTypeEnum.MESSAGESTOACT;
    }

    @Override
    public boolean isTimelineCombined() {
        return true;
    }

    @Override
    public void closeAndGoBack() {
        finish();
    }

    @Override
    public boolean hasOptionsMenu() {
        return false;
    }
}
