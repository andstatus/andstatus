/* 
 * Copyright (c) 2011-2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.LoaderManager.LoaderCallbacks;
import android.app.NotificationManager;
import android.app.SearchManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.SearchRecentSuggestions;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CursorAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.andstatus.app.account.AccountSelector;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.account.MyAccount.CredentialsVerificationStatus;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferenceActivity;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.TimelineSearchSuggestionsProvider;
import org.andstatus.app.data.TimelineSql;
import org.andstatus.app.data.TimelineTypeEnum;
import org.andstatus.app.data.TimelineViewBinder;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyServiceEvent;
import org.andstatus.app.service.MyServiceListener;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.service.MyServiceReceiver;
import org.andstatus.app.util.InstanceId;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.UriUtils;
import org.andstatus.app.widget.MySimpleCursorAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yvolk@yurivolkov.com, torgny.bjers
 */
public class TimelineActivity extends ListActivity implements MyServiceListener, OnScrollListener, OnItemClickListener, ActionableMessageList, LoaderCallbacks<Cursor> {
    private static final int DIALOG_ID_TIMELINE_TYPE = 9;

    /**
     * Visibility of the layout indicates whether Messages are being loaded into the list (asynchronously...)
     * The layout appears at the bottom of the list of messages 
     * when new items are being loaded into the list 
     */
    private LinearLayout mLoadingLayout;

    /** Parameters of currently shown Timeline */
    private TimelineListParameters mListParameters;
    private TimelineListParameters mListParametersNew;
    
    /**
     * Is saved position restored (or some default positions set)?
     */
    private boolean mPositionRestored = false;
    
    /**
     * The is no more items in the query, so don't try to load more pages
     */
    private boolean mNoMoreItems = false;
    
    /**
     * For testing purposes
     */
    private long mInstanceId = 0;
    MyServiceReceiver mServiceConnector;

    /**
     * We are going to finish/restart this Activity (e.g. onResume or even onCreate)
     */
    private volatile boolean mFinishing = false;

    private boolean mShowSyncIndicatorOnTimeline = false;
    private View mSyncIndicator = null;    

    /**
     * Time when shared preferences where changed
     */
    private long mPreferencesChangeTime = 0;

    private MessageContextMenu mContextMenu;
    private MessageEditor mMessageEditor;
    private Menu mOptionsMenu = null;

    protected Menu getOptionsMenu() {
        return mOptionsMenu;
    }

    private String mTextToShareViaThisApp = "";
    private Uri mMediaToShareViaThisApp = Uri.EMPTY;

    private String mRateLimitText = "";

    private boolean isLoading() {
        return mLoadingLayout.getVisibility() == View.VISIBLE;
    }
    
    private void setLoading(boolean loading) {
        if (isLoading() != loading && !isFinishing()) {
            MyLog.v(this, "isLoading set to " + loading + ", instanceId=" + mInstanceId );
            if (loading) {
                mLoadingLayout.setVisibility(View.VISIBLE);
            } else {
                mLoadingLayout.setVisibility(View.INVISIBLE);
            }
        }
    }
    
    /**
     * This method is the first of the whole application to be called 
     * when the application starts for the very first time.
     * So we may put some Application initialization code here. 
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mListParameters = new TimelineListParameters(this);
        mListParametersNew = new TimelineListParameters(this);
        if (mInstanceId == 0) {
            mInstanceId = InstanceId.next();
        } else {
            MyLog.d(this, "onCreate reusing the same instanceId=" + mInstanceId);
        }

        mPreferencesChangeTime = MyContextHolder.initialize(this, this);
        mShowSyncIndicatorOnTimeline = MyPreferences.getBoolean(
		    MyPreferences.KEY_SYNC_INDICATOR_ON_TIMELINE, true);
        
        if (MyLog.isLoggable(this, MyLog.DEBUG)) {
            MyLog.d(this, "onCreate instanceId=" + mInstanceId 
                    + " , preferencesChangeTime=" + mPreferencesChangeTime
                    + (MyContextHolder.get().isReady() ? "" : ", MyContext is not ready")
                    );
        }
        if (HelpActivity.startFromActivity(this)) {
            return;
        }

        MyPreferences.loadTheme(this);
        mListParametersNew.myAccountUserId = MyContextHolder.get().persistentAccounts().getCurrentAccountUserId();
        mServiceConnector = new MyServiceReceiver(this);

        setContentView(R.layout.timeline);
        mSyncIndicator = findViewById(R.id.sync_indicator);
        mContextMenu = new MessageContextMenu(this);
        mMessageEditor = new MessageEditor(this);

        boolean isInstanceStateRestored = restoreInstanceState(savedInstanceState);
        
        LayoutInflater inflater = LayoutInflater.from(this);
        // Create list footer to show the progress of message loading
        mLoadingLayout = (LinearLayout) inflater.inflate(R.layout.item_loading, null);
        getListView().addFooterView(mLoadingLayout);
        
        createListAdapter(new MatrixCursor(TimelineSql.getTimelineProjection()));

        // Attach listeners to the message list
        getListView().setOnScrollListener(this);
        getListView().setOnCreateContextMenuListener(mContextMenu);
        getListView().setOnItemClickListener(this);
        
        if (!isInstanceStateRestored) {
            mListParametersNew.setTimelineCombined(MyPreferences.getDefaultSharedPreferences().getBoolean(MyPreferences.KEY_TIMELINE_IS_COMBINED, false));
            parseNewIntent(getIntent());
        }
        updateScreen();
        queryListData(false);
    }

    private boolean restoreInstanceState(Bundle savedInstanceState) {
        boolean isInstanceStateRestored = false;
        if (savedInstanceState != null) {
            TimelineTypeEnum timelineTypeNew = TimelineTypeEnum.load(savedInstanceState
                    .getString(IntentExtra.EXTRA_TIMELINE_TYPE.key));
            if (timelineTypeNew != TimelineTypeEnum.UNKNOWN) {
                isInstanceStateRestored = true;
                mListParametersNew.setTimelineType(timelineTypeNew);
                mMessageEditor.loadState(savedInstanceState);
                mContextMenu.loadState(savedInstanceState);
                if (savedInstanceState.containsKey(IntentExtra.EXTRA_TIMELINE_IS_COMBINED.key)) {
                    mListParametersNew.setTimelineCombined(savedInstanceState.getBoolean(IntentExtra.EXTRA_TIMELINE_IS_COMBINED.key));
                }
                if (savedInstanceState.containsKey(SearchManager.QUERY)) {
                    mListParametersNew.mSearchQuery = notNullString(savedInstanceState.getString(SearchManager.QUERY));
                }
                if (savedInstanceState.containsKey(IntentExtra.EXTRA_SELECTEDUSERID.key)) {
                    mListParametersNew.mSelectedUserId = savedInstanceState.getLong(IntentExtra.EXTRA_SELECTEDUSERID.key);
                }
            }
        }
        return isInstanceStateRestored;
    }

    /**
     * Switch combined timeline on/off
     * @param view combinedTimelineToggle
     */
    public boolean onCombinedTimelineToggleClick(MenuItem item) {
        boolean on = !isTimelineCombined(); //TODO: Doesn't work?! item.isChecked();
        MyPreferences.getDefaultSharedPreferences().edit().putBoolean(MyPreferences.KEY_TIMELINE_IS_COMBINED, on).commit();
        mContextMenu.switchTimelineActivity(mListParametersNew.getTimelineType(), on, mListParametersNew.myAccountUserId);
        return true;
    }

