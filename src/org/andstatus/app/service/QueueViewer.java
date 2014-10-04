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

import android.app.Activity;
import android.app.ListActivity;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.widget.ListAdapter;

import org.andstatus.app.MyActionBar;
import org.andstatus.app.MyActionBarContainer;
import org.andstatus.app.R;
import org.andstatus.app.account.MySimpleAdapter;
import org.andstatus.app.context.MyContextHolder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.PriorityBlockingQueue;

public class QueueViewer extends ListActivity implements MyActionBarContainer {
    private static final String KEY_QUEUE_TYPE = "queue_type";
    private static final String KEY_COMMAND_SUMMARY = "command_summary";
    private static final String KEY_RESULT_SUMMARY = "result_summary";

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
    }
    
    @Override
    public Activity getActivity() {
        return this;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        MyActionBar actionBar = new MyActionBar(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.queue);
        showList();
        actionBar.attach();
    }

    private void showList() {
        setListAdapter(newListAdapter(newListData()));
    }

    @Override
    public void closeAndGoBack() {
        finish();
    }

    @Override
    public boolean hasOptionsMenu() {
        return false;
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

    private ListAdapter newListAdapter(List<QueueData> queueDataList) {
        List<Map<String, String>> list = new ArrayList<Map<String, String>>();
        for (QueueData queueData : queueDataList) {
            Map<String, String> map = new HashMap<String, String>();
            map.put(KEY_QUEUE_TYPE, queueData.queueType.getAcronym());
            map.put(KEY_COMMAND_SUMMARY, queueData.commandData.toCommandSummary(MyContextHolder.get()));
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
        
        ListAdapter adapter = new MySimpleAdapter(this, 
                list, 
                R.layout.queue_item, 
                new String[] {KEY_QUEUE_TYPE, KEY_COMMAND_SUMMARY, KEY_RESULT_SUMMARY, BaseColumns._ID}, 
                new int[] {R.id.queue_type, R.id.command_summary, R.id.result_summary, R.id.id});
        return adapter;
    }
    
}
