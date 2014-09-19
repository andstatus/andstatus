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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.NotificationManager;
import android.app.SearchManager;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.widget.Button;
import android.widget.CursorAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ToggleButton;

import org.andstatus.app.account.AccountSelector;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.account.MyAccount.CredentialsVerificationStatus;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferenceActivity;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.AccountUserIds;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyDatabase.Download;
import org.andstatus.app.data.MyDatabase.Msg;
import org.andstatus.app.data.MyDatabase.MsgOfUser;
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.TimelineSearchSuggestionsProvider;
import org.andstatus.app.data.TimelineTypeEnum;
import org.andstatus.app.data.TimelineViewBinder;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyServiceEvent;
import org.andstatus.app.service.MyServiceListener;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.service.MyServiceReceiver;
import org.andstatus.app.support.android.v11.app.MyLoader;
import org.andstatus.app.support.android.v11.app.MyLoaderManager;
import org.andstatus.app.support.android.v11.widget.MySimpleCursorAdapter;
import org.andstatus.app.util.InstanceId;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.UriUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yvolk@yurivolkov.com, torgny.bjers
 */
public class TimelineActivity extends ListActivity implements MyServiceListener, OnScrollListener, OnItemClickListener, ActionableMessageList, MyLoaderManager.LoaderCallbacks<Cursor>, MyActionBarContainer {
    private static final int DIALOG_ID_TIMELINE_TYPE = 9;

    private static final String KEY_LAST_POSITION = "last_position_";

    /**
     * Visibility of the layout indicates whether Messages are being loaded into the list (asynchronously...)
     * The layout appears at the bottom of the list of messages 
     * when new items are being loaded into the list 
     */
    private LinearLayout mLoadingLayout;

    /** Parameters of currently shown Timeline */
    private TimelineListParameters mListParameters = new TimelineListParameters();
    
    /**
     * Msg are being loaded into the list starting from one page. More Msg
     * are being loaded in a case User scrolls down to the end of list.
     */
    private static final int PAGE_SIZE = 100;

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

    private TimelineTypeEnum mTimelineType = TimelineTypeEnum.UNKNOWN;

    /** Combined Timeline shows messages from all accounts */
    private boolean mTimelineIsCombined = false;
    
    private boolean mShowSyncIndicatorOnTimeline = false;
    private View mSyncIndicator = null;    
    
    /**
     * UserId of the MyAccount, for which we show the activity
     */
    private long mCurrentMyAccountUserId = 0;
    
    /**
     * Selected User for the {@link TimelineTypeEnum#USER} timeline.
     * This is either User Id of current account OR user id of any other selected user.
     * So it's never == 0 for the {@link TimelineTypeEnum#USER} timeline
     */
    private long mSelectedUserId = 0;

    /**
     * The string is not empty if this timeline is filtered using query string
     * ("Mentions" are not counted here because they have separate TimelineType)
     */
    private String mSearchQuery = "";

    /**
     * Time when shared preferences where changed
     */
    private long mPreferencesChangeTime = 0;

    private MessageContextMenu mContextMenu;
    private MessageEditor mMessageEditor;

    private static final int LOADER_ID = 1;
    private MyLoaderManager<Cursor> mLoaderManager = null;

    private String mTextToShareViaThisApp = "";
    private Uri mMediaToShareViaThisApp = Uri.EMPTY;
    
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
        MyActionBar actionBar = new MyActionBar(this, R.layout.timeline_actions);
        super.onCreate(savedInstanceState);
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

        mCurrentMyAccountUserId = MyContextHolder.get().persistentAccounts().getCurrentAccountUserId();
        mServiceConnector = new MyServiceReceiver(this);

        setContentView(R.layout.timeline);
        actionBar.attach();
        mSyncIndicator = findViewById(R.id.sync_indicator);
        mContextMenu = new MessageContextMenu(this);
        mMessageEditor = new MessageEditor(this);

        boolean isInstanceStateRestored = restoreInstanceState(savedInstanceState);
        
        mLoaderManager = new MyLoaderManager<Cursor>();
        
        LayoutInflater inflater = LayoutInflater.from(this);
        // Create list footer to show the progress of message loading
        mLoadingLayout = (LinearLayout) inflater.inflate(R.layout.item_loading, null);
        getListView().addFooterView(mLoadingLayout);
        