    public boolean onTimelineTypeButtonClick(MenuItem item) {
        showDialog(DIALOG_ID_TIMELINE_TYPE);
        return true;
    }
    
    public boolean onSelectAccountButtonClick(MenuItem item) {
        if (MyContextHolder.get().persistentAccounts().size() > 1) {
            AccountSelector.selectAccount(TimelineActivity.this, 0, ActivityRequestCode.SELECT_ACCOUNT);
        }
        return true;
    }
    
    /**
     * See <a href="http://developer.android.com/guide/topics/search/search-dialog.html">Creating 
     * a Search Interface</a>
     */
    @Override
    public boolean onSearchRequested() {
        return onSearchRequested(false);
    }

    private boolean onSearchRequested(boolean appGlobalSearch) {
        final String method = "onSearchRequested";
        Bundle appSearchData = new Bundle();
        appSearchData.putString(IntentExtra.EXTRA_TIMELINE_TYPE.key,
                appGlobalSearch ? TimelineTypeEnum.PUBLIC.save() : mListParametersNew.getTimelineType().save());
        appSearchData.putBoolean(IntentExtra.EXTRA_TIMELINE_IS_COMBINED.key, mListParametersNew.isTimelineCombined());
        appSearchData.putLong(IntentExtra.EXTRA_SELECTEDUSERID.key, mListParametersNew.mSelectedUserId);
        appSearchData.putBoolean(IntentExtra.EXTRA_GLOBAL_SEARCH.key, appGlobalSearch);
        MyLog.v(this, method  + ": " + appSearchData);
        startSearch(null, false, appSearchData, false);
        return true;
    }
    
    @Override
    protected void onResume() {
        String method = "onResume";
        super.onResume();
        MyLog.v(this, method + ", instanceId=" + mInstanceId);
        if (!mFinishing) {
            if (MyContextHolder.get().persistentAccounts().getCurrentAccount() != null) {
                long preferencesChangeTimeNew = MyContextHolder.initialize(this, this);
                if (preferencesChangeTimeNew != mPreferencesChangeTime) {
                    MyLog.v(this, method + "; Restarting this Activity to apply all new changes of preferences");
                    finish();
                    mContextMenu.switchTimelineActivity(mListParametersNew.getTimelineType(), mListParametersNew.isTimelineCombined(), mListParametersNew.mSelectedUserId);
                }
            } else { 
                MyLog.v(this, method + "; Finishing this Activity because there is no Account selected");
                finish();
            }
        }
        if (!mFinishing) {
            MyContextHolder.get().setInForeground(true);
            mServiceConnector.registerReceiver(this);
        }
    }

    private void saveListPosition() {
        new TimelineListPositionStorage(getListView(), mListParameters).save();
    }

    @Override
    public void onContentChanged() {
        if (MyLog.isLoggable(this, MyLog.DEBUG)) {
            MyLog.d(this, "onContentChanged started");
        }
        super.onContentChanged();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
            MyLog.v(this, "onPause, instanceId=" + mInstanceId);
        }
        mServiceConnector.unregisterReceiver(this);
        hideSyncIndicator();

