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
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.note.NoteEditorListActivity;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.timeline.BaseTimelineAdapter;
import org.andstatus.app.timeline.ListScope;
import org.andstatus.app.timeline.WhichPage;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyStringBuilder;
import org.andstatus.app.util.StringUtil;
import org.andstatus.app.view.MyContextMenu;

/**
 *  List of actors for different contexts
 *  e.g. "Actors of the note", "Followers of my account(s)" etc.
 *  @author yvolk@yurivolkov.com
 */
public class ActorsScreen extends NoteEditorListActivity {
    protected ActorsScreenType actorsScreenType = ActorsScreenType.UNKNOWN;
    private ActorContextMenu contextMenu = null;

    public ActorsScreen() {
        mLayoutId = R.layout.my_list_swipe;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (isFinishing()) {
            return;
        }

        actorsScreenType = getParsedUri().getActorsScreenType();
        contextMenu = new ActorContextMenu(this, MyContextMenu.MENU_GROUP_OBJACTOR);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.actors, menu);
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
                    CommandData.newSearch(SearchObjects.ACTORS, getMyContext(),
                            getParsedUri().getOrigin(getMyContext()), getParsedUri().getSearchQuery()));
        } else {
            showList(WhichPage.CURRENT);
            hideSyncing(method);
        }
    }

    @Override
    protected ActorsLoader newSyncLoader(Bundle args) {
        switch (actorsScreenType) {
            case ACTORS_OF_NOTE:
                return new ActorsOfNoteLoader(myContext, actorsScreenType, getParsedUri().getOrigin(myContext),
                        centralItemId, getParsedUri().getSearchQuery());
            default:
                return new ActorsLoader(myContext, actorsScreenType, getParsedUri().getOrigin(myContext),
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
        return new ActorAdapter(contextMenu, R.layout.actor, getListLoader().getList(),
                Timeline.fromParsedUri(myContext, getParsedUri(), ""));
    }

    protected ActorsLoader getListLoader() {
        return (ActorsLoader) getLoaded();
    }

    @Override
    protected CharSequence getCustomTitle() {
        mSubtitle = I18n.trimTextAt(MyHtml.htmlToCompactPlainText(getListLoader().getSubtitle()), 80);
        final MyStringBuilder title = new MyStringBuilder();
        if (actorsScreenType.scope == ListScope.ORIGIN) {
            title.withSpace(actorsScreenType.title(this));
            Origin origin = getParsedUri().getOrigin(myContext);
            if (origin.isValid()) {
                title.withSpace(actorsScreenType.scope.timelinePreposition(myContext));
                title.withSpace(origin.getName());
            }
        } else {
            Actor actor = Actor.load(myContext, getParsedUri().getActorId());
            if (actor.isEmpty()) {
                title.withSpace(actorsScreenType.title(this));
            } else {
                title.withSpace(actorsScreenType.title(this, actor.getActorNameInTimeline()));
            }
        }
        if (StringUtil.nonEmpty(getParsedUri().getSearchQuery())) {
            title.withSpace("'" + getParsedUri().getSearchQuery() + "'");
        }
        return title.toString();
    }

    @Override
    protected boolean isCommandToShowInSyncIndicator(CommandData commandData) {
        switch (commandData.getCommand()) {
            case GET_ACTOR:
            case GET_FOLLOWERS:
            case GET_FRIENDS:
            case FOLLOW:
            case UNDO_FOLLOW:
            case SEARCH_ACTORS:
            case GET_AVATAR:
                return true;
            default:
                return false;
        }
    }

    @Override
    protected boolean isRefreshNeededAfterExecuting(CommandData commandData) {
        switch(commandData.getCommand()) {
            case FOLLOW:
            case UNDO_FOLLOW:
            case SEARCH_ACTORS:
                return true;
            default:
                return super.isRefreshNeededAfterExecuting(commandData);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        final String method = "onActivityResult";
        MyLog.v(this, () -> method + "; request:" + requestCode + ", result:"
                + (resultCode == RESULT_OK ? "ok" : "fail"));
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
        MyAccount ma = myContext.accounts().fromAccountName(
                data.getStringExtra(IntentExtra.ACCOUNT_NAME.key));
        if (ma.isValid()) {
            contextMenu.setSelectedActingAccount(ma);
            contextMenu.showContextMenu();
        }
    }
}
