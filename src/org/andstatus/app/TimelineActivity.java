/* 
 * Copyright (c) 2011-2013 yvolk (Yuri Volkov), http://yurivolkov.com
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
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.database.Cursor;
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
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ToggleButton;

import org.andstatus.app.MyService.CommandEnum;
import org.andstatus.app.account.AccountSelector;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.account.MyAccount.CredentialsVerificationStatus;
import org.andstatus.app.data.LatestTimelineItem;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyDatabase.Msg;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.PagedCursorAdapter;
import org.andstatus.app.data.TimelineSearchSuggestionProvider;
import org.andstatus.app.data.TweetBinder;
import org.andstatus.app.data.MyDatabase.MsgOfUser;
import org.andstatus.app.data.MyDatabase.TimelineTypeEnum;
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.data.MyPreferences;
import org.andstatus.app.util.InstanceId;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SelectionAndArgs;

/**
 * @author yvolk@yurivolkov.com, torgny.bjers
 */
public class TimelineActivity extends ListActivity implements MyServiceListener, OnScrollListener, OnItemClickListener, ActionableMessageList {
    private static final String TAG = TimelineActivity.class.getSimpleName();

    private static final int DIALOG_ID_TIMELINE_TYPE = 9;

    private static final String KEY_IS_LOADING = "isLoading";
    private static final String KEY_LAST_POSITION = "last_position_";

    /**
     * Visibility of the layout indicates whether Messages are being loaded into the list (asynchronously...)
     * The layout appears at the bottom of the list of messages 
     * when new items are being loaded into the list 
     */
    private LinearLayout loadingLayout;

    private Cursor mCursor;

    private NotificationManager mNM;
    
    /**
     * Msg are being loaded into the list starting from one page. More Msg
     * are being loaded in a case User scrolls down to the end of list.
     */
    private final static int PAGE_SIZE = 100;

    /**
     * Is saved position restored (or some default positions set)?
     */
    private boolean positionRestored = false;
    
    /**
     * The is no more items in the query, so don't try to load more pages
     */
    private boolean noMoreItems = false;
    
    /**
     * For testing purposes
     */
    private int instanceId = 0;
    MyServiceReceiver serviceConnector;

    /**
     * We are going to finish/restart this Activity (e.g. onResume or even onCreate)
     */
    private boolean mIsFinishing = false;

    /**
     * Timeline type
     */
    private TimelineTypeEnum mTimelineType = TimelineTypeEnum.UNKNOWN;

    /**
     * Is the timeline combined? (Timeline shows messages from all accounts)
     */
    private boolean mIsTimelineCombined = false;
    
    /**
     * UserId of the MyAccount, for which we show the activity
     */
    private long mCurrentMyAccountUserId = 0;
    
    /**
     * Selected User for the {@link MyDatabase.TimelineTypeEnum#USER} timeline.
     * This is either User Id of current account OR user id of any other selected user.
     * So it's never == 0 for the {@link MyDatabase.TimelineTypeEnum#USER} timeline
     */
    private long mSelectedUserId = 0;
    
    /**
     * True if this timeline is filtered using query string ("Mentions" are not
     * counted here because they have separate TimelineType)
     */
    private boolean mIsSearchMode = false;

    /**
     * The string is not empty if this timeline is filtered using query string
     * ("Mentions" are not counted here because they have separate TimelineType)
     */
    private String mQueryString = "";

    /**
     * Time when shared preferences where changed
     */
    private long preferencesChangeTime = 0;

    private MessageContextMenu contextMenu;
    private MessageEditor messageEditor;
 
    /** 
     * Table columns to use for the messages content
     */
    private static final String[] PROJECTION = new String[] {
            Msg._ID, User.AUTHOR_NAME, Msg.BODY, Msg.IN_REPLY_TO_MSG_ID, User.IN_REPLY_TO_NAME,
            User.RECIPIENT_NAME,
            MsgOfUser.FAVORITED, Msg.CREATED_DATE,
            User.LINKED_USER_ID
    };

    boolean isLoading() {
        return loadingLayout.getVisibility() == View.VISIBLE;
    }
    
