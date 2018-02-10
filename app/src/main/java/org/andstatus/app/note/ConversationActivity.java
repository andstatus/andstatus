/* 
 * Copyright (c) 2012-2015 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.note;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;

import org.andstatus.app.ActivityRequestCode;
import org.andstatus.app.IntentExtra;
import org.andstatus.app.R;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.list.SyncLoader;
import org.andstatus.app.service.QueueViewer;
import org.andstatus.app.timeline.BaseTimelineAdapter;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.BundleUtils;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyCheckBox;
import org.andstatus.app.util.TriState;

/**
 * One selected note and, optionally, the whole conversation
 * 
 * @author yvolk@yurivolkov.com
 */
public class ConversationActivity extends NoteEditorListActivity implements NoteContextMenuContainer {
    private NoteContextMenu mContextMenu;

    DrawerLayout mDrawerLayout;
    ActionBarDrawerToggle mDrawerToggle;
    private boolean showThreadsOfConversation;
    private boolean oldNotesFirstInConversation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mLayoutId = R.layout.conversation;
        super.onCreate(savedInstanceState);
        if (isFinishing()) {
            return;
        }

        mContextMenu = new NoteContextMenu(this);

        showThreadsOfConversation = MyPreferences.isShowThreadsOfConversation();
        oldNotesFirstInConversation = MyPreferences.areOldNotesFirstInConversation();

        initializeDrawer();
    }


    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        mDrawerToggle.syncState();
    }

    private void initializeDrawer() {
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (mDrawerLayout != null) {
            mDrawerToggle = new ActionBarDrawerToggle(
                    this,
                    mDrawerLayout,
                    R.string.drawer_open,
                    R.string.drawer_close
            ) {
            };
            mDrawerLayout.addDrawerListener(mDrawerToggle);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (ActivityRequestCode.fromId(requestCode)) {
            case SELECT_ACCOUNT_TO_ACT_AS:
                if (resultCode == RESULT_OK) {
                    MyAccount myAccount = MyContextHolder.get().accounts().fromAccountName(
                            data.getStringExtra(IntentExtra.ACCOUNT_NAME.key));
                    if (myAccount.isValid()) {
                        mContextMenu.setMyActor(myAccount);
                        mContextMenu.showContextMenu();
                    }
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
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
        getMenuInflater().inflate(R.menu.conversation, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        prepareDrawer();
        return super.onPrepareOptionsMenu(menu);
    }

    private void prepareDrawer() {
        View drawerView = findViewById(R.id.navigation_drawer);
        if (drawerView == null) {
            return;
        }
        MyCheckBox.set(drawerView, R.id.showThreadsOfConversation,
                showThreadsOfConversation, new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        onShowThreadsOfConversationChanged(buttonView, isChecked);
                    }
                });
        MyCheckBox.set(drawerView, R.id.oldNotesFirstInConversation,
                oldNotesFirstInConversation, new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        onOldNotesFirstInConversationChanged(buttonView, isChecked);
                    }
                });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        switch (item.getItemId()) {
            case R.id.commands_queue_id:
                startActivity(new Intent(getActivity(), QueueViewer.class));
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return false;
    }

    public void onShowThreadsOfConversationChanged(View v, boolean isChecked) {
        closeDrawer();
        showThreadsOfConversation = isChecked;
        MyPreferences.setShowThreadsOfConversation(isChecked);
        updateList(TriState.UNKNOWN, 0, true);
    }

    public void onOldNotesFirstInConversationChanged(View v, boolean isChecked) {
        closeDrawer();
        oldNotesFirstInConversation = isChecked;
        MyPreferences.setOldNotesFirstInConversation(isChecked);
        updateList(TriState.UNKNOWN, 0, true);
    }

    private void closeDrawer() {
        ViewGroup mDrawerList = (ViewGroup) findViewById(R.id.navigation_drawer);
        mDrawerLayout.closeDrawer(mDrawerList);
    }

    @Override
    public Timeline getTimeline() {
        return Timeline.getTimeline(myContext, 0, TimelineType.EVERYTHING,
                getCurrentMyAccount(), 0, getCurrentMyAccount().getOrigin(), "");
    }

    @SuppressWarnings("unchecked")
    private ConversationLoader<ConversationViewItem> getListLoader() {
        return ((ConversationLoader<ConversationViewItem>)getLoaded());
    }
    
    @Override
    protected SyncLoader newSyncLoader(Bundle args) {
        return new ConversationLoaderFactory<ConversationViewItem>().
                getLoader(ConversationViewItem.EMPTY,
                getMyContext(), getCurrentMyAccount(), centralItemId, BundleUtils.hasKey(args, IntentExtra.SYNC.key));
    }

    @Override
    protected BaseTimelineAdapter newListAdapter() {
        return new ConversationAdapter(mContextMenu, centralItemId, getListLoader().getList(),
                showThreadsOfConversation, oldNotesFirstInConversation);
    }

    @Override
    protected CharSequence getCustomTitle() {
        MyAccount currentMyAccount = getCurrentMyAccount();
        if (currentMyAccount != null && currentMyAccount.isValid()) {
            mSubtitle = currentMyAccount.toAccountButtonText(myContext);
        } else {
            mSubtitle = "";
        }
        final StringBuilder title = new StringBuilder(getText(R.string.label_conversation));
        I18n.appendWithSpace(title, getText(R.string.combined_timeline_off_origin));
        I18n.appendWithSpace(title, getCurrentMyAccount().getOrigin().getName());
        return title;
    }
}
