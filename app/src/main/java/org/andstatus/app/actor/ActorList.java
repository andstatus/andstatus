/* 
 * Copyright (c) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.actor;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import org.andstatus.app.ActivityRequestCode;
import org.andstatus.app.IntentExtra;
import org.andstatus.app.R;
import org.andstatus.app.SearchObjects;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.note.NoteEditorListActivity;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.timeline.BaseTimelineAdapter;
import org.andstatus.app.timeline.WhichPage;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtils;
import org.andstatus.app.view.MyContextMenu;

/**
 *  List of users for different contexts 
 *  e.g. "Users of the message", "Followers of my account(s)" etc.
 *  @author yvolk@yurivolkov.com
 */
public class ActorList extends NoteEditorListActivity {
    protected ActorListType mActorListType = ActorListType.UNKNOWN;
    private ActorContextMenu contextMenu = null;

    public ActorList() {
        mLayoutId = R.layout.my_list_swipe;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (isFinishing()) {
            return;
        }

        mActorListType = getParsedUri().getUserListType();
        contextMenu = new ActorContextMenu(this, MyContextMenu.MENU_GROUP_OBJACTOR);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.userlist, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.sync_menu_item:
                syncWithInternet(true);
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return false;
    }

    @Override
    public void onRefresh() {
        syncWithInternet(true);
    }

    void syncWithInternet(boolean manuallyLaunched) {
        final String method = "syncWithInternet";
        if (getParsedUri().isSearch()) {
            showSyncing(method, getText(R.string.options_menu_sync));
            MyServiceManager.sendManualForegroundCommand(
                    CommandData.newSearch(SearchObjects.USERS, getMyContext(),
                            getParsedUri().getOrigin(getMyContext()), getParsedUri().getSearchQuery()));
        } else {
            showList(WhichPage.CURRENT);
            hideSyncing(method);
        }
    }

    @Override
    protected ActorListLoader newSyncLoader(Bundle args) {
        switch (mActorListType) {
            case ACTORS_OF_NOTE:
                return new ActorsOfNoteListLoader(mActorListType, getCurrentMyAccount(), centralItemId,
                        getParsedUri().getSearchQuery());
            default:
                return new ActorListLoader(mActorListType, getCurrentMyAccount(), getParsedUri().getOrigin(myContext),
                        centralItemId, getParsedUri().getSearchQuery());
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (contextMenu != null) {
            contextMenu.onContextItemSelected(item);
        }
        return super.onContextItemSelected(item);
    }

    @Override
    protected BaseTimelineAdapter newListAdapter() {
        return new ActorAdapter(contextMenu, R.layout.user, getListLoader().getList(),
                Timeline.fromParsedUri(myContext, getParsedUri(), ""));
    }

    @SuppressWarnings("unchecked")
    protected ActorListLoader getListLoader() {
        return (ActorListLoader) getLoaded();
    }

    @Override
    protected CharSequence getCustomTitle() {
        mSubtitle = I18n.trimTextAt(MyHtml.fromHtml(getListLoader().getTitle()), 80);
        final StringBuilder title = new StringBuilder(mActorListType.getTitle(this));
        if (StringUtils.nonEmpty(getParsedUri().getSearchQuery())) {
            I18n.appendWithSpace(title, "'" + getParsedUri().getSearchQuery() + "'");
        }
        if (getParsedUri().getOrigin(myContext).isValid()) {
            I18n.appendWithSpace(title, myContext.context().getText(R.string.combined_timeline_off_origin));
            I18n.appendWithSpace(title, getParsedUri().getOrigin(myContext).getName());
        }
        return title.toString();
    }

    @Override
    protected boolean isCommandToShowInSyncIndicator(CommandData commandData) {
        switch (commandData.getCommand()) {
            case GET_ACTOR:
            case GET_FOLLOWERS:
            case GET_FRIENDS:
            case FOLLOW_ACTOR:
            case STOP_FOLLOWING_ACTOR:
            case SEARCH_ACTORS:
            case FETCH_AVATAR:
                return true;
            default:
                return false;
        }
    }

    @Override
    protected boolean isRefreshNeededAfterExecuting(CommandData commandData) {
        switch(commandData.getCommand()) {
            case FOLLOW_ACTOR:
            case STOP_FOLLOWING_ACTOR:
            case SEARCH_ACTORS:
                return true;
            default:
                return super.isRefreshNeededAfterExecuting(commandData);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        final String method = "onActivityResult";
        MyLog.v(this, method + "; request:" + requestCode + ", result:" + (resultCode == RESULT_OK ? "ok" : "fail"));
        if (resultCode != RESULT_OK || data == null) {
            return;
        }
        switch (ActivityRequestCode.fromId(requestCode)) {
            case SELECT_ACCOUNT_TO_ACT_AS:
                accountToActAsSelected(data);
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }

    private void accountToActAsSelected(Intent data) {
        MyAccount ma = myContext.persistentAccounts().fromAccountName(
                data.getStringExtra(IntentExtra.ACCOUNT_NAME.key));
        if (ma.isValid()) {
            contextMenu.setMyActor(ma);
            contextMenu.showContextMenu();
        }
    }
}