    void setIsLoading(boolean isLoadingNew) {
        if (isLoading() != isLoadingNew) {
            MyLog.v(this, "isLoading set to " + isLoadingNew + ", instanceId=" + instanceId );
            if (isLoadingNew) {
                loadingLayout.setVisibility(View.VISIBLE);
            } else {
                loadingLayout.setVisibility(View.INVISIBLE);
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
        requestWindowFeature(Window.FEATURE_NO_TITLE);    // Before loading the content view
        super.onCreate(savedInstanceState);
        if (instanceId == 0) {
            instanceId = InstanceId.next();
        } else {
            MyLog.d(TAG, "onCreate reuse the same instanceId=" + instanceId);
        }

        preferencesChangeTime = MyContextHolder.initialize(this, this);
        MyContextHolder.upgradeIfNeeded(this);
        
        if (MyLog.isLoggable(TAG, MyLog.DEBUG)) {
            MyLog.d(TAG, "onCreate instanceId=" + instanceId + " , preferencesChangeTime=" + preferencesChangeTime);
        }

        if (!mIsFinishing) {
            boolean helpAsFirstActivity = false;
            boolean showChangeLog = false;
            if (!MyContextHolder.get().isReady()) {
                MyLog.i(this, "Context is not ready");
                helpAsFirstActivity = true;
            } else if (MyPreferences.shouldSetDefaultValues()) {
                MyLog.i(this, "We are running the Application for the very first time?");
                helpAsFirstActivity = true;
            } else if (MyContextHolder.get().persistentAccounts().getCurrentAccount() == null) {
                MyLog.i(this, "No current MyAccount");
                helpAsFirstActivity = true;
            } 
            
            // Show Change Log after update
            try {
                if (MyPreferences.checkAndUpdateLastOpenedAppVersion(this)) {
                    showChangeLog = true;                    
                }
            } catch (NameNotFoundException e) {
                MyLog.e(this, "Unable to obtain package information", e);
            }

            if (helpAsFirstActivity || showChangeLog) {
                HelpActivity.startFromActivity(this, helpAsFirstActivity, showChangeLog);
            }
        }
        if (mIsFinishing) {
            return;
        }

        mCurrentMyAccountUserId = MyContextHolder.get().persistentAccounts().getCurrentAccountUserId();
        serviceConnector = new MyServiceReceiver(this);
        
        MyPreferences.loadTheme(TAG, this);

        setContentView(R.layout.timeline);

        ViewGroup messageListParent = (ViewGroup) findViewById(R.id.messageListParent);
        // We use "this" as a context, otherwise custom styles are not recognized...
        LayoutInflater inflater = LayoutInflater.from(this);
        ViewGroup actionsView = (ViewGroup) inflater.inflate(R.layout.timeline_actions, null);
        messageListParent.addView(actionsView, 0);

        contextMenu = new MessageContextMenu(this);
        
        messageEditor = new MessageEditor(this);
        // TODO: Maybe this should be a parameter
        messageEditor.hide();

        boolean isInstanceStateRestored = false;
        boolean isLoadingNew = false;
        if (savedInstanceState != null) {
            TimelineTypeEnum timelineTypeNew = TimelineTypeEnum.load(savedInstanceState
                    .getString(IntentExtra.EXTRA_TIMELINE_TYPE.key));
            if (timelineTypeNew != TimelineTypeEnum.UNKNOWN) {
                isInstanceStateRestored = true;
                mTimelineType = timelineTypeNew;
                messageEditor.loadState(savedInstanceState);
                if (savedInstanceState.containsKey(KEY_IS_LOADING)) {
                    isLoadingNew = savedInstanceState.getBoolean(KEY_IS_LOADING);
                }
                contextMenu.loadState(savedInstanceState);
                if (savedInstanceState.containsKey(IntentExtra.EXTRA_TIMELINE_IS_COMBINED.key)) {
                    mIsTimelineCombined = savedInstanceState.getBoolean(IntentExtra.EXTRA_TIMELINE_IS_COMBINED.key);
                }
                if (savedInstanceState.containsKey(SearchManager.QUERY)) {
                    mQueryString = savedInstanceState.getString(SearchManager.QUERY);
                }
                if (savedInstanceState.containsKey(IntentExtra.EXTRA_SELECTEDUSERID.key)) {
                    mSelectedUserId = savedInstanceState.getLong(IntentExtra.EXTRA_SELECTEDUSERID.key);
                }
            }
        }

        // Set up notification manager
        mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        
        // Create list footer to show the progress of message loading
        loadingLayout = (LinearLayout) inflater.inflate(R.layout.item_loading, null);
        getListView().addFooterView(loadingLayout);
        setIsLoading(isLoadingNew);

        // Attach listeners to the message list
        getListView().setOnScrollListener(this);
        getListView().setOnCreateContextMenuListener(contextMenu);
        getListView().setOnItemClickListener(this);

        Button accountButton = (Button) findViewById(R.id.selectAccountButton);
        accountButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(TimelineActivity.this, AccountSelector.class);
                startActivityForResult(i, ActivityRequestCode.SELECT_ACCOUNT.id);
            }
        });

