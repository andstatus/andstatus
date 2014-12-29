/* 
 * Copyright (c) 2012-2014 yvolk (Yuri Volkov), http://yurivolkov.com
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
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ListView;

import net.jcip.annotations.GuardedBy;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.TimelineTypeEnum;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.MyServiceEvent;
import org.andstatus.app.service.MyServiceListener;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.service.MyServiceReceiver;
import org.andstatus.app.util.InstanceId;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.UriUtils;

/**
 * One selected message and, optionally, the whole conversation
 * 
 * @author yvolk@yurivolkov.com
 */
public class ConversationActivity extends Activity implements MyServiceListener, ActionableMessageList {

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
    private MessageEditor mMessageEditor;

    private Object messagesLock = new Object(); 
    @GuardedBy("messagesLock")
    private List<ConversationOneMessage> messages = new ArrayList<ConversationOneMessage>();

    private final Object loaderLock = new Object();
    @GuardedBy("loaderLock")
    private ContentLoader contentLoader = new ContentLoader();
    private boolean isPaused = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
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

        MyPreferences.setThemedContentView(this, R.layout.conversation);
        
        mMessageEditor = new MessageEditor(this);
        mMessageEditor.hide();
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
        CharSequence title = getText(oMsgs.size() > 1 ? R.string.label_conversation : R.string.message) 
                + ( MyPreferences.showOrigin() && ma != null ? " / " + ma.getOriginName() : "");
        this.getActionBar().setTitle(title);
        ListView list = (ListView) findViewById(android.R.id.list);

        long itemIdOfListPosition = selectedMessageId;
        if (list.getChildCount() > 1) {
            itemIdOfListPosition = list.getAdapter().getItemId(list.getFirstVisiblePosition());
        }
        int firstListPosition = -1;
        for (int ind = 0; ind < oMsgs.size(); ind++) {
            if (oMsgs.get(ind).mMsgId == itemIdOfListPosition) {
                firstListPosition = ind;
            }
        }
        synchronized(messagesLock) {
            messages = oMsgs;
        }
        list.setAdapter(new ConversationViewAdapter(contextMenu, selectedMessageId, oMsgs));
        if (firstListPosition >= 0) {
            list.setSelectionFromTop(firstListPosition, 0);
        }
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
            case ATTACH:
                Uri uri = data != null ? UriUtils.notNull(data.getData()) : null;
                if (resultCode == RESULT_OK && !UriUtils.isEmpty(uri) 
                        && mMessageEditor.isVisible()) {
                    mMessageEditor.setMedia(uri);
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
        if (event == MyServiceEvent.AFTER_EXECUTING_COMMAND) {
            switch(commandData.getCommand()) {
                case GET_STATUS:
                case UPDATE_STATUS:
                case CREATE_FAVORITE:
                case DESTROY_FAVORITE:
                case REBLOG:
                case DESTROY_REBLOG:
                case DESTROY_STATUS:
                case FETCH_ATTACHMENT:
                case FETCH_AVATAR:
                    if (!commandData.getResult().hasError()) {
                        showConversation();
                    }
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        contextMenu.onContextItemSelected(item);
        return super.onContextItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.conversation, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (mMessageEditor != null) {
            mMessageEditor.onPrepareOptionsMenu(menu);
        }
        return super.onPrepareOptionsMenu(menu);
    }
    

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.reload_menu_item:
                showConversation();
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    public Activity getActivity() {
        return this;
    }

    @Override
    public MessageEditor getMessageEditor() {
        return mMessageEditor;
    }

    @Override
    public long getLinkedUserIdFromCursor(int position) {
        synchronized(messagesLock) {
            if (position < 0 || position >= messages.size() ) {
                return 0;
            } else {
                return messages.get(position).mLinkedUserId;
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
}
