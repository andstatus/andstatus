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
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.AsyncTask.Status;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ListView;

import net.jcip.annotations.GuardedBy;

import org.andstatus.app.ConversationLoader.progressPublisher;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.TimelineTypeEnum;
import org.andstatus.app.data.ParsedUri;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.MyServiceEvent;
import org.andstatus.app.service.MyServiceListener;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.service.MyServiceReceiver;
import org.andstatus.app.service.QueueViewer;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.InstanceId;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.UriUtils;

/**
 * One selected message and, optionally, the whole conversation
 * 
 * @author yvolk@yurivolkov.com
 */
public class ConversationActivity extends Activity implements MyServiceListener, ActionableMessageList {
    private static final String ACTIVITY_PERSISTENCE_NAME = ConversationActivity.class.getSimpleName();

    /**
     * Id of current Message, which is sort of a "center" of the conversation view
     */
    protected long selectedMessageId = 0;
    /**
     * We use this for message requests
     */
    private volatile MyAccount ma = MyAccount.getEmpty(MyContextHolder.get(), "");

    protected long instanceId;
    MyServiceReceiver myServiceReceiver;

    private MessageContextMenu contextMenu;
    /** 
     * Controls of the TweetEditor
     */
    private MessageEditor mMessageEditor;

    private final Object loaderLock = new Object();
    @GuardedBy("loaderLock")
    private ContentLoader<ConversationViewItem> completedLoader = new ContentLoader<ConversationViewItem>(
            ConversationViewItem.class);
    @GuardedBy("loaderLock")
    private ContentLoader<ConversationViewItem> workingLoader = completedLoader;
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

        selectedMessageId = ParsedUri.fromUri(getIntent().getData()).getMessageId();

        MyPreferences.setThemedContentView(this, R.layout.conversation);
        
        mMessageEditor = new MessageEditor(this);
        mMessageEditor.hide();
        contextMenu = new MessageContextMenu(this);
        