        createListAdapter(new MatrixCursor(getProjection()));

        // Attach listeners to the message list
        getListView().setOnScrollListener(this);
        getListView().setOnCreateContextMenuListener(mContextMenu);
        getListView().setOnItemClickListener(this);

        Button accountButton = (Button) findViewById(R.id.selectAccountButton);
        accountButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (MyContextHolder.get().persistentAccounts().size() > 1) {
                    AccountSelector.selectAccount(TimelineActivity.this, 0, ActivityRequestCode.SELECT_ACCOUNT);
                }
            }
        });
        
        if (!isInstanceStateRestored) {
            mTimelineIsCombined = MyPreferences.getDefaultSharedPreferences().getBoolean(MyPreferences.KEY_TIMELINE_IS_COMBINED, false);
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
                mTimelineType = timelineTypeNew;
                mMessageEditor.loadState(savedInstanceState);
                mContextMenu.loadState(savedInstanceState);
                if (savedInstanceState.containsKey(IntentExtra.EXTRA_TIMELINE_IS_COMBINED.key)) {
                    mTimelineIsCombined = savedInstanceState.getBoolean(IntentExtra.EXTRA_TIMELINE_IS_COMBINED.key);
                }
                if (savedInstanceState.containsKey(SearchManager.QUERY)) {
                    mSearchQuery = notNullString(savedInstanceState.getString(SearchManager.QUERY));
                }
                if (savedInstanceState.containsKey(IntentExtra.EXTRA_SELECTEDUSERID.key)) {
                    mSelectedUserId = savedInstanceState.getLong(IntentExtra.EXTRA_SELECTEDUSERID.key);
                }
            }
        }
        return isInstanceStateRestored;
    }

    /**
     * Switch combined timeline on/off
     * @param view combinedTimelineToggle
     */
    public void onCombinedTimelineToggle(View view) {
        if (view instanceof android.widget.ToggleButton) {
            boolean on = ((android.widget.ToggleButton) view).isChecked();
            MyPreferences.getDefaultSharedPreferences().edit().putBoolean(MyPreferences.KEY_TIMELINE_IS_COMBINED, on).commit();
            mContextMenu.switchTimelineActivity(mTimelineType, on, mCurrentMyAccountUserId);
        }
    }
    
    public void onTimelineTypeButtonClick(View view) {
        showDialog(DIALOG_ID_TIMELINE_TYPE);
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
                appGlobalSearch ? TimelineTypeEnum.PUBLIC.save() : mTimelineType.save());
        appSearchData.putBoolean(IntentExtra.EXTRA_TIMELINE_IS_COMBINED.key, mTimelineIsCombined);
        appSearchData.putLong(IntentExtra.EXTRA_SELECTEDUSERID.key, mSelectedUserId);
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
                    mContextMenu.switchTimelineActivity(mTimelineType, mTimelineIsCombined, mSelectedUserId);
                }
            } else { 
                MyLog.v(this, method + "; Finishing this Activity because there is no Account selected");
                finish();
            }
        }
        if (!mFinishing) {
            MyContextHolder.get().setInForeground(true);
            mServiceConnector.registerReceiver(this);
            mLoaderManager.onResumeActivity(LOADER_ID);
        }
    }

    /**
     * Save current position per User and per TimeleneType. 
     * The position is NOT saved (i.e. the stored position remains the same),
     * if there are no items in the list. Actually we save two Item IDs.
     * 1. The first visible item. 
     * 2. The last item we should retrieve before restoring the position.
     */
    private void saveListPosition() {
        final String method = "saveListPosition";
        android.widget.ListAdapter la = getListView().getAdapter();
        if (la == null) {
            MyLog.v(this, method + " skipped: no ListAdapter");
            return;
        }
        if (mListParameters.isEmpty()) {
            MyLog.v(this, method + " skipped: no listParameters");
            return;
        }

        int firstVisiblePosition = getListView().getFirstVisiblePosition();
        // Don't count a footer
        int itemCount = la.getCount() - 1;
        if (firstVisiblePosition >= itemCount) {
            firstVisiblePosition = itemCount - 1;
        }
        long firstVisibleItemId = 0;
        int lastPosition = -1;
        long lastItemId = 0;
        if (firstVisiblePosition >= 0) {
            firstVisibleItemId = la.getItemId(firstVisiblePosition);
            MyLog.v(this, method + " firstVisiblePos:" + firstVisiblePosition + " of " + itemCount
                    + "; itemId:" + firstVisibleItemId);
            // We will load one more "page of messages" below (older) current top item
            lastPosition = firstVisiblePosition + PAGE_SIZE;
            if (lastPosition >= itemCount) {
                lastPosition = itemCount - 1;
            }
            if (lastPosition >= PAGE_SIZE) {
                lastItemId = la.getItemId(lastPosition);
            }
        }

        ListPositionStorage ps = new ListPositionStorage(mListParameters);
        if (firstVisibleItemId <= 0) {
            MyLog.v(this, method + " failed: no visible items for \"" + ps.accountGuid + "\"; " + ps.keyFirst);
        } else {
            ps.put(firstVisibleItemId, lastItemId);

            if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
                MyLog.v(this, method + " succeeded \"" + ps.accountGuid + "\"; " + ps.keyFirst + "="
                        + firstVisibleItemId + ", pos=" + firstVisiblePosition + "; lastId="
                        + lastItemId + ", pos=" + lastPosition);
            }
        }
    }
    
    /**
     * Determines where to save / retrieve position in the list
     * Two rows are always stored for each position hence two keys. 
     * Plus Query string is being stored for the search results.
     * @author yvolk@yurivolkov.com
     */
    private static class ListPositionStorage {
        /**
         * MyAccount for SharedPreferences ( =="" for DefaultSharedPreferences) 
         */
        private String accountGuid = "";
        /**
         * SharePreferences to use for storage 
         */
        private SharedPreferences sp = null;
        /**
         * Key name for the first visible item
         */
        private String keyFirst = "";
        /**
         * Key for the last item we should retrieve before restoring position
         */
        private String keyLast = "";
        private String queryString;
        /**
         * Key for the Query string
         */
        private String keyQueryString = "";
        
        private ListPositionStorage(TimelineListParameters listParameters) {
            queryString = listParameters.searchQuery; 
            if ((listParameters.timelineType != TimelineTypeEnum.USER) && !listParameters.timelineCombined) {
                MyAccount ma = MyContextHolder.get().persistentAccounts().fromUserId(listParameters.myAccountUserId);
                if (ma != null) {
                    sp = ma.getAccountPreferences();
                    accountGuid = ma.getAccountName();
                } else {
                    MyLog.e(this, "No accoount for IserId=" + listParameters.myAccountUserId);
                }
            }
            if (sp == null) {
                sp = MyPreferences.getDefaultSharedPreferences();
            }
            
            keyFirst = KEY_LAST_POSITION
                    + listParameters.timelineType.save()
                    + (listParameters.timelineType == TimelineTypeEnum.USER ? "_user"
                            + Long.toString(listParameters.selectedUserId) : "") + (TextUtils.isEmpty(queryString) ? "" : "_search");
            keyLast = keyFirst + "_last";
            keyQueryString = KEY_LAST_POSITION + listParameters.timelineType.save() + "_querystring";
        }

        private void put(long firstVisibleItemId, long lastRetrievedItemId) {
            sp.edit().putLong(keyFirst, firstVisibleItemId)
            .putLong(keyLast, lastRetrievedItemId)
            .putString(keyQueryString, queryString).commit();
        }

        private static final long ID_NOT_FOUND_IN_LIST_POSITION_STORAGE = -4;
        private static final long ID_NOT_FOUND_IN_SHARED_PREFERENCES = -1;
        private long getFirst() {
            long savedItemId = ID_NOT_FOUND_IN_LIST_POSITION_STORAGE;
            if (isThisPositionStored()) {
                savedItemId = sp.getLong(keyFirst, ID_NOT_FOUND_IN_SHARED_PREFERENCES);
            }
            return savedItemId;
        }
        
        private boolean isThisPositionStored() {
            return queryString.compareTo(sp.getString(
                            keyQueryString, "")) == 0;
        }

        private long getLast() {
            long savedItemId = ID_NOT_FOUND_IN_LIST_POSITION_STORAGE;
            if (isThisPositionStored()) {
                savedItemId = sp.getLong(keyLast, ID_NOT_FOUND_IN_SHARED_PREFERENCES);
            }
            return savedItemId;
        }
        
        private void clear() {
            sp.edit().remove(keyFirst).remove(keyLast)
                    .remove(keyQueryString).commit();
            if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
                MyLog.v(this, "Position forgot   \"" + accountGuid + "\"; " + keyFirst);
            }
        }
    }
    
    /**
     * Restore (First visible item) position saved for this user and for this type of timeline
     */
    private void restoreListPosition() {
        final String method = "restoreListPosition";
        ListPositionStorage ps = new ListPositionStorage(mListParameters);
        boolean loaded = false;
        int scrollPos = -1;
        long firstItemId = -3;
        try {
            firstItemId = ps.getFirst();
            if (firstItemId > 0) {
                scrollPos = listPosForId(firstItemId);
            }
            if (scrollPos >= 0) {
                getListView().setSelectionFromTop(scrollPos, 0);
                loaded = true;
            } else {
                // There is no stored position
                if (TextUtils.isEmpty(mSearchQuery)) {
                    scrollPos = getListView().getCount() - 2;
                } else {
                    // In search mode start from the most recent message!
                    scrollPos = 0;
                }
                if (scrollPos >= 0) {
                    setSelectionAtBottom(scrollPos);
                }
            }
        } catch (Exception e) {
            MyLog.v(this, method, e);
        }
        if (loaded) {
            if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
                MyLog.v(this, method + " succeeded \"" + ps.accountGuid + "\"; " + ps.keyFirst + "="
                        + firstItemId +"; index=" + scrollPos);
            }
        } else {
            if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
                MyLog.v(this, method + " failed \"" + ps.accountGuid + "\"; " + ps.keyFirst + "="
                        + firstItemId);
            }
            ps.clear();
        }
        mPositionRestored = true;
    }

    private void setSelectionAtBottom(int scrollPos) {
        if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
            MyLog.v(this, "setSelectionAtBottom, 1");
        }
        int viewHeight = getListView().getHeight();
        int childHeight;
        childHeight = 30;
        int y = viewHeight - childHeight;
        if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
            MyLog.v(this, "set position of last item to " + y + "px");
        }
        getListView().setSelectionFromTop(scrollPos, y);
    }

    /**
     * Returns the position of the item with the given ID.
     * 
     * @param searchedId the ID of the item whose position in the list is to be
     *            returned.
     * @return the position in the list or -1 if the item was not found
     */
    private int listPosForId(long searchedId) {
        int listPos;
        boolean itemFound = false;
        ListView lv = getListView();
        int itemCount = lv.getCount();
        if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
            MyLog.v(this, "item count: " + itemCount);
        }
        for (listPos = 0; !itemFound && (listPos < itemCount); listPos++) {
            long itemId = lv.getItemIdAtPosition(listPos);
            if (itemId == searchedId) {
                itemFound = true;
                break;
            }
        }

        if (!itemFound) {
            listPos = -1;
        }
        return listPos;
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
        mLoaderManager.onPauseActivity(LOADER_ID);
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

        MyAccount ma = MyContextHolder.get().persistentAccounts().fromUserId(mCurrentMyAccountUserId);
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
                if (mLoaderManager != null) {
                    mLoaderManager.destroyLoader(LOADER_ID);
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
                            mTimelineIsCombined, mCurrentMyAccountUserId);
                }
            }
        });
        return builder.create();                
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
        Intent intent = new Intent(null, getIntent().getData());
        intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
        menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0, new ComponentName(this,
                TimelineActivity.class), null, intent, 0, null);
        MyAccount ma = MyContextHolder.get().persistentAccounts().getCurrentAccount();
        
        boolean enableReload = ma != null
                && ma.getCredentialsVerified() == CredentialsVerificationStatus.SUCCEEDED;
        MenuItem item = menu.findItem(R.id.reload_menu_item);
        item.setEnabled(enableReload);
        item.setVisible(enableReload);

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
    public boolean onContextItemSelected(MenuItem item) {
        mContextMenu.onContextItemSelected(item);
        return super.onContextItemSelected(item);
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
                onAttach();
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * See http://stackoverflow.com/questions/2169649/get-pick-an-image-from-androids-built-in-gallery-app-programmatically
     */
    private void onAttach() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent,
                getText(R.string.options_menu_attach)), ActivityRequestCode.ATTACH.id);
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
                        mCurrentMyAccountUserId);
                if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
                    MyLog.v(this,
                            "onItemClick, position=" + position + "; id=" + id + "; view=" + view
                                    + "; linkedUserId=" + linkedUserId + " account="
                                    + ((ma != null) ? ma.getAccountName() : "?"));
                }
                return MyProvider.getTimelineMsgUri((ma != null) ? ma.getUserId() : 0, mTimelineType, true, id);
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

    private void updateActionBarText() {
        updateTimelineTypeButtonText();
        updateAccountButtonText();
        updateRightText("");
    }

    private void updateTimelineTypeButtonText() {
        CharSequence timelinename = mTimelineType.getTitle(this);
        Button timelineTypeButton = (Button) findViewById(R.id.timelineTypeButton);
        timelineTypeButton.setText(timelinename + (TextUtils.isEmpty(mSearchQuery) ? "" : " *"));
    }

    private void updateAccountButtonText() {
        Button selectAccountButton = (Button) findViewById(R.id.selectAccountButton);
        MyAccount ma = MyContextHolder.get().persistentAccounts().getCurrentAccount();
        String accountButtonText = buildAccountButtonText(ma, isTimelineCombined(), getTimelineType());
        selectAccountButton.setText(accountButtonText);
    }

    public static String buildAccountButtonText(MyAccount ma, boolean timelineIsCombined, TimelineTypeEnum timelineType) {
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

    private void updateRightText(String rightText) {
        TextView rightTitle = (TextView) findViewById(R.id.custom_title_right_text);
        rightTitle.setText(rightText);
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
        TimelineTypeEnum timelineTypeNew = TimelineTypeEnum.load(intentNew
                .getStringExtra(IntentExtra.EXTRA_TIMELINE_TYPE.key));
        long newMyAccountUserId = MyContextHolder.get().persistentAccounts().getCurrentAccountUserId();
        if ( mCurrentMyAccountUserId != newMyAccountUserId) {
            mCurrentMyAccountUserId = newMyAccountUserId;
            mSelectedUserId = 0;
        }
        if (timelineTypeNew != TimelineTypeEnum.UNKNOWN) {
            mTimelineType = timelineTypeNew;
            mTimelineIsCombined = intentNew.getBooleanExtra(IntentExtra.EXTRA_TIMELINE_IS_COMBINED.key, mTimelineIsCombined);
            mSearchQuery = notNullString(intentNew.getStringExtra(SearchManager.QUERY));
            mSelectedUserId = intentNew.getLongExtra(IntentExtra.EXTRA_SELECTEDUSERID.key, mSelectedUserId);
        } else {
            parseAppSearchData(intentNew);
        }
        if (mTimelineType == TimelineTypeEnum.UNKNOWN) {
            /* Set default values */
            mTimelineType = TimelineTypeEnum.HOME;
            mSearchQuery = "";
            mSelectedUserId = 0;
        }
        if (mSelectedUserId == 0 && mTimelineType == TimelineTypeEnum.USER) {
            mSelectedUserId = mCurrentMyAccountUserId;
        }

        if (Intent.ACTION_SEND.equals(intentNew.getAction())) {
            shareViaThisApplication(intentNew.getStringExtra(Intent.EXTRA_SUBJECT), 
                    intentNew.getStringExtra(Intent.EXTRA_TEXT),
                    (Uri) intentNew.getParcelableExtra(Intent.EXTRA_STREAM));
        }

        if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
            MyLog.v(this, "processNewIntent; " + mTimelineType + "; accountId=" + mCurrentMyAccountUserId);
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

    private void parseAppSearchData(Intent intentNew) {
        Bundle appSearchData = intentNew.getBundleExtra(SearchManager.APP_DATA);
        if (appSearchData != null) {
            // We use other packaging of the same parameters in onSearchRequested
            TimelineTypeEnum timelineTypeNew = TimelineTypeEnum.load(appSearchData
                    .getString(IntentExtra.EXTRA_TIMELINE_TYPE.key));
            if (timelineTypeNew != TimelineTypeEnum.UNKNOWN) {
                mTimelineType = timelineTypeNew;
                mTimelineIsCombined = appSearchData.getBoolean(IntentExtra.EXTRA_TIMELINE_IS_COMBINED.key, mTimelineIsCombined);
                /* The query itself is still from the Intent */
                mSearchQuery = notNullString(intentNew.getStringExtra(SearchManager.QUERY));
                mSelectedUserId = appSearchData.getLong(IntentExtra.EXTRA_SELECTEDUSERID.key, mSelectedUserId);
                if (!TextUtils.isEmpty(mSearchQuery)
                        && appSearchData.getBoolean(IntentExtra.EXTRA_GLOBAL_SEARCH.key, false)) {
                    MyLog.v(this, "Global search: " + mSearchQuery);
                    setLoading(true);
                    MyServiceManager.sendForegroundCommand(
                            CommandData.searchCommand(
                                    isTimelineCombined()
                                            ? ""
                                            : MyContextHolder.get().persistentAccounts()
                                                    .getCurrentAccountName(),
                                    mSearchQuery));
                }
            }
        }
    }

    private void updateScreen() {
        MyServiceManager.setServiceAvailable();
        TextView selectedUserText = (TextView) findViewById(R.id.selectedUserText);
        ToggleButton combinedTimelineToggle = (ToggleButton) findViewById(R.id.combinedTimelineToggle);
        combinedTimelineToggle.setTextOff(mTimelineType.getPrepositionForNotCombinedTimeline(this));
        combinedTimelineToggle.setChecked(mTimelineIsCombined);
        if (mSelectedUserId != 0 && mSelectedUserId != mCurrentMyAccountUserId) {
            combinedTimelineToggle.setVisibility(View.GONE);
            selectedUserText.setText(MyProvider.userIdToName(mSelectedUserId));
            selectedUserText.setVisibility(View.VISIBLE);
        } else {
            selectedUserText.setVisibility(View.GONE);
            // Show the "Combined" toggle even for one account to see messages, 
            // which are not on the timeline.
            // E.g. messages by users, downloaded on demand.
            combinedTimelineToggle.setVisibility(View.VISIBLE);
        }
        mContextMenu.setAccountUserIdToActAs(0);

        updateActionBarText();
        if (mMessageEditor.isStateLoaded()) {
            mMessageEditor.continueEditingLoadedState();
        } else if (mMessageEditor.isVisible()) {
            // This is done to request focus (if we need this...)
            mMessageEditor.show();
        } else {
            mMessageEditor.updateCreateMessageButton();
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
        mLoaderManager.restartLoader(LOADER_ID, args, this);
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
            nMessages += PAGE_SIZE;
        } else if (nMessages < PAGE_SIZE) {
            nMessages = PAGE_SIZE;
        }
        return nMessages;
    }

    @Override
    public MyLoader<Cursor> onCreateLoader(int id, Bundle args) {
        final String method = "onCreateLoader";
        MyLog.v(this, method + " #" + id);
        TimelineListParameters params = new TimelineListParameters();
        params.loaderCallbacks = this;
        params.timelineType = getTimelineType();
        params.timelineCombined = isTimelineCombined();
        params.myAccountUserId = getCurrentMyAccountUserId();
        params.selectedUserId = getSelectedUserId();
        params.projection = getProjection();
        params.searchQuery = this.mSearchQuery;

        boolean loadOneMorePage = false;
        boolean reQuery = false;
        if (args != null) {
            loadOneMorePage = args.getBoolean(IntentExtra.EXTRA_LOAD_ONE_MORE_PAGE.key);
            reQuery = args.getBoolean(IntentExtra.EXTRA_REQUERY.key);
            params.rowsLimit = args.getInt(IntentExtra.EXTRA_ROWS_LIMIT.key);
        }
        params.loadOneMorePage = loadOneMorePage;
        params.incrementallyLoadingPages = mPositionRestored
                && (getListAdapter() != null)
                && loadOneMorePage;
        params.reQuery = reQuery;
        
        saveSearchQuery();
        prepareQueryForeground(params);

        return new TimelineCursorLoader(params);
    }

    private void saveSearchQuery() {
        if (!TextUtils.isEmpty(mSearchQuery)) {
            // Record the query string in the recent queries
            // of the Suggestion Provider
            SearchRecentSuggestions suggestions = new SearchRecentSuggestions(this,
                    TimelineSearchSuggestionsProvider.AUTHORITY,
                    TimelineSearchSuggestionsProvider.MODE);
            suggestions.saveRecentQuery(mSearchQuery, null);

        }
    }
    
    private void prepareQueryForeground(TimelineListParameters params) {
        params.contentUri = MyProvider.getTimelineSearchUri(mCurrentMyAccountUserId, mTimelineType,
                params.timelineCombined, params.searchQuery);
        Intent intent = getIntent();
        if (!params.contentUri.equals(intent.getData())) {
            intent.setData(params.contentUri);
        }

        if (params.sa.nArgs == 0) {
            // In fact this is needed every time you want to load
            // next page of messages

            /* TODO: Other conditions... */
            params.sa.clear();

            // TODO: Move these selections to the {@link MyProvider} ?!
            switch (mTimelineType) {
                case HOME:
                    // In the Home of the combined timeline we see ALL loaded
                    // messages, even those that we downloaded
                    // not as Home timeline of any Account
                    if (!isTimelineCombined()) {
                        params.sa.addSelection(MyDatabase.MsgOfUser.SUBSCRIBED + " = ?", new String[] {
                                "1"
                        });
                    }
                    break;
                case MENTIONS:
                    params.sa.addSelection(MyDatabase.MsgOfUser.MENTIONED + " = ?", new String[] {
                            "1"
                    });
                    /*
                     * We already figured this out and set {@link MyDatabase.MsgOfUser.MENTIONED}:
                     * sa.addSelection(MyDatabase.Msg.BODY + " LIKE ?" ...
                     */
                    break;
                case FAVORITES:
                    params.sa.addSelection(MyDatabase.MsgOfUser.FAVORITED + " = ?", new String[] {
                            "1"
                    });
                    break;
                case DIRECT:
                    params.sa.addSelection(MyDatabase.MsgOfUser.DIRECTED + " = ?", new String[] {
                            "1"
                    });
                    break;
                case USER:
                    AccountUserIds userIds = new AccountUserIds(isTimelineCombined(), getSelectedUserId());
                    // Reblogs are included also
                    params.sa.addSelection(MyDatabase.Msg.AUTHOR_ID + " " + userIds.getSqlUserIds() 
                            + " OR "
                            + MyDatabase.Msg.SENDER_ID + " " + userIds.getSqlUserIds() 
                            + " OR " 
                            + "("
                            + User.LINKED_USER_ID + " " + userIds.getSqlUserIds() 
                            + " AND "
                            + MyDatabase.MsgOfUser.REBLOGGED + " = 1"
                            + ")",
                            null);
                    break;
                default:
                    break;
            }
        }

        if (!mPositionRestored) {
            // We have to ensure that saved position will be
            // loaded from database into the list
            params.lastItemId = new ListPositionStorage(params).getLast();
        }

        if (params.lastItemId <= 0) {
            int rowsLimit = params.rowsLimit;
            if (rowsLimit < PAGE_SIZE) {
                rowsLimit = PAGE_SIZE;
            }
            params.sortOrder += " LIMIT 0," + rowsLimit;
        }
    }
   
    /** 
     * Table columns to use for the messages content
     */
    protected String[] getProjection() {
        List<String> columnNames = new ArrayList<String>();
        columnNames.add(Msg._ID);
        columnNames.add(User.AUTHOR_NAME);
        columnNames.add(Msg.BODY);
        columnNames.add(Msg.IN_REPLY_TO_MSG_ID);
        columnNames.add(User.IN_REPLY_TO_NAME);
        columnNames.add(User.RECIPIENT_NAME);
        columnNames.add(MsgOfUser.FAVORITED);
        columnNames.add(Msg.CREATED_DATE);
        columnNames.add(User.LINKED_USER_ID);
        if (MyPreferences.showAvatars()) {
            columnNames.add(Msg.AUTHOR_ID);
            columnNames.add(MyDatabase.Download.AVATAR_FILE_NAME);
        }
        if (MyPreferences.showAttachedImages()) {
            columnNames.add(Download.IMAGE_ID);
            columnNames.add(MyDatabase.Download.IMAGE_FILE_NAME);
        }
        if (MyPreferences.getBoolean(
                MyPreferences.KEY_MARK_REPLIES_IN_TIMELINE, false)) {
            columnNames.add(Msg.IN_REPLY_TO_USER_ID);
        }
        return columnNames.toArray(new String[]{});
    }

    @Override
    public void onLoaderReset(MyLoader<Cursor> loader) {
        MyLog.v(this, "onLoaderReset; " + loader);
        setLoading(false);
    }
    
    @Override
    public void onLoadFinished(MyLoader<Cursor> loader, Cursor cursor) {
        final String method = "onLoadFinished"; 
        MyLog.v(this, method);
        TimelineTypeEnum timelineToReload = TimelineTypeEnum.UNKNOWN;
        boolean requestNextPage = false;
        if (loader.isStarted()) {
            if (loader instanceof TimelineCursorLoader) {
                TimelineCursorLoader myLoader = (TimelineCursorLoader) loader;
                changeListContent(myLoader.getParams(), cursor);
                timelineToReload = myLoader.getParams().timelineToReload;
                if (!myLoader.getParams().loadOneMorePage && myLoader.getParams().lastItemId != 0
                        && cursor != null && cursor.getCount() < PAGE_SIZE) {
                    MyLog.v(this, method + "; Requesting next page...");
                    requestNextPage = true;
                }
            } else {
                MyLog.e(this, method + "; Wrong type of loader: " + MyLog.objTagToString(loader));
            }
        }
        setLoading(false);
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
            mNoMoreItems = params.incrementallyLoadingPages &&
                    cursor.getCount() <= getListAdapter().getCount();
            saveListPosition();
            ((CursorAdapter) getListAdapter()).changeCursor(cursor);
            mListParameters = params;
            restoreListPosition();
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
        MyAccount ma = MyContextHolder.get().persistentAccounts().fromUserId(mCurrentMyAccountUserId);
        TimelineTypeEnum timelineTypeForReload = TimelineTypeEnum.HOME;
        long userId = 0;
        switch (mTimelineType) {
            case DIRECT:
            case MENTIONS:
            case PUBLIC:
                timelineTypeForReload = mTimelineType;
                break;
            case USER:
            case FOLLOWING_USER:
                timelineTypeForReload = mTimelineType;
                userId = mSelectedUserId;
                break;
            default:
                break;
        }
        boolean allAccounts = mTimelineIsCombined;
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
        outState.putString(IntentExtra.EXTRA_TIMELINE_TYPE.key, mTimelineType.save());
        mContextMenu.saveState(outState);
        outState.putBoolean(IntentExtra.EXTRA_TIMELINE_IS_COMBINED.key, mTimelineIsCombined);
        outState.putString(SearchManager.QUERY, mSearchQuery);
        outState.putLong(IntentExtra.EXTRA_SELECTEDUSERID.key, mSelectedUserId);

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
                    TimelineTypeEnum timelineTypeNew = mTimelineType;
                    if (mTimelineType == TimelineTypeEnum.USER 
                            &&  (MyContextHolder.get().persistentAccounts().fromUserId(mSelectedUserId) == null)) {
                        /*  "Other User's timeline" vs "My User's timeline" 
                         * Actually we saw messages of the user, who is not MyAccount,
                         * so let's switch to the HOME
                         * TODO: Open "Other User timeline" in a separate Activity?!
                         */
                        timelineTypeNew = TimelineTypeEnum.HOME;
                    }
                    MyContextHolder.get().persistentAccounts().setCurrentAccount(ma);
                    mContextMenu.switchTimelineActivity(timelineTypeNew, mTimelineIsCombined, ma.getUserId());
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
                            0, 0, ma, 
                            mTimelineIsCombined || mCurrentMyAccountUserId != ma.getUserId());
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
                    updateRightText(commandData.getResult().getRemainingHits() + "/"
                            + commandData.getResult().getHourlyLimit());
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
        return mCurrentMyAccountUserId;
    }

    @Override
    public TimelineTypeEnum getTimelineType() {
        return mTimelineType;
    }

    @Override
    public boolean isTimelineCombined() {
        return mTimelineIsCombined;
    }

    @Override
    public long getSelectedUserId() {
        return mSelectedUserId;
    }

    @Override
    public void closeAndGoBack() {
        finish();
    }

    @Override
    public boolean hasOptionsMenu() {
        return true;
    }
}
