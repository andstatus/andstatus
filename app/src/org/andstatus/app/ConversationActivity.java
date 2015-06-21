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
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ListView;

import net.jcip.annotations.GuardedBy;

import org.andstatus.app.LoadableListActivity.ProgressPublisher;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.TimelineType;
import org.andstatus.app.data.ParsedUri;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.MyServiceEvent;
import org.andstatus.app.service.MyServiceEventsListener;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.service.MyServiceEventsReceiver;
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
public class ConversationActivity extends Activity implements MyServiceEventsListener, ActionableMessageList {

    /**
     * Id of current Message, which is sort of a "center" of the conversation view
     */
    protected long mSelectedMessageId = 0;
    /**
     * We use this for message requests
     */
    private volatile MyAccount ma = MyAccount.getEmpty(MyContextHolder.get(), "");

    protected long mInstanceId;
    MyServiceEventsReceiver myServiceReceiver;

    private MessageContextMenu mContextMenu;
    /** 
     * Controls of the TweetEditor
     */
    private MessageEditor mMessageEditor;

    private final Object loaderLock = new Object();
    @GuardedBy("loaderLock")
    private ContentLoader<ConversationViewItem> mCompletedLoader = new ContentLoader<ConversationViewItem>(
            ConversationViewItem.class);
    @GuardedBy("loaderLock")
    private ContentLoader<ConversationViewItem> mWorkingLoader = mCompletedLoader;
    private boolean mIsPaused = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (mInstanceId == 0) {
            mInstanceId = InstanceId.next();
            MyLog.v(this, "onCreate instanceId=" + mInstanceId);
        } else {
            MyLog.v(this, "onCreate reuse the same instanceId=" + mInstanceId);
        }
        MyServiceManager.setServiceAvailable();
        myServiceReceiver = new MyServiceEventsReceiver(this);

        mSelectedMessageId = ParsedUri.fromUri(getIntent().getData()).getMessageId();

        MyPreferences.setThemedContentView(this, R.layout.conversation);
        
        mMessageEditor = new MessageEditor(this);
        mMessageEditor.hide();
        mContextMenu = new MessageContextMenu(this);
        
        restoreActivityState();
        mMessageEditor.updateScreen();
    }

    private void restoreActivityState() {
        mMessageEditor.loadState();
    }
    
    protected void showConversation() {
        MyLog.v(this, "showConversation, instanceId=" + mInstanceId);
        synchronized (loaderLock) {
            if (mSelectedMessageId != 0 && mWorkingLoader.getStatus() != Status.RUNNING) {
                /* On passing the same info twice (Generic parameter + Class) read here:
                 * http://codereview.stackexchange.com/questions/51084/generic-callback-object-but-i-need-the-type-parameter-inside-methods
                 */
                mWorkingLoader = new ContentLoader<ConversationViewItem>(ConversationViewItem.class);
                mWorkingLoader.execute();
            }
        }
    }

    private class ContentLoader<T extends ConversationViewItem> extends AsyncTask<Void, String, ConversationLoader<T>> implements ProgressPublisher {
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

            if (!ma.isValidAndSucceeded()) {
                ma = MyContextHolder
                        .get()
                        .persistentAccounts()
                        .getAccountForThisMessage(mSelectedMessageId, 
                                ParsedUri.fromUri(getIntent().getData()).getAccountUserId(),
                                0,
                                true);
            }
            publishProgress("");
            ConversationLoader<T> loader = new ConversationLoader<T>(mTClass,
                    ConversationActivity.this, ma, mSelectedMessageId);
            if (ma.isValidAndSucceeded()) {
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
                if (!mIsPaused) {
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
        long itemIdOfListPosition = mSelectedMessageId;
        if (list.getChildCount() > 1) {
            itemIdOfListPosition = list.getAdapter().getItemId(list.getFirstVisiblePosition());
        }
        int firstListPosition = -1;
        for (int ind = 0; ind < loader.getMsgs().size(); ind++) {
            if (loader.getMsgs().get(ind).getMsgId() == itemIdOfListPosition) {
                firstListPosition = ind;
            }
        }
        list.setAdapter(new ConversationViewAdapter(mContextMenu, mSelectedMessageId, loader.getMsgs()));
        if (firstListPosition >= 0) {
            list.setSelectionFromTop(firstListPosition, 0);
        }
    }

    private ContentLoader<ConversationViewItem> updateCompletedLoader() {
        synchronized(loaderLock) {
            mCompletedLoader = mWorkingLoader;
            return mWorkingLoader;
        }
    }
    
    void updateTitle(String progress) {
        StringBuilder title = new StringBuilder(getText(getNumberOfMessages() > 1 ? R.string.label_conversation
                : R.string.message));
        if (ma.isValid()) {
            I18n.appendWithSpace(title, "/ " + ma.getOrigin().getName());
        } else {
            I18n.appendWithSpace(title, "/ ? (" + mSelectedMessageId + ")");
        }
        if (!TextUtils.isEmpty(progress)) {
            I18n.appendWithSpace(title, progress);
        }
        this.getActionBar().setTitle(title.toString());
    }

    private int getNumberOfMessages() {
        synchronized(loaderLock) {
            return mCompletedLoader.getMsgs().size();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (ActivityRequestCode.fromId(requestCode)) {
            case SELECT_ACCOUNT_TO_ACT_AS:
                if (resultCode == RESULT_OK) {
                    MyAccount myAccount = MyContextHolder.get().persistentAccounts().fromAccountName(data.getStringExtra(IntentExtra.ACCOUNT_NAME.key));
                    if (myAccount.isValid()) {
                        mContextMenu.setAccountUserIdToActAs(myAccount.getUserId());
                        mContextMenu.showContextMenu();
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
        mIsPaused = false;
        super.onResume();
        myServiceReceiver.registerReceiver(this);
        MyContextHolder.get().setInForeground(true);
        if (getNumberOfMessages() == 0) {
            showConversation();
        }
    }

    @Override
    protected void onPause() {
        mIsPaused = true;
        super.onPause();
        myServiceReceiver.unregisterReceiver(this);
        saveActivityState();
        MyContextHolder.get().setInForeground(false);
    }

    protected void saveActivityState() {
        mMessageEditor.saveState();
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
        mContextMenu.onContextItemSelected(item);
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
                return mCompletedLoader.getMsgs().get(position).mLinkedUserId;
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
    public TimelineType getTimelineType() {
        return TimelineType.MESSAGESTOACT;
    }

    @Override
    public boolean isTimelineCombined() {
        return true;
    }
}
