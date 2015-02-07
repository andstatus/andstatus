/**
 * Copyright (C) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.origin;

import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.MenuItem;

import org.andstatus.app.R;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyServiceEvent;
import org.andstatus.app.service.MyServiceListener;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.service.MyServiceReceiver;

public class DiscoveredOriginList extends OriginList implements MyServiceListener {
    MyServiceReceiver mServiceConnector = new MyServiceReceiver(this);
    SwipeRefreshLayout mSwipeRefreshLayout = null;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.myLayoutParent);
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                manualReload();
            }
        });
        if (DiscoveredOrigins.get().isEmpty()) {
            mSwipeRefreshLayout.setRefreshing(true);
            manualReload();
        }
    }

    protected Iterable<Origin> getOrigins() {
        return DiscoveredOrigins.get();
    }

    private void manualReload() {
        MyServiceManager.setServiceAvailable();
        MyServiceManager.sendForegroundCommand(new CommandData(CommandEnum.GET_OPEN_INSTANCES,
                "", OriginType.GNUSOCIAL.getId()));
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
    public void onReceive(CommandData commandData, MyServiceEvent myServiceEvent) {
        if (MyServiceEvent.AFTER_EXECUTING_COMMAND.equals(myServiceEvent) 
                && CommandEnum.GET_OPEN_INSTANCES.equals(commandData.getCommand())) {
            fillList();
            mSwipeRefreshLayout.setRefreshing(false);
        }
    }

    @Override
    protected int getLayoutResourceId() {
        return R.layout.discovered_origin_list;
    }
    
    @Override
    protected int getMenuResourceId() {
        return R.menu.discovered_origin_list;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.reload_menu_item:
                manualReload();
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }
    
}
