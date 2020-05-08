/* 
 * Copyright (c) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.service;

import android.content.Intent;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import org.andstatus.app.R;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.list.SyncLoader;
import org.andstatus.app.timeline.BaseTimelineAdapter;
import org.andstatus.app.timeline.LoadableListActivity;
import org.andstatus.app.timeline.WhichPage;
import org.andstatus.app.util.MyLog;

import java.util.Collections;

public class QueueViewer extends LoadableListActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mLayoutId = R.layout.my_list;
        super.onCreate(savedInstanceState);
    }

    @Override
    protected SyncLoader<QueueData> newSyncLoader(Bundle args) {
        return new SyncLoader<QueueData>() {
            @Override
            public void load(ProgressPublisher publisher) {
                QueueType[] queueTypes = {QueueType.CURRENT, QueueType.SKIPPED, QueueType.RETRY, QueueType.ERROR};
                for (QueueType queueType : queueTypes) {
                    CommandQueue.OneQueue oneQueue = myContext.queues().get(queueType);
                    for (CommandData commandData : oneQueue.queue) {
                        items.add(QueueData.getNew(queueType, commandData));
                    }
                }
                Collections.sort(items);
            }
        };
    }

    @Override
    protected BaseTimelineAdapter newListAdapter() {
        return new QueueViewerAdapter(this, getLoaded().getList());
    }

    private QueueData queueData = null;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.commands_queue, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.clear_the_queue:
                MyServiceManager.sendManualForegroundCommand(
                        CommandData.newItemCommand(
                                CommandEnum.CLEAR_COMMAND_QUEUE,
                                MyAccount.EMPTY,
                                0));
                showList(WhichPage.CURRENT);
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return false;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        queueData = (QueueData) getListAdapter().getItem(v);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.queue_context_menu, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (queueData == null) {
            return super.onContextItemSelected(item);
        }
        switch (item.getItemId()) {
            case R.id.menuItemShare:
                share(queueData);
                return true;
            case R.id.menuItemResend:
                queueData.commandData.resetRetries();
                MyServiceManager.sendManualForegroundCommand(queueData.commandData);
                return true;
            case R.id.menuItemDelete:
                MyServiceManager.sendManualForegroundCommand(
                        CommandData.newItemCommand(
                                CommandEnum.DELETE_COMMAND,
                                MyAccount.EMPTY,
                                queueData.commandData.getCommandId()));
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    private void share(QueueData queueData) {
        Intent intent = new Intent(android.content.Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, queueData.toSharedSubject());
        intent.putExtra(Intent.EXTRA_TEXT, queueData.toSharedText());
        startActivity(Intent.createChooser(intent, getText(R.string.menu_item_share)));        
    }

    @Override
    public void onReceive(CommandData commandData, MyServiceEvent myServiceEvent) {
        if (MyServiceEvent.ON_STOP == myServiceEvent) {
            MyLog.v(this, "On service stop");
            showList(WhichPage.CURRENT);
        }
    }
    
}