        if (mPositionRestored) {
            // Get rid of the "fast scroll thumb"
            ((ListView) findViewById(android.R.id.list)).setFastScrollEnabled(false);
            clearNotifications();
            if (!isLoading()) {
                saveListPosition();
            }
            mPositionRestored = false;
        }        
        MyContextHolder.get().setInForeground(false);
    }
   
    /**
     *  Cancel notifications of loading timeline, which were set during Timeline downloading 
     */
    private void clearNotifications() {
        NotificationManager mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        
        mNM.cancel(CommandEnum.NOTIFY_HOME_TIMELINE.ordinal());
        mNM.cancel(CommandEnum.NOTIFY_MENTIONS.ordinal());
        mNM.cancel(CommandEnum.NOTIFY_DIRECT_MESSAGE.ordinal());

        MyAccount ma = MyContextHolder.get().persistentAccounts().fromUserId(mListParametersNew.myAccountUserId);
        if (ma != null) {
            MyServiceManager.sendForegroundCommand(new CommandData(CommandEnum.NOTIFY_CLEAR, ma.getAccountName()));
        }
    }

    @Override
    public void onDestroy() {
        MyLog.v(this,"onDestroy, instanceId=" + mInstanceId);
        if (mServiceConnector != null) {
            mServiceConnector.unregisterReceiver(this);
        }
        super.onDestroy();
    }

    @Override
    public void finish() {
        MyLog.v(this, "Finish requested" + (mFinishing ? ", already finishing" : "") 
                + ", instanceId=" + mInstanceId);
        if (!mFinishing) {
            mFinishing = true;
        }
        runOnUiThread( new Runnable() {
            @Override 
            public void run() {
                if (mPositionRestored) {
                    saveListPosition();
                }
            }
        });
        super.finish();
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case DIALOG_ID_TIMELINE_TYPE:
                return newTimelinetypeSelector();
            default:
                break;
        }
        return super.onCreateDialog(id);
    }

    private AlertDialog newTimelinetypeSelector() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dialog_title_select_timeline);
        final TimelineTypeSelector selector = new TimelineTypeSelector(this);
        builder.setItems(selector.getTitles(), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // The 'which' argument contains the index position of the
                // selected item
                TimelineTypeEnum type = selector.positionToType(which);
                if (type != TimelineTypeEnum.UNKNOWN) {
                    mContextMenu.switchTimelineActivity(type,
                            mListParametersNew.isTimelineCombined(), mListParametersNew.myAccountUserId);
                }
            }
        });
        return builder.create();                
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
        inflater.inflate(R.menu.timeline, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        mOptionsMenu = menu;
        MyAccount ma = MyContextHolder.get().persistentAccounts().getCurrentAccount();
        boolean enableReload = isTimelineCombined() || ( ma != null
                && ma.getCredentialsVerified() == CredentialsVerificationStatus.SUCCEEDED);
        MenuItem item = menu.findItem(R.id.reload_menu_item);
        item.setEnabled(enableReload);
        item.setVisible(enableReload);

        item = menu.findItem(R.id.timelineTypeButton);
        item.setTitle(timelineTypeButtonText());
        
        MenuItem combinedTimelineToggle = menu.findItem(R.id.combinedTimelineToggle);
        if (isTimelineCombined()) {
            combinedTimelineToggle.setTitle(R.string.combined_timeline_on);
        } else {
            combinedTimelineToggle.setTitle(mListParametersNew.getTimelineType().getPrepositionForNotCombinedTimeline(this));
        }
        combinedTimelineToggle.setChecked(isTimelineCombined());
        if (mListParametersNew.mSelectedUserId != 0 && mListParametersNew.mSelectedUserId != mListParametersNew.myAccountUserId) {
            combinedTimelineToggle.setVisible(false);
        } else {
            // Show the "Combined" toggle even for one account to see messages, 
            // which are not on the timeline.
            // E.g. messages by users, downloaded on demand.
            combinedTimelineToggle.setVisible(true);
        }
        
        if (mContextMenu != null) {
            mContextMenu.setAccountUserIdToActAs(0);
        }
        updateAccountButtonText(menu);

        if (mMessageEditor != null) {
            mMessageEditor.onPrepareOptionsMenu(menu);
        }
        
        boolean enableGlobalSearch = MyContextHolder.get().persistentAccounts()
                .isGlobalSearchSupported(ma, isTimelineCombined());
        item = menu.findItem(R.id.global_search_menu_id);
        item.setEnabled(enableGlobalSearch);
        item.setVisible(enableGlobalSearch);

        boolean enableAttach = mMessageEditor.isVisible() && MyPreferences.showAttachedImages() ;
        item = menu.findItem(R.id.attach_menu_id);
        item.setEnabled(enableAttach);
        item.setVisible(enableAttach);
        
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.preferences_menu_id:
                startMyPreferenceActivity();
                break;
            case R.id.search_menu_id:
                onSearchRequested();
                break;
            case R.id.reload_menu_item:
                manualReload(false);
                break;
            case R.id.global_search_menu_id:
                onSearchRequested(true);
                break;
            case R.id.attach_menu_id:
                mMessageEditor.onAttach();
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Listener that checks for clicks on the main list view.
     * 
     * @param adapterView
     * @param view
     * @param position
     * @param id
     */
    @Override
    public void onItemClick(AdapterView<?> adapterView, final View view, final int position, final long id) {
        if (id <= 0) {
            if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
                MyLog.v(this, "onItemClick, position=" + position + "; id=" + id + "; view=" + view);
            }
            return;
        }
        
        new AsyncTask<Void, Void, Uri>() {

            @Override
            protected Uri doInBackground(Void... params) {
                long linkedUserId = getLinkedUserIdFromCursor(position);
                MyAccount ma = MyContextHolder.get().persistentAccounts().getAccountWhichMayBeLinkedToThisMessage(id, linkedUserId,
                        mListParametersNew.myAccountUserId);
                if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
                    MyLog.v(this,
                            "onItemClick, position=" + position + "; id=" + id + "; view=" + view
                                    + "; linkedUserId=" + linkedUserId + " account="
                                    + ((ma != null) ? ma.getAccountName() : "?"));
                }
                return MyProvider.getTimelineMsgUri((ma != null) ? ma.getUserId() : 0, mListParametersNew.getTimelineType(), true, id);
            }

            @Override
            protected void onPostExecute(Uri uri) {
                String action = getIntent().getAction();
                if (Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action)) {
                    if (MyLog.isLoggable(this, MyLog.DEBUG)) {
                        MyLog.d(this, "onItemClick, setData=" + uri);
                    }
                    setResult(RESULT_OK, new Intent().setData(uri));
                } else {
                    if (MyLog.isLoggable(this, MyLog.DEBUG)) {
                        MyLog.d(this, "onItemClick, startActivity=" + uri);
                    }
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                }
            }
            
        }.execute();
    }

    /**
     * @param position Of current item in the underlying Cursor
     * @return id of the User linked to this message. This link reflects the User's timeline 
     * or an Account which was used to retrieved the message
     */
    @Override
    public long getLinkedUserIdFromCursor(int position) {
        long userId = 0;
        try {
            Cursor cursor = null;
            if (getListAdapter() != null) {
                cursor = ((CursorAdapter) getListAdapter()).getCursor();
            }
            if (cursor != null && !cursor.isClosed()) {
                cursor.moveToPosition(position);
                int columnIndex = cursor.getColumnIndex(User.LINKED_USER_ID);
                if (columnIndex > -1) {
                    userId = cursor.getLong(columnIndex);
                }
            }
        } catch (Exception e) {
            MyLog.v(this, e);
        }
        return userId;
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
            int totalItemCount) {
        if (!mNoMoreItems && mPositionRestored && !isLoading()) {
            // Idea from http://stackoverflow.com/questions/1080811/android-endless-list
            boolean loadMore = (visibleItemCount > 0) && (firstVisibleItem > 0)
                    && (firstVisibleItem + visibleItemCount >= totalItemCount);
            if (loadMore) {
                MyLog.d(this, "Start Loading more items, rows=" + totalItemCount);
                queryListData(true);
            }
        }
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        switch (scrollState) {
            case OnScrollListener.SCROLL_STATE_IDLE:
                break;
            case OnScrollListener.SCROLL_STATE_TOUCH_SCROLL:
                break;
            case OnScrollListener.SCROLL_STATE_FLING:
                // Turn the "fast scroll thumb" on
                view.setFastScrollEnabled(true);
                break;
            default:
                break;
        }
    }

    private String timelineTypeButtonText() {
        CharSequence timelinename = mListParametersNew.getTimelineType().getTitle(this);
        return timelinename + (TextUtils.isEmpty(mListParametersNew.mSearchQuery) ? "" : " *");
    }

    private void updateAccountButtonText(Menu menu) {
        MenuItem selectAccountButton = menu.findItem(R.id.selectAccountButton);
        String accountButtonText = buildAccountButtonText(mListParametersNew.myAccountUserId, isTimelineCombined(), getTimelineType());
        selectAccountButton.setTitle(accountButtonText);
    }

    public static String buildAccountButtonText(long maccountUserId, boolean timelineIsCombined, TimelineTypeEnum timelineType) {
        MyAccount ma = MyContextHolder.get().persistentAccounts()
                .fromUserId(maccountUserId);
        String accountButtonText;
        if (ma == null) {
            accountButtonText = "?";
        } else if (timelineIsCombined || timelineType != TimelineTypeEnum.PUBLIC) {
            accountButtonText = ma.shortestUniqueAccountName();
            if (ma.getCredentialsVerified() != CredentialsVerificationStatus.SUCCEEDED) {
                accountButtonText = "(" + accountButtonText + ")";
            }
        } else {
            accountButtonText = ma.getOriginName();
        }
        return accountButtonText;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
            MyLog.v(this, "onNewIntent, instanceId=" + mInstanceId
                    + (mFinishing ? ", Is finishing" : "")
                    );
        }
        if (mFinishing) {
            finish();
            return;
        }
        super.onNewIntent(intent);
        MyContextHolder.initialize(this, this);
        parseNewIntent(intent);
        updateScreen();
        queryListData(false);
    }

    private void parseNewIntent(Intent intentNew) {
        mRateLimitText = "";
        mListParametersNew.myAccountUserId = MyContextHolder.get().persistentAccounts().getCurrentAccountUserId();
        mListParametersNew.mSelectedUserId = 0;
        parseIntentData(intentNew);
        if (mListParametersNew.getTimelineType() == TimelineTypeEnum.UNKNOWN) {
            parseAppSearchData(intentNew);
        }
        if (mListParametersNew.getTimelineType() == TimelineTypeEnum.UNKNOWN) {
            /* Set default values */
            mListParametersNew.setTimelineType(TimelineTypeEnum.HOME);
            mListParametersNew.mSearchQuery = "";
        }
        if (mListParametersNew.getTimelineType() == TimelineTypeEnum.USER) {
            if (mListParametersNew.mSelectedUserId == 0) {
                mListParametersNew.mSelectedUserId = mListParametersNew.myAccountUserId;
            }
        } else {
            mListParametersNew.mSelectedUserId = 0;
        }

        if (Intent.ACTION_SEND.equals(intentNew.getAction())) {
            shareViaThisApplication(intentNew.getStringExtra(Intent.EXTRA_SUBJECT), 
                    intentNew.getStringExtra(Intent.EXTRA_TEXT),
                    (Uri) intentNew.getParcelableExtra(Intent.EXTRA_STREAM));
        }
    }

    private void parseIntentData(Intent intentNew) {
        mListParametersNew.setTimelineType(TimelineTypeEnum.load(intentNew
                .getStringExtra(IntentExtra.EXTRA_TIMELINE_TYPE.key)));
        if (mListParametersNew.getTimelineType() != TimelineTypeEnum.UNKNOWN) {
            mListParametersNew.setTimelineCombined(intentNew.getBooleanExtra(IntentExtra.EXTRA_TIMELINE_IS_COMBINED.key, mListParametersNew.isTimelineCombined()));
            mListParametersNew.mSearchQuery = notNullString(intentNew.getStringExtra(SearchManager.QUERY));
            mListParametersNew.mSelectedUserId = intentNew.getLongExtra(IntentExtra.EXTRA_SELECTEDUSERID.key, mListParametersNew.mSelectedUserId);
        }
    }

    private void parseAppSearchData(Intent intentNew) {
        Bundle appSearchData = intentNew.getBundleExtra(SearchManager.APP_DATA);
        if (appSearchData != null) {
            // We use other packaging of the same parameters in onSearchRequested
            mListParametersNew.setTimelineType(TimelineTypeEnum.load(appSearchData
                    .getString(IntentExtra.EXTRA_TIMELINE_TYPE.key)));
            if (mListParametersNew.getTimelineType() != TimelineTypeEnum.UNKNOWN) {
                mListParametersNew.setTimelineCombined(appSearchData.getBoolean(IntentExtra.EXTRA_TIMELINE_IS_COMBINED.key, mListParametersNew.isTimelineCombined()));
                /* The query itself is still from the Intent */
                mListParametersNew.mSearchQuery = notNullString(intentNew.getStringExtra(SearchManager.QUERY));
                mListParametersNew.mSelectedUserId = appSearchData.getLong(IntentExtra.EXTRA_SELECTEDUSERID.key, mListParametersNew.mSelectedUserId);
                if (!TextUtils.isEmpty(mListParametersNew.mSearchQuery)
                        && appSearchData.getBoolean(IntentExtra.EXTRA_GLOBAL_SEARCH.key, false)) {
                    MyLog.v(this, "Global search: " + mListParametersNew.mSearchQuery);
                    setLoading(true);
                    MyServiceManager.sendForegroundCommand(
                            CommandData.searchCommand(
                                    isTimelineCombined()
                                            ? ""
                                            : MyContextHolder.get().persistentAccounts()
                                                    .getCurrentAccountName(),
                                                    mListParametersNew.mSearchQuery));
                }
            }
        }
    }
    
    private void shareViaThisApplication(String subject, String text, Uri mediaUri) {
        if (TextUtils.isEmpty(subject) && TextUtils.isEmpty(text) && UriUtils.isEmpty(mediaUri)) {
            return;
        }
        mTextToShareViaThisApp = "";
        mMediaToShareViaThisApp = mediaUri;
        if (subjectHasAdditionalContent(subject, text)) {
            mTextToShareViaThisApp += subject;
        }
        if (!TextUtils.isEmpty(text)) {
            if (!TextUtils.isEmpty(mTextToShareViaThisApp)) {
                mTextToShareViaThisApp += " ";
            }
            mTextToShareViaThisApp += text;
        }
        MyLog.v(this, "Share via this app " 
        + (!TextUtils.isEmpty(mTextToShareViaThisApp) ? "; text:'" + mTextToShareViaThisApp +"'" : "") 
        + (!UriUtils.isEmpty(mMediaToShareViaThisApp) ? "; media:" + mMediaToShareViaThisApp.toString() : ""));
        AccountSelector.selectAccount(this, 0, ActivityRequestCode.SELECT_ACCOUNT_TO_SHARE_VIA);
    }

    static boolean subjectHasAdditionalContent(String subject, String text) {
        if (TextUtils.isEmpty(subject)) {
            return false;
        }
        if (TextUtils.isEmpty(text)) {
            return true;
        }
        return !text.startsWith(stripEllipsis(stripBeginning(subject)));
    }

    /**
     * Strips e.g. "Message - " or "Message:"
     */
    static String stripBeginning(String textIn) {
        if (TextUtils.isEmpty(textIn)) {
            return "";
        }
        int ind = textIn.indexOf("-");
        if (ind < 0) {
            ind = textIn.indexOf(":");
        }
        if (ind < 0) {
            return textIn;
        }
        String beginningSeparators = "-:;,.[] ";
        while ((ind < textIn.length()) && beginningSeparators.contains(String.valueOf(textIn.charAt(ind)))) {
            ind++;
        }
        if (ind >= textIn.length()) {
            return textIn;
        }
        return textIn.substring(ind);
    }
    
    static String stripEllipsis(String textIn) {
        if (TextUtils.isEmpty(textIn)) {
            return "";
        }
        int ind = textIn.length() - 1;
        String ellipsis = "… .";
        while (ind >= 0 && ellipsis.contains(String.valueOf(textIn.charAt(ind)))) {
            ind--;
        }
        if (ind < -1) {
            return "";
        }
        return textIn.substring(0, ind+1);
    }

    private void updateScreen() {
        MyServiceManager.setServiceAvailable();
        invalidateOptionsMenu();
        if (mMessageEditor.isStateLoaded()) {
            mMessageEditor.continueEditingLoadedState();
        } else if (mMessageEditor.isVisible()) {
            // This is done to request focus (if we need this...)
            mMessageEditor.show();
        }
        updateTitle();
    }

    private void updateTitle() {
        new TimelineTitle(mListParameters.getTimelineType() != TimelineTypeEnum.UNKNOWN ?
                mListParameters : mListParametersNew
                , mRateLimitText).updateTitle(this);
    }
    
    static class TimelineTitle {
        String title;
        String subTitle;
        
        public TimelineTitle(TimelineListParameters ta, String additionalTitleText) {
            title = "" + ta.getTimelineType().getTitle(ta.mContext) + " ";
            subTitle = "";
            
            if (ta.getSelectedUserId() != 0 && ta.getSelectedUserId() != ta.getCurrentMyAccountUserId()) {
                title += ta.getTimelineType().getPrepositionForNotCombinedTimeline(ta.mContext);
                subTitle = MyProvider.userIdToName(ta.getSelectedUserId());
            } else {
                if (ta.isTimelineCombined()) {
                    title += ta.mContext.getText(R.string.combined_timeline_on);
                } else {
                    title += ta.getTimelineType().getPrepositionForNotCombinedTimeline(ta.mContext);
                }
                subTitle = " " + buildAccountButtonText(ta.getCurrentMyAccountUserId(), 
                                ta.isTimelineCombined(),
                                ta.getTimelineType());
            }
            if (!TextUtils.isEmpty(additionalTitleText)) {
                subTitle += " " + additionalTitleText;
            }
        }

        private void updateTitle(Activity activity) {
            ActionBar actionBar = activity.getActionBar();
            if (actionBar != null) {
                actionBar.setTitle(title);
                actionBar.setSubtitle(subTitle);
            }
            if (MyLog.isLoggable(activity, MyLog.VERBOSE)) {
                MyLog.v(activity, "Title: " + toString());
            }
        }
        
        @Override
        public String toString() {
            return title + "; " + subTitle;
        }
    }
    
    /**
     * Prepare a query to the ContentProvider (to the database) and load the visible List of
     * messages with this data
     * This is done asynchronously.
     * This method should be called from UI thread only.
     * 
     * @param loadOneMorePage true - load one more page of messages, false - reload the same page
     */
    protected void queryListData(boolean loadOneMorePage) {
        final String method = "queryListData";
        if (!loadOneMorePage) {
            mNoMoreItems = false;
        }
        MyLog.v(this, method + (loadOneMorePage ? "loadOneMorePage" : ""));
        Bundle args = new Bundle();
        args.putBoolean(IntentExtra.EXTRA_LOAD_ONE_MORE_PAGE.key, loadOneMorePage);
        args.putInt(IntentExtra.EXTRA_ROWS_LIMIT.key, calcRowsLimit(loadOneMorePage));
        getLoaderManager().restartLoader(0, args, this);
        setLoading(true);
    }

    private int calcRowsLimit(boolean loadOneMorePage) {
        int nMessages = 0;
        if (getListAdapter() != null) {
            Cursor cursor = ((CursorAdapter) getListAdapter()).getCursor();
            if (cursor != null && !cursor.isClosed()) {
                nMessages = cursor.getCount();
            }
        }
        if (loadOneMorePage) {
            nMessages += TimelineListParameters.PAGE_SIZE;
        } else if (nMessages < TimelineListParameters.PAGE_SIZE) {
            nMessages = TimelineListParameters.PAGE_SIZE;
        }
        return nMessages;
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        final String method = "onCreateLoader";
        MyLog.v(this, method + " #" + id);
        if (args == null) {
            args = new Bundle();
        }
        args.putBoolean(IntentExtra.EXTRA_POSITION_RESTORED.key, mPositionRestored
                && (getListAdapter() != null));
        
        TimelineListParameters params = TimelineListParameters.clone(mListParametersNew, args);
        Intent intent = getIntent();
        if (!params.mContentUri.equals(intent.getData())) {
            intent.setData(params.mContentUri);
        }
        saveSearchQuery();
        return new TimelineCursorLoader1(params);
    }
   
    private void saveSearchQuery() {
        if (!TextUtils.isEmpty(mListParametersNew.mSearchQuery)) {
            // Record the query string in the recent queries
            // of the Suggestion Provider
            SearchRecentSuggestions suggestions = new SearchRecentSuggestions(this,
                    TimelineSearchSuggestionsProvider.AUTHORITY,
                    TimelineSearchSuggestionsProvider.MODE);
            suggestions.saveRecentQuery(mListParametersNew.mSearchQuery, null);

        }
    }
    
    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        MyLog.v(this, "onLoaderReset; " + loader);
        setLoading(false);
    }
    
    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        final String method = "onLoadFinished"; 
        MyLog.v(this, method);
        TimelineTypeEnum timelineToReload = TimelineTypeEnum.UNKNOWN;
        boolean requestNextPage = false;
        if (loader.isStarted()) {
            if (loader instanceof TimelineCursorLoader1) {
                TimelineCursorLoader1 myLoader = (TimelineCursorLoader1) loader;
                changeListContent(myLoader.getParams(), cursor);
                timelineToReload = myLoader.getParams().timelineToReload;
                if (!myLoader.getParams().mLoadOneMorePage && myLoader.getParams().mLastItemId != 0
                        && cursor != null && cursor.getCount() < TimelineListParameters.PAGE_SIZE) {
                    MyLog.v(this, method + "; Requesting next page...");
                    requestNextPage = true;
                }
            } else {
                MyLog.e(this, method + "; Wrong type of loader: " + MyLog.objTagToString(loader));
            }
        }
        setLoading(false);
        updateScreen();
        if (requestNextPage) {
            queryListData(true);
        } else {
            launchReloadIfNeeded(timelineToReload);
        }
    }
    
    private void changeListContent(TimelineListParameters params, Cursor cursor) {
        if (!params.cancelled && cursor != null && !mFinishing) {
            MyLog.v(this, "On changing Cursor");
            // This check will prevent continuous loading...
            mNoMoreItems = params.mIncrementallyLoadingPages &&
                    cursor.getCount() <= getListAdapter().getCount();
            saveListPosition();
            ((CursorAdapter) getListAdapter()).swapCursor(cursor);
            mListParameters = params;
            mPositionRestored = new TimelineListPositionStorage(getListView(), mListParameters).restoreListPosition();
        }
    }
    
    private void launchReloadIfNeeded(TimelineTypeEnum timelineToReload) {
        switch (timelineToReload) {
            case ALL:
                manualReload(true);
                break;
            case UNKNOWN:
                break;
            default:
                manualReload(false);
                break;
        }
    }

    /**
     * Ask a service to load data from the Internet for the selected TimelineType
     * Only newer messages (newer than last loaded) are being loaded from the
     * Internet, older ones are not being reloaded.
     */
    protected void manualReload(boolean allTimelineTypes) {
        MyAccount ma = MyContextHolder.get().persistentAccounts().fromUserId(mListParametersNew.myAccountUserId);
        TimelineTypeEnum timelineTypeForReload = TimelineTypeEnum.HOME;
        long userId = 0;
        switch (mListParametersNew.getTimelineType()) {
            case DIRECT:
            case MENTIONS:
            case PUBLIC:
                timelineTypeForReload = mListParametersNew.getTimelineType();
                break;
            case USER:
            case FOLLOWING_USER:
                timelineTypeForReload = mListParametersNew.getTimelineType();
                userId = mListParametersNew.mSelectedUserId;
                break;
            default:
                break;
        }
        boolean allAccounts = mListParametersNew.isTimelineCombined();
        if (userId != 0) {
            allAccounts = false;
            long originId = MyProvider.userIdToLongColumnValue(MyDatabase.User.ORIGIN_ID, userId);
            if (originId == 0) {
                MyLog.e(this, "Unknown origin for userId=" + userId);
                return;
            }
            if (ma == null || ma.getOriginId() != originId) {
                ma = MyContextHolder.get().persistentAccounts().fromUserId(userId);
                if (ma == null) {
                    ma = MyContextHolder.get().persistentAccounts().findFirstMyAccountByOriginId(originId);
                }
            }
        }
        if (!allAccounts && ma == null) {
            return;
        }

        setLoading(true);
        MyServiceManager.sendForegroundCommand(
                new CommandData(CommandEnum.FETCH_TIMELINE,
                        allAccounts ? "" : ma.getAccountName(), timelineTypeForReload, userId)
                );

        if (allTimelineTypes && ma != null) {
            ma.requestSync();
        }
    }
    
    protected void startMyPreferenceActivity() {
        finish();
        startActivity(new Intent(this, MyPreferenceActivity.class));
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        mMessageEditor.saveState(outState);
        mListParametersNew.SaveState(outState);
        mContextMenu.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK || data == null) {
            return;
        }
        switch (ActivityRequestCode.fromId(requestCode)) {
            case SELECT_ACCOUNT:
                MyAccount ma = MyContextHolder.get().persistentAccounts().fromAccountName(data.getStringExtra(IntentExtra.EXTRA_ACCOUNT_NAME.key));
                if (ma != null) {
                    MyLog.v(this, "Restarting the activity for the selected account " + ma.getAccountName());
                    finish();
                    TimelineTypeEnum timelineTypeNew = mListParametersNew.getTimelineType();
                    if (mListParametersNew.getTimelineType() == TimelineTypeEnum.USER 
                            &&  (MyContextHolder.get().persistentAccounts().fromUserId(mListParametersNew.mSelectedUserId) == null)) {
                        /*  "Other User's timeline" vs "My User's timeline" 
                         * Actually we saw messages of the user, who is not MyAccount,
                         * so let's switch to the HOME
                         * TODO: Open "Other User timeline" in a separate Activity?!
                         */
                        timelineTypeNew = TimelineTypeEnum.HOME;
                    }
                    MyContextHolder.get().persistentAccounts().setCurrentAccount(ma);
                    mContextMenu.switchTimelineActivity(timelineTypeNew, mListParametersNew.isTimelineCombined(), ma.getUserId());
                }
                break;
            case SELECT_ACCOUNT_TO_ACT_AS:
                ma = MyContextHolder.get().persistentAccounts().fromAccountName(data.getStringExtra(IntentExtra.EXTRA_ACCOUNT_NAME.key));
                if (ma != null) {
                    mContextMenu.setAccountUserIdToActAs(ma.getUserId());
                    mContextMenu.showContextMenu();
                }
                break;
            case SELECT_ACCOUNT_TO_SHARE_VIA:
                ma = MyContextHolder.get().persistentAccounts().fromAccountName(data.getStringExtra(IntentExtra.EXTRA_ACCOUNT_NAME.key));
                if (ma != null) {
                    mMessageEditor.startEditingMessage(mTextToShareViaThisApp, mMediaToShareViaThisApp, 
                            0, 0, ma);
                }
                break;
            case ATTACH:
                Uri uri = UriUtils.notNull(data.getData());
                if (resultCode == RESULT_OK && !UriUtils.isEmpty(uri)) {
                    mMediaToShareViaThisApp = uri;
                    if (mMessageEditor.isVisible()) {
                        mMessageEditor.setMedia(mMediaToShareViaThisApp);
                    }
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }

    private void createListAdapter(Cursor cursor) {
        List<String> columnNames = new ArrayList<String>();
        List<Integer> viewIds = new ArrayList<Integer>();
        columnNames.add(MyDatabase.User.AUTHOR_NAME);
        viewIds.add(R.id.message_author);
        columnNames.add(MyDatabase.Msg.BODY);
        viewIds.add(R.id.message_body);
        columnNames.add(MyDatabase.Msg.CREATED_DATE);
        viewIds.add(R.id.message_details);
        columnNames.add(MyDatabase.MsgOfUser.FAVORITED);
        viewIds.add(R.id.message_favorited);
        columnNames.add(MyDatabase.Msg._ID);
        viewIds.add(R.id.id);
        int listItemLayoutId = R.layout.message_basic;
        if (MyPreferences.showAvatars()) {
            listItemLayoutId = R.layout.message_avatar;
            columnNames.add(MyDatabase.Download.AVATAR_FILE_NAME);
            viewIds.add(R.id.avatar_image);
        }
        if (MyPreferences.showAttachedImages()) {
            columnNames.add(MyDatabase.Download.IMAGE_ID);
            viewIds.add(R.id.attached_image);
        }
        MySimpleCursorAdapter messageAdapter = new MySimpleCursorAdapter(TimelineActivity.this,
                listItemLayoutId, cursor, columnNames.toArray(new String[]{}),
                toIntArray(viewIds), 0);
        messageAdapter.setViewBinder(new TimelineViewBinder());

        setListAdapter(messageAdapter);
    }
    
    /**
     * See http://stackoverflow.com/questions/960431/how-to-convert-listinteger-to-int-in-java
     */
    private static int[] toIntArray(List<Integer> list){
        int[] ret = new int[list.size()];
        for(int i = 0;i < ret.length;i++) {
            ret[i] = list.get(i);
        }
        return ret;
    }

    private static String notNullString(String string) {
        return string == null ? "" : string;
    }

    @Override
    public void onReceive(CommandData commandData, MyServiceEvent event) {
        switch (event) {
            case BEFORE_EXECUTING_COMMAND:
                if (mShowSyncIndicatorOnTimeline
                        && isCommandToShowInSyncIndicator(commandData.getCommand())) {
                    onReceiveBeforeExecutingCommand(commandData);
                }
                break;
            case AFTER_EXECUTING_COMMAND:
                onReceiveAfterExecutingCommand(commandData);
                break;
            case ON_STOP:
                hideSyncIndicator();
                break;
            default:
                break;
        }
    }

    private boolean isCommandToShowInSyncIndicator(CommandEnum command) {
        switch (command) {
            case AUTOMATIC_UPDATE:
            case FETCH_TIMELINE:
            case FETCH_ATTACHMENT:
            case FETCH_AVATAR:
            case UPDATE_STATUS:
            case DESTROY_STATUS:
            case CREATE_FAVORITE:
            case DESTROY_FAVORITE:
            case SEARCH_MESSAGE:
            case FOLLOW_USER:
            case STOP_FOLLOWING_USER:
            case REBLOG:
            case DESTROY_REBLOG:
                return true;
            default:
                return false;
        }
    }
    
    private void onReceiveBeforeExecutingCommand(CommandData commandData) {
        if (mSyncIndicator.getVisibility() != View.VISIBLE) {
            mSyncIndicator.setVisibility(View.VISIBLE);
        }
        String syncMessage = getText(R.string.title_preference_syncing) + ": "
                + commandData.toCommandSummary(MyContextHolder.get());
        ((TextView) findViewById(R.id.sync_text)).setText(syncMessage);
        MyLog.v(this, syncMessage);
    }

    private void onReceiveAfterExecutingCommand(CommandData commandData) {
        switch (commandData.getCommand()) {
            case FETCH_TIMELINE:
            case SEARCH_MESSAGE:
                if (isLoading()) {
                    setLoading(false);
                }
                break;
            case RATE_LIMIT_STATUS:
                if (commandData.getResult().getHourlyLimit() > 0) {
                    mRateLimitText = commandData.getResult().getRemainingHits() + "/"
                            + commandData.getResult().getHourlyLimit();
                    updateTitle();
                }
                break;
            default:
                break;
        }
		if (mShowSyncIndicatorOnTimeline
			&& isCommandToShowInSyncIndicator(commandData.getCommand())) {
			((TextView) findViewById(R.id.sync_text)).setText("");
		}
    }

    private void hideSyncIndicator() {
        if (mSyncIndicator.getVisibility() == View.VISIBLE) {
            mSyncIndicator.setVisibility(View.GONE);
        }
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
    public long getCurrentMyAccountUserId() {
        return mListParametersNew.myAccountUserId;
    }

    @Override
    public TimelineTypeEnum getTimelineType() {
        return mListParametersNew.getTimelineType();
    }

    @Override
    public boolean isTimelineCombined() {
        return mListParametersNew.isTimelineCombined();
    }

    @Override
    public long getSelectedUserId() {
        return mListParametersNew.mSelectedUserId;
    }
}
