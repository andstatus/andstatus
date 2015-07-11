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
import android.provider.BaseColumns;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ListAdapter;

import org.andstatus.app.MyListActivity;
import org.andstatus.app.R;
import org.andstatus.app.account.MySimpleAdapter;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.TimelineType;
import org.andstatus.app.util.MyLog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.PriorityBlockingQueue;

public class QueueViewer extends MyListActivity implements MyServiceEventsListener {
    private static final String KEY_QUEUE_TYPE = "queue_type";
    private static final String KEY_COMMAND_SUMMARY = "command_summary";
    private static final String KEY_RESULT_SUMMARY = "result_summary";
    MyServiceEventsReceiver mServiceConnector;
    private List<QueueData> mListData = null;

    private static class QueueData {
        QueueType queueType;
        CommandData commandData;
        
        static QueueData getNew(QueueType queueType, CommandData commandData) {
            QueueData queueData = new QueueData();
            queueData.queueType = queueType;
            queueData.commandData = commandData;
            return queueData;
        }

        public long getId() {
            return commandData.hashCode();
        }

        public String toSharedSubject() {
            return queueType.getAcronym() + "; "
                    + commandData.toCommandSummary(MyContextHolder.get());
        }
        
        public String toSharedText() {
            return queueType.getAcronym() + "; "
                    + commandData.share(MyContextHolder.get());
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mLayoutId = R.layout.my_list;
        super.onCreate(savedInstanceState);
        mServiceConnector = new MyServiceEventsReceiver(this);
        showList();
        registerForContextMenu(getListView());
    }

    @Override
    protected void onResume() {
        super.onResume();
        MyServiceManager.setServiceAvailable();
        mServiceConnector.registerReceiver(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mServiceConnector.unregisterReceiver(this);
    }
    
    @Override
    protected void onDestroy() {
        if (mServiceConnector != null) {
            mServiceConnector.unregisterReceiver(this);
        }
        super.onDestroy();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.queue_context_menu, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        if (info == null) {
            return super.onContextItemSelected(item);
        }
        QueueData queueData = queueDataFromId(info.id);
        if (queueData == null) {
            return super.onContextItemSelected(item);
        }
        switch (item.getItemId()) {
            case R.id.menuItemShare:
                share(queueData);
                return true;
            case R.id.menuItemResend:
                queueData.commandData.resetRetries();
                queueData.commandData.setManuallyLaunched(true);
                MyServiceManager.sendForegroundCommand(queueData.commandData);
                return true;
            case R.id.menuItemDelete:
                MyServiceManager.sendForegroundCommand(new CommandData(CommandEnum.DELETE_COMMAND,
                        null, queueData.commandData.getId()));
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

    private QueueData queueDataFromId(long id) {
        if (mListData == null) {
            return null;
        }
        for (QueueData queueData : mListData) {
            if (queueData.getId() == id) {
                return queueData;
            }
        }
        return null;
    }
    
    private void showList() {
        mListData = newListData();
        sortList(mListData);
        setListAdapter(newListAdapter(mListData));
        MyContextHolder.get().clearNotification(TimelineType.ALL);
    }

    private void sortList(List<QueueData> data) {
        java.util.Collections.sort(data, new Comparator<QueueData>() {
                @Override
                public int compare(QueueData lhs, QueueData rhs) {
                    return -longCompare(lhs.commandData.getCreatedDate(), rhs.commandData.getCreatedDate());
                }
            });
    }

    // TODO: Replace with Long.compare for API >= 19
    public static int longCompare(long lhs, long rhs) {
        return lhs < rhs ? -1 : (lhs == rhs ? 0 : 1);
    }
    
    private List<QueueData> newListData() {
        List<QueueData> listData = new ArrayList<QueueData>();
        loadQueue(listData, QueueType.CURRENT);
        loadQueue(listData, QueueType.RETRY);
        loadQueue(listData, QueueType.ERROR);
        return listData;
    }

    private void loadQueue(List<QueueData> listData, QueueType queueType) {
        Queue<CommandData> queue = new PriorityBlockingQueue<CommandData>(100);
        CommandData.loadQueue(this, queue, queueType);
        for (CommandData commandData : queue) {
            listData.add(QueueData.getNew(queueType, commandData));
        }
    }

    private ListAdapter newListAdapter(List<QueueData> listData) {
        List<Map<String, String>> list = new ArrayList<Map<String, String>>();
        for (QueueData queueData : listData) {
            Map<String, String> map = new HashMap<String, String>();
            map.put(KEY_QUEUE_TYPE, queueData.queueType.getAcronym());
            map.put(KEY_COMMAND_SUMMARY, queueData.commandData.toCommandSummary(MyContextHolder.get())
                    + "\t "
                    + queueData.commandData.createdDateWithLabel(this)
            );
            map.put(KEY_RESULT_SUMMARY, queueData.commandData.getResult().toSummary());
            map.put(BaseColumns._ID, Long.toString(queueData.getId()));
            list.add(map);
        }
        if (list.isEmpty()) {
            Map<String, String> map = new HashMap<String, String>();
            map.put(KEY_QUEUE_TYPE, "-");
            map.put(KEY_COMMAND_SUMMARY, getText(R.string.empty_in_parenthesis).toString());
            map.put(KEY_RESULT_SUMMARY, "-");
            map.put(BaseColumns._ID, "0");
            list.add(map);
        }
        
        return new MySimpleAdapter(this, 
                list, 
                R.layout.queue_item, 
                new String[] {KEY_QUEUE_TYPE, KEY_COMMAND_SUMMARY, KEY_RESULT_SUMMARY, BaseColumns._ID}, 
                new int[] {R.id.queue_type, R.id.command_summary, R.id.result_summary, R.id.id});
    }

    @Override
    public void onReceive(CommandData commandData, MyServiceEvent myServiceEvent) {
        if (MyServiceEvent.ON_STOP == myServiceEvent) {
            MyLog.v(this, "On service stop");
            showList();
        }
    }
    
}