        restoreActivityState();
        mMessageEditor.updateScreen();
    }

    private boolean restoreActivityState() {
        SharedPreferences activityState = MyPreferences.getSharedPreferences(ACTIVITY_PERSISTENCE_NAME);
        if (activityState != null) {
            mMessageEditor.loadState(activityState);
        }
        return mMessageEditor.isStateLoaded();
    }
    
    protected void showConversation() {
        MyLog.v(this, "showConversation, instanceId=" + instanceId);
        synchronized (loaderLock) {
            if (selectedMessageId != 0 && workingLoader.getStatus() != Status.RUNNING) {
                /* On passing the same info twice (Generic parameter + Class) read here:
                 * http://codereview.stackexchange.com/questions/51084/generic-callback-object-but-i-need-the-type-parameter-inside-methods
                 */
                workingLoader = new ContentLoader<ConversationViewItem>(ConversationViewItem.class);
                workingLoader.execute();
            }
        }
    }

    private class ContentLoader<T extends ConversationViewItem> extends AsyncTask<Void, String, ConversationLoader<T>> implements progressPublisher {
        private volatile long timeStarted = 0;
        private volatile long timeLoaded = 0;
        private volatile long timeCompleted = 0;
        private final Class<T> mTClass;
        private List<T> mMsgs = new ArrayList<T>();

        public ContentLoader(Class<T> tClass) {
            mTClass = tClass;
        }

        List<T> getMsgs() {
            return mMsgs;
        }

        @Override
        protected ConversationLoader<T> doInBackground(Void... params) {
            timeStarted = System.currentTimeMillis();
            publishProgress("...");

            if (!ma.isValid()) {
                ma = MyContextHolder
                        .get()
                        .persistentAccounts()
                        .getAccountWhichMayBeLinkedToThisMessage(selectedMessageId, 0,
                                ParsedUri.fromUri(getIntent().getData()).getAccountUserId());
            }
            publishProgress("");
            ConversationLoader<T> loader = new ConversationLoader<T>(mTClass,
                    ConversationActivity.this, ma, selectedMessageId);
            if (ma.isValid()) {
                loader.allowLoadingFromInternet();
                loader.load(this);
            }
            timeLoaded = System.currentTimeMillis();
            return loader;
        }

        @Override
        public void publish(String progress) {
            publishProgress(progress);
        }
        
        @Override
        protected void onProgressUpdate(String... values) {
            updateTitle(values[0]);
        }

        @Override
        protected void onPostExecute(ConversationLoader<T> loader) {
            try {
                if (!isPaused) {
                    mMsgs = loader.getMsgs();
                    onContentLoaderCompleted();
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

    private void onContentLoaderCompleted() {
        ContentLoader<ConversationViewItem> loader = updateCompletedLoader();
        updateTitle("");
        ListView list = (ListView) findViewById(android.R.id.list);
        long itemIdOfListPosition = selectedMessageId;
        if (list.getChildCount() > 1) {
            itemIdOfListPosition = list.getAdapter().getItemId(list.getFirstVisiblePosition());
        }
        int firstListPosition = -1;
        for (int ind = 0; ind < loader.getMsgs().size(); ind++) {
            if (loader.getMsgs().get(ind).getMsgId() == itemIdOfListPosition) {
                firstListPosition = ind;
            }
        }
        list.setAdapter(new ConversationViewAdapter(contextMenu, selectedMessageId, loader.getMsgs()));
        if (firstListPosition >= 0) {
            list.setSelectionFromTop(firstListPosition, 0);
        }
    }

    private ContentLoader<ConversationViewItem> updateCompletedLoader() {
        synchronized(loaderLock) {
            completedLoader = workingLoader;
            return workingLoader;
        }
    }
    
    void updateTitle(String progress) {
        StringBuilder title = new StringBuilder(getText(getNumberOfMessages() > 1 ? R.string.label_conversation
                : R.string.message));
        if (ma.isValid()) {
            I18n.appendWithSpace(title, "/ " + ma.getOrigin().getName());
        } else {
            I18n.appendWithSpace(title, "/ ? (" + selectedMessageId + ")");
        }
        if (!TextUtils.isEmpty(progress)) {
            I18n.appendWithSpace(title, progress);
        }
        this.getActionBar().setTitle(title.toString());
    }

    private int getNumberOfMessages() {
        synchronized(loaderLock) {
            return completedLoader.getMsgs().size();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (ActivityRequestCode.fromId(requestCode)) {
            case SELECT_ACCOUNT_TO_ACT_AS:
                if (resultCode == RESULT_OK) {
                    MyAccount myAccount = MyContextHolder.get().persistentAccounts().fromAccountName(data.getStringExtra(IntentExtra.EXTRA_ACCOUNT_NAME.key));
                    if (myAccount.isValid()) {
                        contextMenu.setAccountUserIdToActAs(myAccount.getUserId());
                        contextMenu.showContextMenu();
                    }
                }
                break;
            case ATTACH:
                Uri uri = data != null ? UriUtils.notNull(data.getData()) : null;
                if (resultCode == RESULT_OK && !UriUtils.isEmpty(uri) 
                        && mMessageEditor.isVisible()) {
                    UriUtils.takePersistableUriPermission(getActivity(), uri, data.getFlags());
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
        if (getNumberOfMessages() == 0) {
            showConversation();
        }
    }

    @Override
    protected void onPause() {
        isPaused = true;
        super.onPause();
        myServiceReceiver.unregisterReceiver(this);
        saveActivityState();
        MyContextHolder.get().setInForeground(false);
    }

    protected void saveActivityState() {
        SharedPreferences.Editor outState = MyPreferences.getSharedPreferences(ACTIVITY_PERSISTENCE_NAME).edit();
        mMessageEditor.saveState(outState);
        outState.commit();
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
            case R.id.commands_queue_id:
                startActivity(new Intent(getActivity(), QueueViewer.class));
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
    public void onMessageEditorVisibilityChange(boolean isVisible) {
        invalidateOptionsMenu();
    }
    
    @Override
    public long getLinkedUserIdFromCursor(int position) {
        synchronized(loaderLock) {
            if (position < 0 || position >= getNumberOfMessages() ) {
                return 0;
            } else {
                return completedLoader.getMsgs().get(position).mLinkedUserId;
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