        if (!isInstanceStateRestored) {
            mIsTimelineCombined = MyPreferences.getDefaultSharedPreferences().getBoolean(MyPreferences.KEY_TIMELINE_IS_COMBINED, false);
            processNewIntent(getIntent());
        }
        updateThisOnChangedParameters();
    }

    /**
     * Switch combined timeline on/off
     * @param view combinedTimelineToggle
     */
    public void onCombinedTimelineToggle(View view) {
        if (view instanceof android.widget.ToggleButton) {
            boolean on = ((android.widget.ToggleButton) view).isChecked();
            MyPreferences.getDefaultSharedPreferences().edit().putBoolean(MyPreferences.KEY_TIMELINE_IS_COMBINED, on).commit();
            contextMenu.switchTimelineActivity(mTimelineType, on, mCurrentMyAccountUserId);
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
        Bundle appSearchData = new Bundle();
        appSearchData.putString(IntentExtra.EXTRA_TIMELINE_TYPE.key, mTimelineType.save());
        appSearchData.putBoolean(IntentExtra.EXTRA_TIMELINE_IS_COMBINED.key, mIsTimelineCombined);
        appSearchData.putLong(IntentExtra.EXTRA_SELECTEDUSERID.key, mSelectedUserId);
        startSearch(null, false, appSearchData, false);
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        MyLog.v(this, "onResume, instanceId=" + instanceId);
        if (!mIsFinishing) {
            if (MyContextHolder.get().persistentAccounts().getCurrentAccount() != null) {
                long preferencesChangeTimeNew = MyContextHolder.initialize(this, this);
                if (preferencesChangeTimeNew != preferencesChangeTime) {
                    MyLog.v(this, "Restarting this Activity to apply all new changes of preferences");
                    finish();
                    contextMenu.switchTimelineActivity(mTimelineType, mIsTimelineCombined, mSelectedUserId);
                }
            } else { 
                MyLog.v(this, "Finishing this Activity because there is no Account selected");
                finish();
            }
        }
        if (!mIsFinishing) {
            serviceConnector.registerReceiver(this);
            if (!isLoading()) {
                restoreListPosition();
            }
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
        long firstVisibleItemId = 0;
        long lastRetrievedItemId = 0;
        int firstScrollPos = 0;
        int lastScrollPos = -1;
        PositionStorage ps = new PositionStorage();

        firstScrollPos = getListView().getFirstVisiblePosition();
        android.widget.ListAdapter la = getListView().getAdapter();
        if (la == null) {
            MyLog.v(this, "Position wasn't saved - no adapters yet");
            return;
        }
        if (firstScrollPos > la.getCount() - 2) {
            // Skip footer
            firstScrollPos = la.getCount() - 2;
        }
        if (firstScrollPos >= 0) {
            firstVisibleItemId = la.getItemId(firstScrollPos);
            // We will load one more "page of tweets" below (older) current top item
            lastScrollPos = firstScrollPos + PAGE_SIZE;
            if (lastScrollPos > la.getCount() - 2) {
                if (firstScrollPos > PAGE_SIZE - 2) {
                    lastScrollPos = la.getCount() - 2;
                } else {
                    lastScrollPos = -1;
                }
            }
            if (lastScrollPos >= 0) {
                lastRetrievedItemId = la.getItemId(lastScrollPos);
            }
        }

        if (firstVisibleItemId <= 0) {
            MyLog.v(this, "Position wasn't saved \"" + ps.accountGuid + "\"; " + ps.keyFirst);
            return;
        }

        ps.sp.edit().putLong(ps.keyFirst, firstVisibleItemId)
                .putLong(ps.keyLast, lastRetrievedItemId).commit();
        if (mIsSearchMode) {
            // Remember query string for which the position was saved
            ps.sp.edit().putString(ps.keyQueryString, mQueryString)
                    .commit();
        }

        if (MyLog.isLoggable(TAG, MyLog.VERBOSE)) {
            MyLog.v(TAG, "Position saved    \"" + ps.accountGuid + "\"; " + ps.keyFirst + "="
                    + firstVisibleItemId + "; index=" + firstScrollPos + "; lastId="
                    + lastRetrievedItemId + "; index=" + lastScrollPos);
        }
    }

    private void forgetListPosition() {
        PositionStorage ps = new PositionStorage();
        ps.sp.edit().remove(ps.keyFirst).remove(ps.keyLast)
                .remove(ps.keyQueryString).commit();
        if (MyLog.isLoggable(TAG, MyLog.VERBOSE)) {
            MyLog.v(TAG, "Position forgot   \"" + ps.accountGuid + "\"; " + ps.keyFirst);
        }
    }
    
    /**
     * Restore (First visible item) position saved for this user and for this type of timeline
     */
    private void restoreListPosition() {
        PositionStorage ps = new PositionStorage();
        boolean loaded = false;
        long firstItemId = -3;
        try {
            int scrollPos = -1;
            firstItemId = ps.getSavedPosition(false);
            if (firstItemId > 0) {
                scrollPos = listPosForId(firstItemId);
            }
            if (scrollPos >= 0) {
                getListView().setSelectionFromTop(scrollPos, 0);
                loaded = true;
                if (MyLog.isLoggable(TAG, MyLog.VERBOSE)) {
                    MyLog.v(TAG, "Position restored \"" + ps.accountGuid + "\"; " + ps.keyFirst + "="
                            + firstItemId +"; index=" + scrollPos);
                }
            } else {
                // There is no stored position
                if (mIsSearchMode) {
                    // In search mode start from the most recent tweet!
                    scrollPos = 0;
                } else {
                    scrollPos = getListView().getCount() - 2;
                }
                if (scrollPos >= 0) {
                    setSelectionAtBottom(scrollPos);
                }
            }
        } catch (Exception e) {
            MyLog.v(TAG, "Position error", e);
            loaded = false;
        }
        if (!loaded) {
            if (MyLog.isLoggable(TAG, MyLog.VERBOSE)) {
                MyLog.v(TAG, "Didn't restore position \"" + ps.accountGuid + "\"; " + ps.keyFirst + "="
                        + firstItemId);
            }
            forgetListPosition();
        }
        positionRestored = true;
    }

    private void setSelectionAtBottom(int scrollPos) {
        if (MyLog.isLoggable(TAG, MyLog.VERBOSE)) {
            MyLog.v(TAG, "setSelectionAtBottom, 1");
        }
        int viewHeight = getListView().getHeight();
        int childHeight;
        childHeight = 30;
        int y = viewHeight - childHeight;
        if (MyLog.isLoggable(TAG, MyLog.VERBOSE)) {
            MyLog.v(TAG, "set position of last item to " + y + "px");
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
        if (MyLog.isLoggable(TAG, MyLog.VERBOSE)) {
            MyLog.v(TAG, "item count: " + itemCount);
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
        super.onContentChanged();
        if (MyLog.isLoggable(TAG, MyLog.DEBUG)) {
            MyLog.d(TAG, "Content changed");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (MyLog.isLoggable(TAG, MyLog.VERBOSE)) {
            MyLog.v(TAG, "onPause, instanceId=" + instanceId);
        }
        serviceConnector.unregisterReceiver(this);

        if (positionRestored) {
            // Get rid of the "fast scroll thumb"
            ((ListView) findViewById(android.R.id.list)).setFastScrollEnabled(false);
            clearNotifications();
            if (!isLoading()) {
                saveListPosition();
            }
        }        
        positionRestored = false;
    }
   
    /**
     *  Cancel notifications of loading timeline
     *  They were set in 
     *  @see org.andstatus.app.MyService.CommandExecutor#notifyNewTweets(int, CommandEnum)
     */
    private void clearNotifications() {
        try {
            // TODO: Check if there are any notifications
            // and if none than don't waist time for this:

            mNM.cancel(MyService.CommandEnum.NOTIFY_HOME_TIMELINE.ordinal());
            mNM.cancel(MyService.CommandEnum.NOTIFY_MENTIONS.ordinal());
            mNM.cancel(MyService.CommandEnum.NOTIFY_DIRECT_MESSAGE.ordinal());

            // Reset notifications on AppWidget(s)
            Intent intent = new Intent(MyService.ACTION_APPWIDGET_UPDATE);
            intent.putExtra(IntentExtra.EXTRA_MSGTYPE.key, MyService.CommandEnum.NOTIFY_CLEAR.save());
            sendBroadcast(intent);
        } finally {
            // Nothing yet...
        }
    }

    @Override
    public void onDestroy() {
        MyLog.v(this,"onDestroy, instanceId=" + instanceId);
        super.onDestroy();
        if (mCursor != null && !mCursor.isClosed()) {
            mCursor.close();
        }
        if (serviceConnector != null) {
            serviceConnector.unregisterReceiver(this);
        }
    }

    @Override
    public void finish() {
        MyLog.v(this,"Finish requested" + (mIsFinishing ? ", already finishing" : "") + ", instanceId=" + instanceId);
        if (!mIsFinishing) {
            mIsFinishing = true;
        }
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
        String[] timelines = {
                getString(MyDatabase.TimelineTypeEnum.HOME.resId()),
                getString(MyDatabase.TimelineTypeEnum.FAVORITES.resId()),
                getString(MyDatabase.TimelineTypeEnum.MENTIONS.resId()),
                getString(MyDatabase.TimelineTypeEnum.DIRECT.resId()),
                getString(MyDatabase.TimelineTypeEnum.USER.resId()),
                getString(MyDatabase.TimelineTypeEnum.FOLLOWING_USER.resId())
        };
        builder.setItems(timelines, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // The 'which' argument contains the index position of the selected item
                switch (which) {
                    case 0:
                        contextMenu.switchTimelineActivity(MyDatabase.TimelineTypeEnum.HOME, mIsTimelineCombined, mCurrentMyAccountUserId);
                        break;

                    case 1:
                        contextMenu.switchTimelineActivity(MyDatabase.TimelineTypeEnum.FAVORITES, mIsTimelineCombined, mCurrentMyAccountUserId);
                        break;

                    case 2:
                        contextMenu.switchTimelineActivity(MyDatabase.TimelineTypeEnum.MENTIONS, mIsTimelineCombined, mCurrentMyAccountUserId);
                        break;

                    case 3:
                        contextMenu.switchTimelineActivity(MyDatabase.TimelineTypeEnum.DIRECT, mIsTimelineCombined, mCurrentMyAccountUserId);
                        break;

                    case 4:
                        contextMenu.switchTimelineActivity(MyDatabase.TimelineTypeEnum.USER, mIsTimelineCombined, mCurrentMyAccountUserId);
                        break;

                    case 5:
                        contextMenu.switchTimelineActivity(MyDatabase.TimelineTypeEnum.FOLLOWING_USER, mIsTimelineCombined, mCurrentMyAccountUserId);
                        break;
                    default:
                        break;
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

        Intent intent = new Intent(null, getIntent().getData());
        intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
        menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0, new ComponentName(this,
                TimelineActivity.class), null, intent, 0, null);
        MyAccount ma = MyContextHolder.get().persistentAccounts().getCurrentAccount();
        if (ma != null && ma.getCredentialsVerified() != CredentialsVerificationStatus.SUCCEEDED) {
            MenuItem item = menu.findItem(R.id.reload_menu_item);
            item.setEnabled(false);
            item.setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        contextMenu.onContextItemSelected(item);
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
    public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
        if (id <= 0) {
            if (MyLog.isLoggable(TAG, MyLog.VERBOSE)) {
                MyLog.v(TAG, "onItemClick, position=" + position + "; id=" + id + "; view=" + view);
            }
            return;
        }
        long linkedUserId = getLinkedUserIdFromCursor(position);
        MyAccount ma = MyContextHolder.get().persistentAccounts().getAccountWhichMayBeLinkedToThisMessage(id, linkedUserId,
                mCurrentMyAccountUserId);
        if (ma == null) {
            MyLog.e(this, "Account for the message " + id + " was not found");
            return;
        }
        
        if (MyLog.isLoggable(TAG, MyLog.VERBOSE)) {
            MyLog.v(TAG, "onItemClick, position=" + position + "; id=" + id + "; view=" + view
                    + "; linkedUserId=" + linkedUserId + " account=" + ma.getAccountName());
        }
        Uri uri = MyProvider.getTimelineMsgUri(ma.getUserId(), mTimelineType, true, id);
        String action = getIntent().getAction();
        if (Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action)) {
            if (MyLog.isLoggable(TAG, MyLog.DEBUG)) {
                MyLog.d(TAG, "onItemClick, setData=" + uri);
            }
            setResult(RESULT_OK, new Intent().setData(uri));
        } else {
            if (MyLog.isLoggable(TAG, MyLog.DEBUG)) {
                MyLog.d(TAG, "onItemClick, startActivity=" + uri);
            }
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        }
    }

    /**
     * @param position Of current item in the {@link #mCursor}
     * @return id of the User linked to this message. This link reflects the User's timeline 
     * or an Account which was used to retrieved the message
     */
    @Override
    public long getLinkedUserIdFromCursor(int position) {
        long userId = 0;
        try {
            if (mCursor != null && !mCursor.isClosed()) {
                mCursor.moveToPosition(position);
                int columnIndex = mCursor.getColumnIndex(User.LINKED_USER_ID);
                if (columnIndex > -1) {
                    userId = mCursor.getLong(columnIndex);
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
        if (!noMoreItems && positionRestored && !isLoading()) {
            // Idea from http://stackoverflow.com/questions/1080811/android-endless-list
            boolean loadMore = (visibleItemCount > 0) && (firstVisibleItem > 0)
                    && (firstVisibleItem + visibleItemCount >= totalItemCount);
            if (loadMore) {
                MyLog.d(TAG, "Start Loading more items, rows=" + totalItemCount);
                saveListPosition();
                setIsLoading(true);
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

    /**
     * Updates the activity title.
     * Sets the title with a left and right title.
     * 
     * @param rightText Right title part
     */
    public void updateActionBar(String rightText) {
        String timelinename = getString(mTimelineType.resId());
        Button timelineTypeButton = (Button) findViewById(R.id.timelineTypeButton);
        timelineTypeButton.setText(timelinename + (mIsSearchMode ? " *" : ""));
        
        // Show current account info on the left button
        Button selectAccountButton = (Button) findViewById(R.id.selectAccountButton);
        MyAccount ma = MyContextHolder.get().persistentAccounts().getCurrentAccount();
        String accountName = ma.shortestUniqueAccountName();
        if (ma.getCredentialsVerified() != CredentialsVerificationStatus.SUCCEEDED) {
            accountName = "(" + accountName + ")";
        }
        selectAccountButton.setText(accountName);
        
        TextView rightTitle = (TextView) findViewById(R.id.custom_title_right_text);
        rightTitle.setText(rightText);
    }

   public void updateActionBar() {
        updateActionBar("");
    }

    /**
     * Retrieve the text that is currently in the editor.
     * 
     * @return Text currently in the editor
     */
    protected CharSequence getSavedText() {
        return ((EditText) findViewById(R.id.messageBodyEditText)).getText();
    }

    /**
     * Set the text in the text editor.
     * 
     * @param text
     */
    protected void setSavedText(CharSequence text) {
        ((EditText) findViewById(R.id.messageBodyEditText)).setText(text);
    }

    /**
     * Check to see if the system has a hardware keyboard.
     * 
     * @return
     */
    protected boolean hasHardwareKeyboard() {
        Configuration c = getResources().getConfiguration();
        switch (c.keyboard) {
            case Configuration.KEYBOARD_12KEY:
            case Configuration.KEYBOARD_QWERTY:
                return true;
            default:
                return false;
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        MyContextHolder.initialize(this, this);
        if (MyLog.isLoggable(TAG, MyLog.VERBOSE)) {
            MyLog.v(TAG, "onNewIntent, instanceId=" + instanceId);
        }
        processNewIntent(intent);
        updateThisOnChangedParameters();
    }

    /**
     * Change the Activity according to the new intent. This procedure is done
     * both {@link #onCreate(Bundle)} and {@link #onNewIntent(Intent)}
     * 
     * @param intentNew
     */
    private void processNewIntent(Intent intentNew) {
        TimelineTypeEnum timelineTypeNew = TimelineTypeEnum.load(intentNew
                .getStringExtra(IntentExtra.EXTRA_TIMELINE_TYPE.key));
        if (timelineTypeNew != TimelineTypeEnum.UNKNOWN) {
            mTimelineType = timelineTypeNew;
            mIsTimelineCombined = intentNew.getBooleanExtra(IntentExtra.EXTRA_TIMELINE_IS_COMBINED.key, mIsTimelineCombined);
            mQueryString = intentNew.getStringExtra(SearchManager.QUERY);
            mSelectedUserId = intentNew.getLongExtra(IntentExtra.EXTRA_SELECTEDUSERID.key, mSelectedUserId);
        } else {
            Bundle appSearchData = intentNew.getBundleExtra(SearchManager.APP_DATA);
            if (appSearchData != null) {
                // We use other packaging of the same parameters in onSearchRequested
                timelineTypeNew = TimelineTypeEnum.load(appSearchData
                        .getString(IntentExtra.EXTRA_TIMELINE_TYPE.key));
                if (timelineTypeNew != TimelineTypeEnum.UNKNOWN) {
                    mTimelineType = timelineTypeNew;
                    mIsTimelineCombined = appSearchData.getBoolean(IntentExtra.EXTRA_TIMELINE_IS_COMBINED.key, mIsTimelineCombined);
                    /* The query itself is still from the Intent */
                    mQueryString = intentNew.getStringExtra(SearchManager.QUERY);
                    mSelectedUserId = appSearchData.getLong(IntentExtra.EXTRA_SELECTEDUSERID.key, mSelectedUserId);
                }
            }
        }
        if (mTimelineType == TimelineTypeEnum.UNKNOWN) {
            /* Set default values */
            mTimelineType = TimelineTypeEnum.HOME;
            mQueryString = "";
            mSelectedUserId = 0;
        }
        mCurrentMyAccountUserId = MyContextHolder.get().persistentAccounts().getCurrentAccountUserId();
        if (mSelectedUserId == 0 && mTimelineType == TimelineTypeEnum.USER) {
            mSelectedUserId = mCurrentMyAccountUserId;
        }

        // Are we supposed to send a tweet?
        if (Intent.ACTION_SEND.equals(intentNew.getAction())) {
            String textInitial = "";
            // This is Title of the page is Sharing Web page
            String subject = intentNew.getStringExtra(Intent.EXTRA_SUBJECT);
            // This is URL of the page if sharing web page
            String text = intentNew.getStringExtra(Intent.EXTRA_TEXT);
            if (!TextUtils.isEmpty(subject)) {
                textInitial += subject;
            }
            if (!TextUtils.isEmpty(text)) {
                if (!TextUtils.isEmpty(textInitial)) {
                    textInitial += " ";
                }
                textInitial += text;
            }
            MyLog.v(this, "Intent.ACTION_SEND '" + textInitial +"'");
            messageEditor.startEditingMessage(textInitial, 0, 0, MyContextHolder.get().persistentAccounts().getCurrentAccount(), mIsTimelineCombined);
        }

        if (MyLog.isLoggable(TAG, MyLog.VERBOSE)) {
            MyLog.v(TAG, "processNewIntent; type=\"" + mTimelineType.save() + "\"");
        }
    }

    private void updateThisOnChangedParameters() {
        MyServiceManager.setServiceAvailable();
        mIsSearchMode = !TextUtils.isEmpty(mQueryString);
        if (mIsSearchMode) {
            // Let's check if last time we saved position for the same query
            // string

        } else {
            mQueryString = "";
        }

        TextView selectedUserText = (TextView) findViewById(R.id.selectedUserText);
        ToggleButton combinedTimelineToggle = (ToggleButton) findViewById(R.id.combinedTimelineToggle);
        combinedTimelineToggle.setChecked(mIsTimelineCombined);
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
        noMoreItems = false;
        contextMenu.setAccountUserIdToActAs(0);

        updateActionBar();
        if (messageEditor.isStateLoaded()) {
            messageEditor.continueEditingLoadedState();
        } else if (messageEditor.isVisible()) {
            // This is done to request focus (if we need this...)
            messageEditor.show();
        } else {
            messageEditor.updateCreateMessageButton();
        }
        
        queryListData(false);
    }
    
    /**
     * This is to prevent parallel requests to query data
     */
    private boolean queryListDataInProgress = false;
    /**
     * Clean after successful or failed operation
     */
    private void queryListDataEnded(boolean doRestorePosition) {
        if (!mIsFinishing) {
            if (doRestorePosition) {
                restoreListPosition();
            }
            // Do this after restoring position to avoid repeated loading from onScroll event
            setIsLoading(false);
        }
        queryListDataInProgress = false;
    }
    /**
     * Prepare query to the ContentProvider (to the database) and load the visible List of
     * messages with this data
     * This is done asynchronously.
     * This method should be called from UI thread only.
     * 
     * @param otherThread This method is being accessed from other thread
     * @param loadOneMorePage true - load one more page of messages, false - reload the same page
     */
    protected void queryListData(boolean loadOneMorePageIn) {
        final boolean loadOneMorePage = loadOneMorePageIn;

        /**
         * Here we do all the work 
         * @author yvolk@yurivolkov.com
         */
        class AsyncQueryListData extends AsyncTask<Void, Void, Void>{
            long startTime = System.nanoTime();
            Uri contentUri = MyProvider.getTimelineUri(mCurrentMyAccountUserId, mTimelineType,
                    mIsTimelineCombined);

            SelectionAndArgs sa = new SelectionAndArgs();
            String sortOrder = MyDatabase.Msg.DEFAULT_SORT_ORDER;
            
            Cursor cursor;

            @Override
            protected void onPreExecute() {
                Intent intent = getIntent();

                if (MyLog.isLoggable(TAG, MyLog.VERBOSE)) {
                    MyLog.v(TAG, "queryListData; queryString=\"" + mQueryString + "\"; TimelineType="
                            + mTimelineType.save()
                            + "; isCombined=" + (mIsTimelineCombined ? "yes" : "no"));
                }

                // Id of the last (oldest) tweet to retrieve
                long lastItemId = -1;

                if (!TextUtils.isEmpty(mQueryString)) {
                    // Record the query string in the recent queries
                    // of the Suggestion Provider
                    SearchRecentSuggestions suggestions = new SearchRecentSuggestions(TimelineActivity.this,
                            TimelineSearchSuggestionProvider.AUTHORITY,
                            TimelineSearchSuggestionProvider.MODE);
                    suggestions.saveRecentQuery(mQueryString, null);

                    contentUri = MyProvider.getTimelineSearchUri(mCurrentMyAccountUserId, mTimelineType,
                            mIsTimelineCombined, mQueryString);
                }

                if (!contentUri.equals(intent.getData())) {
                    intent.setData(contentUri);
                }

                if (sa.nArgs == 0) {
                    // In fact this is needed every time you want to load
                    // next page of messages

                    /* TODO: Other conditions... */
                    sa.clear();

                    // TODO: Move these selections to the {@link MyProvider} ?!
                    switch (mTimelineType) {
                        case HOME:
                            // In the Home of the combined timeline we see ALL loaded
                            // messages, even those that we downloaded
                            // not as Home timeline of any Account
                            if (!mIsTimelineCombined) {
                                sa.addSelection(MyDatabase.MsgOfUser.SUBSCRIBED + " = ?", new String[] {
                                        "1"
                                });
                            }
                            break;
                        case MENTIONS:
                            sa.addSelection(MyDatabase.MsgOfUser.MENTIONED + " = ?", new String[] {
                                    "1"
                            });
                            /*
                             * We already figured this out and set {@link MyDatabase.MsgOfUser.MENTIONED}:
                             * sa.addSelection(MyDatabase.Msg.BODY + " LIKE ?" ...
                             */
                            break;
                        case FAVORITES:
                            sa.addSelection(MyDatabase.MsgOfUser.FAVORITED + " = ?", new String[] {
                                    "1"
                            });
                            break;
                        case DIRECT:
                            sa.addSelection(MyDatabase.MsgOfUser.DIRECTED + " = ?", new String[] {
                                    "1"
                            });
                            break;
                        case USER:
                            // Reblogs are included also
                            sa.addSelection(MyDatabase.Msg.AUTHOR_ID + " = ? OR "
                                    + MyDatabase.Msg.SENDER_ID + " = ? OR "
                                    + "("
                                    + User.LINKED_USER_ID + " = ? AND "
                                    + MyDatabase.MsgOfUser.REBLOGGED + " = 1"
                                    + ")",
                                    new String[] {
                                            Long.toString(mSelectedUserId), Long.toString(mSelectedUserId),
                                            Long.toString(mSelectedUserId)
                                    });
                            break;
                        default:
                            break;
                    }
                }

                if (!positionRestored) {
                    // We have to ensure that saved position will be
                    // loaded from database into the list
                    lastItemId = new PositionStorage().getSavedPosition(true);
                }

                int nMessages = 0;
                if (mCursor != null && !mCursor.isClosed()) {
                    if (positionRestored) {
                        // If position is NOT loaded - this cursor is from other
                        // timeline/search
                        // and we shouldn't care how much rows are there.
                        nMessages = mCursor.getCount();
                    }
                }

                if (lastItemId > 0) {
                    sa.addSelection(MyDatabase.MSG_TABLE_NAME + "." + MyDatabase.Msg.SENT_DATE + " >= ?",
                            new String[] {
                                String.valueOf(MyProvider.msgIdToLongColumnValue(MyDatabase.Msg.SENT_DATE, lastItemId))
                            });
                } else {
                    if (loadOneMorePage) {
                        nMessages += PAGE_SIZE;
                    } else if (nMessages < PAGE_SIZE) {
                        nMessages = PAGE_SIZE;
                    }
                    sortOrder += " LIMIT 0," + nMessages;
                }
            }

            @Override
            protected Void doInBackground(Void... params) {
                for (int attempt=0; attempt<3; attempt++) {
                    try {
                        cursor = getContentResolver().query(contentUri, PROJECTION, sa.selection,
                                sa.selectionArgs, sortOrder);
                        break;
                    } catch (IllegalStateException e) {
                        MyLog.d(TAG, "Attempt " + attempt + " to prepare cursor", e);
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e2) {
                            MyLog.d(TAG, "Attempt " + attempt + " to prepare cursor was interrupted", e2);
                            break;
                        }
                    }
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                boolean doRestorePosition = true;
                if (cursor != null && !mIsFinishing) {
                    boolean cursorSet = false;
                    if (positionRestored && (getListAdapter() != null)) {
                        if (loadOneMorePage) {
                            // This will prevent continuous loading...
                            if (cursor.getCount() > getListAdapter().getCount()) {
                                MyLog.v(this, "On changing Cursor");
                                ((SimpleCursorAdapter) getListAdapter()).changeCursor(cursor);
                                mCursor = cursor;
                            } else {
                                noMoreItems = true;
                                doRestorePosition = false;
                                // We don't need this cursor: assuming it is the same as existing
                                cursor.close();
                            }
                            cursorSet = true; 
                        }
                    }
                    if (!cursorSet) {
                        if (mCursor != null && !mCursor.isClosed()) {
                            mCursor.close();
                        }
                        mCursor = cursor;
                        createAdapters();
                    }
                }
                
                if (MyLog.isLoggable(TAG, MyLog.VERBOSE)) {
                    String cursorInfo = "cursor - ??";
                    if (cursor == null) {
                        cursorInfo = "new cursor is null";
                    } else if (mCursor == null) {
                        cursorInfo = "cursor is null";
                    } else if (mCursor.isClosed()) {
                        cursorInfo = "cursor is Closed";
                    } else {
                        cursorInfo = mCursor.getCount() + " rows";
                    }
                    MyLog.v(TAG, "queryListData; ended, " + cursorInfo + ", " + Double.valueOf((System.nanoTime() - startTime)/1.0E6).longValue() + " ms");
                }
                
                queryListDataEnded(doRestorePosition);
                
                if (!loadOneMorePage) {
                    switch (mTimelineType) {
                        case USER:
                        case FOLLOWING_USER:
                            // This timeline doesn't update automatically so let's do it now if necessary
                            LatestTimelineItem latestTimelineItem = new LatestTimelineItem(mTimelineType, mSelectedUserId);
                            if (latestTimelineItem.isTimeToAutoUpdate()) {
                                manualReload(false);
                            }
                            break;
                        default:
                            if ( MyProvider.userIdToLongColumnValue(User.HOME_TIMELINE_DATE, mCurrentMyAccountUserId) == 0) {
                                // This is supposed to be a one time task.
                                manualReload(true);
                            } 
                            break;
                    }
                }
            }
        }
        
        if (queryListDataInProgress) {
            MyLog.v(this, "queryListData is already in progress, skipping this request");
            return;
        }

        try {
            queryListDataInProgress = true;
            setIsLoading(true);
            new AsyncQueryListData().execute();
        } catch (Exception e) {
            MyLog.e(this, "Error during AsyncQueryListData", e);
            queryListDataEnded(!loadOneMorePage);
        }
    }
    
    /**
     * Ask a service to load data from the Internet for the selected TimelineType
     * Only newer messages (newer than last loaded) are being loaded from the
     * Internet, older ones are not being reloaded.
     */
    protected void manualReload(boolean allTimelineTypes) {
        MyAccount ma = MyContextHolder.get().persistentAccounts().fromUserId(mCurrentMyAccountUserId);
        MyDatabase.TimelineTypeEnum timelineType = TimelineTypeEnum.HOME;
        long userId = 0;
        switch (mTimelineType) {
            case DIRECT:
            case MENTIONS:
                timelineType = mTimelineType;
                break;
            case USER:
            case FOLLOWING_USER:
                timelineType = mTimelineType;
                userId = mSelectedUserId;
                break;
            default:
                break;
        }
        boolean allAccounts = mIsTimelineCombined;
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

        setIsLoading(true);
        MyServiceManager.sendCommand(
                new CommandData(CommandEnum.FETCH_TIMELINE,
                        allAccounts ? "" : ma.getAccountName(), timelineType, userId)
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
        outState.putBoolean(KEY_IS_LOADING, isLoading());

        messageEditor.saveState(outState);
        outState.putString(IntentExtra.EXTRA_TIMELINE_TYPE.key, mTimelineType.save());
        contextMenu.saveState(outState);
        outState.putBoolean(IntentExtra.EXTRA_TIMELINE_IS_COMBINED.key, mIsTimelineCombined);
        outState.putString(SearchManager.QUERY, mQueryString);
        outState.putLong(IntentExtra.EXTRA_SELECTEDUSERID.key, mSelectedUserId);

        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (ActivityRequestCode.fromId(requestCode)) {
            case SELECT_ACCOUNT:
                if (resultCode == RESULT_OK) {
                    MyAccount ma = MyContextHolder.get().persistentAccounts().fromAccountName(data.getStringExtra(IntentExtra.EXTRA_ACCOUNT_NAME.key));
                    if (ma != null) {
                        MyLog.v(this, "Restarting the activity for the selected account " + ma.getAccountName());
                        finish();
                        TimelineTypeEnum timelineTypeNew = mTimelineType;
                        if (mTimelineType == TimelineTypeEnum.USER && mSelectedUserId != ma.getUserId()) {
                            /*  "Other User's timeline" vs "My User's timeline" 
                             * Actually we saw messages of other user, not of (previous) MyAccount,
                             * so let's switch to the HOME
                             * TODO: Open "Other User timeline" in a separate Activity
                             */
                            timelineTypeNew = TimelineTypeEnum.HOME;
                        }
                        MyContextHolder.get().persistentAccounts().setCurrentAccount(ma);
                        contextMenu.switchTimelineActivity(timelineTypeNew, mIsTimelineCombined, ma.getUserId());
                    }
                }
                break;
            case SELECT_ACCOUNT_TO_ACT_AS:
                if (resultCode == RESULT_OK) {
                    MyAccount ma = MyContextHolder.get().persistentAccounts().fromAccountName(data.getStringExtra(IntentExtra.EXTRA_ACCOUNT_NAME.key));
                    if (ma != null) {
                        contextMenu.setAccountUserIdToActAs(ma.getUserId());
                        contextMenu.showContextMenu();
                    }
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }


    /**
     * Create adapters
     */
    private void createAdapters() {
        int listItemId = R.layout.message_basic;
        if (MyPreferences.getDefaultSharedPreferences().getBoolean("appearance_use_avatars", false)) {
            listItemId = R.layout.message_avatar;
        }
        PagedCursorAdapter tweetsAdapter = new PagedCursorAdapter(TimelineActivity.this,
                listItemId, mCursor, new String[] {
                MyDatabase.User.AUTHOR_NAME, MyDatabase.Msg.BODY, MyDatabase.Msg.CREATED_DATE, MyDatabase.MsgOfUser.FAVORITED
                }, new int[] {
                        R.id.message_author, R.id.message_body, R.id.message_details,
                        R.id.message_favorited
                }, getIntent().getData(), PROJECTION, MyDatabase.Msg.DEFAULT_SORT_ORDER);
        tweetsAdapter.setViewBinder(new TweetBinder());

        setListAdapter(tweetsAdapter);
    }

    /**
     * Determines where to save / retrieve position in the list
     * Two rows are always stored for each position hence two keys. Plus Query string is being stored for the search results.
     * @author yvolk@yurivolkov.com
     */
    private class PositionStorage {
        /**
         * MyAccount for SharedPreferences ( =="" for DefaultSharedPreferences) 
         */
        public String accountGuid = "";
        /**
         * SharePreferences to use for storage 
         */
        public SharedPreferences sp = null;
        /**
         * Key name for the first visible item
         */
        public String keyFirst = "";
        /**
         * Key for the last item we should retrieve before restoring position
         */
        public String keyLast = "";
        /**
         * Key for the Query string
         */
        public String keyQueryString = "";
        
        public PositionStorage() {
            if ((mTimelineType != TimelineTypeEnum.USER) && !mIsTimelineCombined) {
                MyAccount ma = MyContextHolder.get().persistentAccounts().fromUserId(mCurrentMyAccountUserId);
                if (ma != null) {
                    sp = ma.getAccountPreferences();
                    accountGuid = ma.getAccountName();
                } else {
                    MyLog.e(this, "No accoount for IserId=" + mCurrentMyAccountUserId);
                }
            }
            if (sp == null) {
                sp = MyPreferences.getDefaultSharedPreferences();
            }
            
            keyFirst = KEY_LAST_POSITION
                    + mTimelineType.save()
                    + (mTimelineType == TimelineTypeEnum.USER ? "_user"
                            + Long.toString(mSelectedUserId) : "") + (mIsSearchMode ? "_search" : "");
            keyLast = keyFirst + "_last";
            keyQueryString = KEY_LAST_POSITION + mTimelineType.save() + "_querystring";
        }
        
        /**
         * @param ps
         * @param lastRow Key for First visible row (false) or Last row that will be retrieved (true)
         * @return Saved Tweet id or -1 or -4 if none was found.
         */
        protected long getSavedPosition(boolean lastRow) {
            long savedItemId = -4;
            if (!mIsSearchMode
                    || (mQueryString.compareTo(sp.getString(
                            keyQueryString, "")) == 0)) {
                // Load saved position in Search mode only if that position was
                // saved for the same query string
                if (lastRow) {
                    savedItemId = sp.getLong(keyLast, -1);
                } else {
                    savedItemId = sp.getLong(keyFirst, -1);
                }
            }
            return savedItemId;
        }
        
    }

    @Override
    public void onReceive(CommandData commandData) {
        switch (commandData.command) {
            case FETCH_TIMELINE:
                if (commandData.timelineType == mTimelineType) {
                    setIsLoading(false);
                }
                break;
            case RATE_LIMIT_STATUS:
                if (commandData.commandResult.hourlyLimit > 0) {
                    updateActionBar(commandData.commandResult.remainingHits + "/"
                            + commandData.commandResult.hourlyLimit);
                }
                break;
            default:
                break;
        }
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
    public long getCurrentMyAccountUserId() {
        return mCurrentMyAccountUserId;
    }

    @Override
    public TimelineTypeEnum getTimelineType() {
        return mTimelineType;
    }

    @Override
    public boolean isTimelineCombined() {
        return mIsTimelineCombined;
    }

    @Override
    public long getSelectedUserId() {
        return mSelectedUserId;
    }
}
